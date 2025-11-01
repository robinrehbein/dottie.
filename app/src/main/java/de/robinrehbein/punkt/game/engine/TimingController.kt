package de.robinrehbein.punkt.game.engine

import kotlinx.coroutines.*

class TimingController {
    private var job: Job? = null

    suspend fun waitForDuration(duration: Long): Boolean {
        return try {
            delay(duration)
            true
        } catch (e: CancellationException) {
            false
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}