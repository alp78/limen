package dev.verso.limen.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.verso.limen.data.Defect
import dev.verso.limen.data.Grade
import dev.verso.limen.data.GradeStyle
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.KeyCount
import dev.verso.limen.data.KeyKind
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Party
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.Role
import dev.verso.limen.data.Room
import dev.verso.limen.data.RoomItem
import dev.verso.limen.data.RoomKind
import dev.verso.limen.data.Store
import dev.verso.limen.data.Templates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private const val KEY_DARK = "darkTheme"
private const val KEY_LANG = "lang"
private const val KEY_OPEN = "openProtocol"

/**
 * Where the instrument is. [Home] is the case: the list of protocols and the way into a
 * new one. [Value] is the Zeitwert calculator, [Clocks] the legal deadlines. The protocol
 * flow is not a face: it opens over whatever face is current and owns the screen until
 * it is closed, and it is restored after a process death, because the camera app kills
 * this process routinely mid-handover.
 */
enum class AppMode { Home, Value, Clocks }

/** The stepper's stations, in walking order. */
enum class FlowStep { Setup, Rooms, Review, Sign, Export }

class LimenViewModel(app: Application) : AndroidViewModel(app) {

  private val prefs = app.getSharedPreferences("limen", Context.MODE_PRIVATE)
  private val store = Store(app.filesDir)

  var darkTheme by mutableStateOf(prefs.getBoolean(KEY_DARK, false))
    private set

  var appMode by mutableStateOf(AppMode.Home)
    private set

  /** The language the instrument speaks; first run follows the system, then it is a choice. */
  var lang by mutableStateOf(Lang.from(prefs.getString(KEY_LANG, null)))
    private set

  // ── The case ─────────────────────────────────────────────────────────────────────────

  var protocols by mutableStateOf(store.list())
    private set

  // ── The flow ─────────────────────────────────────────────────────────────────────────

  /** The protocol being walked, or null when the case is showing. Saved on every edit. */
  var working by mutableStateOf<Protocol?>(null)
    private set

  var flowStep by mutableStateOf(FlowStep.Setup)
    private set

  /** Which room of the walk is open. */
  var roomIndex by mutableIntStateOf(0)
    private set

  /** The defect being edited, or null. A new defect is created on opening the editor. */
  var editingDefectId by mutableStateOf<String?>(null)
    private set

  init {
    // A handover interrupted by the camera, a phone call or a flat battery reopens where
    // it stood: the draft is on disk after every edit, only the position needs restoring.
    prefs.getString(KEY_OPEN, null)?.let { id ->
      store.load(id)?.let {
        working = it
        flowStep = FlowStep.Setup
      }
    }
  }

  fun photoDir(): File? = working?.let { store.photoDir(it.id) }
  fun signatureDir(): File? = working?.let { store.signatureDir(it.id) }

  /** Every edit funnels through here: state and disk stay one thing. */
  fun edit(transform: (Protocol) -> Protocol) {
    val p = working ?: return
    val next = transform(p)
    working = next
    store.save(next)
  }

  fun createProtocol(kind: Kind) {
    val now = System.currentTimeMillis()
    val rooms = Templates.defaultRooms.mapIndexed { i, rk ->
      Room(id = "r$i", kind = rk, items = Templates.itemsFor(rk).map { RoomItem(it) })
    }
    val p = Protocol(
      id = "p$now",
      kind = kind,
      createdEpochMs = now,
      handoverDate = LocalDate.now().toString(),
      parties = listOf(Party(Role.Tenant), Party(Role.Landlord)),
      keys = listOf(
        KeyCount(KeyKind.Apartment),
        KeyCount(KeyKind.HouseDoor),
        KeyCount(KeyKind.Mailbox),
        KeyCount(KeyKind.Cellar),
      ),
      rooms = rooms,
    )
    store.save(p)
    working = p
    flowStep = FlowStep.Setup
    roomIndex = 0
    prefs.edit().putString(KEY_OPEN, p.id).apply()
  }

