package dev.peringer.limen

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.peringer.limen.data.*
import dev.peringer.limen.ui.*
import org.hamcrest.Matcher
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/**
 * The eight store screenshots, once per language, in the order the listing shows them. The
 * September set was captured by hand over adb and could not be redone after a change to a
 * screen; this walks the same record on the device and writes the PNGs the listing takes.
 *
 * In upload order: 01 home, 02 rooms, 03 defect editor with its photograph, 04 canton
 * overlay, 05 review, 06 signature, 07 the record's cover in the preview, 08 an annex page
 * with a photograph. The status bar is the device's; tools/store-shots.ps1 puts it in demo
 * mode first and pulls the files afterwards.
 */
@RunWith(AndroidJUnit4::class)
class StoreShotsTest {
  @get:Rule val compose = createEmptyComposeRule()
  private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
  private val context get() = instrumentation.targetContext
  private val store get() = Store(context.filesDir)
  private var scenario: ActivityScenario<MainActivity>? = null
  private lateinit var vm: LimenViewModel

  /** Which listing slot this device fills, by its smallest width: the 10-inch slot is the tablet at density 240. */
  private val device: String
    get() {
      val sw = context.resources.configuration.smallestScreenWidthDp
      return when { sw >= 800 -> "tablet10"; sw >= 600 -> "tablet"; else -> "phone" }
    }
  private val prefix get() = if (device == "phone") "phone" else "tablet"

  @Before fun isolated() { check(context.packageName == "dev.peringer.limen.uitest") }
  @After fun close() { scenario?.close() }

  @Test fun storeShotsInEnglish() = walk(Lang.En)
  @Test fun storeShotsInFrench() = walk(Lang.Fr)
  @Test fun storeShotsInGerman() = walk(Lang.De)
  @Test fun storeShotsInItalian() = walk(Lang.It)
  @Test fun storeShotsInRomansh() = walk(Lang.Rm)

  /** The scene is complete to the last rule but the signatures, in every language, before any walk relies on it. */
  @Test fun sceneIsComplete() {
    Lang.entries.forEach { lang ->
      val root = File(context.cacheDir, "scene-${lang.name}").apply { deleteRecursively(); mkdirs() }
      val scratch = Store(root)
      val p = StoreScene.seed(scratch, lang, instrumentation.context.assets)
      assertEquals(listOf(RoomKind.Hall, RoomKind.LivingRoom, RoomKind.Kitchen, RoomKind.Bathroom, RoomKind.Bedroom), p.rooms.map { it.kind })
      assertEquals(3, p.defects.size)
      assertEquals(3, p.defects.map { it.roomId }.distinct().size)
      p.defects.forEach { d ->
        val evidence = d.photoEvidence.single()
        assertEquals(listOf(evidence.file), d.photos)
        assertEquals(PhotoSource.Camera, evidence.source)
        assertEquals(ProtocolIntegrity.fileHash(File(scratch.photoDir(p.id), evidence.file)), evidence.sha256)
      }
      assertTrue(lang.name, p.resolvedLegalOverlay()!!.isComplete(p.legalOverlay))
      assertEquals(
        lang.name,
        listOf(FinalizationIssue.RecordOutcomes),
        protocolCompletionIssues(p, listOf(Role.Tenant, Role.Landlord), scratch.photoDir(p.id), scratch.signatureDir(p.id)),
      )
      dev.peringer.limen.photo.EvidenceValidation.requireReadable(p, scratch.photoDir(p.id), scratch.signatureDir(p.id))
    }
  }

