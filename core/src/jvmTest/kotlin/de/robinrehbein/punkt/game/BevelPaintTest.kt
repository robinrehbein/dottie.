package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Farbableitung des Bevel-Looks. Was hier falsch rechnet, ist auf
 * Telefon, iOS und Uhr gleichzeitig falsch — und fällt erst auf, wenn
 * jemand Kanten zählt.
 */
class BevelPaintTest {

    @Test
    fun `mix rechnet kanalweise und rundet kaufmaennisch`() {
        assertEquals(0xFF000000L, BevelPaint.mix(0xFF000000, 0xFFFFFFFF, 0f))
        assertEquals(0xFFFFFFFFL, BevelPaint.mix(0xFF000000, 0xFFFFFFFF, 1f))
        // 255 · 0.5 = 127.5 → 128: halbe Schritte runden auf.
        assertEquals(0xFF808080L, BevelPaint.mix(0xFF000000, 0xFFFFFFFF, 0.5f))
        // Jeder Kanal für sich: Rot bleibt, Grün und Blau wandern.
        assertEquals(0xFFFF4080L, BevelPaint.mix(0xFFFF0000, 0xFFFF80FF, 0.5f))
        // Alpha ist immer deckend, auch wenn eine Seite es nicht ist.
        assertEquals(0xFFL, (BevelPaint.mix(0x00102030, 0x00102030, 0.3f) shr 24) and 0xFF)
    }

    @Test
    fun `light und dark folgen der Bevel-Regel`() {
        val sand = 0xFFD3C87EL
        assertEquals(BevelPaint.mix(sand, 0xFFFFFFFF, 0.35f), BevelPaint.light(sand))
        assertEquals(BevelPaint.mix(sand, BevelPaint.OUTLINE, 0.30f), BevelPaint.dark(sand))
        assertEquals(0xFFE2DBABL, BevelPaint.light(sand))
        assertEquals(0xFFAD9D6EL, BevelPaint.dark(sand))
        assertEquals(0xFF543847L, BevelPaint.OUTLINE)
    }

    @Test
    fun `die Dunkel-Grenze liegt beim Mittelwert 80`() {
        assertTrue(BevelPaint.isDarkCell(0xFF1E1A22)) // Minenkugel
        assertTrue(BevelPaint.isDarkCell(0xFF4F4F4F)) // Mittel 79
        assertFalse(BevelPaint.isDarkCell(0xFF505050)) // Mittel 80
        assertFalse(BevelPaint.isDarkCell(0xFFFFD847)) // DotBody
    }

    @Test
    fun `die Kugel laesst dunkle Zellen unveraendert`() {
        // Bienenstreifen, Kerne und Pupillen würden aufgehellt grau.
        val dunkel = listOf(0xFF1E1A22L, 0xFF000000L, 0xFF3A2A1AL, 0xFF4F4F4FL)
        for (farbe in dunkel) for (row in 0 until 13) for (col in 0 until 13) {
            assertEquals(farbe, BevelPaint.kugel(col, row, farbe, 0xFFFFFFFF))
        }
    }

    @Test
    fun `die Kugel hat zwei Lichtstufen, eine Mitte und einen Schatten`() {
        val base = 0xFFFFD847L
        val shine = 0xFFFFF3B8L
        // Oben links: s = -12 / (6.25 · √2) ≈ -1.36 → volle Lichtstufe.
        assertEquals(BevelPaint.mix(base, shine, 0.55f), BevelPaint.kugel(0, 0, base, shine))
        // (4, 4): s ≈ -0.45 → noch volle Lichtstufe.
        assertEquals(BevelPaint.mix(base, shine, 0.55f), BevelPaint.kugel(4, 4, base, shine))
        // (5, 4): s ≈ -0.34 → zweite Lichtstufe.
        assertEquals(BevelPaint.mix(base, shine, 0.22f), BevelPaint.kugel(5, 4, base, shine))
        // Mitte: unverändert.
        assertEquals(base, BevelPaint.kugel(6, 6, base, shine))
        // (6, 5): s ≈ -0.11 → Mitte, knapp nicht mehr Licht.
        assertEquals(base, BevelPaint.kugel(6, 5, base, shine))
        // (9, 9): s ≈ 0.68 → Schatten zur Kontur hin.
        assertEquals(BevelPaint.mix(base, BevelPaint.OUTLINE, 0.28f), BevelPaint.kugel(9, 9, base, shine))
        // (8, 9): s ≈ 0.57 → Schatten; (8, 8): s ≈ 0.45 → Mitte.
        assertEquals(BevelPaint.mix(base, BevelPaint.OUTLINE, 0.28f), BevelPaint.kugel(8, 9, base, shine))
        assertEquals(base, BevelPaint.kugel(8, 8, base, shine))
        assertEquals(0xFFD5DEE2L, BevelPaint.EYE_EDGE)
    }

