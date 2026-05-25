package com.ewo.editor.model

/**
 * Target specification as edited by the user. May hold temporarily invalid values
 * (e.g. watts = 0 mid-keystroke). Validation markers catch these — they do not
 * prevent editing.
 */
sealed class EditorTarget {
    data class Power(val watts: Int) : EditorTarget()
    data class FtpPercent(val fraction: Double) : EditorTarget()
    data class HeartRate(val lowBpm: Int, val highBpm: Int) : EditorTarget()
    data class HeartRateRelative(
        val reference: com.ewo.core.HrReference,
        val lowFraction: Double,
        val highFraction: Double,
    ) : EditorTarget()
}

/**
 * Optional cadence guidance carried for editor display/export only.
 */
data class EditorCadenceRange(val low: Int, val high: Int)

/**
 * Localized text with a required default and optional per-locale translations.
 * Mirrors the canonical `.ewo` v1.6+ localized-text shape used by
 * `title_localized` and `description_localized`.
 */
data class EditorLocalizedText(
    val defaultText: String,
    val translations: Map<String, String>,
)

/**
 * Optional authored control bounds for heart-rate-guided workouts.
 */
data class EditorControl(
    val initialPowerWatts: Int,
    val minPowerWatts: Int,
    val maxPowerWatts: Int,
    val signalLossPowerWatts: Int,
    val hrUpperCapBpm: Int,
)

/**
 * Authored message within a workout or segment.
 */
enum class EditorMessageAnchor {
    START,
    END,
}

/**
 * Stable message timing shape used across editor commands and canonical export.
 */
data class EditorMessageTiming(
    val anchor: EditorMessageAnchor = EditorMessageAnchor.START,
    val offsetSec: Int = 0,
)

data class EditorMessage(
    val nodeId: EditorNodeId,
    val kind: String,
    val timing: EditorMessageTiming,
    val defaultText: String,
    val translations: Map<String, String>,
)
