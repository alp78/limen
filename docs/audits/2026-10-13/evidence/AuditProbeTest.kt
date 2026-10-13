package dev.peringer.limen

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.peringer.limen.data.*
import dev.peringer.limen.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Temporary audit probes. They run only in the isolated .uitest package. */
@RunWith(AndroidJUnit4::class)
class AuditProbeTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val store get() = Store(context.filesDir)
  private val out get() = File(context.getExternalFilesDir(null), "audit-2026-10-13").apply { mkdirs() }
  private var scenario: ActivityScenario<MainActivity>? = null
  private lateinit var vm: LimenViewModel

  @Before fun prepare() {
    check(context.packageName == "dev.peringer.limen.uitest")
    out.mkdirs()
  }
  @After fun finish() { scenario?.close() }

  private fun sample() = Protocol(
    id = "audit-probe", kind = Kind.Exit, createdEpochMs = 10,
    sessionRole = Role.Landlord, sessionName = "Audit Operator",
    address = "Rue du Test 1", place = "1003 Lausanne", objectCanton = SwissCanton.VD,
    handoverDate = "2026-10-13", handoverTime = "12:00",
    parties = listOf(Party(Role.Tenant, "Audit Tenant"), Party(Role.Landlord, "Audit Owner")),
    rooms = listOf(Room("r1", RoomKind.Kitchen, inspected = true)), roomSetupComplete = true,
    objects = listOf(ObjectEntry("o1", ObjectGroup.Appliance, catalogue = "Fridge", roomId = "r1", inspected = true)),
    defects = listOf(Defect("d1", "r1", ItemKey.Walls, "Scratch 3 cm beside the door", chargedToTenant = true, costNote = "CHF 100 estimate")),
    dossier = listOf(DossierCard.Keys), dossierChosen = true,
    keys = listOf(KeyCount(KeyKind.Apartment, count = 2)), keyTransfer = KeyTransferStatus.AllTransferred,
    legalOverlay = LegalOverlayRecord(mapOf(LegalRequirement.LeaseAndLocalTiming to LegalCheckStatus.Confirmed,
      LegalRequirement.VaudExitReadiness to LegalCheckStatus.Confirmed), ruleSetId = LEGAL_RULE_SET_ID),
  )

  private fun launch(p: Protocol? = sample(), lang: Lang = Lang.En) {
    scenario?.close()
    context.getSharedPreferences("limen", 0).edit().clear().putString("lang", lang.name)
      .apply { if (p != null) putString("openProtocol", p.id) }.commit()
    if (p != null) store.save(p)
    scenario = ActivityScenario.launch(MainActivity::class.java)
    scenario!!.onActivity { vm = ViewModelProvider(it)[LimenViewModel::class.java] }
    compose.waitForIdle()
  }

  private fun act(block: () -> Unit) { compose.runOnIdle(block); compose.waitForIdle() }
  private fun capture(label: String) {
    compose.waitForIdle()
    // Compose's clock does not wait for Android window/rotation animations.
    android.os.SystemClock.sleep(700)
    val scale = context.resources.configuration.fontScale
    val tag = "$label-font$scale"
    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
      File(out, "$tag.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
    }
    val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
    nodes.forEach { node ->
      val layouts = mutableListOf<TextLayoutResult>()
      node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
      if (layouts.any { it.didOverflowWidth || it.multiParagraph.height > it.size.height + 3 }) {
        File(out, "overflow-significant.txt").appendText("$tag: ${node.config.getOrElse(SemanticsProperties.Text) { emptyList() }}\n")
      }
    }
  }

  @Test fun screensInEveryLanguage() {
    Lang.entries.forEach { lang ->
      val s = Str(lang)
      launch(lang = lang)
      capture("${lang}-hub")
      act { vm.goTo(FlowStep.Dossier) }
      capture("${lang}-dossier")
      compose.onNodeWithText(s.parties, substring = true).performClick()
      capture("${lang}-parties")
      act { vm.goTo(FlowStep.Inspection); vm.openRoom("r1") }
      capture("${lang}-room")
      act { vm.editDefect("d1") }
      capture("${lang}-defect")
      act { vm.closeDefect(); vm.goTo(FlowStep.Objects) }
      capture("${lang}-objects")
      act { vm.goTo(FlowStep.Review) }
      capture("${lang}-review")
      act { vm.goTo(FlowStep.Sign) }
      capture("${lang}-sign")
      compose.onAllNodesWithContentDescription(s.signKey)[0].performClick()
      capture("${lang}-pad")
      compose.onNodeWithContentDescription(s.cancel).performClick()
      act { vm.refuseSignature(Role.Tenant, Plane.Record); vm.refuseSignature(Role.Landlord, Plane.Record) }
      act { vm.finalizeProtocol() }
      compose.waitUntil(10_000) { vm.working?.isFinalized() == true }
      capture("${lang}-finalized-with-refusals")
      val p = vm.working!!
      val pdf = PdfExport.export(context, p, s, store.photoDir(p.id), store.signatureDir(p.id))
      context.contentResolver.openInputStream(pdf.uri)!!.use { input ->
        File(out, "${lang}-sample.pdf").outputStream().use(input::copyTo)
      }
      context.contentResolver.delete(pdf.uri, null, null)
    }
  }

  @Test fun reproduceReservationAndNormalWearLoss() {
    launch(sample().copy(contentRevision = 2, signatures = listOf(
      Signature(Role.Tenant, "Audit Tenant", Plane.Record, refused = true,
        reservation = "Original reservation", contentRevision = 1))))
    act { vm.goTo(FlowStep.Sign); vm.setRecordReservation(Role.Tenant, "") }
    compose.onNodeWithText("Original reservation").assertExists()
    File(out, "probes.txt").appendText("Confirmed: clearing a stale reservation restores the old signature reservation.\n")
    act { vm.editDefect("d1") }
    compose.onNodeWithText("Normal wear").performScrollTo().performClick()
    act {
      val defect = vm.working!!.defects.single()
      assertFalse(defect.chargedToTenant)
      assertEquals("CHF 100 estimate", defect.costNote)
    }
    capture("normal-wear-hidden-claim")
    File(out, "probes.txt").appendText("Confirmed: Normal wear removes the charge, retains its hidden CHF 100 cost note.\n")
  }

  @Test fun corruptPhotoStillExports() {
    val p = sample().copy(defects = listOf(Defect("bad", "r1", text = "Photo evidence", photos = listOf("bad.jpg"))),
      finalizedEpochMs = 1)
    File(store.photoDir(p.id), "bad.jpg").writeText("This is not an image")
    val frozen = p.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(p, store.photoDir(p.id), store.signatureDir(p.id)))
    assertTrue(ProtocolIntegrity.matches(frozen, store.photoDir(p.id), store.signatureDir(p.id)))
    val pdf = PdfExport.export(context, frozen, Str(Lang.En), store.photoDir(p.id), store.signatureDir(p.id))
    context.contentResolver.openInputStream(pdf.uri)!!.use { input ->
      File(out, "corrupt-photo-omitted.pdf").outputStream().use(input::copyTo)
    }
    context.contentResolver.delete(pdf.uri, null, null)
    File(out, "probes.txt").appendText("Confirmed: unreadable photo passes fingerprint checks and PDF export succeeds.\n")
  }

  @Test fun completeEntryThroughVisibleControls() {
    launch(null)
    val s = Str(Lang.En)
    fun fill(label: String, text: String) {
      compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(text)
    }
    compose.onNodeWithText(s.startNewSession).performClick()
    capture("onboarding-top")
    fill(s.firstName, "Audit")
    fill(s.lastName, "Tenant")
    fill(s.street, "Auditstrasse")
    fill(s.flatNumber, "1")
    fill(s.postalCode, "8000")
    fill(s.city, "Zurich")
    fill(s.phoneNumber, "0791234567")
    compose.onNodeWithText("NEW SESSION · ENTRY").performScrollTo().assertIsEnabled().performClick()
    compose.onNodeWithText("DOSSIER").performClick()
    fill(s.address, "Auditstrasse 1")
    fill(s.place, "5000 Aarau")
    compose.onNodeWithContentDescription(s.selectCanton).performScrollTo().performClick()
    compose.onNodeWithContentDescription("AG · Aargau").performScrollTo().performClick()
    fill(s.handoverTime, "1200")
    compose.onAllNodesWithText("Confirmed")[0].performScrollTo().performClick()
    compose.onAllNodesWithText("Confirmed")[1].performScrollTo().performClick()
    compose.onNodeWithText("○ Parties").performClick()
    fill(s.role(Role.Landlord), "Audit Owner")
    compose.onNodeWithText("○ Keys").performClick()
    compose.onNodeWithText("No physical keys").performScrollTo().performClick()
    compose.onNodeWithContentDescription(s.back).performClick()
    compose.onNodeWithText("INSPECTION").performClick()
    compose.onNodeWithText("Kitchen").performClick()
    compose.onNodeWithText(s.useSelectedRooms).performClick()
    compose.onNodeWithText("Kitchen").performClick()
    compose.onNodeWithText(s.roomInspected).performClick()
    compose.onNodeWithText(s.recordDefect).performClick()
    compose.onNodeWithText("Walls").performClick()
    fill(s.defectText, "Three centimetre scratch beside the kitchen door.")
    compose.onNodeWithContentDescription(s.done).performScrollTo().performClick()
    compose.onAllNodesWithContentDescription(s.back)[0].performClick()
    compose.onNodeWithContentDescription(s.back).performClick()
    compose.onNodeWithText(s.hubClose).performScrollTo().performClick()
    capture("entry-review")
    compose.onNodeWithText("SIGNATURES").performScrollTo().performClick()
    repeat(2) {
      compose.onAllNodesWithContentDescription(s.signKey)[0].performClick()
      compose.onNodeWithContentDescription(s.signaturePad).performTouchInput { swipeRight() }
      compose.onNodeWithContentDescription(s.saveInk).performClick()
    }
    compose.onNode(hasText(s.finalizeRecord) and hasClickAction()).performScrollTo().performClick()
    compose.waitUntil(10_000) { vm.working?.isFinalized() == true }
    compose.onNodeWithText(s.exportPdf).performScrollTo().performClick()
    compose.waitUntil(10_000) { vm.exported != null }
    capture("entry-ui-completed")
    context.contentResolver.openInputStream(vm.exported!!.uri)!!.use { input ->
      File(out, "entry-ui-completed.pdf").outputStream().use(input::copyTo)
    }
    File(out, "probes.txt").appendText("Completed new entry through UI: identity, object, canton, legal checks, parties, keys, room, finding, two drawn signatures, finalization and PDF.\n")
  }

  @Test fun dateLocaleAndFinalizedStatus() {
    launch(lang = Lang.Fr)
    val s = Str(Lang.Fr)
    act { vm.goTo(FlowStep.Dossier) }
    compose.onNode(hasText(s.handoverDate) and hasSetTextAction()).performTextReplacement("31022026")
    compose.onNode(hasText(s.address) and hasSetTextAction()).performClick()
    act { assertEquals("2026-10-13", vm.working!!.handoverDate) }
    capture("Fr-invalid-date-retains-old-date")
    File(out, "probes.txt").appendText("Confirmed: visible invalid date 31/02/2026 leaves prior stored date 2026-10-13 and finalization sees the old date.\n")
    compose.onAllNodesWithContentDescription(s.pickDate)[0].performClick()
    capture("Fr-system-date-picker")
    compose.onNodeWithText(s.cancel).performClick()
    act { vm.goTo(FlowStep.Inspection); vm.openRoom("r1"); vm.toggleTheme() }
    capture("Fr-dark-room")
    act { vm.goTo(FlowStep.Sign); vm.refuseSignature(Role.Tenant, Plane.Record); vm.refuseSignature(Role.Landlord, Plane.Record); vm.finalizeProtocol() }
    compose.waitUntil(10_000) { vm.working?.isFinalized() == true }
    act { vm.goTo(FlowStep.Hub) }
    compose.onNodeWithText(s.lockedRecord.uppercase()).assertExists()
    capture("Fr-refusals-described-as-signed")
    File(out, "probes.txt").appendText("Confirmed: both signatures refused, finalized hub nevertheless says Signed, read only.\n")
    launch()
    act { vm.goTo(FlowStep.Sign) }
    scenario!!.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    compose.waitForIdle()
    compose.onAllNodesWithContentDescription(Str(Lang.En).signKey)[0].performClick()
    capture("En-landscape-signature")
    compose.onNodeWithContentDescription(Str(Lang.En).cancel).performClick()
    scenario!!.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
  }

  @Test fun exportOverwritesConcurrentDelivery() {
    val base = sample().copy(finalizedEpochMs = 1, exportedEpochMs = 1)
    val frozen = base.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(base, store.photoDir(base.id), store.signatureDir(base.id)))
    launch(frozen)
    act {
      vm.exportPdf()
      vm.recordDelivery(Role.Tenant, DeliveryMethod.Electronic, "audit delivery during export")
      assertTrue(vm.working!!.copyDeliveredTo(Role.Tenant))
    }
    compose.waitUntil(10_000) { !vm.exporting && vm.exported != null }
    act { assertFalse(vm.working!!.copyDeliveredTo(Role.Tenant)) }
    assertFalse(store.load(frozen.id)!!.copyDeliveredTo(Role.Tenant))
    File(out, "probes.txt").appendText("Confirmed: a delivery entered while a repeated PDF export runs is lost in memory and on disk when export completes.\n")
  }

  @Test fun cleanupOnlyAuditDownloads() {
    val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val ids = mutableListOf<Long>()
    context.contentResolver.query(collection, arrayOf("_id"), "owner_package_name = ?",
      arrayOf(context.packageName), null)?.use { cursor ->
      while (cursor.moveToNext()) ids.add(cursor.getLong(0))
    }
    ids.forEach { id -> context.contentResolver.delete(android.content.ContentUris.withAppendedId(collection, id), null, null) }
    println("Removed ${ids.size} downloads owned by the isolated audit package; release-owned downloads excluded.")
  }
}
