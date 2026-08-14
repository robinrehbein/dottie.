#!/usr/bin/env python3
"""Generiert stilisierte Play-Store-Screenshots (1080x1920, 9:16) in
Deutsch und Englisch — wie Feature-Grafik und App komplett aus Code.

Sechs Motive: Kern-Gameplay, Twists, Daily Challenge, Skin-Menue,
Skin-Galerie und die Ausdauer-/Saison-Achsen. Die Szenen spiegeln das
echte Spiel (Palette, Bahn mit 60 Segmenten, Baeume/Blumen/Buesche der
v2.11-Szenerie).

Zwei Dinge sind hier keine Nachbildung, sondern das Original:

* Die Skins zeichnet `store/skin_paint.py`, eine geprueft
  deckungsgleiche Portierung von `SkinPaint.kt` — jedes der 13x13
  Felder bekommt dieselbe Farbe wie im Spiel. Gemalte Attrappen waeren
  im Store eine Falschangabe.
* Die Beschriftungen im Skin-Menue (Namen, Freischalt-Hinweise,
  Familien-Ueberschriften) liest der Generator aus den echten
  String-Ressourcen der App, nicht aus einer zweiten Liste hier.

Die Werbe-Captions sind bewusst M-frei — der Bytesized-Font rendert das
M wie ein N und macht es auf einem Store-Asset unleserlich. In den
Zeilen, die das Spiel selbst zeigt (Skin-Hinweise), steht dagegen der
echte Text: Genau so sieht ihn auch, wer die App oeffnet.

Ausfuehren aus dem Repo-Root:

    python3 store/generate_screenshots.py
"""

import math
import os
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import skin_paint as sp  # noqa: E402
from pixel_dot import OUTLINE, WHITE, paste_dot  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

W, H = 1080, 1920
CELL = 8  # entspricht floor(1920 / 220) im Spiel

# Umrechnung dp/sp -> px: Ein uebliches Telefon ist 411 dp breit; die
# Menue-Motive uebernehmen daraus ihre Groessen, damit das Layout dem
# echten Bildschirm entspricht statt frei erfunden zu sein.
DP = W / 411.0

# ===== Spiel-Palette =====
SKY_STAGES = [sp.rgb(c) for c in sp.SKY_STAGES]
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

FONT_PATH = os.path.join(REPO, "app/src/main/res/font/bytesized_regular.ttf")
GROUND_TOP = int(H * 0.88)

# ===== Zustaende, in denen die Skins gezeigt werden =====
#
# Bewegte und reagierende Skins SIND ihr Verlauf: THERMO ist bei Score 0
# hellblau wie FROST, MEDAILLE ist ohne Medaille zinnfarben, KOMBO ohne
# Serie grau. Eine Galerie, die alle 42 im Ruhezustand zeigt, zeigt
# deshalb weniger Vielfalt, als das Spiel hat — dreimal Hellblau,
# zweimal Gelb, dreimal Grau. Also bekommt jeder Skin hier den Moment,
# der ihn ausmacht. Erfunden ist daran nichts: Jeder Zustand ist im Lauf
# erreichbar, und gerechnet wird jedes Feld weiter von SkinPaint.
SHOWCASE = {
    "REGENBOGEN": sp.SkinState(elapsed=5.5556),   # Farbton 310: Magenta
    "MAGMA": sp.SkinState(elapsed=0.4),           # Adern halb durchgeglueht
    "CHROM": sp.SkinState(elapsed=1.8333),        # Glanzstreifen in der Mitte
    # Im Nachleuchten des Blitzes (Phase 0.14 bis 0.30): Der Koerper ist
    # nur leicht gelb ueberzogen, der Zickzack steht aber voll da. Im
    # ersten Moment des Blitzes waere beides gelb und der Blitz weg.
    "GEWITTER": sp.SkinState(elapsed=0.2),
    "DIAMANT": sp.SkinState(elapsed=1.0),         # Funkeln ueber der Facette
    "CHAMAELEON": sp.SkinState(score=12),         # Himmelsstufe Lila
    "KOMBO": sp.SkinState(perfect_streak=3),      # halb aufgeladen
    "THERMO": sp.SkinState(score=30),             # aufgeheizt, noch nicht weiss
    "MEDAILLE": sp.SkinState(score=10),           # Bronze — kein zweites Gold
    "TAGESZEIT": sp.SkinState(hour=22),           # Nachtblau mit Sternen
    "JAHRESZEIT": sp.SkinState(month=10),         # Herbstrost
}


