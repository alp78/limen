package dev.peringer.limen.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.peringer.limen.data.Protocol
import dev.peringer.limen.theme.Limen

@Composable
internal fun ParticipantNotice(vm: LimenViewModel, s: Str, p: Protocol) {
  var expanded by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val controller = p.controllerIdentity
  val text = buildList {
    add(s.controllerTitle)
    add(listOf(controller.organisation, controller.contactName, controller.street,
      controller.postalCode, controller.city, controller.email, controller.phone).filter(String::isNotBlank).joinToString("\n"))
    add(s.words("Purpose: document the handover and related claims. Intended recipients: the parties, their authorised representatives, and competent authorities where needed. Contact the controller for access, correction, retention and deletion questions.",
      "But : documenter la remise et les prétentions associées. Destinataires prévus : les parties, leurs représentants autorisés et, si nécessaire, les autorités compétentes. Contactez le responsable pour l’accès, la rectification, la conservation et l’effacement.",
      "Zweck: Übergabe und damit verbundene Ansprüche dokumentieren. Vorgesehene Empfänger: Parteien, bevollmächtigte Vertretungen und nötigenfalls zuständige Behörden. Bei Fragen zu Auskunft, Berichtigung, Aufbewahrung und Löschung die verantwortliche Stelle kontaktieren.",
      "Scopo: documentare la consegna e le pretese connesse. Destinatari previsti: parti, rappresentanti autorizzati e, se necessario, autorità competenti. Contattare il titolare per accesso, rettifica, conservazione e cancellazione.",
      "Intent: documentar la surdada e las pretensiuns colliadas. Destinataris previs: partidas, represchentants autorisads e, sche necessari, autoritads cumpetentas. Contactar il post responsabel per access, rectificaziun, conservaziun e stizzada."))
    add(s.privacyParagraphs[1])
    add(s.privacyParagraphs[3])
  }.joinToString("\n\n")
  Panel(label = s.privacyTitle) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SmallKey(s.privacyTitle, Modifier.fillMaxWidth()) { expanded = !expanded }
      if (expanded) Text(text, color = Limen.colors.ink)
      SmallKey(s.words("Share participant notice", "Partager l’information aux participants", "Datenschutzhinweis teilen", "Condividi informativa per i partecipanti", "Cundivider l’infurmaziun per participants")) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), s.privacyTitle))
      }
      Toggle(s.words("Notice provided before collecting participant data", "Information remise avant de recueillir les données", "Hinweis vor Erhebung der Beteiligten-Daten erteilt", "Informativa fornita prima della raccolta dei dati", "Infurmaziun dada avant da rimnar las datas"), p.details.participantNoticeShown,
        enabled = !LocalRecordLocked.current) { vm.edit { it.copy(details = it.details.copy(participantNoticeShown = !it.details.participantNoticeShown)) } }
    }
  }
}
