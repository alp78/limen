package dev.peringer.limen.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.peringer.limen.data.Appliance
import dev.peringer.limen.data.Defect
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.Furniture
import dev.peringer.limen.data.ItemKey
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.ObjectEntry
import dev.peringer.limen.data.ObjectGroup
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.SwissDate
import dev.peringer.limen.data.TenantResponse
import dev.peringer.limen.data.carries
import dev.peringer.limen.data.legalProfile
import dev.peringer.limen.data.resolvedLegalOverlay
import dev.peringer.limen.theme.Limen

/**
 * The protocol flow. Not a stepper any more: [FlowHub] is the home, and the two halves of a
 * handover, what is in the flat and what is on paper, are entered in whatever order the
 * handover actually takes. The draft is on disk after every keystroke, because the camera
 * app kills this process whenever it pleases.
 */
@Composable
fun FlowScreen(
  modifier: Modifier = Modifier,
  vm: LimenViewModel,
  s: Str,
  predictiveBackProgress: Float = 0f,
) {
  val p = vm.working ?: return
  val locked = p.isFinalized()
  val backAmount = kotlin.math.abs(predictiveBackProgress)
  CompositionLocalProvider(LocalRecordLocked provides locked) {
    Box(
      modifier
        .fillMaxSize()
        .graphicsLayer {
          translationX = size.width * 0.07f * predictiveBackProgress
          scaleX = 1f - backAmount * 0.018f
          scaleY = 1f - backAmount * 0.018f
          alpha = 1f - backAmount * 0.08f
        }
    ) {
      Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        FlowMast(vm, s, p)
        Spacer(Modifier.height(8.dp))
        // Export carries the amendment door in full, with the sentence that explains it.
        if (locked && vm.flowStep != FlowStep.Export) {
          SealBar(vm, s)
          Spacer(Modifier.height(8.dp))
        }
        AnimatedContent(
          targetState = vm.flowStep,
          modifier = Modifier.weight(1f),
          transitionSpec = {
            val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
            (fadeIn(tween(160)) + slideInHorizontally(tween(180)) { direction * it / 10 })
              .togetherWith(
                fadeOut(tween(120)) + slideOutHorizontally(tween(160)) { -direction * it / 12 }
              )
          },
          label = "handoverRoute",
        ) { step ->
          when (step) {
            FlowStep.Hub -> HubStep(vm, s, p)
            FlowStep.Inspection -> InspectionStep(vm, s, p)
            FlowStep.Objects -> ObjectsStep(vm, s, p)
            FlowStep.Dossier -> DossierStep(vm, s, p)
            FlowStep.Review -> ReviewStep(vm, s, p)
            FlowStep.Sign -> SignStep(vm, s, p)
            FlowStep.Export -> ExportStep(vm, s, p)
          }
        }
      }
      if (vm.editingDefectId != null) {
        DefectEditor(vm, s, p, vm.editingDefectId!!)
      }
      if (vm.showDepositCompare) {
        DepositCompareOverlay(vm, s)
      }
      vm.undoNotice?.let { notice ->
        Row(
          Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .background(Limen.colors.plateRaised, RoundedCornerShape(12.dp))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            s.removedForUndo(notice.kind),
            style = MaterialTheme.typography.bodyMedium,
            color = Limen.colors.ink,
            modifier = Modifier.weight(1f),
          )
          SmallKey(s.undo, tick = Tick.Confirm) { vm.undo(notice.token) }
        }
      }
    }
  }
}

/**
 * The flow's own masthead: the way out, where you are, what this place is for, the theme.
 *
 * The step's instruction lives behind the mark rather than in a paragraph over the controls.
 * It is read once, by the person who needs it, and it stops costing everyone else the top
 * fifth of every screen.
 */
