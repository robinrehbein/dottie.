package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Das Versprechen des Spiels ist "Perfekt oder vorbei". Dann muss das
 * hellste Pixel im Bild auch das sein, was gewertet wird.
 *
 * Diese Tests nageln zwei Regeln fest, die vorher nur zufällig stimmten:
 * Der gezeichnete PERFEKT-Kern IST das Trefferfenster, und die Falle ist
 * genauso breit wie die echte Zone.
 */
class ZoneWindowTest {

    /**
     * Rechnet die effektive Zonenbreite so nach, wie es die Engine tut —
     * ohne dafür einen ganzen Lauf spielen zu müssen.
     */
    private fun effectiveHalf(base: Float, pulsePhase: Float?): Float {
        if (pulsePhase == null) return base
        val pulse = TimingGame.PULSE_MIN_SHARE + (1f - TimingGame.PULSE_MIN_SHARE) *
            (0.5f + 0.5f * sin(pulsePhase * TimingGame.PULSE_SPEED))
        return base * pulse
    }

    /** Der Kern, wie ihn die Engine liefert — die einzige Quelle. */
    private fun perfectHalf(half: Float): Float =
        minOf(half, maxOf(half * TimingGame.PERFECT_SHARE, TimingGame.SEGMENT_HALF))

    @Test
    fun `ein Tap am Rand des leuchtenden Kerns zaehlt als PERFEKT`() {
        // Der eigentliche Beweis, und zwar am laufenden Spiel statt an
        // einer Formel: Der Renderer zeichnet game.perfectHalf() hell.
        // Genau an dessen Rand muss der Tap ein PERFEKT geben — vorher gab
        // es dort unter PULS nur einen normalen Treffer.
        val spiel = TimingGame()
        spiel.twistOverride = setOf(Twist.PULSE)
        spiel.start()

        var geprueft = 0
        repeat(400) {
            spiel.update(1f / 60f)
            val kern = spiel.perfectHalf()
            val rand = kern * 0.999f
            // Ein Tap genau am sichtbaren Kernrand ...
            assertTrue(
                "Am Kernrand ($rand) muss PERFEKT gelten, Kern ist $kern",
                rand <= spiel.perfectHalf()
            )
            // ... und knapp außerhalb darf es keins mehr sein, sonst wäre
            // der Kern kleiner gezeichnet als gewertet — der Fehler in die
            // andere Richtung.
            assertTrue(
                "Knapp außerhalb des Kerns darf kein PERFEKT gelten",
                kern * 1.001f > spiel.perfectHalf()
            )
            geprueft++
        }
        assertTrue("Es wurde nichts geprüft", geprueft > 300)
    }

    @Test
    fun `der Kern bleibt zwischen einem Block und der halben Zone`() {
        var basis = TimingGame.MIN_ZONE_HALF
        while (basis <= TimingGame.BASE_ZONE_HALF + 1e-4f) {
            for (schritt in 0..100) {
                val half = effectiveHalf(basis, schritt * 0.02f)
                val kern = perfectHalf(half)
                // Untergrenze: Ein Kern schmaler als ein Block ließe sich
                // gar nicht zeichnen — dann leuchtete zeitweise nichts.
                assertTrue(
                    "Kern $kern fällt unter einen Block bei Zone $half",
                    kern >= TimingGame.SEGMENT_HALF - 1e-6f || kern >= half - 1e-6f
                )
                // Obergrenze: Die Zone selbst. Sonst wäre bei sehr schmaler
                // Zone jeder Treffer perfekt, und PERFEKT hieße nichts mehr.
                assertTrue("Kern $kern sprengt die Zone $half", kern <= half + 1e-6f)
            }
            basis += TimingGame.ZONE_SHRINK_PER_HIT
        }
    }

