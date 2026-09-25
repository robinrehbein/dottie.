package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Kacheln der Sammlung (Plan 7.1, AP-15): Jede Kachel ist genau an
 * ihrer Schwelle voll — bei `target - 1` steht der Balken noch, bei
 * `target` ist er voll und die Kachel offen. Ein voller Balken an einer
 * gesperrten Kachel wäre dieselbe Lüge wie in [ProgressTest].
 */
class CollectionProgressTest {

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

    private fun statsWith(axis: GoalAxis, value: Int): SkinStats = when (axis) {
        GoalAxis.BEST_SCORE -> leer.copy(bestScore = value)
        GoalAxis.PERFECT_STREAK -> leer.copy(bestPerfectStreak = value)
        GoalAxis.DAILY_STREAK -> leer.copy(bestDailyStreak = value)
        GoalAxis.RUN_COUNT -> leer.copy(runCount = value)
        GoalAxis.TOTAL_SCORE -> leer.copy(totalScore = value)
        GoalAxis.DAYS_PLAYED -> leer.copy(daysPlayed = value)
        GoalAxis.MONTHS_PLAYED -> leer.copy(monthsPlayed = value)
        else -> error("keine Tabellen-Achse: $axis")
    }

    private fun assertJustBelow(what: String, p: CollectionItemProgress) {
        assertFalse("$what ist bei target - 1 schon offen", p.unlocked)
        assertTrue("$what ist bei target - 1 schon voll: $p", p.fraction < 1f)
        assertEquals("$what steht nicht bei target - 1", p.target - 1, p.current)
    }

    private fun assertFullAndOpen(what: String, p: CollectionItemProgress) {
        assertTrue("$what ist an der Schwelle nicht offen", p.unlocked)
        assertEquals("$what ist an der Schwelle nicht voll: $p", 1f, p.fraction)
        assertEquals(p.target, p.current)
    }

    @Test
    fun `jeder Skin mit Schwelle ist genau an ihr voll`() {
        Progress.SKIN_THRESHOLDS.forEach { (id, axis, target) ->
            assertJustBelow(id.name, CollectionProgress.skin(id, statsWith(axis, target - 1), 0, 0))
            assertFullAndOpen(id.name, CollectionProgress.skin(id, statsWith(axis, target), 0, 0))
        }
    }

    @Test
    fun `jede Welt und jedes Ton-Set mit Schwelle ist genau an ihr voll`() {
        Progress.SCENE_THRESHOLDS.forEach { (id, axis, target) ->
            assertJustBelow(id.name, CollectionProgress.scene(id, statsWith(axis, target - 1)))
            assertFullAndOpen(id.name, CollectionProgress.scene(id, statsWith(axis, target)))
        }
        Progress.SOUND_THRESHOLDS.forEach { (id, axis, target) ->
            assertJustBelow(id.name, CollectionProgress.sound(id, statsWith(axis, target - 1)))
            assertFullAndOpen(id.name, CollectionProgress.sound(id, statsWith(axis, target)))
        }
    }

    @Test
    fun `der Anfangsbestand ist offen und voll`() {
        assertFullAndOpen("KLASSIK", CollectionProgress.skin(SkinId.KLASSIK, leer, 0, 0))
        assertFullAndOpen("WIESE", CollectionProgress.scene(SceneId.WIESE, leer))
        assertFullAndOpen("KLASSIK-Set", CollectionProgress.sound(SoundSetId.KLASSIK, leer))
        assertFullAndOpen("SCHLICHT", CollectionProgress.frame(CardFrame.SCHLICHT, leer))
        assertEquals(CollectionAxis.NONE, CollectionProgress.skin(SkinId.KLASSIK, leer, 0, 0).axis)
    }

    @Test
    fun `WELTRAUM ist voll, sobald alle anderen Welten im Besitz sind`() {
        val andere = SceneId.entries.filter { it != SceneId.WELTRAUM }.map { it.name }
        val fastAlle = leer.copy(ownedScenes = andere.dropLast(1).toSet())
        assertJustBelow("WELTRAUM", CollectionProgress.scene(SceneId.WELTRAUM, fastAlle))
        val alle = leer.copy(ownedScenes = andere.toSet())
        assertFullAndOpen("WELTRAUM", CollectionProgress.scene(SceneId.WELTRAUM, alle))
        assertEquals(CollectionAxis.SCENE_COLLECTION, CollectionProgress.scene(SceneId.WELTRAUM, alle).axis)
    }

