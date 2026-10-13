# Limen application audit for Switzerland

Audit date: 13 October 2026. Android repository: `alp78/limen`, branch `main`, commit `95b42314edeb2ea170f9f356bf941400a59775cd`. App version: **1.1.2, versionCode 4**. Findings describe this revision; proposed fixes have not been applied to the application.

## Assessment

**Limen has a useful Swiss product proposition and a workable core, but it is not yet a sufficiently complete or dependable professional handover tool.** A straightforward handover works on the connected Pixel, including two signatures and PDF export. The strongest foundations are local storage, five interface/document languages, deliberate inspection confirmation, explicit tenant responses, amendments, and integrity checks over the saved record and evidence.

Several defects affect the accuracy of the record. Recording a tenant's normal-wear position removes the landlord's claim. A delivery confirmation can disappear during a repeated export. A stale signature reservation cannot be cleared normally. An invalid date edit can leave a different, older date on the saved record. An unreadable photograph can be counted as evidence while disappearing from the PDF. These behaviours undermine the app's purpose of recording an accurate account of what happened.

The restrained navy/grey palette, generous main controls and room-based inspection are pleasant and coherent. However, the app feels like a collection of forms once the user leaves the room walk. It asks for too much identity information before allowing useful work, gives destructive actions excessive visual prominence, hides important explanations behind information icons, and has inconsistent navigation cues. The final review does not expose all information that will be printed. These are substantial usability limitations at a handover where two people are waiting and disagreements may already exist.

Accessibility has sound foundations: scalable text, labelled icons, selection semantics on many controls, a large signature pad and a dark theme. Actual Pixel screenshots nevertheless show lost action labels at 115% text size and obscured date values at 200%. Custom overlays expose background controls in the semantics tree, and the PDF has no tagged structure. Accessibility should be part of release acceptance.

Cantonal coverage is partial. All 26 cantons can be selected and their bundled flag checksums match the manifest. The app's seven canton-wide initial-rent-form classifications and two conditional classifications agree with the checked 2026 federal overview. Coverage beyond those classifications is incomplete: there are no municipality/category decisions for Neuchâtel, no district decision for Vaud, no Fribourg/Geneva/Ticino deposit-specific checks, and no actionable local-authority directory.

The recommended release position is a **controlled pilot after the P1 defects below are fixed**, with Swiss tenancy review of the resulting legal wording. Broad claims of comprehensive cantonal coverage or legal security would go beyond the implementation and the evidence.

## Scope and verification

The audit covered the 43 production Kotlin files (12,819 lines), the data model, storage, imports, exports, legal rules, calculations, Compose screens, all five columns of `Strings.kt`, test coverage, Gradle configuration, manifest, permissions, backup rules, assets and signing documentation. Repository notes reviewed include `AGENTS.md`, `CLAUDE.md`, `NOTICE`, all five `docs/listing/*.md` files, `_scratch/README.md`, the August compliance handoff, the scratch Play descriptions/release notes and the screenshot notes. The five privacy-policy sources referenced by the repository were also read in the PERINGER repository; all five published privacy URLs returned HTTP 200.

| Check | Result and interpretation |
| --- | --- |
| Fresh unit suite | **90 tests, 0 failures, 0 errors**; rerun with `--rerun-tasks` |
| Debug build and Android test build | Passed |
| Minified release build | Passed; APK signature verified |
| Debug and release lint | **0 errors, 21 warnings each**; mostly resources/style, plus target/tooling warnings |
| Existing instrumentation suite | **4/4 passed** on the user's connected Pixel 10, Android 17/API 37 |
| Complete handover through visible controls | Passed: new entry, identity, property, canton, checks, parties, keys, room, defect, two drawn test signatures, finalization and PDF |
| Extra audit probes | Six probe methods passed individually after test-selector corrections; these reproduce defects as well as exercise successful paths |
| Languages and scaling | FR, EN, DE, IT and RM rendered on the Pixel at its 115% text setting and at 200%; screenshots inspected; the original 115% setting was restored |
| Other phone checks | French calendar, invalid date editing, dark theme and landscape signature pad |
| PDF examination | Original suite output plus five language samples; page geometry, extracted text, evidence omission and structure metadata checked |
| Cantonal assets | 26/26 present; recorded SHA-256 values match |
| Runtime installation | Separate `dev.peringer.limen.uitest` package, signed with the **Android debug key**; the installed release app and its records were preserved |

The existing actor matrix uses constructed records for several cases; it is not six complete user-entered workflows. The additional entry test does exercise the visible controls. Automated success is not a human usability score. There was no moderated tenant/landlord study, blind-user TalkBack session, penetration test, minimum-API physical-device test, or iOS implementation audit. Network-off operation, real camera capture under low-memory interruption, very large archives and actual delivery to another person remain acceptance tests. No external messages were sent.

The website describes an iPhone version, so this report does **not** infer that Limen is Android-only. Compatibility between the two platforms' archives and legal output needs a separate verification.

## Priority and evidence conventions

**P1** means fix before a broader professional release: loss/misstatement of evidence, misleading legal guidance or a barrier to a core task. **P2** means a material usability, completeness or robustness improvement. **P3** means maintenance or polish. “Reproduced” means exercised on the Pixel or in its generated output. “Source-confirmed” means the relevant implementation establishes the behaviour. “Risk” identifies a path that still needs fault-injection testing.

## Defects requiring priority fixes

### F01 Preserve both parties' positions on normal wear

**P1, reproduced.** In an exit defect, choose “Charged to the tenant”, enter a cost note, then select the tenant response “Normal wear”. The app sets `chargedToTenant=false` and hides the charge/repair/reservation panel. The previous cost note remains in the model and can still print. A tenant's assertion has therefore removed the opposing party's assertion, while leaving hidden information behind. Normal wear established as fact is not chargeable; a disagreement about normal wear must still be recordable.

