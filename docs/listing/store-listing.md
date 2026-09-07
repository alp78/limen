# Limen store listing

Ready to paste into the Play Console. Character limits are Play's and are counted here against the text inside the fenced blocks.

---

## App name

`Limen`

(5 / 30)

---

## Short description

```
Swiss handover protocol: defects, photos, signatures, PDF. Stays on your phone.
```

(79 / 80)

---

## Full description

```
Limen records the handover of a Swiss apartment, from the first meter reading to a signed PDF both parties take away.

It is one document, not two apps. The tenant, the landlord and the managing agency each start a session from their own side, and the record that comes out is the same two-sided protocol.

WHAT IT RECORDS

Rooms hold defects, and only defects. There is no grid of undamaged surfaces to tick, because a handover protocol is a record of what is wrong, not an inventory of what is fine. Every room still carries an "inspected" mark, because "checked and clean" and "never opened" are different facts and the paper should say which.

Each finding takes a description, photographs, who is charged, a repair deadline, an estimate, and the other party's stated position. At an exit protocol the tenant can dispute a finding, call it normal wear, or say it was already there at entry, without the app treating any of it as an admission.

Supplied appliances and furniture are rows rather than checkboxes, so two fridges are two facts, each with its own photograph and its own defect.

THE PAPERWORK

Address, parties, contract, deposit, meter readings and keys. Optional sections are added only when the handover has them, so a record never asks for a deposit it does not involve.

A cantonal overlay covers the rules that differ across Switzerland, including the Vaud RULV framework and the initial rent form, with the year of the snapshot stated on screen.

SIGNING AND EXPORT

Both parties sign on the phone, or refuse and say why, with any reservation recorded next to the signature. A finalized record is sealed: it can be amended by a new linked record, never quietly edited. The export is a plain A4 PDF with the photographs as a numbered annex.

PRIVACY

There is no account, no sign-in, no advertising, no analytics and no crash reporting. Protocols, photographs and signatures stay in the app's private storage until you export, share or back them up.

Limen uses the internet for one thing: when you type a Swiss address, the typed text is sent to the federal swisstopo address service for suggestions. Nothing else leaves the phone, and no handover data is ever sent to the developer.

Photographs come from the system camera or the system picker, so the app asks for no camera and no storage permission. Android cloud backup and device-to-device transfer are switched off for its data.

Backups are yours to make: a single archive of every record, its photographs and its signature ink, with a password if you choose to set one. Limen cannot recover that password.

FIVE LANGUAGES

English, French, German, Italian and Romansh, in the app and in the exported PDF, which can be produced in a different language from the one you are working in.

Limen is a structured handover tool. It is not legal advice and does not replace current official guidance.
```

(2851 / 4000)

---

## Category and tags

- **Category:** Business
- **Tags:** documents, property, inspection, PDF, Switzerland

## Content rating

Business tool with no user-generated public content, no ads, no purchases. Expect Everyone / PEGI 3. The questionnaire answers are all "no": no violence, no sexual content, no profanity, no controlled substances, no gambling, no user-to-user communication, no location sharing.

## Contact and links

- **Privacy policy:** <https://alp78.github.io/legal/limen/privacy/>
- **Support email:** aperinger@proton.me

## Graphics

All of these are in `docs/listing/graphics/` and are ready to upload.

| Asset | File | State |
|---|---|---|
| App icon | `play-icon-512.png` | 512 x 512, 32-bit RGBA, 140 KB. The supplied seal on the launcher's plaster ground, so the store icon and the home-screen icon are the same mark |
| Feature graphic | `play-feature-1024x500.png` | 1024 x 500. The seal, the wordmark and one line, in the app's navy, bone and bronze, set in Roboto because the app renders in `FontFamily.SansSerif` |
| Phone screenshots | `phone-01` to `phone-08` | Eight, 1080 x 2424, captured from the release build at `versionCode 3` |
| Tablet screenshots | none | Optional. Worth adding, since the Galaxy Tab Active 3 is a real target and large-screen shots help ranking |

The eight screenshots, in upload order:

1. `phone-01-hub.png`, the two doors and what the record still needs
2. `phone-02-rooms.png`, five rooms, each carrying its own inspected mark
3. `phone-03-room-with-defects.png`, a room with two defects and their positions
4. `phone-04-defect-editor.png`, recording one defect
5. `phone-05-canton-overlay.png`, the Vaud overlay with the RULV basis and its dated period
6. `phone-06-signature.png`, signing on the phone
7. `phone-07-finalized.png`, the sealed record with its evidence fingerprint and the five PDF languages
8. `phone-08-exported-pdf.png`, the exported PDF and the delivery record

The feature graphic is a plain composition of assets that already exist, not an identity round. Replace it if the visual identity is ever revisited.

**On the aspect ratio.** These are 1080 x 2424, which is the Pixel 10's own shape and taller than the 9:16 the Play documentation names. Play accepts modern tall screenshots in practice, since every recent handset produces them. If the Console ever refuses one, pad rather than crop, so nothing is lost from the frame.

**The one shot not taken.** A defect carrying a photograph. The app takes photographs through the system camera, and an honest store screenshot needs a real one of a real mark on a real wall, which is thirty seconds with the phone and cannot be manufactured here. `phone-04` shows the defect editor with its camera and gallery controls instead.
