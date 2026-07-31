package dev.verso.limen.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.verso.limen.data.Role
import dev.verso.limen.theme.Limen

/**
 * The link face. Above the fold: who this phone is, and the handshake. Below: the linked
 * features, greyed and locked until both keys are exchanged and the joint fingerprint is
 * confirmed aloud; the lock itself teaches what the exchange unlocks. The audit journal
 * unlocks with the pair; the relay features are named but wait for the relay.
 */
@Composable
fun LinkScreen(modifier: Modifier = Modifier, vm: LimenViewModel, s: Str) {
  val context = LocalContext.current
  val pickCard = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) vm.importKeyCard(uri)
  }

  Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    AppHeader(vm, s)
    Spacer(Modifier.height(12.dp))
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Who this phone is: side and name, changeable here and nowhere else.
      Panel(label = s.yourSide, info = s.tipRole()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          PillRow(
            listOf(Role.Tenant, Role.Landlord).map { it to s.role(it) },
            vm.myRole ?: Role.Tenant,
          ) { r -> vm.chooseRole(r, vm.profileName) }
          Field(s.profileName, vm.profileName) { vm.updateProfileName(it) }
        }
      }

      // The handshake.
      Panel(label = s.linkTitle, info = s.tipLink()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          val pair = vm.pair
          if (pair == null) {
            SmallKey(s.shareMyKey, Modifier.fillMaxWidth()) { vm.exportKeyCard() }
            vm.keyCardFile?.let { file ->
              SmallKey(s.share, Modifier.fillMaxWidth()) {
                runCatching {
                  val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, file.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                  context.startActivity(Intent.createChooser(send, s.share))
                }
              }
            }
            SmallKey(s.importTheirKey, Modifier.fillMaxWidth()) {
              pickCard.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
            if (vm.importCardError) {
              Text(s.notACard, style = MaterialTheme.typography.labelSmall, color = Limen.colors.ink)
            }
            vm.importedCard?.let { card ->
              Text(
                "${card.name} · ${s.role(card.role)}",
                style = MaterialTheme.typography.titleMedium,
                color = Limen.colors.ink,
              )
              FingerprintRow(s.jointFingerprintLabel, vm.jointFingerprint().orEmpty())
              Text(
                s.fingerprintsMatchLead,
                style = MaterialTheme.typography.bodyMedium,
                color = Limen.colors.ink,
              )
              BigKey(s.sealPairKey, active = true, Modifier.fillMaxWidth()) { vm.sealPair() }
            }
          } else {
            Text(
              "${s.linkedWith} ${pair.name} · ${s.role(pair.role)}",
              style = MaterialTheme.typography.titleMedium,
              color = Limen.colors.seal,
            )
            FingerprintRow(s.jointFingerprintLabel, vm.jointFingerprint().orEmpty())
            SmallKey(s.unlink, Modifier.fillMaxWidth()) { vm.unpair() }
          }
        }
      }

      // The linked features: locked until the handshake, then alive one by one.
      val unlocked = vm.pair != null
      Column(
        Modifier.alpha(if (unlocked) 1f else 0.38f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Panel(label = s.auditJournal, info = s.tipAudit()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!unlocked) {
              Text(s.lockedUntilPair, style = MaterialTheme.typography.labelSmall, color = Limen.colors.inkDim)
            } else {
              SmallKey(s.exportAudit, Modifier.fillMaxWidth()) { vm.exportAudit() }
              vm.auditFile?.let { file ->
                Text(file.displayName, style = MaterialTheme.typography.titleMedium, color = Limen.colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  SmallKey(s.open, Modifier.weight(1f)) {
                    runCatching {
                      context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                          setDataAndType(file.uri, "application/pdf")
                          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                      )
                    }
                  }
                  SmallKey(s.share, Modifier.weight(1f)) {
                    runCatching {
                      val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, file.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      }
                      context.startActivity(Intent.createChooser(send, s.share))
                    }
                  }
                }
              }
            }
          }
        }

        listOf(s.featureDocuments, s.featureReceipts, s.featureShared).forEach { feature ->
          Panel(label = feature) {
            Text(
              if (unlocked) s.comingWithRelay else s.lockedUntilPair,
              style = MaterialTheme.typography.labelSmall,
              color = Limen.colors.inkDim,
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))
    }
  }
}

@Composable
private fun FingerprintRow(label: String, fp: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      label,
      style = MaterialTheme.typography.labelSmall,
      color = Limen.colors.inkDim,
      modifier = Modifier.weight(1f),
    )
    Text(fp, fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = Limen.colors.ink)
  }
}
