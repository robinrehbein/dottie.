#!/usr/bin/env python3
"""Generiert stilisierte Play-Store-Screenshots (1080x1920, 9:16) in
Deutsch und Englisch — wie Feature-Grafik und App komplett aus Code.

Vier Motive: Kern-Gameplay, Twists, Daily Challenge, Skins. Die Szenen
spiegeln das echte Spiel (Palette, Bahn mit 60 Segmenten, Baeume/
Blumen/Buesche der v2.11-Szenerie), die Captions sind bewusst M-frei —
der Bytesized-Font rendert das M unleserlich. Ausfuehren aus dem
Repo-Root:

    python3 store/generate_screenshots.py
"""

import math
import os
from PIL import Image, ImageDraw, ImageFont

W, H = 1080, 1920
CELL = 8  # entspricht floor(1920 / 220) im Spiel

# ===== Spiel-Palette =====
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
BUSH = (0x71, 0xC8, 0x37)
BUSH_SHADE = (0x5A, 0xA8, 0x2C)
TRUNK = (0x9C, 0x6B, 0x3C)
TRUNK_SHADE = (0x7A, 0x4E, 0x2A)
FAKE = (0xB4, 0x4F, 0xD8)
FAKE_CORE = (0x8A, 0x2F, 0xB0)
ACCENT = (0xFF, 0x8A, 0x3C)
RECORD_RED = (0xE5, 0x39, 0x35)
GOLD = (0xFF, 0xD8, 0x47)
RECORD_YELLOW = (0xFF, 0xE9, 0x5E)
WHITE = (0xFF, 0xFF, 0xFF)

SKINS = [
    ((0xFF, 0xD8, 0x47), (0xF5, 0xA6, 0x23), (0xFF, 0xF3, 0xB8)),  # Klassik
    ((0x4B, 0xE3, 0x8C), (0x2B, 0xA5, 0x5E), (0xC8, 0xFF, 0xE0)),  # Minze
    ((0xFF, 0x5A, 0x36), (0xC2, 0x2F, 0x12), (0xFF, 0xC9, 0xA3)),  # Lava
    ((0xFF, 0xC4, 0x00), (0xCC, 0x8F, 0x00), (0xFF, 0xF7, 0xCC)),  # Gold
    ((0x8F, 0xD8, 0xFF), (0x4F, 0xA3, 0xD8), (0xE8, 0xF9, 0xFF)),  # Frost
    ((0x6B, 0x4F, 0x8A), (0x43, 0x31, 0x5C), (0xCB, 0xB8, 0xE8)),  # Schatten
    ((0xFF, 0x6F, 0xD8), (0xC9, 0x3B, 0xAA), (0xB8, 0xF3, 0xFF)),  # Prisma
]

FONT_PATH = "app/src/main/res/font/bytesized_regular.ttf"
GROUND_TOP = int(H * 0.88)


def wrap_pi(v):
    while v <= -math.pi:
        v += 2 * math.pi
    while v > math.pi:
        v -= 2 * math.pi
    return v


