package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Die Kernregeln aus AP-21: Startregel (erster Tap im Grün ist Treffer 1,
 * daneben kostet nichts) und BLIND! +1 unter NEBEL.
 */
class ReadyTapTest {

    private val dt = 1f / 240f

    /** Zählt jede Zufallszahl, die die Engine zieht. */
    private class ZaehlZufall(seed: Long) : Random() {
        private val inner = Random(seed)
        var zuege = 0
            private set

        override fun nextBits(bitCount: Int): Int {
            zuege++
            return inner.nextBits(bitCount)
        }
    }

    /** Wartet in READY, bis [bedingung] gilt. Liefert die Zahl der Frames. */
    private fun TimingGame.warteInReady(bedingung: TimingGame.() -> Boolean): Int {
        var frames = 0
        while (!bedingung() && frames < 20_000) {
            update(dt)
            frames++
            assertEquals(GamePhase.READY, phase)
        }
        assertTrue("Bedingung in READY nie erreicht", bedingung())
        return frames
    }

    private fun TimingGame.imKern(): Boolean = abs(relativeToZone()) <= perfectHalf() * 0.5f

    private fun TimingGame.amRand(): Boolean =
        isInZone && abs(relativeToZone()) > perfectHalf()

    // ===== Startregel =====

    @Test
    fun `perfekter Start-Treffer liefert Started vor PerfectHit`() {
        val s = TimingGame(Random(1))
        s.warteInReady { imKern() }
        val event = s.tap()
        assertEquals(GameEventPerfectHit, event)
        assertEquals(GamePhase.RUNNING, s.phase)
        assertEquals(0f, s.elapsed, 0f)
        assertEquals(1, s.hits)
        assertEquals(1, s.perfectStreak)
        assertEquals(TimingGame.PERFECT_BASE_SCORE, s.score)
        assertEquals("Treffer dreht die Richtung", -1, s.direction)

        val geliefert = s.update(dt)
        assertEquals(listOf(GameEventStarted, GameEventPerfectHit), geliefert)
        // Die Serie überlebt die Auslieferung (die Apps setzen bei Started
        // ihre Anzeige zurück, deshalb muss Started zuerst kommen).
        assertEquals(1, s.perfectStreak)
    }

    @Test
    fun `Start am Zonenrand ist ein normaler Treffer`() {
        val s = TimingGame(Random(2))
        s.warteInReady { amRand() }
        assertEquals(GameEventHit, s.tap())
        assertEquals(1, s.score)
        assertEquals(1, s.hits)
        assertEquals(0, s.perfectStreak)
        assertEquals(listOf(GameEventStarted, GameEventHit), s.update(dt))
    }

    @Test
    fun `Start-Treffer setzt eine neue Zone und startet die Twist-Auswahl`() {
        val s = TimingGame(Random(3)).apply { twistOverride = setOf(Twist.PULSE) }
        s.warteInReady { isInZone }
        s.tap()
        assertNotEquals(TimingGame.READY_ZONE_CENTER, s.zoneCenter)
        assertEquals(setOf(Twist.PULSE), s.activeTwists.toSet())
        assertTrue("Neue Zone liegt voraus", s.relativeToZone() < 0f)
    }

    @Test
    fun `Tap daneben ist NotYet und laesst Zufall Zone und Phase unberuehrt`() {
        val zufall = ZaehlZufall(5)
        val s = TimingGame(zufall)
        assertFalse(s.isInZone)
        repeat(3) {
            val winkel = s.angle
            assertEquals(GameEventNotYet, s.tap())
            assertEquals(GamePhase.READY, s.phase)
            assertEquals(TimingGame.READY_ZONE_CENTER, s.zoneCenter, 0f)
            assertEquals(TimingGame.BASE_ZONE_HALF, s.zoneHalfWidth, 0f)
            assertEquals(winkel, s.angle, 0f)
            assertEquals(0, s.score)
            assertEquals(0, s.hits)
            assertEquals(0, zufall.zuege)
            // Das Event kommt über den Puffer wie jedes andere, der Punkt
            // kreist weiter.
            assertEquals(listOf(GameEventNotYet), s.update(0.1f))
            assertTrue(s.angle > winkel)
        }
    }

    @Test
    fun `NotYet aendert den folgenden Lauf nicht`() {
        val ohne = TimingGame(Random(11))
        val mit = TimingGame(Random(11))
        val frames = ohne.warteInReady { imKern() }
        repeat(frames) {
            if (!mit.isInZone) assertEquals(GameEventNotYet, mit.tap())
            mit.update(dt)
        }
        assertTrue(mit.imKern())
        ohne.tap()
        mit.tap()
        assertEquals(ohne.zoneCenter, mit.zoneCenter, 0f)
        assertEquals(ohne.activeTwists, mit.activeTwists)
        assertEquals(ohne.score, mit.score)
    }

    @Test
    fun `keine Spaet-Gnade in READY`() {
        val s = TimingGame(Random(4))
        s.warteInReady { relativeToZone() > effectiveZoneHalf() }
        val rel = s.relativeToZone()
        // Im Lauf wäre das noch ein Treffer (Spät-Gnade), in READY nicht.
        assertTrue(rel <= s.effectiveZoneHalf() + s.currentSpeed() * TimingGame.LATE_TAP_FORGIVENESS_SECONDS)
        assertEquals(GameEventNotYet, s.tap())
        assertEquals(GamePhase.READY, s.phase)
        assertEquals(0, s.score)
    }

