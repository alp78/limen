package dev.peringer.limen.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.DeliveryMethod
import dev.peringer.limen.data.*
import dev.peringer.limen.data.Kind
import dev.peringer.limen.data.Plane
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.UnilateralReason
import dev.peringer.limen.theme.Limen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The handover record signature. Refusal is a recorded outcome, not a dead end. */
@Composable
internal fun SignStep(vm: LimenViewModel, s: Str, p: Protocol) {
  val context = LocalContext.current
  // The party's key, not the party: the pad has to reopen over its ink after the activity is
  // recreated, and a Party cannot be saved.
  var padTargetKey by rememberSaveable { mutableStateOf<String?>(null) }

  // A Box, so the pad truly overlays the step instead of queueing below the scroll.
  androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    DocumentPreviewButton(vm, s, p)
    Panel(label = s.signRecordTitle, info = s.tipRecord()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Who has to sign at all. This decides the rows below, which is why it sits here
        // rather than filed under the dossier's notes, where it was also printed only if
        // that optional component happened to be on the record.
        Row(verticalAlignment = Alignment.CenterVertically) {
          Toggle(
            s.oneSided,
            p.oneSided,
            Modifier.weight(1f),
            enabled = !LocalRecordLocked.current,
          ) {
            vm.edit {
              if (it.oneSided) it.copy(
                oneSided = false,
                unilateralReason = null,
                unilateralNote = "",
              ) else it.copy(oneSided = true)
            }
          }
          InfoTip(s.tipOneSided())
        }
        if (p.oneSided) {
          Text(s.infoOneSided, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            UnilateralReason.entries.forEach { reason ->
              Chip(s.unilateralReason(reason), active = p.unilateralReason == reason) {
                vm.edit { it.copy(unilateralReason = reason) }
              }
            }
          }
          Field(s.unilateralNote, p.unilateralNote, singleLine = false) { value ->
            vm.edit { it.copy(unilateralNote = value) }
          }
        }
        p.parties.filter { it.role in vm.requiredRecordRoles(p) }.forEach { party ->
          SignRow(vm, s, p, party, Plane.Record) { padTargetKey = party.participantKey() }
        }
        Text(s.inkEvidenceNotice, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
      }
    }

    val issues = remember(p) { vm.completionIssues(p) }
    // A record that is already frozen has nothing to freeze. The key stayed on screen when
    // this step became reachable after finalization, and it did nothing when pressed.
    if (!LocalRecordLocked.current) Panel(label = s.finalizeRecord, info = s.tipFinalize()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (issues.isNotEmpty()) {
          Text(s.finalizationNeeds, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          issues.forEach { issue ->
            CompletionAction(vm, s, p, issue)
          }
        }
        if (vm.finalizationError) {
          Text(s.finalizationFailed, style = MaterialTheme.typography.labelSmall, color = Limen.colors.ink)
        }
        BigKey(
          if (vm.finalizing) s.finalizing else s.finalizeRecord,
          active = issues.isEmpty() && !vm.finalizing,
          modifier = Modifier.fillMaxWidth(),
          enabled = issues.isEmpty() && !vm.finalizing,
        ) { vm.finalizeProtocol() }
      }
    }

    val canExport = p.isFinalized() || (issues.isEmpty() && !vm.finalizing)
    BigKey(
      s.stepExport.uppercase(),
      active = canExport,
      modifier = Modifier.fillMaxWidth(),
      enabled = canExport,
    ) {
      vm.goTo(FlowStep.Export)
    }
    if (!LocalRecordLocked.current) {
      BigKey(s.saveAsDraft, active = true, Modifier.fillMaxWidth()) { vm.closeFlow() }
    }
    Spacer(Modifier.height(12.dp))
  }

  p.parties.firstOrNull { it.participantKey() == padTargetKey }?.let { party ->
    SignPadOverlay(
      title = "${party.signerName()} · ${s.role(party.role)} · ${party.agency}",
      meaning = s.tipRecord().lead,
      s = s,
      onKeep = { bmp ->
        // The pad stays open with its ink while evidence is being stored: keepSignature
        // refuses then, and closing the pad on that refusal threw the drawn signature away.
        if (vm.evidenceBusy > 0) Toast.makeText(context, s.signatureWaitsForEvidence, Toast.LENGTH_LONG).show()
        else {
          vm.keepSignature(party.role, Plane.Record, bmp, party.participantKey())
          padTargetKey = null
        }
      },
      onCancel = { padTargetKey = null },
    )
  }
  }
}

