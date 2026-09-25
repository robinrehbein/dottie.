package de.robinrehbein.punkt.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * Ein Bot, der einen Lauf deterministisch durchspielt — die Vorlage für
 * die `trace`-Abschnitte in `parity/golden-vectors.txt`.
 *
 * Er hält sich an zwei Regeln, damit derselbe Lauf auf jeder Übersetzung
 * der Engine Frame für Frame dasselbe tut — die JVM und Kotlin/Native
 * rechnen Fließkomma nicht bitgleich:
 *
 * 1. **Fester Zeitschritt.** [DT] ist so klein, dass der Punkt nie über
 *    das Tap-Fenster springt: Selbst bei Höchsttempo wandert er pro Frame
 *    weniger weit, als das Fenster breit ist.
 * 2. **Keine Entscheidung hängt an `sin`.** `effectiveZoneHalf` pulsiert
 *    über einen Sinus, und `sin` darf sich zwischen zwei Sprachen im
 *    letzten Bit unterscheiden. Der Bot rechnet deshalb nur mit der
 *    Basisbreite und tappt so tief im Kern, dass der Treffer auch bei
 *    maximal zusammengezogener Zone noch perfekt ist.
 *
 * Die aufsummierten Zahlen (Winkel, Zonenmitte) dürfen sich dagegen
 * leicht unterscheiden: Die JVM rechnet jede Operation einzeln, LLVM darf
 * `a + b * c` verschmelzen. Über einen ganzen Lauf sind das rund 4·10⁻⁴
 * Radiant — die Ports vergleichen sie deshalb mit Toleranz, während alle
 * ganzzahligen Werte exakt stimmen müssen (siehe parity/README.md).
 */
object ParityBot {

    /** 240 Hz: klein genug, dass kein Tap-Fenster übersprungen wird. */
    const val DT = 1f / 240f

    /** Notbremse, damit ein kaputter Lauf nicht ewig dreht. */
    private const val MAX_FRAMES = 100_000

    /**
     * Abstand zur Zonenmitte, bei dem der Bot tappt — als Anteil der
     * Basisbreite. PERFECT_SHARE * PULSE_MIN_SHARE deckt den ungünstigsten
     * Puls-Zustand ab, die halbe Breite davon lässt Luft nach beiden
     * Seiten.
     */
    private const val TAP_WINDOW =
        TimingGame.PERFECT_SHARE * TimingGame.PULSE_MIN_SHARE * 0.5f

    /** Ein Treffer im Trace. */
    data class Snapshot(
        val score: Int,
        val hits: Int,
        val perfectStreak: Int,
        val lastHitPoints: Int,
        val direction: Int,
        val twists: List<String>,
        val zoneCenter: Float,
        val zoneHalfWidth: Float,
        val angle: Float,
        val chainRemaining: Int,
        val fakeZoneCenter: Float?
    )

    /** Ergebnis eines Laufs ohne einen einzigen Tap. */
    data class Death(
        val framesToDeath: Int,
        val framesToSettle: Int,
        val angleAtDeath: Float,
        val zoneCenterAtDeath: Float
    )

    /**
     * Spielt bis [maxHits] perfekte Treffer und liefert nach jedem Treffer
     * einen Schnappschuss. Sollte der Lauf vorher enden (dann stimmt etwas
     * nicht), bricht die Liste einfach früher ab — auch das ist ein
     * vergleichbares Ergebnis.
     */
    fun playPerfect(seed: Long, maxHits: Int): List<Snapshot> {
        val game = TimingGame(Random(seed))
        game.start() // READY -> RUNNING
        val out = mutableListOf<Snapshot>()
        var frames = 0
        while (out.size < maxHits && frames < MAX_FRAMES) {
            frames++
            game.update(DT)
            if (game.phase != GamePhase.RUNNING) break
            if (abs(game.relativeToZone()) <= game.zoneHalfWidth * TAP_WINDOW) {
                game.tap()
                out.add(snapshot(game))
            }
        }
        return out
    }

