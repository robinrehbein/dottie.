package de.robinrehbein.punkt.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SkinStats
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.ui.data.FakeKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.platform.GameFeedback
import de.robinrehbein.punkt.ui.platform.GameSounds
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Surface

/**
 * Bedienung nach Plan 7.1/7.2 (AP-14), headless wie im ScreenshotRenderer:
 * die gesperrte Game-Over-Leiste, MENÜ danach, der Haptik-Tick über
 * GameScreen und die Rollen aller Knöpfe für Vorleser.
 */
class BedienungTest {

    private val width = 720
    private val height = 1280
    private val density = 2f

    private class NoSounds : GameSounds {
        override var muted = false
        override var soundSet = SoundSetId.KLASSIK
        override fun start() {}
        override fun hit(score: Int) {}
        override fun perfect(streak: Int) {}
        override fun chain() {}
        override fun unlock() {}
        override fun death() {}
        override fun thud() {}
        override fun newRecord() {}
        override fun preview(set: SoundSetId) {}
        override fun release() {}
    }

    private class CountingFeedback : GameFeedback {
        var ticks = 0
        override fun score() {}
        override fun perfect() {}
        override fun unlock() {}
        override fun death() {}
        override fun thud() {}
        override fun newRecord() {}
        override fun tap() {
            ticks++
        }
    }

    private var now = 0L

    private fun ImageComposeScene.step(seconds: Double) {
        val until = now + (seconds * 1_000_000_000L).toLong()
        while (now < until) {
            now += 16_000_000L
            render(now)
        }
    }

    private fun ImageComposeScene.tap(x: Float, y: Float) {
        sendPointerEvent(PointerEventType.Press, Offset(x, y))
        render(now)
        sendPointerEvent(PointerEventType.Release, Offset(x, y))
        render(now)
    }

    /** Mitte der Leiste unten, linkes Feld (MENÜ). */
    private val barX get() = width / 4f
    private val barY get() = height - 32 * density

    /**
     * GameScreen mit geseedetem Spiel bis ins Game-Over bringen: starten,
     * die Zone überfahren lassen, Freeze und Sturz abwarten. Danach steht
     * `game.elapsed` bei 0 (plus höchstens ein Frame).
     */
    private fun gameOverScene(
        game: TimingGame,
        feedback: GameFeedback = CountingFeedback()
    ): ImageComposeScene {
        now = 0L
        val scene = ImageComposeScene(width, height, Density(density)) {
            GameScreen(
                store = GameStore(FakeKeyValueStore()),
                sounds = NoSounds(),
                feedback = feedback,
                game = game,
                runSeed = SEED
            )
        }
        scene.step(0.5)
        game.start()
        var frames = 0
        while (game.phase != GamePhase.OVER && frames++ < 1_000) scene.step(0.016)
        assertEquals(GamePhase.OVER, game.phase, "kein Game-Over erreicht")
        return scene
    }

    /** Bis `game.elapsed` in OVER bei [seconds] steht. */
    private fun ImageComposeScene.untilOverElapsed(game: TimingGame, seconds: Float) {
        while (game.phase == GamePhase.OVER && game.elapsed < seconds) step(0.016)
    }

    @Test
    fun gesperrteLeisteSchlucktTapsBis08Sekunden() {
        val game = TimingGame(Random(SEED))
        gameOverScene(game).use { scene ->
            // 0,3 s: Neustart und Leiste gesperrt.
            scene.untilOverElapsed(game, 0.3f)
            scene.tap(barX, barY)
            scene.step(0.05)
            assertEquals(GamePhase.OVER, game.phase, "Tap auf die Leiste bei 0,3 s")

            // 0,65 s: Der Neustart wäre schon frei. Ein Tap auf die noch
            // gesperrte Leiste darf trotzdem weder neu starten noch ins Menü.
            scene.untilOverElapsed(game, 0.65f)
            scene.tap(barX, barY)
            scene.step(0.05)
            assertEquals(GamePhase.OVER, game.phase, "Tap auf die Leiste bei 0,65 s")

            // 1,0 s: MENÜ öffnet.
            scene.untilOverElapsed(game, 1.0f)
            scene.tap(barX, barY)
            scene.step(0.1)
            assertEquals(GamePhase.READY, game.phase, "MENÜ bei 1,0 s")
        }
    }

    @Test
    fun ueberDerLeisteHeisstNochmal() {
        // Gegenprobe: Derselbe Zeitpunkt, aber über der Leiste, startet neu.
        val game = TimingGame(Random(SEED))
        gameOverScene(game).use { scene ->
            scene.untilOverElapsed(game, 0.65f)
            scene.tap(width / 2f, height * 0.6f)
            scene.step(0.05)
            assertEquals(GamePhase.RUNNING, game.phase)
        }
    }

    @Test
    fun menueTicktUeberDasSpielFeedback() {
        val game = TimingGame(Random(SEED))
        val feedback = CountingFeedback()
        gameOverScene(game, feedback).use { scene ->
            scene.untilOverElapsed(game, 1.0f)
            assertEquals(0, feedback.ticks)
            scene.tap(barX, barY)
            scene.step(0.1)
            assertEquals(1, feedback.ticks, "LocalPressFeedback ist nicht an GameFeedback.tap() gebunden")
        }
    }

