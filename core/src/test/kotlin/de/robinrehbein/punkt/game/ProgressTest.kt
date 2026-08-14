package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Ziel-Liste ist die einzige Stelle, an der die Schwellen ein zweites
 * Mal stehen — deshalb prüft dieser Test vor allem, dass sie mit
 * [SkinPaint.isUnlocked] und [ScenePaint.isUnlocked] übereinstimmen. Ein
 * Balken, der bei 100 % noch nichts freischaltet, wäre schlimmer als gar
 * kein Balken.
 */
class ProgressTest {

    private val leer = SkinStats(bestScore = 0, bestPerfectStreak = 0, bestDailyStreak = 0)

    private val maxStats = SkinStats(
        bestScore = 999,
        bestPerfectStreak = 99,
        bestDailyStreak = 99,
        runCount = 9_999,
        totalScore = 999_999,
        daysPlayed = 365,
        monthsPlayed = 12,
        seasonEarned = 0b1111,
        patronOwned = true
    )

    /** Der Stand auf der Achse eines Ziels, ohne Umweg über die Anzeige. */
    private fun statsWith(axis: GoalAxis, value: Int): SkinStats = when (axis) {
        GoalAxis.BEST_SCORE -> leer.copy(bestScore = value)
        GoalAxis.PERFECT_STREAK -> leer.copy(bestPerfectStreak = value)
        GoalAxis.DAILY_STREAK -> leer.copy(bestDailyStreak = value)
        GoalAxis.RUN_COUNT -> leer.copy(runCount = value)
        GoalAxis.TOTAL_SCORE -> leer.copy(totalScore = value)
        GoalAxis.DAYS_PLAYED -> leer.copy(daysPlayed = value)
        GoalAxis.MONTHS_PLAYED -> leer.copy(monthsPlayed = value)
        else -> leer
    }

    @Test
    fun `frischer Stand kennt Ziele und keines ist erfuellt`() {
        val goals = Progress.goals(leer)
        assertTrue("ohne Fortschritt gibt es etwas zu holen", goals.size > 10)
        goals.forEach { goal ->
            assertTrue("$goal darf nicht schon voll sein", goal.current < goal.target)
            assertTrue("$goal braucht einen Anteil in 0..1", goal.fraction in 0f..1f)
            assertTrue("$goal muss noch etwas übrig haben", goal.remaining > 0)
        }
    }

    @Test
    fun `alles verdient heisst keine Ziele mehr`() {
        assertTrue(Progress.goals(maxStats, month = 10, seasonDays = 5).isEmpty())
        assertNull(Progress.nextGoal(maxStats, month = 10, seasonDays = 5))
    }

    @Test
    fun `Goenner-Skins tauchen nie als Ziel auf`() {
        val ohneKauf = maxStats.copy(patronOwned = false, seasonEarned = 0)
        listOf(leer, ohneKauf).forEach { stats ->
            for (month in 0..12) {
                Progress.goals(stats, month = month, seasonDays = 3).forEach { goal ->
                    assertFalse(
                        "gekaufte Skins sind kein Ziel: $goal",
                        goal.skin != null && SkinPaint.isPatron(goal.skin)
                    )
                }
            }
        }
    }

    @Test
    fun `Saison-Ziele erscheinen nur im eigenen Monat`() {
        Season.entries.forEach { season ->
            val drin = Progress.goals(leer, month = season.month, seasonDays = 1)
                .filter { it.skin == season.skin }
            assertEquals("$season gehört in seinen Monat", 1, drin.size)
            assertEquals(GoalAxis.SEASON_DAYS, drin.first().axis)
            assertEquals(season.requiredDays, drin.first().target)

            // Ein Monat ohne eigenen Saison-Skin (März) zeigt keinen.
            val draussen = Progress.goals(leer, month = 3, seasonDays = 4)
                .filter { it.skin == season.skin }
            assertTrue("$season darf im März nicht auftauchen", draussen.isEmpty())
        }
        // Ohne Kalender (0) bleiben alle Saison-Ziele weg.
        assertTrue(
            Progress.goals(leer, month = 0, seasonDays = 4)
                .none { it.axis == GoalAxis.SEASON_DAYS }
        )
    }

    @Test
    fun `verdienter Saison-Skin ist kein Ziel mehr`() {
        val kuerbis = Season.KUERBIS
        val verdient = leer.copy(seasonEarned = kuerbis.bit)
        assertTrue(
            Progress.goals(verdient, month = kuerbis.month, seasonDays = 5)
                .none { it.skin == kuerbis.skin }
        )
    }

    @Test
    fun `jedes Ziel faellt genau an seiner Schwelle`() {
        var geprueft = 0
        Progress.goals(leer).forEach { goal ->
            if (goal.axis == GoalAxis.SKIN_COLLECTION ||
                goal.axis == GoalAxis.SCENE_COLLECTION ||
                goal.axis == GoalAxis.SEASON_DAYS
            ) {
                return@forEach
            }
            val davor = statsWith(goal.axis, goal.target - 1)
            val genau = statsWith(goal.axis, goal.target)
            val skin = goal.skin
            val scene = goal.scene
            val sound = goal.sound
            if (skin != null) {
                assertFalse("$skin darf bei ${goal.target - 1} noch zu sein",
                    SkinPaint.isUnlocked(skin, davor))
                assertTrue("$skin muss bei ${goal.target} offen sein",
                    SkinPaint.isUnlocked(skin, genau))
            } else if (scene != null) {
                assertFalse("$scene darf bei ${goal.target - 1} noch zu sein",
                    ScenePaint.isUnlocked(scene, davor))
                assertTrue("$scene muss bei ${goal.target} offen sein",
                    ScenePaint.isUnlocked(scene, genau))
            } else if (sound != null) {
                assertFalse("$sound darf bei ${goal.target - 1} noch zu sein",
                    SoundBank.isUnlocked(sound, davor))
                assertTrue("$sound muss bei ${goal.target} offen sein",
                    SoundBank.isUnlocked(sound, genau))
            }
            geprueft++
        }
        assertEquals("alle Zahlen-Ziele geprüft", 39, geprueft)
    }

