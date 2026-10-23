package dev.peringer.limen.ui

internal fun Str.words(en: String, fr: String, de: String, it: String, rm: String): String =
  when (lang) { Lang.En -> en; Lang.Fr -> fr; Lang.De -> de; Lang.It -> it; Lang.Rm -> rm }

val Str.preview get() = words("Preview", "Aperçu", "Vorschau", "Anteprima", "Prevista")
val Str.previewDraft get() = words("DRAFT · Not finalized", "BROUILLON · Non finalisé", "ENTWURF · Nicht abgeschlossen", "BOZZA · Non finalizzata", "SBOZ · Betg finalisà")
val Str.attachments get() = words("Supporting documents", "Pièces justificatives", "Beilagen", "Documenti giustificativi", "Documents cumprovants")
val Str.addDocument get() = words("Attach", "Joindre", "Beifügen", "Allega", "Agiuntar")
val Str.externalReference get() = words("Document reference / location", "Référence / emplacement du document", "Dokumentverweis / Ablage", "Riferimento / ubicazione del documento", "Referenza / lieu dal document")
val Str.authority get() = words("Representative's authority / mandate", "Pouvoir de représentation / mandat", "Vertretungsbefugnis / Vollmacht", "Potere di rappresentanza / mandato", "Dretg da represchentanza / mandat")
val Str.municipality get() = words("Official municipality", "Commune officielle", "Politische Gemeinde", "Comune ufficiale", "Vischnanca uffiziala")
val Str.district get() = words("District", "District", "Bezirk", "Distretto", "District")
val Str.habitableRooms get() = words("Habitable rooms in the lease (half rooms excluded)", "Pièces habitables du bail (demi-pièces exclues)", "Wohnräume laut Mietvertrag (halbe Zimmer nicht gezählt)", "Locali abitabili del contratto (mezzi locali esclusi)", "Stanzas abitabels tenor contract (senza mezs locals)")
val Str.toVerify get() = words("To verify", "À vérifier", "Zu prüfen", "Da verificare", "Da controllar")
val Str.sourceLabel get() = words("Official source", "Source officielle", "Amtliche Quelle", "Fonte ufficiale", "Funtauna uffiziala")
val Str.conciliation get() = words("Conciliation authority", "Autorité de conciliation", "Schlichtungsbehörde", "Autorità di conciliazione", "Autoritad da conciliaziun")
val Str.actualTransfer get() = words("Actual transfer of possession / keys", "Remise effective de la possession / des clés", "Tatsächliche Besitz- / Schlüsselübergabe", "Consegna effettiva del possesso / delle chiavi", "Surdada effectiva da la possessiun / da las clavs")
val Str.notificationClause get() = words("Notification clause and agreed number of days", "Clause de notification et délai convenu en jours", "Meldeklausel und vereinbarte Anzahl Tage", "Clausola di notifica e giorni concordati", "Clausula d’annunzia e dumber da dis convenids")
val Str.followUps get() = words("Follow-up and later findings", "Suivi et constats ultérieurs", "Nachverfolgung und spätere Feststellungen", "Seguito e rilievi successivi", "Seguiment e constataziuns posteriuras")
val Str.discoveredOn get() = words("Discovered on", "Constaté le", "Festgestellt am", "Scoperto il", "Constatà ils")
val Str.notifiedOn get() = words("Notified on", "Notifié le", "Mitgeteilt am", "Notificato il", "Annunzià ils")
val Str.scopeReason get() = words("Not inspected / not available / not applicable: specify reason", "Non inspecté / indisponible / non applicable : préciser le motif", "Nicht geprüft / nicht verfügbar / nicht anwendbar: Grund angeben", "Non ispezionato / non disponibile / non applicabile: indicare il motivo", "Betg inspectà / betg disponibel / betg applitgabel: inditgar il motiv")
val Str.reason get() = words("Reason", "Motif", "Grund", "Motivo", "Motiv")
val Str.identifier get() = words("Identifier (no access codes)", "Identifiant (sans code d’accès)", "Kennzeichnung (keine Zugangscodes)", "Identificativo (senza codici di accesso)", "Identificaziun (senza codes d’access)")
val Str.reading get() = words("Reading", "Relevé", "Zählerstand", "Lettura", "Lectura")
val Str.unit get() = words("Unit", "Unité", "Einheit", "Unità", "Unitad")
val Str.expected get() = words("Expected", "Attendu", "Erwartet", "Previsto", "Spetgà")
val Str.transferred get() = words("Transferred", "Remis", "Übergeben", "Consegnato", "Surdà")
val Str.accessDevices get() = words("Keys, fobs, cards and remotes", "Clés, badges, cartes et télécommandes", "Schlüssel, Anhänger, Karten und Fernbedienungen", "Chiavi, badge, tessere e telecomandi", "Clavs, badges, cartas e telecomandas")
val Str.overviewPhotos get() = words("Room overview photographs", "Photos d’ensemble de la pièce", "Übersichtsfotos des Raums", "Fotografie d’insieme del locale", "Fotografias generalas da la stanza")
val Str.roomActions get() = words("Room actions", "Actions sur la pièce", "Raumaktionen", "Azioni sul locale", "Acziuns per la stanza")
val Str.nextRoom get() = words("Next room", "Pièce suivante", "Nächster Raum", "Locale successivo", "Proxim local")
val Str.moveUp get() = words("Move up", "Déplacer vers le haut", "Nach oben", "Sposta in alto", "Spustar ensi")
val Str.moveDown get() = words("Move down", "Déplacer vers le bas", "Nach unten", "Sposta in basso", "Spustar engiu")
val Str.importedOn get() = words("Imported on", "Importé le", "Importiert am", "Importato il", "Importà ils")
val Str.unableToDraw get() = words("Cannot sign", "Signature impossible", "Unterschrift unmöglich", "Firma impossibile", "Suttascripziun impussibla")
val Str.absentOutcome get() = words("Absent", "Absent", "Abwesend", "Assente", "Absent")
val Str.saveFailed get() = words("Saving failed. Keep this record open and retry.", "Échec de l’enregistrement. Gardez ce dossier ouvert et réessayez.", "Speichern fehlgeschlagen. Protokoll offen lassen und erneut versuchen.", "Salvataggio fallito. Tenere aperto il verbale e riprovare.", "Memorisar n’è betg reussì. Laschar avert il protocol ed empruvar danovamain.")
val Str.retry get() = words("Retry", "Réessayer", "Erneut versuchen", "Riprova", "Empruvar danovamain")
val Str.recover get() = words("Recover", "Récupérer", "Wiederherstellen", "Ripristina", "Restabilir")
val Str.unreadableRecord get() = words("Unreadable record retained for recovery", "Dossier illisible conservé pour récupération", "Unlesbares Protokoll zur Wiederherstellung aufbewahrt", "Verbale illeggibile conservato per il recupero", "Protocol betg legibel conservà per la recuperaziun")
val Str.searchRecords get() = words("Search address, person or date", "Rechercher une adresse, personne ou date", "Adresse, Person oder Datum suchen", "Cerca indirizzo, persona o data", "Tschertgar adressa, persuna u data")
val Str.allRecords get() = words("All", "Tous", "Alle", "Tutti", "Tuts")
val Str.lookupFailed get() = words("Address lookup unavailable.", "Recherche d’adresse indisponible.", "Adresssuche nicht verfügbar.", "Ricerca indirizzi non disponibile.", "Tschertga d’adressas betg disponibla.")
val Str.estimateNotice get() = words("Indicative remaining replacement value only. This does not establish liability or a payment obligation. Repair, cleaning and loss of value require separate assessment.", "Estimation indicative de la valeur résiduelle de remplacement. Elle ne fixe ni responsabilité ni dette. Réparation, nettoyage et moins-value nécessitent une appréciation distincte.", "Unverbindlicher Restwert bei Ersatz. Keine Feststellung einer Haftung oder Zahlungspflicht. Reparatur, Reinigung und Minderwert sind gesondert zu beurteilen.", "Valore residuo di sostituzione indicativo. Non stabilisce responsabilità o obbligo di pagamento. Riparazione, pulizia e minor valore richiedono valutazioni separate.", "Valur restanta indicativa da remplazzament. Quai na fixescha nagina responsabladad u obligaziun da pajar. Reparatura, nettegiada e diminuziun da valur ston vegnir valitadas separadamain.")
val Str.translationNotice get() = words("Document labels are translated; entered statements remain in their original language.", "Les rubriques sont traduites ; les déclarations saisies restent dans leur langue d’origine.", "Rubriken werden übersetzt; erfasste Aussagen bleiben in ihrer Originalsprache.", "Le rubriche sono tradotte; le dichiarazioni inserite restano nella lingua originale.", "Las rubricas vegnan translatadas; las decleraziuns endatadas restan en lur lingua originala.")
val Str.understoodLanguage get() = words("Language understood by the participants", "Langue comprise par les participants", "Von den Beteiligten verstandene Sprache", "Lingua compresa dai partecipanti", "Lingua chapida dals participants")
val Str.accessibleCopy get() = words("HTML copy", "Copie HTML", "HTML-Kopie", "Copia HTML", "Copia HTML")
val Str.contents get() = words("Contents", "Sommaire", "Inhalt", "Indice", "Cuntegn")
val Str.continued get() = words("Continued", "Suite", "Fortsetzung", "Continua", "Cuntinuaziun")
val Str.costSignatureTitle get() = words("Historical cost signature", "Signature historique relative aux frais", "Historische Kostenunterschrift", "Firma storica relativa ai costi", "Suttascripziun istorica davart ils custs")
/** A room's defect count as its pill reads it, singular and plural in each language's own form. */
fun Str.defectCount(n: Int): String = when (lang) {
  Lang.En -> if (n == 1) "1 defect" else "$n defects"
  Lang.Fr -> if (n == 1) "1 défaut" else "$n défauts"
  Lang.De -> if (n == 1) "1 Mangel" else "$n Mängel"
  Lang.It -> if (n == 1) "1 difetto" else "$n difetti"
  Lang.Rm -> if (n == 1) "1 mancanza" else "$n mancanzas"
}
val Str.signatureEditWarning get() = words("Editing this record requires these people to review and sign again:", "La modification exige une nouvelle lecture et signature de ces personnes :", "Nach der Änderung müssen diese Personen erneut prüfen und unterschreiben:", "La modifica richiede una nuova verifica e firma di queste persone:", "La midada pretenda ina nova lectura e suttascripziun da questas persunas:")

