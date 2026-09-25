package de.robinrehbein.punkt.game

import kotlin.math.floor

/**
 * Die Falle (Twist FAKE) als Kette von Minesweeper-Minen — Sprite, Farben
 * und Lauflicht an einer Stelle für Telefon, Uhr und iOS.
 *
 * Nach demselben Muster wie [SkinPaint] und [MedalPaint]: Die Renderer
 * fragen hier nach Pixeln, Farben und Takt und rechnen nichts selbst.
 * Farben sind ARGB-Longs (0xAARRGGBB), damit das Modul frei von Compose-
 * und Android-Typen bleibt.
 *
 * Nichts hier zieht Zufallszahlen: Weder das Lauflicht noch seine Richtung
 * dürfen den Engine-`random` (sonst verschieben sich Daily und Vektoren)
 * oder `Random.Default` (sonst flackert das Bild) anfassen. Die Uhr ist
 * [TimingGame.zoneAge], die Richtung kommt aus der Lage der Falle.
 */
object TrapPaint {

    /**
     * Die Mine, 7×7 Pixel, so groß wie ein Bahn-Block: schwarze Kugel mit
     * vier Zacken und Glanzpunkt. Zeile für Zeile von oben:
     * `O` = Kugel ([BALL] bzw. [RED] im Lauflicht), `W` = Glanz ([GLOSS]),
     * `.` = leer. Um jeden gesetzten Pixel liegt ein dünner Rand ([RIM]),
     * damit die Mine auch vor dunklen Himmeln steht.
     */
    val MINE: List<String> = listOf(
        "...O...",
        ".OOOOO.",
        ".OWWOO.",
        "OOWOOOO",
        ".OOOOO.",
        ".OOOOO.",
        "...O..."
    )

    /** Kantenlänge des Sprites in Pixeln. */
    const val MINE_SIZE = 7

    /** Kugel und Zacken. */
    const val BALL: Long = 0xFF1E1A22

    /** Heller Rand um die Mine, für dunkle Himmel. */
    const val RIM: Long = 0xFFF4E9EC

    /** Glanzpunkt. */
    const val GLOSS: Long = 0xFFFFFFFF

    /** Kugelfarbe einer Mine, über die gerade das Lauflicht läuft. */
    const val RED: Long = 0xFFE53935

    /** Takt des Lauflichts: alle so viele Sekunden ein Schritt. */
    const val LIGHT_STEP_SECONDS = 0.09f

    /**
     * Wie viele Minen die Falle trägt: so viele ganze Blöcke der Größe
     * [cell], wie in die volle Fallenbreite `2 · zoneHalfWidth` passen,
     * mindestens eine. [cell] in derselben Einheit wie [zoneHalfWidth]
     * (im Spiel: Radiant eines Bahn-Blocks).
     *
     * Absichtlich mit der Grundbreite [TimingGame.zoneHalfWidth] statt mit
     * der pulsierenden [TimingGame.fakeZoneHalf]: Unter PULS atmet die
     * Falle, die Zahl der Minen und das Lauflicht sollen dabei stehen
     * bleiben. Gezeichnet wird dann in der Breite von `fakeZoneHalf()`.
     */
    fun count(zoneHalfWidth: Float, cell: Float): Int {
        if (!(cell > 0f) || !(zoneHalfWidth > 0f)) return 1
        return floor(2f * zoneHalfWidth / cell).toInt().coerceAtLeast(1)
    }

    /**
     * Welche der [count] Minen im Lauflicht-Schritt [step] rot sind, in
     * Laufrichtung des Lichts (Index 0 = die erste Mine, die rot wird).
     *
     * Erst wird eine Mine rot, dann zwei, bis ein Block von
     * `wB = ceil(n/2)` Minen rot ist. Der Block wandert weiter und läuft am
     * anderen Ende hinaus, ein Schritt ist ganz dunkel, dann von vorn. Die
     * Periode ist `n + wB`. Bei sechs Minen:
     * `R·····  RR····  RRR···  ·RRR··  ··RRR·  ···RRR  ····RR  ·····R  ······`
     *
     * [step] darf beliebig groß (oder negativ) sein und wird auf die
     * Periode gefaltet; im Spiel ist er `floor(zoneAge / LIGHT_STEP_SECONDS)`.
     */
    fun redMask(count: Int, step: Int): List<Boolean> {
        if (count <= 0) return emptyList()
        val width = (count + 1) / 2
        val period = count + width
        val s = ((step % period) + period) % period
        return List(count) { i -> i <= s && i > s - width }
    }

    /**
     * Laufrichtung des Lichts über die Falle: +1 oder -1.
     *
     * Aus einem Bit der Fließkomma-Darstellung von [fakeZoneCenter] statt
     * aus einer Zufallszahl — pro Falle fest, für jede Falle anders genug,
     * und ohne den Engine-Zufall zu berühren.
     */
    fun direction(fakeZoneCenter: Float): Int {
        val bits = fakeZoneCenter.toRawBits()
        return if (((bits ushr 7) and 1) == 0) 1 else -1
    }
}
