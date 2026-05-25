package android.os

object SystemClock {
    @JvmStatic
    fun uptimeMillis(): Long = System.currentTimeMillis()

    @JvmStatic
    fun elapsedRealtime(): Long = System.currentTimeMillis()
}
