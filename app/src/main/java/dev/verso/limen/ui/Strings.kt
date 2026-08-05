package dev.verso.limen.ui

import dev.verso.limen.data.Appliance
import dev.verso.limen.data.CautionProvider
import dev.verso.limen.data.DepositKind
import dev.verso.limen.data.DossierCard
import dev.verso.limen.data.Furniture
import dev.verso.limen.data.Grade
import dev.verso.limen.data.ItemKey
import dev.verso.limen.data.KeyKind
import dev.verso.limen.data.ObjectGroup
import dev.verso.limen.data.Role
import dev.verso.limen.data.RoomKind
import dev.verso.limen.data.TenantResponse
import java.util.Locale

/**
 * The five languages of the Swiss handover. English is the fallback; Romansh is a first
 * for the portfolio. The RM strings use terminology verified against the canton of
 * Grisons' own tenancy forms (locatur, locatari, contract da locaziun, suttascripziun,
 * autoritad da mediaziun); where no official term exists the wording is coined
 * conservatively and the whole language is flagged for review by a native speaker
 * before store release.
 */
enum class Lang {
  En, Fr, De, It, Rm;

  companion object {
    fun default(): Lang =
      when (Locale.getDefault().language) {
        "fr" -> Fr
        "de" -> De
        "it" -> It
        "rm" -> Rm
        else -> En
      }

    fun from(name: String?): Lang = entries.firstOrNull { it.name == name } ?: default()
  }
}

/**
 * Every word the instrument speaks, in all five languages at once. German follows Swiss
 * usage, ss and never the eszett. Legal terms follow each language's own canon (etat des
 * lieux, Uebergabeprotokoll, verbale di consegna), not translations of one another.
 */
class Str(val lang: Lang) {
  private fun pick(en: String, fr: String, de: String, it: String, rm: String) =
    when (lang) {
      Lang.En -> en
      Lang.Fr -> fr
      Lang.De -> de
      Lang.It -> it
      Lang.Rm -> rm
    }

  // The faces, as the pills and the case name them.
  val faceValue = pick("WEAR", "VÉTUSTÉ", "ZEITWERT", "VETUSTÀ", "VALUR")
  val faceClocks = pick("DEADLINES", "DÉLAIS", "FRISTEN", "TERMINI", "TERMINS")

  // What each case band offers.
  val homeValue = pick(
    "Who pays what, by lifespan",
    "Qui paie quoi, selon la longévité",
    "Wer was zahlt, nach Lebensdauer",
    "Chi paga cosa, secondo la durata di vita",
    "Tgi che paja tge, tenor la durada da vita",
  )
  val homeClocks = pick(
    "The legal clocks of the handover",
    "Les délais légaux de la remise",
    "Die gesetzlichen Fristen der Übergabe",
    "I termini legali della riconsegna",
    "Ils termins legals da la surdada",
  )

  // The case (home).
  val newProtocol = pick(
    "NEW PROTOCOL",
    "NOUVEL ÉTAT DES LIEUX",
    "NEUES PROTOKOLL",
    "NUOVO VERBALE",
    "NOV PROTOCOL",
  )
  val emptyCase = pick(
    "The threshold: where the flat changes hands. Protocols made here stay on this phone, are signed by both parties on the spot, and leave only as the PDF you export.",
    "Le seuil : là où le logement change de mains. Les états des lieux créés ici restent sur ce téléphone, se signent par les deux parties sur place, et n'en sortent que par le PDF que vous exportez.",
    "Die Schwelle: wo die Wohnung den Besitzer wechselt. Protokolle bleiben auf diesem Telefon, werden vor Ort von beiden Parteien unterzeichnet und verlassen es nur als das PDF, das Sie exportieren.",
    "La soglia: dove l'alloggio cambia di mano. I verbali restano su questo telefono, si firmano sul posto da entrambe le parti ed escono solo come il PDF che esportate.",
    "Qui vegnan fatgs ils protocols da surdada da l'abitaziun. Els restan sin quest telefon, vegnan suttascrits al lieu da tuttas duas partidas ed al bandunan mo sco il PDF che Vus exportais.",
  )
  val protocols = pick("Protocols", "États des lieux", "Protokolle", "Verbali", "Protocols")

  // Protocol kinds and states.
  val entry = pick("Entry", "Entrée", "Einzug", "Consegna", "Entrada")
  val exit = pick("Exit", "Sortie", "Auszug", "Riconsegna", "Sortida")
  val draft = pick("Draft", "Brouillon", "Entwurf", "Bozza", "Sboz")
  val signed = pick("Signed", "Signé", "Unterzeichnet", "Firmato", "Suttascrit")
  val finalized = pick("Finalized", "Finalisé", "Abgeschlossen", "Finalizzato", "Finalisà")
  val awaitingSignatures = pick(
    "Awaiting signatures", "En attente des signatures", "Unterschriften ausstehend", "In attesa di firme", "Spetga sin suttascripziuns",
  )
  val exported = pick("Exported", "Exporté", "Exportiert", "Esportato", "Exportà")

  // Common keys.
  val back = pick("BACK", "RETOUR", "ZURÜCK", "INDIETRO", "ENAVOS")
  val next = pick("NEXT", "SUIVANT", "WEITER", "AVANTI", "VINAVANT")
  val cancel = pick("CANCEL", "ANNULER", "ABBRECHEN", "ANNULLA", "INTERRUMPER")
  val done = pick("DONE", "TERMINÉ", "FERTIG", "FATTO", "FINÌ")
  val delete = pick("DELETE", "SUPPRIMER", "LÖSCHEN", "ELIMINA", "STIZZAR")
  val add = pick("ADD", "AJOUTER", "HINZUFÜGEN", "AGGIUNGI", "AGIUNTAR")
  val editDefect = pick("EDIT DEFECT", "MODIFIER LE DÉFAUT", "MANGEL BEARBEITEN", "MODIFICA DIFETTO", "MODIFITGAR LA MANCANZA")

  // The flow's places. Not stations: nothing here is gated behind anything else.
  val stepHub = pick("Handover", "Remise", "Übergabe", "Consegna", "Surdada")
  val stepInspection = pick("Inspection", "Inspection", "Begehung", "Sopralluogo", "Inspecziun")
  val stepObjects = pick("Objects", "Objets", "Gegenstände", "Oggetti", "Objects")
  val stepDossier = pick("Dossier", "Dossier", "Dossier", "Dossier", "Dossier")
  val stepReview = pick("Review", "Relecture", "Durchsicht", "Riesame", "Controlla")
  val stepSign = pick("Signatures", "Signatures", "Unterschriften", "Firme", "Suttascripziuns")
  val stepExport = pick("Export", "Export", "Export", "Esporta", "Export")

  // The hub.
  val hubInspectionLead = pick(
    "Rooms, damage and everything supplied with the flat",
    "Pièces, dégâts et tout ce qui est fourni avec le logement",
    "Räume, Schäden und alles, was zur Wohnung gehört",
    "Locali, danni e tutto ciò che è fornito con l'alloggio",
    "Locals, donns e tut quai che vegn cun l'abitaziun",
  )
  val hubDossierLead = pick(
    "Contract, deposit, meters, keys and the people",
    "Contrat, garantie, compteurs, clés et les personnes",
    "Vertrag, Kaution, Zähler, Schlüssel und die Personen",
    "Contratto, deposito, contatori, chiavi e le persone",
    "Contract, cauziun, dumbraders, clavs e las persunas",
  )
  val hubClose = pick("CLOSE THE RECORD", "CLORE LE CONSTAT", "PROTOKOLL ABSCHLIESSEN", "CHIUDI IL VERBALE", "SERRAR IL PROTOCOL")
  // Readouts name the thing and then count it, the way an instrument's dial is labelled.
  // That also sidesteps five languages' plural rules, which "1 rooms" was getting wrong.
  private val countRooms = pick("ROOMS", "PIÈCES", "RÄUME", "LOCALI", "LOCALS")
  private val countDefects = pick("DEFECTS", "DÉFAUTS", "MÄNGEL", "DIFETTI", "MANCANZAS")
  private val countObjects = pick("OBJECTS", "OBJETS", "GEGENSTÄNDE", "OGGETTI", "OBJECTS")
  private val countComponents = pick("COMPONENTS", "RUBRIQUES", "RUBRIKEN", "RUBRICHE", "RUBRICAS")
  fun countRooms(n: Int) = "$countRooms $n"
  fun countDefects(n: Int) = "$countDefects $n"
  fun countObjects(n: Int) = "$countObjects $n"
  fun countComponents(n: Int) = "$countComponents $n"
  val nothingYet = pick("Nothing recorded yet", "Rien de consigné", "Noch nichts erfasst", "Ancora nulla", "Anc nagut registrà")
  val stillMissing = pick("Still missing", "Encore manquant", "Noch offen", "Ancora mancante", "Anc mancant")

  // Setup.
  val protocolKind = pick("Protocol", "État des lieux", "Protokoll", "Verbale", "Protocol")
  val objectLabel = pick("Object", "Objet loué", "Mietobjekt", "Ente locato", "Object da locaziun")
  val address = pick("Street and number", "Rue et numéro", "Strasse und Nummer", "Via e numero", "Via e numer")
  val place = pick("Postcode and place", "NPA et localité", "PLZ und Ort", "NAP e località", "NPA e lieu")
  val objectDetail = pick("Floor and position", "Étage et position", "Stockwerk und Lage", "Piano e posizione", "Plaun ed posiziun")
  val handoverDate = pick("Date of handover", "Date de la remise", "Datum der Übergabe", "Data della consegna", "Data da la surdada")
  val handoverTime = pick("Time", "Heure", "Uhrzeit", "Ora", "Ura")
  val lastProtocol = pick(
    "Date of the previous protocol",
    "Date de l'état des lieux précédent",
    "Datum des letzten Protokolls",
    "Data del verbale precedente",
    "Data dal protocol precedent",
  )
  val parties = pick("Parties", "Parties", "Parteien", "Parti", "Partidas")
  val meters = pick("Meter readings", "Relevé des compteurs", "Zählerstände", "Letture dei contatori", "Posiziuns dals contaturs")
  val keysLabel = pick("Keys", "Clés", "Schlüssel", "Chiavi", "Clavs")
  val selfMadeKeys = pick(
    "Copies had been made by the tenant",
    "Le locataire a fait faire des copies",
    "Mieterseits wurden Kopien angefertigt",
    "Il conduttore ha fatto fare delle copie",
    "Il locatari ha fatg far copias",
  )
  val deposit = pick("Rent deposit", "Garantie de loyer", "Mietzinsdepot", "Deposito di garanzia", "Deposit da locaziun")
  val depositNone = pick("None", "Aucune", "Keines", "Nessuno", "Nagin")
  val depositBank = pick("Bank account", "Compte bancaire", "Sperrkonto", "Conto bancario", "Conto da banca")
  val depositInsurance = pick("Insurance", "Assurance", "Versicherung", "Assicurazione", "Assicuranza")
  val depositAmount = pick("Amount CHF", "Montant CHF", "Betrag CHF", "Importo CHF", "Import CHF")
  val depositProvider = pick("Caution insurer", "Assureur de caution", "Kautionsversicherer", "Assicuratore della garanzia", "Assicuratur da deposit")
  val otherCautionProvider = pick("Other insurer", "Autre assureur", "Anderer Versicherer", "Altro assicuratore", "Auter assicuratur")
  val depositPolicy = pick("Certificate no.", "No de certificat", "Zertifikat-Nr.", "N. certificato", "Nr. dal certificat")
  val depositBankName = pick("Bank", "Banque", "Bank", "Banca", "Banca")
  val depositAccount = pick("Account / IBAN", "Compte / IBAN", "Konto / IBAN", "Conto / IBAN", "Conto / IBAN")

  // Roles.
  fun role(r: Role) = when (r) {
    Role.Tenant -> pick("Tenant", "Locataire", "Mieter/in", "Conduttore/trice", "Locatari/a")
    Role.Landlord -> pick("Landlord / agency", "Bailleur / régie", "Vermieter/Verwaltung", "Locatore / amministrazione", "Locatur / administraziun")
    Role.NextTenant -> pick("Next tenant", "Locataire suivant", "Nachmieter/in", "Conduttore successivo", "Proxim locatari")
    Role.Witness -> pick("Witness", "Témoin", "Zeuge/Zeugin", "Testimone", "Perditga")
    Role.Expert -> pick("Expert", "Expert·e", "Experte/Expertin", "Perito/a", "Expert/a")
  }
  val representedBy = pick("Represented by", "Représenté·e par", "Vertreten durch", "Rappresentato/a da", "Represchentà/ada da")
  val newAddress = pick("New address", "Nouvelle adresse", "Neue Adresse", "Nuovo indirizzo", "Nova adressa")

  // Meters.
  val meterElec1 = pick("Electricity I", "Électricité I", "Elektrisch I", "Elettricità I", "Electricitad I")
  val meterElec2 = pick("Electricity II", "Électricité II", "Elektrisch II", "Elettricità II", "Electricitad II")
  val meterGas = pick("Gas", "Gaz", "Gas", "Gas", "Gas")
  val meterWater = pick("Water", "Eau", "Wasser", "Acqua", "Aua")
  val meterHotWater = pick("Hot water", "Eau chaude", "Warmwasser", "Acqua calda", "Aua chauda")
  val meterOil = pick("Heating oil", "Mazout", "Heizöl", "Olio da riscaldamento", "Ieli da stgaudar")

