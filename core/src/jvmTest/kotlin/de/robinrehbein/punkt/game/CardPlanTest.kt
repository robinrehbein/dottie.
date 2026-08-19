package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Der Beweis, dass der Umzug der Score-Karte nach :ui niemandes Karte
 * verändert hat.
 *
 * Bis v2.26 zeichnete `app/.../share/ScoreCard.kt` das Blatt allein mit
 * `android.graphics`. Auf dem iPhone gab es die Karte deshalb gar nicht,
 * und der naheliegende Weg dorthin — ein zweiter Port — hätte dieselbe
 * Karte ein zweites Mal beschrieben. Stattdessen steht ihre Geometrie
 * jetzt als [CardPlan] hier, und `:ui` malt sie auf beiden Plattformen
 * mit derselben Compose-Routine aus.
 *
 * Für so einen Umzug gilt dieselbe Regel wie bei den Rahmen (siehe
 * [CardFrameRasterTest]): Wer ihn sieht, hat ihn falsch gemacht. Geprüft
 * wird deshalb Rechteck gegen Rechteck — der Zeichencode unten ist eine
 * wortgetreue Kopie des Bestands (ScoreCard.kt in a2c7482), nur ohne die
 * Android-Leinwand. Dass er hier ein zweites Mal steht, ist der Preis
 * dafür, die Gleichheit überhaupt prüfen zu können.
 *
 * Was hier NICHT geprüft wird, ist die Schrift: `android.graphics` und
 * Compose rastern Text nicht Pixel für Pixel gleich, und eine Zusage,
 * die niemand halten kann, wäre schlimmer als keine. Geprüft ist, was
 * prüfbar ist — Geometrie, Farben und Raster.
 */
class CardPlanTest {

    private companion object {
        const val W = 1080
        const val H = 1350
        const val CELL = 6f
        const val OUTLINE = 0xFF543847L
        const val WHITE = 0xFFFFFFFFL
    }

    /** Ein Rechteck des Bestands, in derselben Form wie [CardRect]. */
    private fun rect(l: Float, t: Float, r: Float, b: Float, color: Long) =
        CardRect(l, t, r - l, b - t, color)

    private fun raster(px: Float): Float = (px / CELL).roundToInt() * CELL

    // ===== Der Bestand, wortgetreu =====

    private fun bestandHintergrund(scene: SceneId, score: Int): List<CardRect> {
        val out = mutableListOf<CardRect>()
        val kulisse = ScenePaint.of(scene)
        out += rect(0f, 0f, W.toFloat(), H.toFloat(), kulisse.sky[SkinPaint.skyStage(score)])
        kulisse.cloud?.let { cloud ->
            out += bestandWolke(W * 0.08f, H * 0.10f, cloud)
            out += bestandWolke(W * 0.62f, H * 0.17f, cloud)
        }
        val groundTop = H * 0.86f
        kulisse.ground?.let { ground ->
            out += rect(0f, groundTop, W.toFloat(), H.toFloat(), ground.sand)
            out += rect(0f, groundTop, W.toFloat(), groundTop + CELL * 5, ground.turfDark)
            var x = 0f
            while (x < W) {
                out += rect(x, groundTop, x + CELL * 5, groundTop + CELL * 4, ground.turfLight)
                x += CELL * 10
            }
            out += rect(0f, groundTop - CELL, W.toFloat(), groundTop, OUTLINE)
        }
        return out
    }

    private fun bestandWolke(x: Float, y: Float, color: Long): List<CardRect> {
        val u = CELL * 4f
        return listOf(
            rect(x, y + u * 2, x + u * 14, y + u * 5, color),
            rect(x + u * 2, y, x + u * 9, y + u * 2, color),
            rect(x + u * 4, y - u * 1.5f, x + u * 8, y, color)
        )
    }

