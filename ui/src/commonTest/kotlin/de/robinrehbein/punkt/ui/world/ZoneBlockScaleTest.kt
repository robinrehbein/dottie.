package de.robinrehbein.punkt.ui.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Die Zone wird zur Mitte hin größer ([zoneBlockScale]), Variante V3. */
class ZoneBlockScaleTest {

    @Test
    fun `in der Mitte voll, am Rand so gross wie ein Sandblock`() {
        assertEquals(1f, zoneBlockScale(0f), 1e-6f)
        // Sandblock: 3 von 5 Zellen.
        assertEquals(0.6f, zoneBlockScale(1f), 1e-6f)
    }

    @Test
    fun `nach aussen nie groesser und ausserhalb begrenzt`() {
        var last = zoneBlockScale(0f)
        for (i in 1..100) {
            val s = zoneBlockScale(i / 100f)
            assertTrue(s <= last, "wächst bei ${i / 100f}")
            last = s
        }
        assertEquals(0.6f, zoneBlockScale(2f), 1e-6f)
        assertEquals(1f, zoneBlockScale(-1f), 1e-6f)
    }

    @Test
    fun `die Blöcke bleiben lange fast voll gross`() {
        // Weich statt Keil: auf halbem Weg noch über 0,85.
        assertTrue(zoneBlockScale(0.5f) > 0.85f)
        // drawTrack misst bis einen halben Block (π/60) hinter den Rand.
        // Der äußerste Block der schmalsten Zone (MIN_ZONE_HALF 0,15)
        // bleibt damit deutlich über Sandgröße, der einer breiten (0,40)
        // knapp darüber.
        val halfSlot = kotlin.math.PI.toFloat() / 60f
        assertTrue(zoneBlockScale(0.15f / (0.15f + halfSlot)) > 0.75f)
        assertTrue(zoneBlockScale(0.40f / (0.40f + halfSlot)) > 0.65f)
    }
}