  // Keys.
  fun keyKind(k: KeyKind) = when (k) {
    KeyKind.Apartment -> pick("Apartment", "Appartement", "Wohnung", "Appartamento", "Abitaziun")
    KeyKind.HouseDoor -> pick("House door", "Porte d'immeuble", "Haustür", "Porta d'ingresso", "Porta da chasa")
    KeyKind.Mailbox -> pick("Mailbox", "Boîte aux lettres", "Briefkasten", "Bucalettere", "Chascha da brevs")
    KeyKind.Cellar -> pick("Cellar", "Cave", "Keller", "Cantina", "Tschaler")
    KeyKind.Attic -> pick("Attic", "Galetas", "Estrich", "Solaio", "Palantschin")
    KeyKind.Garage -> pick("Garage", "Garage", "Garage", "Garage", "Garascha")
    KeyKind.Other -> pick("Other", "Autre", "Weitere", "Altro", "Auter")
  }

  // Rooms.
  fun room(k: RoomKind) = when (k) {
    RoomKind.Kitchen -> pick("Kitchen", "Cuisine", "Küche", "Cucina", "Cuschina")
    RoomKind.Bathroom -> pick("Bathroom", "Salle de bains", "Bad/Dusche/WC", "Bagno", "Stanza da bogn")
    RoomKind.SepWC -> pick("Separate WC", "WC séparé", "Separates WC", "WC separato", "WC separà")
    RoomKind.Hall -> pick("Hall", "Entrée/hall", "Korridor", "Corridoio", "Corridor")
    RoomKind.LivingRoom -> pick("Living room", "Séjour", "Wohnzimmer", "Soggiorno", "Stiva")
    RoomKind.Bedroom -> pick("Bedroom", "Chambre", "Schlafzimmer", "Camera", "Chombra")
    RoomKind.Room -> pick("Room", "Pièce", "Zimmer", "Stanza", "Stanza")
    RoomKind.Balcony -> pick("Balcony/terrace", "Balcon/terrasse", "Balkon/Sitzplatz", "Balcone/terrazza", "Balcun")
    RoomKind.Cellar -> pick("Cellar", "Cave", "Keller", "Cantina", "Tschaler")
    RoomKind.Attic -> pick("Attic", "Galetas", "Estrich", "Solaio", "Palantschin")
    RoomKind.Garage -> pick("Garage/parking", "Garage/place de parc", "Garage/Abstellplatz", "Garage/posteggio", "Garascha")
    RoomKind.LaundryRoom -> pick("Laundry room", "Buanderie", "Waschküche", "Lavanderia", "Lavanderia")
    RoomKind.HobbyRoom -> pick("Hobby room", "Atelier", "Bastelraum", "Locale hobby", "Local da hobi")
    RoomKind.Mailbox -> pick("Mailbox", "Boîte aux lettres", "Briefkasten", "Bucalettere", "Chascha da brevs")
    RoomKind.Custom -> pick("Other room", "Autre pièce", "Weiterer Raum", "Altro locale", "Auter local")
  }

  // Item rows. The nouns of the paper forms, one per canonical row.
  fun item(k: ItemKey) = when (k) {
    ItemKey.Floor -> pick("Floor", "Sol", "Boden", "Pavimento", "Plaun")
    ItemKey.Walls -> pick("Walls", "Murs", "Wände", "Pareti", "Paraids")
    ItemKey.Ceiling -> pick("Ceiling", "Plafond", "Decke", "Soffitto", "Plafun")
    ItemKey.Skirtings -> pick("Skirtings", "Plinthes", "Sockelleisten", "Zoccolini", "Glistas dal plaun")
    ItemKey.Door -> pick("Door", "Porte", "Türe", "Porta", "Porta")
    ItemKey.DoorLock -> pick("Lock and key", "Serrure et clé", "Schloss und Schlüssel", "Serratura e chiave", "Serra e clav")
    ItemKey.Windows -> pick("Windows", "Fenêtres/vitres", "Fenster", "Finestre/vetri", "Fanestras")
    ItemKey.WindowSills -> pick("Window sills", "Tablettes", "Fenstersimse", "Davanzali", "Runsas da fanestra")
    ItemKey.Blinds -> pick("Blinds/shutters", "Stores/volets", "Rollläden/Storen", "Avvolgibili/gelosie", "Rulladas")
    ItemKey.BlindStraps -> pick("Straps/cranks", "Sangles/manivelles", "Gurten/Kurbeln", "Cinghie/manovelle", "Rissas/manivellas")
    ItemKey.CurtainRails -> pick("Curtain rails", "Rails à rideaux", "Vorhangschienen", "Binari per tende", "Sinas da cortinas")
    ItemKey.Radiator -> pick("Radiator/valve", "Radiateur/vanne", "Heizkörper/Ventil", "Radiatore/valvola", "Radiatur/ventil")
    ItemKey.SocketsSwitches -> pick("Sockets/switches", "Prises/interrupteurs", "Steckdosen/Schalter", "Prese/interruttori", "Stecs/interruptours")
    ItemKey.Lighting -> pick("Lighting", "Luminaires", "Beleuchtung", "Illuminazione", "Glischs")
    ItemKey.Stove -> pick("Stove", "Cuisinière", "Kochherd", "Cucina economica", "Furnel")
    ItemKey.Oven -> pick("Oven", "Four", "Backofen", "Forno", "Furn")
    ItemKey.OvenAccessories -> pick("Oven accessories", "Accessoires du four", "Backofenzubehör", "Accessori del forno", "Accessoris dal furn")
    ItemKey.ExtractorHood -> pick("Extractor hood", "Hotte de ventilation", "Dampfabzug", "Cappa aspirante", "Chapa d'aspiraziun")
    ItemKey.Fridge -> pick("Fridge", "Réfrigérateur", "Kühlschrank", "Frigorifero", "Frigorifer")
    ItemKey.Freezer -> pick("Freezer", "Congélateur", "Tiefkühler", "Congelatore", "Congelatur")
    ItemKey.Dishwasher -> pick("Dishwasher", "Lave-vaisselle", "Geschirrspüler", "Lavastoviglie", "Maschina da lavar plats")
    ItemKey.Sink -> pick("Sink", "Évier", "Schüttstein", "Lavello", "Lavadi")
    ItemKey.Tap -> pick("Tap/mixer", "Robinetterie", "Armatur/Batterie", "Rubinetteria", "Crana")
    ItemKey.Worktop -> pick("Worktop", "Plan de travail", "Abdeckung", "Piano di lavoro", "Plaun da lavur")
    ItemKey.Cupboards -> pick("Cupboards", "Armoires", "Schränke", "Armadi", "Stgaffas")
    ItemKey.Tiles -> pick("Tiles", "Catelles", "Plättli", "Piastrelle", "Plattas")
    ItemKey.Bathtub -> pick("Bathtub", "Baignoire", "Badewanne", "Vasca da bagno", "Vaschia da bogn")
    ItemKey.Shower -> pick("Shower", "Douche", "Dusche", "Doccia", "Duscha")
    ItemKey.ShowerHose -> pick("Shower hose", "Flexible de douche", "Brause und Schlauch", "Doccetta e tubo", "Tub da duscha")
    ItemKey.WC -> pick("WC bowl", "Cuvette WC", "Klosett", "Vaso WC", "WC")
    ItemKey.Cistern -> pick("Cistern", "Réservoir de chasse", "Spülkasten", "Cassetta di risciacquo", "Chascha da spelida")
    ItemKey.WCSeat -> pick("WC seat", "Lunette WC", "WC-Brille", "Sedile WC", "Sez dal WC")
    ItemKey.Washbasin -> pick("Washbasin", "Lavabo", "Lavabo", "Lavabo", "Lavabo")
    ItemKey.MirrorCabinet -> pick("Mirror cabinet", "Armoire à glace", "Spiegelschrank", "Armadietto a specchio", "Stgaffa da spievel")
    ItemKey.TowelRail -> pick("Towel rail", "Porte-linge", "Badetuchstange", "Portasciugamani", "Portatualas")
    ItemKey.SoapHolder -> pick("Soap holder", "Porte-savon", "Seifenschale", "Portasapone", "Portasavun")
    ItemKey.Ventilation -> pick("Ventilation", "Ventilation", "Lüftung", "Ventilazione", "Ventilaziun")
    ItemKey.CellarRack -> pick("Cellar rack", "Râtelier de cave", "Hurde", "Rastrelliera", "Palnitscha")
    ItemKey.WashingMachine -> pick("Washing machine", "Lave-linge", "Waschmaschine", "Lavatrice", "Maschina da lavar")
    ItemKey.Dryer -> pick("Dryer", "Sèche-linge", "Tumbler", "Asciugatrice", "Sitgentadur")
    ItemKey.MailboxLock -> pick("Mailbox and lock", "Boîte et serrure", "Briefkasten und Schloss", "Bucalettere e serratura", "Chascha da brevs e serra")
    ItemKey.Custom -> pick("Other", "Autre", "Weiteres", "Altro", "Auter")
  }

  fun appliance(a: Appliance) = when (a) {
    Appliance.Fridge -> pick("Fridge", "Réfrigérateur", "Kühlschrank", "Frigorifero", "Frigorifer")
    Appliance.Freezer -> pick("Freezer", "Congélateur", "Tiefkühler", "Congelatore", "Congelatur")
    Appliance.Oven -> pick("Oven", "Four", "Backofen", "Forno", "Furn")
    Appliance.Hob -> pick("Hob", "Plaques de cuisson", "Kochfeld", "Piano cottura", "Plaun da cuschinar")
    Appliance.Microwave -> pick("Microwave", "Micro-ondes", "Mikrowelle", "Microonde", "Microondas")
    Appliance.Dishwasher -> pick("Dishwasher", "Lave-vaisselle", "Geschirrspüler", "Lavastoviglie", "Maschina da lavar plats")
    Appliance.WashingMachine -> pick("Washing machine", "Lave-linge", "Waschmaschine", "Lavatrice", "Maschina da lavar")
    Appliance.Dryer -> pick("Dryer", "Sèche-linge", "Tumbler", "Asciugatrice", "Sitgentadur")
  }

  fun furniture(f: Furniture) = when (f) {
    Furniture.Bed -> pick("Bed", "Lit", "Bett", "Letto", "Letg")
    Furniture.Wardrobe -> pick("Wardrobe", "Armoire", "Kleiderschrank", "Armadio", "Stgaffa da vestgadira")
    Furniture.Table -> pick("Table", "Table", "Tisch", "Tavolo", "Maisa")
    Furniture.Chairs -> pick("Chairs", "Chaises", "Stühle", "Sedie", "Sutgas")
    Furniture.Sofa -> pick("Sofa", "Canapé", "Sofa", "Divano", "Sofa")
    Furniture.Desk -> pick("Desk", "Bureau", "Schreibtisch", "Scrivania", "Maisa da scriver")
    Furniture.Shelving -> pick("Shelving", "Étagères", "Regale", "Scaffali", "Regals")
    Furniture.Bookcase -> pick("Bookcase", "Bibliothèque", "Bücherregal", "Libreria", "Regal da cudeschs")
  }

  // Grades: the romand canon's letters, spelled out where there is room.
  fun grade(g: Grade) = when (g) {
    Grade.NotChecked -> pick("Not checked", "Non contrôlé", "Nicht geprüft", "Non controllato", "Betg controllà")
    Grade.Ok -> pick("In order", "Conforme", "In Ordnung", "In ordine", "En urden")
    Grade.VeryGood -> pick("Very good (TB)", "Très bon (TB)", "Sehr gut (TB)", "Molto buono (TB)", "Fitg bun (TB)")
    Grade.Good -> pick("Good (B)", "Bon (B)", "Gut (B)", "Buono (B)", "Bun (B)")
    Grade.Fair -> pick("Fair (M)", "Moyen (M)", "Mittel (M)", "Medio (M)", "Mesaun (M)")
    Grade.Poor -> pick("Poor (O)", "Mauvais (O)", "Schlecht (O)", "Cattivo (O)", "Nausch (O)")
  }
  fun gradeShort(g: Grade) = when (g) {
    Grade.NotChecked -> "· "
    Grade.Ok -> "OK"
    Grade.VeryGood -> "TB"
    Grade.Good -> "B"
    Grade.Fair -> "M"
    Grade.Poor -> "O"
  }

  fun depositKind(k: DepositKind) = when (k) {
    DepositKind.None -> depositNone
    DepositKind.BankAccount -> depositBank
    DepositKind.Insurance -> depositInsurance
  }

  fun cautionProvider(provider: CautionProvider) = when (provider) {
    CautionProvider.SwissCaution -> "SwissCaution"
    CautionProvider.GoCaution -> "goCaution"
    CautionProvider.Firstcaution -> "Firstcaution"
    CautionProvider.SmartCaution -> "SmartCaution"
    CautionProvider.Axa -> "AXA"
    CautionProvider.Helvetia -> "Helvetia"
    CautionProvider.Zurich -> "Zurich"
    CautionProvider.Other -> otherCautionProvider
  }

