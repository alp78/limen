package dev.peringer.limen.sense

/**
 * Indicative residual replacement value using a declared useful life and age since
 * installation or renovation. This arithmetic does not establish tenant liability,
 * determine repair/cleaning compensation, or create a payment obligation. The table is
 * guidance; users retain the source and may enter a supported different useful life.
 */
object Zeitwert {

  /** The unamortized fraction of a thing's value, in 0..1. */
  fun remainingFraction(lifespanYears: Double, ageYears: Double): Double {
    require(lifespanYears.isFinite() && ageYears.isFinite())
    if (lifespanYears <= 0.0) return 0.0
    if (ageYears < 0.0) return 1.0
    return ((lifespanYears - ageYears) / lifespanYears).coerceIn(0.0, 1.0)
  }

  /** Legacy API name retained for callers; returns an estimate, never an adjudicated debt. */
  fun tenantShare(costChf: Double, lifespanYears: Double, ageYears: Double): Double {
    return remainingReplacementValue(costChf, lifespanYears, ageYears)
  }

  /** Indicative depreciated replacement value; neither liability nor repair compensation. */
  fun remainingReplacementValue(costChf: Double, lifespanYears: Double, ageYears: Double): Double {
    require(costChf.isFinite())
    if (costChf <= 0.0) return 0.0
    return costChf * remainingFraction(lifespanYears, ageYears)
  }
}

/** One row of the shipped table: what, how long it lives, and who says so. */
data class LifeItem(val key: LifeKey, val years: Double)

/**
 * The publicly cited lifespans. Deliberately NOT the full 80-page table, which is a
 * copyrighted brochure sold by HEV and MV; these are the values their own public pages
 * and the tenant-association literature quote, and the face links to the brochure for
 * the rest. Values as of the 2021 public excerpts; the 2024 edition is checked at
 * release time.
 */
enum class LifeKey { DispersionPaint, AcrylicPaint, WoodchipWallpaper, PaintableWallpaper,
  Carpet, NeedleFelt, LaminateMid, SolidParquet, MultilayerParquet, ParquetSeal, Pvc,
  Linoleum, CeramicTiles, Fridge, Freezer, Stove, Dishwasher, Microwave, Steamer,
  ExtractorHood, Taps, BathtubAcrylic, BathtubSteel, CeramicSink, MirrorCabinet,
  WashingMachine, DoorSolid, LockCylinder, RollerShutterWood, RollerShutterMetal,
  BlindStraps, SealantJoints, Radiator }

object Lifespans {
  val table: List<LifeItem> = listOf(
    LifeItem(LifeKey.DispersionPaint, 8.0),
    LifeItem(LifeKey.AcrylicPaint, 15.0),
    LifeItem(LifeKey.WoodchipWallpaper, 10.0),
    LifeItem(LifeKey.PaintableWallpaper, 24.0),
    LifeItem(LifeKey.Carpet, 10.0),
    LifeItem(LifeKey.NeedleFelt, 8.0),
    LifeItem(LifeKey.LaminateMid, 15.0),
    LifeItem(LifeKey.SolidParquet, 40.0),
    LifeItem(LifeKey.MultilayerParquet, 30.0),
    LifeItem(LifeKey.ParquetSeal, 10.0),
    LifeItem(LifeKey.Pvc, 10.0),
    LifeItem(LifeKey.Linoleum, 20.0),
    LifeItem(LifeKey.CeramicTiles, 30.0),
    LifeItem(LifeKey.Fridge, 10.0),
    LifeItem(LifeKey.Freezer, 15.0),
    LifeItem(LifeKey.Stove, 15.0),
    LifeItem(LifeKey.Dishwasher, 15.0),
    LifeItem(LifeKey.Microwave, 15.0),
    LifeItem(LifeKey.Steamer, 10.0),
    LifeItem(LifeKey.ExtractorHood, 10.0),
    LifeItem(LifeKey.Taps, 20.0),
    LifeItem(LifeKey.BathtubAcrylic, 25.0),
    LifeItem(LifeKey.BathtubSteel, 35.0),
    LifeItem(LifeKey.CeramicSink, 35.0),
    LifeItem(LifeKey.MirrorCabinet, 10.0),
    LifeItem(LifeKey.WashingMachine, 15.0),
    LifeItem(LifeKey.DoorSolid, 30.0),
    LifeItem(LifeKey.LockCylinder, 20.0),
    LifeItem(LifeKey.RollerShutterWood, 25.0),
    LifeItem(LifeKey.RollerShutterMetal, 30.0),
    LifeItem(LifeKey.BlindStraps, 8.0),
    LifeItem(LifeKey.SealantJoints, 9.0),
    LifeItem(LifeKey.Radiator, 50.0),
  )

  fun yearsOf(key: LifeKey): Double = table.first { it.key == key }.years
}