fun Str.depositLocal(canton: dev.peringer.limen.data.SwissCanton?) = when (canton) {
  dev.peringer.limen.data.SwissCanton.GE -> words(
    "GE: the tenant chooses the bank. Cash or securities received by the landlord must be deposited within 10 days; the depositary issues two certificates. For residential leases, a simple guarantee is possible at the tenant’s request and can be replaced by a blocked deposit. Check the actual product and documents.",
    "GE : le locataire choisit la banque. Les espèces ou valeurs reçues par le bailleur doivent être déposées dans les 10 jours ; le dépositaire établit deux certificats. Pour le logement, le cautionnement simple est possible à la demande du locataire et peut être remplacé par un dépôt bloqué. Vérifiez le produit et les pièces.",
    "GE: Die Mieterschaft wählt die Bank. Erhaltenes Geld oder Wertpapiere sind innert 10 Tagen zu hinterlegen; die Hinterlegungsstelle erstellt zwei Bescheinigungen. Bei Wohnraum ist auf Wunsch eine einfache Bürgschaft möglich, ersetzbar durch ein gesperrtes Depot. Produkt und Belege prüfen.",
    "GE: il conduttore sceglie la banca. Contanti o valori ricevuti dal locatore vanno depositati entro 10 giorni; il depositario rilascia due certificati. Per l’abitazione è possibile una fideiussione semplice su richiesta del conduttore, sostituibile con un deposito bloccato. Verificare prodotto e documenti.",
    "GE: il locatari tscherna la banca. Daners u valurs retschavids dal locatur ston vegnir deponids entaifer 10 dis; il depositari emetta dus certificats. Per abitaziuns è ina cauziun simpla pussaivla sin dumonda dal locatari, remplazzabla cun in deposit bloccà. Controllar il product ed ils documents.")
  dev.peringer.limen.data.SwissCanton.FR, dev.peringer.limen.data.SwissCanton.TI -> words(
    "FR/TI: cash or securities received by the landlord or representative must be deposited within 10 days in the tenant’s name at an eligible bank with a seat or branch in the canton. TI additionally requires a receipt in two copies. These checks do not certify insurance products.",
    "FR/TI : les espèces ou valeurs reçues par le bailleur ou son représentant doivent être déposées dans les 10 jours au nom du locataire, auprès d’une banque admise ayant un siège ou une succursale dans le canton. TI exige aussi un reçu en deux exemplaires. Ces contrôles ne certifient pas les produits d’assurance.",
    "FR/TI: Erhaltenes Geld oder Wertpapiere sind innert 10 Tagen auf den Namen der Mieterschaft bei einer zugelassenen Bank mit Sitz oder Niederlassung im Kanton zu hinterlegen. TI verlangt zusätzlich einen Beleg in zwei Exemplaren. Versicherungsprodukte werden damit nicht zertifiziert.",
    "FR/TI: contanti o valori ricevuti dal locatore o rappresentante vanno depositati entro 10 giorni a nome del conduttore presso una banca ammessa con sede o succursale nel cantone. TI richiede anche una ricevuta in due esemplari. Questi controlli non certificano i prodotti assicurativi.",
    "FR/TI: daners u valurs retschavids dal locatur u represchentant ston vegnir deponids entaifer 10 dis en num dal locatari tar ina banca admessa cun sedia u filiala en il chantun. TI pretenda er ina quittanza en dus exemplars. Questas controllas na certifitgeschan betg products d’assicuranza.")
  else -> ""
}