  // The inspection.
  val roomKeys = pick("Keys of this room", "Clés de cette pièce", "Schlüssel dieses Raums", "Chiavi di questo locale", "Clavs da quest local")
  val addRoom = pick("ADD ROOM", "AJOUTER UNE PIÈCE", "RAUM HINZUFÜGEN", "AGGIUNGI LOCALE", "AGIUNTAR IN LOCAL")
  val addDefect = pick("Note a defect", "Noter un défaut", "Mangel erfassen", "Annotare un difetto", "Registrar ina mancanza")
  val roomName = pick("Name of this room", "Nom de cette pièce", "Name dieses Raums", "Nome di questo locale", "Num da quest local")
  val recordDefect = pick("RECORD A DEFECT", "CONSIGNER UN DÉFAUT", "MANGEL ERFASSEN", "REGISTRA UN DIFETTO", "REGISTRAR INA MANCANZA")
  val pickItem = pick("Which item", "Quel élément", "Welches Element", "Quale elemento", "Tge element")
  val otherItem = pick("Something else", "Autre chose", "Etwas anderes", "Qualcos'altro", "Insatge auter")
  val otherItemName = pick("Name it", "Nommez-le", "Benennen", "Nominatelo", "Numnai el")
  val roomClean = pick(
    "No defect recorded in this room",
    "Aucun défaut consigné dans cette pièce",
    "In diesem Raum kein Mangel erfasst",
    "Nessun difetto registrato in questo locale",
    "Nagina mancanza registrada en quest local",
  )
  val clearRoom = pick("CLEAR ROOM", "VIDER LA PIÈCE", "RAUM LEEREN", "SVUOTA IL LOCALE", "SVIDAR IL LOCAL")
  val removeRoom = pick("REMOVE ROOM", "RETIRER LA PIÈCE", "RAUM ENTFERNEN", "RIMUOVI IL LOCALE", "ALLONTANAR IL LOCAL")
  val areYouSure = pick("ARE YOU SURE?", "ÊTES-VOUS SÛR·E ?", "SIND SIE SICHER?", "SIETE SICURI?", "ESSAS VUS SEGIR?")
  val clearRoomBody = pick(
    "This deletes every defect and photo recorded in this room. The room itself stays.",
    "Cette action supprime tous les défauts et photos consignés dans cette pièce. La pièce reste.",
    "Dadurch werden alle in diesem Raum erfassten Mängel und Fotos gelöscht. Der Raum bleibt.",
    "Questa azione elimina tutti i difetti e le foto registrati in questo locale. Il locale resta.",
    "Quai stizza tut las mancanzas e las fotos registradas en quest local. Il local resta.",
  )
  val removeRoomBody = pick(
    "This removes the room and everything recorded in it.",
    "Cette action retire la pièce et tout ce qui y est consigné.",
    "Dadurch werden der Raum und alles darin Erfasste entfernt.",
    "Questa azione rimuove il locale e tutto ciò che vi è registrato.",
    "Quai allontanescha il local e tut quai ch'è registrà en el.",
  )
  val yes = pick("YES", "OUI", "JA", "SÌ", "GEA")
  val no = pick("NO", "NON", "NEIN", "NO", "NA")
  val defects = pick("Defects", "Défauts", "Mängel", "Difetti", "Mancanzas")

  // Objects supplied with the flat. One row each, so a row can carry its own photograph.
  val objects = pick("Objects", "Objets", "Gegenstände", "Oggetti", "Objects")
  val appliances = pick("Appliances", "Appareils", "Geräte", "Elettrodomestici", "Apparats")
  val furniture = pick("Furniture", "Mobilier", "Möbel", "Arredi", "Mobigliar")
  fun objectGroup(group: ObjectGroup) = when (group) {
    ObjectGroup.Appliance -> appliances
    ObjectGroup.Furniture -> furniture
    ObjectGroup.Other -> pick("Other", "Autres", "Weitere", "Altro", "Auter")
  }
  val addObject = pick("Add something else", "Ajouter autre chose", "Weiteres hinzufügen", "Aggiungi altro", "Agiuntar insatge auter")
  val addCategory = pick("ADD A CATEGORY", "AJOUTER UNE CATÉGORIE", "KATEGORIE HINZUFÜGEN", "AGGIUNGI UNA CATEGORIA", "AGIUNTAR INA CATEGORIA")
  val categoryName = pick("Category name", "Nom de la catégorie", "Name der Kategorie", "Nome della categoria", "Num da la categoria")
  val objectNote = pick("Make, model, serial number", "Marque, modèle, numéro de série", "Marke, Modell, Seriennummer", "Marca, modello, numero di serie", "Marca, model, numer da seria")
  val objectRoom = pick("Where it stands", "Où il se trouve", "Wo er steht", "Dove si trova", "Nua ch'el sa chatta")
  val wholeFlat = pick("The flat", "Le logement", "Die Wohnung", "L'alloggio", "L'abitaziun")
  val noObjects = pick(
    "Nothing supplied with the flat yet",
    "Rien de fourni avec le logement",
    "Noch nichts zur Wohnung erfasst",
    "Nulla fornito con l'alloggio",
    "Anc nagut mess a disposiziun cun l'abitaziun",
  )
  val atEntry = pick("At entry", "À l'entrée", "Bei Einzug", "All'entrata", "Tar l'entrada")

  // The dossier's components.
  val identity = pick("Your details", "Vos coordonnées", "Ihre Angaben", "I vostri dati", "Vossas indicaziuns")
  val addComponent = pick("Add a component", "Ajouter une rubrique", "Rubrik hinzufügen", "Aggiungi una rubrica", "Agiuntar ina rubrica")
  val removeComponentBody = pick(
    "This removes the component and clears everything entered in it.",
    "Cette action retire la rubrique et efface tout ce qui y a été saisi.",
    "Dadurch wird die Rubrik entfernt und alles darin Erfasste gelöscht.",
    "Questa azione rimuove la rubrica e cancella tutto ciò che vi è stato inserito.",
    "Quai allontanescha la rubrica e stizza tut quai ch'è vegnì endatà en ella.",
  )
  val contract = pick("Contract", "Contrat", "Mietvertrag", "Contratto", "Contract")
  val monthlyRent = pick("Monthly rent CHF", "Loyer mensuel CHF", "Nettomietzins CHF", "Pigione mensile CHF", "Fittanza mensila CHF")
  val serviceCharges = pick("Service charges CHF", "Charges CHF", "Nebenkosten CHF", "Spese accessorie CHF", "Custs accessoris CHF")
  val tenancyStart = pick("Tenancy from", "Bail depuis le", "Mietbeginn", "Locazione dal", "Locaziun dapi ils")
  val tenancyEnd = pick("Tenancy until", "Bail jusqu'au", "Mietende", "Locazione fino al", "Locaziun enfin ils")
  val noticeDate = pick("Notice given on", "Résiliation notifiée le", "Kündigung erfolgt am", "Disdetta notificata il", "Disditga dada ils")
  val notes = pick("Notes", "Observations", "Bemerkungen", "Osservazioni", "Remartgas")
  fun dossierCard(card: DossierCard) = when (card) {
    DossierCard.Contract -> contract
    DossierCard.Deposit -> deposit
    DossierCard.Meters -> meters
    DossierCard.Keys -> keysLabel
    DossierCard.Notes -> notes
  }
  val addKeyKind = pick("Add a kind of key", "Ajouter un type de clé", "Schlüsselart hinzufügen", "Aggiungi un tipo di chiave", "Agiuntar in tip da clav")
  val keyLabel = pick("Which key", "Quelle clé", "Welcher Schlüssel", "Quale chiave", "Tge clav")

  // The defect editor.
  val defectText = pick(
    "Defect, precisely: what, where, how big",
    "Le défaut, précisément : quoi, où, quelle taille",
    "Der Mangel, präzis: was, wo, wie gross",
    "Il difetto, con precisione: cosa, dove, dimensione",
    "La mancanza, precis: tge, nua, con grond",
  )
  val defectHint = pick(
    "Example: water stain approx. 3 cm under the north window",
    "Exemple : tache d'eau d'env. 3 cm sous la fenêtre nord",
    "Beispiel: ca. 3 cm grosser Wasserfleck unter dem Nordfenster",
    "Esempio: macchia d'acqua di ca. 3 cm sotto la finestra nord",
    "Exempel: macla d'aua da ca. 3 cm sut la fanestra nord",
  )
  val chargedToTenant = pick(
    "Charged to the tenant",
    "Imputé au locataire",
    "Dem Mieter angelastet",
    "Imputato al conduttore",
    "Mess a quint al locatari",
  )
  val infoCharged = pick(
    "An exit protocol is only a valid defect notice for the defects it explicitly charges to the tenant. Unmarked findings document the state of the flat without a claim.",
    "L'état des lieux de sortie ne vaut avis des défauts que pour les défauts explicitement imputés au locataire. Les constats non marqués documentent l'état sans prétention.",
    "Das Rückgabeprotokoll gilt nur für die dem Mieter ausdrücklich angelasteten Mängel als Mängelrüge. Unmarkierte Befunde dokumentieren den Zustand ohne Forderung.",
    "Il verbale di riconsegna vale come notifica dei difetti solo per i difetti esplicitamente imputati al conduttore. I rilievi non marcati documentano lo stato senza pretese.",
    "Il protocol da sortida vala sco notificaziun da mancanzas mo per las mancanzas messas explicitamain a quint al locatari.",
  )
  val fixByLabel = pick("To be fixed by", "À réparer par", "Behebung durch", "Da riparare a cura di", "Da reparar tras")
  val fixOpen = pick("Open", "À définir", "Offen", "Da definire", "Avert")
  val fixDeadline = pick("By (date)", "D'ici au", "Bis am", "Entro il", "Fin ils")
  val costNote = pick("Cost note / estimate", "Coûts / devis", "Kosten / Voranschlag", "Costi / preventivo", "Custs / preventiv")
  val reservationLabel = pick("Reservation", "Réserve", "Vorbehalt", "Riserva", "Resalva")
  val photo = pick("PHOTO", "PHOTO", "FOTO", "FOTO", "FOTO")
  val gallery = pick("GALLERY", "GALERIE", "GALERIE", "GALLERIA", "GALARIA")
  val cameraOpenFailed = pick(
    "The camera could not be opened.",
    "L'appareil photo n'a pas pu être ouvert.",
    "Die Kamera konnte nicht geöffnet werden.",
    "Non è stato possibile aprire la fotocamera.",
    "La camera na pudeva betg vegnir averta.",
  )
  val tenantResponse = pick("Tenant's response", "Réponse du locataire", "Stellungnahme der Mieterschaft", "Posizione del conduttore", "Posiziun dal locatari")
  fun tenantResponse(value: TenantResponse) = when (value) {
    TenantResponse.NotStated -> pick("Not stated", "Non indiquée", "Nicht angegeben", "Non indicata", "Betg inditgà")
    TenantResponse.Agrees -> pick("Agrees", "Accepte", "Einverstanden", "Accetta", "Accepta")
    TenantResponse.Disputes -> pick("Disputes", "Conteste", "Bestreitet", "Contesta", "Contesta")
    TenantResponse.NormalWear -> pick("Normal wear", "Usure normale", "Normale Abnützung", "Usura normale", "Isanza normala")
    TenantResponse.ExistingAtEntry -> pick("Already at entry", "Déjà présent à l'entrée", "Bereits bei Einzug", "Già presente all'entrata", "Gia tar l'entrada")
  }
  val tenantComment = pick("Tenant's comment", "Commentaire du locataire", "Kommentar der Mieterschaft", "Commento del conduttore", "cuntegn dal locatari")
  val normalWearNote = pick(
    "Normal wear is not a tenant charge. Record the tenant's view even where the agency disagrees.",
    "L'usure normale n'est pas à la charge du locataire. Consignez son point de vue même si la régie n'est pas d'accord.",
    "Normale Abnützung ist keine Belastung der Mieterschaft. Halten Sie ihre Sicht auch bei Widerspruch der Verwaltung fest.",
    "L'usura normale non è a carico del conduttore. Registrate la sua posizione anche se l'amministrazione non concorda.",
    "L'isanza normala na vegn betg messa a quint al locatari. Registrai sia posiziun er sche l'administraziun na concorda betg.",
  )

  // Review.
  val remarks = pick("Remarks", "Remarques", "Bemerkungen", "Osservazioni", "Remartgas")
  val chargesBy = pick(
    "Service-charge statement by",
    "Décompte de charges d'ici au",
    "Nebenkostenabrechnung bis",
    "Conteggio spese accessorie entro il",
    "Quint da custs accessorics fin ils",
  )
  val oneSided = pick(
    "One-sided handover, other party absent",
    "Remise unilatérale, partie adverse absente",
    "Einseitige Abgabe, Gegenpartei abwesend",
    "Riconsegna unilaterale, controparte assente",
    "Surdada unilaterala, cuntrapartida absenta",
  )
  val infoOneSided = pick(
    "Nobody on the other side of the threshold: document everything with dated photos, have a neutral witness sign the record, and send the keys by registered mail. The proof of delivery completes the return.",
    "Personne de l'autre côté du seuil : documentez tout par photos datées, faites signer le constat par un témoin neutre et envoyez les clés en recommandé. La preuve de remise achève la restitution.",
    "Niemand auf der anderen Seite: alles mit datierten Fotos festhalten, eine neutrale Zeugin den Befund unterschreiben lassen und die Schlüssel eingeschrieben senden. Der Zustellnachweis vollendet die Rückgabe.",
    "Nessuno dall'altra parte: documentate tutto con foto datate, fate firmare il rilievo a un testimone neutrale e spedite le chiavi per raccomandata. La prova di consegna completa la riconsegna.",
    "Nagin da l'autra vart: documentai tut cun fotos datadas, laschai suttascriver il protocol d'ina perditga neutrala e trametti las clavs cun brev recumandada.",
  )


