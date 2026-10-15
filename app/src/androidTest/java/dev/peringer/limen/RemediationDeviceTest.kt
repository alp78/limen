package dev.peringer.limen

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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

@RunWith(AndroidJUnit4::class)
class RemediationDeviceTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val store get() = Store(context.filesDir)
  private val output get() = File(context.getExternalFilesDir(null), "remediation").apply { mkdirs() }
  private var scenario: ActivityScenario<MainActivity>? = null
  private lateinit var vm: LimenViewModel

  @Before fun isolated() { check(context.packageName == "dev.peringer.limen.uitest") }
  @After fun close() { scenario?.close() }
  private fun sample(id: String = "remediation-${System.nanoTime()}") = Protocol(
    id = id, kind = Kind.Exit, createdEpochMs = 1, sessionRole = Role.Landlord,
    address = "Auditstrasse 1", place = "5000 Aarau", objectCanton = SwissCanton.AG,
    handoverDate = "2026-10-13", handoverTime = "12:00", contract = Contract(conclusionDate = "2026-10-01"),
    parties = listOf(Party(Role.Tenant, "Test Tenant"), Party(Role.Landlord, "Test Owner")),
    rooms = listOf(Room("r1", RoomKind.Kitchen, inspected = true)), roomSetupComplete = true,
    defects = listOf(Defect("d1", "r1", ItemKey.Walls, "Scratch beside the door", chargedToTenant = true, costNote = "CHF 100 estimate")),
    dossier = listOf(DossierCard.Keys), dossierChosen = true,
    keys = listOf(KeyCount(KeyKind.Apartment, count = 2)), keyTransfer = KeyTransferStatus.AllTransferred,
    legalOverlay = LegalOverlayRecord(mapOf(LegalRequirement.LeaseAndLocalTiming to LegalCheckStatus.Confirmed), ruleSetId = LEGAL_RULE_SET_ID),
  )
  private fun launch(p: Protocol? = sample(), language: Lang = Lang.En) {
    scenario?.close()
    context.getSharedPreferences("limen", 0).edit().clear().putString("lang", language.name)
      .apply { if (p != null) putString("openProtocol", p.id) }.commit()
    p?.let(store::save)
    scenario = ActivityScenario.launch(MainActivity::class.java)
    scenario!!.onActivity { vm = ViewModelProvider(it)[LimenViewModel::class.java] }
    compose.waitForIdle()
  }
  private fun act(block: () -> Unit) { compose.runOnIdle(block); compose.waitForIdle() }
  private fun fill(label: String, value: String) {
    compose.onNode(hasContentDescription(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
  }
  private fun capture(name: String) {
    compose.waitForIdle(); android.os.SystemClock.sleep(700)
    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
      File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
    }
  }
  private fun ink() = Bitmap.createBitmap(160, 60, Bitmap.Config.ARGB_8888).apply {
    eraseColor(android.graphics.Color.WHITE)
    android.graphics.Canvas(this).drawLine(10f, 40f, 140f, 10f, android.graphics.Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 4f })
  }

  @Test fun normalWearPreservesTheLandlordClaimAndReservationCanBeCleared() {
    launch(sample().copy(contentRevision = 2, signatures = listOf(Signature(Role.Tenant, plane = Plane.Record,
      refused = true, reservation = "Old reservation", contentRevision = 1))))
    act { vm.setRecordReservation(Role.Tenant, ""); vm.goTo(FlowStep.Sign) }
    compose.onNodeWithText("Old reservation").assertDoesNotExist()
    act { vm.editDefect("d1") }
    compose.onNodeWithText(Str(Lang.En).tenantResponse(TenantResponse.NormalWear)).performScrollTo().performClick()
    act {
      assertTrue(vm.working!!.defects.single().chargedToTenant)
      assertEquals("CHF 100 estimate", vm.working!!.defects.single().costNote)
      assertEquals(TenantResponse.NormalWear, vm.working!!.defects.single().tenantResponse)
    }
    capture("normal-wear-preserves-claim")
  }

  @Test fun invalidVisibleDateSurvivesNavigationAndBlocksFinalization() {
    launch(); act { vm.goTo(FlowStep.Dossier) }
    fill(Str(Lang.En).handoverDate, "31022026")
    act {
      assertEquals("31/02/2026", vm.working!!.handoverDate)
      assertTrue(FinalizationIssue.HandoverDate in vm.completionIssues())
      vm.flush(); vm.goTo(FlowStep.Hub)
      assertEquals("31/02/2026", store.load(vm.working!!.id)!!.handoverDate)
    }
  }

  @Test fun corruptImageIsRejectedBeforeExport() {
    val p = sample().copy(finalizedEpochMs = 1, defects = listOf(Defect("d1", "r1", text = "Failed image", photos = listOf("bad.jpg"))))
    File(store.photoDir(p.id), "bad.jpg").writeText("not an image")
    val frozen = p.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(p, store.photoDir(p.id), store.signatureDir(p.id)))
    assertThrows(IllegalArgumentException::class.java) { PdfExport.export(context, frozen, Str(Lang.En), store.photoDir(p.id), store.signatureDir(p.id)) }
    assertTrue(File(store.photoDir(p.id), "bad.jpg").exists())
  }

  @Test fun exportPreservesConcurrentReceiptAndReportsItsSnapshot() {
    val p = sample().copy(finalizedEpochMs = 1, exportedEpochMs = 1)
    val frozen = p.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(p, store.photoDir(p.id), store.signatureDir(p.id)))
    launch(frozen)
    act { vm.exportPdf(); vm.recordDelivery(Role.Tenant, DeliveryMethod.InPerson, "Recorded during rendering") }
    compose.waitUntil(20_000) { !vm.exporting }
    act {
      assertNotNull(vm.exported)
      assertEquals(1, vm.working!!.deliveryReceipts.size)
      assertEquals(1L, vm.working!!.deliveryRevision)
      assertEquals(0L, vm.working!!.exportHistory.last().deliveryRevision)
      vm.flush()
      assertEquals(1, store.load(p.id)!!.deliveryReceipts.size)
    }
  }

  @Test fun multipleSignersPreviewA4AndAccessibleCopy() {
    val p = sample().copy(parties = listOf(Party(Role.Tenant, "First Tenant", id = "a"), Party(Role.Tenant, "Second Tenant", id = "b"),
      Party(Role.Landlord, "Contractual Owner", representedBy = "Named Agent", id = "owner", agency = "Agency", authority = "Written mandate")),
      details = RecordDetails(understoodLanguage = "English", reservations = mapOf("a" to "No admission of costs", "b" to "Kitchen excluded")),
      defects = listOf(Defect("d1", "r1", text = "Full description <script>unsafe</script>", reservation = "Full defect reservation", tenantComment = "Tenant comment", costNote = "Indicative estimate only")))
    launch(p)
    val preview = PdfExport.preview(context, p, Str(Lang.En), store.photoDir(p.id), store.signatureDir(p.id))
    assertTrue(preview.contains("Full defect reservation")); assertTrue(preview.contains("Tenant comment"))
    assertTrue(preview.contains("&lt;script&gt;")); assertFalse(preview.contains("<script>"))
    assertTrue(preview.contains("No admission of costs")); assertTrue(preview.contains("Named Agent"))
    val bmp = ink()
    act { p.parties.forEach { vm.keepSignature(it.role, Plane.Record, bmp, it.participantKey()) } }
    bmp.recycle()
    act {
      assertEquals(listOf("First Tenant", "Second Tenant", "Named Agent"), vm.working!!.signatures.map { it.name })
      assertTrue(vm.completionIssues().toString(), vm.completionIssues().isEmpty())
      vm.finalizeProtocol()
    }
    compose.waitUntil(10_000) { vm.working?.isFinalized() == true }
    act { vm.exportPdf() }; compose.waitUntil(20_000) { !vm.exporting }
    if (vm.exported == null) {
      val current = vm.working!!
      PdfExport.export(context, current, Str(Lang.En), store.photoDir(p.id), store.signatureDir(p.id))
      fail("View-model export failed: ${vm.exportIssue}")
    }
    val copy = vm.exported!!
    assertNotNull(copy.companionUri)
    context.contentResolver.openFileDescriptor(copy.uri, "r")!!.use { fd -> android.graphics.pdf.PdfRenderer(fd).use { pdf ->
      pdf.openPage(0).use { page -> assertEquals(595, page.width); assertEquals(842, page.height) }
    } }
    context.contentResolver.openInputStream(copy.uri)!!.use { input -> File(output, "three-signers.pdf").outputStream().use(input::copyTo) }
    context.contentResolver.openInputStream(copy.companionUri!!)!!.use { input -> File(output, "three-signers.html").outputStream().use(input::copyTo) }
    act { vm.closeFlow(); vm.openProtocol(p.id); assertNotNull(vm.exported); assertEquals("content", vm.exported!!.uri.scheme) }
  }

  @Test fun fiveLanguagesKeepDateAndParticipantActionsReadable() {
    Lang.entries.forEach { language ->
      launch(language = language)
      val s = Str(language)
      act { vm.goTo(FlowStep.Dossier) }
      compose.onNode(hasContentDescription(s.handoverDate) and hasSetTextAction()).performScrollTo().assertIsDisplayed()
      capture("${language}-date")
      act { vm.navigateIssue(FinalizationIssue.Parties) }
      compose.onNodeWithText("+ ${s.role(Role.NextTenant)}").performScrollTo().assertIsDisplayed()
      capture("${language}-participant-actions")
      act { vm.goTo(FlowStep.Inspection); vm.openRoom("r1") }
      compose.onNode(hasContentDescription(s.roomName) and hasSetTextAction()).performTextReplacement("Kitchen A")
      act { assertEquals("Kitchen A", vm.working!!.rooms.single().name) }
      act { vm.goTo(FlowStep.Review) }
      compose.onNode(hasText(s.preview) and hasClickAction()).performScrollTo().performClick()
      compose.onNodeWithText(s.done).assertIsDisplayed().performClick()
    }
  }

  @Test fun freshEntryThroughVisibleControls() {
    launch(null)
    val s = Str(Lang.En)
    compose.onNodeWithText(s.startNewSession).performClick()
    fill(s.firstName, "Audit")
    fill(s.lastName, "Tenant")
    compose.onNodeWithText(s.validate).performScrollTo().performClick()
    compose.onNodeWithText("DOSSIER").performScrollTo().performClick()
    fill(s.address, "Auditstrasse 1")
    fill(s.place, "5000 Aarau")
    compose.onNodeWithContentDescription(s.selectCanton).performScrollTo().performClick()
    compose.onNodeWithContentDescription("AG · Aargau").performScrollTo().performClick()
    fill(s.handoverTime, "1200")
    compose.onAllNodesWithText(s.legalCheckStatus(LegalCheckStatus.Confirmed))[0].performScrollTo().performClick()
    compose.onAllNodes(hasText(s.toVerify) and hasClickAction())[1].performScrollTo().performClick()
    val notes = compose.onAllNodes(hasContentDescription(s.legalOverlayNote) and hasSetTextAction())
    notes[notes.fetchSemanticsNodes().lastIndex].performScrollTo().performTextReplacement("Lease date not available; official-form position to verify")
    compose.onNodeWithText("○ Parties").performClick()
    fill(s.role(Role.Landlord), "Audit Owner")
    compose.onNodeWithText("○ Keys").performClick()
    compose.onNodeWithText(s.keyTransfer(KeyTransferStatus.NoPhysicalKeys)).performScrollTo().performClick()
    compose.onNodeWithContentDescription(s.back).performClick()
    compose.onNodeWithText("INSPECTION").performClick()
    compose.onNodeWithText("Kitchen").performClick()
    compose.onNodeWithText(s.useSelectedRooms).performClick()
    compose.onNodeWithText("Kitchen").performClick()
    compose.onNodeWithText(s.roomInspected).performScrollTo().performClick()
    compose.onNodeWithText(s.recordDefect).performScrollTo().performClick()
    compose.onNodeWithText("Walls").performScrollTo().performClick()
    fill(s.defectText, "Three centimetre scratch beside the kitchen door")
    compose.onNodeWithContentDescription(s.done).performScrollTo().performClick()
    compose.onNodeWithContentDescription(s.back).performClick()
    compose.onNodeWithContentDescription(s.back).performClick()
    compose.onNodeWithText(s.stepRecord.uppercase()).performScrollTo().performClick()
    compose.onNode(hasText(s.preview) and hasClickAction()).performScrollTo().performClick()
    compose.onNodeWithText(s.done).performClick()
    compose.onNodeWithText(s.stepSign.uppercase()).performScrollTo().performClick()
    repeat(2) {
      compose.onAllNodesWithText(s.signKey)[0].performScrollTo().performClick()
      compose.onNodeWithContentDescription(s.signaturePad).performTouchInput {
        swipe(centerLeft, centerRight, 400)
      }
      compose.onNodeWithContentDescription(s.saveInk).performClick()
    }
    compose.onNode(hasText(s.finalizeRecord) and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
    compose.waitUntil(15_000) { vm.working?.isFinalized() == true }
    compose.onNode(hasText(s.exportPdf) and hasClickAction()).performScrollTo().performClick()
    compose.waitUntil(20_000) { vm.exported != null }
    capture("completed-entry")
    assertEquals(2, vm.working!!.signatures.size)
    assertTrue(vm.working!!.isExported())
  }

  @Test fun saveFailureKeepsTheDraftOpenForRetry() {
    val p = sample(); launch(p)
    val target = File(context.filesDir, "protocols/${p.id}.json")
    act { vm.flush() }
    assertTrue(target.delete()); assertTrue(target.mkdir())
    act {
      vm.edit { it.copy(remarks = "Unsaved note must survive") }; vm.flush()
      assertEquals("failed", vm.saveState)
      vm.closeFlow(); assertNotNull(vm.working)
    }
    compose.onNodeWithText(Str(Lang.En).saveFailed).assertExists()
    assertTrue(target.delete())
    act { vm.flush(); assertEquals("saved", vm.saveState) }
    assertEquals("Unsaved note must survive", store.load(p.id)!!.remarks)
  }

  @Test fun firstSaveCanBeRetriedAfterStorageIsAvailableAgain() {
    launch(null)
    val dir = File(context.filesDir, "protocols").apply { mkdirs() }
    try {
      assertTrue(dir.setWritable(false, false))
      act { vm.startNewSession(Role.Tenant, SessionIdentity(firstName = "Retry"), Kind.Entry) }
      assertEquals("failed", vm.saveState); assertNull(vm.working)
    } finally { assertTrue(dir.setWritable(true, true)) }
    act { vm.startNewSession(Role.Tenant, SessionIdentity(firstName = "Retry"), Kind.Entry) }
    assertNotNull(vm.working); assertEquals("saved", vm.saveState)
    assertEquals("Retry", store.load(vm.working!!.id)!!.sessionName)
  }

  @Test fun signedEditsRequireReviewAndAsyncPhotosStayWithTheirRecord() {
    val p = sample().copy(signatures = listOf(Signature(Role.Tenant, "Named tenant", Plane.Record, refused = true)))
    launch(p)
    act {
      vm.edit { it.copy(address = "Changed address") }
      assertEquals(p.address, vm.working!!.address)
      assertEquals(listOf("Named tenant"), vm.affectedSigners)
      vm.confirmSignedEdit(false); assertEquals(p.address, vm.working!!.address)
      vm.edit { it.copy(address = "Changed address") }; vm.confirmSignedEdit(true)
      assertFalse(vm.working!!.isCurrent(vm.working!!.signatures.single()))
    }
    val image = File(context.cacheDir, "late-photo.jpg")
    ink().also { bitmap -> image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle() }
    val other = sample(); store.save(other)
    act { vm.attachPhoto("d1", image, PhotoSource.Gallery); vm.closeFlow(); vm.openProtocol(other.id) }
    compose.waitUntil(10_000) { store.load(p.id)!!.defects.single().photos.isNotEmpty() }
    assertTrue(vm.working!!.defects.single().photos.isEmpty())
    val photo = store.load(p.id)!!.defects.single().photoEvidence.single()
    assertEquals(0L, photo.capturedEpochMs); assertTrue(photo.importedEpochMs > 0)
  }

  @Test fun allExifOrientationsAreRenderedCorrectly() {
    val colors = listOf(android.graphics.Color.RED, android.graphics.Color.BLUE, android.graphics.Color.YELLOW,
      android.graphics.Color.GREEN, android.graphics.Color.RED, android.graphics.Color.GREEN,
      android.graphics.Color.YELLOW, android.graphics.Color.BLUE)
    for (orientation in 1..8) {
      val file = File(context.cacheDir, "orientation-$orientation.jpg")
      val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
      val canvas = android.graphics.Canvas(bitmap)
      val paint = android.graphics.Paint()
      listOf(android.graphics.Color.RED, android.graphics.Color.BLUE, android.graphics.Color.GREEN, android.graphics.Color.YELLOW).forEachIndexed { index, color ->
        paint.color = color
        val x = (index % 2) * 60f; val y = (index / 2) * 40f
        canvas.drawRect(x, y, x + 60, y + 40, paint)
      }
      file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }; bitmap.recycle()
      androidx.exifinterface.media.ExifInterface(file).apply {
        setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes()
      }
      val decoded = dev.peringer.limen.photo.PhotoDecode.sampled(file, 256)!!
      assertEquals(if (orientation >= 5) 80 else 120, decoded.width)
      val expected = colors[orientation - 1]; val actual = decoded.getPixel(10, 10)
      assertTrue("orientation $orientation", kotlin.math.abs(android.graphics.Color.red(expected) - android.graphics.Color.red(actual)) < 20 &&
        kotlin.math.abs(android.graphics.Color.green(expected) - android.graphics.Color.green(actual)) < 20 &&
        kotlin.math.abs(android.graphics.Color.blue(expected) - android.graphics.Color.blue(actual)) < 20)
      decoded.recycle(); file.delete()
    }
  }

  @Test fun supportingDocumentsMetersAndOverviewPhotosSurviveBackup() {
    val p = sample().copy(details = RecordDetails(
      attachments = listOf(SupportingDocument("lease", DocumentKind.Lease, "Lease supplied at inspection")),
      readings = listOf(MeterReading("meter", "Electricity", "Meter-42", "150.4", "kWh", "2026-10-13", "12:00")),
      accessDevices = listOf(AccessDevice("fob", "Garage remote", "Remote-A", 1, 1)),
    ))
    launch(p)
    val original = File(store.photoDir(p.id), "selected-lease.pdf")
    val pdf = android.graphics.pdf.PdfDocument()
    try {
      val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create())
      page.canvas.drawText("Test lease", 40f, 50f, android.graphics.Paint().apply { textSize = 18f })
      pdf.finishPage(page); original.outputStream().use(pdf::writeTo)
    } finally { pdf.close() }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", original)
    act { vm.attachDocument("lease", uri) }
    compose.waitUntil(10_000) { vm.working!!.details.attachments.single().file.isNotBlank() }
    val photo = File(context.cacheDir, "overview.jpg")
    ink().also { bitmap -> photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle() }
    act { vm.attachOverview("r1", photo, PhotoSource.Gallery) }
    compose.waitUntil(10_000) { vm.working!!.details.roomPhotos["r1"].orEmpty().isNotEmpty() }
    act { vm.flush() }
    val bytes = java.io.ByteArrayOutputStream()
    store.exportArchive("test password".toCharArray(), bytes, setOf(p.id))
    val restored = Store(File(context.cacheDir, "restored-${p.id}"))
    assertEquals(1, restored.importArchive("test password".toCharArray(), java.io.ByteArrayInputStream(bytes.toByteArray())).imported)
    val record = restored.load(p.id)!!
    assertEquals("kWh", record.details.readings.single().unit)
    assertEquals("Remote-A", record.details.accessDevices.single().identifier)
    assertEquals(1, Report.plates(record).size)
    val document = record.details.attachments.single()
    assertEquals(document.sha256, ProtocolIntegrity.fileHash(File(restored.photoDir(p.id), document.file)))
    assertTrue(dev.peringer.limen.photo.EvidenceValidation.unreadable(record, restored.photoDir(p.id), restored.signatureDir(p.id)).isEmpty())
    act { vm.openRoom("r1") }
    compose.onNode(hasContentDescription(Str(Lang.En).overviewPhotos, substring = true) and hasClickAction()).performScrollTo().performClick()
    repeat(3) { compose.onNodeWithText("Zoom in").performClick() }
    compose.onNodeWithText("Fit image").assertIsDisplayed()
    capture("photo-zoom-controls")
    compose.onNodeWithText("Fit image").performClick()
    compose.onNodeWithText(Str(Lang.En).done).performClick()
  }
}
