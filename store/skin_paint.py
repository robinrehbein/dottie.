#!/usr/bin/env python3
"""Das Farbwerk aller 42 Punkt-Skins — Portierung von
`core/src/main/kotlin/de/robinrehbein/punkt/game/SkinPaint.kt`.

Die Store-Generatoren zeichnen die Skins damit exakt so, wie das Spiel
sie zeichnet: [cell] liefert fuer jedes Feld des 13x13-Rasters denselben
Farbwert wie die Kotlin-Quelle. Gemalte Attrappen waeren im Store eine
Falschangabe — deshalb rechnet hier dieselbe Mathematik.

"Dieselbe" heisst woertlich: Kotlin rechnet in `Float` (32 Bit), Python
in `double` (64 Bit). Der Unterschied ist nicht theoretisch — bei DIAMANT
kippt `floor(col * 0.9f + row * 0.4f)` fuer (6,4) genau deshalb um eine
Facette, bei ZUCKERSTANGE springt eine ganze Streifenkante. Also rechnet
[F] hier in 32 Bit nach, Schritt fuer Schritt, und [byte_of] rundet
kaufmaennisch wie Kotlins roundToInt statt abzuschneiden.

Geprueft wird das nicht per Augenmass: `store/check_skin_paint.py` haelt
alle 42 Skins Feld fuer Feld gegen einen Abzug der Kotlin-Quelle.

Reiner Rechenteil, keine Pillow-Abhaengigkeit — gezeichnet wird in
`store/pixel_dot.py`.
"""

import math
import struct

# ===== 32-Bit-Gleitkomma wie Kotlins Float =====

_F32 = struct.Struct("<f")


def f32(v):
    """Rundet einen Python-double auf den naechsten Float-Wert."""
    return _F32.unpack(_F32.pack(v))[0]


class F(float):
    """Eine Zahl, die sich nach jedem Rechenschritt auf 32 Bit zurechtstutzt.

    Damit ergibt jede Kette von Operationen genau das, was die JVM aus
    denselben Float-Ausdruecken macht: gerechnet wird intern in double
    (exakt genug, um den Einzelschritt korrekt zu runden), gespeichert
    wird jedes Zwischenergebnis in 32 Bit.
    """

    __slots__ = ()

    def __new__(cls, v=0.0):
        return float.__new__(cls, f32(v))

    def __add__(self, o):
        return F(float(self) + o)

    __radd__ = __add__

    def __sub__(self, o):
        return F(float(self) - o)

    def __rsub__(self, o):
        return F(o - float(self))

    def __mul__(self, o):
        return F(float(self) * o)

    __rmul__ = __mul__

    def __truediv__(self, o):
        return F(float(self) / o)

    def __rtruediv__(self, o):
        return F(o / float(self))

    def __neg__(self):
        return F(-float(self))

    def __abs__(self):
        return F(abs(float(self)))

    def __mod__(self, o):
        return F(math.fmod(float(self), o))

    def __rmod__(self, o):
        return F(math.fmod(o, float(self)))


def fsin(v):
    """kotlin.math.sin(Float): in double gerechnet, als Float zurueck."""
    return F(math.sin(v))


def fsqrt(v):
    return F(math.sqrt(v))


def ffloor(v):
    return F(math.floor(v))


GRID = 13
MID = F((GRID - 1) / 2.0)
RR = F(GRID / 2.0) - F(0.25)

# Die Schattenkante: untere rechte Haelfte dunkler (GRID * 1.15f).
_SHADE_LIMIT = GRID * F(1.15)

SKY_STAGES = [
    0xFF4EC0CA,  # 0+  Tag
    0xFF5B9BD5,  # 5+  Blau
    0xFF7B6FD0,  # 10+ Lila
    0xFFC0616F,  # 15+ Altrosa
    0xFFD98A3D,  # 20+ Sonnenuntergang
    0xFF3D4A8C,  # 25+ Daemmerung
    0xFF2A2640,  # 30+ Nacht
]
SKY_CYCLE = 12

HEAT_SCORE = 40

# ===== Reihenfolge und Familien (wie SkinId in :core / DotSkin in :app) =====

FAMILIES = [
    ("EINFARBIG", ["KLASSIK", "MINZE", "LAVA", "GOLD", "FROST", "SCHATTEN",
                   "PRISMA"]),
    ("GEMUSTERT", ["BIENE", "MELONE", "PILZ", "KOI", "GALAXIE", "KARO",
                   "EI", "TIGER", "PINGUIN", "FUSSBALL", "DONUT"]),
    ("BEWEGT", ["REGENBOGEN", "AURORA", "MAGMA", "NEON", "CHROM",
                "WELLE", "GEWITTER", "KONFETTI", "DISCO", "HOLO"]),
    ("REAGIEREND", ["CHAMAELEON", "KOMBO", "TINTE",
                    "THERMO", "MEDAILLE", "TAGESZEIT", "JAHRESZEIT"]),
    ("SAISON", ["KUERBIS", "ZUCKERSTANGE", "HERZ", "OSTEREI"]),
    ("GOENNER", ["DIAMANT", "PHOENIX", "ONYX"]),
]