    @Test
    fun `eine Welt aus dem Bestand steht voll, auch unter ihrer Schwelle`() {
        // Rekord 90 mit der STADT aus der Besitz-Menge (Bestandsschutz).
        val bestand = leer.copy(bestScore = 90, ownedScenes = setOf("WIESE", "STADT"))
        assertFullAndOpen("STADT", CollectionProgress.scene(SceneId.STADT, bestand))
    }

    @Test
    fun `REGENBOGEN ist voll, wenn alle anderen zaehlbaren Skins offen sind`() {
        // Rekord 79: HOLO (80) fehlt, alles andere ist offen.
        val ohneHolo = maxStats.copy(bestScore = 79)
        assertJustBelow("REGENBOGEN", CollectionProgress.skin(SkinId.REGENBOGEN, ohneHolo, 0, 0))
        assertFullAndOpen("REGENBOGEN", CollectionProgress.skin(SkinId.REGENBOGEN, maxStats, 0, 0))
    }

    @Test
    fun `Saison-Skins zaehlen nur im eigenen Monat`() {
        Season.entries.forEach { season ->
            val knapp = CollectionProgress.skin(season.skin, leer, season.month, season.requiredDays - 1)
            assertEquals(CollectionAxis.SEASON_DAYS, knapp.axis)
            assertTrue(knapp.fraction < 1f)
            val voll = CollectionProgress.skin(season.skin, leer, season.month, season.requiredDays)
            assertEquals(1f, voll.fraction)
            // Das Bit schreibt der Speicher an genau diesem Tag (GameStore).
            val verdient = leer.copy(seasonEarned = season.bit)
            assertFullAndOpen(season.name, CollectionProgress.skin(season.skin, verdient, 0, 0))
            // Anderer Monat: Das Fenster ist zu, der Balken steht bei null.
            val anderer = if (season.month == 1) 2 else 1
            assertEquals(0, CollectionProgress.skin(season.skin, leer, anderer, 4).current)
        }
    }

    @Test
    fun `Goenner-Skins haengen am Kauf`() {
        listOf(SkinId.DIAMANT, SkinId.PHOENIX, SkinId.ONYX).forEach { id ->
            val offen = CollectionProgress.skin(id, leer, 0, 0)
            assertEquals(CollectionAxis.PURCHASE, offen.axis)
            assertFalse(offen.unlocked)
            assertEquals(0f, offen.fraction)
            assertFullAndOpen(id.name, CollectionProgress.skin(id, leer.copy(patronOwned = true), 0, 0))
        }
    }

    /** Genau neun, dann genau zehn gesammelte Skins. */
    @Test
    fun `DOPPELLINIE ist genau bei zehn Skins voll`() {
        // Rekord 30: MINZE, LAVA, GOLD, MELONE, CHAMAELEON; 25 Läufe:
        // MATCHA, EI; 100 Punkte: TOFFIFEE; dazu KLASSIK = 9.
        val neun = leer.copy(bestScore = 30, runCount = 25, totalScore = 100)
        assertEquals(9, SkinPaint.unlockedCount(neun))
        assertJustBelow("DOPPELLINIE", FrameProgress.of(CardFrame.DOPPELLINIE, neun))
        // 7 Tage: TAGESZEIT = 10.
        val zehn = neun.copy(daysPlayed = 7)
        assertEquals(10, SkinPaint.unlockedCount(zehn))
        assertFullAndOpen("DOPPELLINIE", FrameProgress.of(CardFrame.DOPPELLINIE, zehn))
    }