  // The case's cards and the flow's frame.
  val exitFromThis = pick("EXIT FROM THIS", "SORTIE DEPUIS CELUI-CI", "AUSZUG DARAUS", "RICONSEGNA DA QUESTO", "SORTIDA DA QUEST")
  val finalizeEntryFirst = pick(
    "FINALIZE ENTRY FIRST", "FINALISER L'ENTRÉE D'ABORD", "EINZUG ZUERST ABSCHLIESSEN", "FINALIZZARE PRIMA L'ENTRATA", "FINALISAR L'ENTRADA L'EMPRIM",
  )
  val sure = pick("SURE?", "SÛR ?", "SICHER?", "SICURO?", "SEGIR?")
  val noAddress = pick("Address not yet entered", "Adresse pas encore saisie", "Adresse noch nicht erfasst", "Indirizzo non ancora inserito", "Adressa anc betg endatada")
  val infoDeposit = pick(
    "Three months of rent locked on a bank account, or an insurance premium of 4 to 5 percent of the guarantee per year, never refunded. The account gives the money back one day; the insurance frees it now and costs for as long as the lease runs. Both are legitimate; only the release paperwork differs, and this protocol prints the right one.",
    "Trois mois de loyer bloqués sur un compte, ou une prime d'assurance de 4 à 5 pour cent de la garantie par an, jamais remboursée. Le compte rend l'argent un jour ; l'assurance le libère tout de suite et coûte aussi longtemps que dure le bail. Les deux sont légitimes ; seule la procédure de libération diffère, et ce protocole imprime la bonne.",
    "Drei Monatsmieten auf dem Sperrkonto, oder eine Versicherungsprämie von 4 bis 5 Prozent der Garantie pro Jahr, nie zurückerstattet. Das Konto gibt das Geld eines Tages zurück; die Versicherung macht es sofort frei und kostet, solange das Mietverhältnis läuft. Beides ist legitim; nur die Freigabe läuft anders, und dieses Protokoll druckt die richtige.",
    "Tre mesi di pigione bloccati su un conto, o un premio assicurativo dal 4 al 5 per cento della garanzia all'anno, mai rimborsato. Il conto restituisce i soldi un giorno; l'assicurazione li libera subito e costa finché dura la locazione. Entrambi sono legittimi; cambia solo la procedura di svincolo, e questo verbale stampa quella giusta.",
    "Trais tschains da locaziun bloccads sin in conto, u ina premia d'assicuranza da 4 fin 5 pertschient da la garanzia per onn, mai restituida. Il conto restituescha ils daners in di; l'assicuranza als libera immediat e custa uschè ditg che la locaziun dura.",
  )
  val photos = pick("Photos", "Photos", "Fotos", "Foto", "Fotos")

  // The two signature planes.
  val signRecordTitle = pick(
    "Signature of the record",
    "Signature du constat",
    "Unterschrift des Befunds",
    "Firma del rilievo",
    "Suttascripziun dal protocol",
  )
  val signRecordMeaning = pick(
    "This signature confirms one thing only: the defects are correctly recorded. It is not an acceptance of any costs. In the canton of Vaud this is the law of the RULV; everywhere else this protocol says it in writing.",
    "Cette signature confirme une seule chose : les défauts sont correctement consignés. Elle ne vaut acceptation d'aucun coût. Dans le canton de Vaud, c'est la règle des RULV ; partout ailleurs, ce protocole l'écrit noir sur blanc.",
    "Diese Unterschrift bestätigt nur eines: die Mängel sind richtig protokolliert. Sie ist keine Übernahme irgendwelcher Kosten. Im Kanton Waadt ist das RULV-Recht; überall sonst schreibt dieses Protokoll es ausdrücklich hin.",
    "Questa firma conferma una sola cosa: i difetti sono correttamente verbalizzati. Non vale accettazione di alcun costo. Nel canton Vaud è la regola delle RULV; ovunque altrove questo verbale lo scrive espressamente.",
    "Questa suttascripziun conferma mo ina chaussa: las mancanzas èn registradas correctamain. Ella na vala betg sco acceptaziun da custs.",
  )
  val signCostsTitle = pick(
    "Acceptance of costs",
    "Acceptation des coûts",
    "Kostenübernahme",
    "Accettazione dei costi",
    "Acceptaziun dals custs",
  )
  val signCostsWarning = pick(
    "This second signature is a debt acknowledgment for the charged defects. It is optional, it may be refused, and it should only be given when the duty to pay is beyond doubt. For larger amounts, sign subject to a cost estimate, or involve the liability insurer first.",
    "Cette seconde signature vaut reconnaissance de dette pour les défauts imputés. Elle est facultative, elle peut être refusée, et ne devrait être donnée que si l'obligation de payer est indubitable. Pour les montants importants, signez sous réserve d'un devis, ou passez d'abord par l'assurance RC.",
    "Diese zweite Unterschrift ist eine Schuldanerkennung für die angelasteten Mängel. Sie ist freiwillig, sie darf verweigert werden, und sie gehört nur dorthin, wo die Zahlungspflicht ausser Zweifel steht. Bei grösseren Beträgen mit Vorbehalt eines Kostenvoranschlags unterschreiben oder zuerst die Haftpflichtversicherung beiziehen.",
    "Questa seconda firma vale riconoscimento di debito per i difetti imputati. È facoltativa, può essere rifiutata, e va data solo quando l'obbligo di pagare è fuori dubbio. Per importi rilevanti, firmate con riserva di un preventivo o coinvolgete prima l'assicurazione RC.",
    "Questa segunda suttascripziun vala sco renconuschientscha da debit per las mancanzas messas a quint. Ella è facultativa e po vegnir refusada.",
  )
  val signKey = pick("SIGN", "SIGNER", "UNTERSCHREIBEN", "FIRMA", "SUTTASCRIVER")
  val refusedKey = pick("REFUSED", "REFUSÉE", "VERWEIGERT", "RIFIUTATA", "REFUSADA")
  val clearInk = pick("CLEAR", "EFFACER", "LÖSCHEN", "CANCELLA", "STIZZAR")
  val saveInk = pick("KEEP", "CONSERVER", "ÜBERNEHMEN", "CONSERVA", "TEGNAIR")
  val notSigned = pick("Not signed", "Non signé", "Nicht unterzeichnet", "Non firmato", "Betg suttascrit")
  val resignRequired = pick(
    "Sign again after the change", "Signer à nouveau après la modification", "Nach der Änderung erneut unterschreiben", "Firmare di nuovo dopo la modifica", "Suttascriver danovamain suenter la midada",
  )
  val finalizeRecord = pick("FINALIZE RECORD", "FINALISER LE CONSTAT", "PROTOKOLL ABSCHLIESSEN", "FINALIZZA VERBALE", "FINALISAR PROTOCOL")
  val finalizeLead = pick(
    "Finalizing freezes this version and its original evidence. Further changes create an amendment.",
    "La finalisation fige cette version et ses preuves originales. Toute modification crée un avenant.",
    "Der Abschluss friert diese Version und ihre Originalbelege ein. Änderungen erzeugen einen Nachtrag.",
    "La finalizzazione blocca questa versione e le prove originali. Ogni modifica crea un'integrazione.",
    "La finalisaziun fixescha questa versiun e sias novitads originalas. Midadas creeschan in supplement.",
  )
  val finalizing = pick("Finalizing…", "Finalisation…", "Wird abgeschlossen…", "Finalizzazione…", "Finalisaziun…")
  val finalizationNeeds = pick("Before finalizing", "Avant la finalisation", "Vor dem Abschluss", "Prima della finalizzazione", "Avant la finalisaziun")
  val finalizationFailed = pick(
    "The record could not be finalized. Keep the draft and try again.",
    "Le constat n'a pas pu être finalisé. Conservez le brouillon et réessayez.",
    "Das Protokoll konnte nicht abgeschlossen werden. Entwurf behalten und erneut versuchen.",
    "Il verbale non ha potuto essere finalizzato. Conservate la bozza e riprovate.",
    "Il protocol n'ha betg pudì vegnir finalisà. Tegnai il sboz e empruvai danovamain.",
  )
  fun finalizationIssue(issue: FinalizationIssue) = when (issue) {
    FinalizationIssue.Address -> pick("Add the street and number", "Indiquez la rue et le numéro", "Strasse und Nummer ergänzen", "Indicate via e numero", "Agiuntai via e numer")
    FinalizationIssue.Place -> pick("Add the postcode and place", "Indiquez le NPA et la localité", "PLZ und Ort ergänzen", "Indicate NAP e località", "Agiuntai NPA e lieu")
    FinalizationIssue.HandoverDate -> pick("Add the handover date", "Indiquez la date de remise", "Übergabedatum ergänzen", "Indicate la data di consegna", "Agiuntai la data da surdada")
    FinalizationIssue.HandoverTime -> pick("Add the handover time", "Indiquez l'heure de remise", "Übergabezeit ergänzen", "Indicate l'ora di consegna", "Agiuntai l'ura da surdada")
    FinalizationIssue.Parties -> pick("Name both tenant and landlord / agency", "Nommez le locataire et le bailleur / la régie", "Mieterschaft und Vermieterschaft / Verwaltung benennen", "Indicate conduttore e locatore / amministrazione", "Numnai locatari e locatur / administraziun")
    FinalizationIssue.DefectText -> pick("Describe every defect precisely", "Décrivez chaque défaut précisément", "Jeden Mangel präzise beschreiben", "Descrivete ogni difetto con precisione", "Descrivai mintga mancanza precis")
    FinalizationIssue.RecordOutcomes -> pick("Record each required signature or refusal", "Consignez chaque signature ou refus requis", "Jede erforderliche Unterschrift oder Verweigerung erfassen", "Registrate ogni firma o rifiuto richiesto", "Registrai mintga suttascripziun u refusa necessaria")
  }

  // Export.
  val exportPdf = pick("EXPORT PDF", "EXPORTER LE PDF", "PDF EXPORTIEREN", "ESPORTA PDF", "EXPORTAR PDF")
  val saveAsDraft = pick("SAVE AS DRAFT", "ENREGISTRER LE BROUILLON", "ALS ENTWURF SPEICHERN", "SALVA COME BOZZA", "MEMORISAR SCO SBOZ")
  val pdfLanguage = pick("Language of the PDF", "Langue du PDF", "Sprache des PDF", "Lingua del PDF", "Lingua dal PDF")
  val open = pick("OPEN", "OUVRIR", "ÖFFNEN", "APRI", "AVRIR")
  val share = pick("SHARE", "PARTAGER", "TEILEN", "CONDIVIDI", "CUNDIVIDER")
  val openFileFailed = pick(
    "No app could open this PDF.",
    "Aucune app ne peut ouvrir ce PDF.",
    "Keine App kann dieses PDF öffnen.",
    "Nessuna app può aprire questo PDF.",
    "Nagina app po avrir quest PDF.",
  )
  val shareFileFailed = pick(
    "Sharing is not available on this device.",
    "Le partage n'est pas disponible sur cet appareil.",
    "Teilen ist auf diesem Gerät nicht verfügbar.",
    "La condivisione non è disponibile su questo dispositivo.",
    "Parter na stat betg a disposiziun sin quest apparat.",
  )
  val exportedTo = pick("Saved to", "Enregistré dans", "Gespeichert unter", "Salvato in", "Memorisà en")
  val exportBlocked = pick("Finalize the record before exporting it.", "Finalisez le constat avant de l'exporter.", "Protokoll vor dem Export abschliessen.", "Finalizzate il verbale prima di esportarlo.", "Finalisai il protocol avant d'exportar el.")
  fun exportIssue(issue: ExportIssue) = when (issue) {
    ExportIssue.NotFinalized -> exportBlocked
    ExportIssue.IntegrityMismatch -> pick(
      "Evidence changed or is missing. This record cannot be exported.",
      "Une preuve a changé ou manque. Ce constat ne peut pas être exporté.",
      "Belege wurden verändert oder fehlen. Dieses Protokoll kann nicht exportiert werden.",
      "Una prova è cambiata o manca. Questo verbale non può essere esportato.",
      "Ina novitads è midada u manca. Quest protocol na po betg vegnir exportà.",
    )
    ExportIssue.Failed -> pick("The PDF could not be created. Try again.", "Le PDF n'a pas pu être créé. Réessayez.", "Das PDF konnte nicht erstellt werden. Erneut versuchen.", "Il PDF non ha potuto essere creato. Riprovate.", "Il PDF n'ha betg pudì vegnir creà. Empruvai danovamain.")
  }
  val copiesDelivered = pick("Identical copies delivered", "Exemplaires identiques remis", "Gleichlautende Kopien übergeben", "Copie identiche consegnate", "Copias identicas surdadas")
  fun copyDeliveredTo(partyRole: Role) = pick(
    "Copy handed to ${role(partyRole)}", "Exemplaire remis à ${role(partyRole)}", "Kopie an ${role(partyRole)} übergeben", "Copia consegnata a ${role(partyRole)}", "Copia surdada a ${role(partyRole)}",
  )
  val amendRecord = pick("CREATE AMENDMENT", "CRÉER UN AVENANT", "NACHTRAG ERSTELLEN", "CREA INTEGRAZIONE", "CREAR SUPPLEMENT")
  val amendLead = pick(
    "A correction starts a new draft linked to this final record; the original remains unchanged.",
    "Une correction crée un nouveau brouillon lié à ce constat final ; l'original reste inchangé.",
    "Eine Korrektur erstellt einen neuen, mit diesem Abschluss verknüpften Entwurf; das Original bleibt unverändert.",
    "Una correzione crea una nuova bozza collegata a questo verbale finale; l'originale resta invariato.",
    "Ina correctura creescha in nov sboz collià cun quest protocol final; l'original resta nunmidà.",
  )
  val amending = pick("Creating amendment…", "Création de l'avenant…", "Nachtrag wird erstellt…", "Creazione integrazione…", "Creaziun dal supplement…")
  val fingerprint = pick("Evidence fingerprint", "Empreinte des preuves", "Fingerabdruck der Belege", "Impronta delle prove", "Impronta da las novitads")
  val pdfFinalized = pick("Finalized record", "Constat finalisé", "Abgeschlossenes Protokoll", "Verbale finalizzato", "Protocol finalisà")
  val pdfAmends = pick("Amends record", "Modifie le constat", "Ändert Protokoll", "Modifica verbale", "Mida protocol")