# Reihenfolge des Skin-Menues — genau die Blockfolge von DotSkin.
ALL_SKINS = [s for _, group in FAMILIES for s in group]

# Familien-Ueberschriften des Menues (values-de / values, skin_family_*).
FAMILY_TITLES = {
    "EINFARBIG": {"de": "EINFARBIG", "en": "SOLID"},
    "GEMUSTERT": {"de": "GEMUSTERT", "en": "PATTERNED"},
    "BEWEGT": {"de": "BEWEGT", "en": "ANIMATED"},
    "REAGIEREND": {"de": "REAGIEREND", "en": "REACTIVE"},
    "SAISON": {"de": "SAISON", "en": "SEASONAL"},
    "GOENNER": {"de": "GOENNER", "en": "PATRON"},
}

SEASONAL = ("KUERBIS", "ZUCKERSTANGE", "HERZ", "OSTEREI")
PATRON = ("DIAMANT", "PHOENIX", "ONYX")

# ===== Stellvertreter-Farben =====

BODY = {
    "KLASSIK": 0xFFFFD847, "MINZE": 0xFF4BE38C, "LAVA": 0xFFFF5A36,
    "GOLD": 0xFFFFC400, "FROST": 0xFF8FD8FF, "SCHATTEN": 0xFF6B4F8A,
    "PRISMA": 0xFFFF6FD8, "BIENE": 0xFFFFD847, "MELONE": 0xFFF0555C,
    "PILZ": 0xFFE8452F, "KOI": 0xFFF7F3EE, "GALAXIE": 0xFF4E3C86,
    "KARO": 0xFF4EC0CA, "REGENBOGEN": 0xFFFF6FD8, "AURORA": 0xFF3FE0A8,
    "MAGMA": 0xFF3A2431, "NEON": 0xFF241E33, "CHROM": 0xFFE6EAF2,
    "CHAMAELEON": 0xFF8FD8DE, "KOMBO": 0xFFFFD847, "TINTE": 0xFF2A46A8,
    "EI": 0xFFFFE58F, "TIGER": 0xFFFF8A2B, "PINGUIN": 0xFF2E3440,
    "FUSSBALL": 0xFFF7F3EE, "DONUT": 0xFFFF7FBF, "WELLE": 0xFF2E86D8,
    "GEWITTER": 0xFF4A5568, "KONFETTI": 0xFFF7F3EE, "DISCO": 0xFFC3CBD9,
    "HOLO": 0xFF7FD8E8, "THERMO": 0xFFFFD847, "MEDAILLE": 0xFFC0C0C0,
    "TAGESZEIT": 0xFF8FD8FF, "JAHRESZEIT": 0xFFFFC93C, "KUERBIS": 0xFFF5821F,
    "ZUCKERSTANGE": 0xFFE8452F, "HERZ": 0xFFFF6FA8, "OSTEREI": 0xFFFFB8D9,
    "DIAMANT": 0xFFA8C8EE, "PHOENIX": 0xFFFF8A2B, "ONYX": 0xFF221C29,
}

SHADE = {
    "KLASSIK": 0xFFF5A623, "MINZE": 0xFF2BA55E, "LAVA": 0xFFC22F12,
    "GOLD": 0xFFCC8F00, "FROST": 0xFF4FA3D8, "SCHATTEN": 0xFF43315C,
    "PRISMA": 0xFFC93BAA, "BIENE": 0xFF3A2C33, "MELONE": 0xFF74BF2E,
    "PILZ": 0xFFC2301F, "KOI": 0xFFE8452F, "GALAXIE": 0xFF231A3F,
    "KARO": 0xFF2E8E98, "REGENBOGEN": 0xFF7A3BC9, "AURORA": 0xFF2A7F8E,
    "MAGMA": 0xFFC22F12, "NEON": 0xFF181328, "CHROM": 0xFF5B6478,
    "CHAMAELEON": 0xFF3F9BA5, "KOMBO": 0xFFE0A400, "TINTE": 0xFF1F3A8A,
    "EI": 0xFFE8B92E, "TIGER": 0xFF2A1F1C, "PINGUIN": 0xFF1B1F28,
    "FUSSBALL": 0xFF2A2C33, "DONUT": 0xFFC08A47, "WELLE": 0xFF1F5FA8,
    "GEWITTER": 0xFF2F3644, "KONFETTI": 0xFFFF5A36, "DISCO": 0xFF8892A6,
    "HOLO": 0xFFC93BAA, "THERMO": 0xFFE0A400, "MEDAILLE": 0xFF8F8F9C,
    "TAGESZEIT": 0xFF3D4A8C, "JAHRESZEIT": 0xFFE09218, "KUERBIS": 0xFFC25E10,
    "ZUCKERSTANGE": 0xFFC2301F, "HERZ": 0xFFD6407E, "OSTEREI": 0xFFB096E8,
    "DIAMANT": 0xFF4E6A96, "PHOENIX": 0xFF8E2410, "ONYX": 0xFF141018,
}

