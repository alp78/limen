package dev.peringer.limen.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DocumentNameTest {

  private val exportDay: LocalDate = LocalDate.of(2026, 10, 21)

  @Test fun theNameIsTheAddressAndTheHandoverDayWithDashesBetween() {
    assertEquals("Avenue-de-la-Mesure-30-2026-10-13", DocumentName.of("Avenue de la Mesure 30", "2026-10-13", exportDay))
  }

  @Test fun lettersFoldToAsciiAndAnythingElseIsOneDash() {
    assertEquals("Rue-de-l-Eglise-5-2026-10-13", DocumentName.of("Rue de l’Église 5", "2026-10-13", exportDay))
    assertEquals("Muhlestrasse-7-2026-10-13", DocumentName.of("Mühlestrasse 7", "2026-10-13", exportDay))
    assertEquals("Via-Sasso-12-2026-10-13", DocumentName.of("  Via  Sasso, 12 ", "2026-10-13", exportDay))
  }

  @Test fun aBlankAddressIsNamedLimenAndAnUnsetDayIsTheDayOfTheExport() {
    assertEquals("Limen-2026-10-21", DocumentName.of("", "", exportDay))
    assertEquals("Auditstrasse-1-2026-10-21", DocumentName.of("Auditstrasse 1", "13/10/2026", exportDay))
  }

  @Test fun theAddressIsCutAtEightyCharacters() {
    assertEquals("a".repeat(80) + "-2026-10-13", DocumentName.of("a".repeat(100), "2026-10-13", exportDay))
  }

  @Test fun aSecondExportOfTheDayTakesTheNextFreeNumber() {
    val taken = setOf("X-2026-10-13", "X-2026-10-13-2")
    assertEquals("X-2026-10-13-3", DocumentName.unused("X-2026-10-13") { it in taken })
    assertEquals("Y-2026-10-13", DocumentName.unused("Y-2026-10-13") { it in taken })
  }
}
