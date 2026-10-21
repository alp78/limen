# Limen release notes for Google Play

One section per uploaded `versionCode`, in the five languages the listing is published in. Play caps a release note at 500 characters per language, and the counts below are against the text inside the fenced blocks.

Paste each block into the matching language on the release, or let `play_push.py` upload them with the bundle.

---

## versionCode 11, versionName 1.2.6

21 October 2026. The exported record is drawn in the app’s own style: the Day palette read from one shared source, panels with rounded corners and a navy border, pills and chips, the system sans-serif in three weights and no monospace, a cover with the key facts and a contents list whose page numbers the export verifies against the drawn pages, one panel per room with its numbered findings inside, and the photo annex in the same panels. Independent facts that one line joined with a dot are now chips or rows. Plates, the stand-in swap and the content order are unchanged. The document is named after the address and the handover day, as the iPhone names it.

### en-US

```
The exported record now looks like the app: a cover with the property, the parties, the record’s status and a contents list, then one panel per room with its numbered defects, the keys and meters as the app shows them, and the photo annex in the same panels. Same colours, rounded corners and typeface as on screen, with the page number on every sheet. The file is named after the address and the handover date.
```

(411/500)

### fr-FR

```
Le constat exporté ressemble désormais à l’application : une page de garde avec l’objet, les parties, l’état du constat et un sommaire, puis un cadre par pièce avec ses défauts numérotés, les clés et les compteurs tels que l’application les présente, et l’annexe photos dans les mêmes cadres. Mêmes couleurs, coins arrondis et police qu’à l’écran, avec le numéro de page sur chaque feuille. Le fichier porte le nom de l’adresse et de la date de l’état des lieux.
```

(462/500)

### de-DE

```
Das exportierte Protokoll sieht jetzt aus wie die App: ein Deckblatt mit Mietobjekt, Parteien, Status des Protokolls und Inhaltsverzeichnis, dann ein Feld pro Raum mit seinen nummerierten Mängeln, Schlüssel und Zählerstände wie in der App, und der Fotoanhang in denselben Feldern. Gleiche Farben, abgerundete Ecken und Schrift wie auf dem Bildschirm, mit Seitenzahl auf jedem Blatt. Die Datei ist nach Adresse und Übergabedatum benannt.
```

(436/500)

### it-IT

```
Il verbale esportato ora ha l’aspetto dell’app: una copertina con l’oggetto, le parti, lo stato del verbale e un indice, poi un riquadro per locale con i suoi difetti numerati, chiavi e contatori come li mostra l’app, e l’allegato foto negli stessi riquadri. Stessi colori, angoli arrotondati e carattere dello schermo, con il numero di pagina su ogni foglio. Il file prende il nome dall’indirizzo e dalla data della consegna.
```

(426/500)

### rm

```
Il protocol exportà ha ussa l’aspect da l’app: ina pagina da cuverta cun l’object, las partidas, il stadi dal protocol ed in cuntegn, lura in champ per stanza cun sias mancanzas numeradas, clavs e contaturs sco l’app als mussa, e l’agiunta da fotos en ils medems champs. Las medemas colurs, chantuns arrundads e scrittira sco sin il visur, cun il numer da pagina sin mintga fegl. La datoteca ha il num da l’adressa e da la data da la surdada.
```

(442/500)

---
## versionCode 10, versionName 1.2.5

21 October 2026. Backups repack their JPEG photographs losslessly as JPEG XL through libjxl 0.12.0, built into the app: thirty camera originals make a 54.8 MB backup instead of 68.4 MB, and every photograph restores byte for byte. A backup with repacked photographs is archive version 2, which 1.2.4 and earlier refuse; one with none stays version 1.

### en-US

```
Backups are now about a fifth smaller: photographs are repacked losslessly as JPEG XL and restored byte for byte, so every evidence check still holds. A backup made with this version restores in Limen 1.2.5 or later; older backups restore as before.
```

