package de.robinrehbein.punkt.game

import kotlin.random.Random

/**
 * Pure-Kotlin Engine für das "Punkt."-Spielprinzip: Schwerkraft-Kippen.
 *
 * Der Punkt rollt automatisch nach rechts (die Welt scrollt). Jeder Tap
 * dreht die Schwerkraft um, der Punkt fällt an die Decke bzw. zurück auf
 * den Boden. Hindernis-Säulen ragen von beiden Seiten in den Korridor —
 * wer im Flug ist, kann nur noch einmal umkippen, sonst nichts. Timing
 * und Commitment sind alles.
 *
 * Alle Längen sind in Einheiten der Bildschirmhöhe (h = 1.0) angegeben,
 * damit die Physik auf jedem Gerät identisch spielt. Die Breite der Welt
 * ergibt sich aus dem Seitenverhältnis (aspect = Breite / Höhe).
 */
class PunktGame(private val random: Random = Random.Default) {

    enum class Phase { READY, RUNNING, DYING, OVER }

    enum class GameEvent { STARTED, FLIPPED, SCORED, DIED, LANDED }

    /**
     * Eine Hindernis-Säule: ein Block wächst vom Boden, einer von der
     * Decke. Zwischen beiden bleibt immer ein befliegbarer Korridor.
     */
    class Obstacle(
        var x: Float,
        val floorHeight: Float,
        val ceilingHeight: Float,
        var scored: Boolean = false
    )

    var phase: Phase = Phase.READY
        private set

    var aspect: Float = 0.46f
        private set

    /** +1 = Schwerkraft nach unten, -1 = nach oben. */
    var gravityDir: Int = 1
        private set

    var dotY: Float = 0f
        private set

    var dotVelocity: Float = 0f
        private set

    var score: Int = 0
        private set

    var elapsed: Float = 0f
        private set

    /** Zeit seit dem letzten Schwerkraft-Kippen, für Animationen. */
    var timeSinceFlip: Float = 99f
        private set

    val obstacles = mutableListOf<Obstacle>()

    val dotX: Float get() = aspect * DOT_X_FRACTION

    val worldWidth: Float get() = aspect

    /** Scrollposition für Boden/Decke/Parallax-Ebenen. */
    var scrollOffset: Float = 0f
        private set

    init {
        dotY = playBottom() - DOT_RADIUS
    }

    fun setAspectRatio(widthOverHeight: Float) {
        if (widthOverHeight > 0f) aspect = widthOverHeight
    }

    /** Oberkante des Spielfelds (Unterkante der Decke). */
    fun playTop(): Float = CEILING_HEIGHT

    /** Unterkante des Spielfelds (Oberkante des Bodens). */
    fun playBottom(): Float = 1f - GROUND_HEIGHT

    /** Liegt der Punkt gerade auf einer der beiden Flächen auf? */
    val isGrounded: Boolean
        get() = (gravityDir > 0 && dotY >= playBottom() - DOT_RADIUS - EPSILON) ||
            (gravityDir < 0 && dotY <= playTop() + DOT_RADIUS + EPSILON)

    /** Aktuelle Scrollgeschwindigkeit, zieht mit dem Score an. */
    fun currentSpeed(): Float =
        (BASE_SPEED + score * SPEED_PER_POINT).coerceAtMost(MAX_SPEED)

    /** Aktuelle Korridorhöhe, wird mit dem Score enger. */
    fun currentGap(): Float =
        (BASE_GAP - score * GAP_SHRINK_PER_POINT).coerceAtLeast(MIN_GAP)

    /**
     * Abstand zwischen zwei Säulen — in Sekunden gedacht: Er wächst mit
     * dem Tempo mit, damit die Reaktionszeit für einen Seitenwechsel
     * konstant bleibt, egal wie schnell die Welt schon scrollt.
     */
    fun currentSpacing(): Float = currentSpeed() * OBSTACLE_SPACING_SECONDS

