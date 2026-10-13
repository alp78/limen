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
}
