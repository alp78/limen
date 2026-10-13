# Limen audit evidence

This evidence accompanies [the audit report](REPORT.md) for commit `95b42314edeb2ea170f9f356bf941400a59775cd`, inspected on 13 October 2026. All handover names, descriptions and drawn signatures in the attached samples are synthetic test data. A test signature is not a person's legal signature.

## Build and device provenance

The connected device was the user's **Pixel 10, Android 17, API 37**, 1080 × 2424 pixels at 420 dpi. Initial font scale was 1.15. The 2.0 scaling run restored 1.15 in a `finally` block. The initially started dedicated emulator was stopped when the user requested the Pixel; none of the reported functional test results are attributed to that emulator.

The release package `dev.peringer.limen` was already installed. Audit work installed the separate `dev.peringer.limen.uitest` and instrumentation package; only synthetic data in that test package was cleared by tests. The release was rebuilt for verification, but the user's installed release was not replaced or uninstalled. After preserving the evidence, the two temporary packages, two audit-owned PDF downloads and temporary screenshot/UI-dump files were removed. The remaining installed Limen reports version 1.1.2/code 4, and font scale is restored to 1.15.

| Artifact | SHA-256 |
| --- | --- |
| Installed audit build, `app/build/outputs/apk/debug/app-debug.apk` | `772E94C2E1FA9DA638147947F8D8CF38343B37F8E10110196B6A5F62F34627EB` |
| Rebuilt release, `app/build/outputs/apk/release/app-release.apk` | `8031E9F7929A68FE5106418FD21F11F73221BDED6EF2736D9E95E9290DA3F7DD` |
| Audit signing certificate | `5f11947f8ece4df3852c35ee81c643a8598dbdcdc25b0d9eadad8f07eff1b9e4` — Android debug key |
| Release signing certificate | `bd11a58669405d6a3bb58aa4055b654e22cd670105c3e3c19ab189c2e2b509d3` — configured external `limen-upload` key |

The release certificate's registration in Play Console was not checked. The APK is versionCode 4 / versionName 1.1.2, min SDK 29, target SDK 36. The audit build has a debug timestamp suffix. [verification.json](verification.json) records the machine-readable inventory and results.

## Commands and logs

Build/test commands executed from the repository:

```powershell
.\gradlew.bat --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
.\gradlew.bat --offline :app:testDebugUnitTest :app:noEmDash --rerun-tasks
.\gradlew.bat --offline :app:assembleRelease :app:lintRelease
```

Temporary probes were compiled with `assembleDebugAndroidTest` and run using the AndroidX runner on the explicitly selected Pixel. For reproduction, copy [AuditProbeTest.kt](evidence/AuditProbeTest.kt) temporarily into `app/src/androidTest/java/dev/peringer/limen/`, build the test APK, install it with the debug target, and run the named class. **It is restricted to the `.uitest` package.** The existing repository instrumentation suite clears that package's files/preferences; never retarget it to the real release package.

| Verification | Evidence | Result |
| --- | --- | --- |
| Fresh unit suite and repository style check | [Log](evidence/unit-tests-fresh.txt) | 90 unit tests passed; no-em-dash check passed |
| Release build and release lint | [Log](evidence/release-check.txt) | Passed; 0 lint errors, 21 warnings |
| ZIP alignment with 16 KB page option | [Log](evidence/zipalign.txt) | Passed; not a 16 KB runtime/ELF validation |
| Existing phone suite | [Log](evidence/instrumentation-original.txt) | 4 tests passed |
| New entry through visible controls | [Log](evidence/instrumentation-entry-ui.txt) | Passed, including two drawn strokes, finalization and PDF |
| Reservation/normal-wear and unreadable-photo probes | [Log](evidence/instrumentation-defects-final.txt) | 2 tests passed by reproducing the defects |
| Export/delivery race probe | [Log](evidence/instrumentation-export-race.txt) | Passed by reproducing loss of the receipt in memory and on disk |
| Locale/date/status and five-language screenshots | [Log](evidence/instrumentation-layout115-final.txt) | 2 tests passed at 115%; screenshots wait for window animations |
| Five-language screenshots at 200% | [Log](evidence/instrumentation-font200.txt) | Passed; settings restored |

The six probes are audit demonstrations, not a production regression suite: some assertions deliberately expect the defective behaviour. The preserved source also contains a separate cleanup helper, which deletes Downloads rows only when their owner is the isolated audit package. Early exploratory harness runs failed on ambiguous selectors or an icon-vs-text selector; these were corrected. The final logs above distinguish those harness corrections from app findings. The initial raw text-layout overflow signal also flagged harmless font-metric rounding and background nodes; the report does not use its counts as an accessibility score. Visual findings are based on inspected screenshots and implementation details.

## Selected screenshots

| Screenshot | What it shows |
| --- | --- |
| [German participants at 115%](evidence/De-parties-font1.15.png) | Add-participant labels truncated, including the next-tenant button reduced to “+” |
| [French property fields at 200%](evidence/Fr-dossier-font2.0.png) | Date value obscured and date-label layout collision |
| [French calendar](evidence/Fr-system-date-picker-font1.15.png) | English system calendar in the French app |
| [Invalid edited date](evidence/Fr-invalid-date-retains-old-date-font1.15.png) | Invalid field displayed while the model retains the older valid date |
| [Finalized with two refusals](evidence/Fr-refusals-described-as-signed-font1.15.png) | “Signed” read-only wording despite no signed outcome |
| [French room](evidence/Fr-room-font1.15.png) | Consistent visual identity, but prominent destructive controls |
| [Romansh supplied objects](evidence/Rm-objects-font1.15.png) | Translated inventory and the amount of screen space taken by inline actions |
| [Italian hub](evidence/It-hub-font1.15.png) | Italian workflow wording and hub structure |
| [German signatures at 200%](evidence/De-sign-font2.0.png) | Large-text signing layout |
| [English landscape pad](evidence/En-landscape-signature-font1.15.png) | Stable landscape signature layout with reachable actions |

## PDF samples

[Original suite sample with photograph](evidence/original-en.pdf), [entry completed through the UI](evidence/entry-ui-completed.pdf), and samples in [English](evidence/En-sample.pdf), [French](evidence/Fr-sample.pdf), [German](evidence/De-sample.pdf), [Italian](evidence/It-sample.pdf) and [Romansh](evidence/Rm-sample.pdf) are retained. The five language samples deliberately use the same recorded English free text, demonstrating that export-language selection does not translate participant statements.

The [unreadable-photo reproduction](evidence/corrupt-photo-omitted.pdf) contains “Photos: 1” without an annex. Its deliberately constructed finalized input passed the fingerprint check; this demonstrates the distinction between hashing bytes and validating their printable content.

The inspected PDFs have a 1240 × 1754-point MediaBox (approximately 437.44 × 618.77 mm), not A4. Catalogue keys `StructTreeRoot`, `MarkInfo`, `Lang` and `AcroForm` are absent. These observations are narrower than a full PDF/UA or PDF/A validator result.

## Source and result traceability

[Probe observations](evidence/probes.txt) record the reproduced behaviours. [SHA256SUMS.txt](SHA256SUMS.txt) covers the selected evidence files. The full screenshots, build reports, raw layout diagnostics and HTTP copies remain under `_scratch/audit-2026-10-13/`. The report's legal and market citations link to the source passages/pages checked during this audit. All five public policy routes returned HTTP 200; their live Italian typo and Romansh terminology issues were verified in the returned content as well as the local source.

No production source fixes, commits, pushes or public publication were performed. The temporary probe file was removed from the app source tree after testing; its reviewable copy is retained here.
