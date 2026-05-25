package com.example.ergometerapp.workout

import kotlin.math.round

/**
 * Computes workout-planned Training Stress Score (TSS) from mapped execution segments.
 *
 * The calculation is FTP-normalized and deterministic:
 * - `TSS = durationHours * IF^2 * 100`, summed across all segments.
 * - For ramps, IF^2 is integrated over a linear start/end transition.
 * - For HR steady segments, planned TSS currently uses `initialPowerWatts` as
 *   the provisional intensity anchor until live HR control can emit a richer
 *   realized execution estimate.
 *
 * Returns `null` when strict execution mapping fails, because in that case the
 * workout contains unsupported or invalid constructs and a precise planned TSS
 * cannot be guaranteed.
 */
object WorkoutPlannedTssCalculator {
    /**
     * Returns planned TSS for [workout] using [ftpWatts], rounded to one decimal.
     */
    fun calculate(workout: WorkoutFile, ftpWatts: Int): Double? {
        val ftp = ftpWatts.coerceAtLeast(1)
        val mapped = ExecutionWorkoutMapper.map(workout, ftp = ftp)
        if (mapped !is MappingResult.Success) {
            return null
        }

        return calculate(mapped.workout, ftpWatts = ftp)
    }

    /**
     * Returns planned TSS for an already-mapped execution workout.
     */
    fun calculate(workout: ExecutionWorkout, ftpWatts: Int): Double? {
        val ftp = ftpWatts.coerceAtLeast(1)
        val safeFtp = ftp.toDouble()
        var totalTss = 0.0
        workout.segments.forEach { segment ->
            val durationHours = segment.durationSec.toDouble() / 3600.0
            if (durationHours <= 0.0) return@forEach

            val meanIfSquared = when (segment) {
                is ExecutionSegment.Steady -> {
                    val intensityFactor = segment.targetWatts.toDouble() / safeFtp
                    intensityFactor * intensityFactor
                }

                is ExecutionSegment.Ramp -> {
                    val startIf = segment.startWatts.toDouble() / safeFtp
                    val endIf = segment.endWatts.toDouble() / safeFtp
                    (startIf * startIf + startIf * endIf + endIf * endIf) / 3.0
                }

                is ExecutionSegment.HeartRateSteady -> {
                    val intensityFactor = segment.initialPowerWatts.toDouble() / safeFtp
                    intensityFactor * intensityFactor
                }

                is ExecutionSegment.FreeRide -> 0.0
            }

            totalTss += durationHours * meanIfSquared * 100.0
        }

        if (!totalTss.isFinite()) {
            return null
        }

        return round(totalTss * 10.0) / 10.0
    }
}
