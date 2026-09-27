package de.robinrehbein.punkt.ui.platform

import de.robinrehbein.punkt.game.DailyMissions
import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.game.Twist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die Ereignisse bleiben in den Grenzen von Firebase Analytics — sonst
 * verwirft Firebase sie still, und die Zahlen fehlen, ohne dass es
 * jemand merkt.
 */
class TelemetryTest {

    private val name = Regex("^[a-z][a-z0-9_]{0,39}$")

    private val alle = listOf(
        Telemetry.runEnd(
            score = 23, hits = 17, cause = DeathCause.EARLY, missSeconds = 0.034f,
            daily = true, runNumber = 120, bestBefore = 41, newRecord = false,
            maxPerfectStreak = 4, activeTwists = setOf(Twist.FAKE, Twist.PULSE), skin = "KLASSIK"
        ),
        Telemetry.dailyEnd(12, 5, 1),
        Telemetry.missionDone(DailyMissions.forDay(20_000L, 2)[0], 2, allDone = true),
        Telemetry.jokerEarned("missions", 1),
        Telemetry.streakRescue("watched", 9),
        Telemetry.skinUnlocked(25),
        Telemetry.reminder("prompt_yes"),
        Telemetry.reminderOpened("risk"),
        Telemetry.openScreen("stats"),
        Telemetry.share(30, daily = false)
    )

    @Test
    fun `Namen und Parameter halten die Grenzen ein`() {
        alle.forEach { e ->
            assertTrue(name.matches(e.name), "Ereignisname: ${e.name}")
            assertTrue(e.params.size <= 25, "zu viele Parameter: ${e.name}")
            e.params.forEach { (k, v) ->
                assertTrue(name.matches(k), "Parametername: $k")
                assertTrue(v is Long || v is String, "Wert $k ist ${v::class}")
                if (v is String) assertTrue(v.length <= 100, "Wert $k zu lang")
            }
        }
    }

    @Test
    fun `run_end traegt Ursache, Abstand und Twists`() {
        val e = alle.first()
        assertEquals("run_end", e.name)
        assertEquals("early", e.params["cause"])
        assertEquals(34L, e.params["miss_ms"])
        assertEquals("fake+pulse", e.params["twists"])
        assertEquals(1L, e.params["daily"])
    }

    @Test
    fun `ohne Fehltap kein Abstand`() {
        val e = Telemetry.runEnd(
            score = 5, hits = 5, cause = DeathCause.MISSED, missSeconds = 0f,
            daily = false, runNumber = 1, bestBefore = 0, newRecord = true,
            maxPerfectStreak = 0, activeTwists = emptySet(), skin = "KLASSIK"
        )
        assertFalse("miss_ms" in e.params)
        assertEquals("none", e.params["twists"])
    }
}
