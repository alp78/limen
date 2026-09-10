# Limen release notes for Google Play

One section per uploaded `versionCode`, in the five languages the listing is published in. Play caps a release note at 500 characters per language, and the counts below are against the text inside the fenced blocks.

Paste each block into the matching language on the release, or let `play_push.py` upload them with the bundle.

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
