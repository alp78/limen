package dev.verso.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternationalIdentityTest {
  @Test fun validatesAndFormatsNumbersForTheirSelectedCountry() {
    assertTrue(InternationalPhone.isValid("021 729 71 10", "CH"))
    assertTrue(InternationalPhone.isValid("076 264 31 24", "CH"))
    assertTrue(InternationalPhone.isValid("020 7031 3000", "GB"))
    assertTrue(InternationalPhone.isValid("202 456 1111", "US"))
    assertEquals("+41762643124", InternationalPhone.format("076 264 31 24", "CH"))
    assertEquals("+442070313000", InternationalPhone.format("020 7031 3000", "GB"))
  }

  @Test fun rejectsAValidNumberWhenItDoesNotMatchTheSelectedCountry() {
    assertFalse(InternationalPhone.isValid("020 7031 3000", "CH"))
    assertFalse(InternationalPhone.isValid("020 7031 300", "GB"))
  }

  @Test fun acceptsACompleteForeignAddress() {
    assertTrue(
      SessionIdentity(
        firstName = "Ada",
        lastName = "Lovelace",
        street = "St James's Square",
        flatNumber = "1",
        postalCode = "SW1Y 4JU",
        city = "London",
        addressCountry = "GB",
        usesSwissAddress = false,
        phone = "020 7031 3000",
        phoneRegion = "GB",
      ).hasRequiredFields(),
    )
  }

  @Test fun parsesOfficialAddressSearchResults() {
    val matches = SwissAddressLookup.parse(
      """{"results":[{"attrs":{"origin":"address","label":"Avenue de la Gare 1 <b>1003 Lausanne</b>"}}]}""",
    )
    assertEquals(listOf(SwissAddressCandidate("Avenue de la Gare", "1", "1003", "Lausanne")), matches)
  }
}
