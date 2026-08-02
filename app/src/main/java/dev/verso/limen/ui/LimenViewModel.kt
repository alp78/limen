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
import dev.verso.limen.data.DeliveryReceipt
import dev.verso.limen.data.Grade
import dev.verso.limen.data.GradeStyle
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.KeyCount
import dev.verso.limen.data.KeyKind
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Party
import dev.verso.limen.data.PhotoEvidence
import dev.verso.limen.data.PhotoSource
import dev.verso.limen.data.Plane
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.ProtocolIntegrity
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
private const val KEY_ROLE = "myRole"
private const val KEY_NAME = "profileName"

/**
 * How long the draft waits before it reaches the disk. Long enough that a burst of typing
 * writes once, short enough that it is over before a thumb reaches the next control.
 */
private const val SAVE_DELAY_MS = 400L

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

/** What still prevents a protocol from becoming the frozen record both parties reviewed. */
enum class FinalizationIssue {
  Address,
  Place,
  HandoverDate,
  HandoverTime,
  Parties,
  RoomChecks,
  DefectText,
  RecordOutcomes,
}

/** Why an export is withheld, rather than silently producing a misleading document. */
enum class ExportIssue { NotFinalized, IntegrityMismatch, Failed }

/**
 * Marked stable because every piece of state it publishes is Compose state: a reader is
 * always notified of a change, so a composable that captures this may be skipped when
 * nothing it reads has moved. Anything added here must keep that promise.
 */
@androidx.compose.runtime.Stable
class LimenViewModel(app: Application) : AndroidViewModel(app) {

  private val prefs = app.getSharedPreferences("limen", Context.MODE_PRIVATE)
  private val store = Store(app.filesDir)

  // ── Who holds this phone ─────────────────────────────────────────────────────────────

  /** The side of the threshold this install belongs to; null until the first-run answer. */
  var myRole by mutableStateOf(
    prefs.getString(KEY_ROLE, null)?.let { r -> runCatching { Role.valueOf(r) }.getOrNull() }
  )
    private set

  var profileName by mutableStateOf(prefs.getString(KEY_NAME, "") ?: "")
    private set

  fun chooseRole(role: Role, name: String) {
    myRole = role
    profileName = name.trim()
    prefs.edit().putString(KEY_ROLE, role.name).putString(KEY_NAME, profileName).apply()
  }

  fun updateProfileName(name: String) {
    profileName = name
    prefs.edit().putString(KEY_NAME, name).apply()
  }

  /** Forgets who holds the phone: back to the first question, nothing else touched. */
  fun clearProfile() {
    myRole = null
    profileName = ""
    prefs.edit().remove(KEY_ROLE).remove(KEY_NAME).apply()
  }


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

  /** The entry record shown beside an exit inspection, never copied into the mutable exit. */
  var entryBaseline by mutableStateOf<Protocol?>(null)
    private set

  var flowStep by mutableStateOf(FlowStep.Setup)
    private set

  /** Which room of the walk is open. */
  var roomIndex by mutableIntStateOf(0)
    private set

  /** The defect being edited, or null. A new defect is created on opening the editor. */
  var editingDefectId by mutableStateOf<String?>(null)
    private set

  var finalizing by mutableStateOf(false)
    private set

  var amendmentInProgress by mutableStateOf(false)
    private set

  var finalizationIssues by mutableStateOf(emptyList<FinalizationIssue>())
    private set

  var finalizationError by mutableStateOf(false)
    private set

  init {
    // A handover interrupted by the camera, a phone call or a flat battery reopens where
    // it stood: the draft is on disk after every edit, only the position needs restoring.
    prefs.getString(KEY_OPEN, null)?.let { id ->
      store.load(id)?.let {
        setActiveProtocol(it)
        flowStep = FlowStep.Setup
      }
    }
  }

  fun photoDir(): File? = working?.let { store.photoDir(it.id) }
  fun signatureDir(): File? = working?.let { store.signatureDir(it.id) }

