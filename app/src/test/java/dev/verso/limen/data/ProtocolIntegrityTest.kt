package dev.verso.limen.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProtocolIntegrityTest {

  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun fingerprintCoversTheFrozenRecordAndOriginalEvidence() {
    val photos = File(tmp.root, "photos").apply { mkdirs() }
    val ink = File(tmp.root, "ink").apply { mkdirs() }
    File(photos, "wall.jpg").writeText("original photo bytes")
    File(ink, "tenant-record.png").writeText("original ink bytes")

    val draft = Protocol(
      id = "p1",
      kind = Kind.Exit,
      createdEpochMs = 1L,
      address = "Rue Test 1",
      defects = listOf(
        Defect(
          id = "d1",
          roomId = "r1",
          text = "Stain",
          photos = listOf("wall.jpg"),
          photoEvidence = listOf(PhotoEvidence("wall.jpg", sha256 = ProtocolIntegrity.fileHash(File(photos, "wall.jpg")))),
        )
      ),
      signatures = listOf(Signature(Role.Tenant, plane = Plane.Record, file = "tenant-record.png")),
      finalizedEpochMs = 12L,
    )
    val frozen = draft.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(draft, photos, ink))

    assertEquals(64, frozen.evidenceFingerprint.length)
    assertTrue(ProtocolIntegrity.matches(frozen, photos, ink))
    assertTrue(ProtocolIntegrity.matches(frozen.copy(exportedEpochMs = 13L), photos, ink))
    assertTrue(
      ProtocolIntegrity.matches(
        frozen.copy(
          deliveryReceipts = listOf(DeliveryReceipt(Role.Tenant, 14L)),
          deliveryMetadataEpochMs = 14L,
        ),
        photos,
        ink,
      )
    )

    File(photos, "wall.jpg").writeText("changed photo bytes")
    assertFalse(ProtocolIntegrity.matches(frozen, photos, ink))
  }

  @Test
  fun inkOnlyAppliesToTheRevisionItSaw() {
    val signed = Protocol(
      id = "p1",
      kind = Kind.Entry,
      createdEpochMs = 1L,
      signatures = listOf(
        Signature(Role.Tenant, plane = Plane.Record, file = "tenant-record.png", contentRevision = 3)
      ),
      contentRevision = 3,
    )

    assertTrue(signed.isSigned())
    assertFalse(signed.copy(contentRevision = 4).isSigned())
  }

  @Test
  fun missingEvidenceFailsClosed() {
    val photos = File(tmp.root, "missing-photos").apply { mkdirs() }
    val ink = File(tmp.root, "missing-ink").apply { mkdirs() }
    val record = Protocol(
      id = "p2",
      kind = Kind.Entry,
      createdEpochMs = 1L,
      defects = listOf(Defect(id = "d2", photos = listOf("gone.jpg"))),
    )

    assertEquals(listOf("photo/gone.jpg"), ProtocolIntegrity.missingEvidence(record, photos, ink))
    assertThrows(IllegalArgumentException::class.java) {
      ProtocolIntegrity.fingerprint(record, photos, ink)
    }
    assertFalse(
      ProtocolIntegrity.matches(
        record.copy(finalizedEpochMs = 2L, evidenceFingerprint = "not-a-real-fingerprint"),
        photos,
        ink,
      )
    )
  }
}
