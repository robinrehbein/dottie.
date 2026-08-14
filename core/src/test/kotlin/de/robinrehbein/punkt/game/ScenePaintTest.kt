package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Die Regeln, die eine Kulisse von der Bahn trennen. Was hier durchfällt,
 * fällt in allen vier Renderern gleichzeitig auf — und zwar erst im
 * Spiel, wo es niemand mehr korrigiert.
 */
class ScenePaintTest {

    /** Zielzone (Perfekt-Kern und Band) und Fallen-Zone. */
    private val zielzone = listOf(0xFF74BF2EL, 0xFF9DE85AL)
    private val falle = 0xFFB44FD8L

    private val maxStats = SkinStats(
        bestScore = 999,
        bestPerfectStreak = 99,
        bestDailyStreak = 99,
        runCount = 9_999,
        totalScore = 999_999,
        daysPlayed = 365,
        monthsPlayed = 12
    )

    /** Alle Farben einer Kulisse — Himmel, Wolke, Boden, Requisiten. */
    private fun farben(id: SceneId): List<Long> {
        val scene = ScenePaint.of(id)
        val out = mutableListOf<Long>()
        out += scene.sky.toList()
        scene.cloud?.let { out += it }
        scene.ground?.let { out += listOf(it.sand, it.sandShade, it.turfDark, it.turfLight) }
        scene.props.forEach { prop ->
            out += listOf(prop.dark, prop.body, prop.light, prop.stem, prop.stemShade)
            out += prop.accents
        }
        return out
    }

    @Test
    fun `keine Kulissenfarbe kommt der Zielzone oder der Falle nahe`() {
        // Die Kulisse ist die verkäufliche Fläche, die Bahn nicht. Damit
        // das trägt, darf keine Kulissenfarbe aussehen wie das, worauf
        // getippt wird (grün) oder worauf niemals getippt werden darf
        // (violett) — sonst verkauft die Kulisse Verwirrung.
        //
        // Der Bestand der WIESE reißt diese Grenze selbst: Buschgrün liegt
        // 13 Schritte neben der Zonenfarbe, die Grasnarbe trägt sie exakt.
        // Diese Flächen liegen seit jeher am unteren Bildrand, nie im
        // Ringband (die Bahn endet bei 72 % Höhe, die Kronen beginnen bei
        // 74 %) — sie bleiben deshalb als benannte Ausnahme stehen, statt
        // stillschweigend umgefärbt zu werden.
        val bestandsgruen = ScenePaint.LEGACY_ZONE_GREENS.toSet()

        SceneId.entries.forEach { id ->
            farben(id).forEach { farbe ->
                if (farbe in bestandsgruen) {
                    assertEquals(
                        "Nur die WIESE darf das Bestandsgrün tragen",
                        SceneId.WIESE, id
                    )
                    return@forEach
                }
                zielzone.forEach { zone ->
                    assertTrue(
                        "$id trägt ${hex(farbe)} — nur ${abstand(farbe, zone)} " +
                            "vom Zielzonen-Ton ${hex(zone)} entfernt",
                        abstand(farbe, zone) >= ScenePaint.MIN_ZONE_DISTANCE
                    )
                }
                assertTrue(
                    "$id trägt ${hex(farbe)} — nur ${abstand(farbe, falle)} von der Falle entfernt",
                    abstand(farbe, falle) >= ScenePaint.MIN_ZONE_DISTANCE
                )
            }
        }
    }

    @Test
    fun `das Bestandsgruen bleibt der WIESE vorbehalten und bleibt unveraendert`() {
        // Regression zur Ausnahme oben: Wenn jemand die Ausnahme
        // ausnutzen wollte, müsste er sie erst erweitern — und das fällt
        // hier auf.
        assertEquals(4, ScenePaint.LEGACY_ZONE_GREENS.size)
        assertEquals(
            listOf(0xFF71C837L, 0xFF5AA82CL, 0xFF9DE85AL, 0xFF74BF2EL),
            ScenePaint.LEGACY_ZONE_GREENS.toList()
        )
    }

    @Test
    fun `die sieben Himmelsstufen einer Kulisse bleiben unterscheidbar`() {
        // Der Himmel ist Fortschrittsanzeige, kein Dekor: Wer eine Stufe
        // erreicht, muss den Wechsel sehen. Zwei Stufen, die sich nur um
        // eine Nuance unterscheiden, nehmen dem Lauf sein Feedback.
        SceneId.entries.forEach { id ->
            val sky = ScenePaint.sky(id)
            assertEquals("$id braucht genau sieben Stufen", 7, sky.size)
            for (i in 1 until sky.size) {
                assertTrue(
                    "$id: Stufe ${i - 1} und $i liegen nur ${abstand(sky[i - 1], sky[i])} auseinander",
                    abstand(sky[i - 1], sky[i]) >= ScenePaint.MIN_SKY_STEP
                )
            }
            // Und der Zähler von SkinPaint muss in jeder Kulisse in die
            // Tabelle treffen, auch weit jenseits der Nacht.
            (0..500).forEach { score ->
                assertTrue(
                    "$id: Score $score zeigt auf eine Stufe außerhalb der Tabelle",
                    SkinPaint.skyStage(score) in sky.indices
                )
            }
        }
    }

