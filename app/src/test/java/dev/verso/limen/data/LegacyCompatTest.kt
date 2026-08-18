package dev.verso.limen.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Guards the one thing a finalized record cannot survive.
 *
 * [ProtocolIntegrity] canonicalizes with `encodeDefaults = true`, so a new field without
 * `@EncodeDefault(NEVER)` appears in the canonical JSON of every record ever written, moves
 * their fingerprints, and locks them out of export forever. The store's `schema` field cannot
 * warn us: it is never written to disk, because the store omits defaulted fields.
 */
class LegacyCompatTest {

  @get:Rule
  val tmp = TemporaryFolder()

  /** Exactly the configuration [ProtocolIntegrity] hashes through. */
  private val canonical = Json { encodeDefaults = true }

  private fun v2Record() = Protocol(
    id = "old",
    kind = Kind.Entry,
    createdEpochMs = 1L,
    address = "Rue du Simplon 12",
    place = "1006 Lausanne",
    rooms = listOf(
      Room(
        id = "r1",
        kind = RoomKind.Kitchen,
        items = Templates.itemsFor(RoomKind.Kitchen).map { RoomItem(it, grade = Grade.Ok) },
      )
    ),
  )

  @Test
  fun fieldsAddedAfterTheDossierNeverReachTheCanonicalJson() {
    val json = canonical.encodeToString(Protocol.serializer(), v2Record())
    listOf(
      "objects",
      "contract",
      "dossier",
      "itemLabel",
      "objectId",
      "objectCanton",
      "romandFrameworkInLease",
      "unilateralReason",
      "unilateralNote",
      "keyTransfer",
      "keyTransferNote",
      "pendingRecordReservations",
      "inspected",
    ).forEach { key ->
      assertFalse("$key must not appear in a record that does not use it", json.contains("\"$key\""))
    }
  }

  @Test
  fun aRecordWithoutObjectsFingerprintsExactlyAsItAlwaysDid() {
    val photos = File(tmp.root, "photos").apply { mkdirs() }
    val ink = File(tmp.root, "ink").apply { mkdirs() }
    File(photos, "wall.jpg").writeText("original photo bytes")

    val old = v2Record().copy(
      defects = listOf(Defect(id = "d1", roomId = "r1", text = "Stain", photos = listOf("wall.jpg"))),
      finalizedEpochMs = 12L,
    )
    val frozen = old.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(old, photos, ink))
    assertTrue(ProtocolIntegrity.matches(frozen, photos, ink))

    // Setting the new fields to their defaults explicitly must change nothing at all.
    val restated = frozen.copy(objects = emptyList(), contract = Contract(), dossier = emptyList())
    assertTrue(ProtocolIntegrity.matches(restated, photos, ink))
  }

  @Test
  fun objectPhotosAreEvidenceToo() {
    val photos = File(tmp.root, "photos").apply { mkdirs() }
    val ink = File(tmp.root, "ink").apply { mkdirs() }
    File(photos, "fridge.jpg").writeText("original photo bytes")

    val draft = v2Record().copy(
      objects = listOf(
        ObjectEntry(
          id = "o1",
          group = ObjectGroup.Appliance,
          catalogue = Appliance.Fridge.name,
          photos = listOf("fridge.jpg"),
        )
      ),
      finalizedEpochMs = 12L,
    )
    val frozen = draft.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(draft, photos, ink))
    assertTrue(ProtocolIntegrity.matches(frozen, photos, ink))

    File(photos, "fridge.jpg").writeText("swapped photo bytes")
    assertFalse(ProtocolIntegrity.matches(frozen, photos, ink))
  }

  @Test
  fun anOlderRecordAnswersItsDossierFromItsContent() {
    val old = v2Record().copy(
      deposit = Deposit(kind = DepositKind.Insurance, amountChf = "4500"),
      meters = Meters(electricity1 = "48214.7"),
      remarks = "Ne vaut pas reconnaissance de dette.",
    )
    assertEquals(
      listOf(DossierCard.Deposit, DossierCard.Meters, DossierCard.Notes),
      old.dossierCards(),
    )
    assertTrue(old.carries(DossierCard.Deposit))
    assertFalse(old.carries(DossierCard.Keys))
  }

  @Test
  fun anEmptyOlderRecordCarriesNoComponents() {
    assertEquals(emptyList<DossierCard>(), v2Record().dossierCards())
  }

  @Test
  fun anExplicitDossierOverridesWhatTheContentSuggests() {
    // The person removed the deposit card but the amount is still on the record.
    val edited = v2Record().copy(
      deposit = Deposit(kind = DepositKind.Insurance, amountChf = "4500"),
      dossier = listOf(DossierCard.Keys),
    )
    assertEquals(listOf(DossierCard.Keys), edited.dossierCards())
    assertFalse(edited.carries(DossierCard.Deposit))
  }

  @Test
  fun onlyOlderRoomsCarryTheItemGrid() {
    val old = v2Record().rooms.first()
    assertTrue(old.hasLegacyGrid())
    assertFalse(Room(id = "r9", kind = RoomKind.Bathroom).hasLegacyGrid())
  }
}
