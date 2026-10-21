# Limen, release runbook for Google Play

The historical checks below were checked against the repository and the built artefact on 10 September 2026, at `versionCode 4` / `versionName 1.1.2`. Redo the checks after any commit that changes the app.

## Android 1.2.0 handoff, 13 October 2026

This release implements the audit remediation. Consult [the implementation ledger](../audits/2026-10-13/IMPLEMENTATION.md) for the build, test and signer results of commit `eb5c038`. Five fixes followed before upload, found by a review of that commit: an endless activity recreate on phones whose system language is outside the five, the HTML companion embedding photographs as lossless PNG, finalization decoding every photograph on the main thread, amount fields refusing `2'400.-`, and three serialized defaults left out of the stored JSON. With them, 102 unit tests and 16 instrumented tests pass on the Pixel 10.

The bundle, SHA-256 `3c311a2f63859b49a82db3dd220713b4ff14c054f6ac9449e1af12eaa9184952`, its R8 mapping and the five release notes went to the closed-testing track on 13 October 2026 through the Play Developer API, as a completed release replacing versionCode 4. Play accepted the bundle signed with the upload key `bd11a58669405d6a3bb58aa4055b654e22cd670105c3e3c19ab189c2e2b509d3`, which is the registration check; the installed Pixel app uses the same key.

## Android 1.2.1 (versionCode 6), 15 October 2026

The owner's review of 1.2.0 on the phone rejected its forms: a mid-page button reading a whole sentence, a bottom key naming the session kind, instruction text under fields, a switch and a disclosure for the swisstopo lookup, a data-controller block and a participant notice on the handover form, and a library card cut to "Adresse pas encore". Every control of that kind went: buttons carry an icon and one or two words at the bottom of their form, the lookup runs whenever the phone is online and the field stays free to type otherwise, the hub lists missing data as plain lines under a small label, and a draft's card offers FINALISER. The five privacy notices and the five app pages on peringer.ch were put back to describing an automatic lookup the same day. Bundle SHA-256 `b63a824497d59194d40fb8188d6baefcef997c45fa944945acf23fa8c0c8a7e8`, 102 unit tests, release lint 0 errors, installed on the Pixel 10 over 1.2.0 with its records kept. Uploaded to the closed-testing track the same day.

## Android 1.2.2 (versionCode 7), 15 October 2026

The same review, continued on the owner's own earlier copy: the explanatory sentences behind the masthead's info button on every step, the room picker's sentence and the signature step's "Saisissez toute réserve…" are gone, and the hub's bottom key "RELIRE LE CONSTAT" became a third door, CONSTAT, beside INSPECTION and DOSSIER, with the signature state as its readout. Bundle SHA-256 `4be82657bc3c418ab9c56809d2eafbb33f08fc71dc96a9be414b407138db563a`, 102 unit tests, release lint 0 errors, installed on the Pixel 10 over 1.2.1 with its records kept.

## Android 1.2.3 (versionCode 8), 21 October 2026

The photo annex becomes a photographic report, on the owner's review of the day: a contents page with each section's first page, one section per room opening on a new page under the room's name, then the supplied objects, the meters and anything unplaced, and every photograph in a frame with its legend: the room, the finding's number and words as the body prints them, the surface, who is held to it, which photograph of how many, when it was taken, and its file. No hash is printed for photographs or documents; the record's fingerprint and the LIMEN reference stay. The photographs print up to 1020 units wide or 1100 high, about 17 cm wide or 19 cm high, decoded at two pixels per unit, which is 300 dpi, and stored losslessly: each is written once as a PNG, which the HTML copy embeds and whose pixel stream replaces a marked 4 × 4 stand-in in the file PdfDocument wrote, so no bitmap stays in memory and a record of any size exports. Up to 1.2.2 a Pixel photograph was rasterized into a page image at 72 dpi because its Ultra HDR gain map went with the decoded bitmap; the decoder now drops the map. Backups, restores, photographs, supporting documents and record files lose every size cap; free storage is the only check left, made once per megabyte. Measured on the Pixel 10 with 30 camera originals: 33 pages, PDF 137 MB, HTML 182 MB, export 15.6 s, native heap peak 61 MB; 1.2.2 made 9 pages and 2.56 MB in 8.4 s with every photograph at 72 dpi. Bundle SHA-256 `03fcf5070da5703c4f221c9ec8ae18488b8efca92dfab976c84ffd0b03886637`, 116 unit tests, 18 of 18 instrumented on the Pixel, release lint 0 errors; an R8 build restored a 68 MB backup of the 30-photo record and exported it in French on the phone, the plates pixel-identical between PDF and HTML and readable by MuPDF, pypdf, PDFium and Ghostscript; installed on the Pixel 10 over 1.2.2 with its records kept. The bundle, its R8 mapping and the five release notes went to the closed-testing track on 21 October 2026 through the Play Developer API as a completed release replacing versionCode 7, on the owner's instruction; a fresh read shows 1.2.3 (8) completed on alpha.