class Scene:
    def __init__(self, sky):
        self.img = Image.new("RGB", (W, H), sky)
        self.d = ImageDraw.Draw(self.img)

    # ===== Text =====
    def text_center(self, y, s, size, color, shadow=True):
        font = ImageFont.truetype(FONT_PATH, size)
        x = (W - self.d.textlength(s, font=font)) / 2
        if shadow:
            off = max(3, size // 18)
            self.d.text((x + off, y + off), s, font=font, fill=OUTLINE)
        self.d.text((x, y), s, font=font, fill=color)

    # ===== Himmel-Deko =====
    def stars(self, n=30):
        for k in range(n):
            x = ((k * k * 263 + k * 71) % 431) / 431 * W
            y = ((k * k * 149 + k * 37) % 353) / 353 * (H * 0.5)
            s = 5 if k % 3 else 8
            self.d.rectangle([x, y, x + s, y + s],
                             fill=WHITE if k % 4 else RECORD_YELLOW)

    def cloud(self, x, y, u):
        d = self.d
        d.rectangle([x, y + u * 2, x + u * 14, y + u * 5], fill=CLOUD)
        d.rectangle([x + u * 2, y, x + u * 9, y + u * 2], fill=CLOUD)
        d.rectangle([x + u * 4, y - u * 1.5, x + u * 8, y], fill=CLOUD)

    # ===== Boden & Szenerie (v2.11-Look) =====
    def ground(self):
        d = self.d
        d.rectangle([0, GROUND_TOP, W, H], fill=SAND)
        d.rectangle([0, GROUND_TOP + CELL * 8, W, GROUND_TOP + CELL * 10],
                    fill=SAND_SHADE)
        d.rectangle([0, GROUND_TOP, W, GROUND_TOP + CELL * 5], fill=GRASS_DARK)
        x = 0
        while x < W:
            d.rectangle([x, GROUND_TOP, x + CELL * 5, GROUND_TOP + CELL * 4],
                        fill=GRASS_LIGHT)
            x += CELL * 10
        d.rectangle([0, GROUND_TOP - CELL, W, GROUND_TOP], fill=OUTLINE)

    def _outlined(self, x0, y0, x1, y1, color):
        self.d.rectangle([x0 - CELL, y0 - CELL, x1 + CELL, y1 + CELL],
                         fill=OUTLINE)
        self.d.rectangle([x0, y0, x1, y1], fill=color)

    def tree(self, cx, ground_y, s):
        trunk_w, trunk_h = s * 0.30, s * 0.60
        self._outlined(cx - trunk_w / 2, ground_y - trunk_h,
                       cx + trunk_w / 2, ground_y, TRUNK)
        self.d.rectangle([cx, ground_y - trunk_h, cx + trunk_w / 2, ground_y],
                         fill=TRUNK_SHADE)
        layers = [(s * 1.6, s * 0.45, BUSH_SHADE),
                  (s * 1.2, s * 0.40, BUSH),
                  (s * 0.7, s * 0.35, GRASS_LIGHT)]
        top = ground_y - trunk_h
        for lw, lh, color in layers:
            top -= lh
            self._outlined(cx - lw / 2, top, cx + lw / 2, top + lh, color)

    def bush(self, cx, ground_y, s):
        layers = [(s * 2.1, s * 0.55, BUSH_SHADE),
                  (s * 2.7, s * 0.70, BUSH),
                  (s * 1.5, s * 0.55, GRASS_LIGHT)]
        top = ground_y
        for lw, lh, color in layers:
            top -= lh
            self._outlined(cx - lw / 2, top, cx + lw / 2, top + lh, color)
        u = CELL * 1.5
        self.d.rectangle([cx - s, ground_y - s * 1.05,
                          cx - s + u * 2, ground_y - s * 1.05 + u],
                         fill=GRASS_LIGHT)

    def flower(self, cx, ground_y, s, petal):
        stem_h = s * 1.15
        by = ground_y - stem_h
        self._outlined(cx - CELL * 0.75, by, cx + CELL * 0.75, ground_y,
                       BUSH_SHADE)
        leaf_y = ground_y - stem_h * 0.45
        self._outlined(cx - s * 0.6, leaf_y, cx, leaf_y + CELL * 1.5, BUSH)
        self._outlined(cx, leaf_y + CELL * 4, cx + s * 0.55,
                       leaf_y + CELL * 5.5, BUSH)
        u = s * 0.38
        blocks = [(cx - u / 2, by - u * 1.5, petal),
                  (cx - u * 1.5, by - u / 2, petal),
                  (cx + u / 2, by - u / 2, petal),
                  (cx - u / 2, by + u / 2, petal),
                  (cx - u / 2, by - u / 2, GOLD)]
        for bx, byy, color in blocks:
            self._outlined(bx, byy, bx + u, byy + u, color)

    def scenery(self):
        gy = GROUND_TOP + CELL * 2
        self.tree(90, gy, H * 0.075)
        self.flower(370, gy, H * 0.032, RECORD_RED)
        self.tree(650, gy, H * 0.058)
        self.bush(930, gy, H * 0.026)

    # ===== Bahn & Punkt =====
    def track(self, cx, cy, radius, zone_center, zone_half,
              fake_center=None, fake_half=None):
        segments = 60
        for k in range(segments):
            a = k / segments * 2 * math.pi
            px = cx + math.cos(a) * radius
            py = cy + math.sin(a) * radius
            rel = abs(wrap_pi(a - zone_center))
            in_zone = rel <= zone_half
            in_core = rel <= zone_half * 0.35
            in_fake = in_fake_core = False
            if fake_center is not None:
                frel = abs(wrap_pi(a - fake_center))
                in_fake = frel <= fake_half
                in_fake_core = frel <= fake_half * 0.35
            highlighted = in_zone or in_fake
            outer = CELL * 5 if highlighted else CELL * 3
            inner = CELL * 3.4 if highlighted else CELL * 1.8
            if in_core:
                color = GRASS_LIGHT
            elif in_zone:
                color = GRASS_DARK
            elif in_fake_core:
                color = FAKE_CORE
            elif in_fake:
                color = FAKE
            else:
                color = SAND_SHADE
            self.d.rectangle([px - outer / 2, py - outer / 2,
                              px + outer / 2, py + outer / 2], fill=OUTLINE)
            self.d.rectangle([px - inner / 2, py - inner / 2,
                              px + inner / 2, py + inner / 2], fill=color)

    def dot(self, cx, cy, r, skin=SKINS[0], eye=True):
        body, shade, shine = skin
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
                self.d.rectangle(
                    [cx - r + col * u, cy - r + row * u,
                     cx - r + (col + 1) * u, cy - r + (row + 1) * u], fill=c)
        self.d.rectangle([cx - r + 2.5 * u, cy - r + 2.5 * u,
                          cx - r + 4.5 * u, cy - r + 4.5 * u], fill=shine)
        if eye:
            self.d.rectangle([cx - r + 7.5 * u, cy - r + 3 * u,
                              cx - r + 11 * u, cy - r + 7 * u], fill=WHITE)
            self.d.rectangle([cx - r + 9.5 * u, cy - r + 4 * u,
                              cx - r + 11 * u, cy - r + 6 * u], fill=OUTLINE)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)
        print(path, self.img.size)


