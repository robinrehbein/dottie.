package de.robinrehbein.punkt

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    internal var gameThread: GameThread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        gameThread = GameThread(holder, context)
        gameThread?.running = true
        gameThread?.start()
        Log.d("GameView", "Surface Changed: W=$width, H=$height")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        gameThread?.gameLogic?.setScreenSize(width, height)
        Log.d("GameView", "Surface Changed: W=$width, H=$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        gameThread?.running = false
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                Log.d("GameView", "Error joining thread")
            }
        }
        Log.d("GameView", "Surface Destroyed. GameThread finished.")
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val touchX = event.x.toInt()
            val touchY = event.y.toInt()

            gameThread?.gameLogic?.handleTap(touchX.toFloat(), touchY.toFloat())

            return true
        }
        return super.onTouchEvent(event)
    }

}