    private fun bestandSchild(subline: Float, halbeBreite: Float): Pair<List<CardRect>, Float> {
        val cx = W / 2f
        val halb = raster(halbeBreite + CELL * 5)
        val oben = raster(subline - CELL * 6)
        val unten = oben + CELL * 10
        return listOf(
            rect(cx - halb + CELL, oben, cx + halb - CELL, unten, OUTLINE),
            rect(cx - halb, oben + CELL, cx - halb + CELL, unten - CELL, OUTLINE),
            rect(cx + halb - CELL, oben + CELL, cx + halb, unten - CELL, OUTLINE)
        ) to (oben + CELL * 6)
    }

    private fun bestandPunkt(skin: SkinId, state: SkinState, cy: Float, r: Float): List<CardRect> {
        val cx = W / 2f
        val u = r * 2f / 13f
        val out = mutableListOf<CardRect>()
        fun cellRect(col: Float, row: Float, cols: Float, rows: Float, color: Long) {
            out += rect(
                cx - r + col * u, cy - r + row * u,
                cx - r + (col + cols) * u, cy - r + (row + rows) * u, color
            )
        }
        cellRect(2.5f, 2.5f, 2f, 2f, SkinPaint.shine(skin, state))
        if (SkinPaint.needsEyeOutline(skin)) {
            cellRect(7f, 3f, 0.5f, 4f, OUTLINE)
            cellRect(7.5f, 2.5f, 3.5f, 0.5f, OUTLINE)
            cellRect(7.5f, 7f, 3.5f, 0.5f, OUTLINE)
        }
        cellRect(7.5f, 3f, 3.5f, 4f, WHITE)
        cellRect(9.5f, 4f, 1.5f, 2f, OUTLINE)
        return out
    }

    /** Die Medaillenfarben, wie die Karte sie selbst führte. */
    private fun bestandMedaille(score: Int): Pair<Long, Long>? = when {
        score >= 40 -> 0xFFE5E4E2L to 0xFFADB5C4L
        score >= 30 -> 0xFFFFD700L to 0xFFC9A400L
        score >= 20 -> 0xFFC0C0C0L to 0xFF8F8F9CL
        score >= 10 -> 0xFFCD7F32L to 0xFF9C5A1EL
        else -> null
    }

    private fun bestandBand(cy: Float, radius: Float): List<CardRect> {
        val cx = W / 2f
        val u = radius * 2f / 10f
        val out = mutableListOf<CardRect>()
        fun block(c: Float, r: Float, w: Float, h: Float, color: Long) {
            out += rect(
                cx - 8f * u + c * u, cy - radius - 4.5f * u + r * u,
                cx - 8f * u + (c + w) * u, cy - radius - 4.5f * u + (r + h) * u, color
            )
        }
        val leftBand = listOf(3.5f to 0f, 4.5f to 1.5f, 5.5f to 3f)
        val rightBand = listOf(9.5f to 0f, 8.5f to 1.5f, 7.5f to 3f)
        for ((c, r) in leftBand + rightBand) block(c - 0.5f, r - 0.5f, 3f, 2.5f, OUTLINE)
        for ((c, r) in leftBand) block(c, r, 2f, 1.5f, 0xFFE53935L)
        for ((c, r) in rightBand) block(c, r, 2f, 1.5f, 0xFFB02A28L)
        return out
    }

    private fun bestandPraegung(cy: Float, radius: Float, shade: Long): List<CardRect> {
        val cx = W / 2f
        val cu = radius * 2f / 13f
        val out = mutableListOf<CardRect>()
        fun emboss(c: Float, r: Float, w: Float, h: Float) {
            out += rect(
                cx - radius + c * cu, cy - radius + r * cu,
                cx - radius + (c + w) * cu, cy - radius + (r + h) * cu, shade
            )
        }
        emboss(5f, 5f, 3f, 3f)
        emboss(5.5f, 3.5f, 2f, 2f)
        emboss(5.5f, 7.5f, 2f, 2f)
        emboss(3.5f, 5.5f, 2f, 2f)
        emboss(7.5f, 5.5f, 2f, 2f)
        out += rect(
            cx - radius + 2.5f * cu, cy - radius + 2.5f * cu,
            cx - radius + 4.5f * cu, cy - radius + 4.5f * cu, 0xFFFFF3B8L
        )
        return out
    }

