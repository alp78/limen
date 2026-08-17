package dev.verso.limen.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.Kind
import dev.verso.limen.data.Plane
import dev.verso.limen.data.Protocol
import dev.verso.limen.data.Role
import dev.verso.limen.theme.Limen

/**
 * The two signature planes, kept apart on purpose and by law: the record's signature is
 * never the debt's. Refusal is a key, not a dead end, and every signature can carry its
 * dictated reservation.
 */
@Composable
internal fun SignStep(vm: LimenViewModel, s: Str, p: Protocol) {
  var padTarget by remember { mutableStateOf<Pair<Role, Plane>?>(null) }

  // A Box, so the pad truly overlays the step instead of queueing below the scroll.
  androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Panel(label = s.signRecordTitle, info = s.tipRecord()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.requiredRecordRoles(p).forEach { role ->
          SignRow(vm, s, p, role, Plane.Record) { padTarget = role to Plane.Record }
        }
        // Who has to sign at all. This decides the rows above, which is why it sits here
        // rather than filed under the dossier's notes, where it was also printed only if
        // that optional component happened to be on the record.
        Row(verticalAlignment = Alignment.CenterVertically) {
          Toggle(
            s.oneSided,
            p.oneSided,
            Modifier.weight(1f),
            enabled = !LocalRecordLocked.current,
          ) { vm.edit { it.copy(oneSided = !it.oneSided) } }
          InfoTip(s.tipOneSided())
        }
      }
    }

    Panel(label = s.signCostsTitle, info = s.tipCosts()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Role.Tenant, Role.Landlord).forEach { role ->
          SignRow(vm, s, p, role, Plane.Costs) { padTarget = role to Plane.Costs }
        }
      }
    }

    val issues = vm.completionIssues(p)
    // A record that is already frozen has nothing to freeze. The key stayed on screen when
    // this step became reachable after finalization, and it did nothing when pressed.
    if (!LocalRecordLocked.current) Panel(label = s.finalizeRecord, info = s.tipFinalize()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (issues.isNotEmpty()) {
          Text(s.finalizationNeeds, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
          issues.forEach { issue ->
            Text("• ${s.finalizationIssue(issue)}", style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
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

    val canExport = issues.isEmpty() && !vm.finalizing
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

  padTarget?.let { (role, plane) ->
    SignPadOverlay(
      title = "${if (plane == Plane.Record) s.signRecordTitle else s.signCostsTitle} · ${s.role(role)}",
      meaning = if (plane == Plane.Record) s.tipRecord().lead else s.tipCosts().lead,
      s = s,
      onKeep = { bmp ->
        vm.keepSignature(role, plane, bmp)
        padTarget = null
      },
      onCancel = { padTarget = null },
    )
  }
  }
}

@Composable
private fun SignRow(
  vm: LimenViewModel,
  s: Str,
  p: Protocol,
  role: Role,
  plane: Plane,
  onSign: () -> Unit,
) {
  val sig = p.signatures.firstOrNull { it.role == role && it.plane == plane }
  val current = sig?.let(p::isCurrent) == true
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(s.role(role), style = MaterialTheme.typography.bodyMedium, color = Limen.colors.ink)
        // The status: the one place besides the case's chip where the seal green appears.
        val (label, sealed) = when {
          sig == null -> s.notSigned to false
          !current -> s.resignRequired to false
          sig.refused -> s.refusedNote to false
          else -> s.signed to true
        }
        Text(
          label,
          style = MaterialTheme.typography.labelSmall,
          color = if (sealed) Limen.colors.seal else Limen.colors.inkDim,
        )
      }
      if (LocalRecordLocked.current) {
        // Nothing to offer: the ink is part of what the fingerprint covers.
      } else if (sig == null || !current) {
        IconKey(ButtonGlyph.Edit, s.signKey, Modifier.padding(end = 6.dp), onPress = onSign)
        IconKey(
          ButtonGlyph.Cancel,
          s.refusedKey,
          fillColor = Limen.colors.danger,
          outlineColor = Limen.colors.dangerOutline,
          tint = Limen.colors.dangerOutline,
          tick = Tick.Reject,
        ) { vm.refuseSignature(role, plane) }
      } else {
        IconKey(ButtonGlyph.Clear, s.clearInk, destructive = true) { vm.clearSignature(role, plane) }
      }
    }
    if (sig != null) {
      Field(s.reservationLabel, sig.reservation) { v ->
        vm.setSignatureReservation(role, plane, v)
      }
    }
  }
}

/**
 * The way out: the document's language, the export itself, the result card, and the
 * deposit's next step, which is the last thing both parties read at the door.
 */
@Composable
internal fun ExportStep(vm: LimenViewModel, s: Str, p: Protocol) {
  val context = LocalContext.current
  // The document landing is the other moment that ends something, and it arrives after
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
            "${s.pdfAmends}: ${p.amendedFromId}",
            style = MaterialTheme.typography.labelSmall,
            color = Limen.colors.inkDim,
          )
        }
      }
    }

    Panel(label = s.pdfLanguage) {
      PillRow(
        Lang.entries.map { it to it.name.uppercase() },
        vm.pdfLang ?: vm.lang,
        dense = true,
        onSelect = vm::selectPdfLang,
      )
    }

    BigKey(
      if (vm.exporting) "…" else s.exportPdf,
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
          Text(
            "${s.exportedTo} Download/Limen · ${(file.bytes / 1024).coerceAtLeast(1)} KB",
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

    // The deposit's next step, on screen as well as in the PDF.
    if (p.kind == Kind.Exit && p.deposit.kind != DepositKind.None) {
      Panel(label = s.releaseTitle) {
        Text(
          when (p.deposit.kind) {
            DepositKind.BankAccount -> s.releaseBank
            DepositKind.Insurance -> s.releaseInsurance
            DepositKind.None -> ""
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
      }
    }

    SmallKey(s.done, Modifier.fillMaxWidth()) { vm.closeFlow() }
    Spacer(Modifier.height(12.dp))
    }
  }
}
