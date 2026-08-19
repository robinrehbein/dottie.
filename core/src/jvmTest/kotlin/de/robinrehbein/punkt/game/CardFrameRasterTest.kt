package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * Der Beweis, dass der Umzug der Rahmen nach :core niemandes Karte
 * verändert hat.
 *
 * Bis v2.25 stand das Muster als Zeichencode in `ScoreCard.kt`
 * (`android.graphics`, Android-allein). Seit das Game-Over-Panel in :ui
 * denselben Rahmen trägt, steht es als Tabelle in [CardStyle] — und für
 * so einen Umzug gilt dieselbe Regel wie bei den Kulissen: Wer ihn sieht,
 * hat ihn falsch gemacht.
 *
 * Geprüft wird deshalb nicht "dieselben Aufrufe", sondern "dasselbe
 * Bild": Beide Fassungen malen in ein Feld von 180 mal 225 Feldern — den
 * Kartenraster (1080 mal 1350 Pixel bei 6 Pixeln je Feld) — und jedes
 * einzelne Feld muss danach dieselbe Farbe tragen. Ein vertauschtes Band,
 * ein Zahn mehr, eine Ecke zwei Felder weiter innen: alles fällt hier auf
 * und nicht erst in einem geteilten Screenshot.
 *
 * Der Zeichencode unten ist eine wortgetreue Kopie des Bestands
 * (ScoreCard.kt in 70bbbaf). Dass er hier ein zweites Mal steht, ist der
 * Preis dafür, die Gleichheit überhaupt prüfen zu können — dieselbe
 * Abmachung wie bei den Schwellen in [CardStyleTest].
 */
class CardFrameRasterTest {

    private companion object {
        const val COLS = 180
        const val ROWS = 225

        const val OUTLINE = 0xFF543847L
        const val ACCENT = 0xFFFF8A3CL
        const val RECORD_YELLOW = 0xFFFFE95EL
        const val INLAY = 0xFF4EC0CAL
        const val PEARL = 0xFFF7F3EEL

        /** Die vier Stufen, die es vor dem Umzug schon gab. */
        val BESTAND = listOf(
            CardFrame.SCHLICHT, CardFrame.DOPPELLINIE, CardFrame.ZINNEN, CardFrame.PRACHT
        )
    }

    /** Ein Blatt aus Farbwerten; 0 heißt "hier liegt kein Rahmen". */
    private class Blatt(val cols: Int, val rows: Int) {
        val felder = LongArray(cols * rows)

        fun block(col: Int, row: Int, breite: Int, hoehe: Int, farbe: Long) {
            for (r in row until row + hoehe) {
                if (r !in 0 until rows) continue
                for (c in col until col + breite) {
                    if (c !in 0 until cols) continue
                    felder[r * cols + c] = farbe
                }
            }
        }
    }

    // ===== Der Bestand, wortgetreu =====

