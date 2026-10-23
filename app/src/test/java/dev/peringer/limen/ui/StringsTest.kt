package dev.peringer.limen.ui

import dev.peringer.limen.data.Appliance
import dev.peringer.limen.data.ArchiveRefused
import dev.peringer.limen.data.CautionProvider
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.DeliveryMethod
import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.Furniture
import dev.peringer.limen.data.Grade
import dev.peringer.limen.data.ItemKey
import dev.peringer.limen.data.KeyKind
import dev.peringer.limen.data.KeyTransferStatus
import dev.peringer.limen.data.InitialRentFormRule
import dev.peringer.limen.data.LegalCheckStatus
import dev.peringer.limen.data.LegalRequirement
import dev.peringer.limen.data.LegalProfile
import dev.peringer.limen.data.ObjectGroup
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.RoomKind
import dev.peringer.limen.data.SwissCanton
import dev.peringer.limen.data.UnilateralReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {

  @Test
  fun everyEnumSpeaksEveryLanguage() {
    Lang.entries.forEach { lang ->
      val s = Str(lang)
      RoomKind.entries.forEach { assertTrue("$lang room $it", s.room(it).isNotBlank()) }
      ItemKey.entries.forEach { assertTrue("$lang item $it", s.item(it).isNotBlank()) }
      Grade.entries.forEach { assertTrue("$lang grade $it", s.grade(it).isNotBlank()) }
      Role.entries.forEach { assertTrue("$lang role $it", s.role(it).isNotBlank()) }
      KeyKind.entries.forEach { assertTrue("$lang key $it", s.keyKind(it).isNotBlank()) }
      DepositKind.entries.forEach { assertTrue("$lang deposit $it", s.depositKind(it).isNotBlank()) }
      CautionProvider.entries.forEach { assertTrue("$lang caution $it", s.cautionProvider(it).isNotBlank()) }
      Appliance.entries.forEach { assertTrue("$lang appliance $it", s.appliance(it).isNotBlank()) }
      Furniture.entries.forEach { assertTrue("$lang furniture $it", s.furniture(it).isNotBlank()) }
      ObjectGroup.entries.forEach { assertTrue("$lang group $it", s.objectGroup(it).isNotBlank()) }
      DossierCard.entries.forEach { assertTrue("$lang card $it", s.dossierCard(it).isNotBlank()) }
      SwissCanton.entries.forEach { assertTrue("$lang canton $it", s.canton(it).isNotBlank()) }
      LegalProfile.entries.forEach { assertTrue("$lang legal profile $it", s.legalProfile(it).isNotBlank()) }
      InitialRentFormRule.entries.forEach { assertTrue("$lang rent form $it", s.initialRentFormRule(it).isNotBlank()) }
      LegalRequirement.entries.forEach { assertTrue("$lang legal check $it", s.legalRequirement(it).isNotBlank()) }
      LegalCheckStatus.entries.forEach { assertTrue("$lang legal status $it", s.legalCheckStatus(it).isNotBlank()) }
      KeyTransferStatus.entries.forEach { assertTrue("$lang key transfer $it", s.keyTransfer(it).isNotBlank()) }
      UnilateralReason.entries.forEach { assertTrue("$lang unilateral reason $it", s.unilateralReason(it).isNotBlank()) }
      DeliveryMethod.entries.forEach { assertTrue("$lang delivery $it", s.deliveryMethod(it).isNotBlank()) }
      FinalizationIssue.entries.forEach {
        assertTrue("$lang issue $it", s.finalizationIssue(it).isNotBlank())
      }
      ExportIssue.entries.forEach { assertTrue("$lang export $it", s.exportIssue(it).isNotBlank()) }
    }
  }

  @Test
  fun everyRemovalOfferedBackNamesWhatWasRemovedInEveryLanguage() {
    UndoKind.entries.forEach { kind ->
      val sentences = Lang.entries.map { Str(it).removedForUndo(kind) }
      // The noEmDash task bans the literal character in sources, so the test names it by code point.
      // The noEmDash task bans the literal character in sources, so the test names it by code point.
      assertTrue("undo $kind", sentences.all { it.isNotBlank() && '\u2014' !in it && 'ß' !in it })
      // A sentence left in English under another language would collapse into one here.
      assertEquals("undo $kind", Lang.entries.size, sentences.distinct().size)
    }
    // Two kinds sharing a sentence would tell the person the wrong thing was removed.
    Lang.entries.forEach { lang ->
      val s = Str(lang)
      assertEquals("$lang undo", UndoKind.entries.size, UndoKind.entries.map { s.removedForUndo(it) }.distinct().size)
    }
  }

  @Test
  fun everyBackupRecoveryAndPreviewMessageIsWrittenOutInEveryLanguage() {
    val damaged = ArchiveRefused(ArchiveRefused.Reason.DamagedEvidence, "Rue du Simplon 12")
    val unreadable = ArchiveRefused(ArchiveRefused.Reason.UnreadableRecord)
    fun messages(s: Str) = listOf(s.recoverFailed, s.previewFailed, s.archiveFailure(damaged, true), s.archiveFailure(damaged, false), s.archiveFailure(unreadable, true))
    val perLanguage = Lang.entries.map { messages(Str(it)) }
    messages(Str(Lang.En)).indices.forEach { i ->
      val sentences = perLanguage.map { it[i] }
      assertTrue("message $i", sentences.all { it.isNotBlank() && '\u2014' !in it && 'ß' !in it })
      assertEquals("message $i", Lang.entries.size, sentences.distinct().size)
    }
    // The way to keep the other records safe is named for a full backup and left out of a single record's.
    Lang.entries.forEach { lang ->
      val s = Str(lang)
      val whole = s.archiveFailure(damaged, true)
      val single = s.archiveFailure(damaged, false)
      assertTrue("$lang remedy", whole.startsWith(single) && whole.length > single.length)
    }
  }

  @Test
  fun everyIntakeMessageIsWrittenOutInEveryLanguage() {
    fun messages(s: Str) = listOf(s.photoNotAdded, s.documentNotAdded, s.evidenceAfterFinalized,
      s.signatureNotSaved, s.signatureWaitsForEvidence, s.documentOpenFailed)
    val perLanguage = Lang.entries.map { messages(Str(it)) }
    messages(Str(Lang.En)).indices.forEach { i ->
      val sentences = perLanguage.map { it[i] }
      assertTrue("intake message $i", sentences.all { it.isNotBlank() && '\u2014' !in it && 'ß' !in it })
      // A sentence left in English under another language would collapse into one here.
      assertEquals("intake message $i", Lang.entries.size, sentences.distinct().size)
    }
  }

  @Test
  fun everySessionListAndExportLineIsWrittenOutInEveryLanguage() {
    fun lines(s: Str) = listOf(s.amendedEntryNote, s.keptInApp, s.copyBeforeDeliveryChange)
    val perLanguage = Lang.entries.map { lines(Str(it)) }
    lines(Str(Lang.En)).indices.forEach { i ->
      val sentences = perLanguage.map { it[i] }
      assertTrue("session line $i", sentences.all { it.isNotBlank() && '\u2014' !in it && 'ß' !in it })
      assertEquals("session line $i", Lang.entries.size, sentences.distinct().size)
    }
  }

  @Test
  fun noEszettAnywhereInGerman() {
    // Swiss German writes ss. The whole class is exercised via the enums plus a sample of
    // prose lines; a full sweep lives in the source review, this is the tripwire.
    val s = Str(Lang.De)
    val sample = buildString {
      RoomKind.entries.forEach { append(s.room(it)) }
      ItemKey.entries.forEach { append(s.item(it)) }
      append(s.emptyCase).append(s.infoCharged).append(s.infoOneSided).append(s.tipOneSided())
    }
    assertTrue(!sample.contains('ß'))
  }
}
