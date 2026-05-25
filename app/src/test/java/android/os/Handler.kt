package android.os

open class Handler(
    looper: Looper? = null
) {
    open fun post(runnable: Runnable): Boolean {
        runnable.run()
        return true
    }

    open fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
        runnable.run()
        return true
    }

    open fun removeCallbacks(runnable: Runnable) {
        // no-op
    }
}

