package dev.peringer.limen.data

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

  private companion object {
    const val GOLDEN_FINGERPRINT =
      "9BCDDF82B00F72B599EF5188085D846CC69820F8DEFC7AE7CC35860246C28275"
  }

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
    // Written out rather than taken from Templates. A fixture that reads the app's own
    // catalogue moves whenever the catalogue does, and a pinned fingerprint that moves for
    // that reason teaches everyone to paste the new value, which is how the guard dies.
    rooms = listOf(
      Room(
        id = "r1",
        kind = RoomKind.Kitchen,
        items = listOf(
          RoomItem(ItemKey.Floor, grade = Grade.Ok),
          RoomItem(ItemKey.Walls, grade = Grade.Ok),
        ),
      )
    ),
  )

  /**
   * Every nested @Serializable type, instantiated.
   *
   * This is the whole point of the fixture. Asserting that "itemLabel" is absent from a record
   * that holds no Defect, or "conclusionDate" from one that holds no Contract, tests nothing at
   * all: those serializers are never invoked, so the assertion cannot fail however the
   * annotations are edited. A field only reaches the canonical JSON if something instantiates
   * the type that carries it.
   */
  private fun everyNestedType() = v2Record().copy(
    defects = listOf(Defect(id = "d1", roomId = "r1", text = "Stain")),
    objects = listOf(ObjectEntry(id = "o1", group = ObjectGroup.Appliance)),
    contract = Contract(monthlyRentChf = "1850"),
    parties = listOf(Party(role = Role.Tenant, name = "Marie Rouiller")),
    keys = listOf(KeyCount(kind = KeyKind.Apartment, count = 3)),
    deposit = Deposit(kind = DepositKind.BankAccount, amountChf = "5550"),
    meters = Meters(electricity1 = "48214.7"),
    signatures = listOf(Signature(role = Role.Tenant, plane = Plane.Record, file = "tenant-record.png")),
  )

  @Test
  fun everyNestedSerializableTypeIsActuallyInstantiatedByTheFixture() {
    // The guard on the guard: if this fails, the absence assertions below went vacuous again.
    val json = canonical.encodeToString(Protocol.serializer(), everyNestedType())
    listOf("\"defects\"", "\"objects\"", "\"contract\"", "\"parties\"", "\"keys\"", "\"signatures\"")
      .forEach { key ->
        assertTrue("the fixture must instantiate $key for the absence tests to mean anything",
          json.contains(key))
      }
  }

  @Test
  fun topLevelFieldsAddedAfterTheDossierNeverReachTheCanonicalJson() {
    val json = canonical.encodeToString(Protocol.serializer(), v2Record())
    listOf(
      "objects",
      "contract",
      "dossier",
      "dossierChosen",
      "amendedFromFingerprint",
      "controllerIdentity",
      "roomSetupComplete",
      "deliveryMetadataEpochMs",
      "objectCanton",
      "romandFrameworkInLease",
      "legalOverlay",
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

  /**
   * The same guarantee for fields on the NESTED types, which only a fixture that instantiates
   * those types can test at all. This is the assertion the old suite was missing: it checked
   * "itemLabel" and "objectId" against a record holding no Defect, so the Defect serializer was
   * never invoked and the check could not fail however the annotations were edited.
   */
  @Test
  fun nestedFieldsNeverReachTheCanonicalJsonEitherWhenTheirTypeIsPresent() {
    val json = canonical.encodeToString(Protocol.serializer(), everyNestedType())
    listOf("itemLabel", "objectId", "itemId", "conclusionDate", "inspected").forEach { key ->
      assertFalse(
        "$key must not appear even though its type is on the record",
        json.contains("\"$key\""),
      )
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
    val restated = frozen.copy(
      objects = emptyList(),
      contract = Contract(),
      dossier = emptyList(),
      dossierChosen = false,
      amendedFromFingerprint = "",
    )
    assertTrue(ProtocolIntegrity.matches(restated, photos, ink))
  }

  /**
   * The fingerprint of a fixed record, pinned.
   *
   * Nothing else in the suite fails when a field quietly starts reaching the canonical JSON of
   * a record that already carries the type. This does, immediately and unambiguously, because
   * the hex below was produced by a build whose finalized records are in the field. Do not
   * "fix" it by pasting a new value: a moved fingerprint means every record already signed can
   * no longer be exported, and the annotation is what has to change instead.
   */
  @Test
  fun theCanonicalFingerprintOfAFixedRecordHasNotMoved() {
    val photos = File(tmp.root, "photos").apply { mkdirs() }
    val ink = File(tmp.root, "ink").apply { mkdirs() }
    File(ink, "tenant-record.png").writeText("fixed ink bytes")
    assertEquals(GOLDEN_FINGERPRINT, ProtocolIntegrity.fingerprint(everyNestedType(), photos, ink))
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

  @Test
  fun removingTheLastComponentIsRememberedRatherThanDerivedBack() {
    // Without the explicit flag an empty list means "never chose", so the content-derived
    // answer took over and the card the operator had just confirmed away came straight back.
    val stripped = v2Record().copy(
      oneSided = true,
      remarks = "",
      dossier = emptyList(),
      dossierChosen = true,
    )
    assertEquals(emptyList<DossierCard>(), stripped.dossierCards())
    assertFalse(stripped.carries(DossierCard.Notes))
  }

  @Test
  fun aRecordThatChoseItsComponentsBeforeTheFlagExistedStillAnswersFromItsList() {
    val older = v2Record().copy(dossier = listOf(DossierCard.Keys), dossierChosen = false)
    assertEquals(listOf(DossierCard.Keys), older.dossierCards())
  }

  @Test
  fun aFindingIsUntouchedOnlyWhileItCarriesNothingButItsPlace() {
    val shell = Defect(id = "d1", roomId = "r1", item = ItemKey.Floor)
    assertTrue(shell.isUntouched())
    // Every one of these was discarded with no undo by the old text-and-photos-only test.
    assertFalse(shell.copy(text = "Stain").isUntouched())
    assertFalse(shell.copy(photos = listOf("a.jpg")).isUntouched())
    assertFalse(shell.copy(chargedToTenant = true).isUntouched())
    assertFalse(shell.copy(fixBy = FixBy.Tenant).isUntouched())
    assertFalse(shell.copy(fixDeadline = "2026-09-01").isUntouched())
    assertFalse(shell.copy(costNote = "CHF 120").isUntouched())
    assertFalse(shell.copy(reservation = "conteste").isUntouched())
    assertFalse(shell.copy(tenantResponse = TenantResponse.Disputes).isUntouched())
    assertFalse(shell.copy(tenantComment = "already at entry").isUntouched())
  }
}