_SHINE = {
    "KLASSIK": 0xFFFFF3B8, "MINZE": 0xFFC8FFE0, "LAVA": 0xFFFFC9A3,
    "GOLD": 0xFFFFF7CC, "FROST": 0xFFE8F9FF, "SCHATTEN": 0xFFCBB8E8,
    "PRISMA": 0xFFB8F3FF, "BIENE": 0xFFFFF3B8, "MELONE": 0xFFFFD3D6,
    "PILZ": 0xFFFFD9C9, "KOI": 0xFFFFFFFF, "GALAXIE": 0xFFFFF3B8,
    "KARO": 0xFFFFFFFF, "REGENBOGEN": 0xFFFFFFFF, "AURORA": 0xFFE8F9FF,
    "MAGMA": 0xFFFFD847, "CHROM": 0xFFFFFFFF, "CHAMAELEON": 0xFFFFFFFF,
    "KOMBO": 0xFFFFF3B8, "TINTE": 0xFFA8C0FF, "EI": 0xFFFFFFFF,
    "TIGER": 0xFFFFE0B8, "PINGUIN": 0xFFFFFFFF, "FUSSBALL": 0xFFFFFFFF,
    "DONUT": 0xFFFFFFFF, "WELLE": 0xFFFFFFFF, "GEWITTER": 0xFFFFF3B8,
    "KONFETTI": 0xFFFFFFFF, "DISCO": 0xFFFFFFFF, "HOLO": 0xFFFFFFFF,
    "THERMO": 0xFFFFFFFF, "MEDAILLE": 0xFFFFFFFF, "TAGESZEIT": 0xFFFFFFFF,
    "JAHRESZEIT": 0xFFFFFFFF, "KUERBIS": 0xFFFFE0B8, "ZUCKERSTANGE": 0xFFFFFFFF,
    "HERZ": 0xFFFFFFFF, "OSTEREI": 0xFFFFFFFF, "DIAMANT": 0xFFFFFFFF,
    "PHOENIX": 0xFFFFF3B8, "ONYX": 0xFFFFE07A,
}


class SkinState:
    """Lauf-Zustand, aus dem sich bewegte und reagierende Skins speisen.

    Fuer Standbilder (Auswahl, Score-Karte, Store-Grafik) reicht der
    Standardwert: Mittag im Juni, Zeitpunkt 0.
    """

    __slots__ = ("elapsed", "score", "perfect_streak", "hour", "month")

    def __init__(self, elapsed=0.0, score=0, perfect_streak=0, hour=12,
                 month=6):
        self.elapsed = F(elapsed)
        self.score = score
        self.perfect_streak = perfect_streak
        self.hour = hour
        self.month = month


DEFAULT_STATE = SkinState()


def shine(skin, state=DEFAULT_STATE):
    """Glanzpunkt — bei NEON wandert er mit der Leuchtfarbe mit."""
    if skin == "NEON":
        return _neon_glow(state)
    return _SHINE[skin]


def chips(skin):
    """Drei Farben fuer Vorschau-Kacheln ausserhalb des Spiels."""
    return [BODY[skin], SHADE[skin], shine(skin)]


def is_seasonal(skin):
    return skin in SEASONAL


def is_patron(skin):
    return skin in PATRON


def counts_for_collection(skin):
    return not is_seasonal(skin) and not is_patron(skin)


def has_trail(skin):
    return skin in ("TINTE", "PHOENIX")


def sky_stage(score):
    step = _imod(_imod(_idiv(score, 5), SKY_CYCLE) + SKY_CYCLE, SKY_CYCLE)
    return step if step <= SKY_CYCLE // 2 else SKY_CYCLE - step


def medal_tier(score):
    if score >= 40:
        return 4
    if score >= 30:
        return 3
    if score >= 20:
        return 2
    if score >= 10:
        return 1
    return 0


# ===== Kotlin-Semantik fuer Ganzzahlen =====

def _idiv(a, b):
    """Kotlin-Ganzzahldivision: schneidet gegen null ab, nicht nach unten."""
    q = abs(a) // abs(b)
    return q if (a >= 0) == (b > 0) else -q


def _imod(a, b):
    """Kotlin-Restwert: traegt das Vorzeichen des Dividenden."""
    return a - b * _idiv(a, b)


