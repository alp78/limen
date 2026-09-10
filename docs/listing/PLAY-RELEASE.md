# Limen, release runbook for Google Play

Everything checked below was checked against the repository and the built artefact on 10 September 2026, at `versionCode 4` / `versionName 1.1.2`. Redo the checks after any commit that changes the app.

## What is verified

| Check | Result |
|---|---|
| `applicationId` | `dev.peringer.limen` |
| `minSdk` / `targetSdk` | 29 / 36, above Play's floor of 35 for new apps |
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

**Editing after signing discards the ink without warning.** Signatures carry the `contentRevision` they signed, `isCurrent` compares it against the record's, and any `edit()` bumps that number, so a single character typed into the dossier silently returns both parties to `Not signed`. The integrity rule is right and should not change: ink attests to a specific version of the content, and keeping it across an edit would be the real defect. What is missing is the warning. Two people sign at the door, someone corrects a typo in the bank name, and the app quietly needs both signatures again with nothing said. A confirmation before the edit, naming what will be lost, would cost one dialog.

## The maintenance commitment publication creates

Limen states cantonal rules and prints them onto a legal document. The initial-rent-form table is pinned to its 2026 snapshot and the app says so on screen, which is honest while the app is a private tool and becomes a promise once it is on a store. Review the cantonal overlay and the snapshot year annually, and treat a change in the RULV framework or in the federal notice periods as a release trigger rather than a backlog item.

The same applies to the privacy page: it is versioned against the app, and its permission claims were checked against this build. If the network path ever gains a second service, the page moves before the release does.

## Content and policy notes

- Limen carries no advertising, no in-app purchases, no user-to-user communication and no user-generated public content. The content rating questionnaire is a row of "no" answers, and the expected outcome is Everyone / PEGI 3.
- The app gives structured guidance on Swiss tenancy law and says in five languages that it is not legal advice. Keep that statement in the store description as well as in the app, because the listing is where a reader forms the expectation.
- The cantonal coats of arms are public-domain files from Wikimedia Commons, recorded with their provenance in `app/src/main/assets/cantons/manifest.json` and credited in `NOTICE`. Copyright is not the only rule that governs them: the Coat of Arms Protection Act restricts using a cantonal arms as a badge of authority, which is why the app uses them to identify a canton and never as a seal on the document.