@Composable
private fun FlowMast(vm: LimenViewModel, s: Str, p: Protocol) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    IconKey(ButtonGlyph.Back, s.back) { vm.flowBack() }
    Spacer(Modifier.width(6.dp))
    LimenMark(size = 42.dp)
    Spacer(Modifier.width(8.dp))
    Column(Modifier.weight(1f)) {
      Text(
        (if (p.kind == Kind.Entry) s.entry else s.exit).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Limen.colors.ink,
      )
      Text(
        p.address.ifBlank { s.noAddress },
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        color = Limen.colors.inkDim,
      )
    }
    InfoTip(s.tipStep(vm.flowStep))
    ThemeToggle(vm.darkTheme, s.themeMode, vm::toggleTheme)
  }
}

/** Says why nothing on this screen moves, and offers the one thing that does. */
@Composable
private fun SealBar(vm: LimenViewModel, s: Str) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(Limen.colors.plateRaised)
      .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      s.lockedRecord.uppercase(),
      fontFamily = FontFamily.Monospace,
      fontSize = 12.sp,
      color = Limen.colors.seal,
      modifier = Modifier.weight(1f),
    )
    InfoTip(s.tipLocked())
    // Named for where it goes, not for what is done there. Amending is a deliberate act
    // with its own key on that step; a one-tap amendment on every screen would make a
    // stray thumb start a second record.
    SmallKey(s.stepExport.uppercase()) { vm.goTo(FlowStep.Export) }
  }
}

// ── Naming a finding ───────────────────────────────────────────────────────────────────

/** Where a finding hangs: a room of the flat, or a supplied object. */
internal fun Protocol.defectPlace(d: Defect, s: Str): String {
  if (d.objectId.isNotBlank()) {
    return objects.firstOrNull { it.id == d.objectId }?.displayName(s) ?: s.objects
  }
  val room = rooms.firstOrNull { it.id == d.roomId } ?: return ""
  return room.name.ifBlank { s.room(room.kind) }
}

/**
 * The item a finding names, or null when it is about the room or the object as a whole.
 *
 * A finding written by this version carries its own label. Older records kept it on the
 * room's item row instead, so those are still resolved through the row.
 */
internal fun Protocol.defectItemLabel(d: Defect, s: Str): String? {
  val key = d.item ?: return null
  if (key == ItemKey.Custom) {
    if (d.itemLabel.isNotBlank()) return d.itemLabel
    val legacy = rooms.firstOrNull { it.id == d.roomId }
      ?.items?.firstOrNull { it.key == key && (d.itemId.isBlank() || it.itemId.isBlank() || d.itemId == it.itemId) }
    return legacy?.label?.takeIf { it.isNotBlank() } ?: s.item(key)
  }
  return s.item(key)
}

/** What an object row is called: its catalogue entry when it has one, else what was typed. */
internal fun ObjectEntry.displayName(s: Str): String = when (group) {
  ObjectGroup.Appliance ->
    Appliance.entries.firstOrNull { it.name == catalogue }?.let(s::appliance) ?: label
  ObjectGroup.Furniture ->
    Furniture.entries.firstOrNull { it.name == catalogue }?.let(s::furniture) ?: label
  ObjectGroup.Other -> label
}

// ── Shared keys and confirmations ──────────────────────────────────────────────────────

/** The one prominent action a place offers, coloured for what it does. */
@Composable
internal fun RoomActionKey(
  label: String,
  fill: Color,
  outline: Color,
  text: Color,
  glyph: ButtonGlyph? = null,
  modifier: Modifier = Modifier.fillMaxWidth(),
  onPress: () -> Unit,
) {
  KeySurface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    fillColor = fill,
    outlineColor = outline,
    onPress = onPress,
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      glyph?.let {
        Icon(it.vector(), contentDescription = null, tint = text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text(
        label,
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
        color = text,
      )
    }
  }
}