(249/500)

### fr-FR

```
Les sauvegardes sont désormais environ un cinquième plus légères : les photos y sont recompressées sans perte au format JPEG XL et restaurées à l’octet près, si bien que chaque contrôle des preuves reste valable. Une sauvegarde faite avec cette version se restaure avec Limen 1.2.5 ou plus récent ; les anciennes sauvegardes se restaurent comme avant.
```

(351/500)

### de-DE

```
Sicherungen sind jetzt rund ein Fünftel kleiner: Die Fotos werden verlustfrei als JPEG XL neu verpackt und beim Wiederherstellen Byte für Byte zurückgewonnen, sodass jede Beweisprüfung gültig bleibt. Eine mit dieser Version erstellte Sicherung lässt sich mit Limen 1.2.5 oder neuer wiederherstellen; ältere Sicherungen wie bisher.
```

(330/500)

### it-IT

```
I backup sono ora più leggeri di circa un quinto: le foto vengono ricompresse senza perdita in JPEG XL e ripristinate byte per byte, così ogni verifica delle prove resta valida. Un backup creato con questa versione si ripristina con Limen 1.2.5 o successivo; i backup precedenti si ripristinano come prima.
```

(306/500)

### rm

```
Las copias da segirezza èn ussa var in tschintgavel pli pitschnas: las fotos vegnan cumprimidas senza perdita en il format JPEG XL e restabilidas byte per byte, uschia resta valaivla mintga controlla da las cumprovas. Ina copia da segirezza fatga cun questa versiun sa lascha restabilir cun Limen 1.2.5 u pli nov; copias pli veglias vegnan restabilidas sco fin ussa.
```

(366/500)

---

## versionCode 9, versionName 1.2.4

21 October 2026. The annex photographs go into the PDF as JPEG at 200 dpi, quality 85, instead of lossless at 300 dpi: a 30-photograph record makes 7.7 MB instead of 137 MB. Encrypted backups are sealed and opened a buffer at a time, so a backup larger than memory saves and restores.

### en-US

```
The PDF is now much lighter: annex photographs are stored at the resolution their printed size needs, so a handover with 30 photographs makes a PDF of about 8 MB instead of more than 100 MB, exported more than twice as fast. Backups of any size now save and restore reliably.
```

(275/500)

### fr-FR

```
Le PDF est désormais bien plus léger : les photos de l’annexe sont enregistrées à la résolution qu’exige leur taille d’impression, si bien qu’un état des lieux de 30 photos donne un PDF d’environ 8 Mo au lieu de plus de 100 Mo, exporté plus de deux fois plus vite. Les sauvegardes de toute taille s’enregistrent et se restaurent désormais de manière fiable.
```

(357/500)

### de-DE

```
Das PDF ist jetzt viel leichter: Die Fotos im Anhang werden in der Auflösung gespeichert, die ihre Druckgrösse braucht. Ein Protokoll mit 30 Fotos ergibt so ein PDF von etwa 8 MB statt über 100 MB und wird mehr als doppelt so schnell exportiert. Sicherungen jeder Grösse werden jetzt zuverlässig gespeichert und wiederhergestellt.
```

(330/500)

### it-IT

```
Il PDF è ora molto più leggero: le foto dell’allegato sono salvate alla risoluzione richiesta dalla loro dimensione di stampa, così un verbale con 30 foto produce un PDF di circa 8 MB invece di oltre 100 MB, esportato più di due volte più velocemente. I backup di qualsiasi dimensione ora si salvano e si ripristinano in modo affidabile.
```

(337/500)

### rm

```
Il PDF è ussa bler pli lev: las fotos da l’agiunta vegnan memorisadas en la resoluziun che lur grondezza da stampa dumonda, uschia dat in protocol cun 30 fotos in PDF da var 8 MB empè da passa 100 MB, exportà pli che dubel uschè svelt. Copias da segirezza da mintga grondezza vegnan ussa memorisadas e restabilidas a moda fidabla.
```