  // The deposit's next step, printed at the end of the exit protocol.
  val releaseTitle = pick(
    "Release of the rent deposit",
    "Libération de la garantie de loyer",
    "Freigabe des Mietzinsdepots",
    "Svincolo del deposito di garanzia",
    "Liberaziun dal deposit da locaziun",
  )
  val releaseBank = pick(
    "The blocked account is released only with the signatures of BOTH parties on the bank's release form, or by a court. If the landlord asserts no claim legally within one year of the end of the tenancy, the bank pays the deposit back on the tenant's sole demand (Art. 257e CO).",
    "Le compte bloqué n'est libéré qu'avec les signatures des DEUX parties sur la formule de la banque, ou par un tribunal. Si le bailleur ne fait valoir aucune prétention en justice dans l'année suivant la fin du bail, la banque restitue la garantie à la seule demande du locataire (art. 257e CO).",
    "Das Sperrkonto wird nur mit den Unterschriften BEIDER Parteien auf dem Formular der Bank freigegeben, oder durch ein Gericht. Macht die Vermieterschaft innert eines Jahres nach Mietende keinen Anspruch rechtlich geltend, zahlt die Bank das Depot auf alleiniges Verlangen der Mieterschaft zurück (Art. 257e OR).",
    "Il conto bloccato si svincola solo con le firme di ENTRAMBE le parti sul modulo della banca, o per decisione del giudice. Se il locatore non fa valere alcuna pretesa in giudizio entro un anno dalla fine della locazione, la banca restituisce il deposito su sola richiesta del conduttore (art. 257e CO).",
    "Il conto bloccà vegn liberà mo cun las suttascripziuns da TUTTAS DUAS partidas sin il formular da la banca, u tras in tribunal. Sch'il locatur na fa betg valair ina pretensiun giuridicamain entaifer in onn, restituescha la banca il deposit sin dumonda dal locatari (art. 257e DO).",
  )
  val releaseInsurance = pick(
    "The guarantee insurance is released at the express request of the landlord or agency to the insurer. Open defects can block the release only if they are noted in this protocol or notified within the legal window. The premium already paid is not refunded.",
    "L'assurance de garantie est libérée sur demande expresse du bailleur ou de la régie auprès de l'assureur. Des défauts ouverts ne peuvent bloquer la libération que s'ils figurent dans ce protocole ou ont été signalés dans le délai légal. La prime déjà payée n'est pas remboursée.",
    "Die Garantieversicherung wird auf ausdrückliches Verlangen der Vermieterschaft oder Verwaltung beim Versicherer freigegeben. Offene Mängel können die Freigabe nur blockieren, wenn sie in diesem Protokoll stehen oder fristgerecht gerügt wurden. Die bezahlte Prämie wird nicht zurückerstattet.",
    "L'assicurazione di garanzia si svincola su richiesta espressa del locatore o dell'amministrazione all'assicuratore. Difetti aperti possono bloccare lo svincolo solo se figurano in questo verbale o sono stati notificati nei termini legali. Il premio già pagato non viene rimborsato.",
    "L'assicuranza da garanzia vegn liberada sin dumonda expressa dal locatur u da l'administraziun tar l'assicuradur. La premia gia pajada na vegn betg restituida.",
  )
  val refusedNote = pick(
    "Signature refused",
    "Signature refusée",
    "Unterschrift verweigert",
    "Firma rifiutata",
    "Suttascripziun refusada",
  )
  val pdfDefectAnnex = pick("Photo annex", "Annexe photos", "Fotoanhang", "Allegato foto", "Agiunta da fotos")
  val pdfPage = pick("Page", "Page", "Seite", "Pagina", "Pagina")

  // The Zeitwert face.
  val lifespanYears = pick("Lifespan, years", "Longévité, années", "Lebensdauer, Jahre", "Durata di vita, anni", "Durada da vita, onns")
  val ageYears = pick("Age since last renovation, years", "Âge depuis la dernière rénovation, années", "Alter seit letzter Renovation, Jahre", "Età dall'ultimo rinnovo, anni", "Vegliadetgna dapi l'ultima renovaziun, onns")
  val repairCost = pick("Repair cost CHF", "Coût de la réparation CHF", "Reparaturkosten CHF", "Costo della riparazione CHF", "Custs da reparatura CHF")
  val tenantShare = pick("Tenant's share", "Part du locataire", "Anteil Mieterschaft", "Quota del conduttore", "Part dal locatari")
  val remainingValue = pick("Remaining value", "Valeur résiduelle", "Restwert", "Valore residuo", "Valur restanta")
  val ironRules = pick(
    "Nothing is owed past the full lifespan, however real the damage. Normal wear is never payable. The age counts from the last renovation, not from the start of the lease.",
    "Rien n'est dû au-delà de la longévité, si réel que soit le dégât. L'usure normale ne se paie jamais. L'âge court depuis la dernière rénovation, pas depuis le début du bail.",
    "Nach Ablauf der Lebensdauer ist nichts geschuldet, so real der Schaden auch ist. Normale Abnützung ist nie zu bezahlen. Das Alter zählt ab der letzten Renovation, nicht ab Mietbeginn.",
    "Oltre la durata di vita non si deve nulla, per quanto reale sia il danno. L'usura normale non si paga mai. L'età conta dall'ultimo rinnovo, non dall'inizio della locazione.",
    "Suenter la durada da vita na vegn nagut debità. L'isanza normala na vegn mai pajada. La vegliadetgna quinta da l'ultima renovaziun.",
  )
  val tableDisclaimer = pick(
    "Publicly cited values from the joint MV/HEV lifespan table. The table itself is not binding; the full current edition is sold by HEV and MV.",
    "Valeurs publiques de la table paritaire MV/HEV. La table n'a pas de force contraignante ; l'édition complète est vendue par HEV et ASLOCA/MV.",
    "Öffentlich zitierte Werte der paritätischen Lebensdauertabelle MV/HEV. Die Tabelle ist nicht verbindlich; die vollständige aktuelle Ausgabe verkaufen HEV und MV.",
    "Valori pubblici della tabella paritetica MV/HEV. La tabella non è vincolante; l'edizione completa è venduta da HEV e ASI.",
    "Valurs publicas da la tabella paritetica MV/HEV. La tabella n'è betg liianta.",
  )
  val customLifespan = pick("Own value", "Valeur libre", "Eigener Wert", "Valore proprio", "Atgna valur")

  fun life(k: dev.verso.limen.sense.LifeKey) = when (k) {
    dev.verso.limen.sense.LifeKey.DispersionPaint -> pick("Dispersion paint", "Peinture dispersion", "Dispersionsfarbe", "Pittura a dispersione", "Colur a dispersiun")
    dev.verso.limen.sense.LifeKey.AcrylicPaint -> pick("Acrylic/oil paint", "Peinture acryl/huile", "Acryl-/Ölfarbe", "Pittura acrilica/olio", "Colur acrilica")
    dev.verso.limen.sense.LifeKey.WoodchipWallpaper -> pick("Woodchip wallpaper", "Papier ingrain", "Raufasertapete", "Tappezzeria ruvida", "Tapet grob")
    dev.verso.limen.sense.LifeKey.PaintableWallpaper -> pick("Paintable wallpaper", "Papier repeignable", "Überstreichbare Tapete", "Tappezzeria ritinteggiabile", "Tapet surpittirabel")
    dev.verso.limen.sense.LifeKey.Carpet -> pick("Carpet, medium", "Moquette moyenne", "Spannteppich mittel", "Moquette media", "Tapetsch mesaun")
    dev.verso.limen.sense.LifeKey.NeedleFelt -> pick("Needle felt", "Feutre aiguilleté", "Nadelfilz", "Feltro agugliato", "Feuter")
    dev.verso.limen.sense.LifeKey.LaminateMid -> pick("Laminate, medium", "Stratifié moyen", "Laminat mittel", "Laminato medio", "Laminat mesaun")
    dev.verso.limen.sense.LifeKey.SolidParquet -> pick("Solid parquet", "Parquet massif", "Massivparkett", "Parquet massiccio", "Parquet massiv")
    dev.verso.limen.sense.LifeKey.MultilayerParquet -> pick("Multilayer parquet", "Parquet multicouche", "Mehrschichtparkett", "Parquet multistrato", "Parquet a plirs stgalims")
    dev.verso.limen.sense.LifeKey.ParquetSeal -> pick("Parquet seal", "Vitrification du parquet", "Parkettversiegelung", "Sigillatura del parquet", "Sigillatura dal parquet")
    dev.verso.limen.sense.LifeKey.Pvc -> pick("PVC floor", "Sol PVC", "PVC-Belag", "Pavimento PVC", "Plaun PVC")
    dev.verso.limen.sense.LifeKey.Linoleum -> pick("Linoleum", "Linoléum", "Linoleum", "Linoleum", "Linoleum")
    dev.verso.limen.sense.LifeKey.CeramicTiles -> pick("Ceramic tiles", "Carrelage", "Keramikplatten", "Piastrelle", "Plattas da ceramica")
    dev.verso.limen.sense.LifeKey.Fridge -> pick("Fridge", "Réfrigérateur", "Kühlschrank", "Frigorifero", "Frigorifer")
    dev.verso.limen.sense.LifeKey.Freezer -> pick("Freezer", "Congélateur", "Tiefkühler", "Congelatore", "Congelatur")
    dev.verso.limen.sense.LifeKey.Stove -> pick("Stove/oven", "Cuisinière/four", "Herd/Backofen", "Cucina/forno", "Furnel/furn")
    dev.verso.limen.sense.LifeKey.Dishwasher -> pick("Dishwasher", "Lave-vaisselle", "Geschirrspüler", "Lavastoviglie", "Maschina da lavar plats")
    dev.verso.limen.sense.LifeKey.Microwave -> pick("Microwave", "Micro-ondes", "Mikrowelle", "Microonde", "Microondas")
    dev.verso.limen.sense.LifeKey.Steamer -> pick("Steamer", "Steamer", "Steamer", "Vaporiera", "Steamer")
    dev.verso.limen.sense.LifeKey.ExtractorHood -> pick("Extractor hood", "Hotte", "Dampfabzug", "Cappa", "Chapa d'aspiraziun")
    dev.verso.limen.sense.LifeKey.Taps -> pick("Taps/mixers", "Robinetterie", "Armaturen", "Rubinetteria", "Cranas")
    dev.verso.limen.sense.LifeKey.BathtubAcrylic -> pick("Bathtub, acrylic", "Baignoire acryl", "Badewanne Acryl", "Vasca acrilica", "Vaschia acrilica")
    dev.verso.limen.sense.LifeKey.BathtubSteel -> pick("Bathtub, steel", "Baignoire acier", "Badewanne Stahl", "Vasca in acciaio", "Vaschia d'atschal")
    dev.verso.limen.sense.LifeKey.CeramicSink -> pick("Ceramic sink/WC", "Lavabo/WC céramique", "Lavabo/WC Keramik", "Lavabo/WC ceramica", "Lavabo/WC da ceramica")
    dev.verso.limen.sense.LifeKey.MirrorCabinet -> pick("Mirror cabinet", "Armoire à glace", "Spiegelschrank", "Armadietto a specchio", "Stgaffa da spievel")
    dev.verso.limen.sense.LifeKey.WashingMachine -> pick("Washing machine/dryer", "Lave-linge/sèche-linge", "Waschmaschine/Tumbler", "Lavatrice/asciugatrice", "Maschina da lavar/sitgentadur")
    dev.verso.limen.sense.LifeKey.DoorSolid -> pick("Door, solid wood", "Porte bois massif", "Türe Massivholz", "Porta legno massiccio", "Porta da lain massiv")
    dev.verso.limen.sense.LifeKey.LockCylinder -> pick("Lock cylinder/keys", "Cylindre/clés", "Schliesszylinder/Schlüssel", "Cilindro/chiavi", "Cilinder/clavs")
    dev.verso.limen.sense.LifeKey.RollerShutterWood -> pick("Roller shutter, wood", "Volet roulant bois", "Rollladen Holz", "Avvolgibile legno", "Rullada da lain")
    dev.verso.limen.sense.LifeKey.RollerShutterMetal -> pick("Roller shutter, metal", "Volet roulant métal", "Rollladen Metall", "Avvolgibile metallo", "Rullada da metal")
    dev.verso.limen.sense.LifeKey.BlindStraps -> pick("Blind straps", "Sangles de store", "Storen-Gurten", "Cinghie", "Rissas")
    dev.verso.limen.sense.LifeKey.SealantJoints -> pick("Sealant joints", "Joints silicone", "Kittfugen", "Giunti in silicone", "Giuntas da silicon")
    dev.verso.limen.sense.LifeKey.Radiator -> pick("Radiator", "Radiateur", "Radiator", "Radiatore", "Radiatur")
  }

