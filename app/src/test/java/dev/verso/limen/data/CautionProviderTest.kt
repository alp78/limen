package dev.verso.limen.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CautionProviderTest {

  @Test
  fun recognizesListedInsurersByTheCertificateName() {
    assertEquals(CautionProvider.SwissCaution, CautionProvider.fromStoredName("SwissCaution"))
    assertEquals(CautionProvider.GoCaution, CautionProvider.fromStoredName(" goCaution "))
    assertEquals(CautionProvider.Zurich, CautionProvider.fromStoredName("Zurich"))
  }

  @Test
  fun preservesAnUnlistedInsurerAsOther() {
    assertEquals(CautionProvider.Other, CautionProvider.fromStoredName("Local Guarantee SA"))
  }
}
