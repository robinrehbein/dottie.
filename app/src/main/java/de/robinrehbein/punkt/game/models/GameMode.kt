package de.robinrehbein.punkt.game.models

enum class GameMode(
    val displayName: String
) {
    // CLASSIC, GHOST_POINT, MOVING_TARGET, VIBRATING_POINT, BOCCIA
    CLASSIC("Classic"),
    SPEEDRUN("Speedrun")
}