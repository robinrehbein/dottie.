package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Tests für den Rewarded-Revive ([TimingGame.revive]).
 *
 * Der Revive ist die einzige Stelle, an der ein Lauf nach dem Tod
 * weiterläuft — er darf weder Punkte verschenken noch beliebig oft
 * gehen, und vor allem darf der Punkt nach dem Weiterspielen nicht
 * sofort wieder sterben. Ein sofortiger Tod wäre nach einem gesehenen
 * Werbespot der schlimmste denkbare Fall, deshalb wird die Schonfrist
 * hier über mehrere Tempo-Stufen geprüft.
 */
class TimingGameReviveTest {

    private fun newGame(seed: Int = 42): TimingGame =
        TimingGame(random = Random(seed)).apply {
            // Ohne Twists: die Revive-Mechanik isoliert prüfbar.
            twistOverride = emptySet()
        }

    /** Simuliert Frames, bis der Punkt in der Zone steht. */
    private fun TimingGame.runUntilInZone(maxSeconds: Float = 5f): Boolean {
        var time = 0f
        while (time < maxSeconds) {
            update(1f / 120f)
            time += 1f / 120f
            if (phase != TimingGame.Phase.RUNNING) return false
            if (isInZone) return true
        }
        return false
    }

    private fun TimingGame.hitZone(): TimingGame.GameEvent? {
        if (!runUntilInZone()) return null
        return tap()
    }

    /** Bringt das Spiel per Fehl-Tap in die DYING-Phase. */
    private fun TimingGame.killByBadTap() {
        // Direkt nach einem Treffer/Start ist die Zone weit weg — ein
        // sofortiger Tap liegt garantiert daneben.
        tap()
        check(phase == TimingGame.Phase.DYING) { "Erwarteter Fehl-Tap-Tod, Phase=$phase" }
        // Ein Frame, damit das Died-Event abgeholt ist — im Spiel liegt
        // zwischen Tod und Revive-Angebot ohnehin die ganze
        // Sturz-Animation.
        update(1f / 60f)
    }

    /** Läuft bis in die OVER-Phase weiter (Freeze + Fall-Animation). */
    private fun TimingGame.settle() {
        var time = 0f
        while (phase != TimingGame.Phase.OVER && time < 5f) {
            update(1f / 60f)
            time += 1f / 60f
        }
    }

    @Test
    fun `revive keeps score and hits but resets the perfect streak`() {
        val game = newGame()
        game.tap()
        repeat(3) { game.hitZone() }
        val scoreBefore = game.score
        val hitsBefore = game.hits
        assertTrue("Testlauf sollte Punkte gesammelt haben", scoreBefore > 0)
        game.killByBadTap()

        assertTrue(game.revive())

        assertEquals(scoreBefore, game.score)
        assertEquals(hitsBefore, game.hits)
        assertEquals(0, game.perfectStreak)
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
    }

    @Test
    fun `revive emits a Revived event with the next update`() {
        val game = newGame()
        game.tap()
        game.killByBadTap()

        assertTrue(game.revive())

        assertTrue(game.update(1f / 60f).contains(TimingGame.GameEvent.Revived))
    }

    @Test
    fun `revive works from the OVER phase too`() {
        val game = newGame()
        game.tap()
        game.killByBadTap()
        game.settle()
        assertEquals(TimingGame.Phase.OVER, game.phase)

        assertTrue(game.revive())
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
    }

    @Test
    fun `revive is only available once per run`() {
        val game = newGame()
        game.tap()
        game.killByBadTap()
        assertTrue(game.revive())

        // Zweiter Tod im selben Lauf: kein zweites Weiterspielen.
        game.killByBadTap()
        assertFalse(game.revive())
        assertEquals(TimingGame.Phase.DYING, game.phase)
    }

    @Test
    fun `revive is refused while the run is alive`() {
        val game = newGame()
        assertFalse("READY ist kein Tod", game.revive())

        game.tap()
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
        assertFalse("RUNNING ist kein Tod", game.revive())
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
    }

    @Test
    fun `reset makes revive available again`() {
        val game = newGame()
        game.tap()
        game.killByBadTap()
        assertTrue(game.revive())
        assertTrue(game.reviveUsed)

        game.reset()
        assertFalse(game.reviveUsed)

        game.tap()
        game.killByBadTap()
        assertTrue("Nach reset() muss der Revive wieder gehen", game.revive())
    }

    @Test
    fun `revive puts the dot clearly in front of the fresh zone`() {
        val game = newGame()
        game.tap()
        game.killByBadTap()

        assertTrue(game.revive())

        // Negativ = die Zone liegt noch vor dem Punkt. Der Abstand muss
        // deutlich größer sein als die Zone selbst, sonst stünde der
        // Punkt praktisch schon drin.
        val rel = game.relativeToZone()
        assertTrue("Zone bereits überfahren: rel=$rel", rel < 0f)
        assertTrue(
            "Schonfrist zu knapp: rel=$rel",
            abs(rel) >= TimingGame.REVIVE_GRACE_DISTANCE - 0.001f
        )
    }

    /**
     * Der Kern-Test: Nach dem Revive muss auf JEDER Tempo-Stufe echte
     * Reaktionszeit bleiben. Geprüft wird über wachsende Trefferzahlen
     * (bis das Tempo gedeckelt ist) — der Lauf darf im ersten Frame
     * nicht sterben, und auch die Sekunde danach nicht.
     */
    @Test
    fun `revive never kills the dot right away at any speed`() {
        for (hitsBefore in listOf(0, 5, 15, 30, 60)) {
            val game = newGame(seed = 100 + hitsBefore)
            game.tap()
            repeat(hitsBefore) {
                if (game.phase == TimingGame.Phase.RUNNING) game.hitZone()
            }
            if (game.phase != TimingGame.Phase.RUNNING) continue
            val speed = game.currentSpeed()
            game.killByBadTap()

            assertTrue(game.revive())

            // Ein realistischer Frame (60 Hz) direkt nach dem Revive.
            val firstFrame = game.update(1f / 60f)
            assertFalse(
                "Sofort-Tod nach Revive bei $hitsBefore Treffern (Tempo $speed)",
                firstFrame.contains(TimingGame.GameEvent.Died)
            )
            assertEquals(TimingGame.Phase.RUNNING, game.phase)

            // Auch nach einem halben Reaktionsfenster lebt der Punkt noch:
            // Es bleibt Zeit, den Daumen überhaupt zu heben.
            var time = 0f
            while (time < TimingGame.MIN_REACTION_SECONDS) {
                assertFalse(
                    "Zu früher Tod nach Revive bei $hitsBefore Treffern (Tempo $speed)",
                    game.update(1f / 60f).contains(TimingGame.GameEvent.Died)
                )
                time += 1f / 60f
            }
            assertEquals(TimingGame.Phase.RUNNING, game.phase)
        }
    }

    @Test
    fun `the run continues normally after a revive`() {
        val game = newGame()
        game.tap()
        repeat(2) { game.hitZone() }
        val scoreBefore = game.score
        game.killByBadTap()
        assertTrue(game.revive())

        val event = game.hitZone()

        assertTrue(
            "Nach dem Revive muss ein normaler Treffer wieder zählen",
            event == TimingGame.GameEvent.Hit || event == TimingGame.GameEvent.PerfectHit
        )
        assertTrue(game.score > scoreBefore)
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
    }
}
