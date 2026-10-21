package dev.peringer.limen.data.pdf

import android.graphics.BitmapFactory
import dev.peringer.limen.data.*
import dev.peringer.limen.ui.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The supplemental sections shared with the preview, one panel per heading. */
internal fun Sheet.extras(p: Protocol) = extraContent(p, s).forEach { (heading, lines) ->
  panel(Panel(heading, lines.map { Text(it, Style.Body) }))
}

/** The Notes component: free remarks and the service-charge date, when it is on the record. */
internal fun Sheet.remarks(p: Protocol) {
  if (!Outline.remarks(p)) return
  val blocks = buildList<Block> {
    if (p.remarks.isNotBlank()) add(Text(p.remarks, Style.Body))
    if (p.chargesStatementBy.isNotBlank()) {
      if (p.remarks.isNotBlank()) add(Gap(PdfTokens.LABEL_GAP))
      add(Text("${s.chargesBy} ${SwissDate.display(p.chargesStatementBy)}", Style.Body))
    }
  }
  panel(Panel(s.remarks, blocks))
}

/**
 * The record signatures, always printed, with their meaning above the ink. A handover one
 * party did not attend is a statement about these signatures, so it is the panel's pill and
 * its first lines, unconditionally: under the optional remarks it vanished from the paper
 * whenever that component was not added.
 */
internal fun Sheet.signatures(p: Protocol, signatureDir: File) {
  val blocks = buildList<Block> {
    if (p.oneSided) {
      p.unilateralReason?.let { add(Text("${s.unilateralReasonLabel}: ${s.unilateralReason(it)}", Style.Body)) }
      if (p.unilateralNote.isNotBlank()) add(Text(p.unilateralNote, Style.Small))
      add(Text(s.infoOneSided, Style.Small))
      add(Gap(PdfTokens.LABEL_GAP))
    }
    add(Text(s.signRecordMeaning, Style.Small))
    add(Gap(PdfTokens.LABEL_GAP))
    addAll(plane(p, signatureDir, Plane.Record))
    add(Text(s.inkEvidenceNotice, Style.Small))
    // The Swiss ritual, said on the document itself: two identical papers, one per party.
    // In Vaud the RULV require exactly that, and a tenant who reads it here knows to leave
    // the flat holding one.
    add(Gap(6f))
    add(Text(s.pdfTwoCopies, Style.Small))
  }
  panel(Panel(s.signRecordTitle, blocks, pill = if (p.oneSided) Pill(s.oneSided, PillTone.Warning) else null))
  if (p.signatures.any { it.plane == Plane.Costs }) panel(Panel(s.costSignatureTitle, plane(p, signatureDir, Plane.Costs)))
}

private fun Sheet.plane(p: Protocol, dir: File, plane: Plane): List<Block> {
  val current = p.signatures.filter { it.plane == plane && p.isCurrent(it) }
  if (current.isEmpty()) return listOf(Text(s.notSigned, Style.Body))
  return current.map { sig ->
    val note = if (sig.refused || sig.outcome.isNotBlank()) {
      when (sig.outcome) { "absent" -> s.absentOutcome; "unable" -> s.unableToDraw; else -> s.refusedNote }
    } else null
    val file = File(dir, sig.file)
    val bitmap = if (note == null && sig.file.isNotBlank() && file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    require(note != null || sig.file.isBlank() || bitmap != null) { "Signature cannot be rendered: ${sig.file}" }
    val caption = buildList<Block> {
      add(Text(if (sig.name.isNotBlank()) "${sig.name} (${s.role(sig.role)})" else s.role(sig.role), Style.BodyMedium))
      if (sig.reservation.isNotBlank()) add(Text("${s.reservationLabel}: ${sig.reservation}", Style.Small))
      if (sig.epochMs > 0L) add(Text("${s.capturedOn}: ${formatMoment(sig.epochMs)}", Style.Small))
      add(Text("${s.revisionLabel}: ${sig.contentRevision}", Style.Small))
    }
    Ink(bitmap, "${s.signRecordTitle}: ${sig.name}", note, caption)
  }
}

internal fun formatMoment(epochMs: Long): String =
  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

internal fun Sheet.delivery(p: Protocol) {
  if (p.deliveryReceipts.isEmpty()) return
  val blocks = buildList<Block> {
    add(Text(s.deliverySelfDeclared, Style.Small))
    p.deliveryReceipts.sortedBy { it.role.ordinal }.forEach { receipt ->
      val name = p.parties.firstOrNull {
        it.participantKey() == receipt.participantId || receipt.participantId.isBlank() && it.id.isBlank() && it.role == receipt.role
      }?.name
      add(Text(if (!name.isNullOrBlank()) "$name (${s.role(receipt.role)})" else s.role(receipt.role), Style.BodyMedium, keepWithNext = true))
      val chips = buildList {
        add(Chip(s.deliveryMethod(receipt.method)))
        if (receipt.epochMs > 0L) add(Chip(formatMoment(receipt.epochMs)))
      }
      add(Chips(chips))
      if (receipt.note.isNotBlank()) add(Text(receipt.note, Style.Small))
    }
  }
  panel(Panel(s.copyDelivery, blocks))
}

/** The deposit release instruction, on an exit record that carries a deposit. */
internal fun Sheet.release(p: Protocol) {
  if (!Outline.release(p)) return
  val blocks = buildList<Block> {
    when (p.deposit.kind) {
      DepositKind.BankAccount -> add(Text(s.releaseBank, Style.Body))
      DepositKind.Insurance -> {
        add(Text(s.releaseInsurance, Style.Body))
        if (p.deposit.provider.isNotBlank()) {
          add(Gap(6f))
          val pairs = mutableListOf(s.depositProvider to p.deposit.provider)
          if (p.deposit.policyNo.isNotBlank()) pairs += s.depositPolicy to p.deposit.policyNo
          add(Fields(pairs))
        }
      }
      DepositKind.Unknown -> add(Text(s.toVerify, Style.Body))
      DepositKind.None -> Unit
    }
  }
  panel(Panel(s.releaseTitle, blocks))
}