  private fun setActiveProtocol(protocol: Protocol?) {
    working = protocol
    entryBaseline = protocol
      ?.takeIf { it.kind == Kind.Exit && it.seedId.isNotBlank() }
      ?.let { store.load(it.seedId) }
      ?.takeIf { it.isFinalized() }
  }

  /**
   * Every edit funnels through here. The draft in the hand changes at once; the draft on
   * disk follows a moment later, off this thread. Writing it inline made every keystroke
   * and every graded item wait on serializing the whole protocol and replacing a file,
   * which is what a slow key feels like. Nothing is risked: [flush] runs on every edge
   * that can end the process or read the file back.
   */
  fun edit(transform: (Protocol) -> Protocol) {
    val p = working ?: return
    if (p.isFinalized() || finalizing) return
    val edited = transform(p)
    if (edited == p) return
    working = edited.copy(contentRevision = p.contentRevision + 1, exportedEpochMs = 0)
    finalizationIssues = emptyList()
    finalizationError = false
    scheduleSave()
  }

  /** Ink records agreement with the current draft; it does not itself rewrite the draft. */
  private fun editSignatures(transform: (Protocol) -> Protocol) {
    val p = working ?: return
    if (p.isFinalized() || finalizing) return
    val edited = transform(p)
    if (edited == p) return
    working = edited
    finalizationIssues = emptyList()
    finalizationError = false
    scheduleSave()
  }

  /** Metadata captured after finalization, such as handing over an identical copy. */
  private fun editMetadata(transform: (Protocol) -> Protocol) {
    val p = working ?: return
    working = transform(p)
    scheduleSave()
  }

  private var saveJob: kotlinx.coroutines.Job? = null

  private fun scheduleSave() {
    saveJob?.cancel()
    saveJob = viewModelScope.launch {
      kotlinx.coroutines.delay(SAVE_DELAY_MS)
      val p = working ?: return@launch
      withContext(Dispatchers.IO) { store.save(p) }
    }
  }

  /**
   * The draft on disk, now. Called when the app leaves the foreground, when the flow
   * closes and before any protocol is read back, so the debounce above can never be the
   * reason a keystroke was lost. It writes on the calling thread on purpose: these are
   * lifecycle edges, not typing.
   */
  fun flush() {
    saveJob?.cancel()
    saveJob = null
    val p = working ?: return
    runCatching { store.save(p) }
  }

  override fun onCleared() {
    flush()
    super.onCleared()
  }

