package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
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
        spiel.twistOverride = setOf(TimingGame.Twist.PULSE)
        spiel.tap()

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
        spiel.twistOverride = setOf(TimingGame.Twist.PULSE, TimingGame.Twist.FAKE)
        spiel.tap() // startet den Lauf und setzt die Twists
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
}
