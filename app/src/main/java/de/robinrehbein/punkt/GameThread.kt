package de.robinrehbein.punkt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.SurfaceHolder

class GameThread(private val surfaceHolder: SurfaceHolder,
                 context: Context
) : Thread() {

    @Volatile
    var running = false

    val gameLogic: GameLogic = GameLogic(context)

    private val pointPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }


    override fun run() {
        var canvas: Canvas? = null

        while (running) {
            gameLogic.update()
            canvas = null
            try {
                canvas = surfaceHolder.lockCanvas()
                synchronized(surfaceHolder) {
                    if (canvas != null) {
                        drawGame(canvas)
                    }
                }
            } catch (e: Exception) {
                Log.e("GameThread", "Error locking canvas: ${e.message}")
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e("GameThread", "Error unlocking canvas: ${e.message}")
                    }
                }
            }

            try {
                sleep(10)
            } catch (e: InterruptedException) {
                Log.d("GameThread", "Error sleeping: ${e.message}")
            }
        }
    }

    private fun drawGame(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        if (gameLogic.isPointVisible) {
            val x = gameLogic.pointX
            val y = gameLogic.pointY
            val radius = gameLogic.pointRadius

            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), pointPaint)
        }

        pointPaint.color = Color.GREEN
        pointPaint.textSize = 50f
        canvas.drawText("Score: ${gameLogic.score}", 40f, 100f, pointPaint)

        pointPaint.color = Color.BLACK
    }
}