(330/500)

---

## versionCode 8, versionName 1.2.3

21 October 2026. The photo annex becomes a photographic report: a contents page, one section per room, each photograph framed and large, lossless at 300 dpi, with its context beside it and no hash. Backups, photographs and documents lose every size cap.

### en-US

```
The photo annex of the PDF is now organised by room: each room opens its own section under its name, and every photograph is shown large, in a frame, with its room, the finding it documents, who is held responsible and when it was taken. Photographs keep their full print quality, without any loss. Backups, photographs and documents no longer have a size limit.
```

(362/500)

### fr-FR

```
L’annexe photos du PDF est désormais organisée par pièce : chaque pièce ouvre sa propre section sous son nom, et chaque photo est présentée en grand, dans un cadre, avec sa pièce, le défaut qu’elle documente, la personne tenue pour responsable et le moment de la prise de vue. Les photos gardent leur pleine qualité d’impression, sans aucune perte. Les sauvegardes, les photos et les documents n’ont plus de limite de taille.
```

(425/500)

### de-DE

```
Der Fotoanhang des PDF ist jetzt nach Räumen geordnet: Jeder Raum beginnt einen eigenen Abschnitt unter seinem Namen, und jedes Foto erscheint gross, in einem Rahmen, mit seinem Raum, dem dokumentierten Mangel, der verantwortlichen Partei und dem Aufnahmezeitpunkt. Die Fotos behalten ihre volle Druckqualität, ohne jeden Verlust. Sicherungen, Fotos und Dokumente haben keine Grössenbeschränkung mehr.
```

(401/500)

### it-IT

```
L’allegato foto del PDF è ora organizzato per locale: ogni locale apre una propria sezione con il suo nome, e ogni foto è presentata in grande, in una cornice, con il suo locale, il difetto che documenta, chi ne è ritenuto responsabile e quando è stata scattata. Le foto mantengono la piena qualità di stampa, senza alcuna perdita. Backup, foto e documenti non hanno più limiti di dimensione.
```

(392/500)

### rm

```
L’agiunta da fotos dal PDF è ussa ordinada tenor local: mintga local avra ina atgna secziun sut ses num, e mintga foto vegn mussada grond, en in rom, cun ses local, la mancanza ch’ella documentescha, tgi ch’è responsabel e cura ch’ella è vegnida fatga. Las fotos mantegnan lur plaina qualitad da stampa, senza nagina perdita. Las copias da segirezza, las fotos e la documents n’han betg pli in limit da grondezza.
```

(413/500)

---

## versionCode 7, versionName 1.2.2

15 October 2026, the same day as versionCode 6. The explanatory sentences behind the masthead's info button and on the room picker and the signature step go, and the hub's bottom key becomes a third door.

### en-US

```
The handover overview, dossier, inspection and signature screens lose their explanatory sentences, and the record's review, signatures and export open from a door beside the other two instead of a key at the foot of the page.
```

(221/500)

### fr-FR

```
Les écrans de vue d’ensemble, de dossier, d’inspection et de signature perdent leurs phrases d’explication, et la relecture, les signatures et l’export s’ouvrent depuis une porte à côté des deux autres, au lieu d’un bouton en bas de page.
```

(238/500)

### de-DE

```
Übersicht, Dossier, Begehung und Unterschriften verlieren ihre Erklärungssätze; Durchsicht, Unterschriften und Export öffnen sich über eine Tür neben den beiden anderen statt über eine Taste am Seitenende.
```

(211/500)

### it-IT

```
Panoramica, dossier, sopralluogo e firme perdono le frasi esplicative; rilettura, firme ed esportazione si aprono da una porta accanto alle altre due, non più da un pulsante in fondo alla pagina.
```

(200/500)

### rm