def byte_of(v):
    """Kanalwert auf ein Byte bringen — kaufmaennisch gerundet.

    Kotlins roundToInt rundet die halbe Stufe stets nach oben, Pythons
    round() dagegen zur geraden Zahl. Ohne diesen Nachbau liegen die
    gemischten Farben (CHROM, HOLO, GALAXIE) je Kanal eine Stufe daneben.
    """
    return max(0, min(255, math.floor(v + 0.5)))


def mix(a, b, k):
    """Lineare Mischung zweier ARGB-Farben; k = 0 ergibt a, k = 1 ergibt b."""
    f = F(min(1.0, max(0.0, k)))
    out = 0xFF000000
    for shift in (16, 8, 0):
        ca = (a >> shift) & 0xFF
        cb = (b >> shift) & 0xFF
        out |= byte_of(ca + (cb - ca) * f) << shift
    return out


def hsl(h, s, light):
    """HSL nach ARGB. h in Grad, s und light von 0 bis 1."""
    hue = (F(h) % 360.0 + 360.0) % 360.0
    c = (F(1.0) - abs(F(2.0) * light - 1.0)) * s
    x = c * (F(1.0) - abs(hue / 60.0 % 2.0 - 1.0))
    m = F(light) - c / 2.0
    if hue < 60.0:
        r1, g1, b1 = c, x, F(0.0)
    elif hue < 120.0:
        r1, g1, b1 = x, c, F(0.0)
    elif hue < 180.0:
        r1, g1, b1 = F(0.0), c, x
    elif hue < 240.0:
        r1, g1, b1 = F(0.0), x, c
    elif hue < 300.0:
        r1, g1, b1 = x, F(0.0), c
    else:
        r1, g1, b1 = c, F(0.0), x
    return (0xFF000000
            | (byte_of((r1 + m) * 255.0) << 16)
            | (byte_of((g1 + m) * 255.0) << 8)
            | byte_of((b1 + m) * 255.0))


def _noise(col, row, seed):
    """Deterministisches Rauschen — 32-Bit-Ueberlauf wie in Kotlin."""
    n = ((col * 73856093) ^ (row * 19349663) ^ (seed * 83492791)) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n = (n ^ (n >> 16)) & 0xFFFFFFFF
    signed = n - 0x100000000 if n >= 0x80000000 else n
    # Kotlin: abs(Int.MIN_VALUE) bleibt Int.MIN_VALUE.
    return signed if signed == -0x80000000 else abs(signed)


def _neon_glow(state):
    """Leuchtfarbe von NEON: springt im Vierteltakt weiter."""
    cols = (0xFFFF3DCB, 0xFF3DF5E0, 0xFFC3FF3D)
    step = int(ffloor(state.elapsed * F(2.5)))
    return cols[_imod(_imod(step, len(cols)) + len(cols), len(cols))]


def _shaded(col, row, body, shade):
    """Die Standard-Schattierung des Spiels: untere rechte Haelfte dunkler."""
    return shade if col + row > _SHADE_LIMIT else body


# ===== Muster-Details =====

def _is_seed(col, row):
    return (col, row) in ((4, 3), (7, 5), (3, 6), (8, 2), (6, 7))


def _is_dot(col, row):
    return (col, row) in ((3, 2), (8, 1), (5, 4), (9, 5), (2, 6), (6, 6))


def _is_red_patch(col, row):
    return (col, row) in ((2, 4), (3, 4), (3, 5), (2, 5), (4, 5), (3, 3))


def _is_orange_patch(col, row):
    return (col, row) in ((8, 7), (9, 7), (8, 8), (7, 8), (9, 6), (7, 7))


def _is_star(col, row):
    return (col, row) in ((3, 3), (9, 4), (5, 8), (10, 8), (2, 7))


def _is_nebula(col, row):
    return (col, row) in ((7, 2), (4, 6), (8, 9))


def _is_belly(col, row):
    """Heller Bauch des PINGUIN — als Ellipse, damit er zur Kugel passt."""
    dx = (col - 6) * F(0.9)
    dy = row - F(8.2)
    return fsqrt(dx * dx + dy * dy) < F(3.4)


def _is_ball_patch(col, row):
    """Fuenfeck in der Mitte plus angeschnittene Flecken am Rand."""
    return (col, row) in (
        (6, 5), (5, 6), (6, 6), (7, 6), (5, 7), (6, 7), (7, 7), (6, 8),
        (1, 4), (2, 4), (2, 3), (10, 9), (9, 10), (3, 11))


def _is_sprinkle(col, row):
    return (col, row) in (
        (3, 2), (5, 1), (8, 2), (4, 4), (9, 4), (6, 3), (10, 5), (2, 4))


