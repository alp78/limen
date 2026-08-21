package dev.verso.limen.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.verso.limen.data.Contract
import dev.verso.limen.data.ControllerIdentity
import dev.verso.limen.data.ArchiveImportResult
import dev.verso.limen.data.Defect
import dev.verso.limen.data.Deposit
import dev.verso.limen.data.DossierCard
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.KeyCount
import dev.verso.limen.data.KeyKind
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Meters
import dev.verso.limen.data.ObjectEntry
import dev.verso.limen.data.ObjectGroup
import dev.verso.limen.data.Party
import dev.verso.limen.data.PhotoEvidence
import dev.verso.limen.data.PhotoSource
import dev.verso.limen.data.Plane
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.ProtocolIntegrity
import dev.verso.limen.data.Role
import dev.verso.limen.data.Room
import dev.verso.limen.data.RoomKind
import dev.verso.limen.data.SessionIdentity
import dev.verso.limen.data.Store
import dev.verso.limen.data.Templates
import dev.verso.limen.data.dossierCards
import dev.verso.limen.data.hasRequiredFields
import dev.verso.limen.data.photoFiles
import dev.verso.limen.data.exportStamp
import dev.verso.limen.data.writeToDownloads
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

enum class UndoKind { Defect, Photo, Object }

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

  fun updateProfileName(name: String) {
    profileName = name
    prefs.edit().putString(KEY_NAME, name).apply()
  }

  /** Forgets who holds the phone: back to the first question, nothing else touched. */
  fun clearProfile() {
    myRole = null
    profileName = ""
    profileCompany = ""
    prefs.edit().remove(KEY_ROLE).remove(KEY_NAME).remove(KEY_COMPANY).apply()
  }


  var darkTheme by mutableStateOf(prefs.getBoolean(KEY_DARK, false))
    private set

  /** The language the instrument speaks; first run follows the system, then it is a choice. */
  var lang by mutableStateOf(Lang.from(prefs.getString(KEY_LANG, null)))
    private set

  // ── The case ─────────────────────────────────────────────────────────────────────────

  var protocols by mutableStateOf(store.list())
    private set

  var archiveBusy by mutableStateOf(false)
    private set

  var archiveExported by mutableStateOf<dev.verso.limen.data.ExportedFile?>(null)
    private set

  var archiveImported by mutableStateOf<ArchiveImportResult?>(null)
    private set

  var archiveFailed by mutableStateOf(false)
    private set

  fun exportArchive(password: String) {
    if (archiveBusy) return
    if (password.length < 12) return
    flush()
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
            ) { output -> store.exportArchive(secret, output) }
          } finally {
            secret.fill('\u0000')
          }
        }
      }
      archiveBusy = false
      result.onSuccess { archiveExported = it }.onFailure { archiveFailed = true }
    }
  }

  fun importArchive(uri: Uri, password: String) {
    if (archiveBusy) return
    flush()
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
              store.importArchive(secret, input)
            } ?: error("No archive input stream")
          } finally {
            secret.fill('\u0000')
          }
        }
      }
      archiveBusy = false
      result.onSuccess {
        archiveImported = it
        protocols = store.list()
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

  var finalizationIssues by mutableStateOf(emptyList<FinalizationIssue>())
    private set

  var finalizationError by mutableStateOf(false)
    private set

  var undoNotice by mutableStateOf<UndoNotice?>(null)
    private set

  private var pendingUndo: PendingUndo? = null
  private var undoJob: kotlinx.coroutines.Job? = null

  init {
    val openId = prefs.getString(KEY_OPEN, null)
    if (!openId.isNullOrBlank()) {
      val recovered = store.load(openId)
      if (recovered != null) {
        setActiveProtocol(recovered)
        flowStep = FlowStep.Hub
      } else {
        prefs.edit().remove(KEY_OPEN).apply()
      }
    }
  }

  fun photoDir(): File? = working?.let { store.photoDir(it.id) }
  fun signatureDir(): File? = working?.let { store.signatureDir(it.id) }

  private fun setActiveProtocol(protocol: Protocol?) {
    commitUndo()
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
    commitUndo()
    val p = working ?: return
    if (p.isFinalized() || finalizing) return
    val edited = transform(p)
    if (edited == p) return
    working = edited.copy(contentRevision = p.contentRevision + 1, exportedEpochMs = 0)
    finalizationIssues = emptyList()
    finalizationError = false
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
    val after = edited.copy(contentRevision = before.contentRevision + 1, exportedEpochMs = 0)
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
      kotlinx.coroutines.delay(6_000L)
      commitUndo(token)
    }
  }

  fun undo(token: Long) {
    val pending = pendingUndo?.takeIf { it.token == token } ?: return
    val current = working
    if (current?.id == pending.before.id && current.contentRevision == pending.afterRevision) {
      working = pending.before.copy(
        contentRevision = current.contentRevision + 1,
        exportedEpochMs = 0,
      )
      undoJob?.cancel()
      pendingUndo = null
      undoNotice = null
      scheduleSave()
    } else {
      commitUndo(token)
    }
  }

  fun dismissUndo(token: Long) {
    commitUndo(token)
  }

  private fun commitUndo(token: Long? = null) {
    val pending = pendingUndo ?: return
    if (token != null && pending.token != token) return
    pending.files.forEach { file -> runCatching { file.delete() } }
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
    working = edited
    finalizationIssues = emptyList()
    finalizationError = false
    scheduleSave()
  }

  /** Metadata captured after finalization, such as handing over an identical copy. */
  private fun editMetadata(transform: (Protocol) -> Protocol) {
    commitUndo()
    val p = working ?: return
    working = transform(p)
    scheduleSave()
  }

  fun recordDelivery(
    role: Role,
    method: dev.verso.limen.data.DeliveryMethod,
    note: String = "",
  ) {
    val p = working ?: return
    if (!p.isFinalized() || !p.isExported() || role !in listOf(Role.Tenant, Role.Landlord)) return
    if (method == dev.verso.limen.data.DeliveryMethod.Other && note.isBlank()) return
    val changedAt = System.currentTimeMillis()
    editMetadata { record ->
      record.copy(
        deliveryReceipts = record.deliveryReceipts.filterNot { it.role == role } +
          dev.verso.limen.data.DeliveryReceipt(
            role = role,
            epochMs = changedAt,
            method = method,
            note = note.trim(),
          ),
        deliveryMetadataEpochMs = changedAt,
      )
    }
  }

  fun clearDelivery(role: Role) {
    if (working?.isFinalized() != true) return
    editMetadata { record ->
      record.copy(
        deliveryReceipts = record.deliveryReceipts.filterNot { it.role == role },
        deliveryMetadataEpochMs = System.currentTimeMillis(),
      )
    }
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
    commitUndo()
    saveJob?.cancel()
    saveJob = null
    val p = working ?: return
    runCatching { store.save(p) }
  }

  override fun onCleared() {
    flush()
    super.onCleared()
  }

  /** Starts an immutable session identity and then opens the guided handover. */
  fun startNewSession(role: Role, name: String, kind: Kind) {
    chooseRole(role, name)
    createProtocol(kind, role, name)
  }

  /** Starts the walk only after the new-session form has supplied a complete validated identity. */
  fun startNewSession(role: Role, identity: SessionIdentity, kind: Kind) {
    if (!identity.hasRequiredFields()) return
    val name = identity.fullName
    chooseRole(role, name, identity.company)
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

  fun createProtocol(
    kind: Kind,
    sessionRole: Role? = myRole,
    sessionName: String = profileName,
    sessionIdentity: SessionIdentity = SessionIdentity(firstName = sessionName),
  ) {
    flush()
    val now = System.currentTimeMillis()
    val resolvedName = sessionIdentity.fullName.ifBlank { sessionName.trim() }
    val resolvedIdentity = sessionIdentity.copy(
      phone = dev.verso.limen.data.InternationalPhone.format(
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
          // letter goes to, so it lands in the form's "Neue Adresse" field directly.
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
            resolvedIdentity.company.isNotBlank() -> resolvedIdentity.company
            else -> resolvedName
          },
          representedBy = if (sessionRole == Role.Landlord && resolvedIdentity.company.isNotBlank()) {
            resolvedName
          } else "",
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
    store.save(p)
    setActiveProtocol(p)
    flowStep = FlowStep.Hub
    openRoomId = null
    prefs.edit().putString(KEY_OPEN, p.id).apply()
  }

  /**
   * The exit protocol grown from the stored entry one: the flat's structure, the parties,
   * the deposit and the flags of what was new at entry carry over; the grades and defects
   * do not, because the walk is about to find out.
   */
  fun seedExitFrom(
    entry: Protocol,
    sessionRole: Role = myRole ?: Role.Tenant,
    sessionName: String = profileName,
  ) {
    if (!entry.isFinalized()) return
    chooseRole(sessionRole, sessionName)
    flush()
    val now = System.currentTimeMillis()
    val p = entry.copy(
      id = "p$now",
      kind = Kind.Exit,
      sessionRole = sessionRole,
      sessionName = sessionName.trim(),
      controllerIdentity = if (controllerIdentity.isBlank()) {
        ControllerIdentity(contactName = sessionName.trim())
      } else {
        controllerIdentity
      },
      createdEpochMs = now,
      handoverDate = LocalDate.now().toString(),
      handoverTime = "",
      lastProtocolDate = entry.handoverDate,
      rooms = entry.rooms.map(::freshRoom),
      roomSetupComplete = true,
      objects = entry.objects.map(::freshObject),
      defects = emptyList(),
      remarks = "",
      chargesStatementBy = "",
      oneSided = false,
      unilateralReason = null,
      unilateralNote = "",
      legalOverlay = dev.verso.limen.data.LegalOverlayRecord(),
      keyTransfer = dev.verso.limen.data.KeyTransferStatus.NotRecorded,
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
    )
    store.save(p)
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
      phone = dev.verso.limen.data.InternationalPhone.format(identity.phone, identity.phoneRegion),
    )
    chooseRole(sessionRole, name, identity.company)
    flush()
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
      parties = entry.parties.map { party ->
        when {
          party.role != sessionRole -> party
          // The agency's exit session: firm on the landlord line, the person sent behind
          // "vertreten durch", matching how createProtocol writes an agency entry.
          sessionRole == Role.Landlord && resolvedIdentity.company.isNotBlank() -> party.copy(
            name = resolvedIdentity.company,
            representedBy = name,
            phone = resolvedIdentity.phone,
          )
          // The leaving tenant's contact address is the new address of the exit form.
          sessionRole == Role.Tenant -> party.copy(
            name = name,
            address = listOf(resolvedIdentity.addressLine, resolvedIdentity.placeLine)
              .filter { it.isNotBlank() }.joinToString(", "),
            phone = resolvedIdentity.phone,
          )
          else -> party.copy(name = name, phone = resolvedIdentity.phone)
        }
      },
      rooms = entry.rooms.map(::freshRoom),
      roomSetupComplete = true,
      objects = entry.objects.map(::freshObject),
      defects = emptyList(),
      remarks = "",
      chargesStatementBy = "",
      oneSided = false,
      unilateralReason = null,
      unilateralNote = "",
      legalOverlay = dev.verso.limen.data.LegalOverlayRecord(),
      keyTransfer = dev.verso.limen.data.KeyTransferStatus.NotRecorded,
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
    )
    store.save(p)
    setActiveProtocol(p)
    flowStep = FlowStep.Hub
    openRoomId = null
    prefs.edit().putString(KEY_OPEN, p.id).apply()
  }

  fun openProtocol(id: String) {
    flush()
    val p = store.load(id) ?: return
    setActiveProtocol(p)
    // A reopened record lands on its hub whether it is signed or not. A finalized one used
    // to open straight onto Export with every way back out of the flow, which made the
    // signed protocol the one thing in the app nobody could go back and read.
    flowStep = FlowStep.Hub
    openRoomId = null
    prefs.edit().putString(KEY_OPEN, id).apply()
  }

  fun deleteProtocol(id: String) {
    val deleted = protocols.firstOrNull { it.id == id } ?: store.load(id)
    store.delete(id)
    protocols = store.list()
    // A draft belongs to the person who started this one handover. Once it is discarded,
    // ask who holds the phone again instead of carrying that session's side forward.
    if (deleted?.isFinalized() == false) clearProfile()
  }

  fun closeFlow() {
    flush()
    setActiveProtocol(null)
    editingDefectId = null
    openRoomId = null
    exported = null
    exportIssue = null
    finalizationIssues = emptyList()
    finalizationError = false
    prefs.edit().remove(KEY_OPEN).apply()
    protocols = store.list()
  }

  /**
   * Every place is reachable in every state. A finalized record is walked exactly like a
   * draft and refuses every edit instead, which [edit] enforces at the one point all writes
   * pass through. Locking the navigation as well was belt and braces that cost the reading.
   */
  fun goTo(step: FlowStep) {
    flowStep = step
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
    )
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
            pendingRecordReservations = emptyMap(),
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
            deliveryMetadataEpochMs = 0,
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
        flowStep = FlowStep.Hub
        prefs.edit().putString(KEY_OPEN, draft.id).apply()
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
      )
    }
    if (openRoomId == roomId) openRoomId = null
  }

  /** Clears every finding in this room, leaving the room itself on the walk. */
  fun clearRoom(roomId: String) {
    if (contentLocked()) return
    val gone = working?.defects?.filter { it.roomId == roomId }.orEmpty()
    deletePhotosOf(gone.flatMap(Defect::photoFiles))
    edit { p -> p.copy(defects = p.defects.filterNot { it.roomId == roomId }) }
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

  fun addDossierCard(card: DossierCard) = setDossier { it + card }

  /** Removing a component takes its content with it, so the record never prints a stale field. */
  fun removeDossierCard(card: DossierCard) {
    setDossier { it - card }
    edit { p ->
      when (card) {
        DossierCard.Contract -> p.copy(contract = Contract())
        DossierCard.Deposit -> p.copy(
          deposit = Deposit(),
          legalOverlay = p.legalOverlay.withoutStatus(
            dev.verso.limen.data.LegalRequirement.DepositProof,
          ),
        )
        DossierCard.Meters -> p.copy(meters = Meters())
        DossierCard.Keys -> p.copy(
          keys = emptyList(),
          selfMadeKeys = false,
          keyTransfer = dev.verso.limen.data.KeyTransferStatus.NotRecorded,
          keyTransferNote = "",
        )
        DossierCard.Notes -> p.copy(remarks = "", chargesStatementBy = "")
      }
    }
  }

  /**
   * Seeds from the content-derived answer before applying the change, so a draft written
   * before the dossier existed does not lose its deposit the moment a meter is added.
   */
  private fun setDossier(transform: (List<DossierCard>) -> List<DossierCard>) {
    edit { p ->
      p.copy(dossier = transform(p.dossierCards()).distinct().sortedBy { it.ordinal })
    }
  }

  // ── Defects ──────────────────────────────────────────────────────────────────────────

  fun openDefect(roomId: String, item: ItemKey?, itemLabel: String = "") {
    if (contentLocked()) return
    val id = "d${System.currentTimeMillis()}"
    edit { p ->
      p.copy(defects = p.defects + Defect(
        id = id,
        roomId = roomId,
        item = item,
        itemLabel = itemLabel.trim(),
      ))
    }
    editingDefectId = id
  }

  /** A finding about a supplied object rather than a surface of the flat. */
  fun openObjectDefect(objectId: String) {
    if (contentLocked()) return
    val id = "d${System.currentTimeMillis()}"
    edit { p -> p.copy(defects = p.defects + Defect(id = id, objectId = objectId)) }
    editingDefectId = id
  }

  /** Opens a finding. On a finalized record the editor is the reader; nothing it shows moves. */
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
    if (d != null && d.text.isBlank() && d.photos.isEmpty()) removeDefect(id, allowUndo = false)
    editingDefectId = null
  }

  fun removeDefect(id: String, allowUndo: Boolean = true) {
    if (contentLocked()) return
    val gone = working?.defects?.firstOrNull { it.id == id }
    if (allowUndo) {
      reversibleEdit(UndoKind.Defect, gone?.photoFiles().orEmpty()) { p ->
        p.copy(defects = p.defects.filterNot { it.id == id })
      }
    } else {
      deletePhotosOf(gone?.photoFiles().orEmpty())
      edit { p -> p.copy(defects = p.defects.filterNot { it.id == id }) }
    }
    if (editingDefectId == id) editingDefectId = null
  }

  private fun deletePhotosOf(names: List<String>) {
    val dir = photoDir() ?: return
    names.distinct().filter(::safeEvidenceName).forEach { name -> File(dir, name).delete() }
  }

  private fun safeEvidenceName(name: String): Boolean =
    name == File(name).name && name !in setOf(".", "..")

  // ── Photographs ──────────────────────────────────────────────────────────────────────

  /**
   * A photo the camera or picker delivered, preserved byte-for-byte beside the protocol and
   * hashed at once, because the hash is what the fingerprint later covers. The owner is a
   * defect or a supplied object; the file is named after it either way.
   */
  private fun copyPhoto(ownerId: String, existing: Int, source: File, photoSource: PhotoSource): PhotoEvidence? {
    val dir = photoDir() ?: return null
    val ownerToken = ownerId
      .takeIf { safeEvidenceName(it) && it.matches(Regex("[A-Za-z0-9._-]+")) }
      ?.take(80)
      ?: "e${ownerId.hashCode().toUInt().toString(16)}"
    val name = "$ownerToken-${System.currentTimeMillis()}-${existing + 1}.jpg"
    val target = File(dir, name)
    runCatching { source.copyTo(target, overwrite = true) }.getOrElse { return null }
    source.delete()
    return PhotoEvidence(
      file = name,
      sha256 = ProtocolIntegrity.fileHash(target),
      capturedEpochMs = System.currentTimeMillis(),
      source = photoSource,
    )
  }

  fun attachPhoto(defectId: String, source: File, photoSource: PhotoSource = PhotoSource.Unknown) {
    if (contentLocked()) return
    val had = working?.defects?.firstOrNull { it.id == defectId }?.photos?.size ?: 0
    val evidence = copyPhoto(defectId, had, source, photoSource) ?: return
    updateDefect(defectId) {
      it.copy(
        photos = it.photos + evidence.file,
        photoEvidence = it.photoEvidence.filterNot { photo -> photo.file == evidence.file } + evidence,
      )
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
    val had = working?.objects?.firstOrNull { it.id == objectId }?.photos?.size ?: 0
    val evidence = copyPhoto(objectId, had, source, photoSource) ?: return
    updateObject(objectId) {
      it.copy(
        photos = it.photos + evidence.file,
        photoEvidence = it.photoEvidence.filterNot { photo -> photo.file == evidence.file } + evidence,
      )
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

  // ── Signatures ───────────────────────────────────────────────────────────────────────

  private fun deleteSignatureFile(name: String) {
    if (!safeEvidenceName(name)) return
    signatureDir()?.let { File(it, name).delete() }
  }

  /** Keeps the ink: the PNG lands beside the protocol, the record points at it. */
  fun keepSignature(role: Role, plane: Plane, bitmap: android.graphics.Bitmap) {
    if (contentLocked()) return
    val dir = signatureDir() ?: return
    val name = "${role.name.lowercase()}-${plane.name.lowercase()}.png"
    File(dir, name).outputStream().use {
      bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }
    val partyName = working?.parties?.firstOrNull { it.role == role }?.name.orEmpty()
    editSignatures { p ->
      val reservation = (
        p.pendingRecordReservations[role]
          ?: p.signatures.firstOrNull { it.role == role && it.plane == plane }?.reservation
        ).orEmpty().trim()
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane } +
          dev.verso.limen.data.Signature(
            role = role,
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
  fun refuseSignature(role: Role, plane: Plane) {
    if (contentLocked()) return
    val existing = working?.signatures?.firstOrNull { it.role == role && it.plane == plane }
    if (existing != null && existing.file.isNotBlank()) {
      deleteSignatureFile(existing.file)
    }
    val partyName = working?.parties?.firstOrNull { it.role == role }?.name.orEmpty()
    editSignatures { p ->
      val reservation = (
        p.pendingRecordReservations[role]
          ?: p.signatures.firstOrNull { it.role == role && it.plane == plane }?.reservation
        ).orEmpty().trim()
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane } +
          dev.verso.limen.data.Signature(
            role = role,
            name = partyName,
            plane = plane,
            refused = true,
            reservation = reservation,
            epochMs = System.currentTimeMillis(),
            contentRevision = p.contentRevision,
          )
      )
    }
  }

  fun clearSignature(role: Role, plane: Plane) {
    if (contentLocked()) return
    val existing = working?.signatures?.firstOrNull { it.role == role && it.plane == plane }
    if (existing != null && existing.file.isNotBlank()) {
      deleteSignatureFile(existing.file)
    }
    editSignatures { p ->
      val reservation = existing?.reservation.orEmpty()
      p.copy(
        signatures = p.signatures.filterNot { it.role == role && it.plane == plane },
        pendingRecordReservations = if (reservation.isBlank()) p.pendingRecordReservations else {
          p.pendingRecordReservations + (role to reservation)
        },
      )
    }
  }

  fun setRecordReservation(role: Role, text: String) {
    // A reservation is printed in the shared record. Changing it after another person
    // signed must therefore move the revision and require that signature to be reviewed.
    edit { p ->
      p.copy(
        pendingRecordReservations = if (text.isBlank()) {
          p.pendingRecordReservations - role
        } else {
          p.pendingRecordReservations + (role to text)
        }
      )
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
}
