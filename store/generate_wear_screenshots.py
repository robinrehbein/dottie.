#!/usr/bin/env python3
"""Generiert Play-Store-Screenshots fuer die Wear-App (512x512, 1:1) in
Deutsch und Englisch — wie alle Assets komplett aus Code.

Die Szenen spiegeln den echten Wear-Prototyp (WearRenderer.kt /
WearGameScreen.kt): rundes Display auf schwarzem Grund, Bahn als
Perlenkette mit 40 Segmenten, Pixel-Vogel, Score mittig. Als Schrift
dient DejaVu Sans Bold als Stellvertreter fuer das Roboto-Bold der Uhr —
die Wear-App nutzt bewusst NICHT den Bytesized-Font des Phones.
Ausfuehren aus dem Repo-Root:

    python3 store/generate_wear_screenshots.py
"""

import math
import os
from PIL import Image, ImageDraw, ImageFont

# Play verlangt fuer Wear mindestens 384x384 im Format 1:1 — 512 laesst
# etwas Reserve fuer scharfe Darstellung.
SIZE = 512
# Skalierung sp -> px: Galaxy-Watch-Displays haben ~450 px Kantenlaenge,
# dort entspricht 1 sp etwa 1 px. Auf 512 px hochgerechnet:
SP = SIZE / 450.0

# ===== Palette 1:1 aus WearRenderer.kt =====
SKY_STAGES = [
    (0x4E, 0xC0, 0xCA), (0x5B, 0x9B, 0xD5), (0x7B, 0x6F, 0xD0),
    (0xC0, 0x61, 0x6F), (0xD9, 0x8A, 0x3D), (0x3D, 0x4A, 0x8C),
    (0x2A, 0x26, 0x40),
]
OUTLINE = (0x54, 0x38, 0x47)
TRACK_DEFAULT = (0xD3, 0xC8, 0x7E)
GRASS_LIGHT = (0x9D, 0xE8, 0x5A)
GRASS_DARK = (0x74, 0xBF, 0x2E)
FAKE = (0xB4, 0x4F, 0xD8)
FAKE_CORE = (0x8A, 0x2F, 0xB0)
DOT_BODY = (0xFF, 0xD8, 0x47)
DOT_SHADE = (0xF5, 0xA6, 0x23)
DOT_SHINE = (0xFF, 0xF3, 0xB8)
RECORD_RED = (0xE5, 0x39, 0x35)
WHITE = (0xFF, 0xFF, 0xFF)

FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# Zonen-Geometrie wie in TimingGame: Startbreite der Zone und Anteil des
# Perfekt-Kerns (PERFECT_SHARE).
ZONE_HALF = 0.45
PERFECT_SHARE = 0.35

GRID = 13  # Raster des Pixel-Vogels (WEAR_GRID)

TEXTS = {
    "de": {"tap": "TIPP", "best": "REKORD: {}"},
    "en": {"tap": "TAP", "best": "BEST: {}"},
}


def wrap_pi(v):
    while v <= -math.pi:
        v += 2 * math.pi
    while v > math.pi:
        v -= 2 * math.pi
    return v


