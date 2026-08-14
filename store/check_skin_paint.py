#!/usr/bin/env python3
"""Prueft `store/skin_paint.py` Feld fuer Feld gegen die Kotlin-Quelle.

Die Store-Grafiken behaupten, 42 echte Skins zu zeigen. Damit das keine
Behauptung bleibt, wird die Python-Portierung gegen einen Abzug aus
`core/.../SkinPaint.kt` gehalten: alle 42 Skins, alle 169 Rasterfelder,
in vier Zustaenden (Ruhe, drei gemischte Laeufe). Ein einziger
abweichender Farbwert laesst den Lauf scheitern.

Den Abzug erzeugt der Kotlin-Compiler aus dem Gradle-Cache — Java ist
vorhanden, kotlinc nicht:

    python3 store/check_skin_paint.py

Ohne Kotlin-Compiler im Cache meldet das Skript das und bricht ab; die
Bilder lassen sich dann trotzdem erzeugen, nur eben ungeprueft.
"""

import glob
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import skin_paint as sp  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(
    REPO, "core/src/main/kotlin/de/robinrehbein/punkt/game/SkinPaint.kt")

# Vier Zustaende: Ruhe plus drei, die Uhr, Kalender, Score und Serie
# gleichzeitig aus der Standardstellung schieben.
STATES = [
    sp.SkinState(),
    sp.SkinState(elapsed=1.7, score=23, perfect_streak=3, hour=4, month=10),
    sp.SkinState(elapsed=5.5556, score=40, perfect_streak=5, hour=19, month=3),
    sp.SkinState(elapsed=0.4, score=7, perfect_streak=1, hour=12, month=12),
]

DUMPER = """
import de.robinrehbein.punkt.game.*

fun main() {
    val states = listOf(
        SkinState(),
        SkinState(elapsed = 1.7f, score = 23, perfectStreak = 3, hour = 4, month = 10),
        SkinState(elapsed = 5.5556f, score = 40, perfectStreak = 5, hour = 19, month = 3),
        SkinState(elapsed = 0.4f, score = 7, perfectStreak = 1, hour = 12, month = 12)
    )
    for (id in SkinId.entries) {
        println("SKIN $id ${SkinPaint.body(id)} ${SkinPaint.shade(id)} " +
            "${SkinPaint.shine(id)} ${SkinPaint.needsEyeOutline(id)}")
        for ((si, st) in states.withIndex()) {
            val sb = StringBuilder("CELL $id $si")
            for (row in 0 until SkinPaint.GRID) for (col in 0 until SkinPaint.GRID) {
                sb.append(' ').append(SkinPaint.cell(id, col, row, st))
            }
            println(sb)
        }
    }
}
"""


def _jar(pattern):
    hits = glob.glob(os.path.expanduser(
        "~/.gradle/caches/modules-2/files-2.1/**/" + pattern), recursive=True)
    return sorted(hits)[-1] if hits else None


def kotlin_reference():
    """Kompiliert SkinPaint.kt samt Abzug-Programm und liefert die Zeilen."""
    compiler = _jar("kotlin-compiler-embeddable-*.jar")
    stdlib = _jar("kotlin-stdlib-2*.jar")
    if not compiler or not stdlib:
        print("Kein Kotlin-Compiler im Gradle-Cache — Pruefung nicht moeglich.")
        return None
    extra = [_jar(p) for p in ("kotlinx-coroutines-core-jvm-*.jar",
                              "trove4j-*.jar", "annotations-13*.jar",
                              "kotlin-daemon-embeddable-*.jar")]
    cp = os.pathsep.join([compiler, stdlib] + [j for j in extra if j])
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "src")
        out = os.path.join(tmp, "out")
        os.makedirs(src)
        with open(SOURCE, encoding="utf-8") as f:
            body = f.read()
        with open(os.path.join(src, "SkinPaint.kt"), "w", encoding="utf-8") as f:
            f.write(body)
        with open(os.path.join(src, "Dump.kt"), "w", encoding="utf-8") as f:
            f.write(DUMPER)
        build = subprocess.run(
            ["java", "-cp", cp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
             src, "-d", out, "-classpath", stdlib, "-no-stdlib", "-nowarn"],
            capture_output=True, text=True)
        if not os.path.isdir(out):
            print("Kotlin-Uebersetzung fehlgeschlagen:\n" + build.stderr[-2000:])
            return None
        run = subprocess.run(
            ["java", "-cp", os.pathsep.join([out, stdlib]), "DumpKt"],
            capture_output=True, text=True)
        if run.returncode != 0:
            print("Abzug fehlgeschlagen:\n" + run.stderr[-2000:])
            return None
        return run.stdout.splitlines()


def main():
    lines = kotlin_reference()
    if lines is None:
        return 2
    errors = 0
    seen = set()
    for line in lines:
        parts = line.split()
        if not parts or parts[0] not in ("SKIN", "CELL"):
            continue
        skin = parts[1]
        if parts[0] == "SKIN":
            seen.add(skin)
            want = (int(parts[2]), int(parts[3]), int(parts[4]),
                    parts[5] == "true")
            got = (sp.BODY[skin], sp.SHADE[skin], sp.shine(skin),
                   sp.needs_eye_outline(skin))
            if want != got:
                errors += 1
                print("%-13s Stellvertreter: %s != %s" % (skin, got, want))
            continue
        state = STATES[int(parts[2])]
        want = [int(v) for v in parts[3:]]
        got = [sp.cell(skin, col, row, state)
               for row in range(sp.GRID) for col in range(sp.GRID)]
        for k, (w, g) in enumerate(zip(want, got)):
            if w != g:
                errors += 1
                print("%-13s Zustand %s Feld (%d,%d): %08X != %08X"
                      % (skin, parts[2], k % sp.GRID, k // sp.GRID, g, w))
                if errors > 40:
                    print("... abgebrochen")
                    return 1
    missing = set(sp.ALL_SKINS) ^ seen
    if missing:
        errors += 1
        print("Skin-Liste weicht ab: %s" % sorted(missing))
    if errors:
        print("%d Abweichung(en)." % errors)
        return 1
    print("%d Skins, %d Felder, %d Zustaende — alles deckungsgleich."
          % (len(seen), sp.GRID * sp.GRID, len(STATES)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
