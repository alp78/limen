# Audit remediation

Android 1.2.0, versionCode 5. Implementation started 13 October 2026. The original audit is retained as the baseline. The app changes address all 28 numbered findings; external review and release activities are listed separately below.

## Work queue

- [x] F01–F05: independent claims, export merge, reservations, input validation, evidence decoding.
- [x] F06–F07: complete preview, participants and signing scope.
- [x] F08–F10: dated legal guidance, cantonal checks and privacy controls.
- [x] F11–F12: responsive accessibility and recoverable, ordered storage.
- [x] F13–F17: A4, photo viewer, entry comparison, room organisation and outcome wording.
- [x] F18–F21: reusable profile, issue navigation, explicit inspection scope, meters and access devices.
- [x] F22–F28: conditional estimates, legal snapshots, provenance, attachments, export history and library search.
- [x] Cross-cutting: five-language copy, modal focus, contrast, accessible companion, archive limits and documentation.
- [x] Verification: regression tests, Pixel flows, signed release, evidence and Git handoff.

Native-language review, professional legal sign-off, moderated usability studies and Play Console declarations require external validation. Code and documentation changes will distinguish implemented functionality from these validations.

## Disposition of the findings

| Finding | Implemented behavior |
| --- | --- |
| F01 | A normal-wear response preserves the landlord’s asserted claim and its visible cost/repair fields. Co-tenants can record individual positions. |
| F02 | Export completion merges into the latest record. A monotonic save sequence prevents older writes from winning; each exported copy records the delivery revision it actually rendered. |
| F03 | An explicitly empty reservation is retained and no longer falls back to the previous signature. Participant reservations are independent. |
| F04 | Date and time inputs retain invalid edits in the draft. Domain validation blocks finalization, and the field can restore its last valid value. Leaving a draft is allowed so the user is not trapped in a form. |
| F05 | Intake, finalization and export validate decoding and evidence hashes. Rendering fails visibly for a referenced image that cannot be decoded. Original files remain available for recovery. |
| F06 | The complete read-only preview uses the PDF’s content-selection/drawing pass. Signers see their names and scope; edits to signed content require confirmation naming affected signers. |
| F07 | Stable participant identifiers support multiple tenants, landlords, individual signatures, reservations, positions and delivery receipts. Contractual party, agency, attending representative and mandate have separate fields. Entry-to-exit seeding preserves co-tenant names and rechecks representative authority. |
| F08 | No automatic ten-day entry deadline or safe exit waiting period is presented. A contractual planning date needs its clause and period. Actual possession/key transfer and later findings/notifications have separate fields. Follow-up changes to finalized records use amendments. |
| F09 | All 26 cantons remain available; NE municipality/category and VD district decisions are implemented, with FR/GE/TI deposit checks, official sources, the conciliation directory and the TI expert option. Checks have individual notes and an unresolved outcome. See [legal rules](../../LEGAL-RULES.md). |
| F10 | Address lookup defaults to off with disclosure before activation; manual entry works offline. Backup encryption is stated accurately and plain export requires an explicit choice. Participant notices identify the recorded controller. The five canonical policy sources were corrected without publishing the site. |
| F11 | Essential controls wrap, participant actions use the available width, date/time/contact inputs stack, field labels sit outside the input border, and sticky-header offsets use measured height. |
| F12 | Save failures are visible and prevent abandoning unsaved work. Both first-save failure and later-save failure can be retried. Corrupt records remain listed, last-good recovery preserves damaged originals, and optional archive repair preserves corrupt files before replacing them. |
| F13 | PDF pages are 595 × 842 points, with larger body text. |
| F14 | Photographs open in a fit/zoom viewer with accessible controls and provenance. Delete controls are separate from the image. |
| F15 | Exit inspection exposes the entry’s full findings, notes, images and complete read-only document. Prior external reports can be attached. |
| F16 | Every room can be named, reordered and progressed to the next room. Destructive room actions are behind a separate menu. |
| F17 | Finalized and signed are distinguished. Refusal, absence and inability to draw have explicit outcomes. |
| F18 | A name can start a quick draft; contact details are optional and reusable. Agency names do not substitute for the contractual landlord. |
| F19 | Completion messages are actions leading to the relevant room, finding, signing step or dossier section. The hub initially shows the next check and can expand the remaining list. |
| F20 | Explicit no-deposit, unknown deposit, and uninspected/unavailable scope reasons are supported. Missing contract or guarantee paperwork can be explained without preventing a factual handover. Removing Keys requires a recorded explanation rather than bypassing the check. |
| F21 | Repeatable meters carry identifiers, units, readings, date/time, notes and images. Access devices carry identification, expected/actual counts and discrepancies. Working access codes are discouraged in shared identifiers. |
| F22 | The calculator is labelled as an indicative residual replacement value, not a debt or repair-cost allocation. Inputs, source and date are retained with the finding; invalid/nonfinite/negative input is rejected. |
| F23 | Changes to address, local category, legal dates or deposit facts invalidate the affected checks and explain that a review is needed. Editing deposit details preserves unrelated initial-rent and insurance confirmations; each unresolved check needs its own explanation. |
| F24 | New final records freeze rule results and source metadata; version 2026.1 has an explicit legacy resolver. Retained PDF/HTML files preserve exact copies. Historical cost signatures remain visible. |
| F25 | Gallery ingestion time is distinguished from capture time; every EXIF orientation, including mirror/transpose, is handled by the shared viewer/export decoder. |
| F26 | Classified PDF/image attachments have hashes, file sizes, intake timestamps, external references and an export manifest. They travel in scoped backups. |
| F27 | Export history survives reopening, retains exact private copies, and uses property/language/reference/delivery-revision filenames. PDF hashes detect accidental changes to retained copies; restored records never trust another device’s old MediaStore URI. |
| F28 | The library loads asynchronously, searches address/party/date, filters drafts/final records, groups by property and shows entry/amendment relationships. |