```
La survista, il dossier, l’inspecziun e las suttascripziuns perdan lur frasas explicativas; la relectura, las suttascripziuns e l’export s’avran tras ina porta dasper las autras duas, betg pli tras in buttun al pe da la pagina.
```

(221/500)

---

## versionCode 6, versionName 1.2.1

15 October 2026. The forms of 1.2.0 carried sentence-long buttons, instruction text under fields, a switch for the address lookup and a data-controller block, all removed on the owner's review. Romansh native review remains pending.

### en-US

```
Simpler forms. Buttons are an icon and a word at the bottom of each form, instruction text is gone, and the address lookup runs by itself whenever the phone is online, with free typing when it is offline or nothing matches. The data-controller block and the participant notice no longer appear on the handover form.
```

(318/500)

### fr-FR

```
Formulaires simplifiés. Les boutons sont une icône et un mot au bas de chaque formulaire, les textes d’instruction ont disparu, et la recherche d’adresse se lance d’elle-même dès que le téléphone est en ligne, la saisie libre restant possible hors ligne ou sans correspondance. Le bloc du responsable du traitement et l’information aux participants ne figurent plus sur le formulaire d’état des lieux.
```

(396/500)

### de-DE

```
Einfachere Formulare. Schaltflächen sind ein Symbol und ein Wort am Ende jedes Formulars, Anleitungstexte sind weg, und die Adresssuche läuft von selbst, sobald das Telefon online ist; offline oder ohne Treffer bleibt die freie Eingabe. Der Block zur verantwortlichen Stelle und der Hinweis für Beteiligte erscheinen nicht mehr im Übergabeformular.
```

(343/500)

### it-IT

```
Moduli più semplici. I pulsanti sono un’icona e una parola in fondo a ogni modulo, i testi di istruzione sono spariti e la ricerca dell’indirizzo parte da sola appena il telefono è online, con inserimento libero se è offline o nulla corrisponde. Il blocco del titolare del trattamento e l’informativa per i partecipanti non compaiono più nel modulo di consegna.
```

(347/500)

### rm

```
Formulars pli simpels. Ils buttuns èn in simbol ed in pled a la fin da mintga formular, ils texts d’instrucziun èn davent, e la tschertga d’adressas cumenza da sasezza cur che il telefon è online; offline u senza correspundenza resta l’endataziun libra. Il bloc dal post responsabel e l’infurmaziun per participants na cumparan betg pli en il formular da surdada.
```

(350/500)

---

## versionCode 5, versionName 1.2.0

Uploaded to the closed-testing track on 13 October 2026 through the Play Developer API, as a completed release with these five notes, replacing versionCode 4. Romansh native review remains pending. RULV means the Vaud rental rules, not rent-control periods. The federal BWO document is an overview of cantonal form duties, not a federal initial-rent form. Older notes below are historical.

### en-US

```
Full document preview, A4 PDF and accessible HTML copies. Multiple tenants and named representatives, individual reservations and outcomes. Improved large-text layouts, room photos, entry comparison, meters, keys and attachments. Fixes preserve disputed claims and delivery receipts, validate dates and images, and add recovery tools. Dated cantonal checks and optional online address suggestions.
```

(397/500)

### fr-FR

```
Aperçu complet, PDF A4 et copie HTML accessible. Plusieurs locataires et représentants nommés, réserves et résultats individuels. Meilleure lisibilité en grands caractères, photos d’ensemble, comparaison entrée-sortie, compteurs, clés et pièces jointes. Corrections des prétentions contestées, reçus de remise, dates et images. Récupération des dossiers, contrôles cantonaux datés et recherche d’adresses facultative.
```

(417/500)

### de-DE

```
Vollständige Vorschau, A4-PDF und zugängliche HTML-Kopie. Mehrere Mietparteien und benannte Vertretungen mit eigenen Vorbehalten und Ergebnissen. Verbesserte grosse Schrift, Raumfotos, Einzugsvergleich, Zähler, Schlüssel und Beilagen. Korrekturen bei bestrittenen Ansprüchen, Zustellbelegen, Datumswerten und Bildern. Wiederherstellung, datierte kantonale Prüfungen und freiwillige Adresssuche.
```

