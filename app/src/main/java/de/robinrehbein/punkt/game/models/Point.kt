package de.robinrehbein.punkt.game.models

data class Point(val x: Float, val y: Float, val radius: Float, val appearanceTime: Long = System.currentTimeMillis(), val disappearanceTime: Long = 0L) {
    val isVisible: Boolean
        get() = System.currentTimeMillis() in appearanceTime..disappearanceTime
}