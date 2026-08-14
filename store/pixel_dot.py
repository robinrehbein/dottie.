#!/usr/bin/env python3
"""Der Pixel-Vogel fuer alle Store-Grafiken — eine Portierung von
`drawTimingDot` (TimingGameScreen.kt) und `drawPixelCircle` (ScoreCard.kt).

Gezeichnet wird stumpf Feld fuer Feld: Die Farbe jedes der 13x13 Felder
kommt aus [skin_paint.cell], also aus derselben Rechnung wie im Spiel.
Der Renderer kennt keinen einzigen Skin beim Namen — er kennt nur die
Kreismaske, die Kontur, den Glanzpunkt und das Auge.

Die Kachel entsteht als RGBA-Bild und wird ins Zielbild geklebt: So
lassen sich gesperrte Skins wie im Skin-Menue mit 30 % Deckkraft zeigen,
ohne dass der Zeichencode davon etwas wissen muss.
"""

from PIL import Image, ImageDraw

from skin_paint import DEFAULT_STATE, GRID, cell, needs_eye_outline, rgb, shine

# Kontur des Spiels (OutlineColor).
OUTLINE = (0x54, 0x38, 0x47)
WHITE = (0xFF, 0xFF, 0xFF)

# Kreismaske und Konturbreite wie drawPixelCircle.
_MID = (GRID - 1) / 2.0
_RR = GRID / 2.0 - 0.25


def dot_tile(radius, skin, state=DEFAULT_STATE, facing_left=False, eye=True):
    """Ein Vogel als RGBA-Kachel der Kantenlaenge 2*radius."""
    size = max(1, int(round(radius * 2)))
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    u = size / GRID

    for row in range(GRID):
        for col in range(GRID):
            dx = col - _MID
            dy = row - _MID
            dist = (dx * dx + dy * dy) ** 0.5
            if dist > _RR:
                continue
            color = OUTLINE if dist > _RR - 1.1 else rgb(cell(skin, col, row,
                                                              state))
            d.rectangle([col * u, row * u, (col + 1) * u, (row + 1) * u],
                        fill=color)

    def rect(col, row, cols, rows, color):
        d.rectangle([col * u, row * u, (col + cols) * u, (row + rows) * u],
                    fill=color)

    # Glanzpunkt und Auge folgen der Flugrichtung — wie im Spiel.
    if facing_left:
        rect(GRID - 4.5, 2.5, 2, 2, rgb(shine(skin, state)))
        if eye:
            if needs_eye_outline(skin):
                rect(5.5, 3, 0.5, 4, OUTLINE)
                rect(2, 2.5, 3.5, 0.5, OUTLINE)
                rect(2, 7, 3.5, 0.5, OUTLINE)
            rect(2, 3, 3.5, 4, WHITE)
            rect(2, 4, 1.5, 2, OUTLINE)
    else:
        rect(2.5, 2.5, 2, 2, rgb(shine(skin, state)))
        if eye:
            if needs_eye_outline(skin):
                rect(7, 3, 0.5, 4, OUTLINE)
                rect(7.5, 2.5, 3.5, 0.5, OUTLINE)
                rect(7.5, 7, 3.5, 0.5, OUTLINE)
            rect(7.5, 3, 3.5, 4, WHITE)
            rect(9.5, 4, 1.5, 2, OUTLINE)

    return img


def paste_dot(img, cx, cy, radius, skin, state=DEFAULT_STATE,
              facing_left=False, alpha=1.0, eye=True):
    """Zeichnet einen Vogel mittig auf (cx, cy) in ein RGB-Bild.

    [alpha] unter 1 blendet ihn aus — genau das macht das Skin-Menue mit
    noch nicht verdienten Skins.
    """
    tile = dot_tile(radius, skin, state, facing_left, eye)
    if alpha < 1.0:
        band = tile.getchannel("A").point(lambda v: int(v * alpha))
        tile.putalpha(band)
    img.paste(tile, (int(round(cx - radius)), int(round(cy - radius))), tile)
