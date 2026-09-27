package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.DailyChallenge
import de.robinrehbein.punkt.game.DailyMissions
import de.robinrehbein.punkt.game.RunFacts
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tagesaufgaben, Serien-Joker und die einmaligen Fragen (ab v2.36), von
 * der Speicher-Seite aus gesehen.
 */
class GameStoreRetentionTest {

    private fun store() = GameStore(FakeKeyValueStore())

    private val tag = 20_000L

    /** Ein Lauf, der jede Aufgabe jeder Stufe auf einen Schlag erledigt. */
    private val alles = RunFacts(score = 999, perfectHits = 99, maxPerfectStreak = 99, blindHits = 9, daily = true)

    @Test
    fun `die Stufe des Tages bleibt, auch wenn der Rekord mittags steigt`() {
        val store = store()
        val morgens = store.missionDayFor(tag)
        assertEquals(0, morgens.tier)
        store.submitRun(score = 30, epochDay = tag, month = 6, year = 2026)
        assertEquals(0, store.missionDayFor(tag).tier, "Stufe wechselt erst mit dem Tag")
        assertEquals(DailyMissions.tierFor(30), store.missionDayFor(tag + 1).tier)
    }

    @Test
    fun `ein neuer Tag beginnt bei null`() {
        val store = store()
        store.submitMissionRun(tag, alles)
        assertTrue(store.missionDayFor(tag).allDone)
        val morgen = store.missionDayFor(tag + 1)
        assertEquals(listOf(0, 0, 0), morgen.progress)
    }

    @Test
    fun `alle drei erledigt zaehlt drei Aufgaben und bringt einen Joker`() {
        val store = store()
        val update = store.submitMissionRun(tag, alles)
        assertEquals(listOf(0, 1, 2), update.completed)
        assertTrue(update.allDoneNow)
        assertTrue(update.jokerEarned)
        assertEquals(3, store.missionsDone)
        assertEquals(1, store.streakJokers)

        // Ein zweiter Lauf am selben Tag zählt nichts doppelt.
        val nochmal = store.submitMissionRun(tag, alles)
        assertTrue(nochmal.completed.isEmpty())
        assertFalse(nochmal.allDoneNow)
        assertEquals(3, store.missionsDone)
        assertEquals(1, store.streakJokers)
    }

    @Test
    fun `der Joker-Vorrat ist gedeckelt`() {
        val store = store()
        repeat(DailyChallenge.MAX_JOKERS + 2) { store.submitMissionRun(tag + it, alles) }
        assertEquals(DailyChallenge.MAX_JOKERS, store.streakJokers)
        assertFalse(store.addStreakJoker())
    }

    @Test
    fun `ein Joker rettet die Serie ueber einen verpassten Tag`() {
        val store = store()
        store.submitDailyRun(tag, 5)
        store.submitDailyRun(tag + 1, 5)
        assertEquals(2, store.dailyStreak)
        store.addStreakJoker()

        // Tag +2 verpasst. Die Vorschau zeigt die Serie trotzdem als lebend.
        assertEquals(2, store.dailyStreakPreviewFor(tag + 3))
        store.submitDailyRun(tag + 3, 5)
        assertEquals(3, store.dailyStreak)
        assertEquals(1, store.lastDailyJokersUsed)
        assertEquals(0, store.streakJokers)
    }

    @Test
    fun `ohne Joker reisst die Serie wie bisher`() {
        val store = store()
        store.submitDailyRun(tag, 5)
        store.submitDailyRun(tag + 1, 5)
        assertEquals(0, store.dailyStreakPreviewFor(tag + 3))
        store.submitDailyRun(tag + 3, 5)
        assertEquals(1, store.dailyStreak)
        assertEquals(0, store.lastDailyJokersUsed)
    }

    @Test
    fun `das Rettungs-Angebot kommt einmal und laesst sich ausschlagen`() {
        val store = store()
        store.submitDailyRun(tag, 5)
        store.submitDailyRun(tag + 1, 5)
        assertTrue(store.streakRescuableFor(tag + 3))
        store.declineStreakRescue(tag + 3)
        assertFalse(store.streakRescuableFor(tag + 3))
    }

    @Test
    fun `ein Joker aus dem Spot beendet das Angebot`() {
        val store = store()
        store.submitDailyRun(tag, 5)
        store.submitDailyRun(tag + 1, 5)
        assertTrue(store.streakRescuableFor(tag + 3))
        store.addStreakJoker()
        assertFalse(store.streakRescuableFor(tag + 3))
        assertEquals(2, store.dailyStreakPreviewFor(tag + 3))
    }

    @Test
    fun `erledigte Aufgaben schalten die Aufgaben-Skins frei`() {
        val store = store()
        assertFalse(SkinPaint.isUnlocked(SkinId.STERNCHEN, store.stats()))
        repeat(4) { store.submitMissionRun(tag + it, alles) }
        assertEquals(12, store.missionsDone)
        assertTrue(SkinPaint.isUnlocked(SkinId.STERNCHEN, store.stats()))
        assertFalse(SkinPaint.isUnlocked(SkinId.ORDEN, store.stats()))
    }

    @Test
    fun `die Aufgaben-Zahl wandert zur Uhr und zurueck`() {
        val store = store()
        store.submitMissionRun(tag, alles)
        assertEquals(3, store.syncState().missionsDone)
        assertTrue(store.applySync(store.syncState().copy(missionsDone = 50)))
        assertEquals(50, store.missionsDone)
        // Kleiner wird die Zahl durch einen Abgleich nie.
        store.applySync(SyncState(missionsDone = 1))
        assertEquals(50, store.missionsDone)
    }

    @Test
    fun `Einwilligung hat drei Zustaende`() {
        val store = store()
        assertNull(store.analyticsConsent, "nie gefragt heisst: nichts senden")
        store.analyticsConsent = true
        assertEquals(true, store.analyticsConsent)
        store.analyticsConsent = false
        assertEquals(false, store.analyticsConsent)
        store.analyticsConsent = null
        assertNull(store.analyticsConsent)
    }
}