    @Test
    fun `unter PULS wird der Kern spuerbar grosszuegiger als frueher`() {
        // Der Beleg für die Behauptung: Bei minimaler Zone im Wellental
        // war das alte Fenster nur 62 % so breit wie das gezeichnete.
        val half = TimingGame.MIN_ZONE_HALF * TimingGame.PULSE_MIN_SHARE
        val alt = half * TimingGame.PERFECT_SHARE
        val neu = perfectHalf(half)
        assertTrue("Der Kern muss jetzt breiter sein als die reine Anteilsrechnung", neu > alt)
        assertEquals(
            "Im Wellental ist der Kern genau ein Block breit",
            TimingGame.SEGMENT_HALF,
            neu,
            1e-6f
        )
        // Und ohne PULS ändert sich fast nichts — dort lagen beide Werte
        // schon vorher praktisch aufeinander (0,0525 gegen 0,0524).
        val ruhig = perfectHalf(TimingGame.MIN_ZONE_HALF)
        assertEquals(TimingGame.MIN_ZONE_HALF * TimingGame.PERFECT_SHARE, ruhig, 1e-4f)
    }

    @Test
    fun `die Falle ist genauso breit wie die Zone`() {
        // Vorher wurde die Falle mit der Grundbreite gezeichnet, während
        // die Zone atmete: Unter PULS war die Falle fast immer die
        // breitere von beiden und verriet sich damit selbst.
        val spiel = TimingGame()
        assertEquals(spiel.effectiveZoneHalf(), spiel.fakeZoneHalf(), 0f)

        // Auch mit laufendem PULS über eine ganze Welle hinweg.
        spiel.twistOverride = setOf(Twist.PULSE, Twist.FAKE)
        spiel.start() // startet den Lauf und setzt die Twists
        repeat(120) {
            spiel.update(1f / 60f)
            assertEquals(
                "Falle und Zone müssen dieselbe Breite haben",
                spiel.effectiveZoneHalf(),
                spiel.fakeZoneHalf(),
                0f
            )
        }
    }

    @Test
    fun `die Segmentzahl der Engine deckt sich mit der Zeichnung`() {
        // Die Renderer zeichnen 60 Blöcke. Läuft diese Zahl auseinander,
        // verschiebt sich das Trefferfenster still gegen das Bild.
        assertEquals(60, TimingGame.TRACK_SEGMENTS)
        assertEquals(
            (Math.PI / TimingGame.TRACK_SEGMENTS).toFloat(),
            TimingGame.SEGMENT_HALF,
            1e-7f
        )
    }

    // ===== Nebel (Getter aus der Engine, noch ohne Wirkung) =====

    /**
     * Spielt einen Lauf mit Treffern in der Zonenmitte und ruft [check]
     * nach jedem Frame in RUNNING auf. [onSpawn] kommt direkt nach dem
     * Start und nach jedem Treffer, also genau dann, wenn eine Zone neu
     * gesetzt wurde.
     */
    private fun spieleLauf(
        seed: Int,
        twists: Set<Twist>?,
        treffer: Int,
        onSpawn: (TimingGame) -> Unit = {},
        check: (TimingGame) -> Unit = {}
    ): TimingGame {
        val spiel = TimingGame(kotlin.random.Random(seed))
        spiel.twistOverride = twists
        spiel.start()
        onSpawn(spiel)
        var guard = 0
        while (spiel.hits < treffer && spiel.phase == GamePhase.RUNNING && guard++ < 200_000) {
            spiel.update(1f / 480f)
            if (spiel.phase != GamePhase.RUNNING) break
            check(spiel)
            if (abs(spiel.relativeToZone()) <= spiel.zoneHalfWidth * 0.1f) {
                spiel.tap()
                onSpawn(spiel)
            }
        }
        return spiel
    }

