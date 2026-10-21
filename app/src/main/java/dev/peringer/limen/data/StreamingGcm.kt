package dev.peringer.limen.data

import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM one buffer at a time, so a backup of any size passes through a fixed amount of memory.
 *
 * Android's provider holds a whole GCM message until doFinal, encrypting and decrypting
 * alike: restoring a 68 MB backup asked the heap for a single 128 MB array and failed. The
 * output is still standard GCM, byte for byte what the platform cipher wrote, so every
 * existing backup restores and any GCM reads what this writes. The keystream is the
 * platform's AES in counter mode, started at the counter GCM starts at; only GHASH, the
 * authenticator, is computed here, as NIST SP 800-38D specifies it, and the unit tests check
 * both directions against the JVM's own GCM.
 *
 * Decrypting hands back plaintext before the tag is checked, so the caller holds it aside
 * until [verify] has passed.
 */
internal class StreamingGcm(key: SecretKeySpec, iv: ByteArray, aad: ByteArray, private val encrypting: Boolean) {

  private val ctr: Cipher
  private val hHi: Long
  private val hLo: Long
  private val mask: ByteArray
  private var xHi = 0L
  private var xLo = 0L
  private val partial = ByteArray(16)
  private var partialLength = 0
  private val aadBits = aad.size * 8L
  private var textBytes = 0L
  private var tag: ByteArray? = null

  init {
    require(iv.size == 12) { "GCM here takes a 96-bit IV" }
    // One block of counter-mode keystream over zeros is the block cipher applied to the counter.
    fun encryptBlock(block: ByteArray) =
      Cipher.getInstance("AES/CTR/NoPadding").run { init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(block)); doFinal(ByteArray(16)) }
    val h = encryptBlock(ByteArray(16))
    hHi = long(h, 0)
    hLo = long(h, 8)
    mask = encryptBlock(iv + byteArrayOf(0, 0, 0, 1))
    // The first counter block after J0. The platform counts all 128 bits and GCM only the
    // last 32, which agree until 2^32 blocks, past GCM's own limit, held in [update].
    ctr = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv + byteArrayOf(0, 0, 0, 2))) }
    absorb(aad, 0, aad.size)
    pad()
  }

  /** [length] bytes from [input] at [offset], encrypted or decrypted. */
  fun update(input: ByteArray, offset: Int, length: Int): ByteArray {
    check(tag == null) { "GCM stream already finished" }
    if (!encrypting) absorb(input, offset, length)
    val output = ctr.update(input, offset, length) ?: ByteArray(0)
    if (encrypting) absorb(output, 0, output.size)
    textBytes += length
    check(textBytes <= MAX_TEXT_BYTES) { "Larger than GCM allows" }
    return output
  }

  /** Whatever output the cipher still holds; afterwards the tag is fixed. */
  fun finish(): ByteArray {
    val output = ctr.doFinal()
    if (encrypting) absorb(output, 0, output.size)
    pad()
    multiply(aadBits, textBytes * 8)
    val s = ByteArray(16).also { put(it, 0, xHi); put(it, 8, xLo) }
    tag = ByteArray(16) { (s[it].toInt() xor mask[it].toInt()).toByte() }
    return output
  }

  /** The 16-byte tag an encryption appends, once [finish] has run. */
  fun tag(): ByteArray = checkNotNull(tag) { "GCM stream not finished" }.copyOf()

  /** Throws unless [expected] is the tag of what was decrypted, compared in constant time. */
  fun verify(expected: ByteArray) {
    if (!MessageDigest.isEqual(tag(), expected)) throw AEADBadTagException("Limen backup failed authentication")
  }

  private fun absorb(bytes: ByteArray, offset: Int, length: Int) {
    var i = offset
    val end = offset + length
    if (partialLength > 0) {
      while (partialLength < 16 && i < end) partial[partialLength++] = bytes[i++]
      if (partialLength < 16) return
      multiply(long(partial, 0), long(partial, 8))
      partialLength = 0
    }
    while (end - i >= 16) {
      multiply(long(bytes, i), long(bytes, i + 8))
      i += 16
    }
    while (i < end) partial[partialLength++] = bytes[i++]
  }

  /** The additional data and the text are each padded with zeros to whole blocks. */
  private fun pad() {
    if (partialLength == 0) return
    partial.fill(0, partialLength)
    multiply(long(partial, 0), long(partial, 8))
    partialLength = 0
  }

  /**
   * X = (X ⊕ block) · H in GF(2^128), SP 800-38D's Algorithm 1 bit by bit. Masks instead of
   * branches, so the time taken does not follow the key.
   */
  private fun multiply(hi: Long, lo: Long) {
    val x0 = xHi xor hi
    val x1 = xLo xor lo
    var zHi = 0L
    var zLo = 0L
    var vHi = hHi
    var vLo = hLo
    for (i in 0 until 128) {
      val bit = -(if (i < 64) (x0 ushr (63 - i)) and 1L else (x1 ushr (127 - i)) and 1L)
      zHi = zHi xor (vHi and bit)
      zLo = zLo xor (vLo and bit)
      val carry = -(vLo and 1L)
      vLo = (vLo ushr 1) or (vHi shl 63)
      vHi = (vHi ushr 1) xor (R and carry)
    }
    xHi = zHi
    xLo = zLo
  }

  private companion object {
    /** x^128 + x^7 + x^2 + x + 1, in GCM's reflected bit order. */
    const val R = -0x1f00000000000000L // 0xE1 followed by 120 zero bits
    /** GCM's limit for one message, 2^39 - 256 bits. */
    const val MAX_TEXT_BYTES = (1L shl 36) - 32

    fun long(bytes: ByteArray, at: Int): Long {
      var value = 0L
      for (i in 0 until 8) value = (value shl 8) or (bytes[at + i].toLong() and 0xFF)
      return value
    }

    fun put(bytes: ByteArray, at: Int, value: Long) {
      for (i in 0 until 8) bytes[at + i] = (value ushr (56 - 8 * i)).toByte()
    }
  }
}
