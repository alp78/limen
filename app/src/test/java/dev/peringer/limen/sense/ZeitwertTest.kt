package dev.peringer.limen.sense

import org.junit.Assert.assertEquals
import org.junit.Test

class ZeitwertTest {

  @Test
  fun halfLifeIsHalfShare() {
    // The canonical example: dispersion paint lives 8 years, damaged after 4, the tenant
    // pays half of the repaint.
    assertEquals(0.5, Zeitwert.remainingFraction(8.0, 4.0), 1e-9)
    assertEquals(600.0, Zeitwert.tenantShare(1200.0, 8.0, 4.0), 1e-9)
  }

  @Test
  fun pastLifespanIsZeroHoweverRealTheDamage() {
    assertEquals(0.0, Zeitwert.tenantShare(5000.0, 8.0, 8.0), 1e-9)
    assertEquals(0.0, Zeitwert.tenantShare(5000.0, 8.0, 23.0), 1e-9)
  }

  @Test
  fun brandNewIsFullShare() {
    assertEquals(1200.0, Zeitwert.tenantShare(1200.0, 8.0, 0.0), 1e-9)
  }

  @Test
  fun negativeAgeReadsAsNew() {
    // A typo should never produce a share above the cost.
    assertEquals(1.0, Zeitwert.remainingFraction(8.0, -2.0), 1e-9)
  }

  @Test
  fun zeroLifespanNeverDivides() {
    assertEquals(0.0, Zeitwert.remainingFraction(0.0, 3.0), 1e-9)
  }

  @Test
  fun zeroOrNegativeCostIsZero() {
    assertEquals(0.0, Zeitwert.tenantShare(0.0, 8.0, 1.0), 1e-9)
    assertEquals(0.0, Zeitwert.tenantShare(-50.0, 8.0, 1.0), 1e-9)
  }

  @Test
  fun theAslocaWallpaperExample() {
    // ASLOCA's own worked case: wallpaper lives 10 years, the tenant leaves after 8,
    // and owes 20 percent of the residual value.
    assertEquals(0.2, Zeitwert.remainingFraction(10.0, 8.0), 1e-9)
  }

  @Test
  fun shippedTableHoldsTheCanonicalValues() {
    assertEquals(8.0, Lifespans.yearsOf(LifeKey.DispersionPaint), 1e-9)
    assertEquals(10.0, Lifespans.yearsOf(LifeKey.Fridge), 1e-9)
    assertEquals(40.0, Lifespans.yearsOf(LifeKey.SolidParquet), 1e-9)
    assertEquals(8.0, Lifespans.yearsOf(LifeKey.BlindStraps), 1e-9)
  }
}