@Composable
private fun SignRow(vm: LimenViewModel, s: Str, p: Protocol, party: Party, plane: Plane, onSign: () -> Unit) {
  val sig = p.signatureFor(party, plane)
  val current = sig?.let(p::isCurrent) == true
  val reservation = p.details.reservations[party.participantKey()]
    ?: p.pendingRecordReservations[party.role] ?: sig?.reservation.orEmpty()
  val locked = LocalRecordLocked.current
  var clearing by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("${s.role(party.role)} · ${party.signerName()}", style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
    if (party.representedBy.isNotBlank()) Text("${s.representedBy}: ${party.name} · ${party.authority}", color = Limen.colors.inkDim)
    Text(when {
      sig == null -> s.notSigned
      !current -> s.resignRequired
      sig.outcome == "absent" -> s.absentOutcome
      sig.outcome == "unable" -> s.unableToDraw
      sig.refused -> s.refusedNote
      else -> s.signed
    }, color = Limen.colors.ink)
    if (!locked && !current) {
      Field(s.reservationLabel, reservation, singleLine = false) { vm.setRecordReservation(party.role, it, party.participantKey()) }
      SmallKey(s.signKey, Modifier.fillMaxWidth(), enabled = party.signerName().isNotBlank(), onPress = onSign)
      SmallKey(s.refusedKey, Modifier.fillMaxWidth()) { vm.refuseSignature(party.role, plane, party.participantKey()) }
      SmallKey(s.absentOutcome, Modifier.fillMaxWidth(), enabled = reservation.isNotBlank()) { vm.refuseSignature(party.role, plane, party.participantKey(), "absent") }
      SmallKey(s.unableToDraw, Modifier.fillMaxWidth(), enabled = reservation.isNotBlank()) { vm.refuseSignature(party.role, plane, party.participantKey(), "unable") }
    } else {
      if (sig?.reservation?.isNotBlank() == true) Text("${s.reservationLabel}: ${sig.reservation}", color = Limen.colors.ink)
      if (!locked) SmallKey(s.clearInk) { clearing = true }
    }
  }
  if (clearing) ConfirmDialog(s, s.clearInk, onConfirm = { vm.clearSignature(party.role, plane, party.participantKey()); clearing = false }, onDismiss = { clearing = false })
}

/**
 * The way out: the document's language, the export itself, the result card, and the
 * deposit's next step, which is the last thing both parties read at the door.
 */
