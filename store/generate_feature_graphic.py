#!/usr/bin/env python3
"""Generiert die Play-Store-Feature-Grafik (1024x500) im Pixel-Look des
Spiels — wie die App selbst komplett aus Code, ohne gemalte Assets.

Die Farben entsprechen der Spiel-Palette (GameOverlays/TimingGameScreen);
der Himmel erzaehlt links-nach-rechts die Stufen-Progression von Tag zu
Nacht. Ausfuehren aus dem Repo-Root:

    python3 store/generate_feature_graphic.py
"""

import math
from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
CELL = 4

# Spiel-Palette
SKY_STAGES = [
    (0x4E, 0xC0, 0xCA), (0x5B, 0x9B, 0xD5), (0x7B, 0x6F, 0xD0),
    (0xC0, 0x61, 0x6F), (0xD9, 0x8A, 0x3D), (0x3D, 0x4A, 0x8C),
    (0x2A, 0x26, 0x40),
]
OUTLINE = (0x54, 0x38, 0x47)
CLOUD = (0xE9, 0xFC, 0xFD)
SAND = (0xDE, 0xD8, 0x95)
SAND_SHADE = (0xD3, 0xC8, 0x7E)
GRASS_LIGHT = (0x9D, 0xE8, 0x5A)
GRASS_DARK = (0x74, 0xBF, 0x2E)
DOT_BODY = (0xFF, 0xD8, 0x47)
DOT_SHADE = (0xF5, 0xA6, 0x23)
DOT_SHINE = (0xFF, 0xF3, 0xB8)
FAKE = (0xB4, 0x4F, 0xD8)
WHITE = (0xFF, 0xFF, 0xFF)

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
cloud(250, 140, 5)

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

# ===== Kreisbahn mit Zielzone, Fallen-Zone und Punkt (rechte Haelfte) =====
cx, cy, radius = 790, 218, 150
zone_center = -0.55  # oben rechts
zone_half = 0.42
fake_center = 2.4


def wrap_pi(v):
    while v <= -math.pi:
        v += 2 * math.pi
    while v > math.pi:
        v -= 2 * math.pi
    return v


for k in range(72):
    a = k / 72 * 2 * math.pi
    px = cx + math.cos(a) * radius
    py = cy + math.sin(a) * radius
    rel = abs(wrap_pi(a - zone_center))
    rel_fake = abs(wrap_pi(a - fake_center))
    in_zone = rel <= zone_half
    in_core = rel <= zone_half * 0.35
    in_fake = rel_fake <= 0.30
    highlighted = in_zone or in_fake
    outer = 20 if highlighted else 12
    inner = 14 if highlighted else 7
    if in_core:
        color = GRASS_LIGHT
    elif in_zone:
        color = GRASS_DARK
    elif in_fake:
        color = FAKE
    else:
        color = SAND_SHADE
    d.rectangle([px - outer / 2, py - outer / 2, px + outer / 2, py + outer / 2], fill=OUTLINE)
    d.rectangle([px - inner / 2, py - inner / 2, px + inner / 2, py + inner / 2], fill=color)


def pixel_circle(cx_, cy_, r, body, shade, shine=None, eye=False):
    grid = 13
    u = r * 2 / grid
    mid = (grid - 1) / 2
    rr = grid / 2 - 0.25
    for row in range(grid):
        for col in range(grid):
            dist = math.hypot(col - mid, row - mid)
            if dist > rr:
                continue
            if dist > rr - 1.1:
                c = OUTLINE
            elif row + col > grid * 1.15:
                c = shade
            else:
                c = body
            d.rectangle(
                [cx_ - r + col * u, cy_ - r + row * u,
                 cx_ - r + (col + 1) * u, cy_ - r + (row + 1) * u],
                fill=c,
            )
    if shine:
        d.rectangle([cx_ - r + 2.5 * u, cy_ - r + 2.5 * u,
                     cx_ - r + 4.5 * u, cy_ - r + 4.5 * u], fill=shine)
    if eye:
        d.rectangle([cx_ - r + 7.5 * u, cy_ - r + 3 * u,
                     cx_ - r + 11 * u, cy_ - r + 7 * u], fill=WHITE)
        d.rectangle([cx_ - r + 9.5 * u, cy_ - r + 4 * u,
                     cx_ - r + 11 * u, cy_ - r + 6 * u], fill=OUTLINE)


# Der Punkt kurz vor der Zone, Blickrichtung zur Zone
dot_a = zone_center - 1.15
pixel_circle(
    cx + math.cos(dot_a) * radius, cy + math.sin(dot_a) * radius, 34,
    DOT_BODY, DOT_SHADE, DOT_SHINE, eye=True,
)

# ===== Titel und Tagline (linke Haelfte) =====
font_big = ImageFont.truetype("app/src/main/res/font/bytesized_regular.ttf", 150)
font_small = ImageFont.truetype("app/src/main/res/font/bytesized_regular.ttf", 52)


def shadowed(pos, text, font, color, shadow=6):
    d.text((pos[0] + shadow, pos[1] + shadow), text, font=font, fill=OUTLINE)
    d.text(pos, text, font=font, fill=color)


shadowed((60, 100), "PUNKT.", font_big, WHITE)
shadowed((66, 272), "EIN PUNKT. EIN DAUMEN.", font_small, WHITE)
shadowed((66, 334), "KEIN ERBARMEN.", font_small, DOT_BODY)

img.save("store/feature-graphic.png")
print("store/feature-graphic.png:", img.size)
