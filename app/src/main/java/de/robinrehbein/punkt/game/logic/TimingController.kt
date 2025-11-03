package de.robinrehbein.punkt.game.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

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