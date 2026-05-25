package android.os

class Looper private constructor(
    private val thread: Thread
) {
    companion object {
        private val mainLooper = Looper(Thread.currentThread())

        @JvmStatic
        fun getMainLooper(): Looper = mainLooper

        @JvmStatic
        fun myLooper(): Looper = mainLooper
    }

    fun getThread(): Thread = thread
}