  // The deadlines face.
  val keysReturnedOn = pick("Keys returned on", "Clés rendues le", "Schlüssel zurück am", "Chiavi riconsegnate il", "Clavs restituidas ils")
  val safeBy = pick("Safe if posted by", "Sûr si posté d'ici au", "Sicher, wenn aufgegeben bis", "Sicuro se spedito entro il", "Segir sche tramess fin ils")
  val latestBy = pick("Case law's outer edge", "Limite de la jurisprudence", "Äusserste Grenze der Praxis", "Limite della giurisprudenza", "Ultim termin da la pratica")
  val clockExitTitle = pick(
    "Landlord's defect notice",
    "Avis des défauts du bailleur",
    "Mängelrüge der Vermieterschaft",
    "Notifica dei difetti del locatore",
    "Notificaziun da mancanzas dal locatur",
  )
  val clockExitBody = pick(
    "After getting ALL keys back, the landlord must notify the tenant of every charged defect in writing, itemized, within 2 to 3 working days (Art. 267a CO; BGer 4A_388/2013). Registered mail. Vague or late notice forfeits every claim; one week is already too late.",
    "Après réception de TOUTES les clés, le bailleur doit signaler par écrit au locataire chaque défaut imputé, poste par poste, dans les 2 à 3 jours ouvrables (art. 267a CO ; TF 4A_388/2013). En recommandé. Un avis vague ou tardif fait perdre toute prétention ; une semaine est déjà trop tard.",
    "Nach Erhalt ALLER Schlüssel muss die Vermieterschaft jeden angelasteten Mangel schriftlich und einzeln innert 2 bis 3 Werktagen rügen (Art. 267a OR; BGer 4A_388/2013). Eingeschrieben. Eine vage oder späte Rüge verwirkt jeden Anspruch; eine Woche ist bereits zu spät.",
    "Dopo aver ricevuto TUTTE le chiavi, il locatore deve notificare per iscritto al conduttore ogni difetto imputato, voce per voce, entro 2 o 3 giorni lavorativi (art. 267a CO; TF 4A_388/2013). Per raccomandata. Una notifica vaga o tardiva fa decadere ogni pretesa; una settimana è già troppo tardi.",
    "Suenter avair survegnì TUT las clavs sto il locatur notifitgar en scrit mintga mancanza entaifer 2 fin 3 dis da lavur (art. 267a DO). Cun brev recumandada. Ina notificaziun vaga u tardiva fa perder mintga pretensiun.",
  )
  val clockEntryTitle = pick(
    "Tenant's move-in list",
    "Liste d'entrée du locataire",
    "Mängelliste beim Einzug",
    "Lista dei difetti alla consegna",
    "Glista da mancanzas tar l'entrada",
  )
  val clockEntryBody = pick(
    "Defects found after moving in should go to the landlord in writing within about 10 days, registered. Without that list, the previous tenant's damage can land on your deposit at the exit.",
    "Les défauts découverts après l'emménagement se signalent par écrit dans les 10 jours environ, en recommandé. Sans cette liste, les dégâts du locataire précédent peuvent retomber sur votre garantie à la sortie.",
    "Nach dem Einzug entdeckte Mängel gehören innert rund 10 Tagen schriftlich und eingeschrieben zur Vermieterschaft. Ohne diese Liste können die Schäden der Vormieterschaft beim Auszug auf Ihrem Depot landen.",
    "I difetti scoperti dopo il trasloco vanno segnalati per iscritto entro circa 10 giorni, per raccomandata. Senza questa lista, i danni del conduttore precedente possono ricadere sul vostro deposito alla riconsegna.",
    "Mancanzas chattadas suenter l'entrada duain ir en scrit al locatur entaifer var 10 dis, cun brev recumandada.",
  )
  val clockHiddenTitle = pick("Hidden defects", "Défauts cachés", "Verdeckte Mängel", "Difetti nascosti", "Mancanzas zuppadas")
  val clockHiddenBody = pick(
    "A defect no customary inspection could see must be notified immediately upon discovery, or the claim dies with the delay.",
    "Un défaut invisible lors d'un contrôle usuel doit être signalé immédiatement à sa découverte, sinon la prétention s'éteint avec le retard.",
    "Ein bei üblicher Prüfung nicht erkennbarer Mangel ist sofort nach der Entdeckung zu rügen, sonst erlischt der Anspruch mit der Verspätung.",
    "Un difetto non riconoscibile con un controllo usuale va notificato immediatamente alla scoperta, altrimenti la pretesa si estingue con il ritardo.",
    "Ina mancanza betg visibla tar ina controlla usitada sto vegnir notifitgada immediat suenter la scuverta.",
  )
  val clockDepositTitle = pick("The deposit's year", "L'année de la garantie", "Das Jahr des Depots", "L'anno del deposito", "L'onn dal deposit")
  val clockDepositBody = pick(
    "If the landlord asserts no claim legally within one year of the end of the tenancy, the bank pays the blocked deposit back on the tenant's sole demand (Art. 257e CO).",
    "Si le bailleur ne fait valoir aucune prétention en justice dans l'année suivant la fin du bail, la banque restitue la garantie bloquée à la seule demande du locataire (art. 257e CO).",
    "Macht die Vermieterschaft innert eines Jahres nach Mietende keinen Anspruch rechtlich geltend, zahlt die Bank das gesperrte Depot auf alleiniges Verlangen der Mieterschaft zurück (Art. 257e OR).",
    "Se il locatore non fa valere alcuna pretesa in giudizio entro un anno dalla fine della locazione, la banca restituisce il deposito bloccato su sola richiesta del conduttore (art. 257e CO).",
    "Sch'il locatur na fa betg valair ina pretensiun entaifer in onn, restituescha la banca il deposit sin dumonda suletta dal locatari (art. 257e DO).",
  )
  val clockReturnTitle = pick("The return day", "Le jour de la restitution", "Der Rückgabetag", "Il giorno della riconsegna", "Il di da la restituziun")
  val clockReturnBody = pick(
    "The flat is due back on the lease's last day within business hours; a weekend or holiday shifts it to the next working day. Local custom in Zurich and Bern: noon of the following day. The état des lieux is best made in empty rooms, in daylight, and either party may bring a person versed in tenancy law.",
    "Le logement se rend le dernier jour du bail pendant les heures ouvrables ; un week-end ou un jour férié reporte au jour ouvrable suivant. Usage local à Zurich et Berne : midi le lendemain. L'état des lieux se fait de préférence locaux vides, de jour, et chaque partie peut se faire accompagner d'une personne au fait du droit du bail.",
    "Die Wohnung ist am letzten Tag der Miete zu Geschäftszeiten zurückzugeben; Wochenende oder Feiertag verschieben auf den nächsten Werktag. Ortsgebrauch in Zürich und Bern: 12 Uhr am Folgetag. Das Protokoll entsteht am besten in leeren Räumen, bei Tageslicht, und jede Partei darf eine mietrechtskundige Person beiziehen.",
    "L'alloggio si riconsegna l'ultimo giorno della locazione in orario d'ufficio; un fine settimana o un giorno festivo spostano al giorno lavorativo successivo. Uso locale a Zurigo e Berna: mezzogiorno del giorno dopo. Il verbale si fa preferibilmente in locali vuoti, di giorno, e ogni parte può farsi accompagnare da una persona esperta di diritto di locazione.",
    "L'abitaziun vegn restituida l'ultim di da la locaziun durant las uras da fatschenta; ina fin d'emna u in di da festa spostan al proxim di da lavur.",
  )
  // Structured tooltips: a lead line, then bullets. The screens never print small text;
  // everything below lives behind an info mark.
  fun tipRecord() = Tip(
    pick(
      "This signature confirms one thing only: the defects are correctly recorded.",
      "Cette signature confirme une seule chose : les défauts sont correctement consignés.",
      "Diese Unterschrift bestätigt nur eines: die Mängel sind richtig protokolliert.",
      "Questa firma conferma una sola cosa: i difetti sono correttamente verbalizzati.",
      "Questa suttascripziun conferma mo ina chaussa: las mancanzas èn registradas correctamain.",
    ),
    listOf(
      pick(
        "It is not an acceptance of any costs",
        "Elle ne vaut acceptation d'aucun coût",
        "Sie ist keine Übernahme irgendwelcher Kosten",
        "Non vale accettazione di alcun costo",
        "Ella na vala betg sco acceptaziun da custs",
      ),
      pick(
        "In Vaud this is RULV law; everywhere else the PDF says it in writing",
        "Dans le canton de Vaud, c'est la règle des RULV ; ailleurs le PDF l'écrit noir sur blanc",
        "Im Kanton Waadt ist das RULV-Recht; überall sonst schreibt es das PDF ausdrücklich hin",
        "Nel canton Vaud è la regola delle RULV; altrove il PDF lo scrive espressamente",
        "En il chantun Vad è quai il dretg da las RULV; dapertut auter al scriva il PDF",
      ),
      pick(
        "It may be refused; a refusal is simply recorded",
        "Elle peut être refusée ; le refus est simplement consigné",
        "Sie darf verweigert werden; die Verweigerung wird schlicht protokolliert",
        "Può essere rifiutata; il rifiuto viene semplicemente verbalizzato",
        "Ella po vegnir refusada; la refusa vegn simplamain registrada",
      ),
    ),
  )

  fun tipCosts() = Tip(
    pick(
      "The second signature is a debt acknowledgment for the charged defects.",
      "La seconde signature vaut reconnaissance de dette pour les défauts imputés.",
      "Die zweite Unterschrift ist eine Schuldanerkennung für die angelasteten Mängel.",
      "La seconda firma vale riconoscimento di debito per i difetti imputati.",
      "La segunda suttascripziun vala sco renconuschientscha da debit per las mancanzas messas a quint.",
    ),
    listOf(
      pick(
        "Optional, and it may be refused",
        "Facultative, et elle peut être refusée",
        "Freiwillig, und sie darf verweigert werden",
        "Facoltativa, e può essere rifiutata",
        "Facultativa, ed ella po vegnir refusada",
      ),
      pick(
        "Only where the duty to pay is beyond doubt",
        "Seulement si l'obligation de payer est indubitable",
        "Nur wo die Zahlungspflicht ausser Zweifel steht",
        "Solo quando l'obbligo di pagare è fuori dubbio",
        "Mo nua che l'obligaziun da pajar è senza dubi",
      ),
      pick(
        "Larger amounts: sign subject to a cost estimate",
        "Montants importants : signer sous réserve d'un devis",
        "Grössere Beträge: mit Vorbehalt eines Kostenvoranschlags",
        "Importi rilevanti: firmare con riserva di un preventivo",
        "Imports pli gronds: suttascriver cun resalva d'in preventiv",
      ),
      pick(
        "Or involve the liability insurer first",
        "Ou passer d'abord par l'assurance RC",
        "Oder zuerst die Haftpflichtversicherung beiziehen",
        "O coinvolgere prima l'assicurazione RC",
        "U includer l'emprim l'assicuranza da responsabladad",
      ),
    ),
  )

  fun tipDeposit() = Tip(
    pick(
      "Two legitimate roads for the deposit:",
      "Deux voies légitimes pour la garantie :",
      "Zwei legitime Wege für das Depot:",
      "Due vie legittime per il deposito:",
      "Duas vias legitimas per il deposit:",
    ),
    listOf(
      pick(
        "Blocked account: the money comes back one day; release needs both signatures",
        "Compte bloqué : l'argent revient un jour ; libération à deux signatures",
        "Sperrkonto: das Geld kommt eines Tages zurück; Freigabe mit beiden Unterschriften",
        "Conto bloccato: i soldi tornano un giorno; svincolo con entrambe le firme",
        "Conto bloccà: ils daners returnan in di; liberaziun cun tuttas duas suttascripziuns",
      ),
      pick(
        "Insurance (SwissCaution and others): 4 to 5 percent per year, never refunded",
        "Assurance (SwissCaution et autres) : 4 à 5 pour cent par an, jamais remboursés",
        "Versicherung (SwissCaution und andere): 4 bis 5 Prozent pro Jahr, nie zurückerstattet",
        "Assicurazione (SwissCaution e altri): dal 4 al 5 per cento all'anno, mai rimborsato",
        "Assicuranza (SwissCaution ed auters): 4 fin 5 pertschient per onn, mai restituì",
      ),
      pick(
        "The exit protocol ends with the release step that matches",
        "L'état des lieux de sortie se termine par l'étape de libération correspondante",
        "Das Rückgabeprotokoll endet mit dem passenden Freigabeschritt",
        "Il verbale di riconsegna termina con il passo di svincolo corrispondente",
        "Il protocol da sortida finescha cun il pass da liberaziun correspundent",
      ),
    ),
  )

  fun tipCharged() = Tip(
    pick(
      "An exit protocol is a valid defect notice only for the defects it explicitly charges to the tenant.",
      "L'état des lieux de sortie ne vaut avis des défauts que pour les défauts explicitement imputés au locataire.",
      "Das Rückgabeprotokoll gilt nur für die dem Mieter ausdrücklich angelasteten Mängel als Mängelrüge.",
      "Il verbale di riconsegna vale come notifica dei difetti solo per i difetti esplicitamente imputati al conduttore.",
      "Il protocol da sortida vala sco notificaziun mo per las mancanzas messas explicitamain a quint al locatari.",
    ),
    listOf(
      pick(
        "Unmarked findings document the state of the flat, without a claim",
        "Les constats non marqués documentent l'état, sans prétention",
        "Unmarkierte Befunde dokumentieren den Zustand, ohne Forderung",
        "I rilievi non marcati documentano lo stato, senza pretese",
        "Constataziuns betg marcadas documenteschan il stadi, senza pretensiun",
      ),
    ),
  )

