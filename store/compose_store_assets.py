#!/usr/bin/env python3
"""Baut die Play-Store-Assets aus echten Bildschirmen des Spiels.

Die Rohbilder rendert `StoreShots` (ui/src/jvmTest) aus dem echten
GameScreen, von einem Bot gespielt — nichts daran ist nachgezeichnet.
Dieses Skript legt nur die Werbe-Zeile darüber und setzt das Bild als
Karte auf den Himmel des Motivs:

    SHOTS_DIR=$PWD/build/store-shots ./gradlew :ui:jvmTest \\
        --tests '*StoreShots*' --rerun
    python3 store/compose_store_assets.py build/store-shots/store

Ergebnis:
  store/screenshots/{de,en}/0N-*.png   1080x1920, je 7 Motive
  store/feature-graphic.png            1024x500, deutsch
  store/feature-graphic-en.png         1024x500, englisch

Die Werbe-Zeilen stehen in Silkscreen Bold (SIL OFL, store/fonts/): Der
Bytesized-Font des Spiels rendert M wie N und W wie V, und auf einem
Store-Bild muss die Zeile beim ersten Blick sitzen. Im Spiel selbst
ändert sich nichts. Der Schriftzug DOTTIE. bleibt in Bytesized — er hat
weder M noch W, und er ist das Logo.

Was Play verlangt, halten die Bilder ein: 9:16 bzw. exakt 1024x500,
PNG ohne Alphakanal, kein Geräterahmen.
"""

import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
CAPTION_FONT = os.path.join(HERE, "fonts", "Silkscreen-Bold.ttf")
LOGO_FONT = os.path.join(
    REPO, "ui", "src", "commonMain", "composeResources", "font", "bytesized_regular.ttf"
)

W, H = 1080, 1920
OUTLINE = (0x52, 0x3A, 0x4B)  # Konturfarbe des Spiels
SHADOW = (0x2A, 0x1E, 0x2A)
WHITE = (0xFF, 0xFF, 0xFF)
YELLOW = (0xFF, 0xD9, 0x3C)

# Motiv -> (Zeile 1, Zeile 2) je Sprache. {skins}, {scenes}, {sounds},
# {twists} kommen aus counts.json, also aus dem Code.
CAPTIONS = {
    "de": {
        "01-gameplay": ("EIN TAP.", "PERFEKT ODER VORBEI."),
        "02-bomben": ("NIE AUF BOMBEN TIPPEN!", "{twists} TWISTS HALTEN DICH WACH"),
        "03-nebel": ("DER PUNKT VERSCHWINDET.", "TRIFF BLIND FÜR BONUS"),
        "04-knapp": ("SO KNAPP.", "NOCH EINMAL!"),
        "05-daily": ("JEDEN TAG EIN NEUER LAUF", "GLEICH FÜR ALLE. WER IST BESSER?"),
        "06-tempo": ("SCHNELLER. LANGSAMER.", "BIS IN DEN WELTRAUM"),
        "07-sammlung": ("{skins} VÖGEL ZUM SAMMELN", "{scenes} WELTEN · {sounds} KLANGWELTEN"),
    },
    "en": {
        "01-gameplay": ("ONE TAP.", "PERFECT OR OVER."),
        "02-bomben": ("NEVER TAP A BOMB!", "{twists} TWISTS KEEP YOU SHARP"),
        "03-nebel": ("THE DOT DISAPPEARS.", "HIT IT BLIND FOR A BONUS"),
        "04-knapp": ("SO CLOSE.", "ONE MORE TRY!"),
        "05-daily": ("A NEW RUN EVERY DAY", "SAME FOR EVERYONE. WHO WINS?"),
        "06-tempo": ("FASTER. SLOWER.", "ALL THE WAY TO SPACE"),
        "07-sammlung": ("{skins} BIRDS TO COLLECT", "{scenes} WORLDS · {sounds} SOUND SETS"),
    },
}

FEATURE = {
    "de": ("EIN TAP ENTSCHEIDET.", "PERFEKT ODER VORBEI."),
    "en": ("ONE TAP DECIDES.", "PERFECT OR OVER."),
}


def font(path, size):
    return ImageFont.truetype(path, size)


