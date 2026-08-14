#!/usr/bin/env python3
"""Erzeugt die abgeleiteten PWA-Icons aus web/icon-512.png.

    python3 store/generate_web_icons.py

Zwei Ausgaben:

* ``web/icon-192.png`` — die klassische Android-Launcher-Groesse. Chrome
  akzeptiert zwar auch ein einzelnes 512er, 192 bleibt aber die Groesse,
  die Android ohne Skalierung direkt verwenden kann.
* ``web/icon-maskable-512.png`` — Icon fuer ``"purpose": "maskable"``.
  Android schneidet installierte PWA-Icons in die Systemform (Kreis,
  Squircle, ...). Ohne maskable-Variante legt es das quadratische Bild
  verkleinert in einen weissen Kreis; mit ihr fuellt das Icon die Form.
  Die Spec verlangt, dass alles Wichtige im inneren Kreis mit 80 %
  Durchmesser liegt — dafuer wird das Motiv verkleinert und der Vogel in
  die Mitte gerueckt, der Sand-Streifen laeuft bis zur Kante weiter.

Pixel-Art: durchgehend NEAREST, damit keine weichen Kanten entstehen.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "web" / "icon-512.png"

# Himmelblau der App (theme_color im Manifest, BG_SKY in render.js).
SKY = (78, 192, 202, 255)

# Mitte des Vogels in der 512er-Vorlage — nach dem Verkleinern wird genau
# dieser Punkt auf die Bildmitte gelegt.
BIRD_CENTER = (250, 232)

# Anteil, auf den das Motiv fuer die maskable-Variante schrumpft: 0.8
# entspricht exakt der Safe-Zone der Spec.
MASKABLE_SCALE = 0.8


def write_192(source: Image.Image) -> None:
    out = ROOT / "web" / "icon-192.png"
    source.resize((192, 192), Image.NEAREST).save(out)
    print(f"geschrieben: {out.relative_to(ROOT)}")


def write_maskable(source: Image.Image) -> None:
    size = source.width
    scaled_size = int(size * MASKABLE_SCALE)
    scaled = source.resize((scaled_size, scaled_size), Image.NEAREST)

    offset_x = size // 2 - int(BIRD_CENTER[0] * MASKABLE_SCALE)
    offset_y = size // 2 - int(BIRD_CENTER[1] * MASKABLE_SCALE)

    # Der Boden ist in der Vorlage ein freistehender Block mit Himmel-Rand
    # ringsum — er muss also nicht bis zur Kante verlaengert werden. Der
    # Rand, den das Verkleinern hinzufuegt, ist schlicht mehr Himmel.
    canvas = Image.new("RGBA", (size, size), SKY)
    canvas.paste(scaled, (offset_x, offset_y))

    out = ROOT / "web" / "icon-maskable-512.png"
    canvas.save(out)
    print(f"geschrieben: {out.relative_to(ROOT)}")


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    write_192(source)
    write_maskable(source)


if __name__ == "__main__":
    main()
