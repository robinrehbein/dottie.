package de.robinrehbein.punkt.game

import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure-Kotlin Engine für das Flappy-Gameplay.
 *
 * Alle Längen sind in Einheiten der Bildschirmhöhe (h = 1.0) angegeben,
 * damit die Physik auf jedem Gerät identisch spielt. Die Breite der Welt
 * ergibt sich aus dem Seitenverhältnis (aspect = Breite / Höhe).
 */
class FlappyGame(private val random: Random = Random.Default) {

    enum class Phase { READY, RUNNING, DYING, OVER }

    enum class GameEvent { STARTED, FLAPPED, SCORED, DIED, LANDED }

    class Pipe(
        var x: Float,
        val gapCenter: Float,
        val gapHalf: Float,
        var scored: Boolean = false
    ) {
        val gapTop: Float get() = gapCenter - gapHalf
        val gapBottom: Float get() = gapCenter + gapHalf
    }

    var phase: Phase = Phase.READY
        private set

    var aspect: Float = 0.46f
        private set

    var birdY: Float = READY_BIRD_Y
        private set

    var birdVelocity: Float = 0f
        private set

    var score: Int = 0
        private set

    var elapsed: Float = 0f
        private set

    /** Zeit seit dem letzten Flap, für die Flügel-Animation. */
    var timeSinceFlap: Float = 99f
        private set

    val pipes = mutableListOf<Pipe>()

    val birdX: Float get() = aspect * BIRD_X_FRACTION

    val worldWidth: Float get() = aspect

    /** Scrollposition für Boden/Parallax-Ebenen. */
    var scrollOffset: Float = 0f
        private set

    fun setAspectRatio(widthOverHeight: Float) {
        if (widthOverHeight > 0f) aspect = widthOverHeight
    }

    /** Aktuelle Scrollgeschwindigkeit, zieht mit dem Score an. */
    fun currentSpeed(): Float =
        (BASE_SPEED + score * SPEED_PER_POINT).coerceAtMost(MAX_SPEED)

    /** Aktuelle halbe Lückenhöhe, wird mit dem Score enger. */
    fun currentGapHalf(): Float =
        (BASE_GAP_HALF - score * GAP_SHRINK_PER_POINT).coerceAtLeast(MIN_GAP_HALF)

    /**
     * Verarbeitet einen Tap. In READY startet er den Lauf, in RUNNING ist er
     * ein Flügelschlag, in OVER (nach kurzer Sperre gegen Wut-Taps) geht es
     * zurück in den READY-Zustand.
     */
    fun tap(): GameEvent? {
        return when (phase) {
            Phase.READY -> {
                phase = Phase.RUNNING
                elapsed = 0f
                birdVelocity = FLAP_VELOCITY
                timeSinceFlap = 0f
                spawnInitialPipe()
                GameEvent.STARTED
            }
            Phase.RUNNING -> {
                birdVelocity = FLAP_VELOCITY
                timeSinceFlap = 0f
                GameEvent.FLAPPED
            }
            Phase.DYING -> null
            Phase.OVER -> {
                if (elapsed >= RESTART_LOCK_SECONDS) {
                    reset()
                }
                null
            }
        }
    }

    /** Setzt alles auf den READY-Zustand zurück (Score bleibt beim Store). */
    fun reset() {
        phase = Phase.READY
        birdY = READY_BIRD_Y
        birdVelocity = 0f
        score = 0
        elapsed = 0f
        timeSinceFlap = 99f
        pipes.clear()
    }

    /**
     * Schreibt einen Frame fort und liefert die dabei aufgetretenen Events.
     */
    fun update(deltaSeconds: Float): List<GameEvent> {
        val dt = deltaSeconds.coerceIn(0f, MAX_DELTA)
        elapsed += dt
        timeSinceFlap += dt
        val events = mutableListOf<GameEvent>()

        when (phase) {
            Phase.READY -> {
                birdY = READY_BIRD_Y + sin(elapsed * BOB_FREQUENCY) * BOB_AMPLITUDE
                scrollOffset += currentSpeed() * dt
            }
            Phase.RUNNING -> {
                stepBird(dt)
                scrollOffset += currentSpeed() * dt
                stepPipes(dt, events)
                checkCollisions(events)
            }
            Phase.DYING -> {
                // Der Punkt trudelt ohne Steuerung zu Boden, die Welt steht.
                stepBird(dt)
                if (birdY + BIRD_RADIUS >= groundTop()) {
                    birdY = groundTop() - BIRD_RADIUS
                    phase = Phase.OVER
                    elapsed = 0f
                    events.add(GameEvent.LANDED)
                }
            }
            Phase.OVER -> Unit
        }
        return events
    }