    // ===== Die Proben =====

    private fun gleich(was: String, soll: List<CardRect>, ist: List<CardRect>) {
        assertEquals("$was: andere Anzahl Rechtecke", soll.size, ist.size)
        soll.forEachIndexed { i, s ->
            val r = ist[i]
            assertEquals("$was #$i x", s.x, r.x, 0f)
            assertEquals("$was #$i y", s.y, r.y, 0f)
            assertEquals("$was #$i Breite", s.w, r.w, 0f)
            assertEquals("$was #$i Hoehe", s.h, r.h, 0f)
            assertEquals("$was #$i Farbe", s.color, r.color)
        }
    }

    @Test
    fun `Himmel, Wolken und Boden stehen wie im Bestand`() {
        SceneId.entries.forEach { scene ->
            // Mehrere Scores, weil die Himmelsstufe am Score haengt.
            listOf(0, 7, 13, 22, 41).forEach { score ->
                gleich(
                    "$scene bei $score",
                    bestandHintergrund(scene, score),
                    CardPlan.background(scene, score)
                )
            }
        }
    }

    @Test
    fun `der Boden traegt achtzehn helle Zaehne`() {
        // Die Gegenprobe zur Schleife oben: Waere sie leer, ginge der
        // Vergleich beidseitig durch, ohne etwas zu sichern.
        val boden = CardPlan.background(SceneId.WIESE, 0)
            .filter { it.color == ScenePaint.of(SceneId.WIESE).ground!!.turfLight }
        assertEquals(18, boden.size)
        assertEquals(0f, boden.first().x, 0f)
        assertEquals(1020f, boden.last().x, 0f)
        assertEquals(30f, boden.last().w, 0f)
    }

    @Test
    fun `die Bodenkante sitzt bei sechsundachtzig Prozent`() {
        // Nicht bei den 88 Prozent der Spielwelt: Unten auf der Karte
        // steht noch die Aufforderung.
        assertEquals(0.86f, CardPlan.GROUND_TOP, 0f)
        assertEquals(1161f, H * CardPlan.GROUND_TOP, 0f)
    }

    @Test
    fun `das Beiname-Schild steht wie im Bestand`() {
        listOf(0.20f, 0.205f, 0.22f, 0.26f).forEach { anteil ->
            listOf(0f, 61f, 118.5f, 240f).forEach { halbeBreite ->
                val (soll, sollBaseline) = bestandSchild(H * anteil, halbeBreite)
                val ist = CardPlan.plaque(H * anteil, halbeBreite)
                gleich("Schild bei $anteil / $halbeBreite", soll, ist.rects)
                assertEquals(sollBaseline, ist.baseline, 0f)
            }
        }
    }

    @Test
    fun `das Schild sitzt feldgenau, egal wie breit der Text misst`() {
        // Der Sinn des Rasterns: Compose misst den Beinamen nicht auf
        // denselben Bruchteil genau wie android.graphics — das Schild
        // darum herum darf davon aber keine krumme Kante bekommen.
        listOf(0f, 61f, 61.4f, 118.5f, 119.9f, 240f).forEach { halbeBreite ->
            CardPlan.plaque(H * 0.20f, halbeBreite).rects.forEach { r ->
                listOf(r.x, r.y, r.w, r.h).forEach {
                    assertEquals("krumme Kante bei $halbeBreite: $r", 0f, it % CardPlan.CELL, 0f)
                }
            }
        }
    }

    @Test
    fun `Glanz, Auge und Kontur des Punkts stehen wie im Bestand`() {
        val zustand = SkinState(hour = 14, month = 7)
        SkinPaint.ORDER.forEach { skin ->
            CardFrame.entries.forEach { frame ->
                val layout = CardStyle.layout(frame)
                gleich(
                    "$skin im $frame",
                    bestandPunkt(skin, zustand, H * layout.dot, layout.dotRadius),
                    CardPlan.dotDetails(skin, zustand, H * layout.dot, layout.dotRadius)
                )
            }
        }
    }

