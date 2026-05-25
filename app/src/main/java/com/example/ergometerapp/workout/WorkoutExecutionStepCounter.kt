package com.example.ergometerapp.workout

/**
 * Computes menu-visible step count from the same execution semantics that the runner uses.
 *
 * The count represents executable runner phases, not raw XML step tags:
 * - `IntervalsT` expands to ON/OFF parts for each repeat.
 * - Unsupported mapping cases fall back to legacy-compatible counting so the value still
 *   matches what the session runner will advance through.
 */
object WorkoutExecutionStepCounter {
    /**
     * Returns execution step count for [workout], using [ftpWatts] for strict execution mapping.
     */
    fun count(workout: WorkoutFile, ftpWatts: Int): Int {
        val ftp = ftpWatts.coerceAtLeast(1)
        return when (val mapped = ExecutionWorkoutMapper.map(workout, ftp = ftp)) {
            is MappingResult.Success -> mapped.workout.segments.size
            is MappingResult.Failure -> legacyExpandedCount(workout)
        }
    }

    private fun legacyExpandedCount(workout: WorkoutFile): Int {
        var total = 0L
        workout.steps.forEach { step ->
            total += when (step) {
                is Step.Warmup -> singleStepCount(step.durationSec)
                is Step.Cooldown -> singleStepCount(step.durationSec)
                is Step.SteadyState -> singleStepCount(step.durationSec)
                is Step.Ramp -> singleStepCount(step.durationSec)
                is Step.FreeRide -> singleStepCount(step.durationSec)
                is Step.IntervalsT -> intervalPartCount(step)
                is Step.Unknown -> 0L
            }
            if (total >= Int.MAX_VALUE.toLong()) {
                return Int.MAX_VALUE
            }
        }
        return total.toInt()
    }

    private fun singleStepCount(durationSec: Int?): Long {
        return if (durationSec != null && durationSec > 0) 1L else 0L
    }

    private fun intervalPartCount(step: Step.IntervalsT): Long {
        val repeatCount = step.repeat?.takeIf { it > 0 } ?: return 0L
        val hasOnDuration = step.onDurationSec?.let { it > 0 } == true
        val hasOffDuration = step.offDurationSec?.let { it > 0 } == true
        if (!hasOnDuration || !hasOffDuration) return 0L
        return repeatCount.toLong() * 2L
    }
}
