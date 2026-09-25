package de.robinrehbein.punkt.ui.screens

import androidx.compose.ui.semantics.Role
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinStats
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.ui.data.CollectionSeen
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Sammlung (Plan 7.1, AP-15) headless bedient: Auswahl lässt sie
 * offen, Gesperrtes lässt sich ansehen, aber nicht wählen, Töne sind
 * auch gesperrt probehörbar, der Spot nie für Gönner, das X schließt.
 */
class CollectionOverlayTest {

    private val width = 720
    private val height = 1280
    private val density = 2f

    private var vorher: Locale = Locale.getDefault()

    @BeforeTest
    fun deutsch() {
        vorher = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @AfterTest
    fun zurueck() {
        Locale.setDefault(vorher)
    }

    /** Rekord 30: MINZE, LAVA, GOLD offen, SCHATTEN (Perfekt-Serie 4) zu. */
    private val stats = SkinStats(bestScore = 30, bestPerfectStreak = 0, bestDailyStreak = 0)

    private class Calls {
        val selected = mutableListOf<SkinId>()
        val scenes = mutableListOf<SceneId>()
        val sounds = mutableListOf<SoundSetId>()
        val previews = mutableListOf<SoundSetId>()
        val frames = mutableListOf<CardFrame>()
        val ads = mutableListOf<SkinId>()
        val seen = mutableListOf<String>()
        var closed = 0
    }

    private fun scene(
        calls: Calls,
        selected: SkinId = SkinId.KLASSIK,
        newKeys: Set<String> = emptySet(),
        adOfferReady: Boolean = true
    ) = ProbeScene(width, height, density) {
        CollectionOverlay(
            stats = stats,
            selected = selected,
            onSelect = { calls.selected += it },
            selectedScene = SceneId.WIESE,
            onSelectScene = { calls.scenes += it },
            selectedSound = SoundSetId.KLASSIK,
            onSelectSound = { calls.sounds += it },
            onPreviewSound = { calls.previews += it },
            selectedCardFrame = null,
            onSelectCardFrame = { calls.frames += it },
            onClose = { calls.closed++ },
            newKeys = newKeys,
            onSeen = { calls.seen += it },
            adOfferReady = adOfferReady,
            onWatchAdFor = { calls.ads += it }
        )
    }

    @Test
    fun eineAuswahlSchliesstDieSammlungNicht() {
        val calls = Calls()
        scene(calls).use { s ->
            s.step(0.2)
            s.tap("MINZE")
            assertEquals(listOf(SkinId.MINZE), calls.selected)
            assertEquals(0, calls.closed)
            s.tap("WELT")
            s.tap("WIESE")
            assertEquals(0, calls.closed)
        }
    }

    @Test
    fun gesperrtAnsehenJaWaehlenNeinSpotAlsKnopf() {
        val calls = Calls()
        scene(calls).use { s ->
            s.step(0.2)
            s.tap("SCHATTEN")
            assertTrue(calls.selected.isEmpty(), "gesperrter Skin wurde gewählt: ${calls.selected}")
            // Das Schaufenster zeigt ihn, darunter der Spot als Knopf.
            s.tap("HEUTE PER SPOT TESTEN")
            assertEquals(listOf(SkinId.SCHATTEN), calls.ads)
            assertEquals(0, calls.closed)
        }
    }

    @Test
    fun keinSpotFuerGoennerSkins() {
        val calls = Calls()
        // DIAMANT steht beim Öffnen im Schaufenster (gewählt ist er nicht
        // gedeckt, aber das Schaufenster zeigt die eigene Wahl).
        scene(calls, selected = SkinId.DIAMANT).use { s ->
            s.step(0.2)
            assertNull(s.find("HEUTE PER SPOT TESTEN"), "Spot-Angebot für einen Gönner-Skin: ${s.labels()}")
        }
    }

    @Test
    fun gesperrteToeneSindProbehoerbar() {
        val calls = Calls()
        scene(calls).use { s ->
            s.step(0.2)
            s.tap("TON")
            s.tap("GLOCKE")
            assertEquals(listOf(SoundSetId.GLOCKE), calls.previews)
            assertTrue(calls.sounds.isEmpty(), "gesperrtes Set wurde gewählt")
            // Offenes Set: Hörprobe und Wahl.
            s.tap("KLASSIK")
            assertEquals(SoundSetId.KLASSIK, calls.previews.last())
        }
    }

    @Test
    fun xSchliesst() {
        val calls = Calls()
        scene(calls).use { s ->
            s.step(0.2)
            s.tap("SCHLIESSEN")
            assertEquals(1, calls.closed)
        }
    }

    @Test
    fun neuIstMarkiertUndAngesehenWirdGemeldet() {
        val calls = Calls()
        val minze = CollectionSeen.key(SkinId.MINZE)
        scene(calls, newKeys = setOf(minze)).use { s ->
            s.step(0.2)
            assertTrue(s.labels().any { it == "MINZE, NEU" }, "${s.labels()}")
            s.tap("MINZE")
            assertEquals(listOf(minze), calls.seen)
        }
    }

    @Test
    fun alleKnoepfeHabenEineRolle() {
        scene(Calls()).use { s ->
            s.step(0.2)
            val roles = s.clickables()
            assertTrue(roles.size > 10, "$roles")
            roles.forEach { (label, role, _) ->
                assertTrue(role == Role.Button || role == Role.Tab, "„$label“ hat die Rolle $role")
            }
            assertEquals(4, roles.count { it.second == Role.Tab })
        }
    }
}