    @Test
    fun `unter zehn Punkten gibt es keine Medaille`() {
        listOf(0, 1, 9).forEach { assertNull(CardPlan.medal(it)) }
    }

    @Test
    fun `Band und Praegung der Medaille stehen wie im Bestand`() {
        listOf(10, 19, 20, 29, 30, 39, 40, 99).forEach { score ->
            val medaille = CardPlan.medal(score)!!
            val (body, shade) = bestandMedaille(score)!!
            // Die Karte fuehrte Schwellen und Muenzfarben bis v2.26 ein
            // zweites Mal — jetzt kommen sie aus MedalPaint, und das hier
            // ist die Probe, dass dabei keine Farbe gewandert ist.
            assertEquals("Muenzfarbe bei $score", body, MedalPaint.body(medaille.tier))
            assertEquals("Schattenfarbe bei $score", shade, MedalPaint.shade(medaille.tier))

            val cy = H * 0.79f
            assertEquals(cy, medaille.centerY, 0f)
            assertEquals(62f, medaille.radius, 0f)
            gleich("Band bei $score", bestandBand(cy, 62f), medaille.ribbon)
            gleich("Praegung bei $score", bestandPraegung(cy, 62f, shade), medaille.face)
        }
    }

    @Test
    fun `der Rahmen ist die Feldtabelle mal sechs`() {
        CardFrame.entries.forEach { frame ->
            val felder = CardStyle.frameRects(frame, CardPlan.COLS, CardPlan.ROWS)
            val pixel = CardPlan.frame(frame)
            assertEquals("$frame: andere Anzahl", felder.size, pixel.size)
            felder.forEachIndexed { i, f ->
                val p = pixel[i]
                assertEquals("$frame #$i x", f.col * CELL, p.x, 0f)
                assertEquals("$frame #$i y", f.row * CELL, p.y, 0f)
                assertEquals("$frame #$i Breite", f.cols * CELL, p.w, 0f)
                assertEquals("$frame #$i Hoehe", f.rows * CELL, p.h, 0f)
                assertEquals("$frame #$i Farbe", f.tone.argb, p.color)
            }
            assertTrue(
                "$frame liegt nicht im Blatt",
                pixel.all { it.x >= 0f && it.y >= 0f && it.x + it.w <= W && it.y + it.h <= H }
            )
        }
    }

    @Test
    fun `die Masse des Blattes sind der Bestand`() {
        assertEquals(1080, CardPlan.WIDTH)
        assertEquals(1350, CardPlan.HEIGHT)
        assertEquals(6f, CardPlan.CELL, 0f)
        assertEquals(180, CardPlan.COLS)
        assertEquals(225, CardPlan.ROWS)
        assertEquals(CardPlan.WIDTH.toFloat(), CardPlan.COLS * CardPlan.CELL, 0f)
        assertEquals(CardPlan.HEIGHT.toFloat(), CardPlan.ROWS * CardPlan.CELL, 0f)
        // Die Zeilen und Schriftgrade, die nicht am Rahmen haengen.
        assertEquals(0.55f, CardPlan.SCORE, 0f)
        assertEquals(320f, CardPlan.SCORE_SIZE, 0f)
        assertEquals(0.615f, CardPlan.POINTS, 0f)
        assertEquals(60f, CardPlan.POINTS_SIZE, 0f)
        assertEquals(0.645f, CardPlan.SCENE, 0f)
        assertEquals(34f, CardPlan.SCENE_SIZE, 0f)
        assertEquals(0.68f, CardPlan.RECORD, 0f)
        assertEquals(68f, CardPlan.RECORD_SIZE, 0f)
        assertEquals(72f, CardPlan.CHALLENGE_SIZE, 0f)
        assertEquals(52f, CardPlan.EPITHET_SIZE, 0f)
        assertEquals(0.04f, CardPlan.DAILY_GAP, 0f)
        assertEquals(0.05f, CardPlan.SHADOW, 0f)
    }
}