def _is_bolt(col, row):
    """Zickzack des Blitzes — laeuft von oben rechts nach unten links."""
    return (col, row) in (
        (7, 2), (6, 3), (6, 4), (7, 4), (5, 5), (5, 6), (6, 6), (4, 7),
        (4, 8), (5, 8), (3, 9))


def _is_grin(col, row):
    """Geschnitztes Grinsen des KUERBIS, bewusst unterhalb des Auges."""
    return (row == 10 and 3 <= col <= 9) or (row == 9 and col in (3, 6, 9))


def _is_heart(col, row):
    """Pixelherz, tief gesetzt — oben hat das Auge Vorrang."""
    if row == 6:
        return col in (4, 5, 7, 8)
    if row in (7, 8):
        return 3 <= col <= 9
    if row == 9:
        return 4 <= col <= 8
    if row == 10:
        return 5 <= col <= 7
    if row == 11:
        return col == 6
    return False


SPRINKLE_COLORS = (0xFF4EC0CA, 0xFFFFF3B8, 0xFFFFFFFF, 0xFFFF5A36)
CONFETTI_COLORS = (0xFFFF5A36, 0xFF4EC0CA, 0xFFFFD847, 0xFFFF6FD8, 0xFF7B6FD0)
DISCO_COLORS = (0xFFFF6FD8, 0xFF4EC0CA, 0xFFFFD847)
DIAMOND_COLORS = (0xFFDCEBFF, 0xFFA8C8EE, 0xFF7FA8D8)

# Baender des OSTEREI: Koerper- und Schattenfarbe je Band.
EASTER_COLORS = (
    (0xFFFFB8D9, 0xFFE086B4),
    (0xFFBFE9FF, 0xFF8FC8E8),
    (0xFFFFF0A8, 0xFFE0CE6A),
    (0xFFD9C2FF, 0xFFB096E8),
)

# Legierungen von MEDAILLE: Zinn, Bronze, Silber, Gold, Platin.
MEDAL_COLORS = (
    (0xFFB8BEC9, 0xFF8A909C),
    (0xFFCD7F32, 0xFF9C5A1E),
    (0xFFC0C0C0, 0xFF8F8F9C),
    (0xFFFFD700, 0xFFC9A400),
    (0xFFE5E4E2, 0xFFADB5C4),
)


def _day_palette(hour):
    """Kleid von TAGESZEIT: Morgenrot, Mittagsblau, Abendglut, Sternennacht."""
    if 5 <= hour <= 8:
        return (0xFFFFC58F, 0xFFE8935A)
    if 9 <= hour <= 16:
        return (0xFF8FD8FF, 0xFF4FA3D8)
    if 17 <= hour <= 20:
        return (0xFFFF8A3C, 0xFFC0616F)
    return (0xFF3D4A8C, 0xFF232B55, 0xFFFFF3B8)


def _season_palette(month):
    """Kleid von JAHRESZEIT: Koerper, Schatten, Streufarbe, Streu-Rest."""
    if month in (3, 4, 5):
        return (0xFFFFB8D9, 0xFFE086B4, 0xFFFFFFFF, 5)
    if month in (6, 7, 8):
        return (0xFFFFC93C, 0xFFE09218, 0xFFFFF6C0, 7)
    if month in (9, 10, 11):
        return (0xFFC2551E, 0xFF8E3A14, 0xFFFFB84E, 4)
    return (0xFFDCF3FF, 0xFFA8C8DE, 0xFFFFFFFF, 6)


# ===== Das Farbwerk =====