@Composable
internal fun ExportStep(vm: LimenViewModel, s: Str, p: Protocol) {
  val context = LocalContext.current
  // The finished document is the other moment that ends something, and it arrives after
  // the key was released, so the confirmation is played by the result rather than the tap.
  val play = rememberTick()
  androidx.compose.runtime.LaunchedEffect(vm.exported) {
    if (vm.exported != null) play(Tick.Confirm)
  }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (!p.isFinalized()) {
      Panel(label = s.finalizeRecord) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(s.exportBlocked, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          SmallKey(s.stepSign.uppercase(), Modifier.fillMaxWidth()) { vm.goTo(FlowStep.Sign) }
        }
      }
      vm.exportIssue?.let { issue ->
        Text(s.exportIssue(issue), style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
      }
      BigKey(s.exportPdf, active = false, modifier = Modifier.fillMaxWidth(), enabled = false) {}
      BigKey(s.saveAsDraft, active = true, Modifier.fillMaxWidth()) { vm.closeFlow() }
      Spacer(Modifier.height(12.dp))
    } else {
    Panel(label = s.pdfFinalized) {
      Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(s.pdfFinalized, style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
        Text(
          "${s.fingerprint}: ${p.evidenceFingerprint.take(20)}",
          style = MaterialTheme.typography.labelSmall,
          color = Limen.colors.inkDim,
        )
        if (p.amendedFromId.isNotBlank()) {
          Text(
            "${s.pdfAmends}: ${p.amendedFromFingerprint.takeIf(String::isNotBlank)?.let { "LIMEN-${it.take(16)}" } ?: p.amendedFromId}",
            style = MaterialTheme.typography.labelSmall,
            color = Limen.colors.inkDim,
          )
        }
      }
    }

    Panel(label = s.pdfLanguage) {
      Column {
      Text(s.translationNotice, color = Limen.colors.inkDim)
      PillRow(
        Lang.entries.map { it to it.name.uppercase() },
        vm.pdfLang ?: vm.lang,
        dense = true,
        onSelect = vm::selectPdfLang,
      )
      }
    }
    // The name is the address and the day alone, so what tells the copies apart is said beside it.
    p.exportHistory.asReversed().forEach { copy ->
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SmallKey(copy.name, Modifier.fillMaxWidth()) { vm.selectExport(copy) }
        Text(
          listOfNotNull(
            copy.language.uppercase(),
            deliveryMoment(copy.epochMs),
            s.copyBeforeDeliveryChange.takeIf { copy.deliveryRevision != p.deliveryRevision },
          ).joinToString(" · "),
          style = MaterialTheme.typography.labelSmall,
          color = Limen.colors.inkDim,
        )
      }
    }
    vm.exported?.companionUri?.let { uri ->
      SmallKey(s.accessibleCopy, Modifier.fillMaxWidth()) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/html").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
          .onFailure { Toast.makeText(context, s.openFileFailed, Toast.LENGTH_LONG).show() }
      }
    }
    RecordBackupButton(vm, s, p)

    val deliveryNeedsExport = if (p.exportHistory.isEmpty()) p.deliveryMetadataEpochMs > p.exportedEpochMs else p.deliveryRevision != p.exportHistory.last().deliveryRevision
    if (deliveryNeedsExport) {
      Panel(label = s.deliveryChanged, warning = true) {
        Text(
          s.deliveryChangedBody,
          style = MaterialTheme.typography.bodyMedium,
          color = Limen.colors.ink,
        )
      }
    }

    BigKey(
      if (vm.exporting) "…" else if (deliveryNeedsExport) s.exportUpdatedFinalCopy else s.exportPdf,
      active = !vm.exporting,
      Modifier.fillMaxWidth(),
      enabled = !vm.exporting,
    ) { vm.exportPdf() }

    vm.exportIssue?.let { issue ->
      Text(s.exportIssue(issue), style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
    }

    vm.exported?.let { file ->
      Revealed(file.displayName) {
      Panel(label = s.exported, info = s.tipShareNow()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(file.displayName, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink)
          // Download/Limen only for the copy this export just wrote there. A record opened again,
          // restored on another phone or picked from the history shows the app's own retained copy.
          val where = if (file.uri.authority == "${context.packageName}.files") s.keptInApp else "${s.exportedTo} Download/Limen"
          Text(
            "$where · ${(file.bytes / 1024).coerceAtLeast(1)} ${if (s.lang == Lang.Fr) "ko" else "KB"}",
            style = MaterialTheme.typography.labelSmall,
            color = Limen.colors.inkDim,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconKey(ButtonGlyph.Open, s.open, Modifier.weight(1f)) {
              runCatching {
                context.startActivity(
                  Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(file.uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                )
              }.onFailure { Toast.makeText(context, s.openFileFailed, Toast.LENGTH_SHORT).show() }
            }
            IconKey(ButtonGlyph.Share, s.share, Modifier.weight(1f)) {
              runCatching {
                val send = Intent(Intent.ACTION_SEND).apply {
                  type = "application/pdf"
                  putExtra(Intent.EXTRA_STREAM, file.uri)
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, s.share))
              }.onFailure { Toast.makeText(context, s.shareFileFailed, Toast.LENGTH_SHORT).show() }
            }
          }
        }
      }
      }
    }

    if (p.isExported()) CopyDeliveryPanel(vm, s, p)

    // The deposit's next step, on screen as well as in the PDF.
    if (p.kind == Kind.Exit && p.deposit.kind != DepositKind.None) {
      Panel(label = s.releaseTitle) {
        Text(
          when (p.deposit.kind) {
            DepositKind.BankAccount -> s.releaseBank
            DepositKind.Insurance -> s.releaseInsurance
            DepositKind.None -> ""
              DepositKind.Unknown -> s.toVerify
          },
          style = MaterialTheme.typography.bodyMedium,
          color = Limen.colors.ink,
        )
      }
    }

    Panel(label = s.amendRecord, info = s.tipAmend()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BigKey(
          if (vm.amendmentInProgress) s.amending else s.amendRecord,
          active = !vm.amendmentInProgress,
          modifier = Modifier.fillMaxWidth(),
          enabled = !vm.amendmentInProgress,
        ) { vm.createAmendment() }
        if (vm.amendmentError) {
          Text(
            s.amendFailed,
            style = MaterialTheme.typography.bodySmall,
            color = Limen.colors.dangerOutline,
          )
        }
      }
    }

    // Closed only when nothing is still being written: leaving mid-export used to take the
    // record out from under the writer that was about to stamp it.
    SmallKey(
      if (vm.exporting) "…" else s.done,
      Modifier.fillMaxWidth(),
      enabled = !vm.exporting,
    ) { vm.closeFlow() }
    Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun CopyDeliveryPanel(vm: LimenViewModel, s: Str, p: Protocol) {
  Panel(label = s.copyDelivery) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(s.deliverySelfDeclared, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
      p.parties.filter { it.role in listOf(Role.Tenant, Role.Landlord) }.forEach { party ->
        val role = party.role
        var detail by rememberSaveable(p.id, party.participantKey()) { mutableStateOf("") }
        val receipt = p.deliveryReceipts.firstOrNull { it.role == role && (it.participantId == party.participantKey() || it.participantId.isBlank() && party.id.isBlank()) }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Text("${s.role(role)} · ${party.name}", style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
          if (receipt == null) {
            Text(s.recordCopyFor, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
            Field(
              s.deliveryDetail,
              detail,
              editableWhenLocked = true,
            ) { value ->
              detail = value
            }
            androidx.compose.foundation.layout.FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              DeliveryMethod.entries.forEach { method ->
                Chip(
                  s.deliveryMethod(method),
                  dim = false,
                  enabled = method != DeliveryMethod.Other || detail.isNotBlank(),
                ) { vm.recordDelivery(role, method, detail, party.participantKey()) }
              }
            }
          } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                "${s.deliveryMethod(receipt.method)} · ${deliveryMoment(receipt.epochMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Limen.colors.seal,
                modifier = Modifier.weight(1f),
              )
              IconKey(ButtonGlyph.Clear, s.clearDelivery, destructive = true) { vm.clearDelivery(role, receipt.participantId) }
            }
            if (receipt.note.isNotBlank()) {
              Text(receipt.note, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
            }
          }
        }
      }
    }
  }
}

private fun deliveryMoment(epochMs: Long): String =
  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