def state_for(skin):
    return SHOWCASE.get(skin, sp.DEFAULT_STATE)


# ===== String-Ressourcen der App =====

def _load_strings(path):
    out = {}
    for node in ET.parse(path).getroot():
        if node.tag == "string" and node.text:
            # Android maskiert Apostrophe; sonst ist der Text roh.
            out[node.get("name")] = node.text.replace("\\'", "'")
    return out


STRINGS = {
    "en": _load_strings(os.path.join(REPO, "app/src/main/res/values/strings.xml")),
    "de": _load_strings(
        os.path.join(REPO, "app/src/main/res/values-de/strings.xml")),
}


def skin_name(skin, lang):
    return STRINGS[lang]["skin_" + skin.lower()]


def skin_hint(skin, lang):
    if sp.is_patron(skin):
        return STRINGS[lang]["skin_hint_goenner"]
    return STRINGS[lang].get("skin_hint_" + skin.lower(), "")


def family_title(family, lang):
    return sp.FAMILY_TITLES[family][lang]


# ===== Text =====

_FONTS = {}
_MEASURE = ImageDraw.Draw(Image.new("RGB", (1, 1)))
SHRUNK = []


def font(size):
    if size not in _FONTS:
        _FONTS[size] = ImageFont.truetype(FONT_PATH, size)
    return _FONTS[size]


def text_width(s, size):
    return _MEASURE.textlength(s, font=font(size))