def cell(skin, col, row, state=DEFAULT_STATE):
    """Farbe eines Rasterfelds — Zeile fuer Zeile wie SkinPaint.cell.

    [col] und [row] laufen von 0 bis GRID-1, Feld (0,0) liegt oben links.
    Kreismaske und Kontur bleiben Sache des Renderers.
    """
    t = state.elapsed

    if skin in ("KLASSIK", "MINZE", "LAVA", "GOLD", "FROST", "SCHATTEN",
                "PRISMA", "TINTE"):
        return _shaded(col, row, BODY[skin], SHADE[skin])

    if skin == "BIENE":
        if _imod(_imod(col - row, 6) + 6, 6) < 2:
            return 0xFF3A2C33
        return _shaded(col, row, 0xFFFFD847, 0xFFE0A400)

    if skin == "MELONE":
        if row >= 10:
            return 0xFF5AA020 if col + row > _SHADE_LIMIT else 0xFF74BF2E
        if row == 9:
            return 0xFFDFF2C6
        if _is_seed(col, row):
            return 0xFF3A2C33
        return _shaded(col, row, 0xFFF0555C, 0xFFC93B48)

    if skin == "PILZ":
        if row >= 9:
            return _shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)
        if _is_dot(col, row):
            return 0xFFF7F3EE
        return _shaded(col, row, 0xFFE8452F, 0xFFC2301F)

    if skin == "KOI":
        if _is_red_patch(col, row):
            return 0xFFE8452F
        if _is_orange_patch(col, row):
            return 0xFFF59A2E
        return _shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)

    if skin == "GALAXIE":
        if _is_star(col, row):
            return 0xFFFFF3B8
        if _is_nebula(col, row):
            return 0xFF7FDCE4
        return mix(0xFF4E3C86, 0xFF231A3F, (col + row) / (GRID * F(2.0)))

    if skin == "KARO":
        if _imod(_idiv(col, 2) + _idiv(row, 2), 2) == 0:
            return 0xFF2E8E98 if col + row > _SHADE_LIMIT else 0xFF4EC0CA
        return _shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)

    if skin == "REGENBOGEN":
        # Der Gruenbereich wird uebersprungen: Ein gruener Vogel saehe
        # fuer einen Moment aus wie die Zielzone.
        h = t * F(45.0) % 300.0
        if h > F(80.0):
            h = h + F(60.0)
        if col + row > _SHADE_LIMIT:
            return hsl(h, F(0.70), F(0.44))
        return hsl(h, F(0.85), F(0.62))

    if skin == "AURORA":
        wave = fsin((col + row) * F(0.42) - t * F(1.6))
        h = F(168.0) + wave * F(90.0)
        if col + row > _SHADE_LIMIT:
            return hsl(h, F(0.55), F(0.40))
        return hsl(h, F(0.72), F(0.60))

    if skin == "MAGMA":
        vein = fsin(col * F(1.3) + row * F(0.7)) > F(0.35)
        if not vein:
            return 0xFF241722 if col + row > _SHADE_LIMIT else 0xFF3A2431
        heat = F(0.5) + F(0.5) * fsin(t * F(3.4) + col * F(0.8) + row * F(0.5))
        return mix(0xFF8E2410, 0xFFFFD847, heat)

    if skin == "NEON":
        dx = col - MID
        dy = row - MID
        if fsqrt(dx * dx + dy * dy) > RR - F(2.2):
            return _neon_glow(state)
        return 0xFF181328 if col + row > _SHADE_LIMIT else 0xFF241E33

    if skin == "CHROM":
        band = F(0.5) + F(0.5) * fsin(col * F(1.1))
        base = mix(0xFF5B6478, 0xFFE6EAF2, band)
        sweep = t * F(6.0) % 18.0 - 3.0
        d = abs(col + row * F(0.4) - sweep)
        if d < F(1.6):
            base = mix(base, 0xFFFFFFFF, F(1.0) - d / 1.6)
        if col + row > _SHADE_LIMIT:
            return mix(base, 0xFF3B4152, F(0.35))
        return base

    if skin == "CHAMAELEON":
        sky = SKY_STAGES[sky_stage(state.score)]
        if col + row > _SHADE_LIMIT:
            return mix(sky, 0xFF000000, F(0.18))
        return mix(sky, 0xFFFFFFFF, F(0.34))

    if skin == "KOMBO":
        k = min(state.perfect_streak, 5) / F(5.0)
        return _shaded(col, row,
                       mix(0xFF8C8790, 0xFFFFD847, k),
                       mix(0xFF5F5B63, 0xFFE0A400, k))

    # ===== Gemustert =====

    if skin == "EI":
        # Gezackte Schalenkante: Die Kappe endet je Spalte etwas anders,
        # sonst laege ein gerader Deckel auf dem Kueken.
        jag = (F(3.5) + (F(1.0) if col % 3 == 0 else F(0.0))
               + (F(0.5) if col % 2 == 0 else F(0.0)))
        if row <= jag:
            return _shaded(col, row, 0xFFF7F3EE, 0xFFDCD2C4)
        return _shaded(col, row, 0xFFFFE58F, 0xFFE8B92E)

    if skin == "TIGER":
        wave = col + fsin(row * F(0.55)) * F(2.2)
        if (wave % 6.0 + 6.0) % 6.0 < F(1.7):
            return 0xFF2A1F1C
        return _shaded(col, row, 0xFFFF8A2B, 0xFFD2601A)

    if skin == "PINGUIN":
        if row >= 11:
            return 0xFFF5A623
        if _is_belly(col, row):
            return _shaded(col, row, 0xFFF7F3EE, 0xFFDCD2C4)
        return _shaded(col, row, 0xFF2E3440, 0xFF1B1F28)

    if skin == "FUSSBALL":
        if _is_ball_patch(col, row):
            return 0xFF2A2C33
        return _shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)

    if skin == "DONUT":
        edge = F(5.5) + fsin(col * F(1.05)) * F(1.3)
        if row > edge:
            return _shaded(col, row, 0xFFE8B36A, 0xFFC08A47)
        if _is_sprinkle(col, row):
            return SPRINKLE_COLORS[_imod(col + row, len(SPRINKLE_COLORS))]
        return _shaded(col, row, 0xFFFF7FBF, 0xFFE04E9C)

    # ===== Bewegt =====

    if skin == "WELLE":
        # Eine Wasserlinie, die im Koerper schwappt — darueber Luft, an
        # der Kante Schaum.
        line = F(5.6) + fsin(t * F(1.7) + col * F(0.52)) * F(1.5)
        if row > line + F(0.9):
            return _shaded(col, row, 0xFF2E86D8, 0xFF1F5FA8)
        if row > line:
            return 0xFFBFE9FF
        return _shaded(col, row, 0xFFDCF3FF, 0xFFBBD9E8)

    if skin == "GEWITTER":
        # Der Blitz ist kurz und selten: Er traegt den Skin, aber ein
        # Dauerflackern wuerde den Punkt unlesbar machen.
        phase = t % 2.6
        if phase < F(0.14):
            flash = F(1.0)
        elif phase < F(0.30):
            flash = F(0.35)
        else:
            flash = F(0.0)
        base = _shaded(col, row, 0xFF4A5568, 0xFF2F3644)
        if flash > F(0.0) and _is_bolt(col, row):
            return 0xFFFFF3B8
        if flash > F(0.0):
            return mix(base, 0xFFFFE95E, F(0.5) * flash)
        return base

    if skin == "KONFETTI":
        step = int(ffloor(t * F(0.9)))
        n = _noise(col, row, step)
        if _imod(n, 100) < 38:
            return CONFETTI_COLORS[_imod(n, len(CONFETTI_COLORS))]
        return _shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)

    if skin == "DISCO":
        facet = _imod(_idiv(col, 2) + _idiv(row, 2), 2)
        base = 0xFFC3CBD9 if facet == 0 else 0xFF8892A6
        k = int(ffloor(t * F(7.0)))
        if _imod(col * 2 + row * 3 + k, 11) == 0:
            return 0xFFFFFFFF
        if _imod(col + row * 2 + k, 13) == 0:
            return DISCO_COLORS[_imod(col + row + k, len(DISCO_COLORS))]
        if col + row > _SHADE_LIMIT:
            return mix(base, 0xFF3B4152, F(0.3))
        return base

    if skin == "HOLO":
        # Sammelkarten-Folie; der Gruenbereich wird uebersprungen wie beim
        # REGENBOGEN.
        h = ((col - row) * F(13.0) + t * F(60.0)) % 360.0
        if h < F(0.0):
            h = h + F(360.0)
        if F(80.0) < h < F(150.0):
            h = h + F(70.0)
        color = hsl(h, F(0.75),
                    F(0.46) if col + row > _SHADE_LIMIT else F(0.66))
        sweep = t * F(5.0) % 20.0 - 4.0
        d = abs(col + row * F(0.6) - sweep)
        if d < F(1.4):
            color = mix(color, 0xFFFFFFFF, F(1.0) - d / 1.4)
        return color

    # ===== Reagierend =====

    if skin == "THERMO":
        # Der Vogel heizt sich im Lauf auf: kalt bei 0, weissgluehend bei
        # HEAT_SCORE — Fortschritt genau dort, wo der Daumen hinsieht.
        k = min(state.score, HEAT_SCORE) / F(HEAT_SCORE)
        if k < F(0.5):
            body = mix(0xFF8FD8FF, 0xFFFFD847, k * F(2.0))
            shade = mix(0xFF4FA3D8, 0xFFE0A400, k * F(2.0))
        else:
            body = mix(0xFFFFD847, 0xFFFFF6E0, (k - F(0.5)) * F(2.0))
            shade = mix(0xFFE0A400, 0xFFFF7A3C, (k - F(0.5)) * F(2.0))
        return _shaded(col, row, body, shade)

    if skin == "MEDAILLE":
        tier = MEDAL_COLORS[medal_tier(state.score)]
        dx = col - MID
        dy = row - MID
        # Praegerand: aussen dunkler, damit die Muenze eine Kante hat.
        if fsqrt(dx * dx + dy * dy) > RR - F(1.85):
            return mix(tier[1], 0xFF000000, F(0.18))
        return _shaded(col, row, tier[0], tier[1])

    if skin == "TAGESZEIT":
        p = _day_palette(state.hour)
        if len(p) > 2 and _is_star(col, row):
            return p[2]
        return _shaded(col, row, p[0], p[1])

    if skin == "JAHRESZEIT":
        p = _season_palette(state.month)
        if _imod(col * 3 + row * 5, 11) == p[3]:
            return p[2]
        return _shaded(col, row, p[0], p[1])

    # ===== Saison =====

    if skin == "KUERBIS":
        if row <= 1 and 5 <= col <= 7:
            return 0xFF5AA020
        if _is_grin(col, row):
            return 0xFF2A1F1C
        rib = abs(_imod(_imod(col + 1, 4) + 4, 4) - 2) < 1
        body = 0xFFD86A12 if rib else 0xFFF5821F
        if col + row > _SHADE_LIMIT:
            return mix(body, 0xFF000000, F(0.22))
        return body

    if skin == "ZUCKERSTANGE":
        band = int(ffloor((col + row - t * F(4.0)) / F(2.2)))
        if _imod(_imod(band, 2) + 2, 2) == 0:
            return _shaded(col, row, 0xFFE8452F, 0xFFC2301F)
        return _shaded(col, row, 0xFFF7F3EE, 0xFFDCD2C4)

    if skin == "HERZ":
        # Das Herz sitzt tief: Weiter oben verdeckte es das Auge, und zwei
        # Zeichen im selben Gesicht kaempfen gegeneinander.
        if _is_heart(col, row):
            return _shaded(col, row, 0xFFFFF0F5, 0xFFFFC8DC)
        return _shaded(col, row, 0xFFFF6FA8, 0xFFD6407E)

    if skin == "OSTEREI":
        band = _imod(_idiv(row + (1 if col % 2 == 0 else 0), 2), 4)
        if band == 1 and col % 3 == 0:
            return 0xFFFFFFFF
        return _shaded(col, row, EASTER_COLORS[band][0], EASTER_COLORS[band][1])

    # ===== Goenner =====

    if skin == "DIAMANT":
        facet = _imod(_imod(int(ffloor(col * F(0.9) + row * F(0.4))), 3) + 3, 3)
        base = DIAMOND_COLORS[facet]
        sweep = t * F(7.0) % 20.0 - 4.0
        d = abs(col + row * F(0.5) - sweep)
        if d < F(1.2):
            base = mix(base, 0xFFFFFFFF, F(1.0) - d / 1.2)
        if _imod(_noise(col, row, int(ffloor(t * F(3.0)))), 37) == 0:
            return 0xFFFFFFFF
        if col + row > _SHADE_LIMIT:
            return mix(base, 0xFF4E6A96, F(0.35))
        return base

    if skin == "PHOENIX":
        flicker = F(0.5) + F(0.5) * fsin(t * F(4.0) + col * F(0.7)
                                         - row * F(1.1))
        heat = max(F(0.0), F(1.0) - row / F(11.0)) * F(0.6) + flicker * F(0.5)
        if heat > F(0.9):
            color = 0xFFFFF3B8
        else:
            color = mix(0xFFE5341A, 0xFFFFB020, min(F(1.0), heat))
        if col + row > _SHADE_LIMIT:
            return mix(color, 0xFF8E2410, F(0.35))
        return color

    if skin == "ONYX":
        vein = fsin(col * F(1.15) + row * F(0.85)) > F(0.55)
        if not vein:
            return 0xFF141018 if col + row > _SHADE_LIMIT else 0xFF221C29
        glow = F(0.5) + F(0.5) * fsin(t * F(1.6) + col * F(0.5) + row * F(0.4))
        return mix(0xFF8A6A1E, 0xFFFFE07A, glow)

    raise KeyError("unbekannter Skin: %s" % skin)