(394/500)

### it-IT

```
Anteprima completa, PDF A4 e copia HTML accessibile. Più conduttori e rappresentanti nominati, riserve ed esiti individuali. Migliorati caratteri grandi, foto d’insieme, confronto con l’entrata, contatori, chiavi e allegati. Correzioni per pretese contestate, ricevute, date e immagini. Recupero dei verbali, controlli cantonali datati e ricerca indirizzi facoltativa.
```

(368/500)

### rm

```
Prevista cumpletta, PDF A4 e copia HTML accessibla. Plirs locataris e represchentants numnads cun atgnas resalvas e resultats. Meglra legibladad cun scrittira gronda, fotos generalas, cumparegliaziun cun l’entrada, quintaders, clavs e documents. Correcturas per pretensiuns contestadas, quittanzas, datas e maletgs. Recuperaziun, controllas chantunalas datadas e tschertga d’adressas facultativa.
```

(396/500)

---

## versionCode 4, versionName 1.1.2

One change since the first closed test. `versionCode 3` shipped with the exported protocol naming the Vaud RULV in every canton, which stated something untrue about the legal basis of a Bern, Ticino or Graubünden record.

### English (en-US)

```
Every exported protocol cited the Vaud rent-control periods, whatever canton the flat was in. A Vaud protocol still cites them, and a protocol from any other canton now names the federal initial-rent form on its own.

Please run a full handover in your own canton and report anything the record claims that does not match your practice.
```

(336 / 500)

### French (fr-FR)

```
Le constat exporté citait les périodes RULV vaudoises quel que soit le canton du logement. Un constat vaudois les cite toujours, et un constat d'un autre canton ne mentionne désormais que la formule fédérale OFL.

Effectuez un état des lieux complet dans votre canton et signalez tout ce que le constat affirme et qui ne correspond pas à votre pratique.
```

(353 / 500)

### German (de-DE)

```
Das exportierte Protokoll nannte die Waadtländer RULV-Perioden, in welchem Kanton die Wohnung auch lag. Ein Waadtländer Protokoll nennt sie weiterhin, und ein Protokoll aus einem anderen Kanton führt jetzt allein das eidgenössische Anfangsmietzinsformular an.

Führen Sie eine vollständige Übergabe in Ihrem Kanton durch und melden Sie alles, was das Protokoll behauptet und Ihrer Praxis nicht entspricht.
```

(405 / 500)

### Italian (it-IT)

```
Il verbale esportato citava i periodi RULV vodesi qualunque fosse il cantone dell'alloggio. Un verbale vodese continua a citarli, e un verbale di un altro cantone indica ora soltanto il modulo federale UFAB.

Eseguite una riconsegna completa nel vostro cantone e segnalate tutto ciò che il verbale afferma e che non corrisponde alla vostra pratica.
```

(348 / 500)

### Romansh (rm)

```
Il protocol exportà numnava las periodas RULV Vad, en tge chantun che l'abitaziun era. In protocol vadais las numna vinavant, ed in protocol d'in auter chantun inditgescha ussa be il formular federal UFL.

Exequi ina surdada cumpletta en Voss chantun e rapportai tut quai che il protocol pretenda e che na correspunda betg a Vossa pratica.
```

(339 / 500)

The Romansh here reuses the app's own vocabulary from `Strings.kt`: `RULV Vad`, `chantun`, `formular UFL`, `protocol`, `surdada`. It carries the same open risk as the rest of the Romansh copy, which no native speaker has read yet.

---

## versionCode 3, versionName 1.1.1

The first closed test. Its notes are on the release in Play and are not reproduced here, because they described a first build rather than a change.