TRACK_CX, TRACK_CY = W / 2, H * 0.47
TRACK_R = W * 0.36


def caption(scene, line1, line2):
    scene.text_center(110, line1, 96, WHITE)
    scene.text_center(235, line2, 72, GOLD)


def gameplay(lang):
    s = Scene(SKY_STAGES[0])
    s.cloud(70, 420, 14)
    s.cloud(760, 560, 10)
    s.ground()
    s.scenery()
    zone = -0.9
    s.track(TRACK_CX, TRACK_CY, TRACK_R, zone, 0.5)
    a = zone - 1.7
    s.dot(TRACK_CX + math.cos(a) * TRACK_R, TRACK_CY + math.sin(a) * TRACK_R,
          H * 0.026 * 1.6)
    s.text_center(400, "7", 190, WHITE)
    if lang == "de":
        caption(s, "EIN TAP ENTSCHEIDET.", "PERFEKT ODER VORBEI.")
        s.text_center(1340, "PERFEKT! +2", 64, RECORD_YELLOW)
    else:
        caption(s, "ONE TAP DECIDES.", "PERFECT OR IT'S OVER.")
        s.text_center(1340, "PERFECT! +2", 64, RECORD_YELLOW)
    return s


def twists(lang):
    s = Scene(SKY_STAGES[2])
    s.cloud(120, 430, 11)
    s.ground()
    s.scenery()
    zone = -0.9
    s.track(TRACK_CX, TRACK_CY, TRACK_R, zone, 0.45,
            fake_center=2.2, fake_half=0.45)
    a = zone - 1.7
    s.dot(TRACK_CX + math.cos(a) * TRACK_R, TRACK_CY + math.sin(a) * TRACK_R,
          H * 0.026 * 1.6)
    s.text_center(400, "22", 190, WHITE)
    if lang == "de":
        caption(s, "JEDE STUFE", "EIN NEUER TWIST")
        s.text_center(600, "NEU: FALLEN-ZONE!", 60, ACCENT)
    else:
        caption(s, "NEW TWISTS", "AT EVERY STAGE")
        s.text_center(600, "NEW: TRAP ZONE!", 60, ACCENT)
    return s


def daily(lang):
    s = Scene(SKY_STAGES[5])
    s.stars()
    s.ground()
    s.scenery()
    zone = 2.6
    s.track(TRACK_CX, TRACK_CY, TRACK_R, zone, 0.5)
    a = zone - 1.3
    s.dot(TRACK_CX + math.cos(a) * TRACK_R, TRACK_CY + math.sin(a) * TRACK_R,
          H * 0.026 * 1.6, skin=SKINS[6])
    s.text_center(400, "14", 190, WHITE)
    s.text_center(600, "DAILY", 64, GOLD)
    if lang == "de":
        caption(s, "JEDEN TAG EINE", "NEUE CHALLENGE")
        s.text_center(1360, "SERIE: 5 TAGE", 64, GOLD)
    else:
        caption(s, "A NEW CHALLENGE", "EVERY DAY")
        s.text_center(1360, "STREAK: 5 DAYS", 64, GOLD)
    return s


def skins(lang):
    s = Scene(SKY_STAGES[6])
    s.stars()
    s.ground()
    s.scenery()
    r = 105
    top = [(200, 720), (430, 640), (660, 640), (890, 720)]
    bottom = [(320, 1120), (550, 1040), (780, 1120)]
    for (x, y), skin in zip(top + bottom, SKINS):
        s.dot(x, y, r, skin=skin)
    if lang == "de":
        caption(s, "SKINS", "FREISPIELEN")
    else:
        caption(s, "UNLOCK", "ALL SKINS")
    return s


for lang in ("de", "en"):
    gameplay(lang).save(f"store/screenshots/{lang}/01-gameplay.png")
    twists(lang).save(f"store/screenshots/{lang}/02-twists.png")
    daily(lang).save(f"store/screenshots/{lang}/03-daily.png")
    skins(lang).save(f"store/screenshots/{lang}/04-skins.png")
