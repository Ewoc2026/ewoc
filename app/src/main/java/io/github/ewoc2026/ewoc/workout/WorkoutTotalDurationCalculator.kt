package io.github.ewoc2026.ewoc.workout

/**
 * Computes total workout duration from mapped execution segments.
 *
 * Uses the same execution mapping pipeline as the runner so the displayed
 * duration matches what will actually execute. Falls back to summing raw
 * step durations when strict mapping fails.
 */
object WorkoutTotalDurationCalculator {
    /**
     * Returns total duration in seconds for [workout], or `null` when duration
     * cannot be determined (e.g. open-ended free-ride with no duration).
     */
    fun calculate(workout: WorkoutFile, ftpWatts: Int): Int? {
        val ftp = ftpWatts.coerceAtLeast(1)
        return when (val mapped = ExecutionWorkoutMapper.map(workout, ftp = ftp)) {
            is MappingResult.Success -> mapped.workout.totalDurationSec
            is MappingResult.Failure -> legacyTotalDurationSec(workout)
        }
    }

    private fun legacyTotalDurationSec(workout: WorkoutFile): Int? {
        var total = 0L
        for (step in workout.steps) {
            val stepDuration = when (step) {
                is Step.Warmup -> step.durationSec?.toLong()
                is Step.Cooldown -> step.durationSec?.toLong()
                is Step.SteadyState -> step.durationSec?.toLong()
                is Step.Ramp -> step.durationSec?.toLong()
                is Step.FreeRide -> step.durationSec?.toLong()
                is Step.IntervalsT -> intervalDurationSec(step)
                is Step.Unknown -> null
            } ?: return null
            if (stepDuration <= 0) continue
            total += stepDuration
            if (total > Int.MAX_VALUE) return null
        }
        return total.toInt()
    }

    private fun intervalDurationSec(step: Step.IntervalsT): Long? {
        val repeat = step.repeat?.takeIf { it > 0 } ?: return null
        val onDur = step.onDurationSec?.takeIf { it > 0 } ?: return null
        val offDur = step.offDurationSec?.takeIf { it > 0 } ?: return null
        return repeat.toLong() * (onDur.toLong() + offDur.toLong())
    }
}
