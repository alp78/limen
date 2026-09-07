# Google Play Data safety answer sheet: Limen

**Audited 7 September 2026 against `dev.peringer.limen`, versionCode 3, versionName 1.1.1.**

This is a release worksheet, not legal advice. Recheck it against the current Play Console wording before submission. Google makes the developer responsible for complete and accurate declarations, including the behaviour of every bundled library.

Authoritative references:

- [Google Play Data safety form guidance](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Android data-use declaration guide](https://developer.android.com/privacy-and-security/declare-data-use)

## The one thing that makes this form non-trivial

Limen holds a great deal of personal data and transmits almost none of it. Names, addresses, telephone numbers, photographs of someone's home and handwritten signatures are all recorded, and every one of them stays in private app storage. Under Play's definition, collection means transmitting data off the device, so none of that is collected.

One thing is transmitted. When the user types into a Swiss address field, the app waits briefly and sends the typed text to the Swiss federal swisstopo address service for suggestions. That is an address fragment leaving the device to a third party, and it must be declared. It goes to swisstopo, never to the developer.

The temptation is to answer "no data collected" because nothing reaches Peringer. That answer would be wrong, for the same reason Aplomb's was.

## Section 1: collection and sharing

| Console question | Answer | Basis |
|---|---|---|
| Does the app collect or share any required user data types? | **Yes, collects** | the swisstopo address lookup described below |
| Does the app share user data with third parties? | **No** | swisstopo receives the lookup as the service performing it, which Play treats as collection by the app rather than sharing; PDF and archive exports are user-initiated through the system share sheet, which Play excludes from sharing |
| Is collected data encrypted in transit? | **Yes** | the swisstopo request is HTTPS |
| Can users request deletion? | **Answer against the exact Console wording** | there is no account and no developer-held server data; records are deleted in the app and by clearing app data. Do not claim a deletion endpoint exists |
| Does the app use the advertising ID? | **No** | no ads library and no `AD_ID` permission |
| Independently security reviewed? | **No** | no independent certification has been performed |

## Section 2: data types to declare

| Play data category | Collect? | Share? | Required or optional | Purpose and evidence |
|---|---:|---:|---|---|
| Personal info: Address | **Yes** | **No** | Required | The text typed into a Swiss address field is sent to `https://api3.geo.admin.ch/rest/services/ech/SearchServer` after three characters, to return official address suggestions. Purpose: App functionality. Not linked to an identity, because no identifier is sent with it |

That is the whole of section 2. Everything below is deliberately not declared, with the reason.

## Section 3: data types NOT declared, and why

Each of these is recorded by the app and stays on the device, which is not collection under Play's definition.

- **Names, roles and contact details of the parties.** Private app storage. Printed into a PDF the user exports themselves.
- **Property, lease, deposit, meter and key details.** Private app storage.
- **Photographs.** Taken by the system camera into a temporary grant for one destination file, or chosen through the system photo picker. Limen declares no camera and no storage permission. Photographs are copied into private app storage and never transmitted.
- **Handwritten signatures.** Drawn in the app, written to private storage as PNG, covered by the record's evidence fingerprint. Never transmitted.
- **Defect descriptions, comments and reservations.** Private app storage.
- **Files and documents.** The exported PDF is written to the device and can be passed to the system share sheet by the user. Play excludes user-initiated share-sheet actions from both collection and sharing.
- **The backup archive.** Written to Downloads/Limen, encrypted when the user sets a password and plain when they do not, which the screen states. A password is never transmitted and cannot be recovered by the app.
- **Location.** No location permission, and none is requested. The address is typed, not sensed.
- **Device or other IDs.** None read and none sent.
- **App activity, diagnostics, crash logs.** No analytics library, no crash reporter, no telemetry of any kind.
- **Contacts, calendar, microphone, SMS, health.** No permission and no code path.

## Section 4: permissions in the signed release

Verified with `aapt2 dump permissions` against the release APK:

```
uses-permission: android.permission.INTERNET
permission:      dev.peringer.limen.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: dev.peringer.limen.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

`INTERNET` is the swisstopo lookup and the outbound links to rental-deposit providers, which open in the user's browser. The second is a private permission an AndroidX library defines for its own components; it grants no access to device data and cannot be used by other apps.

## Section 5: things to decide before submitting

**The address lookup is currently unavoidable.** It fires automatically once three characters are typed into a Swiss address field. That is why it is declared Required rather than Optional. If it were behind a setting, the declaration could become Optional and the app could be used entirely offline by choice rather than by accident. Worth considering, but it is a product change, not a form answer.

**Do not describe the app as anonymising anything.** It does not. It keeps data local, which is a different and stronger claim, and the store copy and privacy page both already say exactly that.

**Recheck after any change to the network path.** If a second service is ever contacted, this sheet and the published privacy page both have to move before the release does.
