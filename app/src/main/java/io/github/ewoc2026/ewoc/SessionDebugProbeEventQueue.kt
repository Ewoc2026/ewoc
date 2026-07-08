package io.github.ewoc2026.ewoc

import android.content.Context
import java.io.File

/**
 * Persists a small probe-event stream so live rider signals can be polled
 * directly from app-private storage instead of being reconstructed from logcat.
 *
 * The queue is intentionally append-only JSONL because adb `run-as ... cat`
 * can read it without adding another app-side intent surface, and each event
 * stays inspectable even when operator polling is bursty.
 */
internal class SessionDebugProbeEventQueue(
    private val appContext: Context,
) {
    data class AppendRequest(
        val event: String,
        val screen: String,
        val title: String? = null,
        val message: String? = null,
        val signal: String? = null,
        val signalLabel: String? = null,
        val signalCount: Int? = null,
        val reason: String? = null,
        val powerW: Int? = null,
        val cadenceRpm: Int? = null,
    )

    private val storageRoot: File
        get() = File(appContext.filesDir, storageDirectoryName)

    private val storageFile: File
        get() = File(storageRoot, storageFileName)

    private var nextSequence = 1L

    /**
     * Starts each process from a clean queue so operator polling reflects only
     * the current validation run rather than stale events from an older APK.
     */
    @Synchronized
    fun reset() {
        nextSequence = 1L
        val file = storageFile
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Appends one structured probe event and returns the assigned sequence.
     *
     * Sequence numbers let external pollers request only newer events without
     * relying on wall-clock alignment across adb, the device, and the rider.
     */
    @Synchronized
    fun append(request: AppendRequest): Long {
        val file = storageFile
        val parent = checkNotNull(file.parentFile) {
            "Session debug probe queue file must have a parent directory."
        }
        if (!parent.exists()) {
            check(parent.mkdirs() || parent.exists()) {
                "Could not create session debug probe directory: ${parent.absolutePath}"
            }
        }
        val sequence = nextSequence++
        file.appendText(buildJsonLine(sequence, request), Charsets.UTF_8)
        return sequence
    }

    fun absolutePath(): String = storageFile.absolutePath

    private fun buildJsonLine(
        sequence: Long,
        request: AppendRequest,
    ): String {
        val fields = mutableListOf<String>()
        fields += "\"seq\":$sequence"
        fields += "\"recordedAtEpochMs\":${System.currentTimeMillis()}"
        fields += "\"event\":${jsonString(request.event)}"
        fields += "\"screen\":${jsonString(request.screen)}"
        request.title?.let { fields += "\"title\":${jsonString(it)}" }
        request.message?.let { fields += "\"message\":${jsonString(it)}" }
        request.signal?.let { fields += "\"signal\":${jsonString(it)}" }
        request.signalLabel?.let { fields += "\"signalLabel\":${jsonString(it)}" }
        request.signalCount?.let { fields += "\"signalCount\":$it" }
        request.reason?.let { fields += "\"reason\":${jsonString(it)}" }
        request.powerW?.let { fields += "\"powerW\":$it" }
        request.cadenceRpm?.let { fields += "\"cadenceRpm\":$it" }
        return "{${fields.joinToString(",")}}\n"
    }

    private fun jsonString(value: String): String {
        val escaped = buildString(value.length + 8) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
        return "\"$escaped\""
    }

    private companion object {
        private const val storageDirectoryName = "debug"
        private const val storageFileName = "session-debug-probe-events.jsonl"
    }
}