    /**
     * Für jede Stufe und viele Spielstände: Der Balken ist genau dann
     * voll, wenn die eigene Bedingung erfüllt ist, und offen ist die
     * Stufe genau dann, wenn CardStyle sie für verdient hält.
     */
    @Test
    fun `jede Rahmenstufe ist genau an ihrer Bedingung voll`() {
        val staende = (0..120).map { t ->
            SkinStats(
                bestScore = t,
                bestPerfectStreak = t / 5,
                bestDailyStreak = t / 4,
                runCount = t * 3,
                totalScore = t * 250,
                daysPlayed = t / 10,
                monthsPlayed = t / 30,
                ownedScenes = if (t % 7 == 0) SceneId.entries.map { it.name }.toSet() else emptySet()
            )
        }
        val targets = mapOf(
            CardFrame.DOPPELLINIE to 10,
            CardFrame.ZINNEN to 20,
            CardFrame.PRACHT to 33,
            CardFrame.KASKADE to 6,
            CardFrame.PERLENKRANZ to 3,
            CardFrame.KRONE to SkinPaint.collectableCount()
        )
        staende.forEach { s ->
            CardFrame.entries.forEach { frame ->
                val p = FrameProgress.of(frame, s)
                assertEquals("$frame offen bei $s", CardStyle.isUnlocked(frame, s), p.unlocked)
                if (frame == CardFrame.SCHLICHT) return@forEach
                assertEquals("$frame Ziel", targets.getValue(frame), p.target)
                assertEquals(
                    "$frame voll genau bei erfüllter Bedingung ($s)",
                    CardStyle.earns(frame, s),
                    p.fraction == 1f
                )
            }
        }
        // Die Achsen: 10/20/33 Skins, x/6 Welten, x/3 Töne, KRONE über alle Skins.
        assertEquals(CollectionAxis.SKIN_COLLECTION, FrameProgress.of(CardFrame.PRACHT, leer).axis)
        assertEquals(CollectionAxis.SCENE_COLLECTION, FrameProgress.of(CardFrame.KASKADE, leer).axis)
        assertEquals(CollectionAxis.SOUND_COLLECTION, FrameProgress.of(CardFrame.PERLENKRANZ, leer).axis)
        assertEquals(CollectionAxis.SKIN_COLLECTION, FrameProgress.of(CardFrame.KRONE, leer).axis)
    }

    @Test
    fun `KASKADE ist mit allen Welten voll, aber ohne PRACHT nur vorgemerkt`() {
        val alleWelten = leer.copy(ownedScenes = SceneId.entries.map { it.name }.toSet())
        val p = FrameProgress.of(CardFrame.KASKADE, alleWelten)
        assertEquals(1f, p.fraction)
        assertFalse(p.unlocked)
    }

    @Test
    fun `die Zaehler der Reiter`() {
        assertEquals(46, CollectionProgress.SKIN_TOTAL)
        assertEquals(6, CollectionProgress.SCENE_TOTAL)
        assertEquals(3, CollectionProgress.SOUND_TOTAL)
        assertEquals(7, CollectionProgress.FRAME_TOTAL)
        assertEquals(1, CollectionProgress.skinCount(leer))
        assertEquals(1, CollectionProgress.sceneCount(leer))
        assertEquals(1, CollectionProgress.soundCount(leer))
        assertEquals(1, CollectionProgress.frameCount(leer))
        assertEquals(46, CollectionProgress.skinCount(maxStats))
        assertEquals(6, CollectionProgress.sceneCount(maxStats))
        assertEquals(3, CollectionProgress.soundCount(maxStats))
        assertEquals(7, CollectionProgress.frameCount(maxStats))
    }

    @Test
    fun `kein Balken laeuft ueber`() {
        listOf(leer, maxStats, maxStats.copy(patronOwned = false)).forEach { s ->
            SkinId.entries.forEach { id ->
                val p = CollectionProgress.skin(id, s, 10, 99)
                assertTrue("$id $p", p.current in 0..p.target)
            }
            SceneId.entries.forEach { assertTrue(CollectionProgress.scene(it, s).current <= CollectionProgress.scene(it, s).target) }
            SoundSetId.entries.forEach { assertTrue(CollectionProgress.sound(it, s).current <= CollectionProgress.sound(it, s).target) }
        }
    }
}