    // ===== Rollen für Vorleser =====

    /**
     * Alle antippbaren Knoten (mit OnClick) und ihre Rolle.
     *
     * ImageComposeScene verrät seine Semantik nicht mehr (`roots` ist
     * abgekündigt), deshalb eine eigene Szene, deren Plattform sich die
     * SemanticsOwner meldet.
     */
    @OptIn(InternalComposeUiApi::class)
    private fun clickableRoles(content: @Composable () -> Unit): List<Pair<String, Role?>> {
        val owners = mutableListOf<SemanticsOwner>()
        val listener = object : PlatformContext.SemanticsOwnerListener {
            override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
                owners += semanticsOwner
            }
            override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
                owners -= semanticsOwner
            }
            override fun onSemanticsChange(semanticsOwner: SemanticsOwner) {}
            override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) {}
        }
        val platform = object : PlatformContext by PlatformContext.Empty {
            override val semanticsOwnerListener = listener
        }
        val context = object : ComposeSceneContext {
            override val platformContext = platform
        }
        val scene = CanvasLayersComposeScene(
            Density(density),
            LayoutDirection.Ltr,
            IntSize(width, height),
            Dispatchers.Unconfined,
            context,
            {}
        )
        val out = mutableListOf<Pair<String, Role?>>()
        try {
            scene.setContent(content)
            val surface = Surface.makeRasterN32Premul(width, height)
            repeat(5) { scene.render(surface.canvas.asComposeCanvas(), it * 16_000_000L) }
            fun walk(node: SemanticsNode) {
                if (node.config.getOrNull(SemanticsActions.OnClick) != null) {
                    val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                        ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                        ?: node.children.firstNotNullOfOrNull {
                            it.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                        }
                        ?: "?"
                    out += label to node.config.getOrNull(SemanticsProperties.Role)
                }
                node.children.forEach { walk(it) }
            }
            owners.forEach { walk(it.unmergedRootSemanticsNode) }
        } finally {
            scene.close()
        }
        return out
    }

    private fun assertButtonsOrSwitches(roles: List<Pair<String, Role?>>, expected: Int) {
        assertTrue(roles.size >= expected, "zu wenige Knöpfe gefunden: $roles")
        roles.forEach { (label, role) ->
            assertTrue(
                role == Role.Button || role == Role.Switch,
                "„$label“ hat die Rolle $role statt Button/Switch"
            )
        }
    }

    @Test
    fun einstellungenHabenNurKnoepfeUndSchalter() {
        val roles = clickableRoles {
            SettingsOverlay(
                soundOn = true,
                onToggleSound = {},
                reminderOn = false,
                onToggleReminder = {},
                onHelp = {},
                onClose = {},
                removeAdsPrice = "1,99 €",
                onRemoveAds = {},
                privacyVisible = true,
                onPrivacy = {},
                reminderSupported = true
            )
        }
        // TON, ERINNERUNG, HILFE, Werbung, Datenschutz, X
        assertButtonsOrSwitches(roles, expected = 6)
        assertEquals(2, roles.count { it.second == Role.Switch }, "$roles")
    }

    @Test
    fun leisteHatNurKnoepfe() {
        val roles = clickableRoles {
            GameOverOverlay(
                score = 7,
                bestScore = 12,
                isNewRecord = false,
                taunt = "ERNSTHAFT?",
                daily = false,
                dailyBest = 0,
                dailyStreak = 0,
                skinUnlocked = false,
                newMedal = false,
                newTwist = null,
                goal = null,
                onShare = {},
                onMenu = {}
            )
        }
        // Nur MENÜ und TEILEN; das „?“ ist seit AP-31 weg (Plan 7.3).
        assertButtonsOrSwitches(roles, expected = 2)
        assertTrue(roles.any { it.first in setOf("MENÜ", "MENU") }, "$roles")
        assertTrue(roles.any { it.first in setOf("TEILEN", "SHARE") }, "$roles")
    }

    @Test
    fun hilfeUndStatistikHabenEinX() {
        val roles = clickableRoles { HelpOverlay(onClose = {}) }
        assertTrue(roles.any { it.first in setOf("SCHLIESSEN", "CLOSE") && it.second == Role.Button }, "kein X in der Hilfe: $roles")
        now = 0L
        var closed = 0
        ImageComposeScene(width, height, Density(density)) {
            HelpOverlay(onClose = { closed++ })
        }.use { scene ->
            scene.step(0.1)
            // X oben rechts: 8 dp Rand, 48 dp Tippfläche.
            scene.tap(width - 32 * density, 32 * density)
            scene.step(0.1)
            assertEquals(1, closed)
        }
        now = 0L
        closed = 0
        ImageComposeScene(width, height, Density(density)) {
            StatsOverlay(
                stats = SkinStats(bestScore = 12, bestPerfectStreak = 3, bestDailyStreak = 1),
                goals = emptyList(),
                onClose = { closed++ }
            )
        }.use { scene ->
            scene.step(0.1)
            scene.tap(width - 32 * density, 32 * density)
            scene.step(0.1)
            assertEquals(1, closed)
        }
    }

    private companion object {
        const val SEED = 20260925L
    }
}