def fit(s, size, max_width):
    """Verkleinert die Schrift, bis die Zeile in [max_width] passt.

    Sicherheitsnetz gegen Text, der aus dem Bild laeuft: Uebersetzungen
    sind unterschiedlich lang, und ein abgeschnittenes Wort faellt sonst
    erst im Store auf. Jede Verkleinerung wird am Ende gemeldet.
    """
    out = size
    while out > 10 and text_width(s, out) > max_width:
        out -= 2
    if out != size:
        SHRUNK.append((s, size, out))
    return out


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
    def text_center(self, y, s, size, color, shadow=True, margin=40):
        size = fit(s, size, W - 2 * margin)
        x = (W - text_width(s, size)) / 2
        self.text_at(x, y, s, size, color, shadow)

    def text_at(self, x, y, s, size, color, shadow=True, target=None):
        d = ImageDraw.Draw(target) if target is not None else self.d
        f = font(size)
        if shadow:
            off = max(3, size // 18)
            d.text((x + off, y + off), s, font=f,
                   fill=OUTLINE if target is None else OUTLINE + (255,))
        d.text((x, y), s, font=f, fill=color)

    # ===== Himmel-Deko =====
    def stars(self, n=30, avoid=()):
        """Sternenhimmel; [avoid] sind Rechtecke, die frei bleiben.

        Beschriftete Flaechen bleiben sternfrei: Ein Stern direkt hinter
        dem Schlusspunkt einer Zeile liest sich wie ein zweiter Punkt
        ("ZAEHLT..") und macht die Zeile kaputt.
        """
        blocked = (CAPTION_BAND,) + tuple(avoid)
        for k in range(n):
            x = ((k * k * 263 + k * 71) % 431) / 431 * W
            y = ((k * k * 149 + k * 37) % 353) / 353 * (H * 0.5)
            if any(x0 <= x <= x1 and y0 <= y <= y1
                   for x0, y0, x1, y1 in blocked):
                continue
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

    def dot(self, cx, cy, r, skin="KLASSIK", state=None, facing_left=False,
            alpha=1.0, eye=True, target=None):
        paste_dot(target if target is not None else self.img, cx, cy, r, skin,
                  state or state_for(skin), facing_left, alpha, eye)

    def dot_on_track(self, angle, r, skin="KLASSIK", state=None):
        """Der Punkt auf der Bahn — Blickrichtung wie in drawTimingDot."""
        self.dot(TRACK_CX + math.cos(angle) * TRACK_R,
                 TRACK_CY + math.sin(angle) * TRACK_R, r, skin, state,
                 facing_left=math.sin(angle) > 0)

    def save(self, path):
        full = os.path.join(REPO, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        # RGB ohne Alpha — Play nimmt keine transparenten Screenshots.
        self.img.convert("RGB").save(full)
        print(path, self.img.size)


TRACK_CX, TRACK_CY = W / 2, H * 0.47
TRACK_R = W * 0.36
DOT_R = H * 0.026 * 1.6

# Das Band der beiden Werbezeilen — hier bleibt der Himmel sternfrei.
CAPTION_BAND = (0, 60, W, 350)

# Die Punktzahl steht in der Bahn, nicht darueber: Im Spiel sitzt sie
# oben am Rand (ScoreHud, 40dp), den belegen hier die Werbezeilen. Auf
# halber Hoehe waere sie halb hinter der Bahn verschwunden.
SCORE_Y = 760


def caption(scene, line1, line2, y1=110, y2=235):
    scene.text_center(y1, line1, 96, WHITE)
    scene.text_center(y2, line2, 72, GOLD)


# ===== 01: Kern-Gameplay =====

def gameplay(lang):
    s = Scene(SKY_STAGES[0])
    s.cloud(70, 420, 14)
    s.cloud(760, 560, 10)
    s.ground()
    s.scenery()
    zone = -0.9
    s.track(TRACK_CX, TRACK_CY, TRACK_R, zone, 0.5)
    s.dot_on_track(zone - 1.7, DOT_R)
    s.text_center(SCORE_Y, "7", 190, WHITE)
    if lang == "de":
        caption(s, "EIN TAP ENTSCHEIDET.", "PERFEKT ODER VORBEI.")
        s.text_center(1340, "PERFEKT! +2", 64, RECORD_YELLOW)
    else:
        caption(s, "ONE TAP DECIDES.", "PERFECT OR IT'S OVER.")
        s.text_center(1340, "PERFECT! +2", 64, RECORD_YELLOW)
    return s


# ===== 02: Twists =====

def twists(lang):
    s = Scene(SKY_STAGES[2])
    s.cloud(120, 430, 11)
    s.ground()
    s.scenery()
    zone = -0.9
    s.track(TRACK_CX, TRACK_CY, TRACK_R, zone, 0.45,
            fake_center=2.2, fake_half=0.45)
    s.dot_on_track(zone - 1.7, DOT_R)
    s.text_center(SCORE_Y, "22", 190, WHITE)
    if lang == "de":
        caption(s, "JEDE STUFE", "EIN NEUER TWIST")
        s.text_center(SCORE_Y + 195, "NEU: FALLEN-ZONE!", 60, ACCENT)
    else:
        caption(s, "NEW TWISTS", "AT EVERY STAGE")
        s.text_center(SCORE_Y + 195, "NEW: TRAP ZONE!", 60, ACCENT)
    return s


# ===== 03: Daily Challenge =====

def daily(lang):
    s = Scene(SKY_STAGES[5])
    s.stars()
    s.ground()
    s.scenery()
    zone = 2.6
    s.track(TRACK_CX, TRACK_CY, TRACK_R, zone, 0.5)
    s.dot_on_track(zone - 1.3, DOT_R, "PRISMA")
    s.text_center(SCORE_Y, "14", 190, WHITE)
    s.text_center(SCORE_Y + 195, "DAILY", 64, GOLD)
    if lang == "de":
        caption(s, "JEDEN TAG EINE", "NEUE CHALLENGE")
        s.text_center(1400, "SERIE: 5 TAGE", 64, GOLD)
    else:
        caption(s, "A NEW CHALLENGE", "EVERY DAY")
        s.text_center(1400, "STREAK: 5 DAYS", 64, GOLD)
    return s


# ===== 04: Das Skin-Menue, wie es seit v2.20 aussieht =====
#
# Bei 42 Skins ist die reine Liste nicht mehr lesbar; das Menue gliedert
# sie deshalb nach Familien (SkinOverlay in GameOverlays.kt). Das Motiv
# zeigt den Anfang der Liste in echten Groessen — die letzte Zeile laeuft
# unten aus dem Bild, weil die Liste genau das tut: sie geht weiter.

# Ein glaubhafter Zwischenstand: Rekord 30, vier perfekte in Serie, drei
# Tage Daily-Serie. FROST (Rekord 40) und FLIEGENPILZ (35) fehlen noch.
MENU_UNLOCKED = {"KLASSIK", "MINZE", "LAVA", "GOLD", "SCHATTEN", "PRISMA",
                 "BIENE", "MELONE"}
MENU_SELECTED = "MELONE"


def skin_menu(lang):
    s = Scene(SKY_STAGES[6])
    s.stars()
    s.ground()
    s.scenery()
    # Das Menue liegt als fast deckende Kontur-Flaeche ueber dem Spiel
    # (OutlineColor bei 92 %).
    s.img = Image.alpha_composite(
        s.img.convert("RGBA"), Image.new("RGBA", (W, H), OUTLINE + (235,)))
    ui = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(ui)

    title = STRINGS[lang]["skins"]
    size = int(32 * DP)
    s.text_at((W - text_width(title, size)) / 2, int(32 * DP), title, size,
              WHITE + (255,), target=ui)

    y = int(80 * DP)  # unter Titel und dessen 16dp-Abstand
    left = int(48 * DP)
    row_h = int(36 * DP) + int(20 * DP)
    last_family = None
    for skin in sp.ALL_SKINS:
        # Angefangene Zeilen bleiben weg: Die Liste laeuft im Spiel unten
        # aus dem Bild, ein halber Vogel sieht auf einem Store-Asset aber
        # nach Fehler aus.
        if y + row_h > H:
            break
        family = next(f for f, group in sp.FAMILIES if skin in group)
        if family != last_family:
            last_family = family
            y += int(10 * DP)
            s.text_at(left, y, family_title(family, lang), int(18 * DP),
                      ACCENT + (255,), target=ui)
            y += int(18 * DP) + int(12 * DP)

        unlocked = skin in MENU_UNLOCKED
        cy = y + row_h / 2
        s.dot(left + 18 * DP, cy, 18 * DP, skin,
              alpha=1.0 if unlocked else 0.3, target=ui)
        text_x = left + int(52 * DP)
        fade = 255 if unlocked else 115
        s.text_at(text_x, y + int(8 * DP), skin_name(skin, lang), int(20 * DP),
                  WHITE + (fade,), shadow=False, target=ui)
        if skin == MENU_SELECTED:
            sub, color = STRINGS[lang]["skin_selected"], GOLD + (255,)
        elif unlocked:
            sub, color = STRINGS[lang]["skin_tap_select"], WHITE + (180,)
        else:
            sub, color = skin_hint(skin, lang), WHITE + (115,)
        s.text_at(text_x, y + int(30 * DP), sub, int(14 * DP), color,
                  shadow=False, target=ui)
        y += row_h

    s.img = Image.alpha_composite(s.img, ui)
    return s


# ===== 05: Die Galerie — alle 42 Skins auf einen Blick =====
#
# Das staerkste Argument dieses Updates und deshalb ein eigenes Motiv.
# Gegliedert wie das Menue, damit die Zahl nicht nur gross, sondern auch
# geordnet ist: sechs Familien, jede mit ihrer eigenen Regel.

GALLERY_ROWS = {"GEMUSTERT": 6, "BEWEGT": 5}  # sonst eine Zeile je Familie


def gallery(lang):
    s = Scene(SKY_STAGES[6])
    s.stars(38)
    s.ground()
    s.scenery()
    if lang == "de":
        caption(s, "42 SKINS", "EIN PUNKT, VIELE KLEIDER", y1=96, y2=200)
    else:
        caption(s, "42 SKINS", "ONE DOT, EVERY LOOK", y1=96, y2=200)

    # Acht Reihen und sechs Ueberschriften muessen zwischen Caption und
    # Baumkronen passen — deshalb sitzen die Vogel enger als sonst.
    r = 44
    spacing = 130
    y = 300
    for family, group in sp.FAMILIES:
        s.text_center(y, family_title(family, lang), 34, ACCENT)
        y += 46
        per_row = GALLERY_ROWS.get(family, len(group))
        for start in range(0, len(group), per_row):
            row = group[start:start + per_row]
            x0 = (W - (len(row) - 1) * spacing) / 2
            for k, skin in enumerate(row):
                s.dot(x0 + k * spacing, y + r, r, skin)
            y += 2 * r + 8
    return s


# ===== 06: Sammeln ohne Rekord =====
#
# Seit v2.20 haengen Skins nicht mehr nur am Koennen: Laeufe, Punkte,
# gespielte Tage und Monate wachsen mit jedem Versuch, auch mit den
# schlechten. Dazu die vier Saison-Skins, die nur an Anwesenheit haengen.

ENDURANCE = ["EI", "TIGER", "MEDAILLE", "FUSSBALL",
             "DONUT", "KONFETTI", "TAGESZEIT", "JAHRESZEIT"]

COLLECT_TEXT = {
    "de": {"head": ("JEDER LAUF ZAEHLT.", "AUCH OHNE REKORD."),
           "block": "LAEUFE UND PUNKTE",
           "foot": "SAISON-SKINS: JEDES JAHR WIEDER"},
    "en": {"head": ("EVERY RUN COUNTS.", "RECORD OR NOT."),
           "block": "RUNS AND POINTS",
           "foot": "SEASONAL SKINS RETURN EVERY YEAR"},
}


def collect(lang):
    t = COLLECT_TEXT[lang]
    s = Scene(SKY_STAGES[5])
    # Ueber den beschrifteten Zeilen bleibt der Himmel frei — zwischen
    # "200 LAEUFE" und einem Stern kann das Auge sonst nicht trennen.
    s.stars(40, avoid=[(30, 380, 1060, 1460)])
    s.ground()
    s.scenery()
    caption(s, t["head"][0], t["head"][1])

    def block(y, heading, skins):
        s.text_center(y, heading, 46, ACCENT)
        y += 70
        for k, skin in enumerate(skins):
            col, row = k % 2, k // 2
            x0 = 70 + col * 500
            top = y + row * 118
            s.dot(x0 + 44, top + 52, 44, skin)
            s.text_at(x0 + 104, top + 8, skin_name(skin, lang),
                      fit(skin_name(skin, lang), 42, 376), WHITE)
            hint = skin_hint(skin, lang)
            s.text_at(x0 + 104, top + 62, hint, fit(hint, 28, 376), GOLD)
        return y + ((len(skins) + 1) // 2) * 118

    y = block(420, t["block"], ENDURANCE)
    block(y + 60, family_title("SAISON", lang), list(sp.FAMILIES[4][1]))
    s.text_center(1380, t["foot"], 40, WHITE)
    return s


MOTIFS = [
    ("01-gameplay", gameplay),
    ("02-twists", twists),
    ("03-daily", daily),
    ("04-skins", skin_menu),
    ("05-gallery", gallery),
    ("06-collect", collect),
]


def main():
    for lang in ("de", "en"):
        for name, build in MOTIFS:
            build(lang).save("store/screenshots/%s/%s.png" % (lang, name))
    if SHRUNK:
        print("\nZeilen, die verkleinert werden mussten:")
        for text, was, now in SHRUNK:
            print("  %-34s %d -> %d" % (text, was, now))


if __name__ == "__main__":
    main()
