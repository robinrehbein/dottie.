package de.robinrehbein.punkt.game.engine

import de.robinrehbein.punkt.game.animations.GameAnimation
import de.robinrehbein.punkt.game.models.Point
import de.robinrehbein.punkt.game.models.HitResult

sealed class GameState {
    object Menu : GameState()
    object Ready : GameState()
    data class Countdown(val number: Int) : GameState()
    object Paused : GameState()
    data class ShowingPoint(val point: Point) : GameState()
    data class WaitingPhase(val targetPoint:Point) : GameState()
    data class WaitingForTap(val targetPoint: Point) : GameState()
    data class Feedback(val hitResult: HitResult, val targetPoint: Point, val animations: List<GameAnimation> = emptyList()) : GameState()
    data class GameOver(val finalScore: Int, val level: Int) : GameState()
}