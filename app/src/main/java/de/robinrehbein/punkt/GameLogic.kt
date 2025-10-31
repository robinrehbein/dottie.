package de.robinrehbein.punkt

import android.content.Context
import android.graphics.PointF
import android.util.Log
import kotlin.math.sqrt


class GameLogic(context: Context) {

    private val VISIBLE_DURATION = 800L
    private val WAIT_DURATION = 100L
    private val PERFECT_RADIUS_TOLERANCE = 50f


    var score: Int = 0
    val level: Int = 1

    var pointX: Float = 0f
    var pointY: Float = 0f
    var pointRadius: Float = 0f

    private var lastStateChangeTime: Long = 0L
    private var targetPosition = PointF(0f, 0f)

    enum class GameState {
        WAITING_FOR_SURFACE_SIZE,
        SHOWING_POINT,
        AWAITING_TAP
    }

    var currentState: GameState = GameState.WAITING_FOR_SURFACE_SIZE
    var isPointVisible: Boolean = false

    var screenWidth: Int = 0
    var screenHeight: Int = 0

    fun setScreenSize(width: Int, height: Int) {
        if (screenWidth != width || screenHeight != height) {
            screenWidth = width
            screenHeight = height

            if (currentState == GameState.WAITING_FOR_SURFACE_SIZE) {
                startNewRound()
            }
        }
    }

    fun update() {
        if (currentState == GameState.WAITING_FOR_SURFACE_SIZE) return

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - lastStateChangeTime

        when (currentState) {

            GameState.SHOWING_POINT -> {
                if (elapsedTime >= VISIBLE_DURATION) {
                    isPointVisible = false
                    currentState = GameState.AWAITING_TAP
                    lastStateChangeTime = currentTime
                    Log.d("GameLogic", "Point Hidden. Awaiting Tap.")
                }
            }

            GameState.AWAITING_TAP -> {
                if (elapsedTime >= WAIT_DURATION) {
                    Log.d("GameLogic", "Timeout! Tap missed.")
                    score--
                    startNewRound()
                }
            }

            else -> { /* WAITING_FOR_SURFACE_SIZE ignoriert */ }
        }
    }

    fun handleTap(tapX: Float, tapY: Float) {
        if (currentState == GameState.AWAITING_TAP) {

            val deltaX = tapX - targetPosition.x
            val deltaY = tapY - targetPosition.y
            val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

            if (distance <= PERFECT_RADIUS_TOLERANCE) {
                score += 10
                Log.d("GameLogic", "Perfect Hit! Distance: $distance. New Score: $score")
            } else {
                Log.d("GameLogic", "Missed! Distance: $distance. Score reset.")
                score = 0
            }

            startNewRound()
        } else {
            Log.d("GameLogic", "Tap registered, but outside AWAITING_TAP phase.")
        }
    }

    private fun startNewRound() {
        if (screenWidth == 0 || screenHeight == 0) {
            currentState = GameState.WAITING_FOR_SURFACE_SIZE
            return
        }

        val padding = pointRadius * 2

        val newX = padding + Math.random().toFloat() * (screenWidth - 2 * padding)
        val newY = padding + Math.random().toFloat() * (screenHeight - 2 * padding)

        pointX = newX
        pointY = newY
        targetPosition.set(newX, newY)

        isPointVisible = true
        currentState = GameState.SHOWING_POINT
        lastStateChangeTime = System.currentTimeMillis()

        Log.d("GameLogic", "Starting new round at X: $pointX, Y: $pointY")

        // TODO: Hier müssten Sie Level-Anpassungen (kleinerer Punkt, längere Wartezeit) einfügen
    }
}