The Pixel is a physical device containing user records. Install the matching-signer APK with `adb -s <serial> install -r <apk>` to preserve those records. Do not use the deploy script’s uninstall step on that phone. The audit tests use the separate `.uitest` package.

The legal snapshot, policy source updates and all five listings are prepared. Native Romansh review, Swiss tenancy review, privacy-page publication, refreshed store graphics and the live Console declarations are external release checks. Historic graphics and wording below do not describe the 1.2.0 interface.

## What is verified

| Check | Result |
|---|---|
| `applicationId` | `dev.peringer.limen` |
| `minSdk` / `targetSdk` | 29 / 36, current Play target floor 36 as of 31 August 2026 |
| Release signing | 4096-bit RSA, SHA384withRSA, `CN=Alexis Peringer, O=PERINGER, L=Lausanne, C=CH`, valid to 20 January 2054 |
| Upload keystore | `C:/Users/aperi/keys/limen-peringer-upload.jks`, alias `limen-upload`, outside the repo and matched by the `*.jks` ignore rule besides |
| APK signature | v2 scheme, `apksigner verify` passes |
| AAB signature | `jarsigner -verify` passes, self-signed with the upload key as Play expects |
| R8 | `isMinifyEnabled` and `isShrinkResources` both on |
| 16 KB page alignment | `zipalign -c -P 16` passes on all four `libandroidx.graphics.path.so` |
| Permissions | `INTERNET` only, plus the `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` AndroidX defines for itself and does not surface to the user |
| Backup and transfer | `allowBackup="false"` plus `data_extraction_rules.xml` excluding cloud backup and device transfer |
| Unit tests | 89 green |
| Instrumented tests | 4 green on a Pixel 10, Android 17 |
| Privacy policy | `https://peringer.ch/legal/limen/privacy/`, five languages, built from the peringer.ch repository |
| Third-party notices | `NOTICE` copied into the APK at build time and shown in the app under Data, backup and privacy |

## Devices this was checked on

| Device | Android | Result |
|---|---|---|
| Pixel 10 | 17, API 37 | release installs, launches, 4 instrumented tests green |
| Galaxy Tab Active 3, SM-T575 | 13, API 33 | release installs, launches, 4 instrumented tests green |

The tablet matters more than a second phone would. It is the oldest Android in reach and the only
large screen, so it is where `minSdk 29` stops being a number in a build file, and where a layout
written on a 1080 x 2424 phone has to prove itself. A handover on a rugged tablet is also a real
way to use this app rather than a hypothetical one.

The suite fails on the tablet with "No compose hierarchies found in the app" unless the screen is
awake and the animation scales are zero. That is the harness, not the app: the release build
launches and stays up on Android 13 with an empty crash buffer. `_scratch/DEPLOY.md` carries the
two commands.

## The account gate, which is the long pole

A personal Play developer account created on or after 13 November 2023 cannot publish an app to production until that app has run a closed test with **twelve testers opted in continuously for the preceding fourteen days**.

**The gate is per app, not per account.** Sixteen apps means sixteen closed tests. The saving grace is that the same twelve people can test every one of them, so the cost is recruiting a group once and reusing it, and an app's updates never need testers again once it has production access.

Fourteen days is the floor, not the estimate: the count is of testers opted in continuously, so a drop below twelve restarts it. Google's application form then asks how hard recruiting was, whether testers exercised every feature, and for a summary of the feedback and how it was collected. Twelve names that never opened the app can fail the application.