  fun tipOneSided() = Tip(
    pick(
      "Nobody on the other side of the threshold:",
      "Personne de l'autre côté du seuil :",
      "Niemand auf der anderen Seite der Schwelle:",
      "Nessuno dall'altra parte della soglia:",
      "Nagin da l'autra vart:",
    ),
    listOf(
      pick("Photograph everything, dated", "Tout photographier, avec date", "Alles fotografieren, datiert", "Fotografare tutto, con data", "Fotografar tut, cun data"),
      pick("A neutral witness signs the record", "Un témoin neutre signe le constat", "Eine neutrale Zeugin unterschreibt den Befund", "Un testimone neutrale firma il rilievo", "Ina perditga neutrala suttascriva il protocol"),
      pick("The keys go back by registered mail", "Les clés partent en recommandé", "Die Schlüssel gehen eingeschrieben zurück", "Le chiavi tornano per raccomandata", "Las clavs van enavos cun brev recumandada"),
      pick("The delivery proof completes the return", "La preuve de remise achève la restitution", "Der Zustellnachweis vollendet die Rückgabe", "La prova di consegna completa la riconsegna", "La cumprova da consegna cumplettescha la restituziun"),
    ),
  )

  fun tipZeitwert() = Tip(
    pick(
      "The arithmetic of the joint MV and HEV lifespan table.",
      "L'arithmétique de la table paritaire ASLOCA/MV et HEV.",
      "Die Arithmetik der paritätischen Lebensdauertabelle von MV und HEV.",
      "L'aritmetica della tabella paritetica ASI e HEV.",
      "L'aritmetica da la tabella paritetica da MV ed HEV.",
    ),
    listOf(
      pick("Past the full lifespan nothing is owed", "Au-delà de la longévité, rien n'est dû", "Nach Ablauf der Lebensdauer ist nichts geschuldet", "Oltre la durata di vita non si deve nulla", "Suenter la durada da vita na vegn nagut debità"),
      pick("Normal wear is never payable", "L'usure normale ne se paie jamais", "Normale Abnützung ist nie zu bezahlen", "L'usura normale non si paga mai", "L'isanza normala na vegn mai pajada"),
      pick("Age counts from the last renovation, not the lease", "L'âge court depuis la dernière rénovation, pas depuis le bail", "Das Alter zählt ab der letzten Renovation, nicht ab Mietbeginn", "L'età conta dall'ultimo rinnovo, non dalla locazione", "La vegliadetgna quinta da l'ultima renovaziun"),
      pick("Values here are public citations; the full table is sold by HEV and MV", "Valeurs publiques ; la table complète est vendue par HEV et ASLOCA", "Öffentliche Werte; die vollständige Tabelle verkaufen HEV und MV", "Valori pubblici; la tabella completa è venduta da HEV e ASI", "Valurs publicas; la tabella cumpletta vendan HEV e MV"),
      pick("Not legal advice; conciliation is free everywhere", "Pas un conseil juridique ; la conciliation est gratuite partout", "Keine Rechtsberatung; die Schlichtung ist überall kostenlos", "Non è consulenza legale; la conciliazione è gratuita ovunque", "Nagin cussegl giuridic; la mediaziun è gratuita dapertut"),
    ),
  )

  fun tipClocks() = Tip(
    pick(
      "Practice values with their sources, not legal advice.",
      "Des valeurs de pratique avec leurs sources, pas un conseil juridique.",
      "Praxiswerte mit ihren Quellen, keine Rechtsberatung.",
      "Valori di pratica con le loro fonti, non consulenza legale.",
      "Valurs da pratica cun lur funtaunas, nagin cussegl giuridic.",
    ),
    listOf(
      pick("Exit notice: Art. 267a CO and BGer 4A_388/2013", "Avis des défauts : art. 267a CO et TF 4A_388/2013", "Mängelrüge: Art. 267a OR und BGer 4A_388/2013", "Notifica dei difetti: art. 267a CO e TF 4A_388/2013", "Notificaziun: art. 267a DO e TF 4A_388/2013"),
      pick("Deposit: Art. 257e CO", "Garantie : art. 257e CO", "Depot: Art. 257e OR", "Deposito: art. 257e CO", "Deposit: art. 257e DO"),
      pick("The conciliation authority is free in all of Switzerland", "L'autorité de conciliation est gratuite dans toute la Suisse", "Die Schlichtungsbehörde ist in der ganzen Schweiz kostenlos", "L'autorità di conciliazione è gratuita in tutta la Svizzera", "L'autoritad da mediaziun è gratuita en tut la Svizra"),
    ),
  )


  fun tipCounterSign() = Tip(
    pick(
      "Both phones hold the same document; each signs its digest.",
      "Les deux téléphones tiennent le même document ; chacun en signe l'empreinte.",
      "Beide Telefone halten dasselbe Dokument; jedes signiert dessen Hash.",
      "Entrambi i telefoni tengono lo stesso documento; ognuno ne firma l'impronta.",
      "Tuts dus telefons tegnan il medem document; mintgin suttascriva sia impronta.",
    ),
    listOf(
      pick(
        "First receive the exported PDF from the other phone",
        "Recevez d'abord le PDF exporté de l'autre téléphone",
        "Zuerst das exportierte PDF vom anderen Telefon empfangen",
        "Ricevete prima il PDF esportato dall'altro telefono",
        "L'emprim retschaiver il PDF exportà da l'auter telefon",
      ),
      pick(
        "Read it, then pick it here: the fingerprint binds your signature to that exact file",
        "Lisez-le, puis choisissez-le ici : l'empreinte lie votre signature à ce fichier précis",
        "Lesen, dann hier wählen: der Fingerabdruck bindet Ihre Signatur an genau diese Datei",
        "Leggetelo, poi sceglietelo qui: l'impronta lega la vostra firma a quel preciso file",
        "Legiai el, lura tscherni el qua: l'impronta liia Vossa suttascripziun a quella datoteca precisa",
      ),
      pick(
        "Send the small signature file back; the other phone attaches and verifies it",
        "Renvoyez le petit fichier de signature ; l'autre téléphone le joint et le vérifie",
        "Die kleine Signaturdatei zurücksenden; das andere Telefon hängt sie an und prüft sie",
        "Rimandate il piccolo file di firma; l'altro telefono lo allega e lo verifica",
        "Trametti enavos la pitschna datoteca da suttascripziun; l'auter telefon l'agiunta e la verifitgescha",
      ),
      pick(
        "No account, no network: the files travel by direct share",
        "Sans compte, sans réseau : les fichiers voyagent par partage direct",
        "Ohne Konto, ohne Netz: die Dateien reisen per Direktfreigabe",
        "Senza conto, senza rete: i file viaggiano per condivisione diretta",
        "Senza conto, senza rait: las datotecas viagian cun cundivisiun directa",
      ),
    ),
  )

  fun tipShareNow() = Tip(
    pick(
      "Send the PDF to both parties now, at the door.",
      "Envoyez le PDF aux deux parties maintenant, sur le pas de porte.",
      "Senden Sie das PDF beiden Parteien jetzt, an der Türe.",
      "Inviate il PDF a entrambe le parti adesso, sulla soglia.",
      "Trametti il PDF ad omaduas partidas ussa, sin la sava.",
    ),
    listOf(
      pick(
        "Immediate delivery to both is what a court looks at first",
        "La remise immédiate aux deux est ce qu'un tribunal regarde en premier",
        "Die sofortige Zustellung an beide ist, was ein Gericht zuerst prüft",
        "La consegna immediata a entrambi è ciò che un tribunale guarda per primo",
        "La consegna immediata ad omadus è quai ch'in tribunal guarda l'emprim",
      ),
      pick(
        "Exit defects still need the registered letter within 2 to 3 working days",
        "Les défauts de sortie exigent toujours le recommandé sous 2 à 3 jours ouvrables",
        "Auszugsmängel brauchen weiterhin den eingeschriebenen Brief innert 2 bis 3 Werktagen",
        "I difetti di riconsegna richiedono sempre la raccomandata entro 2 o 3 giorni lavorativi",
        "Las mancanzas da sortida dovran vinavant la brev recumandada entaifer 2 fin 3 dis da lavur",
      ),
      pick(
        "A drawn signature is an acknowledgement in this record, not a qualified electronic signature",
        "Une signature dessinée vaut reconnaissance dans ce constat, pas signature électronique qualifiée",
        "Eine gezeichnete Unterschrift ist eine Bestätigung in diesem Protokoll, keine qualifizierte elektronische Signatur",
        "Una firma disegnata è una conferma in questo verbale, non una firma elettronica qualificata",
        "Ina suttascripziun dissegnada è ina conferma en quest protocol, betg ina suttascripziun electronica qualifitgada",
      ),
    ),
  )

  // ── The deposit comparison ────────────────────────────────────────────────────────────
  val compareKey = pick(
    "COMPARE DEPOSIT OPTIONS",
    "COMPARER LES GARANTIES",
    "KAUTIONEN VERGLEICHEN",
    "CONFRONTA LE GARANZIE",
    "CUMPAREGLIAR GARANZIAS",
  )
  val linkKey = pick("LINK", "LIEN", "LINK", "LINK", "LINK")
  val openLinkFailed = pick(
    "No app could open this website.",
    "Aucune app ne peut ouvrir ce site.",
    "Keine App kann diese Website öffnen.",
    "Nessuna app può aprire questo sito.",
    "Nagina app po avrir questa pagina web.",
  )
  val perYear = pick("per year", "par an", "pro Jahr", "all'anno", "per onn")

  fun tipCompare() = Tip(
    pick(
      "Two legitimate roads; the figures decide, not the brochure.",
      "Deux voies légitimes ; ce sont les chiffres qui décident, pas la brochure.",
      "Zwei legitime Wege; die Zahlen entscheiden, nicht der Prospekt.",
      "Due vie legittime; decidono le cifre, non l'opuscolo.",
      "Duas vias legitimas; las cifras decidan, betg il prospect.",
    ),
    listOf(
      pick(
        "Account: the money stays yours and comes back; it is just locked",
        "Compte : l'argent reste le vôtre et revient ; il est seulement bloqué",
        "Konto: das Geld bleibt Ihres und kommt zurück; es ist nur gesperrt",
        "Conto: i soldi restano vostri e tornano; sono solo bloccati",
        "Conto: ils daners restan Voss e returnan; els èn mo bloccads",
      ),
      pick(
        "Insurance: the capital stays free, the premium is never refunded",
        "Assurance : le capital reste libre, la prime n'est jamais remboursée",
        "Versicherung: das Kapital bleibt frei, die Prämie wird nie zurückerstattet",
        "Assicurazione: il capitale resta libero, il premio non è mai rimborsato",
        "Assicuranza: il chapital resta liber, la premia na vegn mai restituida",
      ),
      pick(
        "Insurance is no waiver: damages stay owed, the insurer pays and recovers from you",
        "L'assurance n'exonère pas : les dégâts restent dus, l'assureur paie puis se retourne contre vous",
        "Die Versicherung befreit nicht: Schäden bleiben geschuldet, der Versicherer zahlt und holt es bei Ihnen",
        "L'assicurazione non esonera: i danni restano dovuti, l'assicuratore paga e si rivale su di voi",
        "L'assicuranza na libera betg: donns restan debitads, l'assicuradur paja e sa regressa tar Vus",
      ),
      pick(
        "Yearly premiums as published, July 2026; the application happens on the provider's site",
        "Primes annuelles publiées, juillet 2026 ; la souscription se fait sur le site du prestataire",
        "Jahresprämien wie publiziert, Juli 2026; der Abschluss geschieht auf der Website des Anbieters",
        "Premi annui come pubblicati, luglio 2026; la sottoscrizione avviene sul sito del fornitore",
        "Premias annualas sco publitgadas, fanadur 2026; la conclusiun ha lieu sin la pagina dal purschider",
      ),
    ),
  )

  // ── Role: which side of the threshold holds this phone ────────────────────────────────
  val roleQuestion = pick(
    "Who holds this phone?",
    "Qui tient ce téléphone ?",
    "Wer hält dieses Telefon?",
    "Chi tiene questo telefono?",
    "Tgi tegna quest telefon?",
  )
  val profileName = pick("Your name", "Votre nom", "Ihr Name", "Il vostro nome", "Voss num")

  fun tipRole() = Tip(
    pick(
      "The instrument adapts to your side of the threshold.",
      "L'instrument s'adapte à votre côté du seuil.",
      "Das Instrument passt sich Ihrer Seite der Schwelle an.",
      "Lo strumento si adatta al vostro lato della soglia.",
      "L'instrument s'adattescha a Vossa vart da la sava.",
    ),
    listOf(
      pick(
        "Your deadlines come first on the clocks face",
        "Vos délais passent en premier sur les échéances",
        "Ihre Fristen stehen zuerst auf dem Fristenblatt",
        "I vostri termini vengono per primi sui termini",
        "Voss termins vegnan l'emprim sin ils termins",
      ),
      pick(
        "The one-phone handover walk stays the same for both",
        "L'état des lieux à un seul téléphone reste identique pour les deux",
        "Die Übergabe auf einem Telefon bleibt für beide gleich",
        "La riconsegna su un solo telefono resta uguale per entrambi",
        "La surdada sin in sulet telefon resta la medema per omadus",
      ),
      pick(
        "Change side any time on the link face",
        "Changez de côté à tout moment sur la face liaison",
        "Die Seite lässt sich jederzeit auf der Verbindungsseite wechseln",
        "Cambiate lato in ogni momento sulla faccia collegamento",
        "Midai la vart da tut temp sin la fatschada da colliaziun",
      ),
    ),
  )

