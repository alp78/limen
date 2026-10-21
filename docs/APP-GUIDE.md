# Limen

Limen is a local Android application for Swiss residential handovers. It records the property, contractual parties and representatives, inspection findings, each side’s position, photographs, supporting documents, meters, access devices and signatures. It exports an A4 PDF and a reflowable HTML companion in French, English, German, Italian or Romansh.

## At the property

1. Start an entry or exit draft with your name. Contact details are optional and reused on the next handover. Enter the property independently from your own address.
2. Record the contractual landlord, each tenant and any representative with their authority. Review the participant privacy notice before collecting their information.
3. Select the canton and check the dated legal guidance. Unresolved checks can be recorded with an explanation. These are recorded checks, not legal certification.
4. Inspect rooms in the order that suits the visit. Rename and reorder rooms, photograph their general condition, record defects and competing positions, and explain any inaccessible or untested areas. An exit can show the saved entry record.
5. Record key and device transfers, meter identifiers and units, and the actual transfer date. Attach the lease, official form or other supporting documents, or record an external reference.
6. Open the complete document preview. Enter all reservations before signing. Each person may sign, refuse, be absent or be unable to draw a signature. The last two outcomes require an explanation. A drawn signature does not establish a qualified electronic signature or an obligation to pay.
7. Finalize, export and record delivery separately for each party. Re-export after updating receipts. Exact PDF and HTML copies remain available in the record’s export history. Changes to a finalized record require a linked amendment.

## Privacy and recovery

Address lookup is off by default. Enabling it shows a disclosure before typed address searches are sent to swisstopo. Manual entry works offline. Optional device-credential protection restricts access to the saved-record library and settings; it does not encrypt files or prevent access to the current handover being reviewed.

Records and evidence remain in private app storage. Removed evidence can remain for recovery until the entire record is deleted. Public exports and copies shared with other apps must be managed separately. Backups contain the selected records and their referenced evidence and retained exports. Passwords enable authenticated encryption; a plain archive requires an explicit choice. Backups, photographs and supporting documents have no size limit; a restore needs free storage for the archive it unpacks. Inside a backup, JPEG photographs are repacked losslessly as JPEG XL, about a fifth smaller, and rebuilt byte for byte on restore; a backup holding repacked photographs restores in Limen 1.2.5 or later.

Save failures remain visible. Unreadable records are listed, and a last-good copy can be recovered. Restore skips healthy existing records. Its optional repair mode preserves corrupt originals in a recovery directory before installing validated replacements.

## Legal and language coverage

The legal snapshot is `ch-tenancy-2026.2`, checked on 13 October 2026. It includes all 26 canton selections, the 2026 initial-rent-form overview, Neuchâtel municipality and dwelling-category conditions, Vaud district conditions and dated RULV, and FR/GE/TI deposit-handling checks. Unknown or different-year facts require verification. Official sources and the federal directory of conciliation authorities are accessible from the dossier.

No standard ten-day entry deadline is assumed. A planning date is shown only for a recorded contractual clause and period. Exit guidance calls for immediate, detailed notice. The calculator estimates residual replacement value only, preserving its inputs and source; it does not determine liability, repair compensation or a debt.

Document labels are translated; entered statements retain their original language. New and revised Romansh wording still requires native review. A Swiss tenancy professional should review the legal workflow before broad professional deployment. Cross-platform archive compatibility has not been certified for these new Android features.

## Development and verification

Run `gradlew.bat :app:testDebugUnitTest :app:noEmDash :app:lintDebug`. Android tests use the separate `dev.peringer.limen.uitest` package. Never clear data or run destructive test setup against the production package. Read [AGENTS.md](../AGENTS.md) for the release handoff.

The original [audit](audits/2026-10-13/REPORT.md) and [implementation ledger](audits/2026-10-13/IMPLEMENTATION.md) contain the findings, validation evidence and remaining external review requirements. Historic notes under `_scratch` describe older releases.
