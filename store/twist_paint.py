#!/usr/bin/env python3
"""Bomben, Nebel und Start-Hand fuer die Store-Generatoren.

Die Sprites stehen nicht ein zweites Mal hier, sondern werden aus dem
Kotlin-Quelltext gelesen, den auch das Spiel benutzt:

* Mine und ihre Farben aus `TrapPaint.kt` (`:core`), wie am Telefon und
  auf der Uhr.
* Die Hand aus `StartCoach.kt` (`:ui`), wie im Startbildschirm.
* Die Nebelfarben aus `FogRenderer.kt` (`:ui`).

Aendert sich dort ein Pixel, zeichnen die Store-Bilder ihn beim naechsten
Lauf mit. Findet der Leser eine Stelle nicht mehr, bricht er ab, statt
still eine Attrappe zu malen.
"""

import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TRAP_KT = os.path.join(
    REPO, "core/src/commonMain/kotlin/de/robinrehbein/punkt/game/TrapPaint.kt")
COACH_KT = os.path.join(
    REPO, "ui/src/commonMain/kotlin/de/robinrehbein/punkt/ui/world/StartCoach.kt")
FOG_KT = os.path.join(
    REPO, "ui/src/commonMain/kotlin/de/robinrehbein/punkt/ui/world/FogRenderer.kt")


def _read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def _string_list(src, name):
    m = re.search(r"val %s\s*(?::[^=]*)?=\s*listOf\((.*?)\)" % name, src, re.S)
    if not m:
        raise SystemExit("%s nicht gefunden" % name)
    return re.findall(r'"([^"]*)"', m.group(1))


def _argb(src, name):
    m = re.search(r"%s(?::\s*Long)?\s*=\s*(?:Color\()?0x([0-9A-Fa-f]{8})" % name, src)
    if not m:
        raise SystemExit("Farbe %s nicht gefunden" % name)
    v = int(m.group(1), 16)
    return ((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF)


_TRAP = _read(TRAP_KT)
MINE = _string_list(_TRAP, "MINE")
MINE_BALL = _argb(_TRAP, "BALL")
MINE_RIM = _argb(_TRAP, "RIM")
MINE_GLOSS = _argb(_TRAP, "GLOSS")
MINE_RED = _argb(_TRAP, "RED")

_COACH = _read(COACH_KT)
HAND = _string_list(_COACH, "HAND")
HAND_SKIN = _argb(_COACH, "HandSkin")
HAND_SHADE = _argb(_COACH, "HandShade")
HAND_SLEEVE = _argb(_COACH, "HandSleeve")
HAND_TIP = _argb(_COACH, "HandPressedTip")

_FOG = _read(FOG_KT)
FOG_BOTTOM = _argb(_FOG, "FogBottom")
FOG_LOW = _argb(_FOG, "FogLow")
FOG_MID = _argb(_FOG, "FogMid")
FOG_TOP = _argb(_FOG, "FogTop")
FOG_INNER = _argb(_FOG, "FogInner")


def red_mask(count, step):
    """Wie TrapPaint.redMask: welche Minen im Lauflicht-Schritt rot sind."""
    if count <= 0:
        return []
    width = (count + 1) // 2
    period = count + width
    s = step % period
    return [s - width < i <= s for i in range(count)]


def draw_mine(d, cx, cy, px, red=False, rim=None):
    """Eine Mine aus TrapPaint.MINE, mittig auf (cx, cy), [px] Bildpunkte
    je Sprite-Pixel. Erst der helle Rand um jeden gesetzten Pixel, dann
    Kugel und Glanz. [rim] ist die Randbreite (Standard: ein Drittel)."""
    n = len(MINE)
    ox = round(cx - px * n / 2)
    oy = round(cy - px * n / 2)
    r = rim if rim is not None else max(1, round(px / 3))
    for row in range(n):
        for col, ch in enumerate(MINE[row]):
            if ch == ".":
                continue
            x, y = ox + col * px, oy + row * px
            d.rectangle([x - r, y - r, x + px + r - 1, y + px + r - 1], fill=MINE_RIM)
    for row in range(n):
        for col, ch in enumerate(MINE[row]):
            if ch == ".":
                continue
            x, y = ox + col * px, oy + row * px
            if ch == "W":
                color = MINE_GLOSS
            else:
                color = MINE_RED if red else MINE_BALL
            d.rectangle([x, y, x + px - 1, y + px - 1], fill=color)


def draw_hand(d, tip_x, tip_y, u, outline, under, pressed=False):
    """Die Start-Hand (StartCoach.HAND), Fingerspitze bei (tip_x, tip_y).
    Gedrueckt sinkt sie um zwei Pixel und die Spitze wird gelblich. Der
    Schlagschatten ist die Kontur mit 35 % Deckkraft ueber [under]."""
    if pressed:
        tip_y += 2 * u
    ox = int(tip_x - u * 5)
    oy = int(tip_y)
    shadow = tuple(int(c * 0.35 + b * 0.65) for c, b in zip(outline, under))
    for row, line in enumerate(HAND):
        for col, ch in enumerate(line):
            if ch == ".":
                continue
            x, y = ox + (col + 1) * u, oy + (row + 1) * u
            d.rectangle([x, y, x + u - 1, y + u - 1], fill=shadow)
    for row, line in enumerate(HAND):
        for col, ch in enumerate(line):
            if ch == ".":
                continue
            color = {
                "O": outline,
                "W": HAND_TIP if (pressed and row < 4) else HAND_SKIN,
                "S": HAND_SHADE,
                "Y": HAND_SLEEVE,
            }[ch]
            x, y = ox + col * u, oy + row * u
            d.rectangle([x, y, x + u - 1, y + u - 1], fill=color)


def fog_layers():
    """Die Schichten der Wolke von aussen nach innen, wie FogRenderer.kt:
    Unterkante, darueber zwei Blautoene, Oberkante weiss, Inneres."""
    return [FOG_BOTTOM, FOG_LOW, FOG_MID, FOG_TOP, FOG_INNER]
