package dev.peringer.limen

import android.content.res.AssetManager
import dev.peringer.limen.data.*
import dev.peringer.limen.ui.Lang
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The scene the store screenshots are taken on, one per language: the five the iPhone's
 * StoreScene.swift carries, so an agency comparing the two stores sees one handover. Each
 * language has its own canton, town, names and telephone, because a German listing showing
 * a Lausanne address reads as the French one with the words swapped.
 *
 * Three defects in three rooms, each with one photograph from the test assets. The
 * photographs are free-licence pictures the owner chose on 25 October 2026, after the
 * devices and disks held none of a defect; they live in the test APK and never in the app.
 */
internal object StoreScene {

  class Scene(
    val lang: Lang,
    /** Play's code for the listing language; Romansh is plain "rm". */
    val locale: String,
    val canton: SwissCanton,
    val street: String,
    val place: String,
    val floor: String,
    val tenant: String,
    val tenantAddress: String,
    val agency: String,
    val representative: String,
    val phone: String,
    val controllerStreet: String,
    val controllerPostal: String,
    val controllerCity: String,
    /** What lets the representative sign for the agency; a party represented by someone needs one. */
    val authority: String,
    val kitchenStain: String,
    val tenantComment: String,
    val bathroomTile: String,
    val parquetScratches: String,
  )

  fun of(lang: Lang): Scene = when (lang) {
    Lang.En -> Scene(
      lang, "en-US", SwissCanton.ZG, "Alpenstrasse 9", "6300 Zug", "2nd floor, left",
      "Daniel Foster", "Chamerstrasse 14, 6300 Zug",
      "Lakeside Property AG", "Emma Clarke", "+41415551234", "Baarerstrasse 25", "6300", "Zug",
      "Management mandate",
      "Water stain 30 x 20 cm on the wall above the worktop, dry to the touch",
      "The stain was there when I moved in",
      "Hole 1 cm across in a wall tile beside the washbasin",
      "Scratches 40 cm long in the parquet in front of the balcony door",
    )
    Lang.Fr -> Scene(
      lang, "fr-FR", SwissCanton.VD, "Rue de Bourg 12", "1003 Lausanne", "2e, porte gauche",
      "Camille Rochat", "Chemin de Boissonnet 4, 1010 Lausanne",
      "Vallonge Immobilier SA", "Sophie Marchand", "+41215551234", "Avenue de Cour 4", "1007", "Lausanne",
      "Mandat de gérance",
      "Tache d’eau de 30 x 20 cm sur le mur au-dessus du plan de travail, sèche au toucher",
      "La tache existait quand j’ai pris possession du logement",
      "Trou de 1 cm dans un carreau mural à côté du lavabo",
      "Rayures de 40 cm sur le parquet devant la porte du balcon",
    )
    Lang.De -> Scene(
      lang, "de-DE", SwissCanton.BE, "Effingerstrasse 42", "3008 Bern", "2. Stock, links",
      "Andrea Baumann", "Waldheimstrasse 12, 3013 Bern",
      "Aarequai Immobilien AG", "Sandra Keller", "+41315551234", "Monbijoustrasse 18", "3011", "Bern",
      "Verwaltungsmandat",
      "Wasserfleck 30 x 20 cm an der Wand über der Arbeitsfläche, trocken",
      "Der Fleck war beim Einzug schon da",
      "Loch von 1 cm in einer Wandplatte neben dem Lavabo",
      "40 cm lange Kratzer im Parkett vor der Balkontür",
    )
    Lang.It -> Scene(
      lang, "it-IT", SwissCanton.TI, "Via Nassa 18", "6900 Lugano", "2o piano, a sinistra",
      "Marco Donati", "Via Zurigo 9, 6900 Lugano",
      "Verzasca Immobiliare SA", "Chiara Bernasconi", "+41915551234", "Via Cattori 6", "6900", "Lugano",
      "Mandato di gestione",
      "Macchia d’acqua di 30 x 20 cm sulla parete sopra il piano di lavoro, asciutta al tatto",
      "La macchia c’era già alla consegna",
      "Foro di 1 cm in una piastrella a parete accanto al lavabo",
      "Graffi di 40 cm sul parquet davanti alla porta del balcone",
    )
    Lang.Rm -> Scene(
      lang, "rm", SwissCanton.GR, "Via Centrala 12", "7130 Ilanz", "2. plaun, a sanestra",
      "Anna Casanova", "Sut Vitg 3, 7130 Ilanz",
      "Sursilva Immobiglias SA", "Gion Caduff", "+41815551234", "Via Cadi 5", "7130", "Ilanz",
      "Mandat d’administraziun",
      "Macla d’aua da 30 x 20 cm sin la paraid sur la surfatscha da lavur, sitga",
      "La macla era gia qua cun l’entrada",
      "Fora d’1 cm en ina plattina da paraid sper il lavabo",
      "Sgrattadas da 40 cm en il parchet davant la porta dal balcun",
    )
  }

  fun id(lang: Lang): String = "store-${lang.name.lowercase()}"

