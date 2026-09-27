package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tagesaufgaben (ab v2.30): deterministisch je Tag und Stufe, erreichbar
 * für die Stufe, und der Fortschritt läuft nie über das Ziel hinaus.
 */
class DailyMissionsTest {

    private val tag = 20_500L

    @Test
    fun `derselbe Tag und dieselbe Stufe geben dieselben Aufgaben`() {
        for (tier in 0 until DailyMissions.TIERS) {
            assertEquals(DailyMissions.forDay(tag, tier), DailyMissions.forDay(tag, tier))
        }
    }

    @Test
    fun `die Aufgaben wechseln ueber die Tage`() {
        val folge = (0 until 30).map { DailyMissions.forDay(tag + it, 2) }.toSet()
        assertTrue("nur ${folge.size} verschiedene Tage", folge.size > 5)
    }

    @Test
    fun `je Tag eine Aufgabe fuers Dabeisein, eine fuer Technik, eine fuers Koennen`() {
        val dabei = setOf(MissionType.RUNS, MissionType.POINTS_TODAY, MissionType.DAILY_RUN)
        val technik = setOf(MissionType.PERFECTS_TODAY, MissionType.PERFECT_STREAK, MissionType.BLIND_HITS)
        val koennen = setOf(MissionType.SCORE_IN_RUN, MissionType.DAILY_SCORE)
        for (d in 0 until 200) for (tier in 0 until DailyMissions.TIERS) {
            val m = DailyMissions.forDay(tag + d, tier)
            assertEquals(DailyMissions.COUNT, m.size)
            assertTrue(m[0].type in dabei)
            assertTrue(m[1].type in technik)
            assertTrue(m[2].type in koennen)
            m.forEach { assertTrue("Ziel muss positiv sein: $it", it.target >= 1) }
        }
    }

    @Test
    fun `Blindtreffer nur fuer Stammspieler`() {
        for (d in 0 until 400) for (tier in 0..2) {
            assertFalse(DailyMissions.forDay(tag + d, tier).any { it.type == MissionType.BLIND_HITS })
        }
    }

    @Test
    fun `ein Lauf-Ziel liegt unter der Rekordgrenze seiner Stufe`() {
        // Wer in Stufe t ist, hat mindestens diesen Rekord — ein Lauf-Ziel
        // darüber wäre eine Wand statt einer Aufgabe.
        val untergrenze = intArrayOf(0, 10, 25, 45)
        for (d in 0 until 200) for (tier in 0 until DailyMissions.TIERS) {
            DailyMissions.forDay(tag + d, tier)
                .filter { it.type == MissionType.SCORE_IN_RUN || it.type == MissionType.DAILY_SCORE }
                .forEach { m ->
                    val grenze = maxOf(untergrenze[tier], 9)
                    assertTrue("$m in Stufe $tier", m.target <= grenze)
                }
        }
    }

    @Test
    fun `Stufen folgen dem Rekord`() {
        assertEquals(0, DailyMissions.tierFor(0))
        assertEquals(0, DailyMissions.tierFor(9))
        assertEquals(1, DailyMissions.tierFor(10))
        assertEquals(1, DailyMissions.tierFor(24))
        assertEquals(2, DailyMissions.tierFor(25))
        assertEquals(3, DailyMissions.tierFor(45))
        assertEquals(3, DailyMissions.tierFor(999))
    }

    @Test
    fun `kumulative Aufgaben addieren, Lauf-Aufgaben nehmen den besten Lauf`() {
        val day = MissionDay(tag, 1, listOf(0, 0, 0))
        val fakten = RunFacts(score = 7, perfectHits = 2, maxPerfectStreak = 2, daily = false)
        val eins = DailyMissions.apply(day, fakten)
        val zwei = DailyMissions.apply(eins, fakten.copy(score = 4, maxPerfectStreak = 1))
        day.missions.forEachIndexed { i, m ->
            val beitrag1 = DailyMissions.contribution(m.type, fakten)
            val beitrag2 = DailyMissions.contribution(m.type, fakten.copy(score = 4, maxPerfectStreak = 1))
            val erwartet = if (m.type.cumulative) beitrag1 + beitrag2 else maxOf(beitrag1, beitrag2)
            assertEquals("$m", minOf(erwartet, m.target), zwei.progress[i])
        }
    }

    @Test
    fun `der Fortschritt laeuft nie ueber das Ziel`() {
        var day = DailyMissions.emptyDay(tag, 3)
        repeat(50) {
            day = DailyMissions.apply(
                day,
                RunFacts(score = 999, perfectHits = 99, maxPerfectStreak = 99, blindHits = 9, daily = true)
            )
        }
        day.missions.forEachIndexed { i, m -> assertEquals(m.target, day.progress[i]) }
        assertTrue(day.allDone)
        assertEquals(3, day.doneCount)
    }

    @Test
    fun `Tagesaufgaben zaehlen nur im Tageslauf`() {
        assertEquals(0, DailyMissions.contribution(MissionType.DAILY_RUN, RunFacts(score = 30)))
        assertEquals(0, DailyMissions.contribution(MissionType.DAILY_SCORE, RunFacts(score = 30)))
        assertEquals(1, DailyMissions.contribution(MissionType.DAILY_RUN, RunFacts(score = 0, daily = true)))
        assertEquals(30, DailyMissions.contribution(MissionType.DAILY_SCORE, RunFacts(score = 30, daily = true)))
    }

    @Test
    fun `die Aufgaben ziehen nicht dieselben Zahlen wie die Zonen der Daily`() {
        // Der Seed ist gesalzen: Sonst verrieten die Aufgaben ein Stück
        // des Tageslaufs.
        val daily = kotlin.random.Random(DailyChallenge.seedFor(tag)).nextInt()
        val aufgaben = kotlin.random.Random(DailyChallenge.seedFor(tag) + 0x5EED_1A5CL).nextInt()
        assertNotEquals(daily, aufgaben)
    }
}
