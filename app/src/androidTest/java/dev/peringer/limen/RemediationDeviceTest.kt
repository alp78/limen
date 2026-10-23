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
    // The preview is the HTML copy, drawn in the app's panels with a contents list.
    assertTrue(preview.contains("class=\"panel")); assertTrue(preview.contains("<nav class=\"contents\""))
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

  @Test fun annexPlatesDecodeUprightAtTheirExactSizeWithoutTheGainMap() {
    fun jpeg(name: String, gainmap: Boolean): File {
      // Larger than its plate in both edges, so the decode samples and then scales.
      val bitmap = Bitmap.createBitmap(3200, 2400, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
      android.graphics.Canvas(bitmap).drawRect(0f, 0f, 1600f, 1200f, android.graphics.Paint().apply { color = android.graphics.Color.RED })
      if (gainmap) bitmap.gainmap = android.graphics.Gainmap(Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888))
      return File(context.cacheDir, name).also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle() }
    }
    // A gain map is what made PdfDocument rasterize every plate at 72 dpi, so the annex must drop it.
    if (android.os.Build.VERSION.SDK_INT >= 34) {
      val hdr = jpeg("annex-hdr.jpg", gainmap = true)
      android.graphics.BitmapFactory.decodeFile(hdr.absolutePath).let { assertTrue(it.hasGainmap()); it.recycle() }
      val fit = AnnexPlate.fit(3200, 2400)
      val plate = dev.peringer.limen.photo.PhotoDecode.uprightAtSize(hdr, fit.pixelWidth, fit.pixelHeight)!!
      assertEquals(1360, plate.width); assertEquals(1020, plate.height); assertFalse(plate.hasGainmap())
      plate.recycle(); hdr.delete()
    }
    val sideways = jpeg("annex-sideways.jpg", gainmap = false)
    androidx.exifinterface.media.ExifInterface(sideways).apply {
      setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes()
    }
    assertEquals(2400 to 3200, dev.peringer.limen.photo.PhotoDecode.uprightSize(sideways))
    val fit = AnnexPlate.fit(2400, 3200)
    val plate = dev.peringer.limen.photo.PhotoDecode.uprightAtSize(sideways, fit.pixelWidth, fit.pixelHeight)!!
    assertEquals(1100, plate.width); assertEquals(1467, plate.height)
    // The red quarter was top left before the quarter turn and stands top right after it.
    fun red(pixel: Int) = android.graphics.Color.red(pixel) > 200 && android.graphics.Color.green(pixel) < 60 && android.graphics.Color.blue(pixel) < 60
    assertTrue(red(plate.getPixel(plate.width - 20, 20))); assertFalse(red(plate.getPixel(20, 20)))
    plate.recycle(); sideways.delete()
  }

  @Test fun theExportedAnnexCarriesEachPlateAsJpegAtItsPlateSize() {
    val base = sample()
    val photo = File(store.photoDir(base.id), "d1-annex.jpg")
    Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888).apply {
      eraseColor(android.graphics.Color.GRAY)
      android.graphics.Canvas(this).drawCircle(800f, 600f, 400f, android.graphics.Paint().apply { color = android.graphics.Color.BLUE })
      photo.outputStream().use { compress(Bitmap.CompressFormat.JPEG, 95, it) }
      recycle()
    }
    val p = base.copy(finalizedEpochMs = 1, defects = listOf(base.defects.single().copy(photos = listOf(photo.name),
      photoEvidence = listOf(PhotoEvidence(photo.name, ProtocolIntegrity.fileHash(photo))))))
    val frozen = p.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(p, store.photoDir(p.id), store.signatureDir(p.id)))
    val exported = PdfExport.export(context, frozen, Str(Lang.Fr), store.photoDir(p.id), store.signatureDir(p.id))
    val pdf = context.contentResolver.openInputStream(exported.uri)!!.use { it.readBytes() }
    val text = String(pdf, Charsets.ISO_8859_1)
    val fit = AnnexPlate.fit(1600, 1200)
    val head = Regex("""/Width ${fit.pixelWidth}\n/Height ${fit.pixelHeight}\n/ColorSpace [^\n]+\n/BitsPerComponent 8\n/Filter /DCTDecode\n/Length (\d+)>> stream\n""").find(text)
    assertNotNull(head)
    val jpeg = pdf.copyOfRange(head!!.range.last + 1, head.range.last + 1 + head.groupValues[1].toInt())
    android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).let {
      assertEquals(fit.pixelWidth, it.width); assertEquals(fit.pixelHeight, it.height); it.recycle()
    }
    // No stand-in survives: every plate was replaced, and a stand-in is the only 4 × 4 image.
    assertFalse(text.contains("/Width ${PdfPlates.SIDE}\n/Height ${PdfPlates.SIDE}\n"))
    context.contentResolver.openFileDescriptor(exported.uri, "r")!!.use { fd -> android.graphics.pdf.PdfRenderer(fd).use { assertTrue(it.pageCount > 1) } }
    // The HTML copy carries the very JPEG the PDF does.
    val html = context.contentResolver.openInputStream(exported.companionUri!!)!!.use { it.readBytes().toString(Charsets.UTF_8) }
    val embedded = Regex("""src="data:image/jpeg;base64,([^"]+)"""").findAll(html).map { android.util.Base64.decode(it.groupValues[1], android.util.Base64.DEFAULT) }
    assertTrue(embedded.any { it.contentEquals(jpeg) })
  }

  @Test fun aBackupRepacksAnUltraHdrJpegAsJpegXlAndRestoresItByteForByte() {
    assertNotNull(JpegXl.loaded)
    // Shaped like a Pixel capture: a gain map appended after the image, which must come back untouched.
    val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
    val random = java.util.Random(3L)
    android.graphics.Canvas(bitmap).apply {
      drawColor(android.graphics.Color.rgb(180, 170, 150))
      val paint = android.graphics.Paint()
      repeat(400) {
        paint.color = android.graphics.Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
        val x = random.nextInt(1600).toFloat(); val y = random.nextInt(1200).toFloat()
        drawRect(x, y, x + 20 + random.nextInt(200), y + 20 + random.nextInt(200), paint)
      }
    }
    if (android.os.Build.VERSION.SDK_INT >= 34) bitmap.gainmap = android.graphics.Gainmap(Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888))
    val jpeg = java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    bitmap.recycle()
    if (android.os.Build.VERSION.SDK_INT >= 34) {
      android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).let { assertTrue(it.hasGainmap()); it.recycle() }
    }
    val packed = JpegXl.pack(jpeg)!!
    assertTrue("${packed.size} against ${jpeg.size}", packed.size < jpeg.size)
    assertArrayEquals(jpeg, JpegXl.unpack(packed))

    val sourceRoot = File(context.cacheDir, "jxl-source").apply { deleteRecursively() }
    val restoredRoot = File(context.cacheDir, "jxl-restored").apply { deleteRecursively() }
    try {
      val source = Store(sourceRoot)
      File(source.photoDir("jxl"), "wall.jpg").writeBytes(jpeg)
      val hash = ProtocolIntegrity.fileHash(File(source.photoDir("jxl"), "wall.jpg"))
      source.save(Protocol(id = "jxl", kind = Kind.Entry, createdEpochMs = 9L,
        defects = listOf(Defect(id = "d1", photos = listOf("wall.jpg"), photoEvidence = listOf(PhotoEvidence("wall.jpg", hash))))))
      // Unencrypted, so the archive's entries can be read here.
      val backup = java.io.ByteArrayOutputStream().also { source.exportArchive(CharArray(0), it) }.toByteArray()
      val names = java.util.zip.ZipInputStream(backup.inputStream()).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
      assertTrue(names.toString(), "jpegxl/jxl/wall.jpg" in names && "photos/jxl/wall.jpg" !in names)
      val restored = Store(restoredRoot)
      assertEquals(1, restored.importArchive(CharArray(0), backup.inputStream()).imported)
      assertArrayEquals(jpeg, File(restored.photoDir("jxl"), "wall.jpg").readBytes())
    } finally {
      sourceRoot.deleteRecursively(); restoredRoot.deleteRecursively()
    }
  }

  @Test fun anEncryptedBackupLargerThanTheHeapSavesAndRestores() {
    // Android's GCM held a whole backup in memory, and a 68 MB one failed to restore for want of heap.
    val p = Protocol(id = "heavy", kind = Kind.Entry, createdEpochMs = 9L, defects = listOf(Defect(id = "d1", photos = listOf("wall.jpg"))))
    val sourceRoot = File(context.cacheDir, "heavy-source").apply { deleteRecursively() }
    val restoredRoot = File(context.cacheDir, "heavy-restored").apply { deleteRecursively() }
    val archive = File(context.cacheDir, "heavy.limen")
    val secret = "heavy password".toCharArray()
    try {
      val source = Store(sourceRoot).apply { save(p) }
      // Random bytes do not compress, so the archive outweighs the heap as the photograph does.
      val photo = File(source.photoDir(p.id), "wall.jpg")
      val random = java.util.Random(9L)
      photo.outputStream().buffered().use { out ->
        val chunk = ByteArray(1 shl 20)
        repeat((Runtime.getRuntime().maxMemory() shr 20).toInt() + 32) { random.nextBytes(chunk); out.write(chunk) }
      }
      archive.outputStream().buffered().use { source.exportArchive(secret, it) }
      assertTrue(archive.length() > Runtime.getRuntime().maxMemory())
      val restored = Store(restoredRoot)
      assertEquals(1, archive.inputStream().buffered().use { restored.importArchive(secret, it) }.imported)
      assertEquals(ProtocolIntegrity.fileHash(photo), ProtocolIntegrity.fileHash(File(restored.photoDir(p.id), "wall.jpg")))
    } finally {
      sourceRoot.deleteRecursively(); restoredRoot.deleteRecursively(); archive.delete()
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

  @Test fun theRecordIsDrawnInTheAppsPaletteAndFamilyOnEveryPage() {
    val p = sample().copy(finalizedEpochMs = 1)
    val frozen = p.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(p, store.photoDir(p.id), store.signatureDir(p.id)))
    val exported = PdfExport.export(context, frozen, Str(Lang.De), store.photoDir(p.id), store.signatureDir(p.id))
    val pdf = context.contentResolver.openInputStream(exported.uri)!!.use { it.readBytes() }
    val text = String(pdf, Charsets.ISO_8859_1)
    // The system sans-serif, which is what the app renders, and never a typewriter face. The
    // Pixel's Roboto is a variable font, which PdfDocument writes as Type3 glyph outlines whose
    // face is named by the descriptor's FontName; an embedded TrueType names it in BaseFont.
    val fonts = Regex("""/(?:BaseFont|FontName)\s*/([A-Za-z0-9+\-_,]+)""").findAll(text).map { it.groupValues[1] }.toList()
    assertTrue(fonts.toString(), fonts.isNotEmpty())
    assertTrue(fonts.toString(), fonts.none { it.contains("Mono", ignoreCase = true) || it.contains("Courier", ignoreCase = true) })
    assertTrue(fonts.toString(), fonts.any { it.contains("Roboto") })
    context.contentResolver.openFileDescriptor(exported.uri, "r")!!.use { fd -> android.graphics.pdf.PdfRenderer(fd).use { renderer ->
      assertTrue(renderer.pageCount >= 2)
      for (i in 0 until renderer.pageCount) renderer.openPage(i).use { page -> assertEquals(595, page.width); assertEquals(842, page.height) }
      // The cover: the landing's navy banner over white paper, the first panel in the app's plate grey.
      val cover = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
      renderer.openPage(0).use { it.render(cover, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
      fun near(pixel: Int, r: Int, g: Int, b: Int) =
        kotlin.math.abs(android.graphics.Color.red(pixel) - r) <= 6 && kotlin.math.abs(android.graphics.Color.green(pixel) - g) <= 6 && kotlin.math.abs(android.graphics.Color.blue(pixel) - b) <= 6
      assertTrue(Integer.toHexString(cover.getPixel(297, 130)), near(cover.getPixel(297, 130), 0x23, 0x33, 0x50))
      assertTrue(Integer.toHexString(cover.getPixel(528, 283)), near(cover.getPixel(528, 283), 0xE7, 0xE9, 0xEE))
      assertTrue(Integer.toHexString(cover.getPixel(10, 10)), near(cover.getPixel(10, 10), 0xFF, 0xFF, 0xFF))
      cover.recycle()
    } }
    context.contentResolver.openInputStream(exported.uri)!!.use { input -> File(output, "palette-de.pdf").outputStream().use(input::copyTo) }
  }

  @Test fun aRecordOfSixRoomsWithPhotographsExportsWithItsContentsOnThePagesItNames() {
    val base = sample()
    val rooms = (1..6).map { Room("room$it", RoomKind.entries[it % 6], name = "Room $it", inspected = true) }
    val defects = mutableListOf<Defect>()
    rooms.forEach { room ->
      (1..3).forEach { n ->
        val photos = (1..2).map { k ->
          val file = File(store.photoDir(base.id), "${room.id}-$n-$k.jpg")
          Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(40 * n, 90, 200 - 30 * k))
            file.outputStream().use { compress(Bitmap.CompressFormat.JPEG, 80, it) }
            recycle()
          }
          file
        }
        defects += Defect("${room.id}-d$n", room.id, ItemKey.Walls, "Finding $n in ${room.name}, described at some length so that the row wraps onto a second line",
          photos = photos.map { it.name }, photoEvidence = photos.map { PhotoEvidence(it.name, ProtocolIntegrity.fileHash(it)) },
          chargedToTenant = n == 1, fixBy = FixBy.Tenant, costNote = "CHF ${100 * n}")
      }
    }
    val p = base.copy(finalizedEpochMs = 1, rooms = rooms, defects = defects)
    val frozen = p.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(p, store.photoDir(p.id), store.signatureDir(p.id)))
    // The export itself refuses a document whose drawn sections or pages differ from the counted ones.
    val exported = PdfExport.export(context, frozen, Str(Lang.Fr), store.photoDir(p.id), store.signatureDir(p.id))
    context.contentResolver.openFileDescriptor(exported.uri, "r")!!.use { fd -> android.graphics.pdf.PdfRenderer(fd).use { renderer ->
      assertTrue("${renderer.pageCount} pages", renderer.pageCount >= 8)
      for (i in 0 until renderer.pageCount) renderer.openPage(i).use { page -> assertEquals(595, page.width); assertEquals(842, page.height) }
    } }
    context.contentResolver.openInputStream(exported.uri)!!.use { input -> File(output, "six-rooms-fr.pdf").outputStream().use(input::copyTo) }
    context.contentResolver.openInputStream(exported.companionUri!!)!!.use { input -> File(output, "six-rooms-fr.html").outputStream().use(input::copyTo) }
  }

  @Test fun theDocumentIsNamedAfterTheAddressAndTheHandoverDayAndNumberedOnARepeat() {
    // A street unique to this run, so an earlier run's copies in Download/Limen do not shift the numbering.
    val address = "Rue de l’Église ${System.nanoTime() % 1_000_000}"
    fun export(id: String): ExportedFile {
      val p = sample(id).copy(address = address, finalizedEpochMs = 1)
      val frozen = p.copy(evidenceFingerprint = ProtocolIntegrity.fingerprint(p, store.photoDir(p.id), store.signatureDir(p.id)))
      return PdfExport.export(context, frozen, Str(Lang.Fr), store.photoDir(p.id), store.signatureDir(p.id))
    }
    val base = "Rue-de-l-Eglise-${address.substringAfterLast(' ')}-2026-10-13"
    val first = export("name-${System.nanoTime()}")
    assertEquals("$base.pdf", first.displayName)
    assertEquals("$base.html", displayName(first.companionUri!!))
    // A second record at the same address on the same day: only Download/Limen holds the name,
    // since each record retains its own folder, so this proves the public folder is consulted.
    val second = export("name-${System.nanoTime()}")
    assertEquals("$base-2.pdf", second.displayName)
    assertEquals("$base-2.html", displayName(second.companionUri!!))
  }

  @Test fun removingAReadingOrADocumentOffersItBackAndUndoRestoresIt() {
    launch(sample().copy(details = RecordDetails(
      readings = listOf(MeterReading("m1", "Electricity", "Meter-42", "150.4", "kWh", "2026-10-13", "12:00")),
      attachments = listOf(SupportingDocument("a1", DocumentKind.Lease, "Lease supplied at inspection")),
    )))
    val en = Str(Lang.En)
    act { vm.removeReading("m1") }
    compose.onNodeWithText(en.removedForUndo(UndoKind.Reading)).assertIsDisplayed()
    act { assertTrue(vm.working!!.details.readings.isEmpty()) }
    compose.onNodeWithText(en.undo).performClick()
    act { assertEquals("Meter-42", vm.working!!.details.readings.single().identifier) }
    act { vm.removeAttachment("a1") }
    compose.onNodeWithText(en.removedForUndo(UndoKind.Document)).assertIsDisplayed()
    compose.onNodeWithText(en.undo).performClick()
    act { assertEquals("a1", vm.working!!.details.attachments.single().id) }
  }

  @Test fun aPhotographRemovedInsideTheOpenFindingEditorOffersItBack() {
    launch(sample().copy(defects = listOf(Defect(id = "d1", roomId = "r1", text = "Scratch beside the door", photos = listOf("wall.jpg")))))
    act { vm.editDefect("d1"); vm.removePhoto("d1", "wall.jpg") }
    compose.onNodeWithText(Str(Lang.En).undo).assertIsDisplayed().performClick()
    act { assertEquals(listOf("wall.jpg"), vm.working!!.defects.single().photos) }
  }

  private fun displayName(uri: android.net.Uri): String =
    context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Downloads.DISPLAY_NAME), null, null, null)!!.use { c -> c.moveToFirst(); c.getString(0) }
}
