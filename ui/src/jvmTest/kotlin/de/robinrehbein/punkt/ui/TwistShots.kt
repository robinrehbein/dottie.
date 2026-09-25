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
    /**
     * Die vier Tode im Freeze: weißer Rahmen um den Vogel, Ursache unter
     * dem Ring. BOOM! einmal mit Lektion (erster Bomben-Tod) und einmal
     * unter PULS, wo die Zone im Freeze stillstehen muss. Dazu jeder Tod
     * auch auf 720×1280 und einmal im Sturz, wenn der Rahmen weg ist.
     */
    @Test
    fun todesursachen() {
        val dir = shotsDir() ?: return
        data class Tod(
            val name: String,
            val twists: Set<Twist>,
            val cause: de.robinrehbein.punkt.game.DeathCause,
            val lesson: Boolean = false
        )
        val tode = listOf(
            Tod("frueh", emptySet(), de.robinrehbein.punkt.game.DeathCause.EARLY),
            Tod("spaet", emptySet(), de.robinrehbein.punkt.game.DeathCause.LATE),
            Tod("verpasst", emptySet(), de.robinrehbein.punkt.game.DeathCause.MISSED),
            Tod("boom", setOf(Twist.FAKE), de.robinrehbein.punkt.game.DeathCause.TRAP, lesson = true),
            Tod("boom-puls", setOf(Twist.FAKE, Twist.PULSE), de.robinrehbein.punkt.game.DeathCause.TRAP)
        )
        tode.forEach { tod ->
            listOf(
                Triple(1080, 2400, 2.625f),
                Triple(720, 1280, 2f)
            ).forEach { (w, h, d) ->
                val game = seededGame(tod.twists)
                check(playTo(game, 6)) { "Bot ist bei ${tod.name} vor 6 Treffern gestorben" }
                dieBy(game, tod.cause, needPulse = Twist.PULSE in tod.twists)
                check(game.lastDeathCause == tod.cause) {
                    "${tod.name}: erwartet ${tod.cause}, bekommen ${game.lastDeathCause}"
                }
                // Mitten im Freeze: Rahmen steht, Blitz und Wackeln sind
                // schon abgeklungen (sonst wäre das Bild weiß).
                val fx = FxState().apply { deathTime = 0.3f }
                game.update(0.3f)
                val suffix = if (w == 1080) "" else "-${w}x$h"
                shootDeath(dir, "tod-${tod.name}$suffix.png", game, fx, tod.lesson, w, h, d)
                if (tod.name == "frueh" && w == 1080) {
                    fx.deathTime = 0.8f
                    game.update(0.5f)
                    shootDeath(dir, "tod-${tod.name}-sturz.png", game, fx, false, w, h, d)
                }
            }
        }
    }

    /**
     * Führt den gewünschten Tod herbei. ZU FRÜH: Tap kurz vor der Zone.
     * ZU SPÄT: Tap knapp hinter der Tap-Gnade, vor dem Überfahren.
     * VERPASST: gar nicht tippen. BOOM!: in die Falle tippen, echte Zonen
     * unterwegs treffen, bis eine Falle kommt (bei [needPulse] eine mit PULS).
     */
    private fun dieBy(game: TimingGame, cause: de.robinrehbein.punkt.game.DeathCause, needPulse: Boolean) {
        var frames = 0
        while (game.phase == GamePhase.RUNNING) {
            game.update(BOT_DT)
            if (game.phase != GamePhase.RUNNING) break
            val rel = game.relativeToZone()
            val half = game.effectiveZoneHalf()
            val speed = game.currentSpeed()
            when (cause) {
                de.robinrehbein.punkt.game.DeathCause.EARLY ->
                    if (rel > -half - 0.12f && rel < -half) game.tap()
                de.robinrehbein.punkt.game.DeathCause.LATE ->
                    if (rel > half + speed * (TimingGame.LATE_TAP_FORGIVENESS_SECONDS + 0.01f)) game.tap()
                de.robinrehbein.punkt.game.DeathCause.MISSED -> Unit
                de.robinrehbein.punkt.game.DeathCause.TRAP -> {
                    val inFake = game.hasFakeZone && (!needPulse || Twist.PULSE in game.activeTwists) &&
                        kotlin.math.abs(wrapToPi(game.angle - game.fakeZoneCenter)) <= game.fakeZoneHalf() * 0.5f
                    val core = game.perfectHalf() * 0.5f
                    if (inFake || (rel >= -core && rel <= core)) game.tap()
                }
                de.robinrehbein.punkt.game.DeathCause.NONE -> error("NONE ist kein Tod")
            }
            check(++frames < MAX_BOT_FRAMES) { "Tod $cause kommt nicht zustande" }
        }
    }

    private fun wrapToPi(a: Float): Float {
        val twoPi = (2.0 * kotlin.math.PI).toFloat()
        var x = a % twoPi
        if (x > kotlin.math.PI) x -= twoPi
        if (x < -kotlin.math.PI) x += twoPi
        return x
    }

    /** Welt und Ursache übereinander, wie im GameScreen während DYING. */
    private fun shootDeath(
        dir: File,
        name: String,
        game: TimingGame,
        fx: FxState,
        lesson: Boolean,
        width: Int,
        height: Int,
        density: Float
    ) {
        ImageComposeScene(width = width, height = height, density = Density(density)) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawTimingWorld(game, fx, SkinId.KLASSIK, SceneId.WIESE, hour = 12, month = 6)
                }
                de.robinrehbein.punkt.ui.screens.DeathCauseLabel(
                    cause = game.lastDeathCause,
                    bombLesson = lesson
                )
            }
        }.use { scene ->
            val data = scene.render(0L).encodeToData(EncodedImageFormat.PNG)!!
            File(dir, name).writeBytes(data.bytes)
            println("-> twists/$name")
        }
    }
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
