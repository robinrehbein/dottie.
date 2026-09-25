package de.robinrehbein.punkt.ui.screens

import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Zurück-Geste nach Plan 7.1 und 8.5 (AP-14): das oberste Overlay
 * zuerst, dann die Phase. Jede Stufe einzeln und jeweils gegen alles, was
 * darunter liegt.
 */
class BackActionTest {

    private fun action(
        help: Boolean = false,
        settings: Boolean = false,
        stats: Boolean = false,
        skins: Boolean = false,
        dailyIntro: Boolean = false,
        phase: GamePhase = GamePhase.READY
    ) = backAction(
        showHelp = help,
        showSettings = settings,
        showStats = stats,
        showSkins = skins,
        showDailyIntro = dailyIntro,
        phase = phase
    )

    @Test
    fun hilfeSchliesstZuerstAuchUeberAllemAnderen() {
        GamePhase.entries.forEach { phase ->
            assertEquals(BackAction.CLOSE_HELP, action(help = true, phase = phase))
            assertEquals(
                BackAction.CLOSE_HELP,
                action(help = true, settings = true, stats = true, skins = true, phase = phase)
            )
        }
    }

    @Test
    fun einstellungenVorStatistikUndSammlung() {
        GamePhase.entries.forEach { phase ->
            assertEquals(BackAction.CLOSE_SETTINGS, action(settings = true, phase = phase))
            assertEquals(
                BackAction.CLOSE_SETTINGS,
                action(settings = true, stats = true, skins = true, phase = phase)
            )
        }
    }

    @Test
    fun statistikVorSammlung() {
        GamePhase.entries.forEach { phase ->
            assertEquals(BackAction.CLOSE_STATS, action(stats = true, phase = phase))
            assertEquals(BackAction.CLOSE_STATS, action(stats = true, skins = true, phase = phase))
        }
    }

    @Test
    fun sammlungVorDerPhase() {
        GamePhase.entries.forEach { phase ->
            assertEquals(BackAction.CLOSE_COLLECTION, action(skins = true, phase = phase))
        }
    }

    @Test
    fun gameOverGehtInsMenue() {
        assertEquals(BackAction.TO_MENU, action(phase = GamePhase.OVER))
    }

    @Test
    fun imLaufWirdZurueckVerbraucht() {
        assertEquals(BackAction.CONSUME, action(phase = GamePhase.RUNNING))
        assertEquals(BackAction.CONSUME, action(phase = GamePhase.DYING))
    }

    @Test
    fun startbildschirmUeberlaesstZurueckDemSystem() {
        assertEquals(BackAction.NOT_HANDLED, action(phase = GamePhase.READY))
    }

    @Test
    fun dailyKarteBleibtBisAp22OhneWirkung() {
        // Die Karte gibt es erst mit AP-22 (Plan 8.4). Bis dahin
        // entscheidet allein die Phase.
        assertEquals(BackAction.NOT_HANDLED, action(dailyIntro = true, phase = GamePhase.READY))
        assertEquals(BackAction.TO_MENU, action(dailyIntro = true, phase = GamePhase.OVER))
    }

    @Test
    fun leistenSperreDauertLaengerAlsDieNeustartSperre() {
        // Sonst landete ein Wut-Tap nach dem Neustart-Ende, aber vor dem
        // Leisten-Ende im Menü statt im nächsten Lauf (Plan 7.1).
        assertTrue(GAME_OVER_BAR_LOCK_SECONDS > TimingGame.RESTART_LOCK_SECONDS)
        assertEquals(0.8f, GAME_OVER_BAR_LOCK_SECONDS)
    }
}