The accessible HTML companion has document language, headings, definition lists, escaped text and labelled embedded images. The Canvas PDF itself is not tagged. Modal editors isolate focus, headings and switches expose semantics, counts are contextual, warning contrast is darker and undo uses Android’s recommended accessibility timeout. Optional device-credential protection restricts the saved-record library and settings. It is not file encryption or an app-wide lock.

Archives are bounded to 64 MB compressed and 256 MB unpacked, with entry/count/JSON limits and authenticated decryption before installation. Scoped backups omit unreferenced evidence but include retained exports. Recovery data remains private until the record is deleted; it is not silently sent to a developer or support service.

## External validation and publication

The engineering changes are not a legal or accessibility certification. Swiss tenancy review, native Romansh review, moderated usability sessions, assistive-technology user testing, and a minimum-API physical-device check remain separate acceptance work. The proposed 90% unaided-completion target in the original audit has not been measured. No native reviewer or external translation provider was represented as having approved the revised copy.

The iPhone application was not changed or tested. Use the updated Android reader for archives containing the new participant, document and inspection fields until cross-platform compatibility has been verified. Existing Android files retain their default serialization and fingerprint compatibility; new fields must continue to respect that canonical format in future releases.

The five policy sources in `C:\Users\aperi\DEV\PERINGER\src\content\legal\limen` were updated and their route/source checks passed. Their [source diff](remediation/privacy-source.patch) is retained. The website was not deployed. Store descriptions and release notes are drafts; existing promotional screenshots need refreshing before a public release. No Play Console declarations, prices, countries, testing tracks or publication state were changed. The Data safety worksheet explicitly removes the unsupported automatic service-provider exemption.

The original market assessment remains a pilot recommendation for individual landlords, tenants and small professional operators. The changes improve the reliability and completeness of that offering; they do not establish market demand, willingness to pay, or readiness for agency-wide integration.

## Final verification and handoff

| Check | Result |
| --- | --- |
| Unit tests | **100 passed**, including cantonal/local rules, fingerprints, stale saves, corrupt-record recovery, scoped restore, co-tenants, date and financial validation |
| Pixel regression suite | **16 passed** on the connected Pixel 10, Android 17/API 37, using the isolated `.uitest` package |
| Visible workflow | Fresh entry through the visible controls, two drawn signatures, finalization and export passed |
| Additional device cases | Claims/reservations, invalid dates, corrupt evidence, export/receipt race, three named signers, save failure and first-save retry, record-bound asynchronous photos, all EXIF orientations, document/meter/photo backup and photo zoom controls passed |
| Language/size matrix | FR, EN, DE, IT and RM at 100%, 115%, 150% and 200%; date and participant-action screenshots inspected |
| PDF and accessible output | A4 595 × 842 points; both co-tenant reservations and the agent’s name present; matching HTML companion retained |
| Release checks | APK and AAB built; `noEmDash` and `git diff --check` passed; release lint **0 errors, 22 warnings** |
| APK signing | Configured non-debug production upload key, SHA-256 `bd11a58669405d6a3bb58aa4055b654e22cd670105c3e3c19ab189c2e2b509d3`; APK signature and ZIP alignment verified |
| Physical installation | Exact handoff APK installed using `adb install -r`; version **1.2.0 (5)**. Original first-install time remains **10 September 2026**. No production uninstall or data clear |
| Release smoke check | Cold launch succeeded in 131 ms; new-handover and privacy screens opened; no AndroidRuntime error in the inspected process log |
| Cleanup | QA-owned downloads and temporary packages removed; original font scale **1.15** restored; production Limen left available |
| Publication | No Git commit/push, Play upload, website deployment or messages to third parties |

The 22 lint warnings concern the existing launcher/vector assets, unused resources, modifier style, KTX/catalog preferences, the current target being below the newest OS, and a conservative free-space check. They are not represented as resolved. The AAB JAR signature verifies, with JDK warnings about the self-signed certificate, timestamp and ZIP/manifest reading order; no Play acceptance was tested. The APK is the artifact installed and smoke-checked on the Pixel.

Artifacts are `C:\Users\aperi\DEV\ANDROID\_scratch\_BUILDS\limen.apk` and `limen.aab`. [Machine-readable verification](remediation/verification.json), [Pixel results](remediation/pixel-tests.txt), [release build](remediation/release-build.txt), [APK signer](remediation/apk-signer.txt), [PDF check](remediation/pdf-check.json), [A4 sample](remediation/three-signers.pdf), and [accessible companion](remediation/three-signers.html) are retained. Screenshots include [French at 200%](remediation/fr-date-200.png), [German participant actions at 200%](remediation/de-participants-200.png), [completed entry](remediation/completed-entry.png), and [zoom controls](remediation/photo-zoom-controls.png).

Paste-ready commands, not executed:

```powershell
Set-Location C:\Users\aperi\DEV\ANDROID\apps\limen
git add -- app docs
git commit -m "Fix Limen audit findings and improve Swiss handovers"
git push origin main

Set-Location C:\Users\aperi\DEV\PERINGER
git add -- src/content/legal/limen
git commit -m "Update Limen privacy notices for Android 1.2"
git push origin main
```
