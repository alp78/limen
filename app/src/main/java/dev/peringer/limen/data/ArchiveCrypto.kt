package dev.peringer.limen.data

import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
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

  /** The same ceiling the unpacker applies, enforced before the unpacker ever sees the bytes. */
  private const val MAX_PLAINTEXT_BYTES = 64L * 1024L * 1024L
  private val random = SecureRandom()

  fun export(root: java.io.File, password: CharArray, output: OutputStream, protocolIds: Set<String>? = null) {
    // No password writes the plain archive, byte for byte the form the legacy branch of
    // [import] has always read, so an unprotected backup needs no new reader and no new
    // version. Whether to protect is the person's call; the screen says which they made.
    if (password.isEmpty()) {
      ArchiveIO.export(root, output, protocolIds)
      return
    }
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
      ArchiveIO.export(root, encrypted, protocolIds)
    }
  }

  /** Encrypted archives are preferred; version-1 plain ZIP files remain importable. */
  fun import(
    root: java.io.File,
    password: CharArray,
    input: InputStream,
    repairCorrupt: Boolean = false,
  ): ArchiveImportResult {
    root.mkdirs()
    root.listFiles()?.filter { it.isFile && it.name.startsWith("archive-decrypt-") }?.forEach { it.delete() }
    val probe = PushbackInputStream(input.buffered(), magic.size)
    val prefix = probe.readUpTo(magic.size)
    if (!prefix.contentEquals(magic)) {
      probe.unread(prefix)
      return ArchiveIO.import(root, probe, repairCorrupt)
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
    // Decrypted whole, and authenticated, BEFORE a single record is installed.
    //
    // CipherInputStream is the trap here: its close() calls doFinal and then swallows the
    // AEADBadTagException, so a tampered or truncated archive read that way reports success.
    // The zip reader compounds it by stopping at the central directory, so the tag bytes were
    // never even reached, and ArchiveIO.import installs as it reads. Calling doFinal here by
    // hand is what makes the tag mean something.
    val staged = java.io.File.createTempFile("archive-decrypt-", ".zip", root)
    try {
      staged.outputStream().buffered().use { out ->
        val buffer = ByteArray(64 * 1024)
        var written = 0L
        var received = 0L
        while (true) {
          val read = probe.read(buffer)
          if (read < 0) break
          if (read == 0) continue
          received += read
          require(received <= MAX_PLAINTEXT_BYTES + 16) { "Encrypted archive exceeds 64 MB; restore smaller scoped archives" }
          cipher.update(buffer, 0, read)?.let { plain ->
            written += plain.size
            require(written <= MAX_PLAINTEXT_BYTES) { "Archive is too large" }
            out.write(plain)
          }
        }
        cipher.doFinal()?.let { plain ->
          written += plain.size
          require(written <= MAX_PLAINTEXT_BYTES) { "Archive is too large" }
          out.write(plain)
        }
      }
      return staged.inputStream().buffered().use { verified ->
        ArchiveIO.import(root, verified, repairCorrupt)
      }
    } finally {
      staged.delete()
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
