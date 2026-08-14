#!/usr/bin/env python3
"""Generiert die Play-Store-Feature-Grafiken (1024x500) im Pixel-Look des
Spiels — wie die App selbst komplett aus Code, ohne gemalte Assets.

Die Farben entsprechen der Spiel-Palette (GameOverlays/TimingGameScreen);
der Himmel erzaehlt links-nach-rechts die Stufen-Progression von Tag zu
Nacht. Seit v2.20 laeuft unter der Tagline eine Reihe echter Skins mit:
Die 42 Skins sind das Argument dieses Updates, und die Feature-Grafik ist
die einzige Flaeche, die Play immer zeigt.

Die Vogel-Reihe ist keine Malerei — jedes ihrer 13x13 Felder kommt aus
`store/skin_paint.py`, der geprueften Portierung von `SkinPaint.kt`.

Das Listing ist zweisprachig (en-US als Standard, de-DE als
Uebersetzung), also entstehen zwei Dateien: `feature-graphic.png`
(deutsch, Name unveraendert) und `feature-graphic-en.png`. Ausfuehren aus
dem Repo-Root:

    python3 store/generate_feature_graphic.py
"""

import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import skin_paint as sp  # noqa: E402
from pixel_dot import OUTLINE, paste_dot  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

W, H = 1024, 500
CELL = 4

# Spiel-Palette
SKY_STAGES = [sp.rgb(c) for c in sp.SKY_STAGES]
CLOUD = (0xE9, 0xFC, 0xFD)
SAND = (0xDE, 0xD8, 0x95)
SAND_SHADE = (0xD3, 0xC8, 0x7E)
GRASS_LIGHT = (0x9D, 0xE8, 0x5A)
GRASS_DARK = (0x74, 0xBF, 0x2E)
DOT_BODY = sp.rgb(sp.BODY["KLASSIK"])
DOT_SHINE = sp.rgb(sp.shine("KLASSIK"))
WHITE = (0xFF, 0xFF, 0xFF)

FONT_PATH = os.path.join(REPO, "app/src/main/res/font/bytesized_regular.ttf")

# Taglines bewusst ohne "M": Der Bytesized-Font rendert das M wie ein N
# und macht es auf einem Store-Asset unleserlich.
TAGLINES = {
    "de": ("EIN TAP ENTSCHEIDET.", "PERFEKT ODER VORBEI."),
    "en": ("ONE TAP DECIDES.", "PERFECT OR IT'S OVER."),
}

# Die Reihe unter der Tagline: neun Skins quer durch alle sechs
# Familien, damit auf den ersten Blick klar ist, dass hier gesammelt
# wird — einfarbig, gemustert, bewegt, reagierend, Saison, Goenner.
STRIP = [
    ("MINZE", sp.DEFAULT_STATE),                    # einfarbig
    ("BIENE", sp.DEFAULT_STATE),                    # gemustert
    ("MELONE", sp.DEFAULT_STATE),
    ("KOI", sp.DEFAULT_STATE),
    ("GALAXIE", sp.DEFAULT_STATE),
    ("REGENBOGEN", sp.SkinState(elapsed=5.5556)),   # bewegt, Farbton 310
    ("MEDAILLE", sp.SkinState(score=10)),           # reagierend, Bronze
    ("DIAMANT", sp.SkinState(elapsed=1.0)),         # Goenner
    ("KUERBIS", sp.DEFAULT_STATE),                  # Saison
]


def wrap_pi(v):
    while v <= -math.pi:
        v += 2 * math.pi
    while v > math.pi:
        v -= 2 * math.pi
    return v