  /**
   * The exit protocol grown from the stored entry one: the flat's structure, the parties,
   * the deposit and the flags of what was new at entry carry over; the grades and defects
   * do not, because the walk is about to find out.
   */
  fun seedExitFrom(entry: Protocol) {
    val now = System.currentTimeMillis()
    val p = entry.copy(
      id = "p$now",
      kind = Kind.Exit,
      createdEpochMs = now,
      handoverDate = LocalDate.now().toString(),
      handoverTime = "",
      lastProtocolDate = entry.handoverDate,
      rooms = entry.rooms.map { r -> r.copy(items = r.items.map { it.copy(grade = Grade.NotChecked) }) },
      defects = emptyList(),
      remarks = "",
      chargesStatementBy = "",
      oneSided = false,
      signatures = emptyList(),
      seedId = entry.id,
      exportedEpochMs = 0,
      meters = dev.verso.limen.data.Meters(),
    )
    store.save(p)
    working = p
    flowStep = FlowStep.Setup
    roomIndex = 0
    prefs.edit().putString(KEY_OPEN, p.id).apply()
  }

  fun openProtocol(id: String) {
    val p = store.load(id) ?: return
    working = p
    flowStep = if (p.isSigned() || p.isExported()) FlowStep.Export else FlowStep.Setup
    roomIndex = 0
    prefs.edit().putString(KEY_OPEN, id).apply()
  }

  fun deleteProtocol(id: String) {
    store.delete(id)
    protocols = store.list()
  }

  fun closeFlow() {
    working = null
    editingDefectId = null
    exported = null
    exportError = false
    prefs.edit().remove(KEY_OPEN).apply()
    protocols = store.list()
  }

  fun goTo(step: FlowStep) {
    flowStep = step
  }

  /** Back inside the flow: one station up, or out to the case from the first. */
  fun flowBack() {
    if (editingDefectId != null) {
      editingDefectId = null
      return
    }
    when (flowStep) {
      FlowStep.Setup -> closeFlow()
      FlowStep.Rooms -> flowStep = FlowStep.Setup
      FlowStep.Review -> flowStep = FlowStep.Rooms
      FlowStep.Sign -> flowStep = FlowStep.Review
      FlowStep.Export -> flowStep = FlowStep.Sign
    }
  }

  fun selectRoom(index: Int) {
    roomIndex = index.coerceIn(0, (working?.rooms?.size ?: 1) - 1)
  }

  /**
   * One tap on an item row: binary style toggles the tick, the romand style walks the
   * grade ladder. NotChecked stays the honest resting state either way.
   */
  fun cycleGrade(roomId: String, index: Int) {
    edit { p ->
      p.copy(rooms = p.rooms.map { r ->
        if (r.id != roomId) r
        else r.copy(items = r.items.mapIndexed { i, item ->
          if (i != index) item
          else item.copy(
            grade = when (p.style) {
              GradeStyle.Binary ->
                if (item.grade == Grade.Ok) Grade.NotChecked else Grade.Ok
              GradeStyle.Grades -> when (item.grade) {
                Grade.NotChecked -> Grade.VeryGood
                Grade.VeryGood -> Grade.Good
                Grade.Good -> Grade.Fair
                Grade.Fair -> Grade.Poor
                else -> Grade.NotChecked
              }
            }
          )
        })
      })
    }
  }

  fun addRoom(kind: RoomKind, name: String = "") {
    edit { p ->
      val id = "r${System.currentTimeMillis()}"
      p.copy(rooms = p.rooms + Room(
        id = id, kind = kind, name = name,
        items = Templates.itemsFor(kind).map { RoomItem(it) },
      ))
    }
    roomIndex = (working?.rooms?.size ?: 1) - 1
  }

  fun removeRoom(roomId: String) {
    edit { p ->
      p.copy(
        rooms = p.rooms.filterNot { it.id == roomId },
        defects = p.defects.filterNot { it.roomId == roomId },
      )
    }
    roomIndex = roomIndex.coerceIn(0, ((working?.rooms?.size ?: 1) - 1).coerceAtLeast(0))
  }

  // ── Defects ──────────────────────────────────────────────────────────────────────────

  fun openDefect(roomId: String, item: ItemKey?) {
    val id = "d${System.currentTimeMillis()}"
    edit { p -> p.copy(defects = p.defects + Defect(id = id, roomId = roomId, item = item)) }
    editingDefectId = id
  }

  fun editDefect(id: String) {
    editingDefectId = id
  }

  fun updateDefect(id: String, transform: (Defect) -> Defect) {
    edit { p -> p.copy(defects = p.defects.map { if (it.id == id) transform(it) else it }) }
  }

  /** Closing the editor discards an empty shell rather than keeping a blank finding. */
  fun closeDefect() {
    val id = editingDefectId ?: return
    val d = working?.defects?.firstOrNull { it.id == id }
    if (d != null && d.text.isBlank() && d.photos.isEmpty()) {
      removeDefect(id)
    }
    editingDefectId = null
  }