  // ── Profile, welcome and guidance ─────────────────────────────────────────────────────
  val profileKey = pick("Profile", "Profil", "Profil", "Profilo", "Profil")
  val resetProfile = pick(
    "RESET PROFILE",
    "RÉINITIALISER LE PROFIL",
    "PROFIL ZURÜCKSETZEN",
    "AZZERA PROFILO",
    "REINIZIALISAR IL PROFIL",
  )
  val doneKey = pick("DONE", "TERMINÉ", "FERTIG", "FATTO", "FATG")
  val toolsLabel = pick("Tools", "Outils", "Werkzeuge", "Strumenti", "Utensils")
  val protocolsLabel = pick(
    "Your protocols",
    "Vos états des lieux",
    "Ihre Protokolle",
    "I vostri verbali",
    "Voss protocols",
  )
  val startNewSession = pick(
    "START NEW SESSION",
    "COMMENCER UNE NOUVELLE SESSION",
    "NEUE SITZUNG STARTEN",
    "AVVIA NUOVA SESSIONE",
    "NOVA SESIUN CUMENZAR",
  )
  val allSessions = pick(
    "ALL SESSIONS",
    "TOUTES LES SESSIONS",
    "ALLE SITZUNGEN",
    "TUTTE LE SESSIONI",
    "TUTTAS LAS SESIUNS",
  )
  val sessionIdentity = pick("Your identity", "Votre identité", "Ihre Identität", "La vostra identità", "Vossa identitad")
  val firstName = pick("First name", "Prénom", "Vorname", "Nome", "Prenum")
  val lastName = pick("Last name", "Nom", "Nachname", "Cognome", "Num")
  val identityAddress = pick("Address", "Adresse", "Adresse", "Indirizzo", "Adressa")
  val addressType = pick("Address type", "Type d'adresse", "Adresstyp", "Tipo di indirizzo", "Tip d'adressa")
  val swissAddress = pick("Swiss address", "Adresse suisse", "Schweizer Adresse", "Indirizzo svizzero", "Adressa svizra")
  val foreignAddress = pick("Foreign address", "Adresse étrangère", "Ausländische Adresse", "Indirizzo estero", "Adressa estra")
  val addressCountry = pick("Address country", "Pays de l'adresse", "Adressland", "Paese dell'indirizzo", "Pajais da l'adressa")
  val selectCountry = pick("Select country", "Sélectionnez un pays", "Land auswählen", "Selezionate un paese", "Tscherner pajais")
  val foreignAddressHint = pick(
    "Enter the address as printed. Swiss official lookup is not used for foreign addresses.",
    "Saisissez l'adresse telle qu'elle est écrite. La recherche officielle suisse ne s'applique pas aux adresses étrangères.",
    "Geben Sie die Adresse wie geschrieben ein. Die offizielle Schweizer Suche gilt nicht für ausländische Adressen.",
    "Inserite l'indirizzo come riportato. La ricerca ufficiale svizzera non viene usata per gli indirizzi esteri.",
    "Endatai l'adressa sco stampada. La tschertga uffiziala svizra na vegn betg duvrada per adressas estras.",
  )
  val street = pick("Street", "Rue", "Strasse", "Via", "Via")
  val flatNumber = pick("House / flat no.", "N° de maison / logement", "Haus- / Wohnungsnr.", "N. civico / appartamento", "Nr. da chasa / abitaziun")
  val postalCode = pick("Postcode", "NPA", "PLZ", "NPA", "NPA")
  val city = pick("City", "Localité", "Ort", "Località", "Lieu")
  val phoneCountry = pick("Phone country / code", "Pays / indicatif téléphonique", "Telefonland / Vorwahl", "Paese / prefisso telefonico", "Pajais / code da telefon")
  val countryNoMatches = pick("No matching country", "Aucun pays correspondant", "Kein passendes Land", "Nessun paese corrispondente", "Nagìn pajais adattà")
  val phoneNumber = pick("Phone number", "Numéro de téléphone", "Telefonnummer", "Numero di telefono", "Numer da telefon")
  val addressLookupHint = pick(
    "Type an address, then choose its official suggestion.",
    "Saisissez une adresse, puis choisissez la suggestion officielle.",
    "Adresse eingeben und den offiziellen Vorschlag wählen.",
    "Inserite un indirizzo, poi scegliete il suggerimento ufficiale.",
    "Endatai ina adressa e tschernai il recumandament uffizial.",
  )
  val addressSearching = pick("Searching official addresses...", "Recherche d'adresses officielles...", "Offizielle Adressen werden gesucht...", "Ricerca di indirizzi ufficiali...", "Tschertgar adressas uffizialas...")
  val addressVerified = pick("Official address selected", "Adresse officielle sélectionnée", "Offizielle Adresse ausgewählt", "Indirizzo ufficiale selezionato", "Adressa uffiziala tschernida")
  val validPhoneNumber = pick("Valid number", "Numéro valide", "Gültige Nummer", "Numero valido", "Numer valid")
  val invalidPhoneNumber = pick("Check the number for the selected country", "Vérifiez le numéro pour le pays sélectionné", "Prüfen Sie die Nummer für das ausgewählte Land", "Controllate il numero per il paese selezionato", "Controllai il numer per il pajais tschernì")
  fun phoneExample(example: String) = pick(
    "Example: $example",
    "Exemple : $example",
    "Beispiel: $example",
    "Esempio: $example",
    "Exempel: $example",
  )
  val userType = pick("Your role", "Votre rôle", "Ihre Rolle", "Il vostro ruolo", "Vossa rolla")
  val sessionKind = pick("Type of handover", "Type de remise", "Art der Übergabe", "Tipo di consegna", "Tip da surdada")
  val sessionsEmpty = pick(
    "No sessions saved on this phone.",
    "Aucune session enregistrée sur ce téléphone.",
    "Keine Sitzungen auf diesem Telefon gespeichert.",
    "Nessuna sessione salvata su questo telefono.",
    "Nagina sesiun memorisada sin quest telefon.",
  )
  val welcomeLead = pick(
    "Limen records the apartment handover, from the first meter reading to a final PDF both sides receive.",
    "Limen consigne l'état des lieux, du premier relevé au PDF final remis aux deux parties.",
    "Limen hält die Wohnungsübergabe fest, vom ersten Zählerstand bis zum finalen PDF für beide Seiten.",
    "Limen registra la consegna dell'alloggio, dalla prima lettura al PDF finale ricevuto da entrambe le parti.",
    "Limen documentescha la surdada da l'abitaziun, da l'emprima letga fin al PDF final per omaduas partidas.",
  )
  val howItWorks = pick(
    "How it works",
    "Comment ça marche",
    "So funktioniert es",
    "Come funziona",
    "Co quai funcziuna",
  )
  val guideSteps = listOf(
    pick(
      "Prepare the protocol together",
      "Préparez le protocole ensemble",
      "Bereitet das Protokoll gemeinsam vor",
      "Preparate il verbale insieme",
      "Preparai il protocol ensemen",
    ),
    pick(
      "Walk the rooms and note each item",
      "Parcourez les pièces et notez chaque point",
      "Geht die Räume durch und haltet jeden Punkt fest",
      "Percorrete i locali e annotate ogni punto",
      "Giai tras ils locals e notai mintga punct",
    ),
    pick(
      "Sign on this phone, both of you",
      "Signez sur ce téléphone, tous les deux",
      "Unterschreibt beide auf diesem Telefon",
      "Firmate entrambi su questo telefono",
      "Suttascrivai tuts dus sin quest telefon",
    ),
    pick(
      "Finalize the record, then export and share the PDF",
      "Finalisez le constat, puis exportez et partagez le PDF",
      "Protokoll abschliessen, dann PDF exportieren und teilen",
      "Finalizzate il verbale, poi esportate e condividete il PDF",
      "Finalisai il protocol, lura exportai e tramettai il PDF",
    ),
  )
  fun stepLead(step: FlowStep) = when (step) {
    FlowStep.Hub -> pick(
      "Two halves, in whatever order suits you: what you can see and touch, and what is on paper.",
      "Deux moitiés, dans l'ordre qui vous convient : ce qui se voit et se touche, et ce qui est sur papier.",
      "Zwei Hälften, in beliebiger Reihenfolge: was sichtbar und greifbar ist, und was auf dem Papier steht.",
      "Due metà, nell'ordine che preferite: ciò che si vede e si tocca, e ciò che è sulla carta.",
      "Duas mitads, en l'urden che vus vulais: quai ch'ins vesa e tutga, e quai ch'è sin palpiri.",
    )
    FlowStep.Inspection -> pick(
      "Go through the flat in your own order. A room you never enter is a room with nothing wrong in it.",
      "Parcourez le logement dans votre ordre. Une pièce où vous n'entrez pas est une pièce sans défaut.",
      "Geht in eurer eigenen Reihenfolge durch. Ein Raum, den ihr nicht betretet, ist ein Raum ohne Mangel.",
      "Percorrete l'alloggio nel vostro ordine. Un locale in cui non entrate è un locale senza difetti.",
      "Giai tras l'abitaziun en voss agen urden. In local che vus n'entrais betg è in local senza mancanzas.",
    )
    FlowStep.Objects -> pick(
      "One row per thing handed over, with a photograph of it. A photo now settles the argument later.",
      "Une ligne par objet remis, avec sa photo. Une photo aujourd'hui tranche la discussion plus tard.",
      "Eine Zeile je übergebenem Gegenstand, mit Foto. Ein Foto heute beendet die Diskussion später.",
      "Una riga per ogni oggetto consegnato, con la sua foto. Una foto oggi chiude la discussione domani.",
      "Ina lingia per mintga object surdà, cun sia foto. Ina foto oz finescha la discussiun pli tard.",
    )
    FlowStep.Dossier -> pick(
      "The paperwork around the handover. Add a component only when this handover has it.",
      "Les documents autour de la remise. N'ajoutez une rubrique que si elle concerne cette remise.",
      "Das Papier rund um die Übergabe. Fügt eine Rubrik nur hinzu, wenn sie diese Übergabe betrifft.",
      "I documenti attorno alla consegna. Aggiungete una rubrica solo se riguarda questa consegna.",
      "Ils documents enturn la surdada. Agiuntai ina rubrica mo sch'ella pertutga questa surdada.",
    )
    FlowStep.Review -> pick(
      "Read the findings aloud together. Mark who carries each defect before anyone signs.",
      "Relisez les constats ensemble. Indiquez qui assume chaque défaut avant de signer.",
      "Geht die Befunde gemeinsam durch. Haltet fest, wer welchen Mangel trägt, bevor unterschrieben wird.",
      "Rileggete i rilievi insieme. Indicate chi si assume ogni difetto prima di firmare.",
      "Legì ils resultats ensemen. Notai tgi che surpiglia mintga defect avant che suttascriver.",
    )
    FlowStep.Sign -> pick(
      "Two signatures with two meanings: first the record, then, only if agreed, the costs.",
      "Deux signatures, deux sens : d'abord le constat, puis, seulement si accepté, les coûts.",
      "Zwei Unterschriften mit zwei Bedeutungen: zuerst das Protokoll, dann, nur wenn einverstanden, die Kosten.",
      "Due firme con due significati: prima il verbale, poi, solo se accettati, i costi.",
      "Duas suttascripziuns cun dus muments: l'emprim il protocol, lura, mo sche acceptà, ils custs.",
    )
    FlowStep.Export -> pick(
      "Export the PDF and hand a copy to each side right now.",
      "Exportez le PDF et remettez un exemplaire à chaque partie immédiatement.",
      "Exportiert das PDF und übergebt jeder Seite sofort ein Exemplar.",
      "Esportate il PDF e consegnate subito una copia a ciascuna parte.",
      "Exportai il PDF e surdatti immediat ina copia a mintga vart.",
    )
  }
  /** Said on the paper, because two signed originals are what both parties go home with. */
  val pdfTwoCopies = pick(
    "Established in two identical copies, one for each party.",
    "Établi en deux exemplaires identiques, un pour chaque partie.",
    "In zwei gleichlautenden Exemplaren erstellt, eines für jede Partei.",
    "Redatto in due esemplari identici, uno per ciascuna parte.",
    "Fatg en dus exemplars identics, in per mintga partida.",
  )

  val notAdvice = pick(
    "Practice values with their sources; not legal advice. In a dispute, the conciliation authority is free of charge everywhere in Switzerland.",
    "Valeurs de pratique avec leurs sources ; pas un conseil juridique. En cas de litige, l'autorité de conciliation est gratuite dans toute la Suisse.",
    "Praxiswerte mit ihren Quellen; keine Rechtsberatung. Im Streitfall ist die Schlichtungsbehörde in der ganzen Schweiz kostenlos.",
    "Valori di pratica con le loro fonti; non è una consulenza legale. In caso di lite, l'autorità di conciliazione è gratuita in tutta la Svizzera.",
    "Valurs da pratica cun lur funtaunas; nagin cussegl giuridic. En cas da dispita è l'autoritad da mediaziun gratuita en tut la Svizra.",
  )
}