    private fun altesBlatt(frame: CardFrame): Blatt {
        val b = Blatt(COLS, ROWS)

        fun band(inset: Int, thickness: Int, color: Long) {
            val breite = COLS - 2 * inset
            val hoehe = ROWS - 2 * inset
            b.block(inset, inset, breite, thickness, color)
            b.block(inset, ROWS - inset - thickness, breite, thickness, color)
            b.block(inset, inset, thickness, hoehe, color)
            b.block(COLS - inset - thickness, inset, thickness, hoehe, color)
        }

        fun teeth(inset: Int, size: Int, step: Int, color: Long) {
            var k = 0
            while (inset + k * step + size <= COLS - inset) {
                val col = inset + k * step
                b.block(col, inset, size, size, color)
                b.block(COLS - col - size, inset, size, size, color)
                b.block(col, ROWS - inset - size, size, size, color)
                b.block(COLS - col - size, ROWS - inset - size, size, size, color)
                k++
            }
            k = 0
            while (inset + k * step + size <= ROWS - inset) {
                val row = inset + k * step
                b.block(inset, row, size, size, color)
                b.block(inset, ROWS - row - size, size, size, color)
                b.block(COLS - inset - size, row, size, size, color)
                b.block(COLS - inset - size, ROWS - row - size, size, size, color)
                k++
            }
        }

        fun cornerBlocks(inset: Int, size: Int, color: Long) {
            for (col in intArrayOf(inset, COLS - inset - size)) {
                for (row in intArrayOf(inset, ROWS - inset - size)) {
                    b.block(col, row, size, size, color)
                }
            }
        }

        fun diamond(col: Int, row: Int, size: Int, color: Long) {
            val mitte = size / 2
            for (r in 0 until size) {
                val halb = min(min(r, size - 1 - r) + 1, mitte)
                b.block(col + mitte - halb, row + r, 2 * halb, 1, color)
            }
        }

        fun cornerDiamonds(inset: Int, size: Int, color: Long) {
            for (col in intArrayOf(inset, COLS - inset - size)) {
                for (row in intArrayOf(inset, ROWS - inset - size)) {
                    diamond(col, row, size, color)
                }
            }
        }

        when (frame) {
            CardFrame.SCHLICHT -> Unit

            CardFrame.DOPPELLINIE -> {
                band(0, 2, OUTLINE)
                band(2, 2, ACCENT)
                band(4, 2, OUTLINE)
                cornerBlocks(0, 10, OUTLINE)
                cornerBlocks(2, 6, ACCENT)
                cornerBlocks(4, 2, RECORD_YELLOW)
            }

            CardFrame.ZINNEN -> {
                band(0, 2, OUTLINE)
                band(2, 4, ACCENT)
                teeth(3, 2, 6, RECORD_YELLOW)
                band(6, 2, OUTLINE)
                band(8, 2, RECORD_YELLOW)
                band(10, 2, OUTLINE)
                cornerBlocks(0, 16, OUTLINE)
                cornerBlocks(2, 12, RECORD_YELLOW)
                cornerBlocks(5, 6, OUTLINE)
                cornerBlocks(7, 2, ACCENT)
            }

            CardFrame.PRACHT -> {
                band(0, 2, OUTLINE)
                band(2, 3, RECORD_YELLOW)
                band(5, 2, OUTLINE)
                band(7, 4, ACCENT)
                teeth(8, 2, 6, PEARL)
                band(11, 2, INLAY)
                band(13, 2, OUTLINE)
                cornerBlocks(0, 22, OUTLINE)
                cornerDiamonds(1, 20, RECORD_YELLOW)
                cornerDiamonds(6, 10, ACCENT)
                cornerBlocks(9, 4, PEARL)
            }

            else -> throw AssertionError("$frame gab es vor dem Umzug nicht")
        }
        return b
    }

    // ===== Die Tabelle =====

    private fun neuesBlatt(frame: CardFrame, cols: Int = COLS, rows: Int = ROWS): Blatt {
        val b = Blatt(cols, rows)
        CardStyle.frameRects(frame, cols, rows).forEach {
            b.block(it.col, it.row, it.cols, it.rows, it.tone.argb)
        }
        return b
    }