def sky_colour(img):
    """Die häufigste Farbe im oberen Streifen: der Himmel ohne Sterne."""
    strip = img.crop((0, 0, img.width, img.height // 12)).convert("RGB")
    colours = strip.getcolors(strip.width * strip.height)
    return max(colours)[1]


def fitted(draw, text, path, size, max_width):
    f = font(path, size)
    while draw.textlength(text, font=f) > max_width and size > 20:
        size -= 2
        f = font(path, size)
    return f


def shadow_text(draw, xy, text, f, fill, depth):
    x, y = xy
    draw.text((x + depth, y + depth), text, font=f, fill=SHADOW)
    draw.text((x, y), text, font=f, fill=fill)


def centred(draw, y, text, f, fill, depth, width=W):
    tw = draw.textlength(text, font=f)
    shadow_text(draw, ((width - tw) / 2, y), text, f, fill, depth)


def screenshot(raw_path, lines, out_path):
    raw = Image.open(raw_path).convert("RGB")
    sky = sky_colour(raw)
    out = Image.new("RGB", (W, H), sky)
    d = ImageDraw.Draw(out)

    top, sub = lines
    f1 = fitted(d, top, CAPTION_FONT, 76, W - 90)
    f2 = fitted(d, sub, CAPTION_FONT, 46, W - 90)
    centred(d, 92, top, f1, WHITE, 7)
    centred(d, 206, sub, f2, YELLOW, 5)

    # Das echte Bild als Karte: Pixelkontur und harter Schatten, unten
    # angeschnitten — nur Sand und Straße fallen weg, die Bodenkante
    # (88 % Höhe) bleibt im Bild.
    card_w = 760
    card_h = round(raw.height * card_w / raw.width)
    card = raw.resize((card_w, card_h), Image.NEAREST)
    x = (W - card_w) // 2
    y = 330
    border = 10
    d.rectangle((x - border + 18, y - border + 18, x + card_w + border + 18, H), fill=SHADOW)
    d.rectangle((x - border, y - border, x + card_w + border, H), fill=OUTLINE)
    out.paste(card, (x, y))

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    out.save(out_path, optimize=True)
    print("->", os.path.relpath(out_path, REPO))


def feature_graphic(raw_path, lines, out_path):
    fw, fh = 1024, 500
    raw = Image.open(raw_path).convert("RGB")
    sky = sky_colour(raw)
    out = Image.new("RGB", (fw, fh), sky)
    d = ImageDraw.Draw(out)

    # Baumkronen, Gras und Sand des echten Bildes als Streifen unten:
    # 1024 px breit geschnitten ab knapp unter den Baumkronen bis in den
    # Sand unter der Bodenkante (88 % Höhe).
    scale = fw / raw.width
    scaled = raw.resize((fw, round(raw.height * scale)), Image.NEAREST)
    edge = round(raw.height * 0.88 * scale)
    ground = scaled.crop((0, edge - 150, fw, edge + 60))
    out.paste(ground, (0, fh - ground.height))

    # Ring und Vogel aus demselben Bild, rechts im Himmel.
    ring = raw.crop((110, 560, 970, 1480))
    rh = 270
    ring = ring.resize((round(ring.width * rh / ring.height), rh), Image.NEAREST)
    out.paste(ring, (fw - ring.width - 60, 14))

    logo = font(LOGO_FONT, 104)
    shadow_text(d, (48, 36), "DOTTIE.", logo, WHITE, 8)
    top, sub = lines
    f1 = fitted(d, top, CAPTION_FONT, 36, 560)
    f2 = fitted(d, sub, CAPTION_FONT, 36, 560)
    shadow_text(d, (54, 170), top, f1, WHITE, 4)
    shadow_text(d, (54, 222), sub, f2, YELLOW, 4)

    out.save(out_path, optimize=True)
    print("->", os.path.relpath(out_path, REPO))


def main():
    if len(sys.argv) != 2:
        sys.exit("Aufruf: compose_store_assets.py <SHOTS_DIR>/store")
    raw_root = sys.argv[1]
    with open(os.path.join(raw_root, "counts.json")) as fh:
        counts = json.load(fh)

    for lang, motifs in CAPTIONS.items():
        target = os.path.join(HERE, "screenshots", lang)
        # Alte Motive räumen: Play zeigt, was im Ordner liegt.
        if os.path.isdir(target):
            for name in os.listdir(target):
                if name.endswith(".png"):
                    os.remove(os.path.join(target, name))
        for name, (top, sub) in motifs.items():
            screenshot(
                os.path.join(raw_root, lang, name + ".png"),
                (top.format(**counts), sub.format(**counts)),
                os.path.join(target, name + ".png"),
            )

    feature_graphic(os.path.join(raw_root, "de", "01-gameplay.png"), FEATURE["de"],
                    os.path.join(HERE, "feature-graphic.png"))
    feature_graphic(os.path.join(raw_root, "en", "01-gameplay.png"), FEATURE["en"],
                    os.path.join(HERE, "feature-graphic-en.png"))


if __name__ == "__main__":
    main()
