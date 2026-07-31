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
private const val KEY_ROLE = "myRole"
private const val KEY_NAME = "profileName"

/**
 * Where the instrument is. [Home] is the case: the list of protocols and the way into a
 * new one. [Value] is the Zeitwert calculator, [Clocks] the legal deadlines. The protocol
 * flow is not a face: it opens over whatever face is current and owns the screen until
 * it is closed, and it is restored after a process death, because the camera app kills
 * this process routinely mid-handover.
 */
enum class AppMode { Home, Value, Clocks, Link }

/** The stepper's stations, in walking order. */
enum class FlowStep { Setup, Rooms, Review, Sign, Export }

class LimenViewModel(app: Application) : AndroidViewModel(app) {

  private val prefs = app.getSharedPreferences("limen", Context.MODE_PRIVATE)
  private val store = Store(app.filesDir)
  private val ledger = dev.verso.limen.data.Ledger(app.filesDir)

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

  // ── The journal ──────────────────────────────────────────────────────────────────────

  /** One signed, chained entry per discrete act; typing is captured by the next state hash. */
  private fun log(kind: dev.verso.limen.data.EventKind, summary: String) {
    val p = working
    runCatching {
      ledger.append(
        dossierId = p?.id ?: "link",
        kind = kind,
        summary = summary,
        stateHash = p?.let { dev.verso.limen.data.Seal.contentHash(it, null, null) } ?: "",
        signer = dev.verso.limen.data.DeviceKey,
        actorName = profileName,
        actorRole = myRole ?: Role.Tenant,
        epochMs = System.currentTimeMillis(),
      )
    }
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
    log(dev.verso.limen.data.EventKind.DossierOpened, kind.name)
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
    log(dev.verso.limen.data.EventKind.DossierOpened, "Exit · seed ${entry.id}")
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
    ledger.delete(id)
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
    val s = Str(lang)
    val room = working?.rooms?.firstOrNull { it.id == roomId }
    val item = room?.items?.getOrNull(index)
    log(
      dev.verso.limen.data.EventKind.GradeSet,
      listOfNotNull(
        room?.let { it.name.ifBlank { s.room(it.kind) } },
        item?.let { s.item(it.key) },
        item?.let { s.grade(it.grade) },
      ).joinToString(" · "),
    )
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
    log(dev.verso.limen.data.EventKind.RoomAdded, name.ifBlank { Str(lang).room(kind) })
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
    log(
      dev.verso.limen.data.EventKind.RoomRemoved,
      gone?.let { it.name.ifBlank { Str(lang).room(it.kind) } } ?: roomId,
    )
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
    val before = working?.defects?.firstOrNull { it.id == id }
    edit { p -> p.copy(defects = p.defects.map { if (it.id == id) transform(it) else it }) }
    val after = working?.defects?.firstOrNull { it.id == id }
    // The charge is the legally loaded bit of a defect: its flips are journaled by name.
    if (before != null && after != null && before.chargedToTenant != after.chargedToTenant) {
      log(
        dev.verso.limen.data.EventKind.DefectCharged,
        "${defectWhere(after)} · ${after.chargedToTenant}",
      )
    }
  }

  /** Closing the editor discards an empty shell rather than keeping a blank finding. */
  fun closeDefect() {
    val id = editingDefectId ?: return
    val d = working?.defects?.firstOrNull { it.id == id }
    if (d != null && d.text.isBlank() && d.photos.isEmpty()) {
      removeDefect(id)
    } else if (d != null) {
      log(
        dev.verso.limen.data.EventKind.DefectAdded,
        "${defectWhere(d)} · ${d.text.take(60)}",
      )
    }
    editingDefectId = null
  }

