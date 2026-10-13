package dev.peringer.limen.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.peringer.limen.data.Contract
import dev.peringer.limen.data.ControllerIdentity
import dev.peringer.limen.data.ArchiveImportResult
import dev.peringer.limen.data.Defect
import dev.peringer.limen.data.isUntouched
import dev.peringer.limen.data.Deposit
import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.ItemKey
import dev.peringer.limen.data.KeyCount
import dev.peringer.limen.data.KeyKind
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.Meters
import dev.peringer.limen.data.ObjectEntry
import dev.peringer.limen.data.ObjectGroup
import dev.peringer.limen.data.Party
import dev.peringer.limen.data.PhotoEvidence
import dev.peringer.limen.data.PhotoSource
import dev.peringer.limen.data.Plane
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.ProtocolIntegrity
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.Room
import dev.peringer.limen.data.RoomKind
import dev.peringer.limen.data.SessionIdentity
import dev.peringer.limen.data.Store
import dev.peringer.limen.data.dossierCards
import dev.peringer.limen.data.hasRequiredFields
import dev.peringer.limen.data.photoFiles
import dev.peringer.limen.data.exportStamp
import dev.peringer.limen.data.writeToDownloads
import dev.peringer.limen.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private const val KEY_DARK = "darkTheme"
private const val KEY_LANG = "lang"
private const val KEY_OPEN = "openProtocol"
private const val KEY_STEP = "openStep"
private const val KEY_ROOM = "openRoom"
private const val KEY_DEFECT = "openDefect"
private const val KEY_ROLE = "myRole"
private const val KEY_NAME = "profileName"
private const val KEY_COMPANY = "profileCompany"
private const val KEY_CONTROLLER_ORGANISATION = "controllerOrganisation"
private const val KEY_CONTROLLER_CONTACT = "controllerContact"
private const val KEY_CONTROLLER_STREET = "controllerStreet"
private const val KEY_CONTROLLER_POSTAL = "controllerPostalCode"
private const val KEY_CONTROLLER_CITY = "controllerCity"
private const val KEY_CONTROLLER_EMAIL = "controllerEmail"
private const val KEY_CONTROLLER_PHONE = "controllerPhone"

/**
 * How long the draft waits before it reaches the disk. Long enough that a burst of typing
 * writes once, short enough that it is over before a thumb reaches the next control.
 */
private const val SAVE_DELAY_MS = 400L

/** How far back the flow can retrace. Deep enough for any real walk, bounded so it cannot grow. */
private const val MAX_TRAIL = 12

/**
 * The protocol flow opens from the case and owns the screen until it is closed. It is restored
 * after a process death because the camera app can interrupt a handover mid-walk.
 *
 * Not a stepper. A handover is walked in the order the flat is built, not the order an app
 * imagines, so [Hub] is the home everything returns to and nothing is gated behind anything
 * else. [Sign] and [Export] are the exception: they follow [Review] because a signature that
 * saw no review is worth nothing.
 */
enum class FlowStep {
  Hub,
  Inspection,
  Objects,
  Dossier,
  Review,
  Sign,
  Export;

  /** Where back goes from here. Null leaves the flow entirely. */
  val parent: FlowStep?
    get() = when (this) {
      Hub -> null
      Inspection, Objects, Dossier, Review -> Hub
      Sign -> Review
      Export -> Sign
    }
}

/** What still prevents a protocol from becoming the frozen record both parties reviewed. */
enum class FinalizationIssue {
  Address,
  Place,
  Canton,
  HandoverDate,
  HandoverTime,
  LegalOverlay,
  Parties,
  NoRooms,
  Rooms,
  Objects,
  Dossier,
  Keys,
  UnilateralReason,
  DefectText,
  Evidence,
  RecordOutcomes,
}

/** Why an export is withheld, rather than silently producing a misleading document. */
enum class ExportIssue { NotFinalized, IntegrityMismatch, Failed }

enum class UndoKind { Defect, Photo, Object, KeyCounts }

data class UndoNotice(val token: Long, val kind: UndoKind)