    @Test
    fun `jede Kulisse hat volle Deckkraft`() {
        SceneId.entries.forEach { id ->
            farben(id).forEach { farbe ->
                assertEquals(
                    "$id: ${hex(farbe)} ist nicht deckend",
                    0xFFL, (farbe shr 24) and 0xFF
                )
            }
        }
    }

    @Test
    fun `die Bodenkante bleibt Layout-Anker — auch ohne Boden`() {
        // Die Bodenkante ist der einzige senkrechte Anker der Welt: Dort
        // stehen die Requisiten, dort beginnt der Bodenstreifen, und dort
        // setzt die Tod-Animation auf. Eine Kulisse, die sie verschieben
        // könnte, würde das Spielgefühl ändern — genau das darf sie nicht.
        assertEquals(0.88f, ScenePaint.GROUND_TOP, 1e-6f)

        val h = 2000f
        // Mario-Tod, Werte wie in allen Renderern (DEATH_HOP_SPEED /
        // DEATH_GRAVITY): erst Hüpfer nach oben, dann Sturz.
        fun sturzZeitBisKante(kante: Float, start: Float): Float {
            var t = 0f
            while (t < 10f) {
                val y = start + (-1.6f * t + 0.5f * 6f * t * t) * h
                if (y >= kante) return t
                t += 0.0005f
            }
            return -1f
        }

        val start = h * 0.44f
        val zeiten = SceneId.entries.map { id ->
            // ScenePaint.groundY kennt die Kulisse gar nicht — genau das
            // ist die Zusicherung. Der Aufruf steht hier trotzdem je
            // Kulisse, damit eine kulissenabhängige Kante auffliegt.
            ScenePaint.groundY(h).also { kante ->
                assertEquals("$id verschiebt die Bodenkante", 1760f, kante, 1e-3f)
            }
            sturzZeitBisKante(ScenePaint.groundY(h), start)
        }.toSet()
        assertEquals("Der Sturz endet nicht in jeder Kulisse auf derselben Linie", 1, zeiten.size)
        assertTrue("Der Sturz erreicht die Kante überhaupt", zeiten.first() > 0f)

        // Der WELTRAUM zeichnet keinen Boden — die Linie bleibt trotzdem.
        assertNull("Im Vakuum gibt es keinen Boden", ScenePaint.ground(SceneId.WELTRAUM))
        assertNull("Im Vakuum gibt es keine Wolken", ScenePaint.cloud(SceneId.WELTRAUM))
        SceneId.entries.filter { it != SceneId.WELTRAUM }.forEach {
            assertNotNull("$it braucht einen Boden", ScenePaint.ground(it))
            assertNotNull("$it braucht Wolken", ScenePaint.cloud(it))
        }
    }

    @Test
    fun `die WIESE ist Pixel fuer Pixel der Bestand`() {
        // Die Messlatte des ganzen Umbaus: Wer die Umstellung sieht, hat
        // sie falsch gemacht. Die Werte hier stammen aus GameOverlays.kt
        // und TimingGameScreen.kt vor der Einführung der Kulissen.
        assertEquals(
            listOf(
                0xFF4EC0CAL, 0xFF5B9BD5L, 0xFF7B6FD0L, 0xFFC0616FL,
                0xFFD98A3DL, 0xFF3D4A8CL, 0xFF2A2640L
            ),
            ScenePaint.sky(SceneId.WIESE).toList()
        )
        assertEquals(0xFFE9FCFDL, ScenePaint.cloud(SceneId.WIESE))
        val boden = ScenePaint.ground(SceneId.WIESE)!!
        assertEquals(0xFFDED895L, boden.sand)
        assertEquals(0xFFD3C87EL, boden.sandShade)
        assertEquals(0xFF74BF2EL, boden.turfDark)
        assertEquals(0xFF9DE85AL, boden.turfLight)

        val props = ScenePaint.props(SceneId.WIESE)
        assertEquals(
            listOf(PropShape.BAUM, PropShape.BLUME, PropShape.BAUM, PropShape.STRAUCH),
            props.map { it.shape }
        )
        assertEquals(listOf(0.075f, 0.032f, 0.058f, 0.026f), props.map { it.size })
        assertEquals(listOf(1.0f, 0.8f, -1.0f, 0.4f), props.map { it.sway })
        assertEquals(0xFF9C6B3CL, props[0].stem)
        assertEquals(0xFF7A4E2AL, props[0].stemShade)
        // Die Blütenmitte war immer Gold, nicht Grün.
        assertEquals(0xFFFFD847L, props[1].light)
        assertEquals(listOf(0xFFE53935L, 0xFFE9FCFDL), props[1].accents)
    }

