package io.github.ewoc2026.ewoc.session

import java.util.Locale

/**
 * One-shot mock-trainer telemetry scenarios used to make manual validation reproducible.
 */
internal enum class MockTrainerDebugScenario(
    val wireName: String,
) {
    WAITING_START_AND_PAUSE_CAPTURE("waiting_start_and_pause_capture"),
    ;

    companion object {
        fun fromWireName(rawScenario: String?): MockTrainerDebugScenario? {
            val normalized = rawScenario
                ?.trim()
                ?.lowercase(Locale.US)
                ?: return null
            return when (normalized) {
                WAITING_START_AND_PAUSE_CAPTURE.wireName,
                "waiting_start_pause_capture" -> WAITING_START_AND_PAUSE_CAPTURE

                else -> null
            }
        }
    }
}