private data class PendingUndo(
  val token: Long,
  val before: Protocol,
  val afterRevision: Long,
  val files: List<File>,
)

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

  /** The agency's firm name when this phone belongs to a Verwaltung; blank for private actors. */
  var profileCompany by mutableStateOf(prefs.getString(KEY_COMPANY, "") ?: "")
    private set

  var profileIdentity by mutableStateOf(runCatching {
    kotlinx.serialization.json.Json.decodeFromString<SessionIdentity>(prefs.getString("profileIdentity", "") ?: "")
  }.getOrDefault(SessionIdentity()))
    private set
  var onlineAddresses by mutableStateOf(prefs.getBoolean("onlineAddresses", false))
    private set
  var libraryProtected by mutableStateOf(prefs.getBoolean("libraryProtected", false))
    private set
  fun protectLibrary(enabled: Boolean) {
    libraryProtected = enabled
    prefs.edit().putBoolean("libraryProtected", enabled).apply()
  }
  fun chooseOnlineAddresses(enabled: Boolean) {
    onlineAddresses = enabled
    prefs.edit().putBoolean("onlineAddresses", enabled).apply()
  }
  private fun saveProfile(identity: SessionIdentity) {
    profileIdentity = identity
    prefs.edit().putString("profileIdentity", kotlinx.serialization.json.Json.encodeToString(SessionIdentity.serializer(), identity)).apply()
  }

  var controllerIdentity by mutableStateOf(
    ControllerIdentity(
      organisation = prefs.getString(KEY_CONTROLLER_ORGANISATION, "") ?: "",
      contactName = prefs.getString(KEY_CONTROLLER_CONTACT, "") ?: "",
      street = prefs.getString(KEY_CONTROLLER_STREET, "") ?: "",
      postalCode = prefs.getString(KEY_CONTROLLER_POSTAL, "") ?: "",
      city = prefs.getString(KEY_CONTROLLER_CITY, "") ?: "",
      email = prefs.getString(KEY_CONTROLLER_EMAIL, "") ?: "",
      phone = prefs.getString(KEY_CONTROLLER_PHONE, "") ?: "",
    )
  )
    private set

  fun updateController(identity: ControllerIdentity) {
    controllerIdentity = identity
    prefs.edit()
      .putString(KEY_CONTROLLER_ORGANISATION, identity.organisation)
      .putString(KEY_CONTROLLER_CONTACT, identity.contactName)
      .putString(KEY_CONTROLLER_STREET, identity.street)
      .putString(KEY_CONTROLLER_POSTAL, identity.postalCode)
      .putString(KEY_CONTROLLER_CITY, identity.city)
      .putString(KEY_CONTROLLER_EMAIL, identity.email)
      .putString(KEY_CONTROLLER_PHONE, identity.phone)
      .apply()
  }

  fun chooseRole(role: Role, name: String, company: String = "") {
    myRole = role
    profileName = name.trim()
    profileCompany = company.trim()
    prefs.edit().putString(KEY_ROLE, role.name).putString(KEY_NAME, profileName)
      .putString(KEY_COMPANY, profileCompany).apply()
  }

  /** Forgets who holds the phone: back to the first question, nothing else touched. */
  fun clearProfile() {
    myRole = null
    profileName = ""
    profileCompany = ""
    profileIdentity = SessionIdentity()
    prefs.edit().remove("profileIdentity").remove(KEY_ROLE).remove(KEY_NAME).remove(KEY_COMPANY).apply()
  }


  var darkTheme by mutableStateOf(prefs.getBoolean(KEY_DARK, false))
    private set

  /** The language the instrument speaks; first run follows the system, then it is a choice. */
  var lang by mutableStateOf(Lang.from(prefs.getString(KEY_LANG, null)))
    private set

  // ── The case ─────────────────────────────────────────────────────────────────────────

  var protocols by mutableStateOf(emptyList<Protocol>())
    private set

  var unreadableRecords by mutableStateOf(emptyList<Store.UnreadableRecord>())
    private set
  var saveState by mutableStateOf("saved")
    private set
  var legalChecksReset by mutableStateOf(false)
    private set
  var evidenceError by mutableStateOf("")
    private set
  var evidenceBusy by mutableIntStateOf(0)
    private set
  var affectedSigners by mutableStateOf(emptyList<String>())
    private set
  private var pendingSignedEdit: (() -> Unit)? = null
  private var acceptingSignedEdit = false
  fun confirmSignedEdit(accept: Boolean) {
    val action = pendingSignedEdit
    pendingSignedEdit = null
    affectedSigners = emptyList()
    if (accept) {
      acceptingSignedEdit = true
      try { action?.invoke() } finally { acceptingSignedEdit = false }
    }
  }
  private fun confirmBeforeSignedEdit(p: Protocol, action: () -> Unit): Boolean {
    val signers = p.signatures.filter { p.isCurrent(it) }
    if (acceptingSignedEdit || signers.isEmpty()) return false
    pendingSignedEdit = action
    affectedSigners = signers.map { it.name.ifBlank { Str(lang).role(it.role) } }
    return true
  }
  private val sequence = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
  private fun nextSequence(): Long = sequence.incrementAndGet()

  fun refreshLibrary() {
    viewModelScope.launch {
      val catalogue = withContext(Dispatchers.IO) { store.catalogue() }
      protocols = catalogue.records
      unreadableRecords = catalogue.unreadable
    }
  }

  fun recoverRecord(id: String) {
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) { runCatching { store.recover(id) } }
      saveState = if (result.getOrDefault(false)) "saved" else "failed"
      refreshLibrary()
    }
  }

  var archiveBusy by mutableStateOf(false)
    private set

  var archiveExported by mutableStateOf<dev.peringer.limen.data.ExportedFile?>(null)
    private set
  /** Whether the last export carried a password, so the screen can say when it did not. */
  var archiveProtected by mutableStateOf(true)
    private set

  var archiveImported by mutableStateOf<ArchiveImportResult?>(null)
    private set

  var archiveFailed by mutableStateOf(false)
    private set

  fun exportArchive(password: String, recordId: String? = null) {
    if (archiveBusy) return
    flush()
    if (working != null && saveState == "failed") return
    archiveProtected = password.isNotEmpty()
    archiveBusy = true
    archiveExported = null
    archiveImported = null
    archiveFailed = false
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val secret = password.toCharArray()
          try {
            writeToDownloads(
              getApplication(),
              "Limen-backup-${exportStamp()}.limen",
              "application/octet-stream",
            ) { output -> store.exportArchive(secret, output, recordId?.let { setOf(it) }) }
          } finally {
            secret.fill('\u0000')
          }
        }
      }
      archiveBusy = false
      result.onSuccess { archiveExported = it }.onFailure { archiveFailed = true }
    }
  }

  fun importArchive(uri: Uri, password: String, repairCorrupt: Boolean = false) {
    if (archiveBusy) return
    flush()
    if (working != null && saveState == "failed") return
    archiveBusy = true
    archiveExported = null
    archiveImported = null
    archiveFailed = false
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val secret = password.toCharArray()
          try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
              store.importArchive(secret, input, repairCorrupt)
            } ?: error("No archive input stream")
          } finally {
            secret.fill('\u0000')
          }
        }
      }
      archiveBusy = false
      result.onSuccess {
        archiveImported = it
        refreshLibrary()
      }.onFailure { archiveFailed = true }
    }
  }

  // ── The flow ─────────────────────────────────────────────────────────────────────────

  /** The protocol being walked, or null when the case is showing. Saved on every edit. */
  var working by mutableStateOf<Protocol?>(null)
    private set

  /** The entry record shown beside an exit inspection, never copied into the mutable exit. */
  var entryBaseline by mutableStateOf<Protocol?>(null)
    private set

  var flowStep by mutableStateOf(FlowStep.Hub)
    private set
  var dossierFocus by mutableStateOf("")
    private set

  fun navigateIssue(issue: FinalizationIssue) {
    val p = working ?: return
    if (issue == FinalizationIssue.Dossier) {
      val defect = p.defects.firstOrNull { it.fixDeadline.isNotBlank() && SwissDate.parse(it.fixDeadline) == null ||
        p.details.estimates[it.id]?.let { e -> nonNegativeNumber(e.replacementChf) == null || nonNegativeNumber(e.ageYears) == null || (nonNegativeNumber(e.lifeYears) ?: 0.0) <= 0 } == true }
      if (defect != null) { editDefect(defect.id); return }
    }
    when (issue) {
      FinalizationIssue.Rooms -> p.rooms.firstOrNull { !it.inspected && p.details.scopeNotes[it.id].isNullOrBlank() }?.let { openRoom(it.id) }
      FinalizationIssue.NoRooms -> goTo(FlowStep.Inspection)
      FinalizationIssue.Objects -> goTo(FlowStep.Objects)
      FinalizationIssue.RecordOutcomes, FinalizationIssue.UnilateralReason -> goTo(FlowStep.Sign)
      FinalizationIssue.DefectText -> p.defects.firstOrNull { it.text.isBlank() }?.let { editDefect(it.id) }
      FinalizationIssue.Evidence -> goTo(FlowStep.Review)
      else -> {
        dossierFocus = when (issue) {
          FinalizationIssue.Parties -> "parties"
          FinalizationIssue.Keys -> if (DossierCard.Keys in p.dossierCards()) "card-Keys" else "transfer"
          FinalizationIssue.Dossier -> incompleteDossierTarget(p)
          FinalizationIssue.HandoverTime -> if (runCatching { java.time.LocalTime.parse(p.handoverTime) }.isSuccess) {
            if (p.details.actualTransferTime.isNotBlank() && runCatching { java.time.LocalTime.parse(p.details.actualTransferTime) }.isFailure) "transfer" else "card-Meters"
          } else "object"
          else -> "object"
        }
        goTo(FlowStep.Dossier)
      }
    }
  }
  fun clearDossierFocus() { dossierFocus = "" }

  /** The steps walked to get here, so back retraces the route rather than guessing it. */
  private var flowTrail by mutableStateOf(emptyList<FlowStep>())

  /**
   * The room being inspected, or null while the grid of rooms is showing. Held by id rather
   * than by index so removing a room cannot silently open a different one.
   */
  var openRoomId by mutableStateOf<String?>(null)
    private set

  /** The defect being edited, or null. A new defect is created on opening the editor. */
  var editingDefectId by mutableStateOf<String?>(null)
    private set

  var finalizing by mutableStateOf(false)
    private set

  var amendmentInProgress by mutableStateOf(false)
    private set

  /** A disk that filled while the photographs were being copied, said rather than swallowed. */
  var amendmentError by mutableStateOf(false)
    private set

  fun clearAmendmentError() {
    amendmentError = false
  }

  var finalizationIssues by mutableStateOf(emptyList<FinalizationIssue>())
    private set

  var finalizationError by mutableStateOf(false)
    private set

  var undoNotice by mutableStateOf<UndoNotice?>(null)
    private set

  /** The record as it stood before the defect editor wrote its shell, keyed by that shell's id. */
  private var defectShell: Pair<String, Protocol>? = null

  private var pendingUndo: PendingUndo? = null
  private var undoJob: kotlinx.coroutines.Job? = null

  fun photoDir(): File? = working?.let { store.photoDir(it.id) }
  fun signatureDir(): File? = working?.let { store.signatureDir(it.id) }

  private fun setActiveProtocol(protocol: Protocol?) {
    commitUndo()
    // Everything below belongs to the record being left, not to the one arriving. An
    // amendment switches protocols without an intervening close, and inherited these: the
    // parent's exported PDF was then offered on the amendment's own export step.
    exported = null
    exportIssue = null
    pdfLang = null
    defectShell = null
    flowTrail = emptyList()
    working = protocol
    if (protocol != null) saveState = "saved"
    legalChecksReset = false
    sequence.accumulateAndGet(protocol?.saveSequence ?: 0, ::maxOf)
    exported = protocol?.exportHistory?.lastOrNull()?.let {
      retainedCopy(protocol, it).takeIf { it.uri != Uri.EMPTY }
    }
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
    commitUndo()
    val p = working ?: return
    if (p.isFinalized() || finalizing) return
    val edited = transform(p)
    if (edited == p) return
    if (confirmBeforeSignedEdit(p) { edit(transform) }) return
    val affectedChecks = p.invalidatedLegalRequirements(edited)
    if (p.legalOverlay.checks.keys.any { it in affectedChecks }) legalChecksReset = true
    val overlay = when {
      affectedChecks.isEmpty() -> edited.legalOverlay
      affectedChecks.size == LegalRequirement.entries.size -> LegalOverlayRecord()
      else -> p.legalOverlay.copy(checks = p.legalOverlay.checks - affectedChecks, notes = p.legalOverlay.notes - affectedChecks)
    }
    working = edited.copy(contentRevision = p.contentRevision + 1, exportedEpochMs = 0,
      saveSequence = nextSequence(), legalOverlay = overlay)
    finalizationIssues = emptyList()
    finalizationError = false
    evidenceError = ""
    scheduleSave()
  }

  private fun reversibleEdit(
    kind: UndoKind,
    evidenceNames: List<String> = emptyList(),
    transform: (Protocol) -> Protocol,
  ) {
    if (contentLocked()) return
    commitUndo()
    val before = working ?: return
    val edited = transform(before)
    if (edited == before) return
    if (confirmBeforeSignedEdit(before) { reversibleEdit(kind, evidenceNames, transform) }) return
    val after = edited.copy(contentRevision = before.contentRevision + 1, exportedEpochMs = 0, saveSequence = nextSequence())
    val token = System.nanoTime()
    val dir = store.photoDir(before.id)
    val files = evidenceNames.distinct().filter(::safeEvidenceName).map { File(dir, it) }
    working = after
    pendingUndo = PendingUndo(token, before, after.contentRevision, files)
    undoNotice = UndoNotice(token, kind)
    finalizationIssues = emptyList()
    finalizationError = false
    scheduleSave()
    undoJob = viewModelScope.launch {
      val accessibility = getApplication<Application>().getSystemService(android.view.accessibility.AccessibilityManager::class.java)
      kotlinx.coroutines.delay(accessibility.getRecommendedTimeoutMillis(10_000,
        android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS or
          android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT).toLong())
      commitUndo(token)
    }
  }

  /**
   * An edit whose only cost is entered numbers rather than a record of its own, offered
   * back through the same undo bar the removals use. "No physical keys" is the one pill
   * that silently zeroes counts the operator typed by hand.
   */
  fun editUndoably(transform: (Protocol) -> Protocol) {
    reversibleEdit(UndoKind.KeyCounts, transform = transform)
  }

  fun undo(token: Long) {
    val pending = pendingUndo?.takeIf { it.token == token } ?: return
    val current = working
    if (current?.id == pending.before.id && current.contentRevision == pending.afterRevision) {
      working = pending.before.copy(
        contentRevision = current.contentRevision + 1,
        exportedEpochMs = 0,
        saveSequence = nextSequence(),
      )
      undoJob?.cancel()
      pendingUndo = null
      undoNotice = null
      scheduleSave()
    } else {
      commitUndo(token)
    }
  }

  private fun commitUndo(token: Long? = null) {
    val pending = pendingUndo ?: return
    if (token != null && pending.token != token) return
    // Originals remain available to the last-good recovery copy until the record is deleted.
    undoJob?.cancel()
    undoJob = null
    pendingUndo = null
    undoNotice = null
  }

  private fun contentLocked(): Boolean = working?.isFinalized() == true || finalizing

  /** Ink records agreement with the current draft; it does not itself rewrite the draft. */
  private fun editSignatures(transform: (Protocol) -> Protocol) {
    commitUndo()
    val p = working ?: return
    if (p.isFinalized() || finalizing) return
    val edited = transform(p)
    if (edited == p) return
    working = edited.copy(saveSequence = nextSequence())
    finalizationIssues = emptyList()
    finalizationError = false
    scheduleSave()
  }

  /** Metadata captured after finalization, such as handing over an identical copy. */
  private fun editMetadata(transform: (Protocol) -> Protocol) {
    commitUndo()
    val p = working ?: return
    working = transform(p).copy(saveSequence = nextSequence())
    scheduleSave()
  }

  fun recordDelivery(
    role: Role,
    method: dev.peringer.limen.data.DeliveryMethod,
    note: String = "",
    participantId: String = "",
  ) {
    val p = working ?: return
    if (!p.isFinalized() || !p.isExported() || role !in listOf(Role.Tenant, Role.Landlord)) return
    if (method == dev.peringer.limen.data.DeliveryMethod.Other && note.isBlank()) return
    val changedAt = System.currentTimeMillis()
    editMetadata { record ->
      record.copy(
        deliveryReceipts = record.deliveryReceipts.filterNot { it.role == role && it.participantId == participantId } +
          dev.peringer.limen.data.DeliveryReceipt(
            role = role,
            epochMs = changedAt,
            method = method,
            note = note.trim(),
            participantId = participantId,
          ),
        deliveryMetadataEpochMs = changedAt,
        deliveryRevision = record.deliveryRevision + 1,
      )
    }
  }

  fun clearDelivery(role: Role, participantId: String = "") {
    if (working?.isFinalized() != true) return
    editMetadata { record ->
      record.copy(
        deliveryReceipts = record.deliveryReceipts.filterNot { it.role == role && it.participantId == participantId },
        deliveryMetadataEpochMs = System.currentTimeMillis(),
        deliveryRevision = record.deliveryRevision + 1,
      )
    }
  }

  private var saveJob: kotlinx.coroutines.Job? = null

  private fun scheduleSave() {
    saveJob?.cancel()
    saveState = "saving"
    saveJob = viewModelScope.launch {
      kotlinx.coroutines.delay(SAVE_DELAY_MS)
      val p = working ?: return@launch
      val result = withContext(Dispatchers.IO) { runCatching { store.save(p) } }
      if (working?.id == p.id && working?.saveSequence == p.saveSequence) {
        saveState = if (result.isSuccess) "saved" else "failed"
      }
    }
  }

  /**
   * The draft on disk, now. Called when the app leaves the foreground, when the flow
   * closes and before any protocol is read back, so the debounce above can never be the
   * reason a keystroke was lost. It writes on the calling thread on purpose: these are
   * lifecycle edges, not typing.
   */
  fun flush() {
    commitUndo()
    saveJob?.cancel()
    saveJob = null
    val p = working ?: return
    if (finalizing) return
    saveState = if (runCatching { store.save(p) }.isSuccess) "saved" else "failed"
    // The place in the walk is part of what "still open" means. The system camera routinely
    // kills this process while the shutter is open, and coming back to the case home instead
    // of the room being photographed is the moment the picture reads as lost.
    prefs.edit()
      .putString(KEY_STEP, flowStep.name)
      .putString(KEY_ROOM, openRoomId)
      .putString(KEY_DEFECT, editingDefectId)
      .apply()
  }

  override fun onCleared() {
    flush()
    super.onCleared()
  }

  /** Starts the walk only after the new-session form has supplied a complete validated identity. */
  fun startNewSession(role: Role, identity: SessionIdentity, kind: Kind) {
    if (!identity.hasRequiredFields()) return
    val name = identity.fullName
    chooseRole(role, name, identity.company)
    saveProfile(identity)
    createProtocol(kind, role, name, identity)
  }

  private fun controllerFor(identity: SessionIdentity): ControllerIdentity {
    if (!controllerIdentity.isBlank()) return controllerIdentity
    return ControllerIdentity(
      organisation = identity.company,
      contactName = identity.fullName,
      street = identity.addressLine,
      postalCode = identity.postalCode,
      city = identity.city,
      phone = identity.phone,
    )
  }

  private fun createProtocol(
    kind: Kind,
    sessionRole: Role? = myRole,
    sessionName: String = profileName,
    sessionIdentity: SessionIdentity = SessionIdentity(firstName = sessionName),
  ) {
    flush()
    if (working != null && saveState == "failed") return
    val now = System.currentTimeMillis()
    val resolvedName = sessionIdentity.fullName.ifBlank { sessionName.trim() }
    val resolvedIdentity = sessionIdentity.copy(
      phone = dev.peringer.limen.data.InternationalPhone.format(
        sessionIdentity.phone,
        sessionIdentity.phoneRegion,
      ),
    )
    val p = Protocol(
      id = "p$now",
      kind = kind,
      sessionRole = sessionRole,
      sessionName = resolvedName,
      sessionIdentity = resolvedIdentity,
      controllerIdentity = controllerFor(resolvedIdentity),
      // The object is the flat being handed over, not the starter's contact address.
      // It is entered independently from the official swisstopo lookup in Setup.
      address = "",
      place = "",
      createdEpochMs = now,
      handoverDate = LocalDate.now().toString(),
      parties = listOf(
        Party(
          Role.Tenant,
          name = if (sessionRole == Role.Tenant) resolvedName else "",
          // At an exit the starter's contact address IS the new address the deposit
          // letter goes to, so it reaches the form's "Neue Adresse" field directly.
          address = if (sessionRole == Role.Tenant && kind == Kind.Exit) {
            listOf(resolvedIdentity.addressLine, resolvedIdentity.placeLine)
              .filter { it.isNotBlank() }.joinToString(", ")
          } else "",
          phone = if (sessionRole == Role.Tenant) resolvedIdentity.phone else "",
        ),
        // An agency session puts the firm on the landlord line and the person present
        // behind "vertreten durch", the way the paper forms record a Verwaltung.
        Party(
          Role.Landlord,
          name = when {
            sessionRole != Role.Landlord -> ""
            resolvedIdentity.company.isNotBlank() -> ""
            else -> resolvedName
          },
          representedBy = if (sessionRole == Role.Landlord && resolvedIdentity.company.isNotBlank()) {
            resolvedName
          } else "",
          agency = if (sessionRole == Role.Landlord) resolvedIdentity.company else "",
          phone = if (sessionRole == Role.Landlord) resolvedIdentity.phone else "",
        ),
      ),
      keys = listOf(
        KeyCount(KeyKind.Apartment),
        KeyCount(KeyKind.HouseDoor),
        KeyCount(KeyKind.Mailbox),
        KeyCount(KeyKind.Cellar),
      ),
      dossier = listOf(DossierCard.Keys),
      rooms = emptyList(),
      roomSetupComplete = false,
    )
    if (runCatching { store.save(p) }.isFailure) { saveState = "failed"; return }
    setActiveProtocol(p)
    flowStep = FlowStep.Hub
    openRoomId = null
    prefs.edit().putString(KEY_OPEN, p.id).apply()
  }

  /**
   * The flat's rooms carry over as the buttons of the exit walk; nothing recorded inside them
   * does. An older entry's item grid is dropped rather than reopened: the exit is a new record
   * and records only what it finds.
   */
  private fun freshRoom(room: Room): Room =
    room.copy(items = emptyList(), keysCount = 0, inspected = false)

  /** The supplied object is still supplied; the photographs of it at entry are not its exit state. */
  private fun freshObject(entry: ObjectEntry): ObjectEntry =
    entry.copy(photos = emptyList(), photoEvidence = emptyList(), inspected = false)

  /** Opens an exit from its entry baseline while retaining the new starter's verified contact data. */
  fun seedExitFrom(entry: Protocol, sessionRole: Role, identity: SessionIdentity) {
    if (!identity.hasRequiredFields()) return
    if (!entry.isFinalized()) return
    val name = identity.fullName
    val resolvedIdentity = identity.copy(
      phone = dev.peringer.limen.data.InternationalPhone.format(identity.phone, identity.phoneRegion),
    )
    chooseRole(sessionRole, name, identity.company)
    saveProfile(identity)
    flush()
    if (working != null && saveState == "failed") return
    val now = System.currentTimeMillis()
    val p = entry.copy(
      id = "p$now",
      kind = Kind.Exit,
      sessionRole = sessionRole,
      sessionName = name,
      sessionIdentity = resolvedIdentity,
      controllerIdentity = controllerFor(resolvedIdentity),
      createdEpochMs = now,
      handoverDate = LocalDate.now().toString(),
      handoverTime = "",
      lastProtocolDate = entry.handoverDate,
      parties = entry.partiesForExit(sessionRole, resolvedIdentity),
      rooms = entry.rooms.map(::freshRoom),
      roomSetupComplete = true,
      objects = entry.objects.map(::freshObject),
      defects = emptyList(),
      remarks = "",
      chargesStatementBy = "",
      oneSided = false,
      unilateralReason = null,
      unilateralNote = "",
      legalOverlay = dev.peringer.limen.data.LegalOverlayRecord(),
      keyTransfer = dev.peringer.limen.data.KeyTransferStatus.NotRecorded,
      keyTransferNote = "",
      pendingRecordReservations = emptyMap(),
      signatures = emptyList(),
      seedId = entry.id,
      amendedFromId = "",
      contentRevision = 0,
      finalizedEpochMs = 0,
      evidenceFingerprint = "",
      deliveryReceipts = emptyList(),
      deliveryMetadataEpochMs = 0,
      exportedEpochMs = 0,
      meters = Meters(),
      details = entry.details.copy(legalSnapshot = null, roomPhotos = emptyMap(), readings = emptyList(),
        reservations = emptyMap(), scopeNotes = emptyMap(), attachments = emptyList(), followUps = emptyList(),
        estimates = emptyMap(), actualTransferDate = "", actualTransferTime = "", reviewRevision = -1,
        participantNoticeShown = false, accessDevices = entry.details.accessDevices.map { it.copy(expected = it.transferred, transferred = 0, note = "") }),
      exportHistory = emptyList(),
      deliveryRevision = 0,
      saveSequence = nextSequence(),
    )
    if (runCatching { store.save(p) }.isFailure) { saveState = "failed"; return }
    setActiveProtocol(p)
    flowStep = FlowStep.Hub
    openRoomId = null
    prefs.edit().putString(KEY_OPEN, p.id).apply()
  }

  fun openProtocol(id: String) {
    flush()
    if (working != null && saveState == "failed") return
    val p = store.load(id) ?: return
    setActiveProtocol(p)
    // A reopened record opens on its hub whether it is signed or not. A finalized one used
    // to open straight onto Export with every way back out of the flow, which made the
    // signed protocol the one thing in the app nobody could go back and read.
    flowStep = FlowStep.Hub
    openRoomId = null
    prefs.edit().putString(KEY_OPEN, id).apply()
  }

  fun deleteProtocol(id: String) {
    val deleted = protocols.firstOrNull { it.id == id } ?: store.load(id)
    store.delete(id)
    refreshLibrary()
    // A draft belongs to the person who started this one handover. Once it is discarded,
    // ask who holds the phone again instead of carrying that session's side forward.
    // Reusable operator details survive discarding a draft.
  }

  fun closeFlow() {
    flush()
    if (working != null && saveState == "failed") return
    setActiveProtocol(null)
    editingDefectId = null
    openRoomId = null
    flowTrail = emptyList()
    // Per-record view state, cleared with the record. The comparison sheet used to survive
    // the close and paint itself over the next protocol opened.
    showDepositCompare = false
    finalizationIssues = emptyList()
    finalizationError = false
    prefs.edit()
      .remove(KEY_OPEN)
      .remove(KEY_STEP)
      .remove(KEY_ROOM)
      .remove(KEY_DEFECT)
      .apply()
    refreshLibrary()
  }

  /**
   * Every place is reachable in every state. A finalized record is walked exactly like a
   * draft and refuses every edit instead, which [edit] enforces at the one point all writes
   * pass through. Locking the navigation as well was belt and braces that cost the reading.
   */
  fun goTo(step: FlowStep) {
    if (step == flowStep) return
    // A trail, not the static parent chain. A finalized record's hub opens the document
    // directly, and following parents back out of it cost three presses to undo one tap.
    flowTrail = (flowTrail + flowStep).takeLast(MAX_TRAIL)
    flowStep = step
    // Leaving the inspection by any route closes the open room. Clearing it only inside
    // flowBack left it set on the seal-bar route, and the next INSPECTION tap then opened
    // a room detail instead of the grid of rooms.
    if (step != FlowStep.Inspection) openRoomId = null
  }

  /** The people whose record outcome is required before this phone can freeze the record. */
  fun requiredRecordRoles(p: Protocol? = working): List<Role> {
    val record = p ?: return emptyList()
    return requiredRecordRoles(record, myRole)
  }

  fun completionIssues(p: Protocol? = working): List<FinalizationIssue> {
    val record = p ?: return emptyList()
    if (record.isFinalized()) return emptyList()
    return protocolCompletionIssues(
      record,
      requiredRecordRoles(record),
      store.photoDir(record.id),
      store.signatureDir(record.id),
    ).plus(if (evidenceBusy > 0) listOf(FinalizationIssue.Evidence) else emptyList()).distinct()
  }

  fun finalizeProtocol() {
    val p = working ?: return
    if (p.isFinalized() || finalizing) return
    val issues = completionIssues(p)
    finalizationIssues = issues
    finalizationError = false
    if (issues.isNotEmpty()) return

    // Locks the content first: checking means hashing and decoding every photograph, which
    // takes seconds on a large record and froze the screen when it ran on the main thread.
    finalizing = true
    viewModelScope.launch {
      val unreadable = withContext(Dispatchers.IO) {
        dev.peringer.limen.photo.EvidenceValidation.unreadable(p, store.photoDir(p.id), store.signatureDir(p.id))
      }
      if (unreadable.isNotEmpty()) {
        evidenceError = unreadable.joinToString()
        finalizationIssues = listOf(FinalizationIssue.Evidence)
        finalizing = false
        return@launch
      }
      saveJob?.cancel()
      saveJob = null
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val withMoment = p.copy(
            details = p.details.copy(legalSnapshot = p.freezeLegalRules()),
            signatures = p.signatures.filter(p::isCurrent),
            pendingRecordReservations = emptyMap(),
            finalizedEpochMs = System.currentTimeMillis(),
            saveSequence = nextSequence(),
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
        if (working?.id == p.id) {
          setActiveProtocol(frozen)
          finalizationIssues = emptyList()
          flowStep = FlowStep.Export
        }
        refreshLibrary()
      }
      result.onFailure { finalizationError = true }
    }
  }

  /** Starts a new draft and copies the original photos; the finalized record stays intact. */
  fun createAmendment() {
    val p = working ?: return
    if (!p.isFinalized() || amendmentInProgress) return
    // The one protocol switch that had no flush before it: a debounced write still in the
    // air would have been aimed at the record being left.
    flush()
    amendmentInProgress = true
    amendmentError = false
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          val now = System.currentTimeMillis()
          val draft = p.copy(
            id = "p$now",
            createdEpochMs = now,
            amendedFromId = p.id,
            amendedFromFingerprint = p.evidenceFingerprint,
            contentRevision = 0,
            finalizedEpochMs = 0,
            evidenceFingerprint = "",
            deliveryReceipts = emptyList(),
            deliveryMetadataEpochMs = 0,
            signatures = emptyList(),
            exportedEpochMs = 0,
            exportHistory = emptyList(),
            deliveryRevision = 0,
            saveSequence = nextSequence(),
            details = p.details.copy(legalSnapshot = null, reviewRevision = -1),
          )
          store.photoDir(p.id).listFiles()?.forEach { source ->
            source.copyTo(File(store.photoDir(draft.id), source.name), overwrite = false)
          }
          store.save(draft)
          draft
        }
      }
      amendmentInProgress = false
      result.onFailure { amendmentError = true }
      result.onSuccess { draft ->
        if (working?.id == p.id) {
          setActiveProtocol(draft)
          finalizationIssues = emptyList()
          flowStep = FlowStep.Hub
          prefs.edit().putString(KEY_OPEN, draft.id).apply()
        }
        refreshLibrary()
      }
    }
  }

  /** Back inside the flow: out of the open room, then up the one branch a step has. */
  fun flowBack() {
    if (editingDefectId != null) {
      // Through [closeDefect], not by clearing the id: leaving the editor by the masthead
      // key used to strand an empty finding on the record, which now counts in a room's
      // total and blocks finalization for a defect nobody meant to write.
      closeDefect()
      return
    }
    if (flowStep == FlowStep.Inspection && openRoomId != null) {
      openRoomId = null
      return
    }
    val trailed = flowTrail.lastOrNull()
    if (trailed != null) {
      flowTrail = flowTrail.dropLast(1)
      flowStep = trailed
      if (trailed != FlowStep.Inspection) openRoomId = null
      return
    }
    val parent = flowStep.parent
    if (parent == null) closeFlow() else flowStep = parent
  }

  // ── Rooms ────────────────────────────────────────────────────────────────────────────

  fun openRoom(roomId: String) {
    if (working?.rooms?.none { it.id == roomId } == true) return
    openRoomId = roomId
    flowStep = FlowStep.Inspection
  }

  fun closeRoom() {
    openRoomId = null
  }

  fun setInitialRooms(kinds: List<RoomKind>) {
    if (contentLocked() || kinds.isEmpty() || working?.rooms?.isNotEmpty() == true) return
    val unique = kinds.distinct()
    edit { p ->
      p.copy(
        rooms = unique.mapIndexed { index, kind -> Room(id = "r${p.createdEpochMs}-$index", kind = kind) },
        roomSetupComplete = true,
      )
    }
  }

  fun addRoom(kind: RoomKind, name: String = "") {
    val id = "r${System.currentTimeMillis()}"
    edit { p -> p.copy(rooms = p.rooms + Room(id = id, kind = kind, name = name.trim())) }
    if (working?.rooms?.any { it.id == id } == true) openRoomId = id
  }

  fun renameRoom(roomId: String, name: String) {
    edit { p ->
      p.copy(rooms = p.rooms.map { if (it.id == roomId) it.copy(name = name) else it })
    }
  }

  fun moveRoom(id: String, offset: Int) {
    edit { p ->
      val list = p.rooms.toMutableList()
      val index = list.indexOfFirst { it.id == id }
      if (index >= 0) { val room = list.removeAt(index); list.add((index + offset).coerceIn(0, list.size), room) }
      p.copy(rooms = list)
    }
  }

  fun nextRoom(id: String) {
    val rooms = working?.rooms.orEmpty()
    openRoomId = rooms.getOrNull(rooms.indexOfFirst { it.id == id } + 1)?.id
  }

  fun setRoomInspected(roomId: String, inspected: Boolean) {
    edit { p ->
      p.copy(rooms = p.rooms.map { if (it.id == roomId) it.copy(inspected = inspected) else it })
    }
  }

  fun removeRoom(roomId: String) {
    if (contentLocked()) return
    val gone = working?.defects?.filter { it.roomId == roomId }.orEmpty()
    deletePhotosOf(gone.flatMap(Defect::photoFiles))
    edit { p ->
      p.copy(
        rooms = p.rooms.filterNot { it.id == roomId },
        defects = p.defects.filterNot { it.roomId == roomId },
        objects = p.objects.map { if (it.roomId == roomId) it.copy(roomId = "") else it },
        details = p.details.copy(roomPhotos = p.details.roomPhotos - roomId,
          scopeNotes = p.details.scopeNotes - roomId, estimates = p.details.estimates - gone.map { it.id }.toSet()),
      )
    }
    if (openRoomId == roomId) openRoomId = null
  }

  /** Clears every finding in this room, leaving the room itself on the walk. */
  fun clearRoom(roomId: String) {
    if (contentLocked()) return
    val gone = working?.defects?.filter { it.roomId == roomId }.orEmpty()
    deletePhotosOf(gone.flatMap(Defect::photoFiles))
    edit { p -> p.copy(defects = p.defects.filterNot { it.roomId == roomId }, details = p.details.copy(estimates = p.details.estimates - gone.map { it.id }.toSet())) }
  }

  // ── Objects ──────────────────────────────────────────────────────────────────────────

  /**
   * Appends a row. Pressing the same catalogue key twice records two of the thing, because
   * two fridges in a flat are two facts, each with its own photograph.
   */
  fun addObject(
    group: ObjectGroup,
    catalogue: String = "",
    label: String = "",
    groupLabel: String = "",
    roomId: String = "",
  ) {
    if (group == ObjectGroup.Other && groupLabel.isBlank()) return
    if (catalogue.isBlank() && label.isBlank()) return
    edit { p ->
      p.copy(objects = p.objects + ObjectEntry(
        id = "o${System.currentTimeMillis()}",
        group = group,
        groupLabel = groupLabel.trim(),
        catalogue = catalogue,
        label = label.trim(),
        roomId = roomId,
      ))
    }
  }

  fun updateObject(id: String, transform: (ObjectEntry) -> ObjectEntry) {
    edit { p -> p.copy(objects = p.objects.map { if (it.id == id) transform(it) else it }) }
  }

  fun setObjectInspected(id: String, inspected: Boolean) {
    updateObject(id) { it.copy(inspected = inspected) }
  }

  /** A removed object takes its photographs and any finding recorded on it with it. */
  fun removeObject(id: String) {
    if (contentLocked()) return
    val entry = working?.objects?.firstOrNull { it.id == id }
    val gone = working?.defects?.filter { it.objectId == id }.orEmpty()
    reversibleEdit(
      kind = UndoKind.Object,
      evidenceNames = entry?.photoFiles().orEmpty() + gone.flatMap(Defect::photoFiles),
    ) { p ->
      p.copy(
        objects = p.objects.filterNot { it.id == id },
        defects = p.defects.filterNot { it.objectId == id },
        details = p.details.copy(scopeNotes = p.details.scopeNotes - id, estimates = p.details.estimates - gone.map { it.id }.toSet()),
      )
    }
  }

  /** Every custom group on this record, in the order the person created them. */
  fun customGroups(p: Protocol? = working): List<String> =
    p?.objects.orEmpty()
      .filter { it.group == ObjectGroup.Other }
      .map { it.groupLabel }
      .filter { it.isNotBlank() }
      .distinct()

  // ── The dossier ──────────────────────────────────────────────────────────────────────

  fun addDossierCard(card: DossierCard) {
    edit { p -> p.copy(dossier = (p.dossierCards() + card).distinct().sortedBy { it.ordinal }, dossierChosen = true,
      deposit = if (card == DossierCard.Deposit && p.deposit == Deposit()) Deposit(DepositKind.Unknown) else p.deposit) }
  }

  /** Removing a component takes its content with it, so the record never prints a stale field. */
  fun removeDossierCard(card: DossierCard) {
    edit { current ->
      val p = current.copy(dossier = current.dossierCards() - card, dossierChosen = true)
      when (card) {
        DossierCard.Contract -> p.copy(contract = Contract())
        DossierCard.Deposit -> p.copy(
          deposit = Deposit(),
          legalOverlay = p.legalOverlay.withoutStatus(
            dev.peringer.limen.data.LegalRequirement.DepositProof,
          ),
        )
        DossierCard.Meters -> p.copy(meters = Meters(), details = p.details.copy(readings = emptyList(), scopeNotes = p.details.scopeNotes - "Meters"))
        DossierCard.Keys -> p.copy(
          keys = emptyList(),
          selfMadeKeys = false,
          keyTransfer = dev.peringer.limen.data.KeyTransferStatus.NotRecorded,
          keyTransferNote = "",
          details = p.details.copy(accessDevices = emptyList()),
        )
        // oneSided goes with it: it is edited on this card, it derives this card back at
        // Protocol.dossierCards, and a record with no notes cannot state why it is one-sided.
        DossierCard.Notes -> p.copy(
          remarks = "",
          chargesStatementBy = "",
          oneSided = false,
          unilateralReason = null,
          unilateralNote = "",
        )
      }
    }
  }

  /**
   * Seeds from the content-derived answer before applying the change, so a draft written
   * before the dossier existed does not lose its deposit the moment a meter is added.
   */
  private fun setDossier(transform: (List<DossierCard>) -> List<DossierCard>) {
    edit { p ->
      p.copy(
        dossier = transform(p.dossierCards()).distinct().sortedBy { it.ordinal },
        dossierChosen = true,
      )
    }
  }

  // ── Defects ──────────────────────────────────────────────────────────────────────────

  fun openDefect(roomId: String, item: ItemKey?, itemLabel: String = "") {
    if (contentLocked()) return
    val id = "d${System.currentTimeMillis()}"
    val base = working
    edit { p ->
      p.copy(defects = p.defects + Defect(
        id = id,
        roomId = roomId,
        item = item,
        itemLabel = itemLabel.trim(),
      ))
    }
    defectShell = base?.let { id to it }
    editingDefectId = id
  }

  /** A finding about a supplied object rather than a surface of the flat. */
  fun openObjectDefect(objectId: String) {
    if (contentLocked()) return
    val id = "d${System.currentTimeMillis()}"
    val base = working
    edit { p -> p.copy(defects = p.defects + Defect(id = id, objectId = objectId)) }
    defectShell = base?.let { id to it }
    editingDefectId = id
  }

  /** Opens a finding. On a finalized record the editor is the reader; nothing it shows moves. */
  fun editDefect(id: String) {
    editingDefectId = id
  }

  fun updateDefect(id: String, transform: (Defect) -> Defect) {
    edit { p -> p.copy(defects = p.defects.map { if (it.id == id) transform(it) else it }) }
  }

  /**
   * Closing the editor discards the shell it opened with, and only that. A finding can carry
   * a charge, a deadline, a tenant response or a reservation without a word of description,
   * and testing emptiness on the description alone threw all of it away with no undo.
   *
   * When nothing else moved in between, the record is put back exactly as it stood before the
   * shell was written: [Protocol.isCurrent] reads contentRevision, so a pair of edits that
   * cancel used to turn both parties' signatures stale on a record whose content never moved.
   */
  fun closeDefect() {
    val id = editingDefectId ?: return
    editingDefectId = null
    val base = defectShell?.takeIf { it.first == id }?.second
    defectShell = null
    val p = working ?: return
    if (p.isFinalized() || finalizing) return
    val d = p.defects.firstOrNull { it.id == id } ?: return
    if (!d.isUntouched()) return
    val without = p.copy(defects = p.defects.filterNot { it.id == id })
    val unchanged = base != null &&
      without.copy(
        contentRevision = base.contentRevision,
        exportedEpochMs = base.exportedEpochMs,
      ) == base
    working = if (unchanged) base else without
    scheduleSave()
  }

  fun removeDefect(id: String, allowUndo: Boolean = true) {
    if (contentLocked()) return
    val gone = working?.defects?.firstOrNull { it.id == id }
    if (allowUndo) {
      reversibleEdit(UndoKind.Defect, gone?.photoFiles().orEmpty()) { p ->
        p.copy(defects = p.defects.filterNot { it.id == id }, details = p.details.copy(estimates = p.details.estimates - id))
      }
    } else {
      deletePhotosOf(gone?.photoFiles().orEmpty())
      edit { p -> p.copy(defects = p.defects.filterNot { it.id == id }, details = p.details.copy(estimates = p.details.estimates - id)) }
    }
    if (editingDefectId == id) editingDefectId = null
  }

  private fun deletePhotosOf(names: List<String>) {
    // Retain unreferenced originals for recovery; scoped archives exclude them.
  }

  private fun safeEvidenceName(name: String): Boolean =
    name == File(name).name && name !in setOf(".", "..")

  // ── Photographs ──────────────────────────────────────────────────────────────────────

  /**
   * A photo the camera or picker delivered, preserved byte-for-byte beside the protocol and
   * hashed at once, because the hash is what the fingerprint later covers. The owner is a
   * defect or a supplied object; the file is named after it either way.
   */
  private fun copyPhoto(recordId: String, ownerId: String, existing: Int, source: File, photoSource: PhotoSource): PhotoEvidence? {
    val dir = store.photoDir(recordId)
    if (!dev.peringer.limen.photo.PhotoDecode.readable(source)) return null
    val ownerToken = ownerId
      .takeIf { safeEvidenceName(it) && it.matches(Regex("[A-Za-z0-9._-]+")) }
      ?.take(80)
      ?: "e${ownerId.hashCode().toUInt().toString(16)}"
    val name = "$ownerToken-${System.currentTimeMillis()}-${existing + 1}.jpg"
    val target = File(dir, name)
    runCatching { source.copyTo(target, overwrite = true) }.getOrElse {
      target.delete()
      return null
    }
    source.delete()
    // fileHash refuses an empty or unreadable file, and a copy onto a full card still
    // produces one. Dropping the photograph is the right answer; throwing here took the
    // process down inside the activity-result callback, with the camera just closed.
    val hash = runCatching { ProtocolIntegrity.fileHash(target) }.getOrElse {
      target.delete()
      return null
    }
    return PhotoEvidence(
      file = name,
      sha256 = hash,
      capturedEpochMs = if (photoSource == PhotoSource.Camera) System.currentTimeMillis() else 0,
      importedEpochMs = System.currentTimeMillis(),
      source = photoSource,
    )
  }

  /**
   * The copy and the SHA-256 run off this thread: both walk the whole JPEG, and a gallery
   * pick of a twelve-megapixel photograph froze the frame for as long as three passes take,
   * right after the picker closed and the hand is waiting to see the thumbnail.
   */
  fun attachPhoto(defectId: String, source: File, photoSource: PhotoSource = PhotoSource.Unknown) {
    if (contentLocked()) return
    val recordId = working?.id ?: return
    val had = working?.defects?.firstOrNull { it.id == defectId }?.photos?.size ?: 0
    evidenceBusy++
    viewModelScope.launch {
      val evidence = withContext(Dispatchers.IO) {
        copyPhoto(recordId, defectId, had, source, photoSource)
      }
      evidenceBusy--
      if (evidence == null) { evidenceError = source.name; return@launch }
      completeEvidence(recordId) { p -> p.copy(defects = p.defects.map {
        if (it.id == defectId) it.copy(photos = it.photos + evidence.file, photoEvidence = it.photoEvidence + evidence) else it
      })
      }
    }
  }

  fun removePhoto(defectId: String, name: String) {
    if (contentLocked()) return
    reversibleEdit(UndoKind.Photo, listOf(name)) { p ->
      p.copy(defects = p.defects.map { defect ->
        if (defect.id != defectId) defect else defect.copy(
          photos = defect.photos - name,
          photoEvidence = defect.photoEvidence.filterNot { photo -> photo.file == name },
        )
      })
    }
  }

  fun attachObjectPhoto(objectId: String, source: File, photoSource: PhotoSource = PhotoSource.Unknown) {
    if (contentLocked()) return
    val recordId = working?.id ?: return
    val had = working?.objects?.firstOrNull { it.id == objectId }?.photos?.size ?: 0
    evidenceBusy++
    viewModelScope.launch {
      val evidence = withContext(Dispatchers.IO) {
        copyPhoto(recordId, objectId, had, source, photoSource)
      }
      evidenceBusy--
      if (evidence == null) { evidenceError = source.name; return@launch }
      completeEvidence(recordId) { p -> p.copy(objects = p.objects.map {
        if (it.id == objectId) it.copy(photos = it.photos + evidence.file, photoEvidence = it.photoEvidence + evidence) else it
      })
      }
    }
  }

  fun removeObjectPhoto(objectId: String, name: String) {
    if (contentLocked()) return
    reversibleEdit(UndoKind.Photo, listOf(name)) { p ->
      p.copy(objects = p.objects.map { entry ->
        if (entry.id != objectId) entry else entry.copy(
          photos = entry.photos - name,
          photoEvidence = entry.photoEvidence.filterNot { photo -> photo.file == name },
        )
      })
    }
  }

  fun attachOverview(owner: String, source: File, photoSource: PhotoSource, meter: Boolean = false) {
    val p = working ?: return
    if (contentLocked()) return
    evidenceBusy++
    viewModelScope.launch {
      val evidence = withContext(Dispatchers.IO) { copyPhoto(p.id, owner, 0, source, photoSource) }
      evidenceBusy--
      if (evidence == null) { evidenceError = source.name; return@launch }
      completeEvidence(p.id) { it.copy(details = if (meter) it.details.copy(readings = it.details.readings.map { reading ->
        if (reading.id == owner) reading.copy(photos = reading.photos + evidence) else reading
      }) else it.details.copy(roomPhotos = it.details.roomPhotos + (owner to (it.details.roomPhotos[owner].orEmpty() + evidence)))) }
    }
  }

  fun recordPhotoDir(id: String): File = store.photoDir(id)
  private fun completeEvidence(recordId: String, transform: (Protocol) -> Protocol) {
    val p = working?.takeIf { it.id == recordId } ?: store.load(recordId) ?: return
    if (p.isFinalized()) { evidenceError = Str(lang).lockedRecord; return }
    if (working?.id == recordId) edit(transform) else {
      val updated = transform(p).copy(contentRevision = p.contentRevision + 1, saveSequence = nextSequence())
      if (runCatching { store.save(updated) }.isFailure) saveState = "failed"
      refreshLibrary()
    }
  }
  fun recordSignatureDir(id: String): File = store.signatureDir(id)

  fun attachDocument(documentId: String, uri: Uri) {
    val p = working ?: return
    if (contentLocked()) return
    evidenceBusy++
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) { runCatching {
        val resolver = getApplication<Application>().contentResolver
        val mime = resolver.getType(uri).orEmpty()
        require(mime in setOf("application/pdf", "image/jpeg", "image/png"))
        val file = File(store.photoDir(p.id), "document-${java.util.UUID.randomUUID()}.${if (mime == "application/pdf") "pdf" else if (mime == "image/png") "png" else "jpg"}")
        try {
          resolver.openInputStream(uri)?.use { input -> file.outputStream().use { out ->
            val buffer = ByteArray(32 * 1024)
            var bytes = 0L
            while (true) { val read = input.read(buffer); if (read < 0) break
              bytes += read; require(bytes <= 32L * 1024 * 1024); out.write(buffer, 0, read) }
          } } ?: error("No input")
          if (mime == "application/pdf") android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { require(it.pageCount > 0) }
          else require(dev.peringer.limen.photo.PhotoDecode.readable(file))
          SupportingDocument(documentId, file = file.name, mime = mime, sha256 = ProtocolIntegrity.fileHash(file),
            bytes = file.length(), importedEpochMs = System.currentTimeMillis())
        } catch (failure: Exception) { file.delete(); throw failure }
      } }
      evidenceBusy--
      result.onSuccess { doc ->
        completeEvidence(p.id) { current ->
          current.copy(details = current.details.copy(attachments = current.details.attachments.map { old ->
            if (old.id == documentId) old.copy(file = doc.file, mime = doc.mime, sha256 = doc.sha256, bytes = doc.bytes, importedEpochMs = doc.importedEpochMs) else old
          }))
        }
      }.onFailure { evidenceError = Str(lang).attachments }
    }
  }

  fun openSupportingDocument(context: Context, document: SupportingDocument) {
    val p = working ?: return
    if (!safeEvidenceName(document.file) || document.mime !in setOf("application/pdf", "image/jpeg", "image/png")) return
    val file = File(store.photoDir(p.id), document.file)
    runCatching {
      val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
      context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, document.mime)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }.onFailure { evidenceError = document.file }
  }

  // ── Signatures ───────────────────────────────────────────────────────────────────────

  private fun deleteSignatureFile(name: String) {
    // Replaced ink is excluded from scoped archives, but retained for draft recovery.
  }

  /**
   * Keeps the ink: the PNG is written beside the protocol and the record points at it.
   *
   * Staged and moved rather than written in place, and the compress result is read. A
   * compress that failed part way left a truncated PNG with a length, which passed the
   * evidence check and printed as an empty rule under a full signature caption.
   */
  fun keepSignature(role: Role, plane: Plane, bitmap: android.graphics.Bitmap, participantId: String = "") {
    if (contentLocked() || evidenceBusy > 0) return
    val dir = signatureDir() ?: return
    val signerToken = participantId.ifBlank { role.name.lowercase() }.let {
      if (it.matches(Regex("[A-Za-z0-9_-]{1,100}"))) it else "signer-${it.hashCode().toUInt()}"
    }
    val name = "$signerToken-${plane.name.lowercase()}-${System.nanoTime()}.png"
    val target = File(dir, name)
    val staged = File(dir, "$name.part")
    val wrote = runCatching {
      staged.outputStream().use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
      }
    }.getOrDefault(false)
    if (!wrote || staged.length() == 0L) {
      staged.delete()
      evidenceError = Str(lang).signRecordTitle
      return
    }
    target.delete()
    if (!staged.renameTo(target)) {
      val copied = runCatching { staged.copyTo(target, overwrite = true) }.isSuccess
      staged.delete()
      if (!copied || target.length() == 0L) {
        target.delete()
        evidenceError = Str(lang).signRecordTitle
        return
      }
    }
    val partyName = working?.parties?.firstOrNull { if (participantId.isBlank()) it.role == role else it.participantKey() == participantId }?.signerName().orEmpty()
    editSignatures { p ->
      val reservation = (
        (if (participantId.isBlank()) p.pendingRecordReservations[role] else p.details.reservations[participantId])
          ?: p.signatures.firstOrNull { it.role == role && it.plane == plane && (if (participantId.isBlank()) it.participantId.isBlank() else it.participantId == participantId || it.participantId.isBlank() && participantId == "legacy-${role.name}") }?.reservation
        ).orEmpty().trim()
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane && (if (participantId.isBlank()) it.participantId.isBlank() else it.participantId == participantId || it.participantId.isBlank() && participantId == "legacy-${role.name}") } +
          dev.peringer.limen.data.Signature(
            role = role,
            participantId = participantId,
            name = partyName,
            plane = plane,
            file = name,
            reservation = reservation,
            epochMs = System.currentTimeMillis(),
            contentRevision = p.contentRevision,
          )
      )
    }
  }

  /** Refusal is an outcome the record keeps, not an error it recovers from. */
  fun refuseSignature(role: Role, plane: Plane, participantId: String = "", outcome: String = "") {
    if (contentLocked()) return
    val existing = working?.signatures?.firstOrNull { it.role == role && it.plane == plane && (if (participantId.isBlank()) it.participantId.isBlank() else it.participantId == participantId || it.participantId.isBlank() && participantId == "legacy-${role.name}") }
    if (existing != null && existing.file.isNotBlank()) {
      deleteSignatureFile(existing.file)
    }
    val partyName = working?.parties?.firstOrNull { if (participantId.isBlank()) it.role == role else it.participantKey() == participantId }?.signerName().orEmpty()
    editSignatures { p ->
      val reservation = (
        (if (participantId.isBlank()) p.pendingRecordReservations[role] else p.details.reservations[participantId])
          ?: p.signatures.firstOrNull { it.role == role && it.plane == plane && (if (participantId.isBlank()) it.participantId.isBlank() else it.participantId == participantId || it.participantId.isBlank() && participantId == "legacy-${role.name}") }?.reservation
        ).orEmpty().trim()
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane && (if (participantId.isBlank()) it.participantId.isBlank() else it.participantId == participantId || it.participantId.isBlank() && participantId == "legacy-${role.name}") } +
          dev.peringer.limen.data.Signature(
            role = role,
            participantId = participantId,
            name = partyName,
            plane = plane,
            refused = outcome.isBlank(),
            outcome = outcome,
            reservation = reservation,
            epochMs = System.currentTimeMillis(),
            contentRevision = p.contentRevision,
          )
      )
    }
  }

  fun clearSignature(role: Role, plane: Plane, participantId: String = "") {
    if (contentLocked()) return
    val existing = working?.signatures?.firstOrNull { it.role == role && it.plane == plane && (if (participantId.isBlank()) it.participantId.isBlank() else it.participantId == participantId || it.participantId.isBlank() && participantId == "legacy-${role.name}") }
    if (existing != null && existing.file.isNotBlank()) {
      deleteSignatureFile(existing.file)
    }
    editSignatures { p ->
      val reservation = existing?.reservation.orEmpty()
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane && (if (participantId.isBlank()) it.participantId.isBlank() else it.participantId == participantId || it.participantId.isBlank() && participantId == "legacy-${role.name}") },
        pendingRecordReservations = if (participantId.isBlank()) p.pendingRecordReservations + (role to reservation) else p.pendingRecordReservations,
        details = if (participantId.isBlank()) p.details else p.details.copy(reservations = p.details.reservations + (participantId to reservation)),
      )
    }
  }

  fun setRecordReservation(role: Role, text: String, participantId: String = "") {
    // A reservation is printed in the shared record. Changing it after another person
    // signed must therefore move the revision and require that signature to be reviewed.
    edit { p ->
      p.copy(
        pendingRecordReservations = if (participantId.isBlank()) p.pendingRecordReservations + (role to text) else p.pendingRecordReservations,
        details = if (participantId.isBlank()) p.details else p.details.copy(reservations = p.details.reservations + (participantId to text))
      )
    }
  }

  // ── Export ───────────────────────────────────────────────────────────────────────────

  var exporting by mutableStateOf(false)
    private set

  var exported by mutableStateOf<dev.peringer.limen.data.ExportedFile?>(null)
    private set

  var exportIssue by mutableStateOf<ExportIssue?>(null)
    private set

  /** The document's language, chosen at export and free of the UI language. */
  var pdfLang by mutableStateOf<Lang?>(null)
    private set

  fun selectPdfLang(l: Lang) {
    pdfLang = l
  }

  fun selectExport(copy: ExportCopy) { working?.let {
    exported = retainedCopy(it, copy).takeIf { out -> out.uri != Uri.EMPTY }
    if (exported == null) exportIssue = ExportIssue.Failed
  } }

  private fun retainedCopy(p: Protocol, copy: ExportCopy): dev.peringer.limen.data.ExportedFile {
    val app = getApplication<Application>()
    fun location(name: String, expectedHash: String = ""): Uri? {
      val file = File(app.filesDir, "exports/${p.id}/$name")
      val valid = safeEvidenceName(name) && file.isFile && (expectedHash.isBlank() ||
        runCatching { ProtocolIntegrity.fileHash(file) == expectedHash }.getOrDefault(false))
      return if (valid) androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.files", file) else null
    }
    return dev.peringer.limen.data.ExportedFile(location(copy.name, copy.sha256) ?: Uri.EMPTY,
      copy.name, copy.bytes, location(copy.name.removeSuffix(".pdf") + ".html"), copy.sha256)
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
          dev.peringer.limen.data.PdfExport.export(
            getApplication(),
            p,
            Str(docLang),
            store.photoDir(p.id),
            store.signatureDir(p.id),
          )
        }
      }
      // The export outlives the screen that started it. Everything it writes back is bound
      // to the record it was started for: closing the flow mid-export used to stamp the
      // moment onto whatever draft happened to be open when the writer finished, and
      // recordDelivery then refused forever on the record that really had been exported.
      val stillOpen = working?.id == p.id
      if (result == null) {
        if (stillOpen) exportIssue = ExportIssue.IntegrityMismatch
      } else {
        result.fold(
          onSuccess = { out ->
            val latest = working?.takeIf { it.id == p.id } ?: store.load(p.id) ?: return@fold
            val now = System.currentTimeMillis()
            val copy = ExportCopy(out.uri.toString(), out.displayName, out.bytes, docLang.name,
              p.deliveryRevision, now, out.companionUri?.toString().orEmpty(), out.sha256)
            val stamped = latest.copy(exportedEpochMs = now,
              exportHistory = latest.exportHistory + copy, saveSequence = nextSequence())
            if (stillOpen) {
              exported = out
              working = stamped
            }
            val saved = withContext(Dispatchers.IO) { runCatching { store.save(stamped) } }
            if (saved.isFailure) saveState = "failed"
            refreshLibrary()
          },
          onFailure = { failure ->
            if (stillOpen) {
              exportIssue = ExportIssue.Failed
              (failure as? dev.peringer.limen.photo.UnrenderableEvidence)?.let { evidenceError = it.files.joinToString() }
            }
          },
        )
      }
      exporting = false
    }
  }

  // ── The deposit comparison ───────────────────────────────────────────────────────────

  var showDepositCompare by mutableStateOf(false)
    private set

  fun setDepositCompare(open: Boolean) {
    showDepositCompare = open
  }

  // ── Controls ─────────────────────────────────────────────────────────────────────────

  fun selectLang(l: Lang) {
    lang = l
    prefs.edit().putString(KEY_LANG, l.name).apply()
  }

  fun toggleTheme() {
    darkTheme = !darkTheme
    prefs.edit().putBoolean(KEY_DARK, darkTheme).apply()
  }

  /**
   * Reopens whatever record was on screen when the process last ended.
   *
   * Declared last on purpose: it calls [setActiveProtocol], which clears view state held in
   * properties further down the class, and Kotlin initializes properties in declaration
   * order. Run from the top, it touched delegates that did not exist yet.
   */
  init {
    refreshLibrary()
    val openId = prefs.getString(KEY_OPEN, null)
    if (!openId.isNullOrBlank()) {
      val recovered = store.load(openId)
      if (recovered != null) {
        setActiveProtocol(recovered)
        flowStep = prefs.getString(KEY_STEP, null)
          ?.let { name -> FlowStep.entries.firstOrNull { it.name == name } }
          ?: FlowStep.Hub
        // Both are checked against the record rather than trusted: a room or a finding can
        // have been removed by an amendment, or by another restore, since the process died.
        openRoomId = prefs.getString(KEY_ROOM, null)
          ?.takeIf { id -> recovered.rooms.any { it.id == id } }
        editingDefectId = prefs.getString(KEY_DEFECT, null)
          ?.takeIf { id -> recovered.defects.any { it.id == id } }
      } else {
        prefs.edit().remove(KEY_OPEN).apply()
      }
    }
  }
}
