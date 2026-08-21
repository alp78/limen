package dev.verso.limen.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.verso.limen.theme.Limen

/**
 * The two roads for the deposit, side by side with outbound links to current terms.
 * Deliberately neutral and deliberately shallow: provider identity and a link, nothing more.
 * The application always happens on the provider's own site; the app never recommends,
 * pre-fills or submits anything, which keeps it plainly outside insurance intermediation.
 */
private data class Offer(val name: String, val url: String)

private val bankOffers = listOf(
  Offer("UBS", "https://www.ubs.com/ch/de/services/accounts-and-cards/accounts/rental-surety-savings-account.html"),
  Offer("BEKB", "https://www.bekb.ch"),
  Offer("ZKB", "https://www.zkb.ch"),
)

private val insuranceOffers = listOf(
  Offer("AXA", "https://www.axa.ch/de/rechner/mietkaution.html"),
  Offer("Helvetia", "https://www.helvetia.com/ch/web/de/privatkunden/versicherungen/mietkaution.html"),
  Offer("goCaution", "https://www.gocaution.ch"),
  Offer("SwissCaution", "https://www.swisscaution.ch"),
  Offer("Firstcaution", "https://www.firstcaution.ch"),
  Offer("SmartCaution", "https://www.smartcaution.ch"),
)

@Composable
fun DepositCompareOverlay(vm: LimenViewModel, s: Str) {
  val context = LocalContext.current
  BackHandler { vm.setDepositCompare(false) }

  Column(
    Modifier
      .fillMaxSize()
      .background(Limen.colors.plate)
      // A background paints but does not hit-test, so every tap that missed a row here went
      // through to the masthead underneath: two of them on the back key closed the record.
      .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent() } } }
      .padding(horizontal = 20.dp),
  ) {
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        s.deposit,
        style = MaterialTheme.typography.titleMedium,
        color = Limen.colors.ink,
        modifier = Modifier.weight(1f),
      )
      InfoTip(s.tipCompare())
    }
    Spacer(Modifier.height(12.dp))

    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Panel(label = s.currentTerms) {
        Text(s.infoDeposit, style = MaterialTheme.typography.bodySmall, color = Limen.colors.inkDim)
      }
      Panel(label = s.depositBank) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          bankOffers.forEach { OfferRow(it, s) { url -> open(context, url, s.openLinkFailed) } }
        }
      }
      Panel(label = s.depositInsurance) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          insuranceOffers.forEach { OfferRow(it, s) { url -> open(context, url, s.openLinkFailed) } }
        }
      }
    }

    Spacer(Modifier.height(10.dp))
    SmallKey(s.done, Modifier.fillMaxWidth()) { vm.setDepositCompare(false) }
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun OfferRow(offer: Offer, s: Str, onOpen: (String) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      offer.name,
      style = MaterialTheme.typography.bodyMedium,
      color = Limen.colors.ink,
      modifier = Modifier.weight(1f),
    )
    Chip(s.linkKey, dim = false) { onOpen(offer.url) }
  }
}

private fun open(context: android.content.Context, url: String, failureMessage: String) {
  runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    .onFailure { Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show() }
}
