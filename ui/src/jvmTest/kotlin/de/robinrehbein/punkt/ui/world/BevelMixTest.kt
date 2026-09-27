package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import de.robinrehbein.punkt.game.BevelPaint
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Zielbilder sind mit Compose `lerp` gerendert (Oklab). [BevelPaint.mix]
 * baut das in :core ohne Compose nach, damit Uhr und iOS dieselben Töne
 * bekommen. Hier wird der Nachbau gegen das Original gehalten: Weicht er
 * ab, treffen die Kanten die Zielbilder nicht mehr.
 */
class BevelMixTest {

    private fun compose(a: Long, b: Long, t: Float): Long =
        lerp(Color(a), Color(b), t).toArgb().toLong() and 0xFFFFFFFFL

    @Test
    fun `mix rechnet wie Compose lerp`() {
        val rnd = Random(77)
        val ts = floatArrayOf(0.2f, 0.22f, 0.28f, 0.3f, 0.35f, 0.45f, 0.55f, 0.6f)
        var worst = 0L
        var diffs = 0
        repeat(20_000) {
            val a = 0xFF000000L or rnd.nextLong(0x1000000L)
            val b = 0xFF000000L or rnd.nextLong(0x1000000L)
            val t = ts[it % ts.size]
            val want = compose(a, b, t)
            val got = BevelPaint.mix(a, b, t)
            if (want != got) {
                diffs++
                for (sh in intArrayOf(0, 8, 16)) {
                    worst = maxOf(worst, kotlin.math.abs(((want shr sh) and 0xFF) - ((got shr sh) and 0xFF)))
                }
            }
        }
        println("mix: $diffs Abweichungen, größte $worst")
        assertEquals(0L, worst, "$diffs Abweichungen")
    }
}
