"""Check a pulled set of store screenshots and copy it into the listing's graphics folder.

    python -I tools/store_shots_check.py <pulled>/phone --into docs/listing/graphics/phone --contact _scratch/store-shots-phone.png
    python -I tools/store_shots_check.py <pulled>/tablet --into docs/listing/graphics/tablet --crop-top 48 ...

Every locale directory must hold the eight shots under their expected names; each image is
written as 24-bit RGB at the device's size, optionally without the status bar; its dominant
colour must be one of the app's Day surfaces, which is what catches a warmed or dark capture
(the Galaxy Tab's evening schedules once turned the navy into a grey and nothing on screen said
so). A contact sheet of the whole set is written for the one-minute look.
"""
import argparse
import os
import sys
from collections import Counter

from PIL import Image, ImageDraw, ImageFont

NAMES = ["01-home", "02-rooms", "03-defect-editor", "04-canton-overlay", "05-review", "06-signature", "07-record-cover", "08-photo-annex"]
LOCALES = ["en-US", "fr-FR", "de-DE", "it-IT", "rm"]
SIZES = {(1080, 2424), (1200, 1920), (1200, 1872), (1200, 1884)}
SURFACES = [(0xF6, 0xF7, 0xF9), (0xE7, 0xE9, 0xEE), (0xFF, 0xFF, 0xFF), (0x23, 0x33, 0x50), (0xEF, 0xF1, 0xF4)]

ap = argparse.ArgumentParser()
ap.add_argument("source")
ap.add_argument("--into", required=True)
ap.add_argument("--crop-top", type=int, default=0)
ap.add_argument("--contact", required=True)
args = ap.parse_args()

prefix = "phone" if os.path.basename(os.path.normpath(args.source)) == "phone" else "tablet"
failures = []
tiles = {}
for locale in LOCALES:
    src = os.path.join(args.source, locale)
    if not os.path.isdir(src):
        failures.append(f"{locale}: no directory")
        continue
    files = sorted(f for f in os.listdir(src) if f.endswith(".png"))
    expected = [f"{prefix}-{n}.png" for n in NAMES]
    if files != expected:
        failures.append(f"{locale}: {files} is not {expected}")
        continue
    dest = os.path.join(args.into, locale)
    os.makedirs(dest, exist_ok=True)
    for old in os.listdir(dest):
        if old.endswith(".png"):
            os.remove(os.path.join(dest, old))
    for name in expected:
        im = Image.open(os.path.join(src, name)).convert("RGB")
        if args.crop_top:
            im = im.crop((0, args.crop_top, im.width, im.height))
        if im.size not in SIZES:
            failures.append(f"{locale}/{name}: {im.size} is not a listing size")
        small = im.resize((im.width // 8, im.height // 8))
        pixels = small.get_flattened_data() if hasattr(small, "get_flattened_data") else small.getdata()
        dominant = Counter(pixels).most_common(1)[0][0]
        if not any(all(abs(a - b) <= 8 for a, b in zip(dominant, surface)) for surface in SURFACES):
            failures.append(f"{locale}/{name}: dominant colour {dominant} is not a Day surface, a tint or a dark palette")
        im.save(os.path.join(dest, name), "PNG", optimize=True)
        tiles[(locale, name)] = im.copy()
        tiles[(locale, name)].thumbnail((200, 440))

if tiles:
    cell_w, cell_h = 210, 470
    sheet = Image.new("RGB", (len(NAMES) * cell_w + 90, len(LOCALES) * cell_h + 30), "white")
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 16)
    except OSError:
        font = ImageFont.load_default()
    for row, locale in enumerate(LOCALES):
        draw.text((8, 20 + row * cell_h + cell_h // 2), locale, fill="black", font=font)
        for col, n in enumerate(NAMES):
            tile = tiles.get((locale, f"{prefix}-{n}.png"))
            if tile is None:
                continue
            x, y = 90 + col * cell_w, 30 + row * cell_h
            sheet.paste(tile, (x, y))
            if row == 0:
                draw.text((x, 6), n, fill="black", font=font)
    os.makedirs(os.path.dirname(os.path.abspath(args.contact)), exist_ok=True)
    sheet.save(args.contact)
    print("contact sheet", args.contact, sheet.size)

for failure in failures:
    print("REFUSED", failure)
print(f"{len(tiles)} images into {args.into}")
sys.exit(1 if failures else 0)
