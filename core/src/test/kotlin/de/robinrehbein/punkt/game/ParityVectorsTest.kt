package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hält `parity/golden-vectors.txt` an der Kotlin-Engine fest.
 *
 * Schlägt dieser Test fehl, hat sich das Verhalten von :core geändert.
 * Das ist erlaubt — dann aber bewusst:
 *
 *     ./gradlew :core:test -Dparity.update=true
 *
 * schreibt die Datei neu. Der Diff zeigt anschließend genau, was sich für
 * die Ports in ios/ und web/ ändert; sie müssen nachgezogen werden, sonst
 * schlagen dort dieselben Vektoren fehl.
 */
class ParityVectorsTest {

    @Test
    fun goldenVectorsAreUpToDate() {
        val generated = ParityVectors.build()
        val file = vectorFile()

        if (System.getProperty("parity.update") == "true") {
            file.parentFile.mkdirs()
            file.writeText(generated)
            println("Paritäts-Vektoren neu geschrieben: ${file.absolutePath}")
            return
        }

        assertTrue(
            "Datei fehlt: ${file.absolutePath} — mit " +
                "./gradlew :core:test -Dparity.update=true erzeugen",
            file.exists()
        )
        assertEquals(
            "Die Kotlin-Engine liefert andere Werte als parity/golden-vectors.txt. " +
                "Wenn das gewollt ist: ./gradlew :core:test -Dparity.update=true " +
                "und den Port in ios/ nachziehen.",
            file.readText(),
            generated
        )
    }

    @Test
    fun botPlaysWithoutDying() {
        // Die Traces sind nur aussagekräftig, wenn der Bot wirklich bis
        // zum Ende durchspielt — sonst vergleichen die Ports einen
        // Zufalls-Abbruch miteinander.
        val trace = ParityBot.playPerfect(20240813L, maxHits = 40)
        assertEquals(40, trace.size)
        assertTrue("Score wächst über die Perfekt-Boni", trace.last().score >= 40 * 2)
    }

    /**
     * Die Datei liegt im Repo-Wurzelverzeichnis unter `parity/`. Gradle
     * startet Tests im Projektverzeichnis (`core/`), deshalb der Schritt
     * nach oben — mit Rückfall, falls jemand aus der Wurzel startet.
     */
    private fun vectorFile(): File {
        val fromModule = File("../parity/golden-vectors.txt")
        return if (fromModule.parentFile.isDirectory || !File("parity").isDirectory) {
            fromModule
        } else {
            File("parity/golden-vectors.txt")
        }
    }
}
