package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Die Engine hält fest, woran ein Lauf gescheitert ist
 * ([TimingGame.lastDeathCause]), und friert Puls und Zonen-Uhr beim Tod
 * ein. Beides ist reine Anzeige-API: an der Wertung ändert sich nichts.
 */
class DeathCauseTest {

    private val dt = 1f / 240f

    private fun spiel(twists: Set<Twist>, seed: Int = 42): TimingGame =
        TimingGame(Random(seed)).apply {
            twistOverride = twists
            start()
        }

    private fun TimingGame.relToFake(): Float = TimingGame.wrapToPi(angle - fakeZoneCenter)

    /**
     * Ein Lauf mit Falle: Nicht jede Zone bekommt eine (sie braucht Platz
     * vor der echten Zone), deshalb über Seeds suchen.
     */
    private fun spielMitFalle(twists: Set<Twist>): TimingGame {
        for (seed in 0 until 200) {
            val s = spiel(twists, seed)
            if (s.hasFakeZone) return s
        }
        throw AssertionError("Kein Seed mit Falle gefunden")
    }

    /** Läuft, bis der Punkt in der Falle steht, und tappt dort. */
    private fun TimingGame.tappeInDieFalle(): GameEvent? {
        var guard = 0
        while (abs(relToFake()) > fakeZoneHalf() && guard++ < 20_000) {
            update(dt)
            assertEquals("Lauf endete vor der Falle", GamePhase.RUNNING, phase)
        }
        assertTrue("Punkt steht in der Falle", abs(relToFake()) <= fakeZoneHalf())
        return tap()
    }

    private fun TimingGame.bisOver() {
        var guard = 0
        while (phase != GamePhase.OVER && guard++ < 10_000) update(1f / 60f)
        assertEquals(GamePhase.OVER, phase)
    }

    @Test
    fun `vor dem ersten Tod gibt es keine Ursache`() {
        val s = TimingGame(Random(1))
        assertEquals(DeathCause.NONE, s.lastDeathCause)
        s.start()
        assertEquals(DeathCause.NONE, s.lastDeathCause)
    }

    @Test
    fun `sofortiger zweiter Tap ist ZU FRUEH`() {
        val s = spiel(emptySet())
        // Die Zone liegt nach dem Start mindestens MIN_ZONE_DISTANCE voraus.
        assertTrue(s.relativeToZone() < 0f)
        assertEquals(GameEventDied, s.tap())
        assertEquals(GamePhase.DYING, s.phase)
        assertEquals(DeathCause.EARLY, s.lastDeathCause)
    }

    @Test
    fun `Tap hinter der Spaet-Gnade ist ZU SPAET`() {
        val s = spiel(emptySet())
        val grenze = s.effectiveZoneHalf() +
            s.currentSpeed() * TimingGame.LATE_TAP_FORGIVENESS_SECONDS
        var guard = 0
        while (s.relativeToZone() <= grenze && guard++ < 20_000) {
            s.update(dt)
        }
        assertEquals("noch nicht überfahren", GamePhase.RUNNING, s.phase)
        assertTrue(s.relativeToZone() > grenze)

        assertEquals(GameEventDied, s.tap())
        assertEquals(DeathCause.LATE, s.lastDeathCause)
    }

    @Test
    fun `nie tappen ist VERPASST`() {
        val s = spiel(emptySet())
        val events = mutableListOf<GameEvent>()
        var guard = 0
        while (s.phase == GamePhase.RUNNING && guard++ < 20_000) events += s.update(dt)
        assertTrue(events.contains(GameEventDied))
        assertEquals(DeathCause.MISSED, s.lastDeathCause)

        // Die Ursache bleibt bis zum nächsten Start stehen ...
        s.bisOver()
        assertEquals(DeathCause.MISSED, s.lastDeathCause)

        // ... und der Sofort-Neustart löscht sie.
        repeat(60) { s.update(1f / 60f) } // länger als RESTART_LOCK_SECONDS
        assertEquals(GameEventStarted, s.tap())
        assertEquals(DeathCause.NONE, s.lastDeathCause)
    }

    @Test
    fun `Tap in die Falle ist BOOM, obwohl er vor der Zone liegt`() {
        val s = spielMitFalle(setOf(Twist.FAKE))
        assertEquals(GameEventDied, s.tappeInDieFalle())
        // Die Falle liegt vor der echten Zone: ohne Fallenprüfung wäre das
        // ein ZU FRÜH. Die Fallenprüfung kommt zuerst.
        assertTrue(s.relativeToZone() < 0f)
        assertEquals(DeathCause.TRAP, s.lastDeathCause)
    }

