package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveTest {

  private val password = "correct horse battery staple".toCharArray()

  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun roundTripRestoresProtocolAndEvidenceWithoutOverwriting() {
    val source = Store(tmp.newFolder("source"))
    val record = Protocol(
      id = "p1",
      kind = Kind.Entry,
      createdEpochMs = 1L,
      defects = listOf(Defect(id = "d1", photos = listOf("wall.jpg"))),
    )
    source.save(record)
    File(source.photoDir("p1"), "wall.jpg").writeText("original evidence")
    val bytes = ByteArrayOutputStream().also { source.exportArchive(password, it) }.toByteArray()

    val restored = Store(tmp.newFolder("restored"))
    val first = restored.importArchive(password, ByteArrayInputStream(bytes))
    val second = restored.importArchive(password, ByteArrayInputStream(bytes))

    assertEquals(ArchiveImportResult(imported = 1, skippedExisting = 0, rejected = 0), first)
    assertEquals(ArchiveImportResult(imported = 0, skippedExisting = 1, rejected = 0), second)
    assertEquals(record, restored.load("p1"))
    assertEquals("original evidence", File(restored.photoDir("p1"), "wall.jpg").readText())
  }

  /** The old cap was 64 MB; thirty camera originals weigh 69 MB, so a real record could not be backed up. */
  @Test
  fun aBackupPastTheOldCapSavesAndRestoresEncryptedAndPlain() {
    listOf(password, CharArray(0)).forEachIndexed { run, secret ->
      val source = Store(tmp.newFolder("source-large-$run"))
      source.save(Protocol(id = "large", kind = Kind.Entry, createdEpochMs = 7L, defects = listOf(Defect(id = "d1", photos = listOf("wall.jpg")))))
      // Random bytes do not compress, so the archive itself passes 64 MB, and so does the one entry.
      val photo = File(source.photoDir("large"), "wall.jpg")
      val random = java.util.Random(7L)
      photo.outputStream().buffered().use { out -> val chunk = ByteArray(1 shl 20); repeat(66) { random.nextBytes(chunk); out.write(chunk) } }
      val archive = tmp.newFile("large-$run.limen")
      archive.outputStream().buffered().use { source.exportArchive(secret, it) }
      assertTrue(archive.length() > 64L * 1024 * 1024)

      val restored = Store(tmp.newFolder("restored-large-$run"))
      assertEquals(1, archive.inputStream().buffered().use { restored.importArchive(secret, it) }.imported)
      assertEquals(ProtocolIntegrity.fileHash(photo), ProtocolIntegrity.fileHash(File(restored.photoDir("large"), "wall.jpg")))
    }
  }

  @Test
  fun restoreRejectsARecordWhoseEvidenceIsMissing() {
    val source = Store(tmp.newFolder("source-missing"))
    source.save(
      Protocol(
        id = "p2",
        kind = Kind.Exit,
        createdEpochMs = 2L,
        defects = listOf(Defect(id = "d2", photos = listOf("missing.jpg"))),
      )
    )
    assertThrows(IllegalArgumentException::class.java) { source.exportArchive(password, ByteArrayOutputStream()) }
    // Import must also reject an archive from another writer that omitted the evidence.
    val bytes = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
      zip.putNextEntry(ZipEntry("limen-archive.txt")); zip.write("LIMEN_ARCHIVE\nversion=1\n".toByteArray()); zip.closeEntry()
      zip.putNextEntry(ZipEntry("protocols/p2.json"))
      zip.write(kotlinx.serialization.json.Json.encodeToString(Protocol.serializer(), source.load("p2")!!).toByteArray()); zip.closeEntry()
    } }.toByteArray()
    val restored = Store(tmp.newFolder("restored-missing"))

    assertEquals(1, restored.importArchive(password, ByteArrayInputStream(bytes)).rejected)
    assertTrue(restored.list().isEmpty())
  }

  @Test
  fun restoreRejectsPathTraversal() {
    val bytes = ByteArrayOutputStream().also { output ->
      ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("../outside"))
        zip.write(byteArrayOf(1))
        zip.closeEntry()
      }
    }.toByteArray()
    val restored = Store(tmp.newFolder("restored-malicious"))

    assertThrows(IllegalArgumentException::class.java) {
      restored.importArchive(password, ByteArrayInputStream(bytes))
    }
  }

  @Test
  fun exportIgnoresStrayFilesThatAreNotArchiveEntries() {
    val root = tmp.newFolder("source-stray")
    val source = Store(root)
    source.save(Protocol(id = "p3", kind = Kind.Entry, createdEpochMs = 3L))
    File(root, "protocols/unfinished.tmp").writeText("partial")
    File(root, "photos/not an id").apply { mkdirs() }
      .resolve("unexpected file.jpg").writeText("orphan")

    val bytes = ByteArrayOutputStream().also { source.exportArchive(password, it) }.toByteArray()
    val restored = Store(tmp.newFolder("restored-stray"))

    assertEquals(1, restored.importArchive(password, ByteArrayInputStream(bytes)).imported)
    assertEquals("p3", restored.list().single().id)
  }

  @Test
  fun encryptedBackupDoesNotExposeProtocolOrEvidenceText() {
    val source = Store(tmp.newFolder("source-private"))
    source.save(
      Protocol(
        id = "private-record",
        kind = Kind.Entry,
        createdEpochMs = 4L,
        remarks = "private reservation phrase",
      )
    )
    val bytes = ByteArrayOutputStream().also { source.exportArchive(password, it) }.toByteArray()
    val encoded = bytes.toString(Charsets.ISO_8859_1)

    assertTrue(bytes.copyOfRange(0, 9).contentEquals("LIMENSAFE".toByteArray()))
    assertTrue(!encoded.contains("private-record"))
    assertTrue(!encoded.contains("private reservation phrase"))
  }

  @Test
  fun wrongPasswordCannotRestoreEncryptedBackup() {
    val source = Store(tmp.newFolder("source-wrong-password"))
    source.save(Protocol(id = "p4", kind = Kind.Entry, createdEpochMs = 5L))
    val bytes = ByteArrayOutputStream().also { source.exportArchive(password, it) }.toByteArray()
    val restored = Store(tmp.newFolder("restored-wrong-password"))

    assertThrows(Exception::class.java) {
      restored.importArchive("wrong password".toCharArray(), ByteArrayInputStream(bytes))
    }
    assertTrue(restored.list().isEmpty())
  }

  /** The tag that refuses a wrong password is the last thing in the file, so the opening bytes have to speak for it. */
  @Test
  fun aWrongPasswordIsRefusedAfterTheFirstChunkNotAfterTheWholeBackup() {
    val source = Store(tmp.newFolder("source-early-wrong"))
    source.save(Protocol(id = "big", kind = Kind.Entry, createdEpochMs = 5L, defects = listOf(Defect(id = "d1", photos = listOf("wall.jpg")))))
    File(source.photoDir("big"), "wall.jpg").writeBytes(ByteArray(3 shl 20).also { java.util.Random(5L).nextBytes(it) })
    val bytes = ByteArrayOutputStream().also { source.exportArchive(password, it) }.toByteArray()
    var consumed = 0
    val counting = object : java.io.InputStream() {
      private val inner = ByteArrayInputStream(bytes)
      override fun read() = inner.read().also { if (it >= 0) consumed++ }
      override fun read(buffer: ByteArray, offset: Int, length: Int) = inner.read(buffer, offset, length).also { if (it > 0) consumed += it }
    }
    val root = tmp.newFolder("restored-early-wrong")

    assertThrows(javax.crypto.AEADBadTagException::class.java) { Store(root).importArchive("wrong password".toCharArray(), counting) }
    assertTrue("read $consumed of ${bytes.size} bytes", consumed < 1 shl 20)
    assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("archive-decrypt-") })
  }

  @Test
  fun aTamperedOrTruncatedEncryptedBackupInstallsNothing() {
    val source = Store(tmp.newFolder("source-tampered"))
    source.save(Protocol(id = "p5", kind = Kind.Entry, createdEpochMs = 5L, remarks = "kept whole"))
    val bytes = ByteArrayOutputStream().also { source.exportArchive(password, it) }.toByteArray()
    val flipped = bytes.copyOf().also { it[bytes.size / 2] = (it[bytes.size / 2].toInt() xor 1).toByte() }
    listOf(flipped, bytes.copyOf(bytes.size - 1), bytes.copyOf(bytes.size - 17), bytes.copyOf(50)).forEachIndexed { i, damaged ->
      val restored = Store(tmp.newFolder("restored-tampered-$i"))
      assertThrows(Exception::class.java) { restored.importArchive(password, ByteArrayInputStream(damaged)) }
      assertTrue(restored.list().isEmpty())
    }
  }

  @Test
  fun aBackupSealedWholeByThePlatformCipherStillRestores() {
    val root = tmp.newFolder("source-platform")
    Store(root).save(Protocol(id = "older", kind = Kind.Entry, createdEpochMs = 8L))
    // How 1.2.3 and earlier sealed a backup: the platform's GCM over the whole archive at once.
    val salt = ByteArray(16) { it.toByte() }
    val iv = ByteArray(12) { (it + 16).toByte() }
    val header = "LIMENSAFE".toByteArray() + byteArrayOf(1) + salt + iv
    val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
      .generateSecret(javax.crypto.spec.PBEKeySpec(password, salt, 210_000, 256)).encoded
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
      updateAAD(header)
    }
    val bytes = header + cipher.doFinal(ByteArrayOutputStream().also { ArchiveIO.export(root, it) }.toByteArray())
    assertEquals(1, Store(tmp.newFolder("restored-platform")).importArchive(password, ByteArrayInputStream(bytes)).imported)
  }

  @Test
  fun legacyPlainZipRemainsImportable() {
    val root = tmp.newFolder("source-legacy")
    val source = Store(root)
    source.save(Protocol(id = "legacy", kind = Kind.Exit, createdEpochMs = 6L))
    val bytes = ByteArrayOutputStream().also { ArchiveIO.export(root, it) }.toByteArray()
    val restored = Store(tmp.newFolder("restored-legacy"))

    assertEquals(
      1,
      restored.importArchive("unused".toCharArray(), ByteArrayInputStream(bytes)).imported,
    )
  }

  @Test
  fun emptyPasswordWritesAPlainArchiveThatRestoresWithoutOne() {
    val source = Store(tmp.newFolder("source-open"))
    source.save(Protocol(id = "open", kind = Kind.Exit, createdEpochMs = 7L))
    val none = CharArray(0)
    val bytes = ByteArrayOutputStream().also { source.exportArchive(none, it) }.toByteArray()

    // Plain means plain: a zip signature, not the LIMENSAFE envelope.
    assertTrue(bytes.copyOfRange(0, 2).contentEquals("PK".toByteArray()))
    val restored = Store(tmp.newFolder("restored-open"))
    assertEquals(1, restored.importArchive(none, ByteArrayInputStream(bytes)).imported)
  }

  /** Deflate standing in for libjxl, which runs only on a device: it shrinks the zero-filled test JPEGs and gives them back exactly. */
  private val deflating = object : JpegPacker {
    override fun pack(jpeg: ByteArray): ByteArray = java.util.zip.Deflater().run {
      setInput(jpeg); finish()
      val out = ByteArray(jpeg.size + 64)
      val n = deflate(out); end(); out.copyOf(n)
    }
    override fun unpack(packed: ByteArray): ByteArray = java.util.zip.Inflater().run {
      setInput(packed)
      val out = ByteArrayOutputStream()
      val buffer = ByteArray(8192)
      while (!finished()) { val n = inflate(buffer); if (n == 0 && needsInput()) break; out.write(buffer, 0, n) }
      end(); out.toByteArray()
    }
  }

  private val jpeg = byteArrayOf(-1, -40, -1, -31) + ByteArray(4000) + byteArrayOf(-1, -39)
  private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) + ByteArray(500)

  private fun entries(bytes: ByteArray): Map<String, ByteArray> = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
    generateSequence { zip.nextEntry }.associate { it.name to zip.readBytes() }
  }

  /** A record with one JPEG and one PNG photograph, the JPEG's hash recorded as at capture. */
  private fun withPhotographs(name: String): File {
    val root = tmp.newFolder(name)
    val store = Store(root)
    File(store.photoDir("photos"), "wall.jpg").writeBytes(jpeg)
    File(store.photoDir("photos"), "plan.png").writeBytes(png)
    val hash = ProtocolIntegrity.fileHash(File(store.photoDir("photos"), "wall.jpg"))
    store.save(Protocol(id = "photos", kind = Kind.Entry, createdEpochMs = 9L, defects = listOf(
      Defect(id = "d1", photos = listOf("wall.jpg", "plan.png"), photoEvidence = listOf(PhotoEvidence("wall.jpg", hash))))))
    return root
  }

  @Test
  fun aJpegPhotographGoesInRepackedAndComesBackByteForByte() {
    val bytes = ByteArrayOutputStream().also { ArchiveIO.export(withPhotographs("source-packed"), it, packer = deflating) }.toByteArray()
    val zip = entries(bytes)
    assertEquals("LIMEN_ARCHIVE\nversion=2\n", String(zip.getValue("limen-archive.txt")))
    assertTrue("jpegxl/photos/wall.jpg" in zip && "photos/photos/wall.jpg" !in zip)
    assertTrue(zip.getValue("photos/photos/plan.png").contentEquals(png))

    val restored = tmp.newFolder("restored-packed")
    assertEquals(1, ArchiveIO.import(restored, ByteArrayInputStream(bytes), packer = deflating).imported)
    assertTrue(File(Store(restored).photoDir("photos"), "wall.jpg").readBytes().contentEquals(jpeg))
    assertTrue(File(Store(restored).photoDir("photos"), "plan.png").readBytes().contentEquals(png))

    // A build that cannot unpack refuses the backup whole rather than restore it without its photograph.
    val unable = tmp.newFolder("restored-unable")
    assertThrows(IllegalStateException::class.java) { ArchiveIO.import(unable, ByteArrayInputStream(bytes), packer = null) }
    assertTrue(Store(unable).list().isEmpty())
  }

  @Test
  fun aJpegThatWouldNotComeBackExactlyGoesInAsItIsAndTheBackupStaysVersionOne() {
    val unfaithful = object : JpegPacker {
      override fun pack(jpeg: ByteArray) = deflating.pack(jpeg)
      override fun unpack(packed: ByteArray) = deflating.unpack(packed).also { it[it.size - 1] = 0 }
    }
    val bytes = ByteArrayOutputStream().also { ArchiveIO.export(withPhotographs("source-unfaithful"), it, packer = unfaithful) }.toByteArray()
    val zip = entries(bytes)
    assertEquals("LIMEN_ARCHIVE\nversion=1\n", String(zip.getValue("limen-archive.txt")))
    assertTrue(zip.keys.none { it.startsWith("jpegxl/") })
    assertTrue(zip.getValue("photos/photos/wall.jpg").contentEquals(jpeg))
    // Version 1 is what 1.2.4 and earlier restore, with no packer at all.
    assertEquals(1, ArchiveIO.import(tmp.newFolder("restored-unfaithful"), ByteArrayInputStream(bytes), packer = null).imported)
  }

  @Test
  fun aJpegThatRunsThePackerOutOfMemoryGoesInAsItIsAndTheBackupStaysVersionOne() {
    val exhausted = object : JpegPacker {
      override fun pack(jpeg: ByteArray): ByteArray = throw OutOfMemoryError()
      override fun unpack(packed: ByteArray): ByteArray? = null
    }
    val bytes = ByteArrayOutputStream().also { ArchiveIO.export(withPhotographs("source-exhausted"), it, packer = exhausted) }.toByteArray()
    val zip = entries(bytes)
    assertEquals("LIMEN_ARCHIVE\nversion=1\n", String(zip.getValue("limen-archive.txt")))
    assertTrue(zip.getValue("photos/photos/wall.jpg").contentEquals(jpeg))
  }

  @Test
  fun aRestoreKilledDuringItsInstallDoesNotBlockTheNextRestoreOfThatRecord() {
    val source = Store(tmp.newFolder("source-killed"))
    source.save(Protocol(id = "p6", kind = Kind.Entry, createdEpochMs = 10L, defects = listOf(Defect(id = "d1", photos = listOf("wall.jpg")))))
    File(source.photoDir("p6"), "wall.jpg").writeText("original evidence")
    val bytes = ByteArrayOutputStream().also { source.exportArchive(CharArray(0), it) }.toByteArray()

    // What the killed restore left: evidence folders written, the record file, which goes last, not.
    val root = tmp.newFolder("restored-killed")
    File(root, "photos/p6").mkdirs()
    File(root, "photos/p6/wall.jpg").writeText("half written")
    File(root, "exports/p6").mkdirs()
    File(root, "exports/p6/old.pdf").writeText("old export")
    val restored = Store(root)

    assertEquals(ArchiveImportResult(imported = 1, skippedExisting = 0, rejected = 0), restored.importArchive(CharArray(0), ByteArrayInputStream(bytes)))
    assertEquals("original evidence", File(restored.photoDir("p6"), "wall.jpg").readText())
    assertTrue(!File(root, "exports/p6").exists())
    // Moved aside, not deleted: nothing here is known to be worthless.
    val kept = File(root, "recovery").walkTopDown().filter { it.isFile }.map { it.readText() }.toSet()
    assertEquals(setOf("half written", "old export"), kept)
  }

  @Test
  fun aBackupIsRefusedWhileAPhotographNoLongerMatchesItsRecordedHash() {
    val root = withPhotographs("source-damaged")
    File(Store(root).photoDir("photos"), "wall.jpg").writeBytes(jpeg.copyOf().also { it[10] = 1 })

    val refused = assertThrows(ArchiveRefused::class.java) { ArchiveIO.export(root, ByteArrayOutputStream(), packer = null) }

    assertEquals(ArchiveRefused.Reason.DamagedEvidence, refused.reason)
    assertEquals("photos", refused.record)
  }

  @Test
  fun anUnreadableRecordRefusesAFullBackupButNotTheBackupOfAnotherRecord() {
    val root = tmp.newFolder("source-unreadable")
    val store = Store(root)
    store.save(Protocol(id = "good", kind = Kind.Entry, createdEpochMs = 11L))
    File(root, "protocols/bad.json").writeText("{ not json")

    val refused = assertThrows(ArchiveRefused::class.java) { store.exportArchive(CharArray(0), ByteArrayOutputStream()) }

    assertEquals(ArchiveRefused.Reason.UnreadableRecord, refused.reason)
    assertTrue(ByteArrayOutputStream().also { store.exportArchive(CharArray(0), it, setOf("good")) }.size() > 0)
  }
}