/** What a backup that failed tells the person: the reason when it was refused, the general sentence otherwise. */
fun Str.archiveFailure(refused: dev.peringer.limen.data.ArchiveRefused?, wholeLibrary: Boolean): String = if (refused == null) archiveFailed else when (refused.reason) {
  dev.peringer.limen.data.ArchiveRefused.Reason.UnreadableRecord -> words(
    "Backup refused: a record cannot be read, so a full backup would leave it out. Back up the other records one at a time from their Export step, or restore an earlier backup with the option Repair unreadable records.",
    "Sauvegarde refusée : un constat est illisible, une sauvegarde complète l’omettrait. Sauvegardez les autres constats un par un depuis leur étape Export, ou restaurez une sauvegarde antérieure avec l’option Réparer les constats illisibles.",
    "Sicherung abgelehnt: Ein Protokoll ist unlesbar, eine vollständige Sicherung würde es auslassen. Die übrigen Protokolle einzeln im Schritt Export sichern oder eine frühere Sicherung mit der Option Unlesbare Protokolle reparieren wiederherstellen.",
    "Backup rifiutato: un verbale è illeggibile e un backup completo lo ometterebbe. Salvate gli altri verbali uno alla volta dalla fase Esporta, oppure ripristinate un backup precedente con l’opzione Ripara i verbali illeggibili.",
    "Segirezza refusada: in protocol n’è betg legibel e ina segirezza cumpletta al laschass ora. Segirar ils auters protocols in per in en la fasa Export u restaurar ina segirezza pli veglia cun l’opziun Reparar protocols illegibels.")
  // A full backup is refused for one record, so the way to keep the others safe is named, as for an unreadable one.
  dev.peringer.limen.data.ArchiveRefused.Reason.DamagedEvidence -> words(
    "Backup refused for ${refused.record}: evidence changed or is missing, so a restore would reject the record. No backup was saved." + if (wholeLibrary) " Back up the other records one at a time from their Export step." else "",
    "Sauvegarde refusée pour ${refused.record} : une preuve a changé ou manque, une restauration rejetterait ce constat. Aucune sauvegarde n’a été enregistrée." + if (wholeLibrary) " Sauvegardez les autres constats un par un depuis leur étape Export." else "",
    "Sicherung abgelehnt für ${refused.record}: Belege wurden verändert oder fehlen, eine Wiederherstellung würde dieses Protokoll abweisen. Es wurde keine Sicherung gespeichert." + if (wholeLibrary) " Die übrigen Protokolle einzeln im Schritt Export sichern." else "",
    "Backup rifiutato per ${refused.record}: una prova è cambiata o manca, un ripristino rifiuterebbe questo verbale. Nessun backup è stato salvato." + if (wholeLibrary) " Salvate gli altri verbali uno alla volta dalla fase Esporta." else "",
    "Segirezza refusada per ${refused.record}: ina cumprova è midada u manca, ina restauraziun refusass quest protocol. Nagina segirezza n’è vegnida memorisada." + if (wholeLibrary) " Segirar ils auters protocols in per in en la fasa Export." else "")
}
/** Under an unreadable record whose Recover key did not bring it back. */
val Str.recoverFailed get() = words("The record could not be recovered. Check the free space on the phone and try again.", "Le constat n’a pas pu être récupéré. Vérifiez l’espace libre sur le téléphone et réessayez.", "Das Protokoll konnte nicht wiederhergestellt werden. Prüfen Sie den freien Speicher und versuchen Sie es erneut.", "Non è stato possibile recuperare il verbale. Verificare lo spazio libero sul telefono e riprovare.", "Il protocol n’ha betg pudì vegnir recuperà. Controllai il spazi liber sin il telefon ed empruvai anc ina giada.")
/** The printed counterpart of [fixDeadline], which is an input prompt and reads "By (date)" in English. */
val Str.repairDeadline get() = words("Repair deadline", "Délai de réparation", "Frist zur Behebung", "Termine di riparazione", "Termin da reparaziun")
/** The printed counterpart of [identifier], whose parenthesis tells the person typing not to enter access codes. */
val Str.meterIdentifier get() = words("Identifier", "Identifiant", "Kennzeichnung", "Identificativo", "Identificaziun")
/** The printed heading of the reasons, without the instruction [scopeReason] gives the person typing them. */
val Str.notInspectedReasons get() = words("Not inspected / not available / not applicable", "Non inspecté / indisponible / non applicable", "Nicht geprüft / nicht verfügbar / nicht anwendbar", "Non ispezionato / non disponibile / non applicabile", "Betg inspectà / betg disponibel / betg applitgabel")
/** When the preview itself failed, as opposed to a photograph or signature file that cannot be rendered. */
val Str.previewFailed get() = words("The preview could not be shown. This does not change the record.", "L’aperçu n’a pas pu être affiché. Le constat n’est pas modifié.", "Die Vorschau konnte nicht angezeigt werden. Das Protokoll bleibt unverändert.", "Non è stato possibile mostrare l’anteprima. Il verbale non è stato modificato.", "La prevista n’ha betg pudì vegnir mussada. Il protocol n’è betg vegnì midà.")
val Str.photoNotAdded get() = words("The photograph could not be read or stored. Check the free space on the phone and try again.", "La photo n’a pas pu être lue ou enregistrée. Vérifiez l’espace libre sur le téléphone et réessayez.", "Das Foto konnte nicht gelesen oder gespeichert werden. Prüfen Sie den freien Speicher und versuchen Sie es erneut.", "Non è stato possibile leggere o salvare la foto. Verificare lo spazio libero sul telefono e riprovare.", "La foto n’ha betg pudì vegnir legida u memorisada. Controllai il spazi liber sin il telefon ed empruvai anc ina giada.")
val Str.documentNotAdded get() = words("The document could not be read or stored. Check the format (PDF, JPEG or PNG) and the free space on the phone, then try again.", "Le document n’a pas pu être lu ou enregistré. Vérifiez le format (PDF, JPEG ou PNG) et l’espace libre sur le téléphone, puis réessayez.", "Das Dokument konnte nicht gelesen oder gespeichert werden. Prüfen Sie das Format (PDF, JPEG oder PNG) und den freien Speicher und versuchen Sie es erneut.", "Non è stato possibile leggere o salvare il documento. Verificare il formato (PDF, JPEG o PNG) e lo spazio libero sul telefono, poi riprovare.", "Il document n’ha betg pudì vegnir legì u memorisà. Controllai il format (PDF, JPEG u PNG) ed il spazi liber sin il telefon ed empruvai anc ina giada.")
val Str.evidenceAfterFinalized get() = words("The record was finalized before the file was stored, so the file was not added.", "Le constat a été finalisé avant l’enregistrement du fichier, qui n’a donc pas été ajouté.", "Das Protokoll wurde abgeschlossen, bevor die Datei gespeichert war; sie wurde nicht hinzugefügt.", "Il verbale è stato finalizzato prima che il file fosse salvato, quindi il file non è stato aggiunto.", "Il protocol è vegnì finalisà avant che la datoteca era memorisada, perquai n’è ella betg vegnida agiuntada.")
val Str.signatureNotSaved get() = words("The signature could not be saved. Check the free space on the phone and draw it again.", "La signature n’a pas pu être enregistrée. Vérifiez l’espace libre sur le téléphone et dessinez-la de nouveau.", "Die Unterschrift konnte nicht gespeichert werden. Prüfen Sie den freien Speicher und zeichnen Sie sie erneut.", "Non è stato possibile salvare la firma. Verificare lo spazio libero sul telefono e disegnarla di nuovo.", "La suttascripziun n’ha betg pudì vegnir memorisada. Controllai il spazi liber sin il telefon e dissegnai ella danovamain.")
val Str.signatureWaitsForEvidence get() = words("A photograph or document is still being stored. Wait for it to finish, then keep the signature.", "Une photo ou un document est encore en cours d’enregistrement. Attendez la fin, puis conservez la signature.", "Ein Foto oder Dokument wird noch gespeichert. Warten Sie, bis es fertig ist, und übernehmen Sie dann die Unterschrift.", "Una foto o un documento è ancora in fase di salvataggio. Attendete che finisca, poi conservate la firma.", "Ina foto u in document vegn anc memorisà. Spetgai fin ch’i è fatg e tegnai lura la suttascripziun.")
val Str.documentOpenFailed get() = words("No app could open this document.", "Aucune app ne peut ouvrir ce document.", "Keine App kann dieses Dokument öffnen.", "Nessuna app può aprire questo documento.", "Nagina app po avrir quest document.")
val Str.removedReading get() = words("Meter reading removed", "Relevé de compteur supprimé", "Zählerstand entfernt", "Lettura del contatore rimossa", "Lectura dal contatur stizzada")
val Str.removedAccessDevice get() = words("Key or access device removed", "Clé ou moyen d’accès supprimé", "Schlüssel oder Zutrittsmittel entfernt", "Chiave o dispositivo di accesso rimosso", "Clav u meds d’access stizzà")
val Str.removedDocument get() = words("Supporting document removed", "Pièce justificative supprimée", "Beilage entfernt", "Documento giustificativo rimosso", "Document cumprovant stizzà")
val Str.removedFollowUp get() = words("Follow-up removed", "Suivi supprimé", "Nachverfolgung entfernt", "Seguito rimosso", "Seguiment stizzà")
val Str.removedEstimate get() = words("Wear estimate removed", "Estimation de vétusté supprimée", "Zeitwertschätzung entfernt", "Stima della vetustà rimossa", "Stima da la valur stizzada")
val Str.removedParty get() = words("Party removed", "Partie supprimée", "Partei entfernt", "Parte rimossa", "Partida stizzada")
val Str.removedKeyKind get() = words("Kind of key removed", "Type de clé supprimé", "Schlüsselart entfernt", "Tipo di chiave rimosso", "Tip da clav stizzà")
/** On the card of an entry that a finalized amendment replaced, in place of the exit key. */
val Str.amendedEntryNote get() = words("A later amendment exists. Start the exit from the amendment.", "Un avenant plus récent existe. Lancez la sortie depuis l’avenant.", "Ein neuerer Nachtrag liegt vor. Den Auszug vom Nachtrag aus starten.", "Esiste un’integrazione più recente. Avviate la riconsegna dall’integrazione.", "I dat in supplement pli nov. Cumenzai la sortida dal supplement.")
/** Where a copy opened again from the export history is: the app's own, not Download/Limen. */
val Str.keptInApp get() = words("Kept in the app", "Conservé dans l’app", "In der App aufbewahrt", "Conservato nell’app", "Memorisà en l’app")
/** Appended to a line of the export history whose delivery state is older than the record's. */
val Str.copyBeforeDeliveryChange get() = words("before the latest delivery change", "avant la dernière modification de la remise", "vor der letzten Änderung der Zustellung", "prima dell’ultima modifica della consegna", "avant l’ultima midada da la consegna")
