package dev.peringer.limen

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.FixBy
import dev.peringer.limen.data.Contract
import dev.peringer.limen.data.Defect
import dev.peringer.limen.data.ControllerIdentity
import dev.peringer.limen.data.DeliveryMethod
import dev.peringer.limen.data.DeliveryReceipt
import dev.peringer.limen.data.Deposit
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.KeyTransferStatus
import dev.peringer.limen.data.KeyCount
import dev.peringer.limen.data.KeyKind
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.LEGAL_RULE_SET_ID
import dev.peringer.limen.data.LegalCheckStatus
import dev.peringer.limen.data.LegalOverlayRecord
import dev.peringer.limen.data.LegalRequirement
import dev.peringer.limen.data.Meters
import dev.peringer.limen.data.ObjectEntry
import dev.peringer.limen.data.ObjectGroup
import dev.peringer.limen.data.Party
import dev.peringer.limen.data.PdfExport
import dev.peringer.limen.data.PhotoEvidence
import dev.peringer.limen.data.PhotoSource
import dev.peringer.limen.data.Plane
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.ProtocolIntegrity
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.Room
import dev.peringer.limen.data.RoomKind
import dev.peringer.limen.data.Store
import dev.peringer.limen.data.Signature
import dev.peringer.limen.data.SessionIdentity
import dev.peringer.limen.data.SwissCanton
import dev.peringer.limen.data.TenantResponse
import dev.peringer.limen.ui.Lang
import dev.peringer.limen.ui.Str
import dev.peringer.limen.ui.protocolCompletionIssues
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HandoffMatrixTest {

  @get:Rule
  val compose = createEmptyComposeRule()

  private lateinit var scenario: ActivityScenario<MainActivity>

  @Before
  fun launchIsolatedApp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.getSharedPreferences("limen", 0).edit().clear().putString("lang", "En").commit()
    context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    scenario = ActivityScenario.launch(MainActivity::class.java)
  }

  @After
  fun closeApp() {
    scenario.close()
  }

  @Test
  fun allActorsBothHandoffsAndDataNoticeAreReachable() {
    val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
    SwissCanton.entries.forEach { canton ->
      assets.open("cantons/${canton.code.lowercase()}.svg").use { assertTrue(it.read() >= 0) }
    }
    compose.onNodeWithText("NEW SESSION").performClick()

    compose.onNodeWithText("Tenant").assertIsDisplayed()
    compose.onNodeWithText("Landlord").assertIsDisplayed().performClick()
    compose.onNodeWithText("Agency").assertIsDisplayed().performClick()
    compose.onNodeWithText("Agency name").assertExists()
    compose.onNodeWithText("Entry").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Exit").performScrollTo().assertIsDisplayed().performClick()
    compose.onNodeWithText("Entry").performScrollTo().performClick()

    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithText("DATA, BACKUP & PRIVACY").performClick()
    compose.onNodeWithText("Privacy information").assertExists()
    compose.onNodeWithText("Responsible data controller").assertExists()
    compose.onNodeWithText("Backup and restore").assertExists()
    compose.onNodeWithText("CREATE BACKUP").performScrollTo().performClick()
    compose.onNodeWithText("Protect backup").assertIsDisplayed()
    compose.onNodeWithText("CANCEL").performClick()
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithText("NEW SESSION").assertIsDisplayed()

    relaunchWithDraft()
    compose.onNodeWithText("INSPECTION").performClick()
    compose.onNodeWithText("Room").assertExists()
    compose.onNodeWithText("OBJECTS").performClick()
    compose.onAllNodesWithText("Add something else").assertCountEquals(2)
    // Back retraces the route rather than jumping to the hub. Objects is reached through a
    // door at the foot of the inspection, so one back press belongs to that door and returns
    // to the rooms; the hub is one press further out.
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithText("Room").assertExists()
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithText("DOSSIER").performClick()
    compose.onNodeWithText("Parties", substring = true).assertExists()
    compose.onNodeWithContentDescription("SELECT CANTON").performScrollTo().performClick()
    compose.onNodeWithContentDescription("ZH · Zürich").performScrollTo().performClick()
    compose.onNodeWithText(
      "Required throughout the canton in the 2026 official snapshot"
    ).performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithText("CLOSE THE RECORD").performScrollTo().performClick()
    compose.onNodeWithText("Object").assertExists()
    compose.onNodeWithText("SIGNATURES").performScrollTo().performClick()
    compose.onNodeWithText("Unilateral record").assertExists()
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithText("NEW SESSION").assertIsDisplayed()
    verifyPdfExport()
  }

  @Test
  fun aNewDraftSelectsOnlyRoomsThatArePresent() {
    relaunchWithRecord(
      Protocol(
        id = "room-setup-draft",
        kind = Kind.Entry,
        createdEpochMs = 10L,
        sessionRole = Role.Tenant,
        sessionName = "Test tenant",
      )
    )

    compose.onNodeWithText("INSPECTION").performClick()
    compose.onNodeWithText("Rooms present").assertIsDisplayed()
    compose.onNodeWithText("Hall").performClick()
    compose.onNodeWithText("Kitchen").performClick()
    compose.onNodeWithText("USE SELECTED ROOMS").performClick()
    compose.onNodeWithText("Hall").assertExists()
    compose.onNodeWithText("Kitchen").assertExists()
  }

  @Test
  fun aRemovedDefectCanBeUndoneBeforeEvidenceIsDeleted() {
    relaunchWithRecord(
      Protocol(
        id = "undo-draft",
        kind = Kind.Exit,
        createdEpochMs = 11L,
        sessionRole = Role.Landlord,
        sessionName = "Test landlord",
        rooms = listOf(Room("r1", RoomKind.Room, inspected = true)),
        roomSetupComplete = true,
        defects = listOf(Defect(id = "d1", roomId = "r1", text = "Window frame scratch")),
      )
    )

    compose.onNodeWithText("INSPECTION").performClick()
    compose.onNodeWithText("Room").performClick()
    compose.onNodeWithContentDescription("DELETE").performClick()
    compose.onNodeWithText("Defect removed").assertIsDisplayed()
    compose.onNodeWithText("UNDO").performClick()
    compose.onNodeWithText("Window frame scratch").assertExists()
  }

  @Test
  fun allThreeActorsCompleteEntryAndExitWithTwoSignaturesAndPdf() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val store = Store(context.filesDir)
    val actors = listOf("tenant", "landlord", "agency")
    actors.forEach { actor ->
      Kind.entries.forEach { kind ->
        val id = "matrix-$actor-${kind.name.lowercase()}"
        val photo = File(store.photoDir(id), "finding.jpg")
        val tenantInk = File(store.signatureDir(id), "tenant-record.png")
        val landlordInk = File(store.signatureDir(id), "landlord-record.png")
        writeEvidenceBitmap(photo, Color.LTGRAY, "$actor ${kind.name} DEFECT")
        writeEvidenceBitmap(tenantInk, Color.WHITE, "TENANT")
        writeEvidenceBitmap(landlordInk, Color.WHITE, "LANDLORD")
        val storedRole = if (actor == "tenant") Role.Tenant else Role.Landlord
        val revision = 3L
        val draft = Protocol(
          id = id,
          kind = kind,
          createdEpochMs = 20L + actor.length + kind.ordinal,
          sessionRole = storedRole,
          sessionName = "$actor operator",
          sessionIdentity = SessionIdentity(
            firstName = actor.replaceFirstChar(Char::uppercase),
            lastName = "Operator",
            company = if (actor == "agency") "Matrix Agency SA" else "",
            street = "Testweg 8",
            postalCode = "5000",
            city = "Aarau",
            phone = "+41625550101",
          ),
          controllerIdentity = ControllerIdentity(
            organisation = if (actor == "agency") "Matrix Agency SA" else "Matrix operator",
            contactName = "$actor operator",
            email = "$actor@example.invalid",
          ),
          address = "Testweg 10",
          place = "5000 Aarau",
          objectCanton = SwissCanton.AG,
          handoverDate = "2026-08-21",
          handoverTime = "09:30",
          legalOverlay = LegalOverlayRecord(
            checks = mapOf(LegalRequirement.LeaseAndLocalTiming to LegalCheckStatus.Confirmed),
            ruleSetId = LEGAL_RULE_SET_ID,
          ),
          parties = listOf(
            Party(Role.Tenant, "Matrix Tenant", representedBy = "Tenant proxy", email = "tenant@example.invalid"),
            Party(
              Role.Landlord,
              if (actor == "agency") "Matrix Agency SA" else "Matrix Landlord",
              representedBy = if (actor == "agency") "Agency Operator" else "",
              email = "landlord@example.invalid",
            ),
          ),
          contract = Contract(conclusionDate = "2026-07-01", startDate = "2026-08-01"),
          rooms = listOf(Room("r1", RoomKind.Room, inspected = true)),
          roomSetupComplete = true,
          defects = listOf(
            Defect(
              id = "d1",
              roomId = "r1",
              text = "Two-centimetre paint chip beside the inner door handle",
              photos = listOf(photo.name),
              photoEvidence = listOf(
                PhotoEvidence(photo.name, ProtocolIntegrity.fileHash(photo), 21L, PhotoSource.Camera)
              ),
              tenantResponse = if (kind == Kind.Exit) TenantResponse.Disputes else TenantResponse.NotStated,
              tenantComment = if (kind == Kind.Exit) "Already present at entry" else "",
              reservation = "Recorded without accepting a debt",
            )
          ),
          dossier = listOf(DossierCard.Keys),
          keyTransfer = KeyTransferStatus.NoPhysicalKeys,
          signatures = listOf(
            Signature(Role.Tenant, "Matrix Tenant", Plane.Record, tenantInk.name, contentRevision = revision),
            Signature(Role.Landlord, "Matrix Landlord", Plane.Record, landlordInk.name, contentRevision = revision),
          ),
          contentRevision = revision,
        )
        assertTrue(
          "Completion issues for $actor $kind: ${protocolCompletionIssues(draft, listOf(Role.Tenant, Role.Landlord), store.photoDir(id), store.signatureDir(id))}",
          protocolCompletionIssues(
            draft,
            listOf(Role.Tenant, Role.Landlord),
            store.photoDir(id),
            store.signatureDir(id),
          ).isEmpty(),
        )
        val timed = draft.copy(finalizedEpochMs = 30L)
        val frozen = timed.copy(
          evidenceFingerprint = ProtocolIntegrity.fingerprint(timed, store.photoDir(id), store.signatureDir(id))
        )
        val exported = PdfExport.export(context, frozen, Str(Lang.En), store.photoDir(id), store.signatureDir(id))
        try {
          assertTrue("PDF too small for $actor $kind", exported.bytes > 1_000L)
        } finally {
          context.contentResolver.delete(exported.uri, null, null)
        }
      }
    }
  }

  private fun relaunchWithDraft() {
    scenario.close()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val record = Protocol(
      id = "navigation-draft",
      kind = Kind.Entry,
      createdEpochMs = 1L,
      sessionRole = Role.Tenant,
      sessionName = "Test tenant",
      address = "Test street 1",
      place = "1000 Test",
      objectCanton = SwissCanton.VD,
      legalOverlay = LegalOverlayRecord(
        checks = mapOf(
          LegalRequirement.LeaseAndLocalTiming to LegalCheckStatus.Confirmed,
          LegalRequirement.VaudExitReadiness to LegalCheckStatus.Confirmed,
        ),
        ruleSetId = LEGAL_RULE_SET_ID,
      ),
      handoverDate = "2026-08-18",
      handoverTime = "10:00",
      contract = Contract(conclusionDate = "2026-07-01"),
      parties = listOf(Party(Role.Tenant, "Test tenant"), Party(Role.Landlord, "Test landlord")),
      rooms = listOf(Room("r1", RoomKind.Room)),
      dossier = listOf(DossierCard.Keys),
      keyTransfer = KeyTransferStatus.NoPhysicalKeys,
    )
    Store(context.filesDir).save(record)
    context.getSharedPreferences("limen", 0).edit().putString("openProtocol", record.id).commit()
    scenario = ActivityScenario.launch(MainActivity::class.java)
  }

  private fun relaunchWithRecord(record: Protocol) {
    scenario.close()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    Store(context.filesDir).save(record)
    context.getSharedPreferences("limen", 0).edit().putString("openProtocol", record.id).commit()
    scenario = ActivityScenario.launch(MainActivity::class.java)
  }

  private fun verifyPdfExport() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val store = Store(context.filesDir)
    val photo = File(store.photoDir("pdf-smoke"), "window-scratch.jpg")
    writeEvidenceBitmap(photo, Color.rgb(210, 180, 150), "WINDOW SCRATCH")
    val tenantInk = File(store.signatureDir("pdf-smoke"), "tenant-record.png")
    val landlordInk = File(store.signatureDir("pdf-smoke"), "landlord-record.png")
    writeEvidenceBitmap(tenantInk, Color.WHITE, "TENANT SIGNATURE")
    writeEvidenceBitmap(landlordInk, Color.WHITE, "LANDLORD SIGNATURE")
    val draft = Protocol(
      id = "pdf-smoke",
      kind = Kind.Exit,
      createdEpochMs = 2L,
      sessionRole = Role.Landlord,
      sessionName = "Test landlord",
      sessionIdentity = SessionIdentity(
        firstName = "Andrea",
        lastName = "Gerber",
        company = "Régie Professionnelle Exemple SA",
        street = "Rue de la Gare 44",
        postalCode = "1003",
        city = "Lausanne",
        phone = "+41215550199",
      ),
      controllerIdentity = ControllerIdentity(
        organisation = "Régie Professionnelle Exemple SA",
        contactName = "Service protection des données",
        street = "Rue de la Gare 44",
        postalCode = "1003",
        city = "Lausanne",
        email = "privacy@example.invalid",
        phone = "+41215550199",
      ),
      address = "Test street 1",
      place = "1000 Test",
      objectCanton = SwissCanton.VD,
      objectDetail = "3e étage, appartement gauche",
      handoverDate = "2026-08-18",
      handoverTime = "10:00",
      lastProtocolDate = "2024-08-18",
      parties = listOf(
        Party(
          Role.Tenant,
          "Test tenant",
          address = "Nouvelle adresse 9, 1200 Genève",
          representedBy = "Mandataire du locataire",
          phone = "+41791234567",
          email = "tenant@example.invalid",
        ),
        Party(
          Role.Landlord,
          "Régie Professionnelle Exemple SA",
          representedBy = "Andrea Gerber",
          phone = "+41215550199",
          email = "agency@example.invalid",
        ),
      ),
      deposit = Deposit(
        kind = DepositKind.BankAccount,
        amountChf = "5400.00",
        bank = "Banque Cantonale Vaudoise, Service professionnel des garanties de loyer",
        account = "CH93 0076 2011 6238 5295 7 / compte bloqué de garantie locative",
      ),
      contract = Contract(
        monthlyRentChf = "1800.00",
        serviceChargesChf = "250.00",
        conclusionDate = "2026-02-12",
        startDate = "2026-03-01",
        endDate = "2026-08-31",
        noticeDate = "2026-05-29",
      ),
      meters = Meters(
        electricity1 = "48214.7",
        electricity2 = "11802.2",
        gas = "923.1",
        water = "1210.3",
        hotWater = "604.8",
        heatingOil = "68%",
      ),
      rooms = listOf(Room("r1", RoomKind.Room, inspected = true)),
      roomSetupComplete = true,
      objects = listOf(
        ObjectEntry(
          id = "o1",
          group = ObjectGroup.Other,
          groupLabel = "Supplied equipment",
          label = "Remote-controlled parking transmitter",
          roomId = "r1",
          note = "Serial no. PARK-2026-0044",
          inspected = true,
        )
      ),
      defects = listOf(
        Defect(
          id = "d1",
          roomId = "r1",
          text = "Scratch 14 cm long on the lower north-facing window frame",
          photos = listOf(photo.name),
          photoEvidence = listOf(
            PhotoEvidence(
              file = photo.name,
              sha256 = ProtocolIntegrity.fileHash(photo),
              capturedEpochMs = 2L,
              source = PhotoSource.Camera,
            )
          ),
          chargedToTenant = true,
          fixBy = FixBy.Landlord,
          fixDeadline = "2026-09-15",
          costNote = "Estimate requested; no debt acknowledged",
          reservation = "Tenant disputes responsibility",
          tenantResponse = TenantResponse.Disputes,
          tenantComment = "Scratch was already visible before move-in",
        )
      ),
      remarks = "Both parties reviewed the numbered finding and reservation before signing.",
      chargesStatementBy = "2027-02-28",
      keys = listOf(KeyCount(KeyKind.Apartment, count = 2), KeyCount(KeyKind.Mailbox, count = 1)),
      selfMadeKeys = true,
      dossier = DossierCard.entries,
      keyTransfer = KeyTransferStatus.AllTransferred,
      signatures = listOf(
        Signature(Role.Tenant, "Test tenant", Plane.Record, file = tenantInk.name, contentRevision = 7L),
        Signature(Role.Landlord, "Andrea Gerber", Plane.Record, file = landlordInk.name, contentRevision = 7L),
      ),
      contentRevision = 7L,
      finalizedEpochMs = 3L,
    )
    val frozen = draft.copy(
      evidenceFingerprint = ProtocolIntegrity.fingerprint(
        draft,
        store.photoDir(draft.id),
        store.signatureDir(draft.id),
      )
    )
    val printable = frozen.copy(
      deliveryReceipts = listOf(
        DeliveryReceipt(Role.Tenant, 1_787_043_600_000L, DeliveryMethod.Electronic, "secure tenant email"),
        DeliveryReceipt(Role.Landlord, 1_787_043_900_000L, DeliveryMethod.InPerson, "agency copy"),
      ),
      deliveryMetadataEpochMs = 1_787_043_900_000L,
    )
    assertTrue(ProtocolIntegrity.matches(printable, store.photoDir(frozen.id), store.signatureDir(frozen.id)))
    val exported = PdfExport.export(
      context,
      printable,
      Str(Lang.En),
      store.photoDir(frozen.id),
      store.signatureDir(frozen.id),
    )
    try {
      assertTrue(exported.bytes > 1_000L)
      val header = context.contentResolver.openInputStream(exported.uri)?.use { stream ->
        ByteArray(5).also { bytes -> stream.read(bytes) }
      }
      assertTrue(header?.contentEquals("%PDF-".toByteArray()) == true)
      val auditCopy = File(context.getExternalFilesDir(null), "limen-professional-output.pdf")
      context.contentResolver.openInputStream(exported.uri)!!.use { input ->
        auditCopy.outputStream().use(input::copyTo)
      }
      assertTrue(auditCopy.length() == exported.bytes)
    } finally {
      context.contentResolver.delete(exported.uri, null, null)
    }
  }

  private fun writeEvidenceBitmap(file: File, background: Int, text: String) {
    val bitmap = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(background)
    canvas.drawText(
      text,
      60f,
      310f,
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 48f
        strokeWidth = 8f
      },
    )
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
  }
}