def build(lang):
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)

    # ===== Himmel: Stufen-Verlauf Tag -> Nacht in diskreten Baendern =====
    band_w = W / len(SKY_STAGES)
    for i, color in enumerate(SKY_STAGES):
        d.rectangle([i * band_w, 0, (i + 1) * band_w, H], fill=color)

    # Sterne auf den Nacht-Baendern (deterministisch gestreut ueber zwei
    # teilerfremde Perioden, damit keine sichtbaren Linien entstehen)
    for k in range(26):
        x = W * 0.57 + ((k * k * 263 + k * 71) % 431) / 431 * (W * 0.41)
        y = ((k * k * 149 + k * 37) % 353) / 353 * (H * 0.55)
        s = 4 if k % 3 else 6
        d.rectangle([x, y, x + s, y + s], fill=WHITE if k % 4 else DOT_SHINE)

    def cloud(x, y, u):
        d.rectangle([x, y + u * 2, x + u * 14, y + u * 5], fill=CLOUD)
        d.rectangle([x + u * 2, y, x + u * 9, y + u * 2], fill=CLOUD)
        d.rectangle([x + u * 4, y - u * 1.5, x + u * 8, y], fill=CLOUD)

    cloud(40, 60, 7)
    # Die kleine Wolke sitzt wie bisher knapp ueber dem "TT" des Titels.
    cloud(250, 108, 5)

    # ===== Boden =====
    ground_top = H - 64
    d.rectangle([0, ground_top, W, H], fill=SAND)
    d.rectangle([0, ground_top + 34, W, ground_top + 42], fill=SAND_SHADE)
    d.rectangle([0, ground_top, W, ground_top + 20], fill=GRASS_DARK)
    x = 0
    while x < W:
        d.rectangle([x, ground_top, x + 20, ground_top + 16], fill=GRASS_LIGHT)
        x += 40
    d.rectangle([0, ground_top - 4, W, ground_top], fill=OUTLINE)

    # ===== Kreisbahn mit Zielzone und Punkt (rechte Haelfte) =====
    # Variante B: Der Punkt ist groesser (Held des Bildes) und steht
    # sichtbar kurz vor der Zone — die Kernmechanik "gleich musst du
    # tippen" soll ohne Nachdenken lesbar sein. Die Fallen-Zone ist raus:
    # In der Statik wirft sie nur Fragen auf.
    cx, cy, radius = 790, 210, 150
    zone_center = -0.9  # oben rechts
    zone_half = 0.5

    for k in range(72):
        a = k / 72 * 2 * math.pi
        px = cx + math.cos(a) * radius
        py = cy + math.sin(a) * radius
        rel = abs(wrap_pi(a - zone_center))
        in_zone = rel <= zone_half
        in_core = rel <= zone_half * 0.35
        outer = 20 if in_zone else 12
        inner = 14 if in_zone else 7
        if in_core:
            color = GRASS_LIGHT
        elif in_zone:
            color = GRASS_DARK
        else:
            color = SAND_SHADE
        d.rectangle([px - outer / 2, py - outer / 2,
                     px + outer / 2, py + outer / 2], fill=OUTLINE)
        d.rectangle([px - inner / 2, py - inner / 2,
                     px + inner / 2, py + inner / 2], fill=color)

    # Der Punkt kurz vor der Zone (laeuft im Uhrzeigersinn auf sie zu),
    # Auge blickt in Laufrichtung.
    dot_a = zone_center - 1.25
    paste_dot(img, cx + math.cos(dot_a) * radius, cy + math.sin(dot_a) * radius,
              46, "KLASSIK", facing_left=math.sin(dot_a) > 0)

    # ===== Titel und Tagline (linke Haelfte) =====
    # "DOTTIE." (7 Zeichen) passt mit vollen 150px links von der
    # Kreisbahn (linker Rand bei x ~ 620).
    font_big = ImageFont.truetype(FONT_PATH, 150)
    font_small = ImageFont.truetype(FONT_PATH, 48)

    def shadowed(pos, text, font, color, shadow=6):
        d.text((pos[0] + shadow, pos[1] + shadow), text, font=font, fill=OUTLINE)
        d.text(pos, text, font=font, fill=color)

    line1, line2 = TAGLINES[lang]
    shadowed((60, 68), "DOTTIE.", font_big, WHITE)
    shadowed((66, 236), line1, font_small, WHITE)
    shadowed((66, 290), line2, font_small, DOT_BODY)

    # ===== Skin-Reihe unter der Tagline =====
    r, spacing = 24, 57
    for k, (skin, state) in enumerate(STRIP):
        paste_dot(img, 90 + k * spacing, 390, r, skin, state)

    out = "store/feature-graphic.png" if lang == "de" \
        else "store/feature-graphic-en.png"
    img.save(os.path.join(REPO, out))
    print(out + ":", img.size)


if __name__ == "__main__":
    for lang in ("de", "en"):
        build(lang)
