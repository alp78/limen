package dev.verso.limen.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.verso.limen.data.Contract
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
  HandoverDate,
  HandoverTime,
  Parties,
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

  /** Starts an immutable session identity and then opens the guided handover. */
  fun startNewSession(role: Role, name: String, kind: Kind) {
    chooseRole(role, name)
    createProtocol(kind, role, name)
  }

  /** Starts the walk only after the new-session form has supplied a complete validated identity. */
  fun startNewSession(role: Role, identity: SessionIdentity, kind: Kind) {
    if (!identity.hasRequiredFields()) return
    val name = identity.fullName
    chooseRole(role, name)
    createProtocol(kind, role, name, identity)
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
    // Rooms start empty. A room's record is its defects, and the walking order is only a
    // suggestion: every one of these is removable and any other kind can be added.
    val rooms = Templates.defaultRooms.mapIndexed { i, rk -> Room(id = "r$i", kind = rk) }
    val p = Protocol(
      id = "p$now",
      kind = kind,
      sessionRole = sessionRole,
      sessionName = resolvedName,
      sessionIdentity = resolvedIdentity,
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
          phone = if (sessionRole == Role.Tenant) resolvedIdentity.phone else "",
        ),
        Party(
          Role.Landlord,
          name = if (sessionRole == Role.Landlord) resolvedName else "",
          phone = if (sessionRole == Role.Landlord) resolvedIdentity.phone else "",
        ),
      ),
      keys = listOf(
        KeyCount(KeyKind.Apartment),
        KeyCount(KeyKind.HouseDoor),
        KeyCount(KeyKind.Mailbox),
        KeyCount(KeyKind.Cellar),
      ),
      rooms = rooms,
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
      createdEpochMs = now,
      handoverDate = LocalDate.now().toString(),
      handoverTime = "",
      lastProtocolDate = entry.handoverDate,
      rooms = entry.rooms.map(::freshRoom),
      objects = entry.objects.map(::freshObject),
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
  private fun freshRoom(room: Room): Room = room.copy(items = emptyList(), keysCount = 0)

  /** The supplied object is still supplied; the photographs of it at entry are not its exit state. */
  private fun freshObject(entry: ObjectEntry): ObjectEntry =
    entry.copy(photos = emptyList(), photoEvidence = emptyList())

  /** Opens an exit from its entry baseline while retaining the new starter's verified contact data. */
  fun seedExitFrom(entry: Protocol, sessionRole: Role, identity: SessionIdentity) {
    if (!identity.hasRequiredFields()) return
    if (!entry.isFinalized()) return
    val name = identity.fullName
    val resolvedIdentity = identity.copy(
      phone = dev.verso.limen.data.InternationalPhone.format(identity.phone, identity.phoneRegion),
    )
    chooseRole(sessionRole, name)
    flush()
    val now = System.currentTimeMillis()
    val p = entry.copy(
      id = "p$now",
      kind = Kind.Exit,
      sessionRole = sessionRole,
      sessionName = name,
      sessionIdentity = resolvedIdentity,
      createdEpochMs = now,
      handoverDate = LocalDate.now().toString(),
      handoverTime = "",
      lastProtocolDate = entry.handoverDate,
      parties = entry.parties.map { party ->
        if (party.role == sessionRole) party.copy(name = name, phone = resolvedIdentity.phone) else party
      },
      rooms = entry.rooms.map(::freshRoom),
      objects = entry.objects.map(::freshObject),
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
    return buildList {
      if (record.oneSided) {
        (record.sessionRole ?: myRole)?.let(::add)
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
      // No room-by-room gate. A flat with nothing wrong in it is a complete finding, and
      // demanding a tap on every undamaged surface only teaches people to tap without looking.
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

  fun removeRoom(roomId: String) {
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

  /** A removed object takes its photographs and any finding recorded on it with it. */
  fun removeObject(id: String) {
    val entry = working?.objects?.firstOrNull { it.id == id }
    val gone = working?.defects?.filter { it.objectId == id }.orEmpty()
    deletePhotosOf(entry?.photoFiles().orEmpty() + gone.flatMap(Defect::photoFiles))
    edit { p ->
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
        DossierCard.Deposit -> p.copy(deposit = Deposit())
        DossierCard.Meters -> p.copy(meters = Meters())
        DossierCard.Keys -> p.copy(keys = emptyList(), selfMadeKeys = false)
        DossierCard.Notes -> p.copy(remarks = "", chargesStatementBy = "", oneSided = false)
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
    if (working?.isFinalized() == true) return
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
    if (working?.isFinalized() == true) return
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
    if (d != null && d.text.isBlank() && d.photos.isEmpty()) removeDefect(id)
    editingDefectId = null
  }

  fun removeDefect(id: String) {
    if (working?.isFinalized() == true) return
    val gone = working?.defects?.firstOrNull { it.id == id }
    deletePhotosOf(gone?.photoFiles().orEmpty())
    edit { p -> p.copy(defects = p.defects.filterNot { it.id == id }) }
    if (editingDefectId == id) editingDefectId = null
  }

  private fun deletePhotosOf(names: List<String>) {
    val dir = photoDir() ?: return
    names.distinct().forEach { name -> File(dir, name).delete() }
  }

  // ── Photographs ──────────────────────────────────────────────────────────────────────

  /**
   * A photo the camera or picker delivered, preserved byte-for-byte beside the protocol and
   * hashed at once, because the hash is what the fingerprint later covers. The owner is a
   * defect or a supplied object; the file is named after it either way.
   */
  private fun copyPhoto(ownerId: String, existing: Int, source: File, photoSource: PhotoSource): PhotoEvidence? {
    val dir = photoDir() ?: return null
    val name = "$ownerId-${System.currentTimeMillis()}-${existing + 1}.jpg"
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
    if (working?.isFinalized() == true) return
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
    if (working?.isFinalized() == true) return
    deletePhotosOf(listOf(name))
    updateDefect(defectId) {
      it.copy(
        photos = it.photos - name,
        photoEvidence = it.photoEvidence.filterNot { photo -> photo.file == name },
      )
    }
  }

  fun attachObjectPhoto(objectId: String, source: File, photoSource: PhotoSource = PhotoSource.Unknown) {
    if (working?.isFinalized() == true) return
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
    if (working?.isFinalized() == true) return
    deletePhotosOf(listOf(name))
    updateObject(objectId) {
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
