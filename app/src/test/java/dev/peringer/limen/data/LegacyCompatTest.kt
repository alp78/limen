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
    const val GOLDEN_FINALIZED_FINGERPRINT =
      "2853CCC553B79B76EAFF7B416E4D5F2C4222297682606D9615F38EEBFB9D50B7"
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

  /**
   * What every record finalized since 1.2.0 carries on top of that: the starter's identity, the
   * controller block and the details, with the legal snapshot that finalization always freezes
   * into them, and the canton, the legal overlay and a tenant's position on a finding, which every
   * such record also carries. All of them are NEVER-encoded at their defaults, so a fixture that
   * leaves them out never invokes their serializers, and a field added to any of them without
   * NEVER would move the fingerprint of every such record while the pin on everyNestedType
   * stayed green.
   *
   * reviewRevision, participantNoticeShown, addressCountry, usesSwissAddress and phoneRegion are
   * left at their declared defaults on purpose: the pin only notices a changed literal default
   * when the fixture takes it. The strings carry accents, quotes, a newline and a surrogate pair,
   * because every real French, German or Italian record does.
   */
  private fun finalizedRecord() = everyNestedType().copy(
    objectCanton = SwissCanton.VD,
    romandFrameworkInLease = true,
    legalOverlay = LegalOverlayRecord(
      checks = mapOf(
        LegalRequirement.InitialRentForm to LegalCheckStatus.Confirmed,
        LegalRequirement.VaudExitReadiness to LegalCheckStatus.MissingOrDisputed,
      ),
      note = "Formule officielle remise avec le bail",
      ruleSetId = "ch-tenancy-2026.2",
      notes = mapOf(LegalRequirement.VaudExitReadiness to "Convocation en attente, voir \\ le dossier"),
    ),
    defects = listOf(
      Defect(
        id = "d1",
        roomId = "r1",
        text = "Stain",
        participantPositions = mapOf("tenant-1" to TenantPosition(TenantResponse.Disputes, "Existait à l’entrée")),
      )
    ),
    sessionIdentity = SessionIdentity(
      firstName = "Hélène",
      lastName = "Müller-Rochat",
      company = "Régie du Léman SA",
      street = "Rue du Simplon",
      flatNumber = "12 b",
      postalCode = "1006",
      city = "Lausanne",
      phone = "+41 21 555 01 23",
    ),
    controllerIdentity = ControllerIdentity(
      organisation = "Régie du Léman SA",
      contactName = "Hélène Müller-Rochat",
      street = "Rue du Simplon 12 b",
      postalCode = "1006",
      city = "Lausanne",
      email = "helene@regie.example",
      phone = "+41 21 555 01 23",
    ),
    details = RecordDetails(
      municipality = "Lausanne",
      district = "Lausanne",
      habitableRooms = "3.5",
      actualTransferDate = "2026-10-01",
      actualTransferTime = "14:30",
      notificationDays = "30",
      notificationClause = "Art. 12 du bail",
      scopeNotes = mapOf("r1" to "Armoire fermée à clé,\nnon inspectée"),
      roomPhotos = mapOf(
        "r1" to listOf(
          PhotoEvidence(
            file = "kitchen-overview.jpg",
            sha256 = "ab12cd34",
            capturedEpochMs = 1_760_000_000_000L,
            source = PhotoSource.Camera,
            importedEpochMs = 1_760_000_100_000L,
          )
        )
      ),
      readings = listOf(
        MeterReading(
          id = "m1",
          label = "Électricité",
          identifier = "CH-1234",
          value = "48214.7",
          unit = "kWh",
          date = "2026-10-01",
          time = "14:40",
          note = "Compteur au sous-sol 🔑",
          photos = listOf(PhotoEvidence(file = "meter-electricity.jpg", source = PhotoSource.Gallery)),
        )
      ),
      accessDevices = listOf(
        AccessDevice(id = "a1", label = "Badge", identifier = "B-77", expected = 2, transferred = 2, note = "Remis en main propre")
      ),
      attachments = listOf(
        SupportingDocument(
          id = "doc1",
          kind = DocumentKind.Lease,
          title = "Bail à loyer",
          file = "lease.pdf",
          sha256 = "ef56ab78",
          bytes = 2048,
          importedEpochMs = 1_760_000_200_000L,
          externalReference = "Réf. 2026\\17",
        )
      ),
      followUps = listOf(
        FollowUp(
          id = "f1",
          description = "Remplacer le joint",
          discoveredDate = "2026-10-01",
          notifiedDate = "2026-10-02",
          recipient = "Régie",
          method = "Courriel",
          deadline = "2026-11-01",
          done = true,
        )
      ),
      estimates = mapOf(
        "d1" to ReplacementEstimate(
          replacementChf = "1200",
          ageYears = "6",
          lifeYears = "15",
          basis = "Table de durée de vie",
          date = "2026-10-01",
        )
      ),
      reservations = mapOf("tenant-1" to "Je conteste l’état du \"sol\""),
      legalSnapshot = LegalSnapshot(
        // The literal, not the app's constant: that moves with every legal catalogue release, and a pin that fails for it teaches the habit of pasting a new value.
        ruleSetId = "ch-tenancy-2026.2",
        profile = "VD",
        initialRentFormRule = "Formule officielle exigée",
        requirements = listOf(LegalRequirement.InitialRentForm, LegalRequirement.VaudExitReadiness),
        sources = listOf("https://www.vd.ch/exemple"),
      ),
      understoodLanguage = "fr",
    ),
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
  fun theFinalizedFixtureReachesTheSerializersOfTheTypesThatFinalizationFills() {
    // The guard on the pin below: a fixture that drops one of these goes vacuous without failing.
    val json = canonical.encodeToString(Protocol.serializer(), finalizedRecord())
    listOf(
      "\"sessionIdentity\"", "\"addressCountry\"", "\"phoneRegion\"", "\"company\"",
      "\"controllerIdentity\"", "\"organisation\"",
      "\"details\"", "\"reviewRevision\"", "\"participantNoticeShown\"", "\"roomPhotos\"",
      "\"importedEpochMs\"", "\"readings\"", "\"accessDevices\"", "\"attachments\"",
      "\"followUps\"", "\"estimates\"", "\"legalSnapshot\"", "\"checkedOn\"",
      "\"objectCanton\"", "\"romandFrameworkInLease\"", "\"legalOverlay\"", "\"notes\"", "\"participantPositions\"",
    ).forEach { key ->
      assertTrue("the finalized fixture must instantiate $key", json.contains(key))
    }
    // A backslash is escaped in the canonical form, and the strings of a real record carry one.
    assertTrue(json.contains("\\\\"))
  }

  /**
   * The same pin for a record as 1.2.0 and later finalize it. The pin above covers a record whose
   * three NEVER-encoded blocks sit at their defaults, so it cannot see them; this one can.
   */
  @Test
  fun theCanonicalFingerprintOfAFixedFinalizedRecordHasNotMoved() {
    val photos = File(tmp.root, "photos").apply { mkdirs() }
    val ink = File(tmp.root, "ink").apply { mkdirs() }
    File(ink, "tenant-record.png").writeText("fixed ink bytes")
    listOf("kitchen-overview.jpg", "meter-electricity.jpg", "lease.pdf").forEach { name ->
      File(photos, name).writeText("fixed bytes of $name")
    }
    assertEquals(GOLDEN_FINALIZED_FINGERPRINT, ProtocolIntegrity.fingerprint(finalizedRecord(), photos, ink))
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