    /**
     * Verarbeitet einen Tap. In READY startet er den Lauf, in RUNNING
     * kippt er die Schwerkraft, in OVER (nach kurzer Sperre gegen
     * Wut-Taps) geht es zurück in den READY-Zustand.
     */
    fun tap(): GameEvent? {
        return when (phase) {
            Phase.READY -> {
                phase = Phase.RUNNING
                elapsed = 0f
                spawnInitialObstacle()
                GameEvent.STARTED
            }
            Phase.RUNNING -> {
                gravityDir = -gravityDir
                timeSinceFlip = 0f
                GameEvent.FLIPPED
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

    /** Setzt alles auf den READY-Zustand zurück (Rekord bleibt beim Store). */
    fun reset() {
        phase = Phase.READY
        gravityDir = 1
        dotY = playBottom() - DOT_RADIUS
        dotVelocity = 0f
        score = 0
        elapsed = 0f
        timeSinceFlip = 99f
        obstacles.clear()
    }

    /**
     * Schreibt einen Frame fort und liefert die dabei aufgetretenen Events.
     */
    fun update(deltaSeconds: Float): List<GameEvent> {
        val dt = deltaSeconds.coerceIn(0f, MAX_DELTA)
        elapsed += dt
        timeSinceFlip += dt
        val events = mutableListOf<GameEvent>()

        when (phase) {
            Phase.READY -> {
                dotY = playBottom() - DOT_RADIUS
                scrollOffset += currentSpeed() * dt
            }
            Phase.RUNNING -> {
                stepDot(dt)
                scrollOffset += currentSpeed() * dt
                stepObstacles(dt, events)
                checkCollisions(events)
            }
            Phase.DYING -> {
                // Der Punkt trudelt ohne Steuerung zur Fläche, die Welt steht.
                dotVelocity = (dotVelocity + GRAVITY * gravityDir * dt)
                    .coerceIn(-TERMINAL_VELOCITY, TERMINAL_VELOCITY)
                dotY += dotVelocity * dt
                val landedDown = gravityDir > 0 && dotY + DOT_RADIUS >= playBottom()
                val landedUp = gravityDir < 0 && dotY - DOT_RADIUS <= playTop()
                if (landedDown || landedUp) {
                    dotY = if (landedDown) playBottom() - DOT_RADIUS else playTop() + DOT_RADIUS
                    phase = Phase.OVER
                    elapsed = 0f
                    events.add(GameEvent.LANDED)
                }
            }
            Phase.OVER -> Unit
        }
        return events
    }

    private fun stepDot(dt: Float) {
        dotVelocity = (dotVelocity + GRAVITY * gravityDir * dt)
            .coerceIn(-TERMINAL_VELOCITY, TERMINAL_VELOCITY)
        dotY += dotVelocity * dt

        // Auf den Flächen aufsetzen — die Flächen selbst sind nie tödlich.
        if (dotY + DOT_RADIUS >= playBottom()) {
            dotY = playBottom() - DOT_RADIUS
            if (dotVelocity > 0f) dotVelocity = 0f
        }
        if (dotY - DOT_RADIUS <= playTop()) {
            dotY = playTop() + DOT_RADIUS
            if (dotVelocity < 0f) dotVelocity = 0f
        }
    }

    private fun stepObstacles(dt: Float, events: MutableList<GameEvent>) {
        val speed = currentSpeed()
        obstacles.forEach { it.x -= speed * dt }
        obstacles.removeAll { it.x + OBSTACLE_WIDTH < -0.1f }

        val last = obstacles.lastOrNull()
        if (last == null || last.x <= worldWidth - currentSpacing()) {
            spawnObstacle(worldWidth + OBSTACLE_WIDTH)
        }

        obstacles.forEach { obstacle ->
            if (!obstacle.scored && obstacle.x + OBSTACLE_WIDTH < dotX - DOT_RADIUS) {
                obstacle.scored = true
                score++
                events.add(GameEvent.SCORED)
            }
        }
    }

    private fun checkCollisions(events: MutableList<GameEvent>) {
        val hitRadius = DOT_RADIUS * HITBOX_FORGIVENESS
        obstacles.forEach { obstacle ->
            val left = obstacle.x
            val right = obstacle.x + OBSTACLE_WIDTH
            if (dotX + hitRadius > left && dotX - hitRadius < right) {
                val floorBlockTop = playBottom() - obstacle.floorHeight
                val ceilingBlockBottom = playTop() + obstacle.ceilingHeight
                if (dotY + hitRadius > floorBlockTop || dotY - hitRadius < ceilingBlockBottom) {
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
        // Kleiner Todes-Hüpfer entgegen der Schwerkraft.
        dotVelocity = -DEATH_BOUNCE_VELOCITY * gravityDir
        events.add(GameEvent.DIED)
    }

    private fun spawnInitialObstacle() {
        obstacles.clear()
        spawnObstacle(worldWidth + FIRST_OBSTACLE_DISTANCE)
    }

    private fun spawnObstacle(x: Float) {
        val playHeight = playBottom() - playTop()
        val gap = currentGap()
        val blocked = (playHeight - gap).coerceAtLeast(MIN_BLOCK_HEIGHT)

        // Die ersten Säulen kommen nur vom Boden, danach wird gemischt:
        // nur Boden, nur Decke oder beide Seiten gleichzeitig.
        val floorHeight: Float
        val ceilingHeight: Float
        when {
            score < EASY_OBSTACLE_COUNT -> {
                floorHeight = blocked
                ceilingHeight = 0f
            }
            else -> when (random.nextInt(3)) {
                0 -> {
                    floorHeight = blocked
                    ceilingHeight = 0f
                }
                1 -> {
                    floorHeight = 0f
                    ceilingHeight = blocked
                }
                else -> {
                    val floorShare = MIN_SPLIT_SHARE +
                        random.nextFloat() * (1f - 2 * MIN_SPLIT_SHARE)
                    floorHeight = blocked * floorShare
                    ceilingHeight = blocked - floorHeight
                }
            }
        }
        obstacles.add(Obstacle(x = x, floorHeight = floorHeight, ceilingHeight = ceilingHeight))
    }

    companion object {
        // Physik (Einheiten: Bildschirmhöhen bzw. Bildschirmhöhen pro Sekunde)
        const val GRAVITY = 7.0f
        const val TERMINAL_VELOCITY = 2.3f
        const val DEATH_BOUNCE_VELOCITY = 0.42f
        const val MAX_DELTA = 1f / 30f
        const val EPSILON = 0.001f

        // Welt
        const val DOT_X_FRACTION = 0.32f
        const val DOT_RADIUS = 0.026f
        const val HITBOX_FORGIVENESS = 0.85f
        const val CEILING_HEIGHT = 0.08f
        const val GROUND_HEIGHT = 0.12f
        const val OBSTACLE_WIDTH = 0.09f
        const val OBSTACLE_SPACING_SECONDS = 1.5f
        const val FIRST_OBSTACLE_DISTANCE = 0.7f
        const val MIN_BLOCK_HEIGHT = 0.1f
        const val MIN_SPLIT_SHARE = 0.25f
        const val EASY_OBSTACLE_COUNT = 4

        // Schwierigkeitskurve
        const val BASE_SPEED = 0.34f
        const val SPEED_PER_POINT = 0.003f
        const val MAX_SPEED = 0.52f
        const val BASE_GAP = 0.36f
        const val GAP_SHRINK_PER_POINT = 0.002f
        const val MIN_GAP = 0.27f

        // Sperre gegen versehentliches Überspringen des Game-Over-Screens
        const val RESTART_LOCK_SECONDS = 0.55f
    }
}
