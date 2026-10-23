package dev.peringer.limen.data

import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted envelope around the validated Limen archive stream. */
internal object ArchiveCrypto {
  private val magic = "LIMENSAFE".toByteArray(Charsets.US_ASCII)
  private const val VERSION: Byte = 1
  private const val SALT_BYTES = 16
  private const val IV_BYTES = 12
  private const val TAG_BYTES = 16
  private const val KEY_BITS = 256
  private const val ITERATIONS = 210_000
  private val ZIP_START = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

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
    val gcm = StreamingGcm(derive(password, salt), iv, header, encrypting = true)
    output.write(header)
    val encrypted = object : OutputStream() {
      override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
      override fun write(buffer: ByteArray, offset: Int, length: Int) = output.write(gcm.update(buffer, offset, length))
      override fun flush() = output.flush()
    }
    ArchiveIO.export(root, encrypted, protocolIds)
    output.write(gcm.finish())
    output.write(gcm.tag())
    output.flush()
  }

  /**
   * What a restore killed part way leaves in [root]: the decrypted copy of an encrypted backup
   * and the unpacked stage. Each is a whole extra copy of the evidence that nothing else reads,
   * so it sits in private storage until something removes it.
   */
  fun sweep(root: java.io.File) {
    root.listFiles()?.filter { it.isFile && it.name.startsWith("archive-decrypt-") }?.forEach { it.delete() }
    ArchiveIO.sweepStages(root)
  }

  /** Encrypted archives are preferred; version-1 plain ZIP files remain importable. */
  fun import(
    root: java.io.File,
    password: CharArray,
    input: InputStream,
    repairCorrupt: Boolean = false,
  ): ArchiveImportResult {
    root.mkdirs()
    sweep(root)
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
    val gcm = StreamingGcm(derive(password, salt), iv, header, encrypting = false)
    // Decrypted whole, and authenticated, BEFORE a single record is installed.
    //
    // CipherInputStream is the trap here: its close() calls doFinal and then swallows the
    // AEADBadTagException, so a tampered or truncated archive read that way reports success.
    // The zip reader compounds it by stopping at the central directory, so the tag bytes were
    // never even reached, and ArchiveIO.import installs as it reads. Checking the tag here by
    // hand, at the end of the stream, is what makes it mean something.
    val staged = java.io.File.createTempFile("archive-decrypt-", ".zip", root)
    try {
      staged.outputStream().buffered().use { out ->
        // The tag is the last 16 bytes, which only the end of the stream reveals, so the
        // newest 16 bytes read are always held back from the cipher.
        val buffer = ByteArray(64 * 1024 + TAG_BYTES)
        var held = 0
        // No size limit: the decrypted copy needs room on disk, and nothing else bounds it.
        val space = FreeSpace(root)
        // A wrong password decrypts to noise, but the tag that says so is the last thing in the
        // file. Every archive ArchiveIO.import accepts opens with a zip local header, since it
        // needs the manifest entry, so noise there is refused after the first chunk instead of
        // after the whole backup has been written to disk.
        var opening = ByteArray(0)
        while (true) {
          val read = probe.read(buffer, held, buffer.size - held)
          if (read < 0) break
          val total = held + read
          val ready = total - TAG_BYTES
          if (ready > 0) {
            space.before(ready)
            val plain = gcm.update(buffer, 0, ready)
            if (opening.size < ZIP_START.size) {
              opening += plain.copyOf(minOf(plain.size, ZIP_START.size - opening.size))
              if (opening.size == ZIP_START.size && !opening.contentEquals(ZIP_START)) {
                throw AEADBadTagException("Wrong password or not a Limen backup")
              }
            }
            out.write(plain)
            buffer.copyInto(buffer, 0, ready, total)
            held = TAG_BYTES
          } else {
            held = total
          }
        }
        require(held == TAG_BYTES) { "Truncated Limen backup" }
        out.write(gcm.finish())
        gcm.verify(buffer.copyOf(TAG_BYTES))
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
