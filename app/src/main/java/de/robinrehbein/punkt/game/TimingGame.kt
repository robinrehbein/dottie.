package de.robinrehbein.punkt.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/**
 * Pure-Kotlin Engine für das "Stopp"-Spielprinzip: Timing-Präzision.
 *
 * Der Punkt läuft automatisch auf einer Kreisbahn. Irgendwo auf der Bahn
 * liegt eine Zielzone — ein Tap, während der Punkt in der Zone ist,
 * zählt einen Treffer: Die Laufrichtung dreht um, die Zone springt an
 * eine neue Position, das Tempo steigt und die Zone schrumpft. Ein Tap
 * außerhalb der Zone oder ein Überfahren der Zone ohne Tap ist sofort
 * das Ende. Ein Spiel über Präzision statt Reflex-Dauerfeuer.
 *
 * Winkel sind in Radiant, Geschwindigkeiten in Radiant pro Sekunde.
 */
class TimingGame(private val random: Random = Random.Default) {

    enum class Phase { READY, RUNNING, DYING, OVER }

    enum class GameEvent { STARTED, HIT, PERFECT_HIT, DIED, SETTLED }

    var phase: Phase = Phase.READY
        private set

    /** Position des Punkts auf der Bahn. */
    var angle: Float = 0f
        private set

    /** Laufrichtung: +1 = im Uhrzeigersinn, -1 = dagegen. */
    var direction: Int = 1
        private set

    var zoneCenter: Float = 1.8f
        private set

    var zoneHalfWidth: Float = BASE_ZONE_HALF
        private set

    var score: Int = 0
        private set

    var elapsed: Float = 0f
        private set

    /** Zeit seit dem letzten Treffer, für Animationen. */
    var timeSinceHit: Float = 99f
        private set

    /** War der letzte Treffer ein perfekter? Für Anzeige-Effekte. */
    var lastHitPerfect: Boolean = false
        private set

    /**
     * Events aus tap() werden gepuffert und mit dem nächsten update()
     * ausgeliefert, damit der UI-Loop eine einzige Event-Quelle hat.
     */
    private val pendingEvents = mutableListOf<GameEvent>()

    /** Relative Position des Punkts zur Zone: negativ = davor, 0 = Mitte. */
    fun relativeToZone(): Float = wrapToPi(direction * (angle - zoneCenter))

    /** Steht der Punkt gerade in der Zielzone? */
    val isInZone: Boolean get() = abs(relativeToZone()) <= zoneHalfWidth

    fun currentSpeed(): Float =
        (BASE_SPEED + score * SPEED_PER_HIT).coerceAtMost(MAX_SPEED)

    /**
     * Verarbeitet einen Tap. In READY startet er den Lauf, in RUNNING ist
     * er der Stopp-Versuch, in OVER (nach kurzer Sperre gegen Wut-Taps)
     * geht es zurück in den READY-Zustand.
     */
    fun tap(): GameEvent? {
        val event = when (phase) {
            Phase.READY -> {
                phase = Phase.RUNNING
                elapsed = 0f
                spawnZone()
                GameEvent.STARTED
            }
            Phase.RUNNING -> {
                val rel = relativeToZone()
                if (abs(rel) <= zoneHalfWidth) {
                    val perfect = abs(rel) <= zoneHalfWidth * PERFECT_SHARE
                    registerHit(perfect)
                    if (perfect) GameEvent.PERFECT_HIT else GameEvent.HIT
                } else {
                    die()
                    GameEvent.DIED
                }
            }
            Phase.DYING -> null
            Phase.OVER -> {
                if (elapsed >= RESTART_LOCK_SECONDS) {
                    reset()
                }
                null
            }
        }
        if (event != null) pendingEvents.add(event)
        return event
    }

    /** Setzt alles auf den READY-Zustand zurück (Rekord bleibt beim Store). */
    fun reset() {
        phase = Phase.READY
        angle = 0f
        direction = 1
        zoneCenter = 1.8f
        zoneHalfWidth = BASE_ZONE_HALF
        score = 0
        elapsed = 0f
        timeSinceHit = 99f
        lastHitPerfect = false
        pendingEvents.clear()
    }

    /**
     * Schreibt einen Frame fort und liefert die dabei aufgetretenen Events.
     */
    fun update(deltaSeconds: Float): List<GameEvent> {
        val dt = deltaSeconds.coerceIn(0f, MAX_DELTA)
        elapsed += dt
        timeSinceHit += dt
        val events = mutableListOf<GameEvent>()
        events.addAll(pendingEvents)
        pendingEvents.clear()

        when (phase) {
            Phase.READY -> {
                angle = wrapTwoPi(angle + direction * READY_SPEED * dt)
            }
            Phase.RUNNING -> {
                angle = wrapTwoPi(angle + direction * currentSpeed() * dt)
                // Zone ohne Tap überfahren → vorbei.
                if (relativeToZone() > zoneHalfWidth + PASS_BUFFER) {
                    die()
                    events.add(GameEvent.DIED)
                }
            }
            Phase.DYING -> {
                // Kurzer Freeze für Flash und Shake, dann steht das Ergebnis.
                if (elapsed >= DEATH_FREEZE_SECONDS) {
                    phase = Phase.OVER
                    elapsed = 0f
                    events.add(GameEvent.SETTLED)
                }
            }
            Phase.OVER -> Unit
        }
        return events
    }

    private fun registerHit(perfect: Boolean) {
        score++
        timeSinceHit = 0f
        lastHitPerfect = perfect
        zoneHalfWidth = (BASE_ZONE_HALF - score * ZONE_SHRINK_PER_HIT)
            .coerceAtLeast(MIN_ZONE_HALF)
        direction = -direction
        spawnZone()
    }

    private fun spawnZone() {
        // Die Zone erscheint immer in Laufrichtung, mindestens MIN_DIST
        // entfernt und höchstens eine gute halbe Runde — nie sofort tödlich.
        val distance = MIN_ZONE_DISTANCE +
            random.nextFloat() * (MAX_ZONE_DISTANCE - MIN_ZONE_DISTANCE)
        zoneCenter = wrapTwoPi(angle + direction * distance)
    }

    private fun die() {
        if (phase != Phase.RUNNING) return
        phase = Phase.DYING
        elapsed = 0f
    }

    companion object {
        const val MAX_DELTA = 1f / 30f

        // Tempo (Radiant pro Sekunde)
        const val BASE_SPEED = 2.4f
        const val SPEED_PER_HIT = 0.07f
        const val MAX_SPEED = 5.2f
        const val READY_SPEED = 1.2f

        // Zielzone (Radiant)
        const val BASE_ZONE_HALF = 0.40f
        const val ZONE_SHRINK_PER_HIT = 0.006f
        const val MIN_ZONE_HALF = 0.15f
        const val PERFECT_SHARE = 0.35f
        const val PASS_BUFFER = 0.05f
        const val MIN_ZONE_DISTANCE = 1.1f
        const val MAX_ZONE_DISTANCE = 2.8f

        const val DEATH_FREEZE_SECONDS = 0.5f
        const val RESTART_LOCK_SECONDS = 0.55f

        private const val TWO_PI = (2 * PI).toFloat()

        /** Normalisiert auf (-PI, PI]. */
        fun wrapToPi(value: Float): Float {
            var v = value % TWO_PI
            if (v <= -PI) v += TWO_PI
            if (v > PI) v -= TWO_PI
            return v
        }

        /** Normalisiert auf [0, 2*PI). */
        fun wrapTwoPi(value: Float): Float {
            var v = value % TWO_PI
            if (v < 0f) v += TWO_PI
            return v
        }
    }
}