/** Asked before anything recorded is thrown away, and never for anything reversible. */
@Composable
internal fun ConfirmDialog(s: Str, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Limen.colors.plateRaised,
    titleContentColor = Limen.colors.ink,
    textContentColor = Limen.colors.inkDim,
    title = { Text(s.areYouSure, fontWeight = FontWeight.Bold) },
    text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = {
      RoomActionKey(
        label = s.yes,
        fill = Limen.colors.positive,
        outline = Limen.colors.positive,
        text = Limen.colors.onPositive,
        modifier = Modifier.width(88.dp),
        onPress = onConfirm,
      )
    },
    dismissButton = {
      RoomActionKey(
        label = s.no,
        fill = Limen.colors.danger,
        outline = Limen.colors.dangerOutline,
        text = Limen.colors.dangerOutline,
        modifier = Modifier.width(88.dp),
        onPress = onDismiss,
      )
    },
  )
}

@Composable
internal fun DismissibleItemRow(s: Str, onRemove: () -> Unit, content: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f)) { content() }
    Spacer(Modifier.width(6.dp))
    IconKey(
      ButtonGlyph.Delete,
      s.delete,
      destructive = true,
      tick = Tick.Reject,
      onPress = onRemove,
    )
  }
}

/** One finding, one line: what it says, what hangs on it, and the way into the editor. */
@Composable
internal fun DefectLine(vm: LimenViewModel, s: Str, p: Protocol, d: Defect, showPlace: Boolean = false) {
  val play = rememberTick()
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      // The row is the only way into the editor, so it is a key and presses like one.
      .clickable {
        play(Tick.Key)
        vm.editDefect(d.id)
      }
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        d.text.ifBlank { s.defectText },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        color = Limen.colors.ink,
      )
      val parts = mutableListOf<String>()
      if (showPlace) p.defectPlace(d, s).takeIf { it.isNotBlank() }?.let(parts::add)
      p.defectItemLabel(d, s)?.let(parts::add)
      if (d.photos.isNotEmpty()) parts.add("${s.photos} ${d.photos.size}")
      if (d.chargedToTenant) parts.add(s.chargedToTenant)
      if (d.tenantResponse != TenantResponse.NotStated) parts.add(s.tenantResponse(d.tenantResponse))
      if (parts.isNotEmpty()) {
        Text(
          parts.joinToString(" · "),
          style = MaterialTheme.typography.labelSmall,
          color = Limen.colors.inkDim,
        )
      }
    }
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = Limen.colors.inkDim)
  }
}

// ── Review ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReviewLine(label: String, value: String) {
  if (value.isBlank()) return
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim, modifier = Modifier.weight(0.42f))
    Text(value, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink, modifier = Modifier.weight(0.58f))
  }
}

@Composable
private fun ReviewIdentity(s: Str, p: Protocol) {
  Panel(label = s.objectLabel) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
      ReviewLine(s.protocolKind, if (p.kind == Kind.Entry) s.entry else s.exit)
      ReviewLine(s.address, p.address)
      ReviewLine(s.place, p.place)
      ReviewLine(s.cantonLabel, p.objectCanton?.let(s::canton).orEmpty())
      ReviewLine(s.objectDetail, p.objectDetail)
      // Swiss, like every other date the operator sees and every date on the paper. The
      // review used to be the one screen that showed the ISO form held on disk.
      ReviewLine(
        s.handoverDate,
        listOf(SwissDate.display(p.handoverDate), p.handoverTime)
          .filter { it.isNotBlank() }.joinToString(" · "),
      )
      ReviewLine(s.legalBasis, s.legalProfile(p.legalProfile()))
    }
  }
  Panel(label = s.parties) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      p.parties.filter { it.name.isNotBlank() }.forEach { party ->
        val detail = listOf(
          party.name,
          party.representedBy.takeIf { it.isNotBlank() }?.let { "${s.representedBy}: $it" },
          party.address.takeIf { it.isNotBlank() },
          party.phone.takeIf { it.isNotBlank() },
          party.email.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString(" · ")
        ReviewLine(s.role(party.role), detail)
      }
    }
  }
  if (!p.controllerIdentity.isBlank()) {
    Panel(label = s.controllerTitle) {
      Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ReviewLine(s.organisation, p.controllerIdentity.organisation)
        ReviewLine(s.controllerContact, p.controllerIdentity.contactName)
        ReviewLine(
          s.identityAddress,
          listOf(
            p.controllerIdentity.street,
            listOf(p.controllerIdentity.postalCode, p.controllerIdentity.city)
              .filter { it.isNotBlank() }.joinToString(" "),
          ).filter { it.isNotBlank() }.joinToString(", "),
        )
        ReviewLine(
          s.email,
          listOf(p.controllerIdentity.email, p.controllerIdentity.phone)
            .filter { it.isNotBlank() }.joinToString(" · "),
        )
      }
    }
  }
}

