package de.robinrehbein.punkt.game

import kotlin.math.PI

/**
 * Markierungen der späten Twists auf der Bahn (ab v2.36) — welche ein
 * Bahnplatz trägt und in welcher Farbe. Telefon und Uhr zeichnen beide
 * daraus, deshalb steht es hier und nicht in einem der Renderer.
 *
 * - [FAST] / [SLOW]: das Tempo-Band vor der Zone ([TimingGame.tempoStart]
 *   bis [TimingGame.tempoEnd]) — schneller oder langsamer.
 * - [ONWARD]: die [MIRROR_BLOCKS] Plätze hinter einer SPIEGEL-Zone — dort
 *   geht es nach dem Treffer weiter, ohne Wende.
 */
enum class TrackMark { FAST, SLOW, ONWARD }

object TrackMarks {

    /** So viele Bahnplätze hinter einer SPIEGEL-Zone tragen die Markierung. */
    const val MIRROR_BLOCKS = 5

    /**
     * Flächenfarben (ARGB). Warm heißt schneller, kalt heißt langsamer,
     * perlweiß heißt „weiter". Keine kommt dem Grün der Zone nahe.
     */
    fun face(mark: TrackMark): Long = when (mark) {
        TrackMark.FAST -> 0xFFFF9A3C
        TrackMark.SLOW -> 0xFF9FD8FF
        TrackMark.ONWARD -> 0xFFF4F0FF
    }

    /**
     * Welche Markierung der Bahnplatz bei [angle] gerade trägt — null für
     * keine. Nur im laufenden Spiel und im Freeze nach dem Tod, damit man
     * dort noch sieht, was los war. [segments] ist die Zahl der
     * Bahnplätze des Renderers (für die Länge der SPIEGEL-Markierung).
     */
    fun at(game: TimingGame, angle: Float, segments: Int): TrackMark? {
        if (game.phase != GamePhase.RUNNING && game.phase != GamePhase.DYING) return null
        val rel = TimingGame.wrapToPi(game.direction * (angle - game.zoneCenter))
        if (game.hasTempo && rel >= game.tempoStart() && rel < game.tempoEnd()) {
            return if (game.isTempoFast) TrackMark.FAST else TrackMark.SLOW
        }
        if (game.isMirrorZone) {
            val h = game.effectiveZoneHalf()
            val span = MIRROR_BLOCKS * 2f * PI.toFloat() / segments
            if (rel > h && rel <= h + span) return TrackMark.ONWARD
        }
        return null
    }
}