    /** Startet den Lauf und tappt nie wieder — Tod durch Überfahren. */
    fun playPassive(seed: Long): Death {
        val game = TimingGame(Random(seed))
        game.start()
        var frames = 0
        var angle = game.angle
        var zoneCenter = game.zoneCenter
        while (game.phase == GamePhase.RUNNING && frames < MAX_FRAMES) {
            frames++
            angle = game.angle
            zoneCenter = game.zoneCenter
            game.update(DT)
        }
        val framesToDeath = frames
        var settle = 0
        while (game.phase == GamePhase.DYING && settle < MAX_FRAMES) {
            settle++
            game.update(DT)
        }
        return Death(framesToDeath, settle, angle, zoneCenter)
    }

    /** Ergebnis eines Laufs, der mit der Startregel beginnt. */
    data class ReadyRun(
        /** Event des Taps daneben in READY (vor dem Start). */
        val notYetEvent: String,
        /** Was das update() danach aus dem Puffer ausliefert. */
        val notYetDelivered: List<String>,
        /** Zonenmitte nach dem Tap daneben — muss [TimingGame.READY_ZONE_CENTER] bleiben. */
        val notYetZoneCenter: Float,
        /** Frames in READY bis zum Start-Treffer. */
        val framesToHit: Int,
        /** Was das erste update() nach dem Start-Treffer ausliefert. */
        val startEvents: List<String>,
        val snapshots: List<Snapshot>
    )

    /**
     * Startregel mit Perfekt-Treffer: ein Tap daneben (NotYet), dann bis
     * tief in den Kern der READY-Zone warten und tappen. Das ist Treffer 1.
     * Danach geht es weiter wie in [playPerfect], bis [maxHits]
     * Schnappschüsse (inklusive des Start-Treffers) beisammen sind.
     */
    fun playReadyHit(seed: Long, maxHits: Int): ReadyRun {
        val game = TimingGame(Random(seed))
        val notYet = game.tap()
        val notYetZone = game.zoneCenter
        val notYetDelivered = game.update(0f).map { name(it) }
        var frames = 0
        while (game.phase == GamePhase.READY && frames < MAX_FRAMES) {
            frames++
            game.update(DT)
            if (abs(game.relativeToZone()) <= game.zoneHalfWidth * TAP_WINDOW) {
                game.tap()
                break
            }
        }
        val out = mutableListOf(snapshot(game))
        val startEvents = game.update(DT).map { name(it) }
        var guard = 0
        while (out.size < maxHits && guard++ < MAX_FRAMES) {
            game.update(DT)
            if (game.phase != GamePhase.RUNNING) break
            if (abs(game.relativeToZone()) <= game.zoneHalfWidth * TAP_WINDOW) {
                game.tap()
                out.add(snapshot(game))
            }
        }
        return ReadyRun(name(notYet), notYetDelivered, notYetZone, frames, startEvents, out)
    }

    /**
     * Startregel am Zonenrand: Der Bot tappt im ersten Frame, in dem der
     * Punkt in der READY-Zone steht. Das ergibt einen normalen Treffer.
     * Die READY-Zone pulsiert nicht, die Entscheidung hängt an keinem `sin`.
     */
    fun playReadyEdge(seed: Long): ReadyRun {
        val game = TimingGame(Random(seed))
        var frames = 0
        while (game.phase == GamePhase.READY && frames < MAX_FRAMES) {
            frames++
            game.update(DT)
            if (game.isInZone) {
                game.tap()
                break
            }
        }
        val snap = snapshot(game)
        val startEvents = game.update(DT).map { name(it) }
        return ReadyRun("-", emptyList(), game.zoneCenter, frames, startEvents, listOf(snap))
    }

    private fun name(event: GameEvent?): String = event?.let { it::class.simpleName } ?: "-"

    private fun snapshot(game: TimingGame) = Snapshot(
        score = game.score,
        hits = game.hits,
        perfectStreak = game.perfectStreak,
        lastHitPoints = game.lastHitPoints,
        direction = game.direction,
        // Sortiert, weil ein Set in beiden Sprachen eine eigene
        // Reihenfolge hat — verglichen wird der Inhalt.
        twists = game.activeTwists.map { it.name }.sorted(),
        zoneCenter = game.zoneCenter,
        zoneHalfWidth = game.zoneHalfWidth,
        angle = game.angle,
        chainRemaining = game.chainRemaining,
        fakeZoneCenter = if (game.hasFakeZone) game.fakeZoneCenter else null
    )
}
