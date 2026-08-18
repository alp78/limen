package dev.verso.limen

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
import dev.verso.limen.data.DossierCard
import dev.verso.limen.data.KeyTransferStatus
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Party
import dev.verso.limen.data.PdfExport
import dev.verso.limen.data.Plane
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.ProtocolIntegrity
import dev.verso.limen.data.Role
import dev.verso.limen.data.Room
import dev.verso.limen.data.RoomKind
import dev.verso.limen.data.Store
import dev.verso.limen.data.Signature
import dev.verso.limen.data.SwissCanton
import dev.verso.limen.ui.Lang
import dev.verso.limen.ui.Str
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

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
    compose.onNodeWithText("DATA, BACKUP & PRIVACY").performScrollTo().performClick()
    compose.onNodeWithText("Privacy information").assertExists()
    compose.onNodeWithText("Backup and restore").assertExists()
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithText("NEW SESSION").assertIsDisplayed()

    relaunchWithDraft()
    compose.onNodeWithText("INSPECTION").performClick()
    compose.onNodeWithText("Room").assertExists()
    compose.onNodeWithText("OBJECTS").performClick()
    compose.onAllNodesWithText("Add something else").assertCountEquals(2)
    compose.onNodeWithContentDescription("BACK").performClick()
    compose.onNodeWithText("DOSSIER").performClick()
    compose.onNodeWithText("Parties").assertExists()
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
      handoverDate = "2026-08-18",
      handoverTime = "10:00",
      parties = listOf(Party(Role.Tenant, "Test tenant"), Party(Role.Landlord, "Test landlord")),
      rooms = listOf(Room("r1", RoomKind.Room)),
      dossier = listOf(DossierCard.Keys),
      keyTransfer = KeyTransferStatus.NoPhysicalKeys,
    )
    Store(context.filesDir).save(record)
    context.getSharedPreferences("limen", 0).edit().putString("openProtocol", record.id).commit()
    scenario = ActivityScenario.launch(MainActivity::class.java)
  }

  private fun verifyPdfExport() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val store = Store(context.filesDir)
    val draft = Protocol(
      id = "pdf-smoke",
      kind = Kind.Exit,
      createdEpochMs = 2L,
      sessionRole = Role.Landlord,
      sessionName = "Test landlord",
      address = "Test street 1",
      place = "1000 Test",
      objectCanton = SwissCanton.VD,
      handoverDate = "2026-08-18",
      handoverTime = "10:00",
      parties = listOf(Party(Role.Tenant, "Test tenant"), Party(Role.Landlord, "Test landlord")),
      rooms = listOf(Room("r1", RoomKind.Room, inspected = true)),
      dossier = listOf(DossierCard.Keys),
      keyTransfer = KeyTransferStatus.NoPhysicalKeys,
      signatures = listOf(
        Signature(Role.Tenant, plane = Plane.Record, refused = true),
        Signature(Role.Landlord, plane = Plane.Record, refused = true),
      ),
      finalizedEpochMs = 3L,
    )
    val frozen = draft.copy(
      evidenceFingerprint = ProtocolIntegrity.fingerprint(
        draft,
        store.photoDir(draft.id),
        store.signatureDir(draft.id),
      )
    )
    val exported = PdfExport.export(
      context,
      frozen,
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
    } finally {
      context.contentResolver.delete(exported.uri, null, null)
    }
  }
}
