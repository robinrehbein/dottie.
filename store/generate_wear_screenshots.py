#!/usr/bin/env python3
"""Generiert Play-Store-Screenshots fuer die Wear-App (512x512, 1:1) in
Deutsch und Englisch — wie alle Assets komplett aus Code.

Die Szenen spiegeln den echten Wear-Prototyp (WearRenderer.kt /
WearGameScreen.kt): rundes Display auf schwarzem Grund, Bahn als
Perlenkette mit 40 Segmenten, Pixel-Vogel, Score mittig. Als Schrift
dient DejaVu Sans Bold als Stellvertreter fuer das Roboto-Bold der Uhr —
die Wear-App nutzt bewusst NICHT den Bytesized-Font des Phones.
Seit v2.28: Himmel aus `store/skin_paint.py` (ohne Lila), die Falle als
Kette aus Minen ohne Lauflicht (TrapPaint, ueber `store/twist_paint.py`),
das einfache Nebelband der Uhr und ein ruhiges TIPP (es blinkt nicht mehr;
der erste Tap im Gruen zaehlt).

Ausfuehren aus dem Repo-Root:

    python3 store/generate_wear_screenshots.py
"""

import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import skin_paint as sp  # noqa: E402
import twist_paint as tp  # noqa: E402

# Play verlangt fuer Wear mindestens 384x384 im Format 1:1 — 512 laesst
# etwas Reserve fuer scharfe Darstellung.
SIZE = 512
# Skalierung sp -> px: Galaxy-Watch-Displays haben ~450 px Kantenlaenge,
# dort entspricht 1 sp etwa 1 px. Auf 512 px hochgerechnet:
SP = SIZE / 450.0

# ===== Palette 1:1 aus WearRenderer.kt =====
# Der Himmel kommt wie auf der Uhr aus ScenePaint (Welt WIESE = die
# Stufen aus SkinPaint), hier ueber die gepruefte Portierung.
SKY_STAGES = [sp.rgb(c) for c in sp.SKY_STAGES]
OUTLINE = (0x54, 0x38, 0x47)
TRACK_DEFAULT = (0xD3, 0xC8, 0x7E)
GRASS_LIGHT = (0x9D, 0xE8, 0x5A)
GRASS_DARK = (0x74, 0xBF, 0x2E)
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
        """Bahn wie drawWearWorld. Mit [has_fake] liegen auf der Falle
        Minen statt Bloecke, ohne Lauflicht (die Uhr hat keins)."""
        radius = SIZE * 0.38
        cx = cy = SIZE / 2
        segments = 40
        core_half = max(ZONE_HALF * PERFECT_SHARE, math.pi / segments)
        mines = []
        for k in range(segments):
            a = k / segments * 2 * math.pi
            px = cx + math.cos(a) * radius
            py = cy + math.sin(a) * radius

            rel = abs(wrap_pi(a - zone_center))
            in_zone = rel <= ZONE_HALF
            in_core = rel <= core_half
            rel_fake = abs(wrap_pi(a - fake_center))
            if has_fake and not in_zone and rel_fake <= ZONE_HALF:
                mines.append((px, py))
                continue

            # Zonen-Bloecke wie in WearRenderer.kt: 7f/5f seit dem
            # Geraete-Test (5f/3.4f waren auf der Uhr zu klein).
            outer = self.cell * (7 if in_zone else 3)
            inner = self.cell * (5 if in_zone else 1.8)
            if in_core:
                color = GRASS_LIGHT
            elif in_zone:
                color = GRASS_DARK
            else:
                color = TRACK_DEFAULT

            self.d.rectangle(
                [px - outer / 2, py - outer / 2, px + outer / 2, py + outer / 2],
                fill=OUTLINE)
            self.d.rectangle(
                [px - inner / 2, py - inner / 2, px + inner / 2, py + inner / 2],
                fill=color)
        # Die Minen, schwarz: kein Lauflicht auf der Uhr.
        for px, py in mines:
            tp.draw_mine(self.d, px, py, self.cell + 1)

    def fog_band(self, start, end):
        """Das Nebelband der Uhr (drawFogBand): Bloecke von [start] bis
        [end] auf der Bahn, Rand #A0BEDA, Mitte #D6E5F4, Kern #F4F8FD,
        ein Block etwas groesser als der Vogel."""
        radius = SIZE * 0.38
        cx = cy = SIZE / 2
        u = SIZE * 0.075 * 2 / GRID
        edge = max(1, round(u))
        outer = round(u * (GRID + 2))
        mid = outer - 2 * edge
        core = mid - 4 * edge
        n = max(1, math.ceil(abs(end - start) * radius / (outer / 2)))
        angles = [start + (end - start) * i / n for i in range(n + 1)]
        bottom, _, fog_mid, _, inner = tp.fog_layers()
        for extent, color in ((outer, bottom), (mid, fog_mid), (core, inner)):
            for a in angles:
                px = round(cx + math.cos(a) * radius)
                py = round(cy + math.sin(a) * radius)
                self.d.rectangle([px - extent / 2, py - extent / 2,
                                  px + extent / 2, py + extent / 2], fill=color)

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

        # ===== 02: Startscreen — ruhiges TIPP/TAP + Rekordzeile. Der erste
        # Tap im Gruen ist schon Treffer 1.
        s = WatchScene(score_stage=0)
        s.track(zone_center=2.3)
        s.dot(angle=0.6, direction=1)
        s.text_center(-6, t["tap"], 26, WHITE)
        s.text_center(22, t["best"].format(23), 16, WHITE)
        s.finish(f"{outdir}/wear-02-ready.png")

        # ===== 03: Bomben am Abendhimmel — gruene Zone und die Minenkette
        # daneben, Score 21.
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

        # ===== 05: Nebel — das Band liegt vor der Zone, der Vogel kommt
        # gleich an und verschwindet darin. Score 17.
        s = WatchScene(score_stage=3)
        zone = 0.9
        speed = 2.4 + 16 * 0.07
        s.track(zone_center=zone)
        s.fog_band(zone - ZONE_HALF - 0.12 * speed, zone - ZONE_HALF * 0.5)
        s.dot(angle=zone - ZONE_HALF - 0.12 * speed - 0.7, direction=1)
        s.text_center(0, "17", 44, WHITE)
        s.finish(f"{outdir}/wear-05-nebel.png")


if __name__ == "__main__":
    main()