    @Test
    fun `die Mine bekommt ihre Kante an den richtigen Pixeln`() {
        val m = TrapPaint.MINE
        // Innen: alle vier Nachbarn gesetzt → Kugelfarbe.
        assertEquals(TrapPaint.BALL, BevelPaint.mineCell(4, 3, red = false))
        assertEquals(TrapPaint.RED, BevelPaint.mineCell(4, 3, red = true))
        // Zacke oben: rechts und unten? Rechts fehlt → Schatten gewinnt.
        assertEquals(TrapPaint.BALL_DARK, BevelPaint.mineCell(0, 3, red = false))
        // Linke Kante in Zeile 1: links fehlt, rechts und unten da → Licht.
        assertEquals(TrapPaint.BALL_LIGHT, BevelPaint.mineCell(1, 1, red = false))
        assertEquals(TrapPaint.RED_LIGHT, BevelPaint.mineCell(1, 1, red = true))
        // Rechte Kante in Zeile 4: rechts fehlt → Schatten.
        assertEquals(TrapPaint.BALL_DARK, BevelPaint.mineCell(4, 5, red = false))
        assertEquals(TrapPaint.RED_DARK, BevelPaint.mineCell(4, 5, red = true))
        // Unterste volle Zeile: unten fehlt (außer über der Zacke) → Schatten.
        assertEquals(TrapPaint.BALL_DARK, BevelPaint.mineCell(5, 2, red = false))
        // Über der unteren Zacke ist unten gesetzt, links und rechts auch,
        // oben auch → innen.
        assertEquals(TrapPaint.BALL, BevelPaint.mineCell(5, 3, red = false))
        // Der Glanz zählt als Nachbar: (3, 1) hat links 'O', oben 'O'
        // ... und rechts 'W' → innen.
        assertEquals('W', m[3][2])
        assertEquals(TrapPaint.BALL, BevelPaint.mineCell(3, 1, red = false))

        // Gegenprobe über das ganze Sprite gegen die Regel.
        fun set(r: Int, c: Int) = r in m.indices && c in m[r].indices && m[r][c] != '.'
        for (r in m.indices) for (c in m[r].indices) {
            if (m[r][c] != 'O') continue
            val erwartet = when {
                !set(r, c + 1) || !set(r + 1, c) -> TrapPaint.BALL_DARK
                !set(r, c - 1) || !set(r - 1, c) -> TrapPaint.BALL_LIGHT
                else -> TrapPaint.BALL
            }
            assertEquals("Pixel ($r, $c)", erwartet, BevelPaint.mineCell(r, c, red = false))
        }
    }

    @Test
    fun `die Minenkanten bleiben bei den abgestimmten Werten`() {
        assertEquals(0xFF4E4656L, TrapPaint.BALL_LIGHT)
        assertEquals(0xFF0A080CL, TrapPaint.BALL_DARK)
        assertEquals(0xFFFF8A7EL, TrapPaint.RED_LIGHT)
        assertEquals(0xFF9E1F1CL, TrapPaint.RED_DARK)
        // Das Sprite selbst bleibt unverändert.
        assertEquals(
            listOf("...O...", ".OOOOO.", ".OWWOO.", "OOWOOOO", ".OOOOO.", ".OOOOO.", "...O..."),
            TrapPaint.MINE
        )
    }

    @Test
    fun `Galaxienarme verblassen in drei deckenden Stufen`() {
        val arm = 0xFFE89AB8L
        val himmel = ScenePaint.sky(SceneId.WELTRAUM)
        for (sky in himmel) {
            assertEquals(arm, BevelPaint.galaxyTone(arm, sky, 0f))
            assertEquals(arm, BevelPaint.galaxyTone(arm, sky, 0.34f))
            assertEquals(BevelPaint.mix(arm, sky, 0.2f), BevelPaint.galaxyTone(arm, sky, 0.35f))
            assertEquals(BevelPaint.mix(arm, sky, 0.2f), BevelPaint.galaxyTone(arm, sky, 0.69f))
            assertEquals(BevelPaint.mix(arm, sky, 0.45f), BevelPaint.galaxyTone(arm, sky, 0.7f))
            assertEquals(BevelPaint.mix(arm, sky, 0.45f), BevelPaint.galaxyTone(arm, sky, 1f))
            assertEquals(BevelPaint.galaxyTone(arm, sky, 0.5f), BevelPaint.galaxyGlow(arm, sky))
        }
        assertEquals(BevelPaint.mix(arm, 0xFFFFFFFF, 0.6f), BevelPaint.galaxyDust(arm))
    }

    @Test
    fun `keine Galaxienstufe kommt dem Zonengruen nahe`() {
        val zone = listOf(0xFF74BF2EL, 0xFF9DE85AL)
        val farben = ScenePaint.of(SceneId.WELTRAUM).backdrop!!.colors
        val arme = listOf(farben[4], farben[5])
        for (arm in arme) for (sky in ScenePaint.sky(SceneId.WELTRAUM)) {
            val stufen = listOf(0f, 0.5f, 0.9f).map { BevelPaint.galaxyTone(arm, sky, it) } +
                BevelPaint.galaxyDust(arm) + BevelPaint.galaxyGlow(arm, sky)
            for (s in stufen) for (z in zone) {
                assertTrue(
                    "Galaxienton ${hex(s)} liegt zu nah an ${hex(z)}",
                    abstand(s, z) >= ScenePaint.MIN_ZONE_DISTANCE
                )
            }
        }
    }

    @Test
    fun `die Wolkenunterkante ist dunkler als die Wolke`() {
        SceneId.entries.forEach { id ->
            val wolke = ScenePaint.cloud(id) ?: return@forEach
            val schatten = BevelPaint.cloudShade(wolke)
            assertTrue("$id: Unterkante ist nicht dunkler", helligkeit(schatten) < helligkeit(wolke))
            assertEquals(BevelPaint.mix(wolke, 0xFF7A9AB0, 0.3f), schatten)
        }
    }

    private fun helligkeit(c: Long) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)

    private fun abstand(a: Long, b: Long): Float {
        var sum = 0f
        for (shift in intArrayOf(16, 8, 0)) {
            val d = (((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)).toFloat()
            sum += d * d
        }
        return kotlin.math.sqrt(sum)
    }

    private fun hex(color: Long): String = "#" + color.toString(16).uppercase().takeLast(6)
}
