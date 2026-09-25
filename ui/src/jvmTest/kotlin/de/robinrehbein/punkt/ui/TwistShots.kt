package de.robinrehbein.punkt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.world.FxState
import de.robinrehbein.punkt.ui.world.drawTimingWorld
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Twist-Bilder ohne Bildschirm: zeichnet [drawTimingWorld] mit einem
 * geseedeten [TimingGame], erzwungenem Twist-Set ([TimingGame.twistOverride])
 * und einer eigenen Bot-Schleife.
 *
 * Über GameScreen ginge das nicht: Dort bestimmt der Zufall, welcher
 * Twist wann kommt, und ein Twist zeigt sich erst ab seinem Score. Der
 * Bot aus core/jvmTest (ParityBot) ist von hier aus nicht sichtbar,
 * deshalb spielt [playTo] selbst — nur über die öffentliche API der
 * Engine.
 *
 * Wie der ScreenshotRenderer ein Werkzeug, kein Prüfstein: Ohne SHOTS_DIR
 * kehrt jeder Test sofort zurück. Jedes Paket legt seine Bilder als
 * eigenen Test zwischen seine Anker.
 */
class TwistShots {

    /** Zielverzeichnis der Bilder, oder null: dann tut der Test nichts. */
    private fun shotsDir(): File? {
        val dir = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return null
        return File(dir, "twists").apply { mkdirs() }
    }

    // == AP-11 todesursache ==
    // == /AP-11 ==

    /**
     * Grundbilder: je Twist ein Bild nach 15 Treffern, dazu eins ohne
     * Twist — der Punkt kurz vor der nächsten Zone.
     */
    @Test
    fun grundbilder() {
        val dir = shotsDir() ?: return
        val sets = listOf("ohne" to emptySet<Twist>()) +
            Twist.entries.map { it.name.lowercase() to setOf(it) }
        sets.forEach { (name, twists) ->
            val game = seededGame(twists)
            check(playTo(game, 15)) { "Bot ist bei $name vor 15 Treffern gestorben" }
            if (Twist.FAKE in twists) playUntilFake(game)
            approachZone(game)
            shoot(dir, "twist-$name.png", game)
        }
    }

    // == AP-12 bomben ==
    // == /AP-12 ==

    /** Ein Spiel mit festem Seed und festem Twist-Set, noch in READY. */
    private fun seededGame(twists: Set<Twist>, seed: Long = SEED): TimingGame =
        TimingGame(Random(seed)).apply { twistOverride = twists }

    // == AP-13 welten ==
    /**
     * Der Himmel ab Score 10 in allen sechs Welten — die Stufe, die bis
     * zur Welten-Leiter Lila war und für die Falle gehalten wurde. Ohne
     * Twist, der Punkt kurz vor der Zone.
     */
    @Test
    fun himmelBeiScore10() {
        val dir = shotsDir() ?: return
        SceneId.entries.forEach { scene ->
            val game = seededGame(emptySet())
            var runde = 0
            while (game.score < 10) {
                check(playTo(game, game.hits + 1)) { "Bot ist in $scene vor Score 10 gestorben" }
                check(++runde < 20) { "Score 10 wird in $scene nicht erreicht" }
            }
            val stufe = de.robinrehbein.punkt.game.SkinPaint.skyStage(game.score)
            check(stufe == 2) { "Score ${game.score} liegt auf Stufe $stufe statt 2" }
            approachZone(game)
            shoot(dir, "himmel-10-${scene.name.lowercase()}.png", game, scene = scene)
        }
    }
    // == /AP-13 ==

    /**
     * Spielt, bis [hits] Treffer erreicht sind: Getippt wird genau dann,
     * wenn der Punkt die innere Hälfte des PERFEKT-Kerns erreicht. Der
     * Takt ist fein genug (1/240 s), dass selbst beim Höchsttempo kein
     * Kern übersprungen wird. Liefert false, falls das Spiel vorher endet.
     *
     * Der Start läuft über dieselbe Regel: Ein Tap im Grün startet heute
     * den Lauf und zählt nach der neuen Startregel (AP-21) als Treffer —
     * der Bot funktioniert mit beiden.
     */
    private fun playTo(game: TimingGame, hits: Int): Boolean {
        var frames = 0
        while (game.hits < hits) {
            game.update(BOT_DT)
            if (game.phase == GamePhase.DYING || game.phase == GamePhase.OVER) return false
            val rel = game.relativeToZone()
            val core = game.perfectHalf() * 0.5f
            if (rel >= -core && rel <= core) game.tap()
            check(++frames < MAX_BOT_FRAMES) { "Bot kommt nicht voran" }
        }
        return true
    }

    /** Spielt weiter, bis die aktuelle Zone eine Falle hat (höchstens 20 Treffer). */
    private fun playUntilFake(game: TimingGame) {
        repeat(20) {
            if (game.hasFakeZone) return
            check(playTo(game, game.hits + 1)) { "Bot ist auf der Suche nach der Falle gestorben" }
        }
    }

    // == AP-23 nebel ==
    // == /AP-23 ==

    /** Lässt den Punkt bis kurz vor die Zone laufen, ohne zu tippen. */
    private fun approachZone(game: TimingGame, gap: Float = 0.5f) {
        var frames = 0
        while (game.relativeToZone() < -gap - game.effectiveZoneHalf()) {
            game.update(BOT_DT)
            check(++frames < MAX_BOT_FRAMES) { "Punkt erreicht die Zone nicht" }
        }
    }

    /** Zeichnet die Welt einmal und schreibt sie als PNG. */
    private fun shoot(
        dir: File,
        name: String,
        game: TimingGame,
        fx: FxState = FxState(),
        scene: SceneId = SceneId.WIESE,
        width: Int = 1080,
        height: Int = 2400,
        density: Float = 2.625f
    ) {
        ImageComposeScene(width = width, height = height, density = Density(density)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTimingWorld(game, fx, SkinId.KLASSIK, scene, hour = 12, month = 6)
            }
        }.use { scene ->
            val data = scene.render(0L).encodeToData(EncodedImageFormat.PNG)!!
            File(dir, name).writeBytes(data.bytes)
            println("-> twists/$name")
        }
    }

    private companion object {
        const val SEED = 20260925L
        const val BOT_DT = 1f / 240f
        const val MAX_BOT_FRAMES = 2_000_000
    }
}