  fun removeDefect(id: String) {
    val dir = photoDir()
    val gone = working?.defects?.firstOrNull { it.id == id }
    gone?.photos?.forEach { name ->
      if (dir != null) File(dir, name).delete()
    }
    edit { p -> p.copy(defects = p.defects.filterNot { it.id == id }) }
    if (editingDefectId == id) editingDefectId = null
    if (gone != null && (gone.text.isNotBlank() || gone.photos.isNotEmpty())) {
      log(dev.verso.limen.data.EventKind.DefectRemoved, defectWhere(gone))
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

  /** A photo the camera or picker delivered, moved from the cache into the protocol. */
  fun attachPhoto(defectId: String, source: File) {
    val dir = photoDir() ?: return
    val count = working?.defects?.firstOrNull { it.id == defectId }?.photos?.size ?: 0
    val name = "$defectId-${count + 1}.jpg"
    source.copyTo(File(dir, name), overwrite = true)
    source.delete()
    updateDefect(defectId) { it.copy(photos = it.photos + name) }
    log(dev.verso.limen.data.EventKind.PhotoAdded, name)
  }

  fun removePhoto(defectId: String, name: String) {
    photoDir()?.let { File(it, name).delete() }
    updateDefect(defectId) { it.copy(photos = it.photos - name) }
    log(dev.verso.limen.data.EventKind.PhotoRemoved, name)
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
    log(dev.verso.limen.data.EventKind.SignatureKept, "${Str(lang).role(role)} · ${plane.name}")
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
    log(dev.verso.limen.data.EventKind.SignatureRefused, "${Str(lang).role(role)} · ${plane.name}")
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
          // The seal pipeline: fingerprint the content, print it inside the PDF, hash the
          // PDF as it streams out, then sign both digests with this device's key.
          val contentHash =
            dev.verso.limen.data.Seal.contentHash(p, store.photoDir(p.id), store.signatureDir(p.id))
          val out = dev.verso.limen.data.PdfExport.export(
            getApplication(),
            p,
            Str(docLang),
            store.photoDir(p.id),
            store.signatureDir(p.id),
            contentHash,
          )
          val seal = dev.verso.limen.data.Seal.makeSeal(
            dev.verso.limen.data.DeviceKey, contentHash, out.pdfSha256, System.currentTimeMillis(),
          )
          out to seal
        }
      }
      exporting = false
      result.fold(
        onSuccess = { (out, seal) ->
          exported = out.file
          // A re-export is a new document: the old counterseal no longer matches it.
          edit { q ->
            q.copy(exportedEpochMs = System.currentTimeMillis(), seal = seal, counterSeal = null)
          }
          log(dev.verso.limen.data.EventKind.Exported, out.file.displayName)
        },
        onFailure = { exportError = true },
      )
    }
  }

  // ── The seal exchange ────────────────────────────────────────────────────────────────

  /** The countersign face open over the case: this phone signing the OTHER phone's PDF. */
  var counterSigning by mutableStateOf(false)
    private set

  fun openCounterSign() {
    counterSigning = true
  }

  fun closeCounterSign() {
    counterSigning = false
  }

  /** Whether the last counterseal file failed: null quiet, false mismatch, true attached. */
  var counterSealResult by mutableStateOf<Boolean?>(null)
    private set

  /** Reads a .limensig the other phone produced, verifies it against the seal, keeps it. */
  fun attachCounterSeal(uri: android.net.Uri) {
    val p = working ?: return
    val seal = p.seal ?: return
    viewModelScope.launch {
      val attached = withContext(Dispatchers.IO) {
        runCatching {
          val text = getApplication<Application>().contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@runCatching null
          val c = dev.verso.limen.data.Seal.decodeCounterSeal(text) ?: return@runCatching null
          val matches = c.forPdfHash == seal.pdfHash &&
            dev.verso.limen.data.Seal.verifyCounterSeal(c)
          if (matches) c else null
        }.getOrNull()
      }
      if (attached != null) {
        edit { q -> q.copy(counterSeal = attached) }
        counterSealResult = true
        log(
          dev.verso.limen.data.EventKind.CounterSealed,
          "${attached.name} · ${Str(lang).role(attached.role)}",
        )
      } else {
        counterSealResult = false
      }
    }
  }

  /** The one-page certificate naming both digests, both keys and both signatures. */
  fun exportCertificate() {
    val p = working ?: return
    if (exporting) return
    exporting = true
    val docLang = pdfLang ?: lang
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          dev.verso.limen.data.SealCertificate.export(getApplication(), p, Str(docLang))
        }
      }
      exporting = false
      result.fold(onSuccess = { exported = it }, onFailure = { exportError = true })
    }
  }

  /** The letter asking a neutral official to record the facts, for the dispute that came. */
  fun exportBefund(commune: String) {
    val p = working ?: return
    if (exporting) return
    exporting = true
    val docLang = pdfLang ?: lang
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          dev.verso.limen.data.Befund.export(getApplication(), p, Str(docLang), commune)
        }
      }
      exporting = false
      result.fold(onSuccess = { exported = it }, onFailure = { exportError = true })
    }
  }

  // ── The deposit comparison ───────────────────────────────────────────────────────────

  var showDepositCompare by mutableStateOf(false)
    private set

  fun setDepositCompare(open: Boolean) {
    showDepositCompare = open
  }

  // ── The handshake ────────────────────────────────────────────────────────────────────

  /** The sealed counterparty, or null while the linked features stay locked. */
  var pair by mutableStateOf(dev.verso.limen.data.Pairing.load(app.filesDir))
    private set

  /** A received key card awaiting the spoken fingerprint check. */
  var importedCard by mutableStateOf<dev.verso.limen.data.KeyCard?>(null)
    private set

  var importCardError by mutableStateOf(false)
    private set

  var keyCardFile by mutableStateOf<dev.verso.limen.data.ExportedFile?>(null)
    private set

  fun myKeyFingerprint(): String =
    dev.verso.limen.data.Seal.keyFingerprint(dev.verso.limen.data.DeviceKey.publicKeyBase64)

  fun jointFingerprint(): String? {
    val other = importedCard?.publicKey ?: pair?.publicKey ?: return null
    return dev.verso.limen.data.Pairing.jointFingerprint(
      dev.verso.limen.data.DeviceKey.publicKeyBase64, other,
    )
  }

  fun exportKeyCard() {
    viewModelScope.launch {
      keyCardFile = withContext(Dispatchers.IO) {
        runCatching {
          val card = dev.verso.limen.data.Pairing.makeCard(
            dev.verso.limen.data.DeviceKey,
            profileName,
            myRole ?: Role.Tenant,
            System.currentTimeMillis(),
          )
          dev.verso.limen.data.writeToDownloads(
            getApplication(),
            "limen-key-${dev.verso.limen.data.exportStamp()}.limenpair",
            "application/json",
          ) { it.write(dev.verso.limen.data.Pairing.encodeCard(card).toByteArray(Charsets.UTF_8)) }
        }.getOrNull()
      }
    }
  }

  fun importKeyCard(uri: android.net.Uri) {
    viewModelScope.launch {
      val card = withContext(Dispatchers.IO) {
        runCatching {
          getApplication<Application>().contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?.let { dev.verso.limen.data.Pairing.decodeCard(it) }
        }.getOrNull()
      }
      importedCard = card
      importCardError = card == null
    }
  }

  /** Both people compared the joint fingerprint aloud; from here the link is sealed. */
  fun sealPair() {
    val card = importedCard ?: return
    val record = dev.verso.limen.data.PairRecord(
      name = card.name,
      role = card.role,
      publicKey = card.publicKey,
      sealedEpochMs = System.currentTimeMillis(),
    )
    dev.verso.limen.data.Pairing.save(getApplication<Application>().filesDir, record)
    pair = record
    importedCard = null
    log(
      dev.verso.limen.data.EventKind.PairSealed,
      "${card.name} · ${Str(lang).role(card.role)} · ${jointFingerprint().orEmpty()}",
    )
  }

  fun unpair() {
    dev.verso.limen.data.Pairing.clear(getApplication<Application>().filesDir)
    pair = null
    importedCard = null
    log(dev.verso.limen.data.EventKind.ProtocolEdit, "link removed")
  }

  // ── The audit journal ────────────────────────────────────────────────────────────────

  var auditFile by mutableStateOf<dev.verso.limen.data.ExportedFile?>(null)
    private set

  /** Every chain on this phone, printed and verified: the link's and each protocol's. */
  fun exportAudit() {
    val docLang = pdfLang ?: lang
    viewModelScope.launch {
      auditFile = withContext(Dispatchers.IO) {
        runCatching {
          val s = Str(docLang)
          val chains = buildList {
            val linkEvents = ledger.events("link")
            if (linkEvents.isNotEmpty()) {
              add(dev.verso.limen.data.AuditExport.Chain(s.linkTitle, linkEvents, ledger.verify("link")))
            }
            store.list().forEach { p ->
              val events = ledger.events(p.id)
              if (events.isNotEmpty()) {
                val kind = if (p.kind == Kind.Entry) s.entry else s.exit
                add(
                  dev.verso.limen.data.AuditExport.Chain(
                    "${p.address.ifBlank { p.id }} · $kind",
                    events,
                    ledger.verify(p.id),
                  )
                )
              }
            }
          }
          dev.verso.limen.data.AuditExport.export(getApplication(), s, chains)
        }.getOrNull()
      }
      if (auditFile != null) log(dev.verso.limen.data.EventKind.AuditExported, auditFile!!.displayName)
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
