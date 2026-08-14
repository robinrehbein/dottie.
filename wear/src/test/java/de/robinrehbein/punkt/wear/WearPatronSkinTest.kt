package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.SkinPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Gönner-Skins auf der Uhr: gesperrt ohne Kauf, offen mit Kauf — und
 * in beiden Fällen ohne Einfluss auf den Sammlungsstand. Gekauft ist kein
 * Verdienst.
 */
class WearPatronSkinTest {

    private val goenner = listOf(WearDotSkin.DIAMANT, WearDotSkin.PHOENIX, WearDotSkin.ONYX)

    /** Alles, was sich ohne Saison und ohne Kauf verdienen lässt. */
    private fun allesVerdient(patron: Boolean = false) = WearDotSkin.Stats(
        bestScore = 80,
        bestPerfectStreak = 15,
        bestDailyStreak = 21,
        runCount = 300,
        totalScore = 5_000,
        daysPlayed = 7,
        monthsPlayed = 3,
        seasonEarned = 0,
        patronOwned = patron
    )

    @Test
    fun `Ohne Kauf bleiben die Goenner-Skins gesperrt`() {
        val stats = allesVerdient()
        goenner.forEach { assertFalse("${it.name} darf gesperrt sein", it.isUnlocked(stats)) }
    }

    @Test
    fun `Mit Kauf sind die Goenner-Skins offen, auch ohne jede Leistung`() {
        val frisch = WearDotSkin.Stats(
            bestScore = 0, bestPerfectStreak = 0, bestDailyStreak = 0, patronOwned = true
        )
        goenner.forEach { assertTrue("${it.name} muss offen sein", it.isUnlocked(frisch)) }
    }

    @Test
    fun `Der Sammlungsstand zaehlt den Kauf nicht mit`() {
        val ohne = SkinPaint.unlockedCount(allesVerdient(patron = false).toSkinStats())
        val mit = SkinPaint.unlockedCount(allesVerdient(patron = true).toSkinStats())
        assertEquals(ohne, mit)
    }

    /**
     * Der Regenbogen schliesst die Sammlung ab — er darf nie am Kauf
     * haengen, sonst waere die Sammlung nur mit Geld vollstaendig.
     */
    @Test
    fun `Der Regenbogen kommt ohne Kauf und ohne Saison`() {
        assertTrue(WearDotSkin.REGENBOGEN.isUnlocked(allesVerdient()))
    }

    @Test
    fun `Ein unbekannter Skin-Name faellt auf Klassik zurueck`() {
        assertEquals(WearDotSkin.KLASSIK, WearDotSkin.fromName(null))
        assertEquals(WearDotSkin.KLASSIK, WearDotSkin.fromName("GIBTSNICHT"))
        assertEquals(WearDotSkin.ONYX, WearDotSkin.fromName("ONYX"))
    }
}
