package com.example.ergometerapp.session

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists session summaries to private app storage.
 *
 * The storage format is intentionally simple text to keep offline inspection
 * easy and avoid schema migration concerns for this early version.
 */
object SessionStorage {

    /**
     * Saves a summary using a timestamped filename.
     *
     *
     */
    fun save(context: Context, summary: SessionSummary) {
        val formatter =
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)

        val filename =
            "session_${formatter.format(Date())}.txt"

        val content = buildString {
            appendLine("Duration: ${formatTime(summary.durationSeconds)}")
            appendLine("Actual TSS: ${formatOneDecimal(summary.actualTss)}")
            appendLine("Avg power: ${summary.avgPower ?: "--"} W")
            appendLine("Max HR: ${summary.maxHeartRate ?: "--"} bpm")
            appendLine("Distance: ${summary.distanceMeters ?: "--"} m")
            appendLine("Total calories: ${summary.totalEnergyKcal ?: "--"} kcal")
        }

        context.openFileOutput(filename, Context.MODE_PRIVATE)
            .use { it.write(content.toByteArray()) }
    }

    /**
     * Formats a duration as `M:SS` for human readability in stored files.
     */
    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    private fun formatOneDecimal(value: Double?): String {
        return if (value == null) "--" else String.format(Locale.US, "%.1f", value)
    }
}