  fun createProtocol(kind: Kind) {
    flush()
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
      // The grading canon follows the region the language points at, so most people never
      // touch the setting: romand forms grade TB/B/M/O, the Deutschschweiz canon ticks
      // "in Ordnung" or writes a defect.
      style = if (lang == Lang.Fr) GradeStyle.Grades else GradeStyle.Binary,
    )
    store.save(p)
    setActiveProtocol(p)
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
    if (!entry.isFinalized()) return
    flush()
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
      amendedFromId = "",
      contentRevision = 0,
      finalizedEpochMs = 0,
      evidenceFingerprint = "",
      deliveryReceipts = emptyList(),
      exportedEpochMs = 0,
      meters = dev.verso.limen.data.Meters(),
    )
    store.save(p)
    setActiveProtocol(p)
    flowStep = FlowStep.Setup
    roomIndex = 0
    prefs.edit().putString(KEY_OPEN, p.id).apply()
  }

  fun openProtocol(id: String) {
    flush()
    val p = store.load(id) ?: return
    setActiveProtocol(p)
    flowStep = if (p.isFinalized()) FlowStep.Export else FlowStep.Setup
    roomIndex = 0
    prefs.edit().putString(KEY_OPEN, id).apply()
  }

  fun deleteProtocol(id: String) {
    store.delete(id)
    protocols = store.list()
  }

  fun closeFlow() {
    flush()
    setActiveProtocol(null)
    editingDefectId = null
    exported = null
    exportIssue = null
    finalizationIssues = emptyList()
    finalizationError = false
    prefs.edit().remove(KEY_OPEN).apply()
    protocols = store.list()
  }

  fun goTo(step: FlowStep) {
    if (working?.isFinalized() == true && step != FlowStep.Export) return
    flowStep = step
  }

  /** The people whose record outcome is required before this phone can freeze the record. */
  fun requiredRecordRoles(p: Protocol? = working): List<Role> {
    val record = p ?: return emptyList()
    return buildList {
      if (record.oneSided) {
        myRole?.let(::add)
      } else {
        add(Role.Tenant)
        add(Role.Landlord)
      }
      if (record.parties.any { it.role == Role.Witness }) add(Role.Witness)
      if (record.parties.any { it.role == Role.Expert }) add(Role.Expert)
    }.distinct()
  }

  fun completionIssues(p: Protocol? = working): List<FinalizationIssue> {
    val record = p ?: return emptyList()
    return buildList {
      if (record.address.isBlank()) add(FinalizationIssue.Address)
      if (record.place.isBlank()) add(FinalizationIssue.Place)
      if (record.handoverDate.isBlank()) add(FinalizationIssue.HandoverDate)
      if (record.handoverTime.isBlank()) add(FinalizationIssue.HandoverTime)
      if (listOf(Role.Tenant, Role.Landlord).any { role ->
          record.parties.firstOrNull { it.role == role }?.name.isNullOrBlank()
        }) add(FinalizationIssue.Parties)
      val flagged = record.defects.mapNotNull { it.item }.toSet()
      if (record.rooms.isEmpty() || record.rooms.any { room ->
          room.items.any { it.grade == Grade.NotChecked && it.key !in flagged }
        }) add(FinalizationIssue.RoomChecks)
      if (record.defects.any { it.text.isBlank() }) add(FinalizationIssue.DefectText)
      if (requiredRecordRoles(record).any { role ->
          record.signatures.none { signature ->
            signature.role == role && signature.plane == Plane.Record && record.isCurrent(signature)
          }
        }) add(FinalizationIssue.RecordOutcomes)
    }
  }

  fun finalizeProtocol() {
    val p = working ?: return
    if (p.isFinalized() || finalizing) return
    val issues = completionIssues(p)
    finalizationIssues = issues
    finalizationError = false
    if (issues.isNotEmpty()) return

    finalizing = true
    saveJob?.cancel()
    saveJob = null
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val withMoment = p.copy(
            signatures = p.signatures.filter(p::isCurrent),
            finalizedEpochMs = System.currentTimeMillis(),
          )
          val frozen = withMoment.copy(
            evidenceFingerprint = ProtocolIntegrity.fingerprint(
              withMoment,
              store.photoDir(withMoment.id),
              store.signatureDir(withMoment.id),
            ),
          )
          store.save(frozen)
          frozen
        }
      }
      finalizing = false
      result.onSuccess { frozen ->
        setActiveProtocol(frozen)
        finalizationIssues = emptyList()
        flowStep = FlowStep.Export
      }
      result.onFailure { finalizationError = true }
    }
  }

  /** Starts a new draft and copies the original photos; the finalized record stays intact. */
  fun createAmendment() {
    val p = working ?: return
    if (!p.isFinalized() || amendmentInProgress) return
    amendmentInProgress = true
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val now = System.currentTimeMillis()
          val draft = p.copy(
            id = "p$now",
            createdEpochMs = now,
            amendedFromId = p.id,
            contentRevision = 0,
            finalizedEpochMs = 0,
            evidenceFingerprint = "",
            deliveryReceipts = emptyList(),
            signatures = emptyList(),
            exportedEpochMs = 0,
          )
          store.photoDir(p.id).listFiles()?.forEach { source ->
            source.copyTo(File(store.photoDir(draft.id), source.name), overwrite = false)
          }
          store.save(draft)
          draft
        }
      }
      amendmentInProgress = false
      result.onSuccess { draft ->
        setActiveProtocol(draft)
        finalizationIssues = emptyList()
        flowStep = FlowStep.Review
        prefs.edit().putString(KEY_OPEN, draft.id).apply()
      }
    }
  }

  fun setCopyDelivered(role: Role, delivered: Boolean) {
    val p = working ?: return
    if (!p.isFinalized()) return
    editMetadata { current ->
      val existing = current.deliveryReceipts.filterNot { it.role == role }
      current.copy(
        deliveryReceipts = if (delivered) {
          existing + DeliveryReceipt(role = role, epochMs = System.currentTimeMillis())
        } else existing,
      )
    }
  }

  /** Back inside the flow: one station up, or out to the case from the first. */
  fun flowBack() {
    if (editingDefectId != null) {
      editingDefectId = null
      return
    }
    if (working?.isFinalized() == true) {
      closeFlow()
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
    val s = Str(lang)
    val room = working?.rooms?.firstOrNull { it.id == roomId }
    val item = room?.items?.getOrNull(index)
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
    val gone = working?.rooms?.firstOrNull { it.id == roomId }
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
    if (working?.isFinalized() == true) return
    val id = "d${System.currentTimeMillis()}"
    edit { p -> p.copy(defects = p.defects + Defect(id = id, roomId = roomId, item = item)) }
    editingDefectId = id
  }

  fun editDefect(id: String) {
    if (working?.isFinalized() == true) return
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
    } else if (d != null) {
    }
    editingDefectId = null
  }

  fun removeDefect(id: String) {
    if (working?.isFinalized() == true) return
    val dir = photoDir()
    val gone = working?.defects?.firstOrNull { it.id == id }
    gone?.photos?.forEach { name ->
      if (dir != null) File(dir, name).delete()
    }
    edit { p -> p.copy(defects = p.defects.filterNot { it.id == id }) }
    if (editingDefectId == id) editingDefectId = null
    if (gone != null && (gone.text.isNotBlank() || gone.photos.isNotEmpty())) {
    }
  }

  private fun defectWhere(d: Defect): String {
    val s = Str(lang)
    val room = working?.rooms?.firstOrNull { it.id == d.roomId }
    return listOfNotNull(
      room?.let { it.name.ifBlank { s.room(it.kind) } },
      d.item?.let { s.item(it) },
    ).joinToString(" · ")
  }

  /** A photo the camera or picker delivered, preserved byte-for-byte beside the protocol. */
  fun attachPhoto(defectId: String, source: File, photoSource: PhotoSource = PhotoSource.Unknown) {
    if (working?.isFinalized() == true) return
    val dir = photoDir() ?: return
    val count = working?.defects?.firstOrNull { it.id == defectId }?.photos?.size ?: 0
    val name = "$defectId-${System.currentTimeMillis()}-${count + 1}.jpg"
    val target = File(dir, name)
    runCatching { source.copyTo(target, overwrite = true) }.getOrElse { return }
    val evidence = PhotoEvidence(
      file = name,
      sha256 = ProtocolIntegrity.fileHash(target),
      capturedEpochMs = System.currentTimeMillis(),
      source = photoSource,
    )
    source.delete()
    updateDefect(defectId) {
      it.copy(
        photos = it.photos + name,
        photoEvidence = it.photoEvidence.filterNot { photo -> photo.file == name } + evidence,
      )
    }
  }

  fun removePhoto(defectId: String, name: String) {
    if (working?.isFinalized() == true) return
    photoDir()?.let { File(it, name).delete() }
    updateDefect(defectId) {
      it.copy(
        photos = it.photos - name,
        photoEvidence = it.photoEvidence.filterNot { photo -> photo.file == name },
      )
    }
  }

  // ── Signatures ───────────────────────────────────────────────────────────────────────

  /** Keeps the ink: the PNG lands beside the protocol, the record points at it. */
  fun keepSignature(role: Role, plane: Plane, bitmap: android.graphics.Bitmap) {
    if (working?.isFinalized() == true) return
    val dir = signatureDir() ?: return
    val name = "${role.name.lowercase()}-${plane.name.lowercase()}.png"
    File(dir, name).outputStream().use {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
    val partyName = working?.parties?.firstOrNull { it.role == role }?.name.orEmpty()
    editSignatures { p ->
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane } +
          dev.verso.limen.data.Signature(
            role = role,
            name = partyName,
            plane = plane,
            file = name,
            epochMs = System.currentTimeMillis(),
            contentRevision = p.contentRevision,
          )
      )
    }
  }

  /** Refusal is an outcome the record keeps, not an error it recovers from. */
  fun refuseSignature(role: Role, plane: Plane) {
    if (working?.isFinalized() == true) return
    val existing = working?.signatures?.firstOrNull { it.role == role && it.plane == plane }
    if (existing != null && existing.file.isNotBlank()) {
      signatureDir()?.let { File(it, existing.file).delete() }
    }
    val partyName = working?.parties?.firstOrNull { it.role == role }?.name.orEmpty()
    editSignatures { p ->
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane } +
          dev.verso.limen.data.Signature(
            role = role,
            name = partyName,
            plane = plane,
            refused = true,
            epochMs = System.currentTimeMillis(),
            contentRevision = p.contentRevision,
          )
      )
    }
  }

  fun clearSignature(role: Role, plane: Plane) {
    if (working?.isFinalized() == true) return
    val existing = working?.signatures?.firstOrNull { it.role == role && it.plane == plane }
    if (existing != null && existing.file.isNotBlank()) {
      signatureDir()?.let { File(it, existing.file).delete() }
    }
    editSignatures { p ->
      p.copy(signatures = p.signatures.filterNot { it.role == role && it.plane == plane })
    }
  }

  fun setSignatureReservation(role: Role, plane: Plane, text: String) {
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

  var exportIssue by mutableStateOf<ExportIssue?>(null)
    private set

  /** The document's language, chosen at export and free of the UI language. */
  var pdfLang by mutableStateOf<Lang?>(null)
    private set

  fun selectPdfLang(l: Lang) {
    pdfLang = l
  }

  fun exportPdf() {
    val p = working ?: return
    if (!p.isFinalized()) {
      exportIssue = ExportIssue.NotFinalized
      return
    }
    if (exporting) return
    exporting = true
    exportIssue = null
    exported = null
    val docLang = pdfLang ?: lang
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        if (!ProtocolIntegrity.matches(p, store.photoDir(p.id), store.signatureDir(p.id))) null
        else runCatching {
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
      if (result == null) {
        exportIssue = ExportIssue.IntegrityMismatch
      } else {
        result.fold(
          onSuccess = { out ->
            exported = out
            editMetadata { q -> q.copy(exportedEpochMs = System.currentTimeMillis()) }
          },
          onFailure = {
            exportIssue = ExportIssue.Failed
          },
        )
      }
    }
  }

  /** The letter asking a neutral official to record the facts, for the dispute that came. */
  fun exportBefund(commune: String) {
    val p = working ?: return
    if (!p.isFinalized()) {
      exportIssue = ExportIssue.NotFinalized
      return
    }
    if (exporting) return
    exporting = true
    exportIssue = null
    val docLang = pdfLang ?: lang
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        if (!ProtocolIntegrity.matches(p, store.photoDir(p.id), store.signatureDir(p.id))) null
        else runCatching {
          dev.verso.limen.data.Befund.export(getApplication(), p, Str(docLang), commune)
        }
      }
      exporting = false
      if (result == null) {
        exportIssue = ExportIssue.IntegrityMismatch
      } else {
        result.fold(
          onSuccess = { exported = it },
          onFailure = {
            exportIssue = ExportIssue.Failed
          },
        )
      }
    }
  }

  // ── The deposit comparison ───────────────────────────────────────────────────────────

  var showDepositCompare by mutableStateOf(false)
    private set

  fun setDepositCompare(open: Boolean) {
    showDepositCompare = open
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