@Composable
private fun ReviewLegalOverlay(s: Str, p: Protocol) {
  val overlay = p.resolvedLegalOverlay() ?: return
  Panel(label = s.legalOverlayTitle) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CantonFlag(overlay.canton, contentDescription = null, size = 28.dp)
        Text(s.canton(overlay.canton), style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
      }
      if (p.kind == Kind.Entry) {
        ReviewLine(s.initialRentForm, s.initialRentFormRule(overlay.initialRentFormRule))
      }
      val requirements = if (p.isFinalized() && p.legalOverlay.checks.isNotEmpty()) {
        p.legalOverlay.checks.keys.sortedBy { it.ordinal }
      } else {
        overlay.requirements
      }
      requirements.forEach { requirement ->
        ReviewLine(
          s.legalRequirement(requirement),
          p.legalOverlay.status(requirement)?.let(s::legalCheckStatus) ?: s.stillMissing,
        )
      }
      ReviewLine(s.legalOverlayNote, p.legalOverlay.note)
    }
  }
}

@Composable
private fun ReviewInspection(s: Str, p: Protocol) {
  Panel(label = s.stepInspection) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
      p.rooms.forEach { room ->
        val roomName = room.name.ifBlank { s.room(room.kind) }
        val findingCount = p.defects.count { it.roomId == room.id && it.objectId.isBlank() }
        val state = listOf(
          if (room.inspected) s.roomInspected else s.notInspected,
          s.countDefects(findingCount).takeIf { findingCount > 0 },
        ).filterNotNull().joinToString(" · ")
        ReviewLine(roomName, state)
      }
      p.objects.forEach { entry ->
        val findingCount = p.defects.count { it.objectId == entry.id }
        val state = listOf(
          if (entry.inspected) s.objectInspected else s.notInspected,
          s.countDefects(findingCount).takeIf { findingCount > 0 },
        ).filterNotNull().joinToString(" · ")
        ReviewLine(entry.displayName(s), state)
      }
    }
  }
}

