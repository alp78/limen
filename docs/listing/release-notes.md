# Limen release notes for Google Play

One section per uploaded `versionCode`, in the five languages the listing is published in. Play caps a release note at 500 characters per language, and the counts below are against the text inside the fenced blocks.

Paste each block into the matching language on the release, or let `play_push.py` upload them with the bundle.

---

## versionCode 5, versionName 1.2.0 (prepared, not uploaded)

13 October 2026. Romansh native review remains pending. RULV means the Vaud rental rules, not rent-control periods. The federal BWO document is an overview of cantonal form duties, not a federal initial-rent form. Older notes below are historical.

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
