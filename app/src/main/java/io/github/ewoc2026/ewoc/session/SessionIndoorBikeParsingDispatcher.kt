package io.github.ewoc2026.ewoc.session

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

internal interface SessionIndoorBikeParsingDispatcher {
    fun dispatch(task: () -> Unit): Boolean
    fun close()
}

internal class DefaultSessionIndoorBikeParsingDispatcher(
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ftms-indoor-bike-parse").apply { isDaemon = true }
    },
) : SessionIndoorBikeParsingDispatcher {
    override fun dispatch(task: () -> Unit): Boolean {
        return try {
            executorService.execute(task)
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    override fun close() {
        executorService.shutdownNow()
    }
}