@Composable
private fun ReviewDossier(s: Str, p: Protocol) {
  Panel(label = s.stepDossier) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
      if (p.carries(DossierCard.Contract)) {
        val contract = p.contract
        ReviewLine(
          s.contract,
          listOf(
            contract.monthlyRentChf.takeIf { it.isNotBlank() }?.let { "${s.monthlyRent}: $it" },
            contract.serviceChargesChf.takeIf { it.isNotBlank() }?.let { "${s.serviceCharges}: $it" },
            contract.conclusionDate.takeIf { it.isNotBlank() }?.let { "${s.leaseConcludedOn}: ${SwissDate.display(it)}" },
            contract.startDate.takeIf { it.isNotBlank() }?.let { "${s.tenancyStart}: ${SwissDate.display(it)}" },
            contract.endDate.takeIf { it.isNotBlank() }?.let { "${s.tenancyEnd}: ${SwissDate.display(it)}" },
            contract.noticeDate.takeIf { it.isNotBlank() }?.let { "${s.noticeDate}: ${SwissDate.display(it)}" },
          ).filterNotNull().joinToString(" · "),
        )
      }
      if (p.carries(DossierCard.Deposit)) {
        val deposit = p.deposit
        ReviewLine(
          s.deposit,
          listOf(
            s.depositKind(deposit.kind),
            deposit.amountChf.takeIf { it.isNotBlank() }?.let { "CHF $it" },
            when (deposit.kind) {
              DepositKind.BankAccount -> deposit.bank
              DepositKind.Insurance -> deposit.provider
              DepositKind.None -> ""
            }.takeIf { it.isNotBlank() },
            deposit.policyNo.takeIf { it.isNotBlank() },
            deposit.account.takeIf { it.isNotBlank() },
          ).filterNotNull().joinToString(" · "),
        )
      }
      ReviewMeters(s, p)
      ReviewKeys(s, p)
      if (p.carries(DossierCard.Notes)) {
        ReviewLine(s.remarks, p.remarks)
        ReviewLine(s.chargesBy, SwissDate.display(p.chargesStatementBy))
      }
    }
  }
}

@Composable
private fun ReviewMeters(s: Str, p: Protocol) {
  if (!p.carries(DossierCard.Meters)) return
  val meters = listOf(
    s.meterElec1 to p.meters.electricity1,
    s.meterElec2 to p.meters.electricity2,
    s.meterGas to p.meters.gas,
    s.meterWater to p.meters.water,
    s.meterHotWater to p.meters.hotWater,
    s.meterOil to p.meters.heatingOil,
  ).filter { (_, value) -> value.isNotBlank() }
  ReviewLine(s.meters, meters.joinToString(" · ") { (label, value) -> "$label: $value" })
}

@Composable
private fun ReviewKeys(s: Str, p: Protocol) {
  if (!p.carries(DossierCard.Keys)) return
  val counts = buildList {
    p.keys.filter { it.count > 0 }.forEach { key ->
      val label = if (key.kind == dev.peringer.limen.data.KeyKind.Other && key.label.isNotBlank()) {
        key.label
      } else {
        s.keyKind(key.kind)
      }
      add("$label: ${key.count}")
    }
    p.rooms.filter { it.keysCount > 0 }.forEach { room ->
      add("${room.name.ifBlank { s.room(room.kind) }}: ${room.keysCount}")
    }
  }
  ReviewLine(
    s.keysLabel,
    listOf(
      s.keyTransfer(p.keyTransfer),
      counts.joinToString(", ").takeIf { it.isNotBlank() },
      p.keyTransferNote.takeIf { it.isNotBlank() },
      s.selfMadeKeys.takeIf { p.selfMadeKeys },
    ).filterNotNull().joinToString(" · "),
  )
}

@Composable
private fun ReviewStep(vm: LimenViewModel, s: Str, p: Protocol) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    ReviewIdentity(s, p)
    ReviewLegalOverlay(s, p)
    ReviewInspection(s, p)
    ReviewDossier(s, p)
    Panel(
      label = "${s.defects} (${p.defects.size})",
      info = s.tipCharged(),
      warning = p.defects.isNotEmpty(),
    ) {
      if (p.defects.isEmpty()) {
        Text(s.nothingYet, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.inkDim)
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          p.defects.forEachIndexed { i, d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                "${i + 1}.",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Limen.colors.inkDim,
                modifier = Modifier.width(30.dp),
              )
              Box(Modifier.weight(1f)) { DefectLine(vm, s, p, d, showPlace = true) }
            }
          }
        }
      }
    }

    // Remarks and the service-charge date used to be repeated here as well as in the
    // dossier's Notes component. They print only when that component is on the record, so
    // the copy on this screen wrote into fields the PDF then left out.
    BigKey(s.stepSign.uppercase(), active = true, Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Sign) }
    Spacer(Modifier.height(12.dp))
  }
}

// The Sign and Export stations live in FlowSignExport.kt.
