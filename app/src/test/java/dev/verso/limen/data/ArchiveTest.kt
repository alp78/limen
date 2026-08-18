package dev.verso.limen.data

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
    val bytes = ByteArrayOutputStream().also(source::exportArchive).toByteArray()

    val restored = Store(tmp.newFolder("restored"))
    val first = restored.importArchive(ByteArrayInputStream(bytes))
    val second = restored.importArchive(ByteArrayInputStream(bytes))

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
    val bytes = ByteArrayOutputStream().also(source::exportArchive).toByteArray()
    val restored = Store(tmp.newFolder("restored-missing"))

    assertEquals(1, restored.importArchive(ByteArrayInputStream(bytes)).rejected)
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
      restored.importArchive(ByteArrayInputStream(bytes))
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

    val bytes = ByteArrayOutputStream().also(source::exportArchive).toByteArray()
    val restored = Store(tmp.newFolder("restored-stray"))

    assertEquals(1, restored.importArchive(ByteArrayInputStream(bytes)).imported)
    assertEquals("p3", restored.list().single().id)
  }
}