  private fun walk(lang: Lang) {
    val s = Str(lang)
    val scene = StoreScene.of(lang)
    context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    val p = StoreScene.seed(store, lang, instrumentation.context.assets)
    context.getSharedPreferences("limen", 0).edit().clear()
      .putString("lang", lang.name)
      .putString("myRole", "Landlord")
      .putString("profileName", scene.representative)
      .putString("profileCompany", scene.agency)
      .commit()
    val out = File(context.getExternalFilesDir(null), "store/$device/${scene.locale}").apply { deleteRecursively(); mkdirs() }
    scenario = ActivityScenario.launch(MainActivity::class.java)
    scenario!!.onActivity { vm = ViewModelProvider(it)[LimenViewModel::class.java] }
    compose.waitForIdle()

    // 01 the app's face, with the seeded record one key away.
    compose.onNodeWithText(s.startNewSession).assertIsDisplayed()
    shoot(out, 1, "home")

    act { vm.openProtocol(p.id) }
    compose.waitUntil(10_000) { vm.working?.id == p.id }

    // 02 the rooms, three of them carrying one defect each.
    act { vm.goTo(FlowStep.Inspection) }
    compose.waitUntil(10_000) { compose.onAllNodes(hasText(s.room(RoomKind.Kitchen), substring = true)).fetchSemanticsNodes().isNotEmpty() }
    shoot(out, 2, "rooms")

    // 03 the kitchen's defect with its photograph; the thumbnail decodes off the main thread.
    act { vm.openRoom("r3"); vm.editDefect("d1") }
    compose.waitUntil(20_000) { compose.onAllNodes(hasContentDescription(scene.kitchenStain, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    shoot(out, 3, "defect-editor")
    act { vm.closeDefect() }

    // 04 the canton overlay, its legal basis and its dated period.
    act { vm.goTo(FlowStep.Dossier) }
    awaitCount(hasText(s.legalOverlayTitle), 1)
    compose.onNodeWithText(s.legalOverlayTitle).performScrollTo()
    settleUnderStickyHeader(s.legalOverlayTitle)
    shoot(out, 4, "canton-overlay")

    // 05 the whole record before anyone signs it.
    act { vm.goTo(FlowStep.Review) }
    awaitCount(hasText(s.objectLabel), 1)
    shoot(out, 5, "review")

    // 06 the agency's representative signs first, so the pad's title carries the agency.
    act { vm.goTo(FlowStep.Sign) }
    awaitCount(hasText(s.signKey), 2)
    compose.onAllNodesWithText(s.signKey)[1].performScrollTo().performClick()
    awaitCount(hasContentDescription(s.signaturePad), 1)
    compose.onNodeWithContentDescription(s.signaturePad).performTouchInput { sign() }
    shoot(out, 6, "signature")
    compose.onNodeWithContentDescription(s.saveInk).performClick()
    awaitCount(hasText(s.signKey), 1)
    compose.onAllNodesWithText(s.signKey)[0].performScrollTo().performClick()
    awaitCount(hasContentDescription(s.signaturePad), 1)
    compose.onNodeWithContentDescription(s.signaturePad).performTouchInput { sign() }
    compose.onNodeWithContentDescription(s.saveInk).performClick()

    // The app finalizes: the enabled key proves the scene satisfies every completion rule.
    compose.waitUntil(10_000) { vm.completionIssues().isEmpty() }
    awaitCount(hasText(s.finalizeRecord) and hasClickAction(), 1)
    compose.onNode(hasText(s.finalizeRecord) and hasClickAction()).performScrollTo().assertIsEnabled().performClick()
    compose.waitUntil(30_000) { vm.working?.isFinalized() == true }

    // 07 the record's cover, as the preview shows the finalized document.
    act { vm.goTo(FlowStep.Sign) }
    awaitCount(hasText(s.preview) and hasClickAction(), 1)
    compose.onNode(hasText(s.preview) and hasClickAction()).performScrollTo().performClick()
    awaitPreview()
    shoot(out, 7, "record-cover")

    // 08 the first annex section with its photograph.
    scrollPreviewToAnnex()
    shoot(out, 8, "photo-annex")
    awaitCount(hasText(s.done), 1)
    compose.onNodeWithText(s.done).performClick()

    assertEquals(8, out.listFiles()!!.size)
  }

  private fun act(block: () -> Unit) { compose.runOnIdle(block); compose.waitForIdle() }

  /** At least [count] nodes match, waited for: the moment after a step change can leave no hierarchy to query. */
  private fun awaitCount(matcher: SemanticsMatcher, count: Int) {
    compose.waitUntil(15_000) { runCatching { compose.onAllNodes(matcher).fetchSemanticsNodes().size >= count }.getOrDefault(false) }
  }

  /** A signature rather than a scribble: three strokes that read as handwriting at listing size. */
  private fun TouchInjectionScope.sign() {
    val marks = listOf(0.14f to 0.62f, 0.34f to 0.34f, 0.30f to 0.58f, 0.52f to 0.40f, 0.48f to 0.44f, 0.82f to 0.52f)
    marks.chunked(2).forEach { (from, to) ->
      val start = androidx.compose.ui.geometry.Offset(width * from.first, height * from.second)
      val end = androidx.compose.ui.geometry.Offset(width * to.first, height * to.second)
      down(start)
      for (step in 1..12) {
        val t = step / 12f
        val wave = kotlin.math.sin(t * Math.PI.toFloat() * 2f) * height * 0.06f
        moveTo(androidx.compose.ui.geometry.Offset(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t + wave), 16)
      }
      up()
    }
  }

  /**
   * The dossier is a list under a sticky row of chips, and a node scrolled into view by the
   * least amount lands at the bottom edge. This moves the panel's title to just under the
   * chips, so the panel's whole body is in the frame.
   */
  private fun settleUnderStickyHeader(title: String) {
    val list = compose.onNode(hasScrollToIndexAction())
    val titleTop = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
    // The chips name the dossier's cards, ticked or not; the lowest of them is the header's edge.
    val chips = compose.onAllNodes(hasText("✓ ", substring = true) or hasText("○ ", substring = true)).fetchSemanticsNodes()
    val headerBottom = chips.filter { it.boundsInRoot.top < titleTop }.maxOfOrNull { it.boundsInRoot.bottom }
      ?: list.fetchSemanticsNode().boundsInRoot.top
    val delta = titleTop - headerBottom - with(compose.density) { 12.dp.toPx() }
    if (delta > 1f) {
      list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
      compose.waitForIdle()
    }
  }

  private fun webView(action: (WebView) -> Unit) {
    onView(isAssignableFrom(WebView::class.java)).inRoot(isDialog()).perform(object : ViewAction {
      override fun getConstraints(): Matcher<View> = isAssignableFrom(WebView::class.java)
      override fun getDescription() = "drive the preview"
      override fun perform(uiController: UiController, view: View) = action(view as WebView)
    })
  }

  /** The dialog shows an ellipsis while the HTML is built off the main thread; then the WebView loads it. */
  private fun awaitPreview() {
    compose.waitUntil(90_000) { compose.onAllNodesWithText("…").fetchSemanticsNodes().isEmpty() }
    var ready = false
    val deadline = SystemClock.uptimeMillis() + 60_000
    while (!ready && SystemClock.uptimeMillis() < deadline) {
      try { webView { ready = it.progress == 100 && it.contentHeight > 0 } } catch (_: NoMatchingViewException) { }
      if (!ready) SystemClock.sleep(300)
    }
    assertTrue("the preview did not load", ready)
    SystemClock.sleep(1500)
  }

  /**
   * The first photograph's section: the heading right before the first photograph's figure,
   * which the annex writes for each room. The preview loads with JavaScript off; it is switched on for
   * this one scroll, and the page reloaded with it on when the switch did not take.
   */
  private fun scrollPreviewToAnnex() {
    // The first JPEG figure: a signature is a PNG figure and comes earlier in the document.
    val js = "(function(){var i=document.querySelector('figure img[src^=\"data:image/jpeg\"]');if(!i)return 'none';" +
      "var f=i.closest('figure');var h=f.previousElementSibling;(h&&h.tagName==='H2'?h:f).scrollIntoView(true);return String(window.scrollY)})()"
    fun attempt(): String? {
      var result: String? = null
      webView { web ->
        web.settings.javaScriptEnabled = true
        web.evaluateJavascript(js) { result = it }
      }
      val deadline = SystemClock.uptimeMillis() + 10_000
      while (result == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
      return result
    }
    var scrolled = attempt()
    if (scrolled == null || scrolled == "null" || scrolled == "\"none\"" || scrolled == "\"0\"") {
      webView { it.reload() }
      awaitPreview()
      scrolled = attempt()
    }
    if (scrolled == null || scrolled == "null" || scrolled == "\"none\"" || scrolled == "\"0\"") {
      webView { it.pageDown(true) }
    }
    SystemClock.sleep(1500)
  }

  /** The whole screen, status bar included, as a 24-bit PNG of the device's own size. */
  private fun shoot(dir: File, index: Int, name: String) {
    compose.waitForIdle()
    SystemClock.sleep(900)
    val raw = instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
    val bitmap = if (raw.config == Bitmap.Config.HARDWARE) raw.copy(Bitmap.Config.ARGB_8888, false) else raw
    bitmap.setHasAlpha(false)
    val bounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
    assertEquals(bounds.width(), bitmap.width)
    assertEquals(bounds.height(), bitmap.height)
    val file = File(dir, "$prefix-%02d-%s.png".format(index, name))
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    assertTrue(file.length() > 0)
    if (bitmap !== raw) raw.recycle()
    bitmap.recycle()
  }
}
