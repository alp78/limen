package dev.peringer.limen.sense

/**
 * The arithmetic of the paritaetische Lebensdauertabelle (MV + HEV), the joint lifespan
 * table Swiss conciliation boards compute with. Two iron rules, both encoded here and
 * printed wherever the numbers appear:
 *
 * 1. The tenant owes only the remaining value: cost x (lifespan - age) / lifespan.
 *    Past the full lifespan the share is zero, however real the damage.
 * 2. Age counts from the LAST RENOVATION of the surface or device, not from the start of
 *    the tenancy; a flat taken over unrenovated carries its full age.
 *
 * The table itself carries no binding force (its own disclaimer), so the values shipped
 * in [Lifespans] are the publicly cited ones with sources, and every lifespan can be
 * overridden by hand.
 */
object Zeitwert {

  /** The unamortized fraction of a thing's value, in 0..1. */
  fun remainingFraction(lifespanYears: Double, ageYears: Double): Double {
    if (lifespanYears <= 0.0) return 0.0
    if (ageYears < 0.0) return 1.0
    return ((lifespanYears - ageYears) / lifespanYears).coerceIn(0.0, 1.0)
  }

  /** The tenant's share of a repair, by remaining value. Zero past the lifespan. */
  fun tenantShare(costChf: Double, lifespanYears: Double, ageYears: Double): Double {
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