    @Test
    fun `Tap in die Falle unter PULS ist BOOM und die Breite bleibt stehen`() {
        var geprueft = 0
        for (seed in 0 until 60) {
            val s = spiel(setOf(Twist.FAKE, Twist.PULSE), seed)
            if (!s.hasFakeZone) continue
            // Einen Moment laufen lassen, damit der Puls nicht bei Phase 0 steht.
            var guard = 0
            while (abs(s.relToFake()) > s.fakeZoneHalf() && guard++ < 20_000) {
                s.update(dt)
                if (s.phase != GamePhase.RUNNING) break
            }
            if (s.phase != GamePhase.RUNNING) continue
            val halbVorher = s.effectiveZoneHalf()
            val falleVorher = s.fakeZoneHalf()
            assertEquals(GameEventDied, s.tap())
            assertEquals("Seed $seed", DeathCause.TRAP, s.lastDeathCause)
            assertEquals(halbVorher, s.effectiveZoneHalf(), 0f)
            assertEquals(falleVorher, s.fakeZoneHalf(), 0f)
            geprueft++
        }
        assertTrue("zu wenige Fallen-Tode geprüft: $geprueft", geprueft >= 10)
    }

    @Test
    fun `effectiveZoneHalf ist vor und nach dem Tod gleich`() {
        // Über viele Zeitpunkte der Pulswelle: Ohne Einfrieren sprang die
        // Breite beim Tod auf die Phase 0 zurück (die() setzt elapsed = 0)
        // und atmete in DYING weiter.
        var geprueft = 0
        var abseitsPhaseNull = 0
        for (schritte in 1..60) {
            val s = spiel(setOf(Twist.PULSE))
            repeat(schritte) { s.update(1f / 60f) }
            if (s.phase != GamePhase.RUNNING) continue
            val vorher = s.effectiveZoneHalf()
            val perfektVorher = s.perfectHalf()
            val event = s.tap()
            // Steht der Punkt zufällig im Grün, ist das ein Treffer, kein Tod.
            if (event != GameEventDied) continue
            geprueft++
            // Wert, den die Breite ohne Einfrieren nach die() (elapsed = 0) hätte.
            val phaseNull = s.zoneHalfWidth *
                (TimingGame.PULSE_MIN_SHARE + (1f - TimingGame.PULSE_MIN_SHARE) * 0.5f)
            if (abs(vorher - phaseNull) > 0.01f) abseitsPhaseNull++
            assertEquals("direkt nach dem Tap", vorher, s.effectiveZoneHalf(), 0f)
            assertEquals(perfektVorher, s.perfectHalf(), 0f)

            // Durch Freeze, Sturz und Game-Over hindurch.
            var guard = 0
            while (s.phase != GamePhase.OVER && guard++ < 1000) {
                s.update(1f / 60f)
                assertEquals("in ${s.phase}", vorher, s.effectiveZoneHalf(), 0f)
            }
            repeat(30) {
                s.update(1f / 60f)
                assertEquals("in OVER", vorher, s.effectiveZoneHalf(), 0f)
            }
        }
        assertTrue("zu wenige Tode geprüft: $geprueft", geprueft >= 30)
        // Der Test muss Fälle enthalten, in denen das Einfrieren etwas ändert.
        assertTrue("Puls stand fast immer bei Phase 0", abseitsPhaseNull >= 20)
    }

    @Test
    fun `der Puls laeuft im Lauf weiter`() {
        // Gegenprobe: Das Einfrieren darf nur nach dem Tod greifen.
        val s = spiel(setOf(Twist.PULSE))
        val gesehen = mutableSetOf<Float>()
        repeat(40) {
            s.update(1f / 60f)
            if (s.phase == GamePhase.RUNNING) gesehen += s.effectiveZoneHalf()
        }
        assertTrue(gesehen.size > 10)
    }

    @Test
    fun `zoneAge zaehlt ab Start und ab jedem Treffer und steht nach dem Tod`() {
        val s = spiel(emptySet())
        assertEquals(0f, s.zoneAge, 0f)
        repeat(12) { s.update(1f / 60f) }
        assertEquals(12f / 60f, s.zoneAge, 1e-4f)

        // Treffer: neue Zone, neue Uhr.
        var guard = 0
        while (!s.isInZone && guard++ < 20_000) s.update(dt)
        val treffer = s.tap()
        assertTrue(treffer == GameEventHit || treffer == GameEventPerfectHit)
        assertEquals(GamePhase.RUNNING, s.phase)
        assertEquals(0f, s.zoneAge, 0f)
        repeat(6) { s.update(1f / 60f) }
        assertEquals(6f / 60f, s.zoneAge, 1e-4f)

        // Tod: die Uhr bleibt beim Wert des Todes stehen.
        val beimTod = s.zoneAge
        assertEquals(GameEventDied, s.tap())
        repeat(20) {
            s.update(1f / 60f)
            assertEquals(beimTod, s.zoneAge, 0f)
        }
        s.bisOver()
        assertEquals(beimTod, s.zoneAge, 0f)
    }

    @Test
    fun `reset loescht Ursache und eingefrorene Werte`() {
        val s = spiel(setOf(Twist.PULSE))
        repeat(10) { s.update(1f / 60f) }
        s.tap()
        assertEquals(DeathCause.EARLY, s.lastDeathCause)
        s.reset()
        assertEquals(DeathCause.NONE, s.lastDeathCause)
        assertEquals(0f, s.zoneAge, 0f)
        assertFalse(s.lastHitBlind)
    }
}