# ===== Auge =====

# Felder, an die das Auge grenzt — in beiden Blickrichtungen.
EYE_NEIGHBOURS = (
    (7, 3), (7, 4), (7, 5), (7, 6), (8, 2), (9, 2), (10, 2), (8, 7), (9, 7),
    (10, 7),
    (5, 3), (5, 4), (5, 5), (5, 6), (4, 2), (3, 2), (2, 2), (4, 7), (3, 7),
    (2, 7),
)

EYE_OUTLINE_DISTANCE = F(60.0)


def _distance_to_white(color):
    r = F(255.0) - ((color >> 16) & 0xFF)
    g = F(255.0) - ((color >> 8) & 0xFF)
    b = F(255.0) - (color & 0xFF)
    return fsqrt(r * r + g * g + b * b)


def needs_eye_outline(skin):
    """Braucht das Auge dieses Skins eine Kontur zum Koerper hin?

    Auf sehr hellen Koerpern (Koi, Chrom) verschwaende das weisse Auge
    sonst. Gemessen wird im Ruhezustand, nicht pro Bild — sonst ginge die
    Kontur bei bewegten Skins mitten im Lauf an und aus.
    """
    return any(_distance_to_white(cell(skin, col, row)) < EYE_OUTLINE_DISTANCE
               for col, row in EYE_NEIGHBOURS)


def rgb(color):
    """ARGB-Long zu einem RGB-Tupel fuer Pillow."""
    return ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