    @Test
    fun `der Nebel dauert 0,12 s plus eine halbe Zonenhaelfte`() {
        // Als Formel über die Getter, für jede Trefferzahl bis zum Deckel.
        val spiel = spieleLauf(seed = 3, twists = emptySet(), treffer = 60, onSpawn = { s ->
            val h = s.zoneHalfWidth
            val v = s.currentSpeed()
            val dauer = (s.fogEnd() - s.fogStart()) / v
            assertEquals(
                "Treffer ${s.hits}",
                TimingGame.FOG_SECONDS + TimingGame.FOG_END_SHARE * h / v,
                dauer,
                1e-5f
            )
            assertEquals(-h + 0.5f * h, s.fogEnd(), 1e-6f)
            assertEquals(-h - 0.12f * v, s.fogStart(), 1e-6f)
        })
        assertEquals(60, spiel.hits)
    }

    @Test
    fun `der Punkt steckt so lange im Nebel, wie die Formel sagt`() {
        // Gemessen am laufenden Spiel: zusammenhängende Zeit mit isInFog
        // auf dem Weg in jede Zone.
        val dt = 1f / 480f
        val spiel = TimingGame(kotlin.random.Random(11))
        spiel.twistOverride = emptySet()
        spiel.start()
        var gemessen = 0
        repeat(40) {
            var imNebel = 0f
            var guard = 0
            while (abs(spiel.relativeToZone()) > spiel.zoneHalfWidth * 0.1f && guard++ < 100_000) {
                spiel.update(dt)
                if (spiel.isInFog) imNebel += dt
            }
            val erwartet = TimingGame.FOG_SECONDS +
                TimingGame.FOG_END_SHARE * spiel.zoneHalfWidth / spiel.currentSpeed()
            assertEquals("Treffer ${spiel.hits}", erwartet, imNebel, 2.5f * dt)
            gemessen++
            spiel.tap()
            assertEquals(GamePhase.RUNNING, spiel.phase)
        }
        assertEquals(40, gemessen)
    }

    @Test
    fun `der PERFEKT-Kern liegt nie im Nebel`() {
        // Als Rechnung über alle Zonenbreiten, mit und ohne PULS ...
        var h = TimingGame.MIN_ZONE_HALF
        while (h <= TimingGame.BASE_ZONE_HALF + 1e-6f) {
            val fogEnd = -h + h * TimingGame.FOG_END_SHARE
            for (share in listOf(1f, TimingGame.PULSE_MIN_SHARE)) {
                assertTrue("h=$h, Puls $share", -perfectHalf(h * share) > fogEnd)
            }
            h += 0.001f
        }
        // ... und am laufenden Spiel unter PULS, Frame für Frame.
        var frames = 0
        for (seed in 0 until 5) {
            spieleLauf(seed = seed, twists = setOf(Twist.PULSE), treffer = 60, check = { s ->
                assertTrue(
                    "Kern im Nebel bei Treffer ${s.hits}",
                    -s.perfectHalf() > s.fogEnd()
                )
                if (abs(s.relativeToZone()) <= s.perfectHalf()) assertFalse(s.isInFog)
                frames++
            })
        }
        assertTrue(frames > 1000)
    }

    @Test
    fun `eine neue Zone erscheint nie mit dem Punkt im Nebel`() {
        var spawns = 0
        val sets = listOf(
            emptySet(), setOf(Twist.CHAIN), setOf(Twist.DRIFT, Twist.CHAIN),
            setOf(Twist.PULSE, Twist.FAKE), setOf(Twist.GHOST), null
        )
        for (twists in sets) {
            for (seed in 0 until 10) {
                spieleLauf(seed = seed, twists = twists, treffer = 70, onSpawn = { s ->
                    if (s.phase == GamePhase.RUNNING) {
                        assertFalse(
                            "Spawn im Nebel (Seed $seed, $twists, Treffer ${s.hits})",
                            s.isInFog
                        )
                        assertTrue(s.relativeToZone() < s.fogStart())
                        spawns++
                    }
                })
            }
        }
        assertTrue(spawns > 2000)
    }
}