    @Test
    fun `jede Kulisse beschreibt vollstaendig vier Requisiten`() {
        SceneId.entries.forEach { id ->
            val props = ScenePaint.props(id)
            assertEquals("$id braucht vier Requisiten-Plätze", ScenePaint.PROP_SLOTS, props.size)
            props.forEach { prop ->
                assertTrue("$id: Requisiten-Größe muss positiv sein", prop.size > 0f)
                assertTrue("$id: Requisite ist größer als das halbe Bild", prop.size < 0.5f)
                assertTrue("$id: Windanteil ist aus dem Ruder", prop.sway in -1.5f..1.5f)
            }
            // Formen, die einen Akzent brauchen, müssen mindestens zwei
            // haben — sonst wechselt nichts und die Reihe wirkt gestempelt.
            props.filter { it.shape == PropShape.BLUME || it.shape == PropShape.HOCHHAUS }
                .forEach {
                    assertTrue("$id: ${it.shape} braucht wechselnde Akzente", it.accents.size >= 2)
                }
        }
    }

    @Test
    fun `die WIESE ist offen, alles andere haengt an Leistung`() {
        val leer = SkinStats(0, 0, 0)
        assertTrue(ScenePaint.isUnlocked(SceneId.WIESE, leer))
        SceneId.entries.filter { it != SceneId.WIESE }.forEach {
            assertFalse("$it darf ohne Leistung nicht offen sein", ScenePaint.isUnlocked(it, leer))
        }
        assertEquals(1, ScenePaint.unlockedCount(leer))
    }

    @Test
    fun `jede Kulisse haengt an ihrer eigenen Achse`() {
        // Der Sinn der Streuung: Wer nur Rekorde jagt, bekommt trotzdem
        // nicht alle Kulissen, und wer nur täglich spielt, auch nicht.
        assertTrue(ScenePaint.isUnlocked(SceneId.WUESTE, SkinStats(0, 0, 0, runCount = 500)))
        assertFalse(ScenePaint.isUnlocked(SceneId.WUESTE, SkinStats(0, 0, 0, runCount = 499)))
        assertTrue(ScenePaint.isUnlocked(SceneId.MEER, SkinStats(0, 0, 0, totalScore = 10_000)))
        assertFalse(ScenePaint.isUnlocked(SceneId.MEER, SkinStats(0, 0, 0, totalScore = 9_999)))
        assertTrue(ScenePaint.isUnlocked(SceneId.BERG, SkinStats(0, 0, 30)))
        assertFalse(ScenePaint.isUnlocked(SceneId.BERG, SkinStats(0, 0, 29)))
        assertTrue(ScenePaint.isUnlocked(SceneId.STADT, SkinStats(85, 0, 0)))
        assertFalse(ScenePaint.isUnlocked(SceneId.STADT, SkinStats(84, 0, 0)))
    }

    @Test
    fun `der Weltraum schliesst die Kulissen-Sammlung ab`() {
        val fastAlles = maxStats.copy(bestScore = 84)
        assertFalse(
            "Solange die STADT fehlt, bleibt der WELTRAUM zu",
            ScenePaint.isUnlocked(SceneId.WELTRAUM, fastAlles)
        )
        assertTrue(ScenePaint.isUnlocked(SceneId.WELTRAUM, maxStats))
        assertEquals(SceneId.entries.size, ScenePaint.unlockedCount(maxStats))

        // Und er hängt an keinem Kauf: maxStats hat patronOwned = false.
        assertFalse(maxStats.patronOwned)
    }

    @Test
    fun `gespeicherte Namen finden zurueck, alles andere landet auf der WIESE`() {
        SceneId.entries.forEach { assertEquals(it, ScenePaint.fromName(it.name)) }
        assertEquals(SceneId.WIESE, ScenePaint.fromName(null))
        assertEquals(SceneId.WIESE, ScenePaint.fromName("WOLKENKUCKUCKSHEIM"))
    }

    // ===== Werkzeug =====

    /** Abstand zweier ARGB-Farben im RGB-Raum. */
    private fun abstand(a: Long, b: Long): Float {
        var sum = 0f
        for (shift in intArrayOf(16, 8, 0)) {
            val d = (((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)).toFloat()
            sum += d * d
        }
        return sqrt(sum)
    }

    private fun hex(color: Long): String = "#" + color.toString(16).uppercase().takeLast(6)
}