  /** The handover moment the iPhone scene uses; the photographs are taken minutes after it. */
  val handover: ZonedDateTime = ZonedDateTime.of(2026, 9, 10, 12, 0, 0, 0, ZoneId.of("Europe/Zurich"))

  private class Shot(val defectId: String, val asset: String, val minutesAfterHandover: Long)

  private val shots = listOf(
    Shot("d1", "store/kitchen-wall-water-stain.jpg", 18),
    Shot("d2", "store/bathroom-tile-hole.jpg", 31),
    Shot("d3", "store/living-room-parquet-scratches.jpg", 44),
  )

  /**
   * Builds the record and saves it as a draft complete to the last rule except the two
   * signatures: the walk draws those on the pad and lets the app finalize, so the ink, the
   * fingerprint and the legal snapshot are the app's own. [assets] is the test APK's.
   */
  fun seed(store: Store, lang: Lang, assets: AssetManager): Protocol {
    val scene = of(lang)
    val id = id(lang)
    val photoDir = store.photoDir(id).apply { mkdirs() }
    // Named as the app names a capture, since the file name prints in the annex legend.
    val photos = shots.associate { shot ->
      val captured = handover.plusMinutes(shot.minutesAfterHandover).toInstant().toEpochMilli()
      val file = File(photoDir, "${shot.defectId}-$captured-1.jpg")
      assets.open(shot.asset).use { input -> file.outputStream().use(input::copyTo) }
      shot.defectId to PhotoEvidence(file.name, ProtocolIntegrity.fileHash(file), captured, PhotoSource.Camera)
    }
    fun evidence(defectId: String) = listOf(photos.getValue(defectId))
    val (firstName, lastName) = scene.representative.split(" ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val draft = Protocol(
      id = id,
      kind = Kind.Exit,
      createdEpochMs = 1L,
      sessionRole = Role.Landlord,
      sessionName = scene.representative,
      sessionIdentity = SessionIdentity(
        firstName = firstName, lastName = lastName, company = scene.agency,
        street = scene.controllerStreet, postalCode = scene.controllerPostal, city = scene.controllerCity, phone = scene.phone,
      ),
      controllerIdentity = ControllerIdentity(
        organisation = scene.agency, contactName = scene.representative,
        street = scene.controllerStreet, postalCode = scene.controllerPostal, city = scene.controllerCity, phone = scene.phone,
      ),
      address = scene.street,
      place = scene.place,
      objectCanton = scene.canton,
      objectDetail = scene.floor,
      handoverDate = "2026-09-10",
      handoverTime = "12:00",
      contract = Contract(conclusionDate = "2021-03-02", startDate = "2021-04-01", endDate = "2026-09-30", noticeDate = "2026-06-27"),
      parties = listOf(
        Party(Role.Tenant, scene.tenant, address = scene.tenantAddress, id = "tenant"),
        Party(
          Role.Landlord, scene.agency, representedBy = scene.representative, phone = scene.phone,
          id = "landlord", agency = scene.agency, authority = scene.authority,
        ),
      ),
      rooms = listOf(
        Room("r1", RoomKind.Hall, inspected = true),
        Room("r2", RoomKind.LivingRoom, inspected = true),
        Room("r3", RoomKind.Kitchen, inspected = true),
        Room("r4", RoomKind.Bathroom, inspected = true),
        Room("r5", RoomKind.Bedroom, inspected = true),
      ),
      roomSetupComplete = true,
      defects = listOf(
        Defect(
          "d1", roomId = "r3", item = ItemKey.Walls, text = scene.kitchenStain,
          photos = evidence("d1").map { it.file }, photoEvidence = evidence("d1"),
          tenantResponse = TenantResponse.Disputes, tenantComment = scene.tenantComment,
        ),
        Defect(
          "d2", roomId = "r4", item = ItemKey.Tiles, text = scene.bathroomTile,
          photos = evidence("d2").map { it.file }, photoEvidence = evidence("d2"),
          chargedToTenant = true, fixBy = FixBy.Tenant, costNote = "CHF 180",
        ),
        Defect(
          "d3", roomId = "r2", item = ItemKey.Floor, text = scene.parquetScratches,
          photos = evidence("d3").map { it.file }, photoEvidence = evidence("d3"),
          tenantResponse = TenantResponse.NormalWear,
        ),
      ),
      dossier = listOf(DossierCard.Contract, DossierCard.Keys),
      dossierChosen = true,
      keys = listOf(KeyCount(KeyKind.Apartment, count = 3), KeyCount(KeyKind.Mailbox, count = 1)),
      keyTransfer = KeyTransferStatus.AllTransferred,
    )
    // Every requirement the canton resolves to, as the iPhone seed answers them: Vaud carries
    // rows Zug does not, and a scene that answered only the shared ones could not finalize.
    val overlay = draft.resolvedLegalOverlay()!!.requirements
      .fold(LegalOverlayRecord()) { record, requirement -> record.withStatus(requirement, LegalCheckStatus.Confirmed) }
    val p = draft.copy(legalOverlay = overlay)
    store.save(p)
    return p
  }
}