    fun groundTop(): Float = 1f - GROUND_HEIGHT

    private fun stepBird(dt: Float) {
        birdVelocity = (birdVelocity + GRAVITY * dt).coerceAtMost(TERMINAL_VELOCITY)
        birdY += birdVelocity * dt
        if (birdY - BIRD_RADIUS < 0f) {
            birdY = BIRD_RADIUS
            birdVelocity = 0f
        }
    }

    private fun stepPipes(dt: Float, events: MutableList<GameEvent>) {
        val speed = currentSpeed()
        pipes.forEach { it.x -= speed * dt }
        pipes.removeAll { it.x + PIPE_WIDTH < -0.1f }

        val last = pipes.lastOrNull()
        if (last == null || last.x <= worldWidth - PIPE_SPACING) {
            spawnPipe(worldWidth + PIPE_WIDTH)
        }

        pipes.forEach { pipe ->
            if (!pipe.scored && pipe.x + PIPE_WIDTH < birdX - BIRD_RADIUS) {
                pipe.scored = true
                score++
                events.add(GameEvent.SCORED)
            }
        }
    }

    private fun checkCollisions(events: MutableList<GameEvent>) {
        if (birdY + BIRD_RADIUS >= groundTop()) {
            birdY = groundTop() - BIRD_RADIUS
            die(events)
            events.add(GameEvent.LANDED)
            phase = Phase.OVER
            elapsed = 0f
            return
        }

        val hitRadius = BIRD_RADIUS * HITBOX_FORGIVENESS
        pipes.forEach { pipe ->
            val insideX = birdX + hitRadius > pipe.x && birdX - hitRadius < pipe.x + PIPE_WIDTH
            if (insideX) {
                val hitsTop = birdY - hitRadius < pipe.gapTop
                val hitsBottom = birdY + hitRadius > pipe.gapBottom
                if (hitsTop || hitsBottom) {
                    die(events)
                    return
                }
            }
        }
    }

    private fun die(events: MutableList<GameEvent>) {
        if (phase != Phase.RUNNING) return
        phase = Phase.DYING
        elapsed = 0f
        // Kleiner Todes-Hüpfer wie beim Original.
        birdVelocity = DEATH_BOUNCE_VELOCITY
        events.add(GameEvent.DIED)
    }

    private fun spawnInitialPipe() {
        pipes.clear()
        spawnPipe(worldWidth + FIRST_PIPE_DISTANCE)
    }

    private fun spawnPipe(x: Float) {
        val gapHalf = currentGapHalf()
        val minCenter = GAP_MARGIN + gapHalf
        val maxCenter = groundTop() - GAP_MARGIN - gapHalf
        val center = if (maxCenter > minCenter) {
            minCenter + random.nextFloat() * (maxCenter - minCenter)
        } else {
            (minCenter + maxCenter) / 2f
        }
        pipes.add(Pipe(x = x, gapCenter = center, gapHalf = gapHalf))
    }

    companion object {
        // Physik (Einheiten: Bildschirmhöhen bzw. Bildschirmhöhen pro Sekunde)
        const val GRAVITY = 3.4f
        const val FLAP_VELOCITY = -0.88f
        const val TERMINAL_VELOCITY = 1.6f
        const val DEATH_BOUNCE_VELOCITY = -0.35f
        const val MAX_DELTA = 1f / 30f

        // Welt
        const val BIRD_X_FRACTION = 0.32f
        const val BIRD_RADIUS = 0.026f
        const val HITBOX_FORGIVENESS = 0.85f
        const val GROUND_HEIGHT = 0.12f
        const val PIPE_WIDTH = 0.085f
        const val PIPE_SPACING = 0.44f
        const val FIRST_PIPE_DISTANCE = 0.55f
        const val GAP_MARGIN = 0.05f

        // Schwierigkeitskurve
        const val BASE_SPEED = 0.34f
        const val SPEED_PER_POINT = 0.0035f
        const val MAX_SPEED = 0.52f
        const val BASE_GAP_HALF = 0.135f
        const val GAP_SHRINK_PER_POINT = 0.0011f
        const val MIN_GAP_HALF = 0.105f

        // Menü-Animation
        const val READY_BIRD_Y = 0.42f
        const val BOB_FREQUENCY = 4.5f
        const val BOB_AMPLITUDE = 0.012f

        // Sperre gegen versehentliches Überspringen des Game-Over-Screens
        const val RESTART_LOCK_SECONDS = 0.55f
    }
}