Start the closed test before the listing is perfect. The listing can be edited during the fourteen days; the clock cannot be started retroactively.

Limen has an advantage here that most of the portfolio does not: it takes two people to use it. A tester pair walking one real handover exercises rooms, defects, photographs, both signature planes, the PDF and the amendment in a single sitting, which is exactly the engagement Google asks about.

## Building the artefact

```powershell
cd C:\Users\aperi\DEV\ANDROID\apps\limen
.\gradlew clean bundleRelease
```

The bundle is written to `app\build\outputs\bundle\release\app-release.aab`. Play takes the `.aab`; the `.apk` in `_scratch\_BUILDS` is for the handset and is never uploaded.

Bump `versionCode` in `app\build.gradle.kts` before every upload. Play rejects a repeated `versionCode`, and the closed-test and production tracks share the numbering.

## What is still missing before submission

1. ~~**Store graphics.**~~ Done, and localized. Icon, five feature graphics, eight phone screenshots per language and eight tablet screenshots per language are in `docs/listing/graphics/`. Each language carries its own canton and its own names. `docs/listing/store-listing.md` names the layout and the upload order. The only shot not taken is a defect carrying a photograph, which needs a real photograph of a real mark and is thirty seconds with the phone.
2. **The closed test.** Twelve testers, fourteen continuous days, per the section above.
3. **Country selection.** Limen is Swiss to the bone: Swiss tenancy law, cantonal overlay, swisstopo addresses, and five languages of which one is Romansh. A worldwide listing invites installs it cannot serve. Switzerland, and arguably Liechtenstein, is the honest reach.
4. **Free or paid.** Play lets a paid app become free later and never the other way round, so this is decided once. Signet went paid at CHF 3.00 and carries the four obligations that follow, including a payments profile that is not instant to clear.
5. **Data safety form.** Answers are worked out in `docs/listing/data-safety.md`. The one non-obvious answer is that the app does collect data, because the swisstopo address lookup sends typed address text off the device.

## Two rough edges found while capturing the screenshots

Neither blocks submission. Both were found by walking a whole exit protocol on the emulator, from an empty install to a signed PDF.

~~**The review screen labels a phone number as an email.**~~ Fixed in `618ecab`, which split the controller row so the phone number carries its own label. The PDF was never affected, because [`PdfExport.kt:401`](../../app/src/main/java/dev/peringer/limen/data/PdfExport.kt#L401) adds the same values without that label.

**Historical issue, fixed in 1.2.0. Editing after signing previously invalidated ink without warning.** Signatures carry the `contentRevision` they signed, `isCurrent` compares it against the record's, and any `edit()` bumps that number, so a single character typed into the dossier silently returns both parties to `Not signed`. The integrity rule is right and should not change: ink attests to a specific version of the content, and keeping it across an edit would be the real defect. What is missing is the warning. Two people sign at the door, someone corrects a typo in the bank name, and the app quietly needs both signatures again with nothing said. A confirmation before the edit, naming what will be lost, would cost one dialog.

## The maintenance commitment publication creates

Limen states cantonal rules and prints them onto a legal document. The initial-rent-form table is pinned to its 2026 snapshot and the app says so on screen, which is honest while the app is a private tool and becomes a promise once it is on a store. Review the cantonal overlay and the snapshot year annually, and treat a change in the RULV framework or in the federal notice periods as a release trigger rather than a backlog item.

The same applies to the privacy page: it is versioned against the app, and its permission claims were checked against this build. If the network path ever gains a second service, the page moves before the release does.

## Content and policy notes

- Limen carries no advertising, no in-app purchases, no user-to-user communication and no user-generated public content. The content rating questionnaire is a row of "no" answers, and the expected outcome is Everyone / PEGI 3.
- The app gives structured guidance on Swiss tenancy law and says in five languages that it is not legal advice. Keep that statement in the store description as well as in the app, because the listing is where a reader forms the expectation.
- The cantonal coats of arms are public-domain files from Wikimedia Commons, recorded with their provenance in `app/src/main/assets/cantons/manifest.json` and credited in `NOTICE`. Copyright is not the only rule that governs them: the Coat of Arms Protection Act restricts using a cantonal arms as a badge of authority, which is why the app uses them to identify a canton and never as a seal on the document.
