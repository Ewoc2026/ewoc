package com.example.ergometerapp.workout

/**
 * Parsed representation of a Zwift `.zwo` workout file.
 */
data class WorkoutFile(
    val name: String?,
    val description: String?,
    val author: String?,
    val tags: List<String>,
    val steps: List<Step>,
    val textEvents: List<WorkoutTextEvent> = emptyList(),
    val heartRateTargets: WorkoutHeartRateTargets? = null,
)

/**
 * Optional workout-level heart-rate guidance parsed from `.zwo` metadata tags.
 *
 * The values are preserved exactly for import compatibility even though current
 * runtime execution still follows the step power targets only.
 */
data class WorkoutHeartRateTargets(
    val targetHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
)

/**
 * Time-based text cue parsed from a ZWO workout.
 *
 * The event starts at [timeOffsetSec] from workout start. When [durationSec] is null,
 * caller-defined fallback duration should be used.
 */
data class WorkoutTextEvent(
    val timeOffsetSec: Int,
    val message: String,
    val durationSec: Int?,
)

/**
 * Steps preserve the file's units; numeric fields are nullable when attributes are missing.
 */
sealed class Step {
    data class Warmup(
        val durationSec: Int?,
        val powerLow: Double?,
        val powerHigh: Double?,
        val cadence: Int?,
    ) : Step()

    data class Cooldown(
        val durationSec: Int?,
        val powerLow: Double?,
        val powerHigh: Double?,
        val cadence: Int?,
    ) : Step()

    data class SteadyState(
        val durationSec: Int?,
        val power: Double?,
        val cadence: Int?,
    ) : Step()

    data class Ramp(
        val durationSec: Int?,
        val powerLow: Double?,
        val powerHigh: Double?,
        val cadence: Int?,
    ) : Step()

    data class IntervalsT(
        val onDurationSec: Int?,
        val offDurationSec: Int?,
        val onPower: Double?,
        val offPower: Double?,
        val repeat: Int?,
        val cadence: Int?,
    ) : Step()

    data class FreeRide(
        val durationSec: Int?,
        val cadence: Int?,
    ) : Step()

    data class Unknown(
        val tagName: String,
        val attributes: Map<String, String>,
    ) : Step()
}
