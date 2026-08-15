package de.robinrehbein.punkt.game

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der einzige Test, der Quelltext liest statt Werte zu vergleichen — und
 * er tut es aus einem Grund, den kein Wertevergleich abdecken kann.
 *
 * Zwei Fairness-Fehler sind daran gestorben, dass Renderer selbst
 * gerechnet haben, statt die Engine zu fragen: Der PERFEKT-Kern wurde
 * anders gezeichnet als gewertet (unter PULS bis zu 61 % zu breit), und
 * die Falle wurde mit der Grundbreite gezeichnet, während die Zone
 * atmete — womit die Falle sich selbst verriet. Beides ist behoben,
 * indem [TimingGame.perfectHalf] und [TimingGame.fakeZoneHalf] die
 * einzige Quelle wurden.
 *
 * Ein Rückfall wäre am Ergebnis unsichtbar: Wer `PERFECT_SHARE` wieder
 * selbst multipliziert, bekommt in den meisten Zuständen dieselbe Zahl
 * heraus. Erst unter PULS, erst bei schmaler Zone, erst im Wellental
 * laufen die Werte auseinander — genau dort, wo niemand hinsieht.
 * Deshalb prüft dieser Test nicht das Ergebnis, sondern die Bauweise.
 *
 * Er stand bis v2.22 in den Web-Tests. Mit der Konzentration auf die
 * nativen Apps ist `web/` entfallen, und der Wächter wäre mit ihm
 * verschwunden — er gehört ohnehin hierher, denn die Regel gehört der
 * Engine und nicht einem Port.
 */
class RendererSourceTest {

    /**
     * Die Renderer, die das Ringband zeichnen.
     *
     * Seit v2.24 sind es zwei: `:ui` zeichnet fuer Telefon und iPhone,
     * `:wear` hat wegen der runden Anzeige weiterhin einen eigenen. Der
     * SpriteKit-Renderer stand hier bis dahin als dritter — er ist mit
     * dem Umstieg auf Compose Multiplatform entfallen.
     */
    private val renderer = listOf(
        "ui/src/commonMain/kotlin/de/robinrehbein/punkt/ui/world/WorldRenderer.kt",
        "wear/src/main/java/de/robinrehbein/punkt/wear/WearRenderer.kt"
    )

    /**
     * Die Wurzel des Projekts, von der Modulmappe aus gesucht. Ein fester
     * Pfad `..` würde brechen, sobald jemand die Tests aus einem anderen
     * Verzeichnis startet.
     */
    private fun wurzel(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError("Projektwurzel nicht gefunden (settings.gradle.kts)")
    }

    @Test
    fun `kein Renderer rechnet Kern oder Fallenbreite selbst aus`() {
        val wurzel = wurzel()
        var geprueft = 0

        renderer.forEach { pfad ->
            val datei = File(wurzel, pfad)
            assertTrue("$pfad nicht gefunden — wurde der Renderer verschoben?", datei.isFile)
            val quelle = datei.readText()

            assertTrue(
                "$pfad muss den PERFEKT-Kern aus der Engine ziehen (perfectHalf)",
                quelle.contains("perfectHalf(")
            )
            assertTrue(
                "$pfad muss die Fallenbreite aus der Engine ziehen (fakeZoneHalf)",
                quelle.contains("fakeZoneHalf(")
            )
            // Die eigentliche Falle: Wer die Anteilskonstante im Renderer
            // stehen hat, rechnet wieder selbst — auch wenn daneben noch
            // der Aufruf steht.
            assertTrue(
                "$pfad darf den Kern nicht selbst aus PERFECT_SHARE rechnen",
                !quelle.contains("PERFECT_SHARE") && !quelle.contains("perfectShare")
            )
            geprueft++
        }

        assertTrue("Es wurde kein Renderer geprüft", geprueft == renderer.size)
    }
}