  fun removeDefect(id: String) {
    val dir = photoDir()
    working?.defects?.firstOrNull { it.id == id }?.photos?.forEach { name ->
      if (dir != null) File(dir, name).delete()
    }
    edit { p -> p.copy(defects = p.defects.filterNot { it.id == id }) }
    if (editingDefectId == id) editingDefectId = null
  }

  /** A photo the camera or picker delivered, moved from the cache into the protocol. */
  fun attachPhoto(defectId: String, source: File) {
    val dir = photoDir() ?: return
    val count = working?.defects?.firstOrNull { it.id == defectId }?.photos?.size ?: 0
    val name = "$defectId-${count + 1}.jpg"
    source.copyTo(File(dir, name), overwrite = true)
    source.delete()
    updateDefect(defectId) { it.copy(photos = it.photos + name) }
  }

  fun removePhoto(defectId: String, name: String) {
    photoDir()?.let { File(it, name).delete() }
    updateDefect(defectId) { it.copy(photos = it.photos - name) }
  }

  // ── Signatures ───────────────────────────────────────────────────────────────────────

  /** Keeps the ink: the PNG lands beside the protocol, the record points at it. */
  fun keepSignature(role: Role, plane: dev.verso.limen.data.Plane, bitmap: android.graphics.Bitmap) {
    val dir = signatureDir() ?: return
    val name = "${role.name.lowercase()}-${plane.name.lowercase()}.png"
    File(dir, name).outputStream().use {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
    val partyName = working?.parties?.firstOrNull { it.role == role }?.name.orEmpty()
    edit { p ->
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane } +
          dev.verso.limen.data.Signature(
            role = role,
            name = partyName,
            plane = plane,
            file = name,
            epochMs = System.currentTimeMillis(),
          )
      )
    }
  }

  /** Refusal is an outcome the record keeps, not an error it recovers from. */
  fun refuseSignature(role: Role, plane: dev.verso.limen.data.Plane) {
    val partyName = working?.parties?.firstOrNull { it.role == role }?.name.orEmpty()
    edit { p ->
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane } +
          dev.verso.limen.data.Signature(
            role = role,
            name = partyName,
            plane = plane,
            refused = true,
            epochMs = System.currentTimeMillis(),
          )
      )
    }
  }

  fun clearSignature(role: Role, plane: dev.verso.limen.data.Plane) {
    val existing = working?.signatures?.firstOrNull { it.role == role && it.plane == plane }
    if (existing != null && existing.file.isNotBlank()) {
      signatureDir()?.let { File(it, existing.file).delete() }
    }
    edit { p ->
      p.copy(signatures = p.signatures.filterNot { it.role == role && it.plane == plane })
    }
  }

  fun setSignatureReservation(role: Role, plane: dev.verso.limen.data.Plane, text: String) {
    edit { p ->
      p.copy(signatures = p.signatures.map {
        if (it.role == role && it.plane == plane) it.copy(reservation = text) else it
      })
    }
  }

  // ── Export ───────────────────────────────────────────────────────────────────────────

  var exporting by mutableStateOf(false)
    private set

  var exported by mutableStateOf<dev.verso.limen.data.ExportedFile?>(null)
    private set

  var exportError by mutableStateOf(false)
    private set

  /** The document's language, chosen at export and free of the UI language. */
  var pdfLang by mutableStateOf<Lang?>(null)
    private set

  fun selectPdfLang(l: Lang) {
    pdfLang = l
  }

  fun exportPdf() {
    val p = working ?: return
    if (exporting) return
    exporting = true
    exportError = false
    exported = null
    val docLang = pdfLang ?: lang
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          dev.verso.limen.data.PdfExport.export(
            getApplication(),
            p,
            Str(docLang),
            store.photoDir(p.id),
            store.signatureDir(p.id),
          )
        }
      }
      exporting = false
      result.fold(
        onSuccess = {
          exported = it
          edit { q -> q.copy(exportedEpochMs = System.currentTimeMillis()) }
        },
        onFailure = { exportError = true },
      )
    }
  }

  // ── Controls ─────────────────────────────────────────────────────────────────────────

  fun selectMode(mode: AppMode) {
    appMode = mode
  }

  fun selectLang(l: Lang) {
    lang = l
    prefs.edit().putString(KEY_LANG, l.name).apply()
  }

  fun toggleTheme() {
    darkTheme = !darkTheme
    prefs.edit().putBoolean(KEY_DARK, darkTheme).apply()
  }
}