class WatchScene:
    """Eine runde Watch-Szene: quadratisches Bild, Spielwelt im Kreis,
    Ecken schwarz wie das echte Gehaeuse-Umfeld."""

    def __init__(self, score_stage):
        sky = SKY_STAGES[min(score_stage, len(SKY_STAGES) - 1)]
        self.world = Image.new("RGB", (SIZE, SIZE), sky)
        self.d = ImageDraw.Draw(self.world)
        # Zell-Groesse wie WearRenderer: floor(d / 220), min. 2
        self.cell = max(2, SIZE // 220)

    # ===== Spielwelt (Geometrie aus drawWearWorld) =====
    def track(self, zone_center, has_fake=False, fake_center=0.0):
        radius = SIZE * 0.38
        cx = cy = SIZE / 2
        segments = 40
        core_half = max(ZONE_HALF * PERFECT_SHARE, math.pi / segments)
        for k in range(segments):
            a = k / segments * 2 * math.pi
            px = cx + math.cos(a) * radius
            py = cy + math.sin(a) * radius

            rel = abs(wrap_pi(a - zone_center))
            in_zone = rel <= ZONE_HALF
            in_core = rel <= core_half
            rel_fake = abs(wrap_pi(a - fake_center))
            in_fake = has_fake and rel_fake <= ZONE_HALF
            in_fake_core = has_fake and rel_fake <= ZONE_HALF * PERFECT_SHARE

            # Zonen-Bloecke wie in WearRenderer.kt: 7f/5f seit dem
            # Geraete-Test (5f/3.4f waren auf der Uhr zu klein).
            highlighted = in_zone or in_fake
            outer = self.cell * (7 if highlighted else 3)
            inner = self.cell * (5 if highlighted else 1.8)
            if in_core:
                color = GRASS_LIGHT
            elif in_zone:
                color = GRASS_DARK
            elif in_fake_core:
                color = FAKE_CORE
            elif in_fake:
                color = FAKE
            else:
                color = TRACK_DEFAULT

            self.d.rectangle(
                [px - outer / 2, py - outer / 2, px + outer / 2, py + outer / 2],
                fill=OUTLINE)
            self.d.rectangle(
                [px - inner / 2, py - inner / 2, px + inner / 2, py + inner / 2],
                fill=color)

    def dot(self, angle, direction=1):
        radius = SIZE * 0.38
        cx = cy = SIZE / 2
        px = cx + math.cos(angle) * radius
        py = cy + math.sin(angle) * radius
        r = SIZE * 0.075
        u = r * 2 / GRID
        mid = (GRID - 1) / 2
        rr = GRID / 2 - 0.25

        for row in range(GRID):
            for col in range(GRID):
                dist = math.hypot(col - mid, row - mid)
                if dist > rr:
                    continue
                if dist > rr - 1.1:
                    c = OUTLINE
                elif row + col > GRID * 1.15:
                    c = DOT_SHADE
                else:
                    c = DOT_BODY
                self.d.rectangle(
                    [px - r + col * u, py - r + row * u,
                     px - r + (col + 1) * u, py - r + (row + 1) * u],
                    fill=c)

        def cellrect(col, row, cols, rows, color):
            self.d.rectangle(
                [px - r + col * u, py - r + row * u,
                 px - r + (col + cols) * u, py - r + (row + rows) * u],
                fill=color)

        # Auge/Glanzpunkt in Flugrichtung (drawWearDot)
        facing_left = math.sin(angle) * direction > 0
        if facing_left:
            cellrect(GRID - 4.5, 2.5, 2, 2, DOT_SHINE)
            cellrect(2, 3, 3.5, 4, WHITE)
            cellrect(2, 4, 1.5, 2, OUTLINE)
        else:
            cellrect(2.5, 2.5, 2, 2, DOT_SHINE)
            cellrect(7.5, 3, 3.5, 4, WHITE)
            cellrect(9.5, 4, 1.5, 2, OUTLINE)

    # ===== Overlays (Typo aus WearGameScreen.kt) =====
    def text_center(self, dy_sp, s, size_sp, color):
        font = ImageFont.truetype(FONT_PATH, int(size_sp * SP))
        w = self.d.textlength(s, font=font)
        asc, desc = font.getmetrics()
        x = (SIZE - w) / 2
        y = SIZE / 2 + dy_sp * SP - (asc + desc) / 2
        # Duenner dunkler Schatten, damit Weiss auch auf dem Tag-Himmel
        # lesbar bleibt (die Uhr loest das ueber Bold-Gewicht).
        off = max(1, int(size_sp * SP) // 22)
        self.d.text((x + off, y + off), s, font=font, fill=OUTLINE)
        self.d.text((x, y), s, font=font, fill=color)

    def finish(self, path):
        """Rundes Display ausstanzen: Ecken schwarz, 4x-Supersampling der
        Maske gegen Treppchen an der Kreiskante."""
        mask = Image.new("L", (SIZE * 4, SIZE * 4), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, SIZE * 4 - 1, SIZE * 4 - 1], fill=255)
        mask = mask.resize((SIZE, SIZE), Image.LANCZOS)
        out = Image.new("RGB", (SIZE, SIZE), (0, 0, 0))
        out.paste(self.world, (0, 0), mask)
        out.save(path)
        print(path)


def main():
    for lang, t in TEXTS.items():
        outdir = f"store/screenshots/wear/{lang}"
        os.makedirs(outdir, exist_ok=True)

        # ===== 01: Kern-Gameplay — Punkt laeuft auf die gruene Zone zu,
        # Score mittig wie im RUNNING-Overlay.
        s = WatchScene(score_stage=1)  # Score 7 -> zweite Himmelsstufe
        zone = -0.9
        s.track(zone_center=zone)
        s.dot(angle=zone - 1.15, direction=1)
        s.text_center(0, "7", 44, WHITE)
        s.finish(f"{outdir}/wear-01-gameplay.png")

        # ===== 02: Startscreen — blinkendes TIPP/TAP + Rekordzeile.
        s = WatchScene(score_stage=0)
        s.track(zone_center=2.3)
        s.dot(angle=0.6, direction=1)
        s.text_center(-6, t["tap"], 26, WHITE)
        s.text_center(22, t["best"].format(23), 16, WHITE)
        s.finish(f"{outdir}/wear-02-ready.png")

        # ===== 03: Falle-Twist am Abendhimmel — gruene Zone und violette
        # Koeder-Zone gleichzeitig, Score 21.
        s = WatchScene(score_stage=4)  # Score 21 -> Sonnenuntergang
        s.track(zone_center=-0.7, has_fake=True, fake_center=2.1)
        s.dot(angle=-2.4, direction=1)
        s.text_center(0, "21", 44, WHITE)
        s.finish(f"{outdir}/wear-03-twist.png")

        # ===== 04: Game Over bei Nacht — Score gross, neuer Rekord rot,
        # TIPP-Hinweis (OVER-Overlay).
        s = WatchScene(score_stage=6)  # Score 34 -> Nacht
        s.track(zone_center=1.9)
        s.text_center(-22, "34", 40, WHITE)
        s.text_center(10, t["best"].format(34), 18, RECORD_RED)
        s.text_center(36, t["tap"], 16, WHITE)
        s.finish(f"{outdir}/wear-04-gameover.png")


if __name__ == "__main__":
    main()