Fix: keep the landlord's asserted claim and the tenant's response independent. Label the former as a claim, show the dispute clearly, and make any deletion explicit. Test both directions of the disagreement and ensure the review and PDF show the same facts. Evidence: [DefectEditor.kt, lines 107–150](../../../app/src/main/java/dev/peringer/limen/ui/DefectEditor.kt#L107), [audit probe results](evidence/probes.txt). The requirement to identify the defects for which liability is asserted is explained by the [Zurich courts](https://www.gerichte-zh.ch/de/themen/miete/kuendigung-rueckgabe/rueckgabe-und-ausweisung/maengelruege).

### F02 Do not overwrite delivery confirmations during export

**P1, reproduced in memory and on disk.** Export an already-exported finalized record again and add a delivery receipt while the PDF is being generated. Export completion saves the older captured `p` with a new export timestamp, replacing the receipt just entered. The receipt panel remains usable while export is running.

Fix: serialize record writes and merge export metadata into the latest persisted record. Track the delivery revision actually included in each PDF; comparing wall-clock timestamps is insufficient. A receipt added after the rendered snapshot must survive and keep the updated-copy warning active. Evidence: [LimenViewModel.kt, lines 1390–1431](../../../app/src/main/java/dev/peringer/limen/ui/LimenViewModel.kt#L1390), [export race test](evidence/instrumentation-export-race.txt).

### F03 Allow an old signature reservation to be cleared

**P1, reproduced.** When a signature becomes stale, its reservation appears in an editable field. Clearing the field removes the pending-map entry, which makes the UI fall back to the old signature's reservation. The old wording reappears and may be reused when signing again.

Fix: distinguish an explicitly empty replacement from “no replacement entered”. Keep an empty value until the new signature is captured, then clear the pending state. Test removing, replacing and retaining a reservation after another field changes. Evidence: [FlowSignExport.kt, line 160](../../../app/src/main/java/dev/peringer/limen/ui/FlowSignExport.kt#L160), [LimenViewModel.kt, lines 1293 and 1357](../../../app/src/main/java/dev/peringer/limen/ui/LimenViewModel.kt#L1357).

### F04 Resolve invalid date edits before continuing

**P1, reproduced.** Replacing a valid handover date with `31/02/2026` marks the field invalid, but the stored date remains `2026-10-13`. Completion checks still see the old valid value. Leaving the screen can discard the attempted correction, and the exported record uses the older date. The same design applies to time fields and optional dates.

Fix: retain input validity in the form state, block closing/finalization while a changed field is invalid, and offer an explicit revert. Validate parsed dates/times in the domain completion check as well as the widget, including restored legacy/imported drafts. Evidence: [DateField.kt, lines 52–84 and 132–159](../../../app/src/main/java/dev/peringer/limen/ui/DateField.kt#L52), [CompletionRules.kt, line 58](../../../app/src/main/java/dev/peringer/limen/ui/CompletionRules.kt#L58).

### F05 Fail visibly when evidence cannot be rendered

**P1, reproduced with a deliberately unreadable photo file.** A nonempty file can pass the evidence-presence and fingerprint checks although it cannot decode as an image. PDF export then silently omits it from the annex. The test PDF says “Photos: 1” and has no photo annex. Signature decoding has a comparable silent-omission path.

Fix: validate image decoding before accepting evidence and before finalization/export. Preserve the original file, identify the failed item and offer recovery; never silently issue a supposedly complete record with an omitted attachment. Distinguish a missing file, a corrupt image and an unsupported format. Evidence: [ProtocolIntegrity.kt](../../../app/src/main/java/dev/peringer/limen/data/ProtocolIntegrity.kt), [PdfExport.kt, lines 750–770 and 843–897](../../../app/src/main/java/dev/peringer/limen/data/PdfExport.kt#L843), [reproduced PDF](evidence/corrupt-photo-omitted.pdf).

### F06 Make the final review match what people are signing

**P1, source-confirmed and observed.** Review shows a two-line defect summary, its location, charge flag and response label. It does not directly show the complete description, defect reservation, tenant comment, cost note, repair deadline or photographs. Supplied items lose their notes and detailed evidence in this summary. The user must reopen individual editors to discover those details. The PDF is only available after finalization, and the signature-pad explanation narrows confirmation to correctly recorded defects even though the signed aggregate contains much more.

Fix: provide a complete, read-only document preview before signing, including all reservations and attachments. Use exactly the same content selection as export. Identify the signer's actual name and representative capacity above the pad, and use one consistent explanation of the signature's scope. Warn before an edit invalidates signatures, with affected signers named. Evidence: [FlowScreen.kt, lines 337–373 and 388–625](../../../app/src/main/java/dev/peringer/limen/ui/FlowScreen.kt#L337), [FlowSignExport.kt, lines 134–144](../../../app/src/main/java/dev/peringer/limen/ui/FlowSignExport.kt#L134), [LimenViewModel.kt, line 370](../../../app/src/main/java/dev/peringer/limen/ui/LimenViewModel.kt#L370).

### F07 Separate legal parties from operators and allow multiple signers

**P1, source-confirmed feature gap.** An agency session puts the agency in the landlord field and its employee in `representedBy`. The contractual landlord is not separately identified. A couple or flatshare cannot have individual tenant records, responses, reservations and signatures: signatures and reservations are keyed by `Role`, so a second tenant cannot be represented as a second tenant signer. The witness slot is not an appropriate workaround.

Fix: use stable participant IDs, support several tenants and landlords, and model the owner/contractual landlord, managing agency, attending person and proxy separately. Record the authority of a proxy without forcing an identity-document scan. Gate signatures by the persons expected to act, and capture the actual signer's name rather than the agency name. The [Aargau conciliation form](https://www.ag.ch/media/kanton-aargau/jb/dokumente/schlichtungsbehoerden/schlichtungsbehoerden-fuer-miete-und-pacht/schlichtungsformular-stand-25-11-2024.pdf) explicitly distinguishes the contractual party from a property manager acting as representative. Evidence: [Protocol.kt, lines 189–206 and 135](../../../app/src/main/java/dev/peringer/limen/data/Protocol.kt#L189), [LimenViewModel.kt, lines 599–612 and 1291–1306](../../../app/src/main/java/dev/peringer/limen/ui/LimenViewModel.kt#L599).

### F08 Do not present assumed dates as contractual deadlines

**P1, source-confirmed.** Every entry handover gets a computed “Contractual 10-day date”, even when the contract has no recorded notification period. The qualifications are hidden in a tooltip. Exit dates are computed from the handover date, without a separate actual key-return date and without cantonal/local holidays. The tooltip correctly says the law requires immediate notice and the dates are practice references; the main screen gives the calculated dates much greater prominence.

Fix: lead with prompt notification; calculate an entry period only after the applicable contractual clause has been recorded. Show any planning date's assumption beside it, including holiday limitations. Distinguish appointment, possession/key return, tenancy end and later discovery of a hidden defect. Link directly to the applicable source and provide a dated follow-up notice workflow. Evidence: [FlowDossier.kt, lines 347–367](../../../app/src/main/java/dev/peringer/limen/ui/FlowDossier.kt#L347), [Clocks.kt](../../../app/src/main/java/dev/peringer/limen/sense/Clocks.kt), [Zurich court guidance on immediate defect notices](https://www.gerichte-zh.ch/de/themen/miete/kuendigung-rueckgabe/rueckgabe-und-ausweisung/maengelruege). A practice target must never be represented as a safe statutory waiting period.

### F09 Complete the legal checks and make them actionable

**P1 for a comprehensive-Switzerland claim; source-confirmed gaps.** The rule set contains only six requirement types. It omits deposit-handling provisions in FR, GE and TI, and it cannot decide the local/category conditions in NE or VD. “Form checked and kept with the entry dossier” is a self-declaration; the app cannot attach that official form as a document. There are no official-source links in the legal panel, and an entry with an unknown lease conclusion date can satisfy its check with a generic confirmation.

Fix: implement the cantonal gaps described in the matrix below; keep explicit “unknown / to verify” outcomes; provide the official form/source and a document attachment or external-document reference. Record individual explanations and evidence against each check, instead of one shared note for all exceptions. Do not make missing paperwork prevent documenting a real, disputed handover, but do not label it legally verified. Evidence: [LegalOverlay.kt](../../../app/src/main/java/dev/peringer/limen/data/LegalOverlay.kt), [LegalOverlayPanel.kt](../../../app/src/main/java/dev/peringer/limen/ui/LegalOverlayPanel.kt).

### F10 Correct the privacy and backup statements

**P1, source-confirmed contradiction.** The in-app privacy text says new backups encrypt records/photos/signatures, while an empty password intentionally produces a plain ZIP-format `.limen` archive. The backup dialog does explain this; that does not make the earlier unconditional statement accurate. The published policies correctly describe optional encryption. Address queries also start automatically after typing, although the in-app notice says “when you request” a suggestion and gives no local-only switch.

Fix: use the same precise explanation in every language and surface it where the action occurs. Clearly distinguish an encrypted backup from a readable archive, with an explicit choice for the latter. Add a short address-lookup disclosure and a manual/offline option. Give participants a shareable operator notice before collecting their data, and verify the configured controller at session creation. Evidence: [Strings.kt, lines 1463–1475](../../../app/src/main/java/dev/peringer/limen/ui/Strings.kt#L1463), [ArchiveCrypto.kt, lines 29–34](../../../app/src/main/java/dev/peringer/limen/data/ArchiveCrypto.kt#L29), [published policy](https://peringer.ch/legal/limen/privacy/). The [FDPIC information guidance](https://www.edoeb.admin.ch/fr/devoir-dinformer) supports clear information about the controller, purpose and recipients; a generic developer notice cannot cover every agency's processing.

The Play Data safety worksheet also assumes that swisstopo qualifies for the service-provider sharing exception simply because it provides the lookup. Google's definition requires processing on the developer's behalf and instructions. Verify the actual terms and applicable exception before carrying that answer into Console; automatic lookup alone does not establish it. Check address/network metadata handling and retention, without claiming anonymity or ephemeral processing that has not been verified. [Google's current definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en). This is a declaration-review gap, not a finding that the submitted Console answer is known to be false.

### F11 Preserve action labels and field values at larger text sizes

**P1, reproduced.** At 115% text on the Pixel, the German participant row shows truncated witness/expert labels and only “+” for the next-tenant action. At 200%, the French handover-date field cannot visibly display its full date and its label collides with surrounding layout. Several controls constrain text to one line inside narrow equal-width rows. The app remains navigable by test selectors, but that is not a usable visual presentation.

Fix: let essential labels wrap, stack date/time and contact fields when necessary, and replace three equal participant buttons with full-width actions or an “Add participant” menu. Measure sticky-header height rather than using `160` physical pixels. Check all languages at 100%, 115%, 150% and 200%, with narrow portrait and landscape layouts. Evidence: [German participant screenshot](evidence/De-parties-font1.15.png), [French date screenshot at 200%](evidence/Fr-dossier-font2.0.png), [Components.kt, lines 457–558](../../../app/src/main/java/dev/peringer/limen/ui/Components.kt#L457), [FlowDossier.kt, lines 81 and 399–406](../../../app/src/main/java/dev/peringer/limen/ui/FlowDossier.kt#L399). [WCAG 2.2 text resizing](https://www.w3.org/TR/WCAG22/#resize-text) is an appropriate acceptance benchmark here, rather than a claim that every private Swiss app is legally required to meet that entire standard.

### F12 Make storage failure and recovery visible

**P1, source-confirmed failure handling; disk-full execution not attempted on the user's phone.** `Store.list()` silently drops unreadable JSON records; `load()` returns null. A saved handover can therefore disappear from the list without a recovery explanation. `flush()` discards save failures, whereas debounced `store.save()` exceptions are not caught in the coroutine. The UI has no reliable saved/saving/failed state. Restore then skips an existing ID before assessing whether its current file is corrupt, so a backup cannot repair that record through the normal restore action.

Fix: surface unreadable records, retain the failed file, expose save status and a retry/recovery action, and keep a last-good version. Offer an explicit restore conflict/recovery flow with preview and preservation of the existing file. Serialize persistence by record/revision so an older queued write cannot replace newer state. Evidence: [Store.kt, lines 36–60](../../../app/src/main/java/dev/peringer/limen/data/Store.kt#L36), [LimenViewModel.kt, lines 502–523](../../../app/src/main/java/dev/peringer/limen/ui/LimenViewModel.kt#L502), [Archive.kt, lines 128–147](../../../app/src/main/java/dev/peringer/limen/data/Archive.kt#L128). Fault-injection tests should cover no space, interrupted write, corrupt JSON and concurrent export/save.

## Further functional and usability fixes

| ID | Priority and evidence | Finding and concrete remedy |
| --- | --- | --- |
| F13 | P2, reproduced PDF | **The PDF is not A4.** Its MediaBox is 1240 × 1754 points, about 437 × 619 mm. Use approximately 595 × 842 points and scale/reflow the drawing; check actual-size printing. The [Android PDF API](https://developer.android.com/reference/android/graphics/pdf/PdfDocument.PageInfo.Builder) uses 1/72-inch points. See [PdfExport.kt, lines 39–40 and 134](../../../app/src/main/java/dev/peringer/limen/data/PdfExport.kt#L39). |
| F14 | P2, source-confirmed | **Photos cannot be inspected properly in the app.** They are cropped 72/84 dp thumbnails without an open/zoom action. Add a full-screen viewer with fit-to-image, zoom, captions and source/import-time information; keep delete separate. See [PhotoCapture.kt, lines 123–168](../../../app/src/main/java/dev/peringer/limen/ui/PhotoCapture.kt#L123). |
| F15 | P2, source-confirmed | **The entry comparison is only a count.** The exit room shows how many entry defects existed, but not their descriptions or photos; objects have no comparable baseline view. Add a read-only side-by-side prior/current view and a link to the entry document, without copying old defects as new ones. See [FlowInspection.kt, lines 221–248](../../../app/src/main/java/dev/peringer/limen/ui/FlowInspection.kt#L221). |
| F16 | P2, source-confirmed | **Standard rooms cannot be renamed.** Only `Custom` rooms show the name field, so two bedrooms are both “Bedroom”. Allow names for all rooms while retaining the translated room type, and support ordering. See [FlowInspection.kt, lines 255–258](../../../app/src/main/java/dev/peringer/limen/ui/FlowInspection.kt#L255). |
| F17 | P2, reproduced | **Finalized is sometimes falsely called signed.** Both parties may refuse and the record can appropriately be finalized, but its read-only bar still says “Signed”. Use “Finalized, read only”, with separate signed/refused/absent outcomes. See [FlowScreen.kt, line 196](../../../app/src/main/java/dev/peringer/limen/ui/FlowScreen.kt#L196), [refusal screenshot](evidence/Fr-refusals-described-as-signed-font1.15.png). |
| F18 | P2, source-confirmed and observed | **Starting another handover repeats the operator form.** Name/company are reused, but street, number, postcode, city and phone start empty. The compulsory valid phone, two-character names and compulsory house number exclude legitimate cases. Add a reusable operator profile, inclusive name/address validation, clear required/optional labels and a rapid draft mode. See [RoleGate.kt, lines 66–117](../../../app/src/main/java/dev/peringer/limen/ui/RoleGate.kt#L66) and [SwissIdentity.kt, lines 103–109](../../../app/src/main/java/dev/peringer/limen/data/SwissIdentity.kt#L103). |
| F19 | P2, source-confirmed | **Missing-item messages do not navigate to the problem.** Hub/signing list generic messages, including “complete or remove every component”. Make each item an action to the precise field/room, identify the missing component, and put the next useful action above the long list. See [FlowHub.kt, lines 102–113](../../../app/src/main/java/dev/peringer/limen/ui/FlowHub.kt#L102). |
| F20 | P2, source-confirmed | **None, missing and omitted are conflated.** A deposit card with “None” blocks finalization, while deleting Keys removes all key validation. Support explicit “not applicable”, “not available”, “not inspected” and reasons, with appropriate PDF wording. A partial inspection should not force a false “inspected” answer. See [CompletionRules.kt](../../../app/src/main/java/dev/peringer/limen/ui/CompletionRules.kt). |
| F21 | P2, source-confirmed | **Meter/key records are too shallow for reliable later identification.** Meters are six strings without identifiers, units, timestamp or photos; keys have counts but no structured identifier, expected/returned comparison or separate return moment. Add repeatable meter and access-device records, including fobs/remotes/cards and missing-item reasons. Never put working access codes into a broadly shared PDF by default. |
| F22 | P1 for the calculation's interpretation; source-confirmed | **The wear calculator labels a depreciation multiplication as the tenant's share of a repair.** Replacement, a local repair, cleaning and diminished value are different cases; the tenant's liability is not established by entering numbers. Separate replacement-value estimation from repair/loss analysis, validate finite nonnegative numbers, and preserve the inputs/source/date with the defect if used. It currently keeps them only in transient UI state. The [MV/HEV explanation](https://www.mieterverband.ch/mietrecht/unterlagen-und-tools/lebensdauertabelle/) supports residual-value guidance, not automatic adjudication. See [Zeitwert.kt](../../../app/src/main/java/dev/peringer/limen/sense/Zeitwert.kt), [ValueScreen.kt](../../../app/src/main/java/dev/peringer/limen/ui/ValueScreen.kt). |
| F23 | P2, source-confirmed | **Local legal confirmations can become stale within the same canton.** Editing the property address/place keeps the overlay answers, even where municipality/district matters. Removing Contract also clears its conclusion date without resetting the overlay. Invalidate affected checks when their factual inputs change, explaining what needs rechecking. See [FlowDossier.kt, lines 260–311](../../../app/src/main/java/dev/peringer/limen/ui/FlowDossier.kt#L260), [LimenViewModel.kt, line 1030](../../../app/src/main/java/dev/peringer/limen/ui/LimenViewModel.kt#L1030). |
| F24 | P2, source-confirmed architectural risk | **Legal output is recalculated with current code when an old record is exported.** The saved rule-set ID does not select an immutable historical resolver; `PdfExport` calls the current `resolvedLegalOverlay()`. Freeze the resolved legal facts/version with finalization and preserve the original rendered PDF or a reproducible rendering version. An upgrade must not change the legal meaning while retaining the same evidence reference. |
| F25 | P2, source-confirmed | **Import metadata and photo presentation need strengthening.** Gallery evidence records the import moment as `capturedEpochMs`; EXIF mirror/transpose orientations are ignored, though 90/180/270 rotation is handled. Distinguish observed capture metadata from app ingestion time, preserve originals, render every EXIF orientation and document provenance limits. See [LimenViewModel.kt, line 1184](../../../app/src/main/java/dev/peringer/limen/ui/LimenViewModel.kt#L1184), [PhotoDecode.kt](../../../app/src/main/java/dev/peringer/limen/photo/PhotoDecode.kt). |
| F26 | P2, source-confirmed | **A record cannot carry ordinary supporting documents.** Add lease, official rent form, mandate, prior condition report, guarantee certificate and delivery proof as classified attachments with hashes and an export manifest. Existing photo fields and a free-text note do not provide this dossier function. |
| F27 | P2, source-confirmed | **Final-document retrieval and filing are inconvenient.** Reopening forgets the previous export URI, so another export is needed to regain Open/Share. File names contain type and timestamp, but no property identifier, language or delivery revision. Store an export history and offer clearly named copies, preserving the exact version previously delivered. |
| F28 | P2, source-confirmed | **Session management lacks search and property history.** All JSON records are read synchronously and listed by creation time. Add address/party/date search, draft/finalized filters and entry–exit–amendment grouping; move file enumeration off the UI thread. This matters particularly for repeat professional use. |

## Usage and organisation

### What works well

The distinction between the property being inspected and the operator's contact address is sensible. The user deliberately selects the rooms present. Inspection can follow the physical apartment rather than a prescribed order. Recording a finding is relatively direct, the defect description invites location and dimensions, and an inspected room with no recorded defect is distinct from an untouched room. System photo intake and local evidence storage avoid an unnecessary account. A single shared record is appropriate for a joint handover.

Finalization, amendment creation and export are meaningful separate actions. Keeping an original record while creating a linked correction is a good model. A tenant can dispute a finding, and the main signature explanation separates observations from liability/payment. These foundations should remain.

### Where the current flow gets in the way

The initial form collects the operator's role, handover type, name, full contact address, address-country mode, telephone country and validated telephone before a draft can exist. This front-loads administration before the person can record the scratch they are looking at. It also repeats contact details on every new visit. The full UI test confirms that manual address entry can work without choosing a suggestion, contrary to the scratch README, but the screen still instructs users to choose an official suggestion.

The hub offers Inspection and Dossier equally, while another panel says the property/canton should come first. The long list of everything missing makes a brand-new record look problematic. None of its items takes the user to the correction. Objects are reached through Inspection and occupy a separate whole-flat screen, although checking an appliance normally happens inside its room. Meters and keys are buried in Dossier, despite being physical actions performed during the walk.

The room screen gives “Clear room” and “Remove room” two large red buttons beside the principal work. In French, the first becomes three lines tall. Those controls compete with the inspection task and are easy to encounter accidentally; move them into a labelled room-actions menu. A useful primary action here is “Finish this room / Next room”. Preserve Back as a predictable return path. Two visually identical back arrows currently coexist in a room, and the masthead primarily identifies entry/exit and address rather than the current task.

The app also lacks a neutral place to photograph a room with no defect. Room photographs require an artificial defect or an unrelated object. A light inspection guide and optional overview photos would improve the baseline without reinstating a burdensome grading grid. Partial access needs an explicit outcome: a locked cellar, untested appliance or covered floor should be documented without falsely marking it inspected.

At the close, “Close the record” opens Review and “Finalize record” actually freezes it; in German both can read “PROTOKOLL ABSCHLIESSEN”. The signature actions use pencil, cross and refresh icons. A cross can be mistaken for cancel even though it records refusal. Use visible action labels for legally significant outcomes, state whose decision is being recorded and let an accidental outcome be corrected with an appropriate warning.

### Recommended action order

Offer the following order as guidance while retaining free navigation during inspection.

| Stage | Main action | Information available at that point |
| --- | --- | --- |
| 1. Prepare | Choose entry/exit, property and reusable operator profile | Address, canton, appointment; manual entry works offline |
| 2. Establish scope | Identify contractual parties, representatives, rooms and previous record | Co-tenants, authority to act, furnished/shared spaces, exclusions |
| 3. Check local paperwork | Show only relevant legal/contract checks | Dated official sources, rent form if applicable, deposit/insurance evidence, unresolved questions |
| 4. Walk the property | Inspect each room and its supplied items | Overview photos, precise findings, each side's position, uninspected areas and reasons |
| 5. Transfer possession | Record meters, keys and access devices | Actual readings/units/identifiers, counts, missing items and actual return time |
| 6. Review and sign | Read the complete document together, settle all reservations, then capture each outcome | Exact names/capacities and the complete content covered by each signature |
| 7. Deliver and follow up | Save/share identical copies, record delivery, back up, track open actions | Stable document version, evidence/receipt references, repairs, later defects and deposit next steps |

A routine private handover should not require every optional financial field. A professional operator should be able to prepare the administrative parts in advance and complete the inspection at the property. Default empty values must remain visibly distinct from “none” and “not applicable”.

### Usability acceptance tests

Use realistic cases: a couple moving in; an agency employee representing an owner; a furnished sublet; three bedrooms; a missing cellar key; no mobile signal; a tenant refusing a charge but agreeing with the description; an absent participant; and an appliance that could not be tested. Ask users to explain what each main action will do before pressing it. Measure time to first finding, backtracking, assistance needed, missed reservations and time to retrieve the final copy. Proposed targets are at least 90% unaided completion of the routine scenario and no unobserved change to evidence or party positions. These are product targets, not measured results of this audit.

## Accessibility assessment

| Area | Assessment and required improvement |
| --- | --- |
| Text scaling | Confirmed clipping at 115% and 200%. Use content-driven heights and responsive stacking. Full labels, dates and essential controls must remain readable. |
| Contrast | Calculated from the declared palette: day main text 15.75:1, day secondary text on panels 4.92:1, night main text 14.88:1 and night secondary text 6.12:1. These are good base combinations. Amber text on the warning background is **3.62:1**, and on a raised panel **3.49:1**; small informative text needs a darker colour. Actual gradients should also be checked. [W3C contrast criterion](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html). |
| Screen-reader structure | Many controls have descriptions and some selectors expose selection. However, room/object headings lack systematic heading semantics, count controls say only Increase/Decrease, photo descriptions are filenames, and several direct `Chip(active=...)` callers omit `selectionState`. Add contextual labels such as “Increase apartment key count”, meaningful photo descriptions, heading navigation and state announcements. |
| Overlays | DefectEditor and the deposit comparison are Boxes over the underlying screen. The audit's semantics queries found background controls while the editor was open. Use an actual modal/focus boundary and exclude obscured content from accessibility. Verify this with TalkBack, rather than assuming that a visual overlay provides focus isolation. |
| Touch targets | Main `KeySurface` controls normally request 48 dp minimum dimensions. Information controls declare 40 dp and thumbnail-delete controls 44 dp; check their effective expanded touch bounds and separation, particularly near another action. Avoid claiming every custom control has an explicitly measured 48 dp target. |
| Motor access | Signing requires freehand drawing. Add an honest “unable to draw a signature” workflow and appropriate representative/paper-signing route, distinct from refusal. Test Switch Access and external keyboard focus/order. Do not present a typed name as a qualified electronic signature. |
| Alerts and undo | The six-second Undo period is fixed, and another edit/navigation commits it early. Honour the user's accessibility timeout and announce removal, save failure and export completion. Consider an accessible undo history for evidence changes. |
| Language | The app's custom language selection does not set Android component locales. The French UI opens an English calendar on this Pixel; country names also follow the device locale. Apply a consistent app locale and test assistive speech language. [Calendar evidence](evidence/Fr-system-date-picker-font1.15.png). |
| PDF | Examined PDFs have no `StructTreeRoot`, `MarkInfo` or document `Lang`. Text extraction works, but that is not an accessible reading structure. Provide a tagged document with headings, language, reading order and useful image alternatives, or an equivalent accessible companion format. |
| Orientation and theme | Landscape signing and dark mode are implemented and were exercised. Test the stable pad at large text too, and ensure the transition does not discard entered ink. The original phone settings were restored. |

WCAG 2.2 AA, Android accessibility guidance and practical assistive-technology testing are suitable engineering benchmarks. The applicable Swiss legal obligations depend on the operator and service, including whether a public body uses the app; the [EDI explanation of public and private services](https://www.edi.admin.ch/dam/edi/de/dokumente/gleichstellung/infomaterial/faktenblatt_4_fuerdieoeffentlichkeitbestimmtedienstleistungen.pdf.download.pdf/faktenblatt_4_fuerdieoeffentlichkeitbestimmtedienstleistungen.pdf) distinguishes those contexts. This review does not certify WCAG, PDF/UA or legal accessibility conformity.

## Wording and all five languages

The five-argument `pick()` design prevents an omitted language argument, and enum tests ensure nonempty text. It does not verify meaning, legal equivalence, natural language, accessible pronunciation or layout. All five languages share the functional defects above. Free-text observations remain in the language entered; selecting a different PDF language translates labels and standard prose, not those observations. State that explicitly before export and record the language understood by each signer.

| Version | Assessment | Concrete wording work |
| --- | --- | --- |
| English | Understandable, but technical vocabulary slows occasional users. | Prefer “New handover”, “My handovers”, “Property”, “Details” and “Review record” over session/object/component terminology. Change “Charged to the tenant” to an explicitly attributed landlord claim. Describe the calculator as an estimate with its assumptions. |
| French | The core terminology is appropriate to Swiss practice; several recent labels are clearer than the other languages. | Keep “état des lieux”, “garantie de loyer”, “régie” and “NPA”. Replace ambiguous “Accepte” with “Confirme la description” if that is the intended response; avoid implying acceptance of liability. Use “Relire le constat” for entry into Review and “Finaliser le constat” only for freezing. In the listing, “pas d'identification” should be “sans compte utilisateur”, since identifying information is required in the app. |
| German | Swiss `ss` usage is broadly respected, but terminology and available space need attention. | Replace “NEUE SITZUNG / ALLE SITZUNGEN” with handover/protocol terms. Replace “Elektrisch I/II” with “Stromzähler I/II”. Correct inconsistent ASCII forms such as “Schluesselzahlen” and “Pruefen”. Prefer “Protokoll prüfen” for review and a separate finalization label. The participant buttons need layout changes, not merely shorter translations. |
| Italian | Core locazione terminology is usable; some labels read as translations of software internals. | Prefer “Regole cantonali” to “Livello cantonale”, and a consistent “verbale” vocabulary. The listing uses “riconsegna” in a general sentence about both entry and exit. The published privacy page contains the literal typo “ProtocollI” and an untranslated “Swiss federal swisstopo SearchServer”; correct both. |
| Romansh | Presence is verified; publication-quality linguistic/legal accuracy is **not established**. Repository notes explicitly acknowledge no native-speaker review. | Reconcile “mancanza/Don”, “NPA/NAP”, “suttascripziun/suttasignatura” and session terminology. Review likely agreement problems such as “Nagins obligaziun” and “Naginas suttascripziun”. The live privacy page uses “cudesch avert” for open-source software and several untranslated technical fragments; it needs a native Rumantsch Grischun reviewer with a legal glossary. These are review findings, not an authoritative replacement translation. |

Several shared phrases need coordinated correction in **every** language:

- “BWO/OFL/UFAB initial-rent form” and the release notes' “federal form” misdescribe the source: the federal office publishes an overview of **cantonal form duties**, while the form is prescribed/approved by the canton. Use “Cantonal initial-rent form rules, BWO overview dated …”. RULV are tenancy rules/usages, not simply “rent-control periods”.
- “Signed, read only” must depend on actual signing outcomes, or become “Finalized, read only”.
- “Agrees/Accepte/Einverstanden/Accetta/Accepta” needs an object: description, requested work or liability are different things.
- “Established in two identical copies” is printed before the app has evidence that two copies were delivered. Prefer an instruction to provide an identical copy to each party, followed by the separate delivery record.
- The saved-backup explanation must state optional encryption consistently. The actual automatic address request must be explained without implying an explicit search action.

Use a reviewed glossary and a legal-copy change log across the app, PDF, website, listing and release notes. Test each critical legal sentence for equivalent scope, particularly claims, reservations, refusal and delivery. Native review is most urgent for Romansh and the Italian/German website policy; automated translation or a nonempty-string test cannot supply that assurance. Keep the final approval attached to a text version, not just a language name.

## Swiss legal and cantonal coverage

### Common legal foundation

Swiss tenancy is principally federal; cantonal additions operate where federal law reserves competence, particularly rental securities and initial-rent forms. Keeping one common workflow with targeted local rules is therefore sensible. See the [Federal Housing Office explanation](https://www.bwo.admin.ch/de/rechtsgebiet-mietrecht).

The relevant federal baseline includes delivery/maintenance of suitable premises (CO 256), disclosure of a prior return report on request and previous rent (256a), notification of defects (257g), deposit protection (257e), ordinary-use condition on return (267), immediate inspection/notification and later hidden defects (267a), and the initial-rent challenge period (270). Money/securities furnished as rental security belong in a bank account/deposit in the tenant's name; residential security is capped at three months' rent. Its release is governed by agreement or the prescribed enforceable title, with a one-year backstop subject to claims having been legally asserted. These points were checked against the [Code of Obligations, consolidated 1 January 2026](https://www.fedlex.admin.ch/filestore/fedlex.data.admin.ch/eli/cc/27/317_321_377/20260101/fr/pdf-a/fedlex-data-admin-ch-eli-cc-27-317_321_377-20260101-fr-pdf-a.pdf).

Limen covers recording condition, party positions, keys, deposits and basic delivery metadata. It does not issue an approved rent notification, serve a proven defect notice, obtain a bank release, calculate a court deadline with all local facts, or determine liability. Those functions should not be implied by a completed checklist. The PDF is useful evidence, but a drawn signature and a locally stored hash do not independently establish identity, trusted time or qualified signing. [OFCOM explains the distinction between qualified signatures and other electronic signatures](https://www.bakom.admin.ch/fr/23-quelle-est-la-valeur-legale-des-signatures-electroniques).

### All 26 cantons

The table separates the checked 2026 **initial-rent-form classification** from other handover coverage. `R` = required throughout the canton in the checked overview; `C` = local/category conditions; `N` = no duty listed for that canton in this overview. It does not mean every tenancy category is within scope. The source is the [BWO overview updated 16 September 2026](https://www.bwo.admin.ch/dam/fr/sd-web/lp4WEVqxdXWN/2026-09-16_F_Verzeichnis%20Formularpflicht%20Anfangsmietzins_Kantonale%20Gesetze%20und%20Leerwohnungsziffer.pdf).

“Common” below means the shared federal/lease workflow plus the app's generic local-timing confirmation. Every canton is selectable. All rows lack built-in local holiday resolution and a direct competent-authority link. `N` does not establish the absence of any other cantonal or contractual requirement.

| Canton | 2026 form duty and app classification | Other app coverage and remaining requirement |
| --- | --- | --- |
| AG Aargau | N / matches | Common. Preserve contractual landlord vs agency identity; route disputes to the competent district authority. |
| AI Appenzell Innerrhoden | N / matches | Common. Relevant lease/local timing and competent conciliation body must be checked externally. |
| AR Appenzell Ausserrhoden | N / matches | Common. Relevant lease/local timing and conciliation procedure remain external. |
| BE Bern | R / matches | Common plus form check. No form attachment, receipt details or direct official guidance. |
| BL Basel-Landschaft | N / matches | Common. Filing a court/conciliation application is a separate action with its own submission requirements. |
| BS Basel-Stadt | R / matches | Common plus form check. No source/form attachment or date-specific update mechanism. |
| FR Fribourg | R / matches | Optional contractual CCR label. **Cantonal deposit handling under LABLF art. 26 is absent.** |
| GE Genève | R / matches | Optional contractual CCR label. **LGFL guarantee requirements are absent.** |
| GL Glarus | N / matches | Common. Contract/local timing and conciliation guidance remain external. |
| GR Graubünden | N / matches | Common; Romansh interface/PDF available. No regional authority routing or validated Romansh legal glossary. |
| JU Jura | N / matches | Optional contractual CCR label. Its actual clauses are not translated into a checklist. |
| LU Luzern | R / matches | Common plus form check. No official-form attachment or source link. |
| NE Neuchâtel | C / matches | Optional CCR label; generic local/category prompt. **No municipality or dwelling-category resolution.** |
| NW Nidwalden | N / matches | Common. Contract/local timing and conciliation guidance remain external. |
| OW Obwalden | N / matches | Common. Contract/local timing and conciliation guidance remain external. |
| SG St. Gallen | N / matches | Common. No routing to the local rent/lease conciliation office. |
| SH Schaffhausen | N / matches | Common. Contract/local timing and conciliation guidance remain external. |
| SO Solothurn | N / matches | Common. Court/conciliation filing requirements are not represented by a PDF share action. |
| SZ Schwyz | N / matches | Common. District-specific authority routing remains external. |
| TG Thurgau | N / matches | Common. Municipality/joint-authority routing remains external. |
| TI Ticino | N / matches | Common. **Cantonal security-deposit requirements and the municipal property-expert route are absent.** |
| UR Uri | N / matches | Common. Contract/local timing and conciliation guidance remain external. |
| VD Vaud | C / matches | Dated RULV profiles and selected checks. **No district resolution; incomplete practical/deposit checklist.** |
| VS Valais | N / matches | Optional contractual CCR label. Local language, lease clauses and competent authority remain external. |
| ZG Zug | R / matches | Common plus form check. No official-form attachment or source link. |
| ZH Zürich | R / matches | Common plus form check. The general immediate-notice duty must remain visible; the app's planning dates do not establish timely notice. |

The authority-routing review used the [BWO national directory](https://www.bwo.admin.ch/dam/de/sd-web/tzHM7tFgxwhW/20260114_Schlichtungsbeh%C3%B6rden.pdf), whose internal date is 8 July 2026, plus cantonal guidance. The filename's January date should not be mistaken for the document's actual revision. Examples of why links matter: [Basel-Landschaft](https://oslvb.bl.ch/Behoerdengang/553) and [Solothurn](https://so.ch/services/schlichtungsverfahren-fuer-miete-und-pacht-schlichtungsgesuch/) explain submission formalities which an ordinary shared PDF does not satisfy. This directory should be linked through the actual property locality; a canton name alone is sometimes insufficient.

### Specific cantonal additions missing or incomplete

**Fribourg.** LABLF art. 26 requires a landlord/representative receiving cash or securities as security to place them within ten days in an account/deposit in the tenant's name at an eligible bank with a seat or branch in the canton. Limen records amount, bank and account, but not receipt/deposit dates, account holder or compliance evidence, and it generates no FR-specific check. Add a conditional check when the landlord actually receives the security, rather than imposing it on every bank transfer. [Official Fribourg law](https://bdlf.fr.ch/api/fr/versions/7153/pdf_file).

**Geneva.** LGFL distinguishes the permitted form of security, the tenant's choice of bank, ten-day placement of received cash/securities and the deposit certificate; it also covers subletting. A generic bank/insurance selector does not establish those conditions. Record the actual security form, receipt date, bank/account holder, certificate reference and any exception, with a link to current guidance. Do not treat every marketed guarantee product as automatically fitting every legal category. [LGFL arts. 1–3, 6, 8 and 10](https://silgeneve.ch/legis/data/rsg_i4_10.htm).

**Ticino.** The implementation law requires qualifying security to be deposited within ten days at a bank with a seat/branch in the canton and provides for a receipt in two copies. It also establishes municipal property experts who may be requested for tenancy findings. Limen's generic Expert role supplies neither that explanation nor a route to the relevant expert; appointment is not mandatory at every handover. Add the deposit evidence/checks and an optional municipal-expert contact route. [Official law, arts. 6–13](https://www3.ti.ch/CAN/RLeggi/public/index.php/raccolta-leggi/legge/num/153).

**Neuchâtel.** The 2026 rule depends on both municipality and dwelling category. For example, Laténa's specified category is four/five rooms; La Grande Béroche's is three/four/five. The decree defines habitable-room counting and ignores half rooms. Limen stores neither a legal dwelling category nor municipality ID; counting its inspection rooms, including hall/cellar/mailbox, would be wrong. Add a separate official property category, dated lookup and evidence of the check. [2026 Neuchâtel decree](https://rsn.ne.ch/DATA/program/books/rsne/htm/846.02.htm), read with the BWO form overview.

**Vaud.** The current RULV period from 1 July 2026 to 30 June 2032 is correctly represented. The app records entry insurance/deposit checks, return readiness and key transfer, but misses received-cash placement, structured accessory-condition evidence and important practical prompts. Expand the guide to cover the applicable entry and return provisions, while retaining the difference between condition confirmation and debt acknowledgement. Sources: [Vaud tenancy guidance](https://www.vd.ch/territoire-et-construction/logement/droit-du-bail) and [RULV text](https://www.vd.ch/fileadmin/user_upload/themes/vie_privee/logement/fichiers_pdf/rulv.pdf).

For the initial-rent form, the [2026 Vaud district page](https://www.vd.ch/territoire-et-construction/logement/politique-du-logement/penurie-de-logements) identifies Aigle as the exception to the district shortage classification. Limen asks for verification but provides no district decision or source link. A locality resolver and a dated rule record would make that check usable.

**Contractual Romand framework.** The app correctly avoids treating the CCR as generally binding outside the applicable Vaud framework: its general binding force ended on 1 July 2020. An expressly incorporated contract can still apply, but the current toggle only changes the profile label; it does not resolve the incorporated clauses or attach them. Record which terms/version were incorporated. [BWO framework-contract explanation](https://www.bwo.admin.ch/fr/contrats-cadres).

### Legal governance and boundaries

The initial-rent table deliberately falls back to manual verification outside 2026. That is preferable to silently reusing the table, but it needs a visible review date, official links, update ownership and tested effective-date boundaries. Vaud dates outside the implemented 2020–2032 periods currently fall back to another profile: use an explicit unsupported-period warning instead of implying that no historical/future framework applies.

A full Swiss product should distinguish residential/commercial premises, subletting, shared accommodation, furnished rooms, early return, absent parties, later-discovered defects and inaccessible parts. Public/social housing or an unusual lease can introduce further conditions. The app should state its supported residential scope and route exceptions to verification. This audit checks the principal handover-related federal/cantonal layers above; it does not certify every municipal rule, special tenancy or particular contract.

A coat of arms is not made unrestricted merely by a public-domain copyright label. The inspected assets are documented as canton flags and serve identification; the existing non-endorsement notice is helpful. Preserve that use, document the distinction from protected arms and avoid authority/seal claims in branding. The [IPI explains the separate protection of flags and emblems](https://www.ige.ch/fr/droit-et-politique/evolutions-internationales/organisations-de-pi/ompi/drapeaux-et-emblemes). No unlawful use was established in this audit.

## Feature completeness for real handovers

These are product requirements and acceptance recommendations. A missing app field does not by itself establish a breach of law by a particular landlord or tenant.

| Capability | Current state | What a complete workflow needs |
| --- | --- | --- |
| Bilateral residential entry/exit | Available and exercised | Keep the common record; fix the evidence and party-position defects |
| Couples, joint tenants and representatives | Partial | Individual identities/outcomes, actual contractual landlord and authority/capacity |
| Furnished/shared/sublet premises | Some room/object flexibility | Explicit scope, ownership of supplied items, common areas and relevant subletting parties |
| Property identity | Address/canton/floor free text | Stable unit reference; separate legal municipality/dwelling category where needed |
| Condition inventory | Defects plus whole-room/item inspected flags | Optional inspection prompts, neutral overview photos, cleaning/functional checks and inaccessible/not-tested outcomes |
| Baseline comparison | Linked entry and a defect count | Readable entry descriptions/photos and imported earlier paper/PDF reports |
| Photos | Camera/gallery intake, hashes and thumbnails | Full viewer, useful captions, camera/import provenance and visible decode errors |
| Meter readings | Six values | Repeatable IDs/units/photos and reading time; absent/shared meter outcomes |
| Keys and electronic access | Counts and transfer outcome | Key/fob/card identity, expected vs transferred quantities, actual transfer time and missing items |
| Repair follow-up | Exit free-text owner/deadline/cost | Entry remediation too; per-action status, due date, evidence, assigned party and later completion |
| Liability and cost discussions | Claim toggle, tenant position, calculator | Independent attributed positions, estimate basis, no automatic admission, separate settlement if ever added |
| Official forms and supporting documents | Check and note only | Attach/reference forms, lease, mandate, deposit/insurance certificate and delivery proof |
| Signing | Drawn ink or refusal; unilateral option | Full pre-sign review, reservations settled first, actual signer names and inability-to-sign handling |
| Delivery | Share sheet and self-declared metadata | Stable exported version, recipient/address/reference, exact delivered copy, no lost updates |
| Later discoveries | Generic amendment | Dated defect addendum, discovery/notification evidence and original linkage |
| Deposit follow-up | General release text | Relevant security type, return/dispute state, correct dates, references and provider/cantonal conditions |
| Long-term records | Local JSON, evidence files, backup | Search, recovery, reminders to copy backups off the phone, original-PDF retention and restore conflict handling |
| Accessibility | Partial | All labels at large text, assistive focus control, contextual semantics and accessible document output |

Add a participant-review mode that keeps the other party within the current handover when the operator passes over the phone. The current device-wide record library can otherwise be reached through navigation. A session-scoped view and an optional lock for the professional library improve privacy without requiring a remote account.

The initial-rent information should also link to the challenge procedure and distinguish taking possession, form receipt and contract dates. Do not silently calculate a definitive challenge deadline from only one of those facts. Likewise, the handover does not by itself settle an early termination, cleaning invoice, final service-charge statement or deposit claim.

## Codebase and operational assessment

### Foundations worth retaining

The codebase is small enough to understand, uses typed immutable record objects and keeps calculations/record-selection logic separately testable. Atomic replacement in `Store.save`, path checks in archive import, rejection of missing evidence, authenticated encryption before installing decrypted records, compatibility fixtures and a non-debug release signing configuration are useful safeguards. Unknown JSON keys are tolerated, and added default fields are deliberately protected against changes to older fingerprints.

The rebuilt release declares Internet plus the private AndroidX receiver permission; it requests neither broad storage nor camera permission. Its file provider exposes only the capture cache path. Android cloud/device-transfer backup is excluded. The release APK contains the application NOTICE and full Apache licence texts under library metadata paths. `zipalign -c -P 16 -v 4` passes. Native-library ELF alignment and a 16 KB runtime should still be part of release acceptance; ZIP alignment alone does not prove the latter.

Target SDK 36 meets the checked [Google Play submission requirement effective 31 August 2026](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-EN). The lint `OldTargetApi` warning does not, by itself, mean the app fails current Play requirements. Minimum SDK 29 needs runtime coverage on an older target, independently of success on the API 37 Pixel.

### Changes justified by the defects

1. **Give persistence one ordered owner.** The 1,489-line ViewModel coordinates UI state, files, signatures, photos, archives, legal inputs, navigation and exports. Extract a record repository with explicit operation results, revision checks and serialized updates. Keep UI state separate from the durable protocol. This directly addresses F02/F12.
2. **Bind asynchronous work to an immutable record ID and revision.** Photo attachment reads `working` inside background work and applies the result to the currently open record; amendment/finalization also finish asynchronously. Test switching records, navigating back or finalizing while those jobs run. Wrong-record attachment, orphan files and unexpected record switches are risks requiring targeted reproduction.
3. **Centralize validation without hiding uncertainty.** Names, parties, legal dates, money, key counts and asset references need coherent domain checks. Monetary/deposit strings currently pass if merely nonblank; added witness/expert names can remain blank. The phone-number gate is stricter than many financially relevant fields. Preserve legitimate “unknown” and disputed facts instead of coercing them to invented defaults.
4. **Version the saved and rendered contract.** Pin schema behaviour, legal snapshots, signature meaning and export rendering. Test finalized historical records after upgrades, including legacy cost-signature planes which are retained in the model but not printed by the current record-only renderer. Never rewrite historical evidence merely to satisfy a new UI.
5. **Harden archive resource limits and interruption recovery.** The 2 GiB import ceiling is large for a phone. Bound encrypted input, individual JSON files, entry sizes, disk consumption and decompressed totals. The final `cipher.doFinal()` output bypasses the running plaintext-size counter, and some GCM providers buffer until authentication; test actual Android memory behaviour. Sweep abandoned `archive-decrypt-*.zip` files left by process death, not just the unpacking-stage directories. These are source-identified risks, not a demonstrated remote exploit.
6. **Make errors actionable.** Address lookup returns an empty list for no match, timeout and failure; photo-copy failures can return without explanation; backup errors have one generic message. Distinguish these cases and preserve entered data. The gallery-to-cache copy currently occurs in the activity-result callback; move large copies off the main thread and show progress.
7. **Test rendered outcomes.** Current tests are useful for arithmetic, hash stability and structural completeness, but several encode implementation choices rather than user/legal correctness. The 10-day date arithmetic and “remove Keys to remove the gate” tests cannot establish a suitable handover policy. Add workflow assertions, full-content review/PDF comparisons, actual A4/structure checks, language scaling and failure recovery.

No dependency vulnerability database scan or penetration test was performed. Passing build/lint tests is not a security clearance. Produce an SBOM, review dependency advisories and attach repeatable device/export checks to CI; update dependencies in bounded changes with compatibility tests.

### Documentation drift and signing

| Note or claim | Current finding |
| --- | --- |
| Scratch README requires a selected swisstopo result | Current UI permits manual entry; the end-to-end entry test did so |
| August handoff/README says all new backups are encrypted | Empty-password plaintext archives are intentionally supported |
| README promises room rename and entry comparison | Standard-room naming and prior evidence presentation are incomplete |
| README says every custom target is at least 48 dp | Some controls explicitly declare smaller layouts; effective touch bounds need measurement |
| Older release notes describe both signature planes | Current UI offers one record-signature plane |
| Older notes describe a three-day legal outer edge | Current explanatory code is more careful; the notes should match the qualified wording |
| Release runbook calls API 35 the submission floor | The checked current floor is API 36; the app already targets it |
| Historical signing digest | The August handoff's recorded digest differs from the certificate verified in this audit |

The freshly rebuilt release is signed by the configured `limen-upload` key from the external Limen upload keystore, with certificate SHA-256 **`bd11a58669405d6a3bb58aa4055b654e22cd670105c3e3c19ab189c2e2b509d3`**. This is not the debug key. The on-phone audit package uses the Android debug certificate **`5f11947f8ece4df3852c35ee81c643a8598dbdcdc25b0d9eadad8f07eff1b9e4`**. Reconcile the historical note and verify the configured upload certificate against Play Console before the next upload; Console registration was not checked here, and a mismatch in a note does not prove an unauthorized key rotation.

Keep one current product/verification document and clearly date archived handoffs. Legal-source updates should include effective dates, affected cases, reviewer identity, changed wording in all languages and regression examples. Back up the signing material separately from handover archives.

## Value in the Swiss market

### Demand and differentiation

There is a real recurring use case. The Federal Statistical Office reports that **9.3% of the resident population moved in 2024**, with roughly **697,000 people moving within Switzerland**. These are people, not households, handover events or potential paying customers; they do not provide a defensible app revenue forecast. They do establish that documenting changes of occupancy is a substantial recurring activity. [FSO movement statistics, published 8 December 2025](https://dam-api.bfs.admin.ch/hub/api/dam/assets/36260767/master).

The market assessment is that Limen's best initial audience is **small landlords, independent handover specialists and tenants who want their own organized evidence**, followed by small agencies that accept a local workflow. The useful proposition is a shared Swiss residential condition record, usable at the property, with readable exports and no developer-held dossiers. Five languages, including verified Romansh, could reinforce that position. Supporting Romansh inaccurately would weaken trust instead.

Photos, signatures and PDF export are already expected capabilities. They cannot carry the proposition alone. The following are current vendor claims, not independent performance or legal-conformity tests:

| Comparator | Published capability relevant to Limen | Implication |
| --- | --- | --- |
| [Wohnungsprotokoll](https://wohnungsapp.com/de-de/) | Guided handover, photos/meters/keys/signatures and PDF preview before signing; iOS/Android; per-document and subscription options | Limen needs a clearer first-use journey and pre-sign document review even in the smaller standalone-tool segment |
| [AXimmo / Tayo](https://www.axept.ch/aximmo) | Property history, integrated handover processes, work orders, digital signing and document distribution | Larger agencies will evaluate integration, repeatability, handover history and follow-up, beyond individual PDF creation |
| [ImmoPad](https://immopad.com/logiciel-application-d-etat-des-lieux/) | Offline inspection, photo inventory, entry/exit comparison and professional reporting | Offline work and mobile capture are useful but not unique; Limen's defensible difference needs verified Swiss guidance and simpler evidence handling |

The absence of cloud synchronization is a useful privacy/product choice, but professional users still need reliable multi-device handoff, recovery and filing. That can start with carefully scoped encrypted exports and documented interoperability; it does not require immediately building an agency ERP or cloud account system. The separate iPhone version described by the publisher should be tested for archive and document parity before making cross-platform continuity claims.

### Commercial priorities

First establish dependable record creation and a clear legal-maintenance process. Then test the product with tenant–landlord pairs and small professional operators in French-, German- and Italian-speaking areas, with a native Romansh reviewer. Ask whether they would replace their existing form/tool, which part saves effort and what would prevent them from trusting the result. Test willingness to pay and support costs; do not derive a price from the amount of code or the number of languages.

An occasional user will value a straightforward purchase or per-handover option; repeat professionals may value ongoing maintenance/support. Whichever model is chosen, make the terms clear before the appointment and avoid trapping a signed record behind a newly introduced export payment. Current pricing and production-store availability were not established from this repository. The old runbook's free/paid decision should not be treated as a current commercial fact.

The biggest adoption risks are inaccurate or overbroad legal claims, loss of a reservation or receipt, inability to retrieve an old handover, language defects at signing, and repetitive administration. Resolving those provides more immediate value than AI-generated descriptions, insurer comparison features or broad integrations. Describe the app as a structured documentation aid with dated Swiss guidance; demonstrate sample records and recovery procedures rather than promising legal enforceability.

## Remediation sequence and acceptance gates

| Sequence | Work | Acceptance evidence |
| --- | --- | --- |
| 1. Record integrity | F01–F05 and F12; independent positions, explicit reservation replacement, valid-input state, complete evidence, ordered persistence | Regression cases reproduce the current defects and pass after fixes; export during receipt entry preserves the receipt on disk; corrupt/unsupported images produce a specific error |
| 2. Trust at signing | F06/F07/F17; complete review, actual party/signatory identities, joint tenants and clear refusal/absence states | Two tenants and an agency representative complete a handover; exact reviewed content matches the PDF; an edit names whose signatures become stale; refusal is never called a signature |
| 3. Legal coverage | F08–F10/F23/F24; cantonal deposits, local criteria, dates, provenance and consistent notices | A reviewed 26-canton matrix; NE category and VD district fixtures; FR/GE/TI security examples; missing/unknown/disputed cases; historical and expired snapshots never silently acquire current legal meaning |
| 4. Field usability | F11/F14–F21/F26–F28; inclusive intake, room photos/naming, comparison, keys/meters, direct error navigation | Full entry and exit through visible controls; no clipped essential text in any language; routine handover works without a suggestion lookup; older records can be found and inspected |
| 5. Output and language | Correct A4, accessible document structure, shared glossary and native review | PDF geometry and full-text/attachment comparisons; FR/EN/DE/IT/RM samples; native approval for critical wording; no untranslated system calendar within the chosen language |
| 6. Recovery and release | Archive limits/recovery, versioned render history, device matrix and signing documentation | Restore on a clean second device; recovery over a corrupt local record; wrong-password/tamper rejection; process interruption tests; minified release smoke checks on API 29 and a current Pixel |

The legal regression set should at least contain every canton for a 2026 entry and exit; missing/2025/2027 conclusion dates; Vaud at 30 June/1 July 2026; an out-of-supported-period Vaud case; Laténa with three versus four rooms; La Grande Béroche with two versus three; Aigle versus Lausanne; changes of locality within the same canton; a deposit added/removed after verification; and contractual CCR clauses present/absent. “Not required by this snapshot” must never become “all handover obligations satisfied”.

The field regression set should include a clean room, a room that cannot be inspected, repeated room types, an existing entry defect, a disputed normal-wear finding, a missing key, no physical keys, a meter without its ID, a witness with missing identity, an absent party, an “other” unilateral reason without an explanation, long names/reservations, an impossible edited date, interruption during photo intake/export and delivery metadata changed during rendering. Unsupported or unknown facts should remain explicit in the record.

Release acceptance should retain the project's signed-release/export/smoke-test handoff for any later implementation work. This audit changed documentation and used temporary test probes; it did not refactor or fix the production app, commit or push anything, or replace the user's installed release.

## Evidence and handoff

See [EVIDENCE.md](EVIDENCE.md) for the test commands, artifact fingerprints, selected Pixel screenshots, sample PDFs and the temporary audit probe source. The full working evidence remains in `_scratch/audit-2026-10-13/`; selected artifacts are copied beside this report so its main findings can be reviewed without rebuilding.

To commit only the report and its selected evidence, the following commands are provided, **not executed**:

```powershell
Set-Location C:\Users\aperi\DEV\ANDROID\apps\limen
git add -- docs/audits/2026-10-13
git commit -m "Document Limen Swiss handover application audit"
git push origin main
```