    @Test
    fun `Daily-Seed bleibt deterministisch`() {
        fun lauf(mitFehltaps: Boolean): List<Float> {
            val s = TimingGame()
            s.reseed(DailyChallenge.seedFor(20_000L))
            while (!s.imKern()) {
                if (mitFehltaps && !s.isInZone) s.tap()
                s.update(dt)
            }
            s.tap()
            val zonen = mutableListOf(s.zoneCenter)
            var guard = 0
            while (zonen.size < 8 && guard++ < 100_000) {
                s.update(dt)
                if (s.phase != GamePhase.RUNNING) break
                if (abs(s.relativeToZone()) <= s.zoneHalfWidth * 0.1f) {
                    s.tap()
                    zonen += s.zoneCenter
                }
            }
            return zonen
        }
        val a = lauf(mitFehltaps = false)
        assertEquals(8, a.size)
        assertEquals(a, lauf(mitFehltaps = false))
        assertEquals(a, lauf(mitFehltaps = true))
    }

    @Test
    fun `Sofort-Neustart aus OVER bleibt`() {
        val s = TimingGame(Random(6))
        s.start()
        assertEquals(GameEventDied, s.tap()) // sofort: ZU FRÜH
        var guard = 0
        while (s.phase != GamePhase.OVER && guard++ < 10_000) s.update(1f / 60f)
        repeat(60) { s.update(1f / 60f) }
        assertEquals(GameEventStarted, s.tap())
        assertEquals(GamePhase.RUNNING, s.phase)
        assertEquals(0, s.score)
    }

    // ===== BLIND! +1 =====

    /** NEBEL-Lauf mit zwei perfekten Treffern (Serie 2). */
    private fun nebelLaufMitSerie(bonus: Boolean): TimingGame {
        val s = TimingGame(Random(21)).apply {
            twistOverride = setOf(Twist.GHOST)
            blindBonus = bonus
        }
        s.warteInReady { imKern() }
        assertEquals(GameEventPerfectHit, s.tap())
        s.laufeBis { imKern() }
        assertEquals(GameEventPerfectHit, s.tap())
        assertEquals(2, s.perfectStreak)
        return s
    }

    private fun TimingGame.laufeBis(bedingung: TimingGame.() -> Boolean) {
        var guard = 0
        while (!bedingung() && guard++ < 20_000) {
            update(dt)
            assertEquals(GamePhase.RUNNING, phase)
        }
        assertTrue(bedingung())
    }

    private fun TimingGame.blindImGruen(): Boolean = isInZone && isInFog

    @Test
    fun `blindBonus ist standardmaessig an`() {
        assertTrue(TimingGame().blindBonus)
    }

    @Test
    fun `Blindtreffer gibt plus eins und haelt die Serie`() {
        val s = nebelLaufMitSerie(bonus = true)
        s.laufeBis { blindImGruen() }
        assertFalse(s.isDotVisible)
        val vorher = s.score
        assertEquals(GameEventHit, s.tap())
        assertTrue(s.lastHitBlind)
        assertEquals(2, s.lastHitPoints)
        assertEquals(vorher + 2, s.score)
        assertEquals("Serie bleibt stehen", 2, s.perfectStreak)

        // Der nächste Perfekt-Treffer setzt die Serie fort.
        s.laufeBis { imKern() }
        assertEquals(GameEventPerfectHit, s.tap())
        assertFalse(s.lastHitBlind)
        assertEquals(3, s.perfectStreak)
        assertEquals(TimingGame.PERFECT_BASE_SCORE + 2, s.lastHitPoints)
    }

    @Test
    fun `ohne blindBonus ist der Treffer im Nebel ein normaler`() {
        val s = nebelLaufMitSerie(bonus = false)
        s.laufeBis { blindImGruen() }
        val vorher = s.score
        assertEquals(GameEventHit, s.tap())
        assertFalse(s.lastHitBlind)
        assertEquals(1, s.lastHitPoints)
        assertEquals(vorher + 1, s.score)
        assertEquals(0, s.perfectStreak)
    }

    @Test
    fun `Fehltap im Nebel gibt nichts`() {
        val s = nebelLaufMitSerie(bonus = true)
        s.laufeBis { isInFog && !isInZone }
        val vorher = s.score
        assertEquals(GameEventDied, s.tap())
        assertEquals(DeathCause.EARLY, s.lastDeathCause)
        assertFalse(s.lastHitBlind)
        assertEquals(vorher, s.score)
    }

    @Test
    fun `ohne NEBEL gibt es keinen Blindtreffer`() {
        val s = TimingGame(Random(21)).apply { twistOverride = emptySet() }
        s.warteInReady { imKern() }
        s.tap()
        s.laufeBis { blindImGruen() } // Geometrie ja, Twist nein
        assertTrue(s.isDotVisible)
        assertEquals(GameEventHit, s.tap())
        assertFalse(s.lastHitBlind)
        assertEquals(1, s.lastHitPoints)
    }

    @Test
    fun `Start-Treffer und Spaet-Gnade sind nie blind`() {
        val s = TimingGame(Random(21)).apply { twistOverride = setOf(Twist.GHOST) }
        s.warteInReady { isInZone }
        s.tap()
        assertFalse(s.lastHitBlind)
        s.laufeBis { relativeToZone() > effectiveZoneHalf() }
        assertEquals(GameEventHit, s.tap())
        assertFalse(s.lastHitBlind)
        assertEquals(1, s.lastHitPoints)
    }
}
