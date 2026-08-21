package dev.verso.limen.data

import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted envelope around the validated Limen archive stream. */
internal object ArchiveCrypto {
  private val magic = "LIMENSAFE".toByteArray(Charsets.US_ASCII)
  private const val VERSION: Byte = 1
  private const val SALT_BYTES = 16
  private const val IV_BYTES = 12
  private const val KEY_BITS = 256
  private const val ITERATIONS = 210_000
  private val random = SecureRandom()

  fun export(root: java.io.File, password: CharArray, output: OutputStream) {
    require(password.isNotEmpty()) { "Backup password is empty" }
    val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
    val iv = ByteArray(IV_BYTES).also(random::nextBytes)
    val header = magic + byteArrayOf(VERSION) + salt + iv
    val key = derive(password, salt)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
      updateAAD(header)
    }
    output.write(header)
    CipherOutputStream(output, cipher).use { encrypted ->
      ArchiveIO.export(root, encrypted)
    }
  }

  /** Encrypted archives are preferred; version-1 plain ZIP files remain importable. */
  fun import(
    root: java.io.File,
    password: CharArray,
    input: InputStream,
  ): ArchiveImportResult {
    val probe = PushbackInputStream(input.buffered(), magic.size)
    val prefix = probe.readUpTo(magic.size)
    if (!prefix.contentEquals(magic)) {
      probe.unread(prefix)
      return ArchiveIO.import(root, probe)
    }

    require(password.isNotEmpty()) { "Backup password is empty" }
    val version = probe.read()
    require(version == VERSION.toInt()) { "Unsupported Limen backup version" }
    val salt = probe.readExactly(SALT_BYTES)
    val iv = probe.readExactly(IV_BYTES)
    val header = magic + byteArrayOf(VERSION) + salt + iv
    val key = derive(password, salt)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
      updateAAD(header)
    }
    return CipherInputStream(probe, cipher).use { decrypted ->
      ArchiveIO.import(root, decrypted)
    }
  }

  private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
    return try {
      val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(spec)
        .encoded
      SecretKeySpec(encoded, "AES")
    } finally {
      spec.clearPassword()
    }
  }

  private fun InputStream.readExactly(size: Int): ByteArray =
    readUpTo(size).also { require(it.size == size) { "Truncated Limen backup" } }

  private fun InputStream.readUpTo(size: Int): ByteArray {
    val bytes = ByteArray(size)
    var offset = 0
    while (offset < size) {
      val count = read(bytes, offset, size - offset)
      if (count < 0) break
      if (count == 0) continue
      offset += count
    }
    return if (offset == size) bytes else bytes.copyOf(offset)
  }
}