    @Test
    fun `die Karte sieht Feld fuer Feld aus wie vorher`() {
        BESTAND.forEach { frame ->
            val alt = altesBlatt(frame)
            val neu = neuesBlatt(frame)
            for (row in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val i = row * COLS + col
                    assertEquals(
                        "$frame: Feld ($col,$row) hat die Farbe gewechselt",
                        alt.felder[i], neu.felder[i]
                    )
                }
            }
        }
    }

    @Test
    fun `SCHLICHT zeichnet weiterhin gar nichts`() {
        assertTrue(CardStyle.frameRects(CardFrame.SCHLICHT, COLS, ROWS).isEmpty())
        assertEquals(0, CardStyle.thickness(CardFrame.SCHLICHT))
        assertTrue(CardStyle.parts(CardFrame.SCHLICHT).isEmpty())
    }

    // ===== Das Muster selbst =====

    @Test
    fun `jede Stufe hat ein Muster, und keine zwei teilen sich eins`() {
        val muster = CardFrame.entries.map { CardStyle.parts(it) }
        assertEquals(CardFrame.entries.size, muster.toSet().size)
        CardFrame.entries.filter { it != CardFrame.SCHLICHT }.forEach {
            assertTrue("$it ohne Muster", CardStyle.parts(it).isNotEmpty())
        }
    }

    @Test
    fun `die Stufen werden nach oben hin breiter`() {
        var vorher = -1
        CardFrame.entries.forEach { frame ->
            val dick = CardStyle.thickness(frame)
            assertTrue(
                "$frame ist nicht breiter als die Stufe darunter ($dick)",
                dick > vorher
            )
            vorher = dick
        }
        // Die Karte ist 180 Felder breit. Ein Rahmen, der mehr als ein
        // Zehntel davon je Seite frisst, lässt für die Zahl keinen Platz
        // mehr — das ist die harte Grenze, an der die Muster enden.
        assertTrue(
            "der breiteste Rahmen frisst zu viel Karte",
            CardStyle.thickness(CardFrame.entries.last()) <= COLS / 10
        )
    }

    @Test
    fun `jedes Muster faengt aussen mit der Kontur an und hoert innen mit ihr auf`() {
        // Ohne die dunkle Kante aussen sässe der Rahmen ohne Halt im
        // Himmel, ohne die innere liefe er in die Zahl aus. Beides ist
        // in allen sieben Stufen gleich — und genau deshalb prüfbar.
        CardFrame.entries.filter { it != CardFrame.SCHLICHT }.forEach { frame ->
            val baender = CardStyle.parts(frame).filter { it.shape == FrameShape.BAND }
            assertEquals("$frame beginnt nicht am Blattrand", 0, baender.first().inset)
            assertEquals(
                "$frame beginnt nicht mit der Kontur",
                FrameTone.OUTLINE, baender.first().tone
            )
            assertEquals(
                "$frame endet nicht mit der Kontur",
                FrameTone.OUTLINE, baender.last().tone
            )
            assertEquals(
                "$frame lässt zwischen Muster und Inhalt eine Lücke",
                CardStyle.thickness(frame), baender.last().inset + baender.last().size
            )
        }
    }

    @Test
    fun `die Baender liegen lueckenlos aufeinander`() {
        // Ein Loch zwischen zwei Bändern zeigte den Himmel mitten im
        // Rahmen — auf 1080 Pixeln Breite ein sichtbarer Streifen.
        CardFrame.entries.filter { it != CardFrame.SCHLICHT }.forEach { frame ->
            var kante = 0
            CardStyle.parts(frame).filter { it.shape == FrameShape.BAND }.forEach {
                assertEquals("$frame hat eine Lücke bei Feld $kante", kante, it.inset)
                kante = it.inset + it.size
            }
        }
    }

    @Test
    fun `Zaehne und Perlen bleiben in ihrem Band`() {
        CardFrame.entries.forEach { frame ->
            val baender = CardStyle.parts(frame).filter { it.shape == FrameShape.BAND }
            CardStyle.parts(frame)
                .filter { it.shape == FrameShape.ZAEHNE || it.shape == FrameShape.PERLEN }
                .forEach { reihe ->
                    assertTrue("$frame: eine Reihe ohne Takt", reihe.step > reihe.size)
                    val passt = baender.any {
                        reihe.inset >= it.inset && reihe.inset + reihe.size <= it.inset + it.size
                    }
                    assertTrue(
                        "$frame: die Reihe bei Feld ${reihe.inset} ragt aus ihrem Band",
                        passt
                    )
                }
        }
    }

    @Test
    fun `das Muster passt sich der Blattgroesse an`() {
        // Das Game-Over-Panel ist kleiner als die Karte und hat ein
        // anderes Seitenverhältnis. Die Tabelle rechnet deshalb in
        // Feldern: Bei halber Feldzahl muss derselbe Rahmen
        // herauskommen, nur eben auf weniger Feldern.
        val klein = neuesBlatt(CardFrame.KRONE, cols = 90, rows = 60)
        val dick = CardStyle.thickness(CardFrame.KRONE)
        // Aussenkante rundum dunkel, Mitte frei.
        assertEquals(FrameTone.OUTLINE.argb, klein.felder[0])
        assertEquals(FrameTone.OUTLINE.argb, klein.felder[89])
        assertEquals(FrameTone.OUTLINE.argb, klein.felder[59 * 90 + 89])
        assertEquals(0L, klein.felder[30 * 90 + 45])
        // Und die Kante liegt genau da, wo die Tabelle sie ansagt.
        assertTrue(klein.felder[30 * 90 + (dick - 1)] != 0L)
        assertEquals(0L, klein.felder[30 * 90 + dick])
    }
}