    @Test
    fun `kein Skin, keine Kulisse und kein Ton-Set faellt aus der Tabelle`() {
        val offen = Progress.goals(leer, month = 10, seasonDays = 0)
        SkinId.entries.forEach { id ->
            val erwartet = !SkinPaint.isPatron(id) &&
                !SkinPaint.isUnlocked(id, leer) &&
                // Drei Saison-Skins liegen außerhalb des Oktobers.
                (Season.forSkin(id)?.month ?: 10) == 10
            assertEquals(
                "$id gehört ${if (erwartet) "" else "nicht "}in die Ziel-Liste",
                erwartet,
                offen.any { it.skin == id }
            )
        }
        SceneId.entries.forEach { id ->
            assertEquals(
                "$id gehört in die Ziel-Liste",
                !ScenePaint.isUnlocked(id, leer),
                offen.any { it.scene == id }
            )
        }
        SoundSetId.entries.forEach { id ->
            assertEquals(
                "$id gehört in die Ziel-Liste",
                !SoundBank.isUnlocked(id, leer),
                offen.any { it.sound == id }
            )
        }
    }

    @Test
    fun `ein Ton-Set faellt an seiner Schwelle und keinen Punkt frueher`() {
        // Das Muster aller Ziele, hier für die neue Sammlung: bei
        // target - 1 steht es noch, bei target ist es offen — und die
        // Ziel-Liste sagt genau das auch.
        listOf(
            Triple(SoundSetId.GLOCKE, GoalAxis.PERFECT_STREAK, 20),
            Triple(SoundSetId.AMBOSS, GoalAxis.TOTAL_SCORE, 25_000)
        ).forEach { (id, axis, target) ->
            val ziel = Progress.goals(leer).first { it.sound == id }
            assertEquals(axis, ziel.axis)
            assertEquals(target, ziel.target)
            assertEquals(0, ziel.current)

            assertFalse(SoundBank.isUnlocked(id, statsWith(axis, target - 1)))
            assertTrue(SoundBank.isUnlocked(id, statsWith(axis, target)))
            assertTrue(
                "$id bleibt bei ${target - 1} ein Ziel",
                Progress.goals(statsWith(axis, target - 1)).any { it.sound == id }
            )
            assertTrue(
                "$id ist bei $target kein Ziel mehr",
                Progress.goals(statsWith(axis, target)).none { it.sound == id }
            )
        }
    }

    @Test
    fun `die Liste steht nach Naehe zum Ziel`() {
        val stats = leer.copy(
            bestScore = 24,   // 24/25 zur MELONE — das nächste Ziel
            runCount = 30,
            totalScore = 400,
            daysPlayed = 2
        )
        val goals = Progress.goals(stats)
        assertEquals(SkinId.MELONE, goals.first().skin)
        goals.zipWithNext().forEach { (a, b) ->
            assertTrue("$a muss vor $b stehen", a.fraction >= b.fraction)
        }
    }

    @Test
    fun `die Ausdauer-Achse traegt auch den ewigen Rekord 25`() {
        // Genau der Fall aus dem README: Rekord 25, aber fleißig — wer am
        // Können hängenbleibt, kommt über die Menge trotzdem voran, und
        // die Ausdauer-Achse darf dann auch vorne stehen.
        val stats = leer.copy(bestScore = 25, runCount = 280, totalScore = 3_400)
        val naechstes = Progress.nextGoal(stats)
        assertNotNull(naechstes)
        assertEquals(SkinId.FUSSBALL, naechstes!!.skin)
        assertEquals(GoalAxis.RUN_COUNT, naechstes.axis)
        assertEquals(280, naechstes.current)
        assertEquals(300, naechstes.target)
        assertEquals(20, naechstes.remaining)
    }

    @Test
    fun `Sammlungs-Ziele zaehlen sich selbst nicht mit`() {
        // Fast alles verdient: REGENBOGEN und WELTRAUM stehen dann als
        // Abschluss der beiden Sammlungen da — und zählen sich dabei
        // selbst nicht mit, sonst wäre die Bedingung zirkulär.
        val fast = maxStats.copy(bestScore = 79, patronOwned = false, seasonEarned = 0)
        val regenbogen = Progress.goals(fast).first { it.skin == SkinId.REGENBOGEN }
        assertEquals(GoalAxis.SKIN_COLLECTION, regenbogen.axis)
        assertEquals(SkinPaint.collectableCount() - 1, regenbogen.target)
        assertEquals(SkinPaint.unlockedCount(fast), regenbogen.current)

        val weltraum = Progress.goals(fast).first { it.scene == SceneId.WELTRAUM }
        assertEquals(GoalAxis.SCENE_COLLECTION, weltraum.axis)
        assertEquals(SceneId.entries.size - 1, weltraum.target)
    }

    @Test
    fun `nextGoals kuerzt auf die Seitenlaenge`() {
        assertEquals(Progress.PAGE_GOALS, Progress.nextGoals(leer).size)
        assertEquals(1, Progress.nextGoals(leer, limit = 1).size)
        assertEquals(Progress.goals(leer).take(2), Progress.nextGoals(leer, limit = 2))
    }
}
