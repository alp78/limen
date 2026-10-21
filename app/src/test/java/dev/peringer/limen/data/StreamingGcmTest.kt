package dev.peringer.limen.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The streaming GCM against the JVM's own, in both directions, and against the published vectors. */
class StreamingGcmTest {

  private val random = java.util.Random(11L)

  private fun bytes(size: Int) = ByteArray(size).also(random::nextBytes)

  private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

  /** [input] through [gcm] in pieces of random length, the way a stream hands it over. */
  private fun run(gcm: StreamingGcm, input: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    var at = 0
    while (at < input.size) {
      val length = minOf(input.size - at, 1 + random.nextInt(40_000))
      out.write(gcm.update(input, at, length))
      at += length
    }
    out.write(gcm.finish())
    return out.toByteArray()
  }

  private fun platform(mode: Int, key: SecretKeySpec, iv: ByteArray, aad: ByteArray, input: ByteArray): ByteArray =
    Cipher.getInstance("AES/GCM/NoPadding").run { init(mode, key, GCMParameterSpec(128, iv)); updateAAD(aad); doFinal(input) }

  private val sizes = listOf(0, 1, 15, 16, 17, 31, 32, 33, 255, 1000, 65_543, (1 shl 20) + 5)

  @Test fun itWritesWhatThePlatformWritesAndReadsWhatThePlatformWrites() {
    sizes.forEach { size ->
      // 38 bytes is the backup header: magic, version, salt and IV.
      listOf(0, 13, 16, 38).forEach { aadSize ->
        val key = SecretKeySpec(bytes(32), "AES")
        val iv = bytes(12)
        val aad = bytes(aadSize)
        val plain = bytes(size)
        val expected = platform(Cipher.ENCRYPT_MODE, key, iv, aad, plain)

        val sealing = StreamingGcm(key, iv, aad, encrypting = true)
        assertArrayEquals("$size bytes, $aadSize of aad", expected, run(sealing, plain) + sealing.tag())

        val opening = StreamingGcm(key, iv, aad, encrypting = false)
        assertArrayEquals(plain, run(opening, expected.copyOf(size)))
        opening.verify(expected.copyOfRange(size, size + 16))
      }
    }
  }

  @Test fun itMatchesTheGcmSpecificationsTestCaseFour() {
    val key = SecretKeySpec(hex("feffe9928665731c6d6a8f9467308308"), "AES")
    val iv = hex("cafebabefacedbaddecaf888")
    val aad = hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")
    val plain = hex("d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39")
    val sealing = StreamingGcm(key, iv, aad, encrypting = true)
    assertArrayEquals(hex("42831ec2217774244b7221b784d0d49ce3aa212f2c02a4e035c17e2329aca12e21d514b25466931c7d8f6a5aac84aa051ba30b396a0aac973d58e091"), run(sealing, plain))
    assertArrayEquals(hex("5bc94fbc3221a5db94fae95ae7121a47"), sealing.tag())
  }

  @Test fun anyChangedBitOrWrongKeyFailsTheTag() {
    val key = SecretKeySpec(bytes(32), "AES")
    val iv = bytes(12)
    val aad = bytes(38)
    val sealed = platform(Cipher.ENCRYPT_MODE, key, iv, aad, bytes(5000))
    fun open(key: SecretKeySpec, aad: ByteArray, sealed: ByteArray) =
      StreamingGcm(key, iv, aad, encrypting = false).also { run(it, sealed.copyOf(sealed.size - 16)) }.verify(sealed.copyOfRange(sealed.size - 16, sealed.size))
    open(key, aad, sealed)
    listOf(0, 2500, sealed.size - 17, sealed.size - 1).forEach { at ->
      val flipped = sealed.copyOf().also { it[at] = (it[at].toInt() xor 1).toByte() }
      assertThrows("bit flipped at $at", AEADBadTagException::class.java) { open(key, aad, flipped) }
    }
    val otherAad = aad.copyOf().also { it[0] = (it[0].toInt() xor 0x80).toByte() }
    assertThrows(AEADBadTagException::class.java) { open(key, otherAad, sealed) }
    assertThrows(AEADBadTagException::class.java) { open(SecretKeySpec(bytes(32), "AES"), aad, sealed) }
  }
}
