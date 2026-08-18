package dev.verso.limen.ui

import dev.verso.limen.data.Appliance
import dev.verso.limen.data.CautionProvider
import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.DeliveryMethod
import dev.verso.limen.data.DossierCard
import dev.verso.limen.data.Furniture
import dev.verso.limen.data.Grade
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.KeyKind
import dev.verso.limen.data.KeyTransferStatus
import dev.verso.limen.data.InitialRentFormRule
import dev.verso.limen.data.LegalCheckStatus
import dev.verso.limen.data.LegalRequirement
import dev.verso.limen.data.LegalProfile
import dev.verso.limen.data.ObjectGroup
import dev.verso.limen.data.Role
import dev.verso.limen.data.RoomKind
import dev.verso.limen.data.SwissCanton
import dev.verso.limen.data.UnilateralReason
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
      // Every place in the flow says what it is for; a place with no lead is a place a
      // person is standing in without being told why.
      FlowStep.entries.forEach { assertTrue("$lang lead $it", s.stepLead(it).isNotBlank()) }
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
