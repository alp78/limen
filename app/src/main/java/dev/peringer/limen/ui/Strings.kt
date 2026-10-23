package dev.peringer.limen.ui

import dev.peringer.limen.data.Appliance
import dev.peringer.limen.data.CautionProvider
import dev.peringer.limen.data.DepositKind
import dev.peringer.limen.data.DossierCard
import dev.peringer.limen.data.Furniture
import dev.peringer.limen.data.Grade
import dev.peringer.limen.data.ItemKey
import dev.peringer.limen.data.KeyTransferStatus
import dev.peringer.limen.data.KeyKind
import dev.peringer.limen.data.LegalProfile
import dev.peringer.limen.data.InitialRentFormRule
import dev.peringer.limen.data.LegalCheckStatus
import dev.peringer.limen.data.LegalRequirement
import dev.peringer.limen.data.ObjectGroup
import dev.peringer.limen.data.Role
import dev.peringer.limen.data.RoomKind
import dev.peringer.limen.data.DeliveryMethod
import dev.peringer.limen.data.SwissCanton
import dev.peringer.limen.data.TenantResponse
import dev.peringer.limen.data.UnilateralReason
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
  Fr, En, De, It, Rm;

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
  val validate = pick("Validate", "Valider", "Bestätigen", "Conferma", "Confermar")
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
  val clear = pick("CLEAR", "EFFACER", "LEEREN", "SVUOTA", "STIZZAR")
  val selectAll = pick("SELECT ALL", "TOUT SÉLECTIONNER", "ALLE AUSWÄHLEN", "SELEZIONA TUTTO", "TSCHERNER TUT")
  val undo = pick("UNDO", "ANNULER", "RÜCKGÄNGIG", "ANNULLA", "REVOCAR")
  fun removedForUndo(kind: UndoKind) = when (kind) {
    UndoKind.Defect -> pick(
      "Defect removed",
      "Défaut supprimé",
      "Mangel entfernt",
      "Difetto rimosso",
      "Don stizzà",
    )
    UndoKind.Photo -> pick(
      "Photo removed",
      "Photo supprimée",
      "Foto entfernt",
      "Foto rimossa",
      "Foto stizzada",
    )
    UndoKind.Object -> pick(
      "Supplied item removed",
      "Objet fourni supprimé",
      "Mitgelieferter Gegenstand entfernt",
      "Oggetto fornito rimosso",
      "Object furnì stizzà",
    )
    UndoKind.KeyCounts -> pick(
      "Key counts cleared",
      "Nombres de clés effacés",
      "Schlüsselzahlen geleert",
      "Numero di chiavi azzerato",
      "Dumbers da clavs stizzads",
    )
    UndoKind.Reading -> removedReading
    UndoKind.AccessDevice -> removedAccessDevice
    UndoKind.Document -> removedDocument
    UndoKind.FollowUp -> removedFollowUp
    UndoKind.Estimate -> removedEstimate
    UndoKind.Party -> removedParty
    UndoKind.KeyKind -> removedKeyKind
  }
  val add = pick("ADD", "AJOUTER", "HINZUFÜGEN", "AGGIUNGI", "AGIUNTAR")
  val decrease = pick("Decrease", "Diminuer", "Verringern", "Diminuire", "Reducir")
  val increase = pick("Increase", "Augmenter", "Erhöhen", "Aumentare", "Augmentar")
  val themeMode = pick("Dark theme", "Thème sombre", "Dunkles Design", "Tema scuro", "Tema stgir")
  val stepInspection = pick("Inspection", "Inspection", "Begehung", "Sopralluogo", "Inspecziun")
  val stepObjects = pick("Objects", "Objets", "Gegenstände", "Oggetti", "Objects")
  val stepDossier = pick("Dossier", "Dossier", "Dossier", "Dossier", "Dossier")
  val stepSign = pick("Signatures", "Signatures", "Unterschriften", "Firme", "Suttascripziuns")
  val stepExport = pick("Export", "Export", "Export", "Esporta", "Export")
  val stepRecord = pick("Record", "Constat", "Protokoll", "Verbale", "Protocol")
  val hubRecordLead = pick("Review, signatures and export", "Relecture, signatures et export", "Durchsicht, Unterschriften und Export", "Rilettura, firme ed esportazione", "Relectura, suttascripziuns ed export")

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
  val objectPreflight = pick(
    "Object and canton first",
    "Informations sur le logement",
    "Objekt und Kanton zuerst",
    "Prima oggetto e cantone",
    "Emprim object e chantun",
  )
  val completeObjectAndCanton = pick("COMPLETE", "COMPLÉTER", "ERGÄNZEN", "COMPLETA", "CUMPLETTAR")
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
  val stillMissing = pick("Missing data", "Données manquantes", "Fehlende Angaben", "Dati mancanti", "Datas mancantas")

  // Setup.
  val protocolKind = pick("Protocol", "État des lieux", "Protokoll", "Verbale", "Protocol")
  val objectLabel = pick("Object", "Objet loué", "Mietobjekt", "Ente locato", "Object da locaziun")
  val address = pick("Street and number", "Rue et numéro", "Strasse und Nummer", "Via e numero", "Via e numer")
  val place = pick("Postcode and place", "NPA et localité", "PLZ und Ort", "NAP e località", "NPA e lieu")
  val cantonLabel = pick("Canton of the object", "Canton de l'objet", "Kanton des Mietobjekts", "Cantone dell'oggetto", "Chantun da l'object")
  val selectCanton = pick("SELECT CANTON", "CHOISIR LE CANTON", "KANTON WÄHLEN", "SCEGLI CANTONE", "TSCHERNER CHANTUN")
  fun canton(canton: SwissCanton): String = "${canton.code} · ${canton.label}"
  val legalBasis = pick("Applicable basis", "Base applicable", "Anwendbare Grundlage", "Base applicabile", "Basa applitgabla")
  fun legalProfile(profile: LegalProfile) = when (profile) {
    LegalProfile.FederalAndLease -> pick(
      "Federal CO, the lease, and applicable cantonal or local rules",
      "CO fédéral, bail et règles cantonales ou locales applicables",
      "Bundesrecht OR, Mietvertrag und anwendbare kantonale oder örtliche Regeln",
      "CO federale, contratto e regole cantonali o locali applicabili",
      "DO federal, contract e reglas chantunalas u localas applitgablas",
    )
    LegalProfile.RomandContractual -> pick("Romand framework incorporated in the lease", "Contrat-cadre romand intégré au bail", "Westschweizer Rahmenvertrag im Mietvertrag", "Contratto-quadro romando integrato", "Contract general romand integrà")
    LegalProfile.VaudRulv2020 -> pick(
      "Vaud RULV, prior generally binding period from 1 July 2020 to 30 June 2026",
      "RULV Vaud, période obligatoire précédente du 1er juillet 2020 au 30 juin 2026",
      "Waadtländer RULV, frühere Allgemeinverbindlichkeit vom 1. Juli 2020 bis 30. Juni 2026",
      "RULV Vaud, precedente periodo obbligatorio dal 1 luglio 2020 al 30 giugno 2026",
      "RULV Vad, perioda obligatorica precedenta dal 1. fanadur 2020 fin ils 30 da zercladur 2026",
    )
    LegalProfile.VaudRulv2026 -> pick(
      "Vaud RULV, generally binding from 1 July 2026 to 30 June 2032",
      "RULV Vaud, force obligatoire du 1er juillet 2026 au 30 juin 2032",
      "Waadtländer RULV, allgemeinverbindlich vom 1. Juli 2026 bis 30. Juni 2032",
      "RULV Vaud, obbligatorie dal 1 luglio 2026 al 30 giugno 2032",
      "RULV Vad, obligatoric dal 1. fanadur 2026 fin ils 30 da zercladur 2032",
    )
  }
  val legalOverlayTitle = pick(
    "Canton overlay",
    "Règles applicables dans le canton",
    "Kantonale Ergänzung",
    "Regole cantonali",
    "Cumplement chantunal",
  )
  val initialRentForm = pick(
    "Official initial-rent form",
    "Formule officielle du loyer initial",
    "Amtliches Anfangsmietzinsformular",
    "Modulo ufficiale della pigione iniziale",
    "Formular uffizial da la fit iniziala",
  )
  fun initialRentFormRule(rule: InitialRentFormRule) = when (rule) {
    InitialRentFormRule.Required -> pick(
      "Required throughout the canton in the 2026 official snapshot",
      "Obligatoire dans tout le canton selon le relevé officiel 2026",
      "Gemäss amtlicher Übersicht 2026 im ganzen Kanton erforderlich",
      "Obbligatorio in tutto il cantone secondo il rilevamento ufficiale 2026",
      "Necessari en tut il chantun tenor la survista uffiziala 2026",
    )
    InitialRentFormRule.ConditionalLocal -> pick(
      "Check municipality, district and dwelling category in the 2026 official snapshot",
      "Vérifier commune, district et catégorie de logement dans le relevé officiel 2026",
      "Gemeinde, Bezirk und Wohnungskategorie in der amtlichen Übersicht 2026 prüfen",
      "Verificare comune, distretto e categoria dell'alloggio nel rilevamento ufficiale 2026",
      "Controllar vischnanca, district e categoria d'abitaziun en la survista uffiziala 2026",
    )
    InitialRentFormRule.NotRequired -> pick(
      "No canton-wide requirement listed in the 2026 official snapshot",
      "Aucune obligation cantonale générale dans le relevé officiel 2026",
      "Keine kantonsweite Pflicht in der amtlichen Übersicht 2026 aufgeführt",
      "Nessun obbligo cantonale generale nel rilevamento ufficiale 2026",
      "Nagina obligaziun chantunala generala en la survista uffiziala 2026",
    )
    InitialRentFormRule.VerifyCurrent -> pick(
      "Add the lease conclusion date or verify the rule in force on that date",
      "Ajoutez la date de conclusion du bail ou vérifiez la règle en vigueur à cette date",
      "Datum des Vertragsabschlusses ergänzen oder die damals geltende Regel prüfen",
      "Aggiungete la data di conclusione del contratto o verificate la regola allora in vigore",
      "Agiuntai la data da conclusiun dal contract u controllai la regla valaivla quella data",
    )
  }
  fun legalRequirement(requirement: LegalRequirement) = when (requirement) {
    LegalRequirement.CantonalDepositHandling -> pick(
      "Cantonal deposit handling: tenant-named account, deadline, bank and receipt checked",
      "Garantie cantonale : compte au nom du locataire, délai, banque et reçu vérifiés",
      "Kantonale Sicherheit: Konto auf Mieternamen, Frist, Bank und Beleg geprüft",
      "Garanzia cantonale: conto intestato al conduttore, termine, banca e ricevuta verificati",
      "Garanzia chantunala: conto en num dal locatari, termin, banca e quittanza controllads",
    )
    LegalRequirement.LeaseAndLocalTiming -> pick(
      "Appointment checked against the lease, local dates and public holidays",
      "Date de l’état des lieux vérifiée selon le bail, les échéances locales et les jours fériés",
      "Termin mit Mietvertrag, örtlichen Terminen und Feiertagen abgeglichen",
      "Appuntamento verificato con contratto, termini locali e giorni festivi",
      "Termin controllà tenor contract, termins locals e dis da festa",
    )
    LegalRequirement.InitialRentForm -> pick(
      "Official initial-rent form checked and kept with the entry dossier",
      "Formule officielle du loyer initial vérifiée et jointe au dossier d'entrée",
      "Amtliches Anfangsmietzinsformular geprüft und im Einzugsdossier abgelegt",
      "Modulo ufficiale della pigione iniziale verificato e conservato nel dossier d'entrata",
      "Formular uffizial da la fit iniziala controllà e mess en il dossier d'entrada",
    )
    LegalRequirement.DepositProof -> pick(
      "Deposit proof supplied within 30 days of signature and no later than entry",
      "Preuve de garantie fournie dans les 30 jours suivant la signature et au plus tard à l'entrée",
      "Nachweis der Sicherheit innert 30 Tagen nach Unterzeichnung und spätestens beim Einzug",
      "Prova della garanzia entro 30 giorni dalla firma e non oltre l'entrata",
      "Cumprova da la garanzia entaifer 30 dis da la suttascripziun e betg pli tard che l'entrada",
    )
    LegalRequirement.TenantLiabilityInsurance -> pick(
      "Tenant liability insurance checked for the tenancy",
      "Assurance responsabilité civile du locataire vérifiée pour le bail",
      "Mieterhaftpflichtversicherung für das Mietverhältnis geprüft",
      "Assicurazione responsabilità civile del conduttore verificata per la locazione",
      "Assicuranza da responsabladad dal locatari controllada per la locaziun",
    )
    LegalRequirement.LandlordLiabilityInsurance -> pick(
      "Landlord liability insurance checked for the tenancy",
      "Assurance responsabilité civile du bailleur vérifiée pour le bail",
      "Vermieterhaftpflichtversicherung für das Mietverhältnis geprüft",
      "Assicurazione responsabilità civile del locatore verificata per la locazione",
      "Assicuranza da responsabladad dal locatur controllada per la locaziun",
    )
    LegalRequirement.VaudExitReadiness -> pick(
      "Vaud return readiness checked: empty, clean, accessible and due at noon",
      "Restitution dans le canton de Vaud : logement vide, propre et accessible ; échéance de remise à midi vérifiée",
      "Waadtländer Rückgabe geprüft: leer, sauber, zugänglich und mittags fällig",
      "Restituzione vodese verificata: vuota, pulita, accessibile e dovuta a mezzogiorno",
      "Restituziun vadaisa controllada: vida, netta, accessibla e dueivel a mezdi",
    )
  }
  fun legalCheckStatus(status: LegalCheckStatus) = when (status) {
    LegalCheckStatus.ToVerify -> toVerify
    LegalCheckStatus.Confirmed -> pick("Confirmed", "Confirmé", "Bestätigt", "Confermato", "Confermà")
    LegalCheckStatus.MissingOrDisputed -> pick(
      "Missing / disputed",
      "Manquant / contesté",
      "Fehlt / bestritten",
      "Mancante / contestato",
      "Manca / contestà",
    )
    LegalCheckStatus.NotApplicable -> pick(
      "Not applicable",
      "Non applicable",
      "Nicht anwendbar",
      "Non applicabile",
      "Betg applitgabel",
    )
  }
  val legalOverlayNote = pick(
    "Reason, reservation or local verification",
    "Motif, réserve ou vérification locale",
    "Grund, Vorbehalt oder örtliche Prüfung",
    "Motivo, riserva o verifica locale",
    "Motiv, resalva u controlla locala",
  )
  val legalOverlayNoteRequired = pick(
    "Add a note for every missing, disputed or non-applicable outcome",
    "Ajoutez une note pour tout résultat manquant, contesté ou non applicable",
    "Bei fehlendem, bestrittenem oder nicht anwendbarem Ergebnis Notiz ergänzen",
    "Aggiungete una nota per ogni esito mancante, contestato o non applicabile",
    "Agiuntai ina nota per mintga resultat mancant, contestà u betg applitgabel",
  )
  // Federal overview of cantonal duties; the approved forms belong to the cantons.
  val legalSnapshot = pick("2026 BWO overview of cantonal initial-rent form duties", "Aperçu OFL 2026 des obligations cantonales de formule du loyer initial", "BWO-Übersicht 2026 der kantonalen Formularpflicht für Anfangsmietzinse", "Panoramica UFAB 2026 degli obblighi cantonali del modulo di pigione iniziale", "Survista UFL 2026 da las obligaziuns chantunalas per il formular da la fit iniziala")
  val legalSnapshotVaud = pick("2026 BWO cantonal overview and dated Vaud RULV", "Aperçu cantonal OFL 2026 et RULV vaudoises datées", "BWO-Kantonsübersicht 2026 und datierte Waadtländer RULV", "Panoramica cantonale UFAB 2026 e RULV vodesi datate", "Survista chantunala UFL 2026 e RULV vadaisas datadas")

  /** The snapshot line for one canton: only Vaud rests on the RULV. */
  fun legalSnapshotFor(canton: SwissCanton): String =
    if (canton == SwissCanton.VD) legalSnapshotVaud else legalSnapshot
  val vaudPreviousExitReport = pick(
    "Vaud entry: the tenant may request a copy of the previous tenant's exit report.",
    "Entrée vaudoise: le locataire peut demander une copie de l'état des lieux de sortie précédent.",
    "Einzug in Waadt: Die Mieterschaft kann eine Kopie des vorherigen Auszugsprotokolls verlangen.",
    "Entrata nel Vaud: il conduttore può chiedere una copia del precedente verbale d'uscita.",
    "Entrada en il Vad: il locatari po dumandar ina copia dal protocol da sortida precedent.",
  )
  val romandFrameworkInLease = pick(
    "The lease expressly incorporates the Romand framework contract",
    "Le bail intègre expressément le contrat-cadre romand",
    "Der Mietvertrag bezieht den Westschweizer Rahmenvertrag ausdrücklich ein",
    "Il contratto integra espressamente il contratto-quadro romando",
    "Il contract integrescha explicitamain il contract general romand",
  )
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
  // The shape a date is typed in, spelled in each language's own initials.
  val dateFormat = pick("DD/MM/YYYY", "JJ/MM/AAAA", "TT/MM/JJJJ", "GG/MM/AAAA", "DD/MM/AAAA")
  val timeFormat = pick("HH:MM", "HH:MM", "HH:MM", "HH:MM", "HH:MM")
  val pickDate = pick("PICK A DATE", "CHOISIR UNE DATE", "DATUM WÄHLEN", "SCEGLI UNA DATA", "TSCHERNER INA DATA")
  val pickTime = pick("PICK A TIME", "CHOISIR UNE HEURE", "UHRZEIT WÄHLEN", "SCEGLI UN'ORA", "TSCHERNER IN'URA")
  val parties = pick("Parties", "Parties", "Parteien", "Parti", "Partidas")
  val meters = pick("Meter readings", "Relevé des compteurs", "Zählerstände", "Letture dei contatori", "Posiziuns dals contaturs")
  val keysLabel = pick("Keys", "Clés", "Schlüssel", "Chiavi", "Clavs")
  val keyTransferLabel = pick("Key transfer", "Remise des clés", "Schlüsselübergabe", "Consegna delle chiavi", "Surdada da las clavs")
  fun keyTransfer(value: KeyTransferStatus) = when (value) {
    KeyTransferStatus.NotRecorded -> pick("Not recorded", "Non consignée", "Nicht erfasst", "Non registrata", "Betg registrà")
    KeyTransferStatus.AllTransferred -> pick("All keys and copies transferred", "Toutes les clés et copies remises", "Alle Schlüssel und Kopien übergeben", "Tutte le chiavi e copie consegnate", "Tut las clavs e copias surdadas")
    KeyTransferStatus.NoPhysicalKeys -> pick("No physical keys", "Aucune clé physique", "Keine physischen Schlüssel", "Nessuna chiave fisica", "Naginas clavs fisicas")
    KeyTransferStatus.Incomplete -> pick("Incomplete or disputed", "Incomplète ou contestée", "Unvollständig oder bestritten", "Incompleta o contestata", "Incumpletta u contestada")
  }
  val keyTransferNote = pick("Missing keys or delivery detail", "Clés manquantes ou détail de remise", "Fehlende Schlüssel oder Übergabedetail", "Chiavi mancanti o dettaglio", "Clavs mancantas u detagl")
  val selfMadeKeys = pick(
    "Copies had been made by the tenant",
    "Le locataire a fait faire des doubles",
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
    Role.Landlord -> pick("Contractual landlord", "Bailleur contractuel", "Vertragliche Vermieterschaft", "Locatore contrattuale", "Locatur contractual")
    Role.NextTenant -> pick("Next tenant", "Locataire suivant", "Nachmieter/in", "Conduttore successivo", "Proxim locatari")
    Role.Witness -> pick("Witness", "Témoin", "Zeuge/Zeugin", "Testimone", "Perditga")
    Role.Expert -> pick("Expert", "Expert·e", "Experte/Expertin", "Perito/a", "Expert/a")
  }
  val compiledBy = pick("Record compiled by", "Constat établi par", "Protokoll erstellt von", "Verbale redatto da", "Protocol fatg da")
  val representedBy = pick("Represented by", "Représenté·e par", "Vertreten durch", "Rappresentato/a da", "Represchentà/ada da")
  val newAddress = pick("New address", "Nouvelle adresse", "Neue Adresse", "Nuovo indirizzo", "Nova adressa")
  // The three actor buttons of the new-session form. On paper the protocol still has two
  // sides; the agency is the landlord side acting through the representative it sends.
  val actorLandlordPrivate = pick("Landlord", "Bailleur", "Vermieter/in", "Locatore/trice", "Locatur/a")
  val actorAgency = pick("Agency", "Régie", "Verwaltung", "Amministrazione", "Administraziun")
  val companyName = pick("Agency name", "Nom de la régie", "Name der Verwaltung", "Nome dell'amministrazione", "Num da l'administraziun")
  val email = pick("Email", "E-mail", "E-Mail", "E-mail", "E-mail")
  val tenantExitAddressHint = pick(
    "Your new address: the deposit letter and the final service-charge statement will be sent there.",
    "Votre nouvelle adresse : la lettre de garantie et le décompte final des charges y seront envoyés.",
    "Ihre neue Adresse: Der Depotbrief und die Schlussabrechnung der Nebenkosten werden dorthin geschickt.",
    "Il vostro nuovo indirizzo: la lettera del deposito e il conteggio finale delle spese accessorie saranno inviati lì.",
    "Vossa nova adressa: la brev dal deposit ed il quint final dals custs accessorics vegnan tramess là.",
  )

  // Meters.
  val meterElec1 = pick("Electricity I", "Électricité I", "Stromzähler I", "Elettricità I", "Electricitad I")
  val meterElec2 = pick("Electricity II", "Électricité II", "Stromzähler II", "Elettricità II", "Electricitad II")
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
    ItemKey.Stove -> pick("Stove", "Cuisinière", "Kochherd", "Cucina", "Furnel")
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
    DepositKind.Unknown -> toVerify
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
  val roomInspected = pick("ROOM INSPECTED", "PIÈCE CONTRÔLÉE", "RAUM GEPRÜFT", "LOCALE CONTROLLATO", "LOCAL CONTROLLÀ")
  val notInspected = pick("Not inspected", "Non contrôlé", "Nicht geprüft", "Non controllato", "Betg controllà")
  val inspectedNoDefects = pick("Inspected, no defect recorded", "Contrôle effectué, aucun défaut consigné", "Geprüft, kein Mangel erfasst", "Controllato, nessun difetto registrato", "Controllà, nagina mancanza registrada")
  val clearRoom = pick("CLEAR FINDINGS", "EFFACER LES CONSTATS", "BEFUNDE LÖSCHEN", "CANCELLA RILIEVI", "STIZZAR CONSTATAZIUNS")
  val removeRoom = pick("REMOVE ROOM", "RETIRER LA PIÈCE", "RAUM ENTFERNEN", "RIMUOVI LOCALE", "ALLONTANAR LOCAL")
  val chooseRoomsPresent = pick(
    "Rooms present",
    "Pièces présentes",
    "Vorhandene Räume",
    "Locali presenti",
    "Locals avant maun",
  )
  val useSelectedRooms = pick(
    "USE SELECTED ROOMS",
    "UTILISER CES PIÈCES",
    "AUSGEWÄHLTE RÄUME ÜBERNEHMEN",
    "USA I LOCALI SELEZIONATI",
    "DUVRAR ILS LOCALS TSCHERNIDS",
  )
  val areYouSure = pick("ARE YOU SURE?", "ÊTES-VOUS SÛR·E ?", "SIND SIE SICHER?", "SIETE SICURI?", "ESSAS VUS SEGIR?")
  val clearRoomBody = pick(
    "This removes every defect recorded in this room from the record, with the photos attached to them. The room, its overview photographs and its notes stay. Removed photos stay in private app storage for recovery until the whole record is deleted.",
    "Cette action retire du constat tous les défauts consignés dans cette pièce, avec les photos qui y sont jointes. La pièce, ses photos d’ensemble et ses notes restent. Les photos retirées restent dans le stockage privé de l’app, pour récupération, jusqu’à la suppression de tout le constat.",
    "Dadurch werden alle in diesem Raum erfassten Mängel mit den zugehörigen Fotos aus dem Protokoll entfernt. Der Raum, seine Übersichtsfotos und seine Notizen bleiben. Die entfernten Fotos bleiben zur Wiederherstellung im privaten App-Speicher, bis das ganze Protokoll gelöscht wird.",
    "Questa azione rimuove dal verbale tutti i difetti registrati in questo locale, con le foto allegate. Il locale, le sue fotografie d’insieme e le sue note restano. Le foto rimosse restano nello spazio privato dell’app per il recupero, finché l’intero verbale non viene eliminato.",
    "Quai allontanescha dal protocol tut las mancanzas registradas en quest local, cun las fotos agiuntadas. Il local, sias fotografias generalas e sias notizias restan. Las fotos allontanadas restan en la memoria privata da l’app per la recuperaziun, fin che l’entir protocol vegn stizzà.",
  )
  val removeRoomBody = pick(
    "This removes the room, its defects, its overview photographs and its notes from the record. Objects placed in it stay in the Objects list, without a room. Removed photos stay in private app storage for recovery until the whole record is deleted.",
    "Cette action retire du constat la pièce, ses défauts, ses photos d’ensemble et ses notes. Les objets qui y sont placés restent dans la liste des objets, sans pièce. Les photos retirées restent dans le stockage privé de l’app, pour récupération, jusqu’à la suppression de tout le constat.",
    "Dadurch werden der Raum, seine Mängel, seine Übersichtsfotos und seine Notizen aus dem Protokoll entfernt. Darin platzierte Gegenstände bleiben in der Liste der Gegenstände, ohne Raum. Die entfernten Fotos bleiben zur Wiederherstellung im privaten App-Speicher, bis das ganze Protokoll gelöscht wird.",
    "Questa azione rimuove dal verbale il locale, i suoi difetti, le sue fotografie d’insieme e le sue note. Gli oggetti collocati nel locale restano nell’elenco degli oggetti, senza locale. Le foto rimosse restano nello spazio privato dell’app per il recupero, finché l’intero verbale non viene eliminato.",
    "Quai allontanescha dal protocol il local, sias mancanzas, sias fotografias generalas e sias notizias. Ils objects mess en el restan en la glista dals objects, senza local. Las fotos allontanadas restan en la memoria privata da l’app per la recuperaziun, fin che l’entir protocol vegn stizzà.",
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
  val objectInspected = pick("ITEM AND CONDITION CHECKED", "OBJET ET ÉTAT CONTRÔLÉS", "GEGENSTAND UND ZUSTAND GEPRÜFT", "OGGETTO E STATO CONTROLLATI", "OBJECT E STADI CONTROLLADS")
  val wholeFlat = pick("The flat", "Le logement", "Die Wohnung", "L'alloggio", "L'abitaziun")
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
  val leaseConcludedOn = pick(
    "Lease concluded / signed on",
    "Bail conclu / signé le",
    "Mietvertrag abgeschlossen / unterzeichnet am",
    "Contratto concluso / firmato il",
    "Contract concludì / suttascrit ils",
  )
  val tenancyStart = pick("Tenancy from", "Début du bail", "Mietbeginn", "Locazione dal", "Locaziun dapi ils")
  val tenancyEnd = pick("Tenancy until", "Fin du bail", "Mietende", "Locazione fino al", "Locaziun enfin ils")
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
    "Description du défaut : emplacement et dimensions",
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
  val chargedToTenant = pick("Landlord asserts a tenant liability claim", "Le bailleur invoque la responsabilité du locataire", "Vermieterschaft macht einen Anspruch geltend", "Il locatore fa valere una pretesa verso il conduttore", "Il locatur fa valair ina pretensiun envers il locatari")
  val infoCharged = pick(
    "An exit protocol is only a valid defect notice for the defects it explicitly charges to the tenant. Unmarked findings document the state of the flat without a claim.",
    "L'état des lieux de sortie ne vaut avis des défauts que pour les défauts explicitement imputés au locataire. Les constats non marqués documentent l'état sans prétention.",
    "Das Rückgabeprotokoll gilt nur für die dem Mieter ausdrücklich angelasteten Mängel als Mängelrüge. Unmarkierte Befunde dokumentieren den Zustand ohne Forderung.",
    "Il verbale di riconsegna vale come notifica dei difetti solo per i difetti esplicitamente imputati al conduttore. I rilievi non marcati documentano lo stato senza pretese.",
    "Il protocol da sortida vala sco notificaziun da mancanzas mo per las mancanzas messas explicitamain a quint al locatari.",
  )
  val fixByLabel = pick("To be fixed by", "À réparer par", "Behebung durch", "Da riparare a cura di", "Da reparar tras")
  val fixOpen = pick("Open", "À définir", "Offen", "Da definire", "Avert")
  val fixDeadline = pick("By (date)", "Délai de réparation", "Bis am", "Entro il", "Fin ils")
  val costNote = pick("Cost note / estimate", "Coût estimé ou montant du devis", "Kosten / Voranschlag", "Costi / preventivo", "Custs / preventiv")
  val reservationLabel = pick("Reservation", "Réserve", "Vorbehalt", "Riserva", "Resalva")
  val inkEvidenceNotice = pick(
    "Captured ink is evidence of the recorded outcome, not a Swiss qualified electronic signature.",
    "La signature confirme les constatations et réserves enregistrées. La prise en charge des frais reste distincte. Il ne s’agit pas d’une signature électronique qualifiée.",
    "Die erfasste Unterschrift belegt das protokollierte Ergebnis; sie ist keine qualifizierte elektronische Signatur der Schweiz.",
    "La firma acquisita prova l'esito registrato; non è una firma elettronica qualificata svizzera.",
    "La suttascripziun registrada documentescha il resultat; ella n'è betg ina suttascripziun electronica qualifitgada svizra.",
  )
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
    TenantResponse.Agrees -> pick("Agrees with the description", "Accepte la description", "Mit der Beschreibung einverstanden", "Concorda con la descrizione", "Accepta la descripziun")
    TenantResponse.Disputes -> pick("Disputes", "Conteste", "Bestreitet", "Contesta", "Contesta")
    TenantResponse.NormalWear -> pick("Normal wear", "Usure normale", "Normale Abnützung", "Usura normale", "Isanza normala")
    TenantResponse.ExistingAtEntry -> pick("Already at entry", "Déjà présent à l'entrée", "Bereits bei Einzug", "Già presente all'entrata", "Gia tar l'entrada")
  }
  val tenantComment = pick("Tenant's comment", "Commentaire du locataire", "Kommentar der Mieterschaft", "Commento del conduttore", "Commentari dal locatari")
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
    "Unilateral record",
    "Constat unilatéral",
    "Einseitiges Protokoll",
    "Verbale unilaterale",
    "Protocol unilateral",
  )
  val unilateralReasonLabel = pick("Reason", "Motif", "Grund", "Motivo", "Motiv")
  fun unilateralReason(reason: UnilateralReason) = when (reason) {
    UnilateralReason.OtherPartyAbsent -> pick("Other party absent", "Autre partie absente", "Gegenpartei abwesend", "Controparte assente", "Cuntrapartida absenta")
    UnilateralReason.RefusedInspection -> pick("Inspection refused", "Contrôle refusé", "Abnahme verweigert", "Controllo rifiutato", "Controlla refusada")
    UnilateralReason.RemoteKeyReturn -> pick("Keys returned remotely", "Clés remises à distance", "Schlüssel aus der Ferne zurückgegeben", "Chiavi restituite a distanza", "Clavs restituidas a distanza")
    UnilateralReason.Other -> pick("Other documented reason", "Autre motif documenté", "Anderer dokumentierter Grund", "Altro motivo documentato", "Auter motiv documentà")
  }
  val unilateralNote = pick("Facts, notice and witness", "Faits, avis et témoin", "Sachverhalt, Mitteilung und Zeuge", "Fatti, avviso e testimone", "Fats, avis e perditga")
  val infoOneSided = pick(
    "If the other side is absent or refuses: record the reason, document the condition with dated evidence, use a neutral witness where possible, and return keys by a traceable agreed method. Keep proof of delivery.",
    "Si l'autre partie est absente ou refuse : consignez le motif, documentez l'état avec des preuves datées, faites appel si possible à un témoin neutre et rendez les clés par une méthode convenue et traçable. Conservez la preuve de remise.",
    "Ist die andere Seite abwesend oder verweigert sie: Grund festhalten, Zustand mit datierten Belegen dokumentieren, wenn möglich eine neutrale Zeugin beiziehen und Schlüssel auf vereinbarte nachweisbare Weise zurückgeben. Zustellnachweis aufbewahren.",
    "Se l'altra parte è assente o rifiuta: registrate il motivo, documentate lo stato con prove datate, ricorrete se possibile a un testimone neutrale e restituite le chiavi con un metodo concordato e tracciabile. Conservate la prova di consegna.",
    "Sche l'autra vart manca u refusescha: registrai il motiv, documentai il stadi cun cumprovas datadas, duvrai sche pussaivel ina perditga neutrala e restitui las clavs cun ina metoda cunvegnida e cumprovabla. Conservai la cumprova da consegna.",
  )


  // The case's cards and the flow's frame.
  val exitFromThis = pick("EXIT", "SORTIE", "AUSZUG", "RICONSEGNA", "SORTIDA")
  val finalizeEntryFirst = pick("FINALIZE", "FINALISER", "ABSCHLIESSEN", "FINALIZZA", "FINALISAR")
  val sure = pick("SURE?", "SÛR ?", "SICHER?", "SICURO?", "SEGIR?")
  val noAddress = pick("No address", "Sans adresse", "Ohne Adresse", "Senza indirizzo", "Senza adressa")
  val infoDeposit = pick(
    "A blocked bank account preserves the deposited capital. Guarantee insurance replaces that cash deposit with a recurring, normally non-refundable premium. Prices and conditions change, so compare current terms directly with the bank or insurer.",
    "Le compte bloqué conserve le capital déposé. L'assurance de garantie remplace ce dépôt en espèces par une prime périodique, généralement non remboursable. Les prix et conditions changent : vérifiez les conditions actuelles auprès de la banque ou de l'assureur.",
    "Das Sperrkonto erhält das eingezahlte Kapital. Eine Kautionsversicherung ersetzt diese Bareinlage durch eine wiederkehrende, in der Regel nicht rückzahlbare Prämie. Preise und Bedingungen ändern sich; aktuelle Angaben direkt bei Bank oder Versicherer prüfen.",
    "Il conto bloccato conserva il capitale depositato. L'assicurazione di garanzia sostituisce il deposito in contanti con un premio periodico, di regola non rimborsabile. Prezzi e condizioni cambiano: verificate quelli attuali presso banca o assicuratore.",
    "Il conto bloccà mantegna il chapital deposità. L'assicuranza da garanzia remplazza il deposit en daners cun ina premia periodica, per regla betg restituibla. Pretschs e cundiziuns ston vegnir verifitgads tar la banca u l'assicuradur.",
  )
  val photos = pick("Photos", "Photos", "Fotos", "Foto", "Fotos")

  // The only signature surface offered by current records.
  val signRecordTitle = pick(
    "Signature of the record",
    "Signature du constat",
    "Unterschrift des Befunds",
    "Firma del verbale",
    "Suttascripziun dal protocol",
  )
  val signRecordMeaning get() = tipRecord().lead
  val signKey = pick("SIGN", "SIGNER", "UNTERSCHREIBEN", "FIRMA", "SUTTASCRIVER")
  val refusedKey = pick("REFUSED", "REFUSÉE", "VERWEIGERT", "RIFIUTATA", "REFUSADA")
  val clearInk = pick("CLEAR", "EFFACER", "LÖSCHEN", "CANCELLA", "STIZZAR")
  val saveInk = pick("KEEP", "CONSERVER", "ÜBERNEHMEN", "CONSERVA", "TEGNAIR")
  val signaturePad = pick(
    "Signature drawing area",
    "Zone de dessin de la signature",
    "Zeichenfeld für die Unterschrift",
    "Area per disegnare la firma",
    "Zona per dissegnar la suttascripziun",
  )
  val signaturePadEmpty = pick(
    "No signature drawn",
    "Aucune signature dessinée",
    "Noch keine Unterschrift gezeichnet",
    "Nessuna firma disegnata",
    "Nagina suttascripziun dissegnada",
  )
  val signaturePadHasInk = pick(
    "Signature drawn",
    "Signature dessinée",
    "Unterschrift gezeichnet",
    "Firma disegnata",
    "Suttascripziun dissegnada",
  )
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
    "La finalisaziun fixescha questa versiun e sias cumprovas originalas. Midadas creeschan in supplement.",
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
    FinalizationIssue.Address -> pick("Add the street and number", "Indiquez la rue et le numéro", "Strasse und Nummer ergänzen", "Indicate via e numero civico", "Agiuntai via e numer")
    FinalizationIssue.Place -> pick("Add the postcode and place", "Indiquez le NPA et la localité", "PLZ und Ort ergänzen", "Indicate NAP e località", "Agiuntai NPA e lieu")
    FinalizationIssue.Canton -> pick("Select the object's canton", "Choisissez le canton de l'objet", "Kanton des Mietobjekts wählen", "Scegliete il cantone dell'oggetto", "Tscherner il chantun da l'object")
    FinalizationIssue.HandoverDate -> pick("Add the handover date", "Indiquez la date de remise", "Übergabedatum ergänzen", "Indicate la data di consegna", "Agiuntai la data da surdada")
    FinalizationIssue.HandoverTime -> pick("Add the handover time, or correct a time that is not valid", "Indiquez l'heure de remise ou corrigez une heure non valide", "Übergabezeit ergänzen oder ungültige Uhrzeit korrigieren", "Indicate l'ora di consegna o correggete un'ora non valida", "Agiuntai l'ura da surdada u midai ina ura nunvalida")
    FinalizationIssue.LegalOverlay -> pick(
      "Record every applicable canton-overlay check and explain exceptions",
      "Consignez chaque contrôle cantonal applicable et expliquez les exceptions",
      "Alle anwendbaren Kantonprüfungen erfassen und Ausnahmen erläutern",
      "Registrate ogni verifica cantonale applicabile e spiegate le eccezioni",
      "Registrai mintga controlla chantunala applitgabla ed explitgai excepziuns",
    )
    FinalizationIssue.Parties -> pick("Name both tenant and landlord / agency", "Nommez le locataire et le bailleur / la régie", "Mieterschaft und Vermieterschaft / Verwaltung benennen", "Indicate conduttore e locatore / amministrazione", "Numnai locatari e locatur / administraziun")
    FinalizationIssue.Rooms -> pick("Confirm every listed room was inspected", "Confirmez le contrôle de chaque pièce", "Prüfung jedes aufgeführten Raums bestätigen", "Confermate ogni locale controllato", "Confermai mintga local controllà")
    FinalizationIssue.NoRooms -> pick(
      "Add at least one room to the inspection",
      "Ajoutez au moins une pièce à l'inspection",
      "Fügen Sie der Besichtigung mindestens einen Raum hinzu",
      "Aggiungete almeno un locale all'ispezione",
      "Agiuntai almain in local a l'inspecziun",
    )
    FinalizationIssue.Objects -> pick("Confirm every supplied item was checked", "Confirmez chaque objet fourni", "Jeden mitübergebenen Gegenstand bestätigen", "Confermate ogni oggetto fornito", "Confermai mintga object surdà")
    FinalizationIssue.Dossier -> pick("Complete or remove every added dossier component", "Complétez ou retirez chaque rubrique ajoutée au dossier", "Jede hinzugefügte Dossierrubrik ausfüllen oder entfernen", "Completate o rimuovete ogni rubrica aggiunta al dossier", "Cumplettai u allontanai mintga rubrica agiuntada al dossier")
    FinalizationIssue.Keys -> pick("Record a consistent key-transfer outcome and missing-key details", "Consignez une remise des clés cohérente et le détail des clés manquantes", "Stimmige Schlüsselübergabe und fehlende Schlüssel erfassen", "Registrate una consegna coerente e i dettagli delle chiavi mancanti", "Registrai ina surdada coerenta e detagls da clavs mancantas")
    FinalizationIssue.UnilateralReason -> pick("State why this record is unilateral", "Indiquez pourquoi le constat est unilatéral", "Grund für das einseitige Protokoll angeben", "Indicate perché il verbale è unilaterale", "Inditgai pertge il protocol è unilateral")
    FinalizationIssue.DefectText -> pick("Describe every defect precisely", "Décrivez chaque défaut précisément", "Jeden Mangel präzise beschreiben", "Descrivete ogni difetto con precisione", "Descrivai mintga mancanza precis")
    FinalizationIssue.Evidence -> pick("Restore every referenced photo and signature file", "Rétablissez chaque photo et signature référencée", "Alle referenzierten Fotos und Unterschriften wiederherstellen", "Ripristinate ogni foto e firma indicata", "Restaurai mintga foto e suttascripziun inditgada")
    FinalizationIssue.RecordOutcomes -> pick("Record each required signature or refusal", "Consignez chaque signature ou refus requis", "Jede erforderliche Unterschrift oder Verweigerung erfassen", "Registrate ogni firma o rifiuto richiesto", "Registrai mintga suttascripziun u refusa necessaria")
  }

  // Export.
  val exportPdf = pick("EXPORT PDF", "EXPORTER LE PDF", "PDF EXPORTIEREN", "ESPORTA PDF", "EXPORTAR PDF")
  val exportUpdatedFinalCopy = pick(
    "EXPORT UPDATED FINAL COPY",
    "EXPORTER LA COPIE FINALE ACTUALISÉE",
    "AKTUALISIERTE ENDKOPIE EXPORTIEREN",
    "ESPORTA COPIA FINALE AGGIORNATA",
    "EXPORTAR COPIA FINALA ACTUALISADA",
  )
  val saveAsDraft = pick("SAVE AS DRAFT", "ENREGISTRER LE BROUILLON", "ALS ENTWURF SPEICHERN", "SALVA COME BOZZA", "MEMORISAR SCO SBOZ")
  val pdfLanguage = pick("Language of the PDF", "Langue du PDF", "Sprache des PDF", "Lingua del PDF", "Lingua dal PDF")
  val open = pick("OPEN", "OUVRIR", "ÖFFNEN", "APRI", "AVRIR")
  val share = pick("SHARE", "PARTAGER", "TEILEN", "CONDIVIDI", "CUNDIVIDER")
  val capturedOn = pick("Captured", "Saisie", "Erfasst", "Acquisita", "Registrada")
  val revisionLabel = pick("Record revision", "Révision du constat", "Protokollrevision", "Revisione del verbale", "Revisiun dal protocol")
  val copyDelivery = pick("Copies delivered", "Copies remises", "Kopien zugestellt", "Copie consegnate", "Copias surdadas")
  val recordCopyFor = pick("Record copy for", "Confirmer la remise d’une copie à", "Kopie erfassen für", "Registra copia per", "Registrar copia per")
  val deliveryDetail = pick("Recipient, address or delivery reference", "Destinataire, adresse ou référence d'envoi", "Empfänger, Adresse oder Zustellreferenz", "Destinatario, indirizzo o riferimento di consegna", "Destinatari, adressa u referenza da consegna")
  val clearDelivery = pick("Clear delivery record", "Effacer la remise", "Zustellnachweis löschen", "Cancella consegna", "Stizzar la consegna")
  val deliveryChanged = pick(
    "Final copy needs updating",
    "La copie finale doit être actualisée",
    "Endkopie muss aktualisiert werden",
    "La copia finale va aggiornata",
    "La copia finala sto vegnir actualisada",
  )
  val deliveryChangedBody = pick(
    "The delivery record changed after the last PDF export. Export and send the updated final copy.",
    "Le suivi de remise a changé après le dernier export PDF. Exportez et envoyez la copie finale actualisée.",
    "Der Zustellnachweis wurde nach dem letzten PDF-Export geändert. Aktualisierte Endkopie exportieren und senden.",
    "La consegna è cambiata dopo l'ultimo PDF. Esportate e inviate la copia finale aggiornata.",
    "Il register da consegna è sa midà suenter l'ultim PDF. Exportai e tramettai la copia finala actualisada.",
  )
  fun deliveryMethod(method: DeliveryMethod) = when (method) {
    DeliveryMethod.InPerson -> pick("In person", "En main propre", "Persönlich", "Di persona", "Persunalmain")
    DeliveryMethod.Electronic -> pick("Electronic share", "Envoi électronique", "Elektronisch", "Invio elettronico", "Tramess electronic")
    DeliveryMethod.RegisteredMail -> pick("Registered mail", "Courrier recommandé", "Eingeschriebene Post", "Posta raccomandata", "Posta recumandada")
    DeliveryMethod.Other -> pick("Other documented method", "Autre mode documenté", "Andere dokumentierte Art", "Altro metodo documentato", "Autra moda documentada")
  }
  val deliverySelfDeclared = pick(
    "Confirm only after an identical copy was actually handed over or sent. This self-declared post-finalization metadata is not covered by the evidence fingerprint.",
    "Confirmez la remise après avoir transmis une copie identique. Cette confirmation, ajoutée après la finalisation, ne fait pas partie des éléments couverts par l’empreinte du constat.",
    "Erst bestätigen, nachdem eine identische Kopie tatsächlich übergeben oder gesendet wurde. Diese selbst deklarierte Metadatenangabe nach Abschluss ist nicht vom Beleg-Fingerabdruck gedeckt.",
    "Confermate solo dopo aver realmente consegnato o inviato una copia identica. Questo metadato autodichiarato dopo la finalizzazione non è coperto dall'impronta delle prove.",
    "Confermai pir suenter che ina copia identica è vegnida surdada u tramessa. Questa metadata declerada sez suenter la finalisaziun n'è betg cumpigliada da l'impronta da las cumprovas.",
  )
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
      "Ina cumprova è midada u manca. Quest protocol na po betg vegnir exportà.",
    )
    ExportIssue.Failed -> pick("The PDF could not be created. Try again.", "Le PDF n'a pas pu être créé. Réessayez.", "Das PDF konnte nicht erstellt werden. Erneut versuchen.", "Il PDF non ha potuto essere creato. Riprovate.", "Il PDF n'ha betg pudì vegnir creà. Empruvai danovamain.")
  }
  val lockedRecord = pick("Finalized, read only", "Finalisé, lecture seule", "Abgeschlossen, nur lesen", "Finalizzato, sola lettura", "Finalisà, mo leger")
  val amendRecord = pick("CREATE AMENDMENT", "CRÉER UN AVENANT", "NACHTRAG ERSTELLEN", "CREA INTEGRAZIONE", "CREAR SUPPLEMENT")
  val amendLead = pick(
    "A correction starts a new draft linked to this final record; the original remains unchanged.",
    "Une correction crée un nouveau brouillon lié à ce constat final ; l'original reste inchangé.",
    "Eine Korrektur erstellt einen neuen, mit diesem Abschluss verknüpften Entwurf; das Original bleibt unverändert.",
    "Una correzione crea una nuova bozza collegata a questo verbale finale; l'originale resta invariato.",
    "Ina correctura creescha in nov sboz collià cun quest protocol final; l'original resta nunmidà.",
  )
  val amending = pick("Creating amendment…", "Création de l'avenant…", "Nachtrag wird erstellt…", "Creazione integrazione…", "Creaziun dal supplement…")
  val amendFailed = pick(
    "The amendment could not be created. Check the free space on the phone and try again.",
    "L'avenant n'a pas pu être créé. Vérifiez l'espace libre sur le téléphone et réessayez.",
    "Der Nachtrag konnte nicht erstellt werden. Prüfen Sie den freien Speicher und versuchen Sie es erneut.",
    "Non è stato possibile creare l'integrazione. Verificare lo spazio libero sul telefono e riprovare.",
    "Il supplement n'ha betg pudì vegnir creà. Controllai il spazi liber sin il telefon ed empruvai anc ina giada.",
  )
  val fingerprint = pick("Evidence fingerprint", "Empreinte numérique du constat", "Fingerabdruck der Belege", "Impronta delle prove", "Impronta da las cumprovas")
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
    "Under Art. 257e CO, the bank may release the blocked deposit with both parties' agreement, an unopposed payment order, or an enforceable judgment. If the landlord starts no court, debt-enforcement or bankruptcy claim within one year after the tenancy ends, the tenant may request repayment from the bank.",
    "Selon l'art. 257e CO, la banque peut libérer la garantie avec l'accord des deux parties, un commandement de payer non frappé d'opposition ou un jugement exécutoire. Si le bailleur n'engage aucune procédure judiciaire, poursuite ou faillite dans l'année suivant la fin du bail, le locataire peut demander la restitution à la banque.",
    "Nach Art. 257e OR darf die Bank das Depot mit Zustimmung beider Parteien, aufgrund eines rechtskräftigen Zahlungsbefehls oder eines rechtskräftigen Urteils freigeben. Leitet die Vermieterschaft innert eines Jahres nach Mietende kein Gerichts-, Betreibungs- oder Konkursverfahren ein, kann die Mieterschaft die Rückzahlung verlangen.",
    "Secondo l'art. 257e CO, la banca può liberare il deposito con l'accordo di entrambe le parti, un precetto esecutivo non opposto o una sentenza esecutiva. Se entro un anno dalla fine della locazione il locatore non avvia un'azione giudiziaria, esecutiva o fallimentare, il conduttore può chiedere la restituzione alla banca.",
    "Tenor l'art. 257e DO po la banca liberar il deposit cun il consentiment da tuttas duas partidas, in cumond da pajament senza opposiziun u ina sentenzia executabla. Sche il locatur n'avra nagina procedura giudiziala, d'execuziun u da concurs entaifer in onn suenter la fin da la locaziun, po il locatari pretender la restituziun da la banca.",
  )
  val releaseInsurance = pick(
    "A guarantee insurance is not a blocked bank account. Follow the insurer's current cancellation and release procedure; provider terms govern, and premiums already paid are normally not refunded.",
    "Une assurance de garantie n'est pas un compte bancaire bloqué. Suivez la procédure actuelle de résiliation et de libération de l'assureur ; ses conditions s'appliquent et les primes déjà payées ne sont en principe pas remboursées.",
    "Eine Kautionsversicherung ist kein Sperrkonto. Das aktuelle Kündigungs- und Freigabeverfahren des Versicherers befolgen; dessen Bedingungen gelten, und bezahlte Prämien werden in der Regel nicht zurückerstattet.",
    "Un'assicurazione di garanzia non è un conto bloccato. Seguite la procedura attuale dell'assicuratore per disdetta e svincolo; valgono le sue condizioni e i premi già pagati di regola non sono rimborsati.",
    "Ina assicuranza da garanzia n'è betg in conto bloccà. Suandai la procedura actuala da l'assicuradur per la disditga e liberaziun; sias cundiziuns valan e premias pajadas na vegnan per regla betg restituidas.",
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
  val repairCost = pick("Equivalent replacement cost CHF", "Coût de remplacement équivalent CHF", "Kosten für gleichwertigen Ersatz CHF", "Costo di sostituzione equivalente CHF", "Custs da remplazzament equivalent CHF")
  val tenantShare = pick("Indicative remaining replacement value", "Valeur résiduelle indicative de remplacement", "Unverbindlicher Restwert bei Ersatz", "Valore residuo indicativo di sostituzione", "Valur restanta indicativa da remplazzament")
  val remainingValue = pick("Remaining value", "Valeur résiduelle", "Restwert", "Valore residuo", "Valur restanta")
  val customLifespan = pick("Own value", "Valeur libre", "Eigener Wert", "Valore proprio", "Atgna valur")

  fun life(k: dev.peringer.limen.sense.LifeKey) = when (k) {
    dev.peringer.limen.sense.LifeKey.DispersionPaint -> pick("Dispersion paint", "Peinture à dispersion", "Dispersionsfarbe", "Pittura a dispersione", "Colur a dispersiun")
    dev.peringer.limen.sense.LifeKey.AcrylicPaint -> pick("Acrylic/oil paint", "Peinture acryl/huile", "Acryl-/Ölfarbe", "Pittura acrilica/olio", "Colur acrilica")
    dev.peringer.limen.sense.LifeKey.WoodchipWallpaper -> pick("Woodchip wallpaper", "Papier ingrain", "Raufasertapete", "Tappezzeria ruvida", "Tapet grob")
    dev.peringer.limen.sense.LifeKey.PaintableWallpaper -> pick("Paintable wallpaper", "Papier repeignable", "Überstreichbare Tapete", "Tappezzeria ritinteggiabile", "Tapet surpittirabel")
    dev.peringer.limen.sense.LifeKey.Carpet -> pick("Carpet, medium", "Moquette moyenne", "Spannteppich mittel", "Moquette media", "Tapetsch mesaun")
    dev.peringer.limen.sense.LifeKey.NeedleFelt -> pick("Needle felt", "Feutre aiguilleté", "Nadelfilz", "Feltro agugliato", "Feuter")
    dev.peringer.limen.sense.LifeKey.LaminateMid -> pick("Laminate, medium", "Stratifié moyen", "Laminat mittel", "Laminato medio", "Laminat mesaun")
    dev.peringer.limen.sense.LifeKey.SolidParquet -> pick("Solid parquet", "Parquet massif", "Massivparkett", "Parquet massiccio", "Parquet massiv")
    dev.peringer.limen.sense.LifeKey.MultilayerParquet -> pick("Multilayer parquet", "Parquet multicouche", "Mehrschichtparkett", "Parquet multistrato", "Parquet a plirs stgalims")
    dev.peringer.limen.sense.LifeKey.ParquetSeal -> pick("Parquet seal", "Vitrification du parquet", "Parkettversiegelung", "Sigillatura del parquet", "Sigillatura dal parquet")
    dev.peringer.limen.sense.LifeKey.Pvc -> pick("PVC floor", "Sol PVC", "PVC-Belag", "Pavimento PVC", "Plaun PVC")
    dev.peringer.limen.sense.LifeKey.Linoleum -> pick("Linoleum", "Linoléum", "Linoleum", "Linoleum", "Linoleum")
    dev.peringer.limen.sense.LifeKey.CeramicTiles -> pick("Ceramic tiles", "Carrelage", "Keramikplatten", "Piastrelle", "Plattas da ceramica")
    dev.peringer.limen.sense.LifeKey.Fridge -> pick("Fridge", "Réfrigérateur", "Kühlschrank", "Frigorifero", "Frigorifer")
    dev.peringer.limen.sense.LifeKey.Freezer -> pick("Freezer", "Congélateur", "Tiefkühler", "Congelatore", "Congelatur")
    dev.peringer.limen.sense.LifeKey.Stove -> pick("Stove/oven", "Cuisinière/four", "Herd/Backofen", "Cucina/forno", "Furnel/furn")
    dev.peringer.limen.sense.LifeKey.Dishwasher -> pick("Dishwasher", "Lave-vaisselle", "Geschirrspüler", "Lavastoviglie", "Maschina da lavar plats")
    dev.peringer.limen.sense.LifeKey.Microwave -> pick("Microwave", "Micro-ondes", "Mikrowelle", "Microonde", "Microondas")
    dev.peringer.limen.sense.LifeKey.Steamer -> pick("Steamer", "Steamer", "Steamer", "Vaporiera", "Steamer")
    dev.peringer.limen.sense.LifeKey.ExtractorHood -> pick("Extractor hood", "Hotte", "Dampfabzug", "Cappa", "Chapa d'aspiraziun")
    dev.peringer.limen.sense.LifeKey.Taps -> pick("Taps/mixers", "Robinetterie", "Armaturen", "Rubinetteria", "Cranas")
    dev.peringer.limen.sense.LifeKey.BathtubAcrylic -> pick("Bathtub, acrylic", "Baignoire acryl", "Badewanne Acryl", "Vasca acrilica", "Vaschia acrilica")
    dev.peringer.limen.sense.LifeKey.BathtubSteel -> pick("Bathtub, steel", "Baignoire acier", "Badewanne Stahl", "Vasca in acciaio", "Vaschia d'atschal")
    dev.peringer.limen.sense.LifeKey.CeramicSink -> pick("Ceramic sink/WC", "Lavabo/WC céramique", "Lavabo/WC Keramik", "Lavabo/WC ceramica", "Lavabo/WC da ceramica")
    dev.peringer.limen.sense.LifeKey.MirrorCabinet -> pick("Mirror cabinet", "Armoire à glace", "Spiegelschrank", "Armadietto a specchio", "Stgaffa da spievel")
    dev.peringer.limen.sense.LifeKey.WashingMachine -> pick("Washing machine/dryer", "Lave-linge/sèche-linge", "Waschmaschine/Tumbler", "Lavatrice/asciugatrice", "Maschina da lavar/sitgentadur")
    dev.peringer.limen.sense.LifeKey.DoorSolid -> pick("Door, solid wood", "Porte bois massif", "Türe Massivholz", "Porta legno massiccio", "Porta da lain massiv")
    dev.peringer.limen.sense.LifeKey.LockCylinder -> pick("Lock cylinder/keys", "Cylindre/clés", "Schliesszylinder/Schlüssel", "Cilindro/chiavi", "Cilinder/clavs")
    dev.peringer.limen.sense.LifeKey.RollerShutterWood -> pick("Roller shutter, wood", "Volet roulant bois", "Rollladen Holz", "Avvolgibile legno", "Rullada da lain")
    dev.peringer.limen.sense.LifeKey.RollerShutterMetal -> pick("Roller shutter, metal", "Volet roulant métal", "Rollladen Metall", "Avvolgibile metallo", "Rullada da metal")
    dev.peringer.limen.sense.LifeKey.BlindStraps -> pick("Blind straps", "Sangles de store", "Storen-Gurten", "Cinghie", "Rissas")
    dev.peringer.limen.sense.LifeKey.SealantJoints -> pick("Sealant joints", "Joints silicone", "Kittfugen", "Giunti in silicone", "Giuntas da silicon")
    dev.peringer.limen.sense.LifeKey.Radiator -> pick("Radiator", "Radiateur", "Heizkörper", "Radiatore", "Radiatur")
  }

  // The deadlines face.
  val safeBy = pick("2-working-day target", "Objectif : 2 jours ouvrables", "Ziel: 2 Werktage", "Obiettivo: 2 giorni lavorativi", "Finamira: 2 dis da lavur")
  val listBy = pick("Contractual 10-day date", "Date contractuelle à 10 jours", "Vertragliches 10-Tage-Datum", "Data contrattuale a 10 giorni", "Data contractuala da 10 dis")
  val latestBy = pick("3-working-day practice reference", "Repère pratique : 3 jours ouvrables", "Praxiswert: 3 Werktage", "Riferimento pratico: 3 giorni lavorativi", "Valur da pratica: 3 dis da lavur")
  val clockExitTitle = pick(
    "Landlord's defect notice",
    "Avis des défauts du bailleur",
    "Mängelrüge der Vermieterschaft",
    "Notifica dei difetti del locatore",
    "Notificaziun da mancanzas dal locatur",
  )
  val clockExitBody = pick(
    "Art. 267a CO requires the landlord to inspect on return and notify the tenant immediately of each defect for which liability is claimed. BGer 4A_388/2013 reports usual guidance of 2 to 3 working days, another view up to one week, and confirms that circumstances decide. Send an itemized notice without delay and keep proof of delivery.",
    "L'art. 267a CO impose au bailleur de vérifier la chose à sa restitution et d'aviser immédiatement le locataire de chaque défaut dont il entend le rendre responsable. L'arrêt TF 4A_388/2013 mentionne une pratique de 2 à 3 jours ouvrables, une autre opinion allant jusqu'à une semaine, et rappelle que les circonstances décident. Envoyez sans délai un avis détaillé et conservez la preuve d'envoi.",
    "Art. 267a OR verlangt, dass die Vermieterschaft bei der Rückgabe prüft und der Mieterschaft jeden Mangel, für den sie einstehen soll, sofort meldet. BGer 4A_388/2013 nennt als übliche Richtlinie 2 bis 3 Werktage, eine andere Auffassung bis zu einer Woche, und stellt auf die Umstände ab. Senden Sie unverzüglich eine detaillierte Mitteilung und bewahren Sie den Zustellnachweis auf.",
    "L'art. 267a CO impone al locatore di esaminare la cosa alla riconsegna e di notificare immediatamente al conduttore ogni difetto di cui intende renderlo responsabile. La sentenza TF 4A_388/2013 menziona una prassi di 2 o 3 giorni lavorativi, un'altra opinione fino a una settimana, e conferma che contano le circostanze. Inviate senza ritardo una notifica dettagliata e conservate la prova.",
    "L'art. 267a DO pretenda che il locatur examineschia l'object tar la restituziun e notifitgeschia immediat mintga mancanza imputada al locatari. La sentenzia TF 4A_388/2013 menziunescha 2 fin 3 dis da lavur sco pratica usitada, in'autra opiniun fin ad in'emna, e resguarda las circumstanzas. Tramettai senza retard ina glista detagliada e conservai la cumprova.",
  )
  val clockEntryTitle = pick(
    "Tenant's move-in list",
    "Liste d'entrée du locataire",
    "Mängelliste beim Einzug",
    "Lista dei difetti alla consegna",
    "Glista da mancanzas tar l'entrada",
  )
  val clockEntryBody = pick(
    "Check the lease and applicable cantonal terms. If they set a ten-day period for additions to the move-in record, meet it; otherwise report additional defects promptly in writing and keep proof. Ten days is not a uniform federal statutory deadline.",
    "Vérifiez le bail et les règles cantonales applicables. S'ils prévoient dix jours pour compléter l'état des lieux d'entrée, respectez ce délai ; sinon, signalez rapidement tout défaut supplémentaire par écrit et conservez la preuve. Dix jours ne constituent pas un délai légal fédéral uniforme.",
    "Mietvertrag und anwendbare kantonale Regeln prüfen. Sehen sie zehn Tage für Ergänzungen zum Einzugsprotokoll vor, ist diese Frist einzuhalten; andernfalls zusätzliche Mängel rasch schriftlich melden und den Nachweis aufbewahren. Zehn Tage sind keine einheitliche bundesrechtliche Frist.",
    "Verificate il contratto e le regole cantonali applicabili. Se prevedono dieci giorni per completare il verbale d'entrata, rispettate tale termine; altrimenti segnalate rapidamente per iscritto gli ulteriori difetti e conservate la prova. Dieci giorni non sono un termine legale federale uniforme.",
    "Controllai il contract e las reglas chantunalas applitgablas. Sch'ellas fixeschan diesch dis per cumplettar il protocol d'entrada, observai quel termin; uschiglio annunziai svelt en scrit ulteriuras mancanzas e conservai la cumprova. Diesch dis n'èn betg in termin federal unitar.",
  )
  fun tipRecord() = Tip(
    pick(
      "This signature confirms the complete handover record and its reservations, without admitting liability or a payment obligation.",
      "Cette signature confirme l’ensemble du constat et ses réserves, sans reconnaissance de responsabilité ni de dette.",
      "Diese Unterschrift bestätigt das vollständige Protokoll samt Vorbehalten, ohne Anerkennung einer Haftung oder Zahlungspflicht.",
      "Questa firma conferma l’intero verbale e le riserve, senza riconoscimento di responsabilità o debito.",
      "Questa suttascripziun conferma l’entir protocol e las resalvas, senza renconuschientscha da responsabladad u da debit.",
    ),
    listOf(
      pick(
        "Responsibility and any amount are handled separately",
        "La responsabilité et tout montant sont traités séparément",
        "Verantwortung und ein allfälliger Betrag werden getrennt behandelt",
        "Responsabilità ed eventuali importi sono trattati separatamente",
        "Responsabladad ed eventuals imports vegnan tractads separadamain",
      ),
      pick(
        "Any reservation entered before signing is printed beside that person's outcome",
        "Toute réserve saisie avant la signature est imprimée à côté de l'issue de cette personne",
        "Ein vor der Unterschrift erfasster Vorbehalt steht neben dem Ergebnis dieser Person",
        "Ogni riserva inserita prima della firma è stampata accanto all'esito della persona",
        "Mintga resalva endatada avant la suttascripziun vegn stampada dasper il resultat da la persuna",
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
        "Blocked account: release follows Art. 257e CO, including agreement or an enforceable title",
        "Compte bloqué : la libération suit l'art. 257e CO, notamment l'accord ou un titre exécutoire",
        "Sperrkonto: Freigabe nach Art. 257e OR, insbesondere mit Zustimmung oder Vollstreckungstitel",
        "Conto bloccato: lo svincolo segue l'art. 257e CO, incluso l'accordo o un titolo esecutivo",
        "Conto bloccà: liberaziun tenor l'art. 257e DO, cun consentiment u in titel executabel",
      ),
      pick(
        "Guarantee insurance: compare the provider's current recurring premium and refund terms",
        "Assurance de garantie : comparez la prime périodique et les conditions de remboursement actuelles",
        "Kautionsversicherung: aktuelle wiederkehrende Prämie und Rückerstattungsbedingungen vergleichen",
        "Assicurazione di garanzia: confrontate il premio periodico e le condizioni di rimborso attuali",
        "Assicuranza da garanzia: cumparegliai la premia periodica e las cundiziuns actualas da restituziun",
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
      pick("Return keys by an agreed, traceable method", "Rendre les clés par une méthode convenue et traçable", "Schlüssel auf vereinbarte, nachweisbare Weise zurückgeben", "Restituire le chiavi con un metodo concordato e tracciabile", "Restituir las clavs cun ina metoda cunvegnida e cumprovabla"),
      pick("Keep proof of the key delivery", "Conserver la preuve de remise des clés", "Nachweis der Schlüsselzustellung aufbewahren", "Conservare la prova di consegna delle chiavi", "Conservar la cumprova da consegna da las clavs"),
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
      pick("Lifespan estimates residual value; actual repair and loss still matter", "La longévité estime la valeur résiduelle ; la réparation et le dommage réels restent déterminants", "Die Lebensdauer schätzt den Restwert; tatsächliche Reparatur und Schaden bleiben massgebend", "La durata stima il valore residuo; riparazione e danno effettivi restano determinanti", "La durada stima la valur restanta; reparatura e donn effectiv restan decisivs"),
      pick("Normal wear is not charged to the tenant", "L'usure normale n'est pas imputée au locataire", "Normale Abnützung wird der Mieterschaft nicht belastet", "L'usura normale non è addebitata al conduttore", "L'isanza normala na vegn betg debitada al locatari"),
      pick("Use the known installation or renovation date", "Utilisez la date connue de pose ou de rénovation", "Bekanntes Einbau- oder Renovationsdatum verwenden", "Usate la data nota di installazione o ristrutturazione", "Duvrai la data enconuschenta da l'installaziun u renovaziun"),
      pick("Values here are public citations; the full table is sold by HEV and MV", "Valeurs publiques ; la table complète est vendue par HEV et ASLOCA", "Öffentliche Werte; die vollständige Tabelle verkaufen HEV und MV", "Valori pubblici; la tabella completa è venduta da HEV e ASI", "Valurs publicas; la tabella cumpletta vendan HEV e MV"),
      pick("Not legal advice; conciliation has no court fee, but your own costs may remain", "Pas un conseil juridique ; la conciliation est sans frais judiciaires, mais vos propres frais peuvent subsister", "Keine Rechtsberatung; im Schlichtungsverfahren fallen keine Gerichtskosten an, eigene Kosten können bleiben", "Non è consulenza legale; la conciliazione non comporta spese giudiziarie, ma possono restare costi propri", "Nagin cussegl giuridic; la mediaziun n'ha nagins custs giudizials, ma agens custs pon restar"),
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
      pick("Cantonal and local holidays are not calculated; check the calendar", "Les jours fériés cantonaux et locaux ne sont pas calculés ; vérifiez le calendrier", "Kantonale und lokale Feiertage werden nicht berechnet; Kalender prüfen", "Le festività cantonali e locali non sono calcolate; verificate il calendario", "Firaus chantunals e locals na vegnan betg calculads; controllai il chalender"),
      pick("Deposit: Art. 257e CO", "Garantie : art. 257e CO", "Depot: Art. 257e OR", "Deposito: art. 257e CO", "Deposit: art. 257e DO"),
      pick("No court fee is charged in conciliation proceedings; personal costs may remain", "La procédure de conciliation ne comporte pas de frais judiciaires ; des frais personnels peuvent subsister", "Im Schlichtungsverfahren werden keine Gerichtskosten erhoben; eigene Kosten können bleiben", "Nella procedura di conciliazione non sono riscosse spese giudiziarie; possono restare costi personali", "En la procedura da mediaziun na vegnan incassads nagins custs giudizials; agens custs pon restar"),
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
        "Prompt delivery to both creates clear proof of what each party received and when",
        "La remise rapide aux deux établit clairement ce que chaque partie a reçu et quand",
        "Rasche Zustellung an beide belegt klar, was jede Partei wann erhalten hat",
        "La consegna rapida a entrambi prova chiaramente cosa ha ricevuto ciascuna parte e quando",
        "La consegna svelta ad omadus cumprova cleramain tge che mintga partida ha survegnì e cura",
      ),
      pick(
        "Exit defects still require prompt, itemized and provable notice under Art. 267a CO",
        "Les défauts de sortie exigent toujours un avis rapide, détaillé et prouvable selon l'art. 267a CO",
        "Auszugsmängel verlangen weiterhin eine rasche, einzelne und nachweisbare Mitteilung nach Art. 267a OR",
        "I difetti di riconsegna richiedono sempre una notifica rapida, dettagliata e comprovabile secondo l'art. 267a CO",
        "Mancanzas da sortida pretendan vinavant ina notificaziun svelta, detagliada e cumprovabla tenor l'art. 267a DO",
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

  /**
   * The tips that replaced prose on the screens. Most of them carry a sentence that used to
   * sit under a heading in small type, moved behind the mark without being rewritten: the
   * words were right, the place was wrong.
   */

  fun tipAddress() = Tip(addressLookupHint)

  fun tipLegalOverlay() = Tip(
    pick(
      "The canton adapts a common Swiss handover record; it does not create a separate workflow.",
      "Le canton adapte un constat suisse commun; il ne crée pas un parcours séparé.",
      "Der Kanton ergänzt ein gemeinsames Schweizer Protokoll; er erzeugt keinen eigenen Ablauf.",
      "Il cantone adatta un verbale svizzero comune; non crea un percorso separato.",
      "Il chantun cumplettescha in protocol svizzer cuminaivel; el na creescha betg in agen process.",
    ),
    listOf(
      legalSnapshot,
      pick(
        "Swisstopo proposes the canton from the object address; the operator remains responsible for checking it.",
        "Swisstopo propose le canton depuis l'adresse de l'objet; l'opérateur doit le vérifier.",
        "Swisstopo schlägt den Kanton aus der Objektadresse vor; die prüfende Person bleibt verantwortlich.",
        "Swisstopo propone il cantone dall'indirizzo dell'oggetto; chi opera deve verificarlo.",
        "Swisstopo propona il chantun da l'adressa da l'object; la persuna responsabla sto controllar el.",
      ),
      pick(
        "This is a structured handover aid, not a legal opinion or a substitute for current official guidance.",
        "Il s'agit d'une aide structurée à la remise, non d'un avis juridique ni d'un remplacement des sources officielles actuelles.",
        "Dies ist eine strukturierte Übergabehilfe, keine Rechtsberatung und kein Ersatz für aktuelle amtliche Hinweise.",
        "È un ausilio strutturato alla consegna, non un parere legale né un sostituto delle fonti ufficiali aggiornate.",
        "Quai è in agid structurà per la surdada, betg in avis giuridic ni in remplazzament per infurmaziuns uffizialas actualas.",
      ),
    ),
  )

  fun tipDefect() = Tip(defectHint)

  fun tipNormalWear() = Tip(normalWearNote)

  fun tipFinalize() = Tip(finalizeLead)

  fun tipAmend() = Tip(amendLead)

  fun tipEntryNotice() = Tip(clockEntryBody)

  fun tipExitNotice() = Tip(clockExitBody, tipClocks().bullets)

  fun tipLocked() = Tip(
    pick(
      "This record is signed. Everything in it can be read; nothing in it can be changed.",
      "Ce constat est signé. Tout s'y lit ; rien ne s'y modifie.",
      "Dieses Protokoll ist unterzeichnet. Alles darin ist lesbar; nichts darin ist änderbar.",
      "Questo verbale è firmato. Tutto vi si legge; nulla vi si modifica.",
      "Quest protocol è suttascrit. Tut sa lascha leger; nagut sa lascha midar.",
    ),
    listOf(
      pick(
        "Its fingerprint was taken over exactly what it holds now",
        "Son empreinte a été prise sur exactement ce qu'il contient",
        "Sein Fingerabdruck wurde über genau das genommen, was es enthält",
        "La sua impronta è stata presa esattamente su ciò che contiene",
        "Sia impronta è vegnida prendida sur exactamain quai ch'el cuntegna",
      ),
      pick(
        "A correction is an amendment: a new draft, with the original left intact",
        "Une correction est un avenant : un nouveau brouillon, l'original restant intact",
        "Eine Korrektur ist ein Nachtrag: ein neuer Entwurf, das Original bleibt unberührt",
        "Una correzione è un'integrazione: una nuova bozza, l'originale resta intatto",
        "Ina correctura è in supplement: in nov sboz, l'original resta intact",
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
  val currentTerms = pick("Current terms", "Conditions actuelles", "Aktuelle Bedingungen", "Condizioni attuali", "Cundiziuns actualas")

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
        "Insurance: the capital stays free; premiums are normally non-refundable under current terms",
        "Assurance : le capital reste libre ; les primes sont en principe non remboursables selon les conditions actuelles",
        "Versicherung: Das Kapital bleibt frei; Prämien sind nach aktuellen Bedingungen in der Regel nicht rückzahlbar",
        "Assicurazione: il capitale resta libero; secondo le condizioni attuali i premi di regola non sono rimborsabili",
        "Assicuranza: il chapital resta liber; tenor las cundiziuns actualas na vegnan premias per regla betg restituidas",
      ),
      pick(
        "Insurance is no waiver: damages stay owed, the insurer pays and recovers from you",
        "L'assurance n'exonère pas : les dégâts restent dus, l'assureur paie puis se retourne contre vous",
        "Die Versicherung befreit nicht: Schäden bleiben geschuldet, der Versicherer zahlt und holt es bei Ihnen",
        "L'assicurazione non esonera: i danni restano dovuti, l'assicuratore paga e si rivale su di voi",
        "L'assicuranza na libera betg: donns restan debitads, l'assicuradur paja e sa regressa tar Vus",
      ),
      pick(
        "Prices and conditions change; verify them on the provider's site before choosing",
        "Les prix et conditions changent ; vérifiez-les sur le site du prestataire avant de choisir",
        "Preise und Bedingungen ändern sich; vor der Wahl auf der Website des Anbieters prüfen",
        "Prezzi e condizioni cambiano; verificateli sul sito del fornitore prima di scegliere",
        "Pretschs e cundiziuns sa midan; controllai els sin la pagina dal purschider avant da tscherner",
      ),
    ),
  )

  // ── Role: which side of the threshold holds this phone ────────────────────────────────
  val startNewSession = pick(
    "NEW HANDOVER",
    "NOUVEL ÉTAT DES LIEUX",
    "NEUE ÜBERGABE",
    "NUOVA CONSEGNA",
    "NOVA SURDADA",
  )
  val allSessions = pick(
    "SAVED HANDOVERS",
    "MES ÉTATS DES LIEUX",
    "GESPEICHERTE PROTOKOLLE",
    "VERBALI SALVATI",
    "PROTOCOLS MEMORISADS",
  )
  val dataAndPrivacy = pick(
    "DATA, BACKUP & PRIVACY",
    "DONNÉES, SAUVEGARDE ET CONFIDENTIALITÉ",
    "DATEN, SICHERUNG UND DATENSCHUTZ",
    "DATI, BACKUP E PRIVACY",
    "DATAS, SEGIREZZA E PROTECZIUN",
  )
  val privacyTitle = pick(
    "Privacy information",
    "Information sur la protection des données",
    "Datenschutzinformation",
    "Informativa sulla privacy",
    "Infurmaziun davart la protecziun da datas",
  )
  val privacyParagraphs = listOf(
    pick(
      "The person or organisation using this installation decides why and how personal data is recorded and is the data controller. A professional operator must give participants its identity, contact details and any additional notice its processing requires.",
      "La personne ou l'organisation qui utilise cette installation décide pourquoi et comment les données personnelles sont consignées et en est le responsable du traitement. Un opérateur professionnel doit communiquer aux participants son identité, ses coordonnées et toute information supplémentaire requise.",
      "Die Person oder Organisation, die diese Installation nutzt, bestimmt Zweck und Mittel der Bearbeitung und ist die Verantwortliche. Professionelle Betreiber müssen den Beteiligten ihre Identität, Kontaktdaten und alle zusätzlich erforderlichen Informationen mitteilen.",
      "La persona o organizzazione che usa questa installazione decide perché e come registrare i dati personali ed è il titolare del trattamento. Un operatore professionale deve fornire ai partecipanti identità, recapiti e ogni ulteriore informazione richiesta.",
      "La persuna u organisaziun che dovra questa installaziun decida pertge e co datas persunalas vegnan registradas ed è responsabla. In gestiunari professiunal sto communitgar identitad, contact e mintga ulteriura infurmaziun necessaria.",
    ),
    pick(
      "Protocols, photos and drawn signatures stay in this app's private storage. Limen has no developer account or developer-operated server and sends no protocol data to the developer.",
      "Les états des lieux, photos et signatures dessinées restent dans l'espace privé de l'app. Limen n'a ni compte développeur ni serveur exploité par le développeur et ne lui transmet aucune donnée du constat.",
      "Protokolle, Fotos und gezeichnete Unterschriften bleiben im privaten App-Speicher. Limen hat kein Entwicklerkonto und keinen vom Entwickler betriebenen Server; Protokolldaten gehen nicht an den Entwickler.",
      "Verbali, foto e firme disegnate restano nello spazio privato dell'app. Limen non dispone di un account o server gestito dallo sviluppatore e non gli invia dati del verbale.",
      "Protocols, fotos e suttascripziuns dissegnadas restan en la memoria privata da l'app. Limen n'ha nagin conto u server dal sviluppader e na trametta naginas datas dal protocol al sviluppader.",
    ),
    pick(
      "When you request an official Swiss address suggestion, the typed search text and ordinary network metadata go to the federal swisstopo service. Opening provider links sends data under that website's own terms.",
      "Lorsque vous demandez une suggestion d'adresse suisse officielle, le texte saisi et les métadonnées réseau usuelles sont transmis au service fédéral swisstopo. L'ouverture d'un lien de prestataire relève des conditions de ce site.",
      "Bei einer amtlichen Schweizer Adresssuche gehen der eingegebene Suchtext und übliche Netzwerkdaten an den Bundesdienst swisstopo. Für geöffnete Anbieterlinks gelten die Bedingungen der jeweiligen Website.",
      "Quando richiedete un suggerimento ufficiale per un indirizzo svizzero, il testo digitato e i normali metadati di rete sono inviati al servizio federale swisstopo. Ai link dei fornitori si applicano le condizioni del relativo sito.",
      "Tar ina dumonda d'ina adressa svizra uffiziala vegnan il text endatà e metadatas da rait usitadas tramess al servetsch federal swisstopo. Per links da purschiders valan lur atgnas cundiziuns.",
    ),
    pick("Exported PDFs, HTML copies and backups are readable outside private app storage. A backup is encrypted only when you set a password; an explicitly chosen archive without a password is unencrypted.", "Les PDF, copies HTML et sauvegardes exportés sont lisibles hors du stockage privé de l’app. Une sauvegarde est chiffrée uniquement avec un mot de passe ; l’archive sans mot de passe, choisie explicitement, ne l’est pas.", "Exportierte PDFs, HTML-Kopien und Sicherungen sind ausserhalb des privaten App-Speichers lesbar. Nur mit Passwort wird eine Sicherung verschlüsselt; ein ausdrücklich gewähltes Archiv ohne Passwort bleibt unverschlüsselt.", "PDF, copie HTML e backup esportati sono leggibili fuori dallo spazio privato dell’app. Il backup è cifrato solo con una password; l’archivio senza password, scelto esplicitamente, non è cifrato.", "PDFs, copias HTML e segirezzas exportadas èn legiblas ordaifer la memoria privata da l’app. Ina segirezza è criptada mo cun pled-clav; in archiv senza pled-clav, tschernì explicitamain, n’è betg criptà."),
    pick(
      "Records remain until you delete them or remove app data. Use the device lock, keep copies only as long as needed, and direct access, correction or deletion requests to the person or organisation operating this installation.",
      "Les constats restent jusqu'à leur suppression ou l'effacement des données de l'app. Verrouillez l'appareil, ne gardez les copies que le temps nécessaire et adressez les demandes d'accès, de rectification ou d'effacement à l'exploitant de cette installation.",
      "Protokolle bleiben bis zur Löschung oder Entfernung der App-Daten. Gerätesperre verwenden, Kopien nur solange nötig aufbewahren und Auskunfts-, Berichtigungs- oder Löschbegehren an den Betreiber dieser Installation richten.",
      "I verbali restano finché li eliminate o cancellate i dati dell'app. Usate il blocco del dispositivo, conservate le copie solo quanto necessario e rivolgete le richieste di accesso, rettifica o cancellazione a chi gestisce questa installazione.",
      "Protocols restan fin che Vus stizzais els u las datas da l'app. Duvrai la bloccada dal telefon, conservai copias mo uschè ditg sco necessari e drizzai dumondas d'access, rectificaziun u stizzada al gestiunari da questa installaziun.",
    ),
  )
  val controllerTitle = pick(
    "Responsible data controller",
    "Responsable du traitement",
    "Verantwortliche Stelle",
    "Titolare del trattamento",
    "Post responsabel",
  )
  val controllerBody = pick(
    "Configure the professional operator shown on new handover records. Changes apply to new sessions; existing records keep their original controller.",
    "Configurez l'opérateur professionnel indiqué sur les nouveaux constats. Les modifications valent pour les nouvelles sessions; les constats existants gardent leur responsable initial.",
    "Konfigurieren Sie die professionelle Stelle für neue Protokolle. Änderungen gelten für neue Übergaben; bestehende Protokolle behalten ihre ursprüngliche Stelle.",
    "Configurate l'operatore professionale indicato nei nuovi verbali. Le modifiche valgono per le nuove sessioni; i verbali esistenti mantengono il titolare originale.",
    "Configurai il gestiunari professiunal per novs protocols. Midadas valan per novas sesiuns; protocols existents mantegnan il post original.",
  )
  val organisation = pick("Organisation", "Organisation", "Organisation", "Organizzazione", "Organisaziun")
  val controllerContact = pick(
    "Privacy contact",
    "Contact protection des données",
    "Datenschutzkontakt",
    "Contatto privacy",
    "Contact da protecziun da datas",
  )
  val backupTitle = pick("Backup and restore", "Sauvegarde et restauration", "Sichern und wiederherstellen", "Backup e ripristino", "Segirezza e restauraziun")
  val backupBody = pick(
    "Create a complete archive before changing phones or clearing app data. Restore validates evidence and imports only new protocol ids; existing records are never overwritten.",
    "Créez une archive complète avant de changer de téléphone ou d'effacer les données de l'app. La restauration vérifie les preuves et n'importe que de nouveaux identifiants ; aucun constat existant n'est écrasé.",
    "Vor einem Gerätewechsel oder Löschen der App-Daten eine vollständige Sicherung erstellen. Die Wiederherstellung prüft Belege und importiert nur neue Protokoll-IDs; bestehende Protokolle werden nie überschrieben.",
    "Create un archivio completo prima di cambiare telefono o cancellare i dati dell'app. Il ripristino verifica le prove e importa solo nuovi identificativi; i verbali esistenti non vengono mai sovrascritti.",
    "Creai in archiv cumplet avant da midar telefon u stizzar las datas da l'app. La restauraziun controllescha las cumprovas ed importescha mo novas IDs; protocols existents na vegnan mai remplazzads.",
  )
  val licencesTitle = pick(
    "Open-source licences",
    "Licences open source",
    "Open-Source-Lizenzen",
    "Licenze open source",
    "Licenzas open source",
  )
  val licencesBody = pick(
    "Limen is built on open-source libraries, and its cantonal flags come from Wikimedia Commons. The full notice ships inside the app.",
    "Limen repose sur des bibliothèques open source, et ses drapeaux cantonaux proviennent de Wikimedia Commons. L'avis complet est inclus dans l'app.",
    "Limen baut auf Open-Source-Bibliotheken auf, und die Kantonsfahnen stammen von Wikimedia Commons. Der vollständige Hinweis ist in der App enthalten.",
    "Limen si basa su librerie open source e le sue bandiere cantonali provengono da Wikimedia Commons. L'avviso completo è incluso nell'app.",
    "Limen sa basa sin bibliotecas open source, e sias bandieras chantunalas derivan da Wikimedia Commons. L'avis cumplet è cuntegnì en l'app.",
  )
  val openLicences = pick("LICENCES", "LICENCES", "LIZENZEN", "LICENZE", "LICENZAS")
  val licencesMissing = pick(
    "The notice is missing from this build.",
    "L'avis est absent de cette version.",
    "Der Hinweis fehlt in diesem Build.",
    "L'avviso manca in questa build.",
    "L'avis manca en questa versiun.",
  )
  val createBackup = pick("BACK UP", "SAUVEGARDER", "SICHERN", "SALVA", "SEGIRAR")
  val restoreBackup = pick("RESTORE", "RESTAURER", "WIEDERHERSTELLEN", "RIPRISTINA", "RESTAURAR")
  val protectBackup = pick("Protect backup", "Protéger la sauvegarde", "Sicherung schützen", "Proteggi backup", "Proteger la segirezza")
  val unlockBackup = pick("Unlock backup", "Déverrouiller la sauvegarde", "Sicherung entsperren", "Sblocca backup", "Debloccar la segirezza")
  val backupPassword = pick("Backup password", "Mot de passe", "Sicherungspasswort", "Password del backup", "Pled-clav da segirezza")
  val confirmBackupPassword = pick("Confirm password", "Confirmer le mot de passe", "Passwort bestätigen", "Conferma password", "Confermar il pled-clav")
  val backupPasswordBody = pick(
    "A password is optional. Without one, anyone who has the file can read every record in it. With one, Limen cannot recover it; keep it separately from the backup.",
    "Le mot de passe est facultatif. Sans lui, quiconque détient le fichier peut lire tous les constats qu'il contient. Avec, Limen ne peut pas le récupérer; conservez-le séparément de la sauvegarde.",
    "Ein Passwort ist freiwillig. Ohne Passwort kann jeder, der die Datei hat, jedes Protokoll darin lesen. Mit Passwort kann Limen es nicht wiederherstellen; getrennt von der Sicherung aufbewahren.",
    "La password è facoltativa. Senza, chiunque abbia il file può leggere ogni verbale che contiene. Con la password, Limen non può recuperarla; conservatela separatamente dal backup.",
    "Il pled-clav è facultativ. Senza pled-clav po mintgin che ha la datoteca leger tut ils protocols en ella. Cun pled-clav na po Limen betg al restabilir; al tegnair separà da la segirezza.",
  )
  val restorePasswordBody = pick(
    "Enter the password this backup was made with, or leave the field empty if it was made without one.",
    "Saisissez le mot de passe utilisé pour cette sauvegarde, ou laissez le champ vide si elle a été créée sans.",
    "Das Passwort dieser Sicherung eingeben oder das Feld leer lassen, wenn sie ohne Passwort erstellt wurde.",
    "Inserite la password di questo backup, oppure lasciate il campo vuoto se è stato creato senza.",
    "Endatai il pled-clav da questa segirezza u laschai vid il champ, sch'ella è vegnida creada senza.",
  )
  val working = pick("Working...", "Traitement...", "Wird verarbeitet...", "Elaborazione...", "Elavurar...")
  val archiveFailed = pick(
    "Backup operation failed. No existing record was changed.",
    "L'opération de sauvegarde a échoué. Aucun constat existant n'a été modifié.",
    "Sicherungsvorgang fehlgeschlagen. Kein bestehendes Protokoll wurde verändert.",
    "Operazione di backup non riuscita. Nessun verbale esistente è stato modificato.",
    "L'operaziun da segirezza n'è betg reussida. Nagin protocol existent è vegnì midà.",
  )
  fun archiveSaved(name: String) = pick(
    "Saved to Downloads/Limen as $name",
    "Enregistré dans Téléchargements/Limen sous $name",
    "Unter Downloads/Limen als $name gespeichert",
    "Salvato in Download/Limen come $name",
    "Memorisà en Downloads/Limen sco $name",
  )
  fun archiveSavedOpen(name: String) = pick(
    "Saved to Downloads/Limen as $name, without a password: anyone who has this file can read every record in it.",
    "Enregistré dans Téléchargements/Limen sous $name, sans mot de passe: quiconque détient ce fichier peut lire tous les constats qu'il contient.",
    "Unter Downloads/Limen als $name gespeichert, ohne Passwort: Wer diese Datei hat, kann jedes Protokoll darin lesen.",
    "Salvato in Download/Limen come $name, senza password: chiunque abbia questo file può leggere ogni verbale che contiene.",
    "Memorisà en Downloads/Limen sco $name, senza pled-clav: mintgin che ha questa datoteca po leger tut ils protocols en ella.",
  )
  fun archiveRestored(result: dev.peringer.limen.data.ArchiveImportResult) = pick(
    "Restored ${result.imported}; kept ${result.skippedExisting} existing; rejected ${result.rejected} invalid.",
    "Restaurés : ${result.imported} ; existants conservés : ${result.skippedExisting} ; invalides refusés : ${result.rejected}.",
    "Wiederhergestellt: ${result.imported}; bestehend belassen: ${result.skippedExisting}; ungültig abgelehnt: ${result.rejected}.",
    "Ripristinati: ${result.imported}; esistenti mantenuti: ${result.skippedExisting}; non validi rifiutati: ${result.rejected}.",
    "Restaurads: ${result.imported}; existents mantegnids: ${result.skippedExisting}; nunvalids refusads: ${result.rejected}.",
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
  val postalCode = pick("Postcode", "NPA", "PLZ", "NAP", "NPA")
  val city = pick("City", "Localité", "Ort", "Località", "Lieu")
  val phoneCountry = pick("Phone country / code", "Pays / indicatif téléphonique", "Telefonland / Vorwahl", "Paese / prefisso telefonico", "Pajais / code da telefon")
  val countryNoMatches = pick("No matching country", "Aucun pays correspondant", "Kein passendes Land", "Nessun paese corrispondente", "Nagin pajais adattà")
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
    "Keine Übergabeprotokolle auf diesem Telefon gespeichert.",
    "Nessuna sessione salvata su questo telefono.",
    "Nagina sesiun memorisada sin quest telefon.",
  )
  // One line under the mark, not a paragraph. What the app IS, in the same words the
  // store listing and the feature graphic use, so the phone and the listing agree.
  val welcomeLead = pick(
    "Swiss handover protocol",
    "État des lieux suisse",
    "Schweizer Übergabeprotokoll",
    "Verbale di consegna svizzero",
    "Protocol da surdada svizzer",
  )
  /** Said on the paper, because two signed originals are what both parties go home with. */
  val pdfTwoCopies = pick("Provide an identical copy to each party. Delivery is recorded separately.", "Remettre un exemplaire identique à chaque partie. La remise est consignée séparément.", "Jeder Partei eine gleichlautende Kopie übergeben. Die Zustellung wird gesondert erfasst.", "Consegnare una copia identica a ogni parte. La consegna è registrata separatamente.", "Surdar ina copia identica a mintga partida. La surdada vegn registrada separadamain.")

  val notAdvice = pick(
    "Practice values with their sources; not legal advice. No court fee is charged in conciliation proceedings, but your own advice, representation or other costs may remain.",
    "Valeurs de pratique avec leurs sources ; pas un conseil juridique. La procédure de conciliation ne comporte pas de frais judiciaires, mais vos frais de conseil, de représentation ou autres peuvent subsister.",
    "Praxiswerte mit ihren Quellen; keine Rechtsberatung. Im Schlichtungsverfahren werden keine Gerichtskosten erhoben; eigene Beratungs-, Vertretungs- oder andere Kosten können bleiben.",
    "Valori di pratica con le loro fonti; non è una consulenza legale. Nella procedura di conciliazione non sono riscosse spese giudiziarie; possono restare costi propri di consulenza, rappresentanza o altro.",
    "Valurs da pratica cun lur funtaunas; nagin cussegl giuridic. En la procedura da mediaziun na vegnan incassads nagins custs giudizials; agens custs da cussegliaziun, represchentanza u auters pon restar.",
  )
}
