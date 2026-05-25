package com.ewo.core

import kotlin.math.abs

/**
 * Non-fatal workout quality checks run on the fully compiled timeline.
 *
 * All checks return [SanitySeverity.WARNING] — they never cause a parse failure.
 * FTP-relative checks are skipped entirely when [ftpWatts] is null or non-positive,
 * since intensity cannot be expressed as a fraction of FTP without a known FTP value.
 *
 * Runs after [EwoWorkoutRepeatExpansionCompiler] so repeats are expanded, FTP-percent
 * targets are already resolved to absolute watts, and the flat step list makes
 * contiguous-block analysis straightforward.
 */
internal object EwoWorkoutSanityValidator {

    // MISSING_WARMUP: inspect the first this many seconds of the workout.
    private const val WARMUP_WINDOW_SEC = 300

    // Below this FTP fraction a step counts as "easy riding" for warmup purposes.
    private const val WARMUP_EASY_THRESHOLD_FTP = 0.65

    // At or above this FTP fraction a step is considered "hard" for warmup purposes.
    private const val WARMUP_HARD_THRESHOLD_FTP = 0.90

    // Warn if the time-weighted average intensity of the first 5 min reaches this fraction.
    private const val WARMUP_AVG_WARN_THRESHOLD_FTP = 0.75

    // A hard step appearing before this many seconds of easy riding triggers a warmup warning.
    private const val WARMUP_REQUIRED_EASY_SEC = 180

    // SPRINT_RECOVERY_TOO_SHORT: thresholds for sprint work and recovery.
    private const val SPRINT_MAX_DURATION_SEC = 30
    private const val SPRINT_INTENSITY_THRESHOLD_FTP = 1.40
    private const val SPRINT_MIN_RECOVERY_SEC = 60

    // HR_INTERVAL_TOO_SHORT: HR-guided segments below this duration warn.
    private const val HR_MIN_INTERVAL_SEC = 90

    // RAMP_TOO_STEEP: ramps exceeding either of these thresholds warn.
    private const val RAMP_ALWAYS_STEEP_WATTS_PER_MIN = 40.0
    private const val RAMP_LONG_STEEP_WATTS_PER_MIN = 25.0
    private const val RAMP_LONG_STEEP_MIN_DURATION_SEC = 300

    // SUSTAINED_HIGH_INTENSITY: multi-tier (intensity_fraction, min_duration_sec) pairs.
    // Applied only to steady power steps; ramps use RAMP_TOO_STEEP separately.
    private val SUSTAINED_TIERS = listOf(
        1.25 to 300,
        1.15 to 1200,
        1.05 to 2400,
    )

    fun check(workout: CompiledEwoWorkout, ftpWatts: Int?): SanityCheckResult {
        val issues = mutableListOf<SanityIssue>()
        val steps = workout.steps

        // HR check does not require FTP.
        checkHrIntervalTooShort(steps, issues)

        if (ftpWatts != null && ftpWatts > 0) {
            checkMissingWarmup(steps, ftpWatts, issues)
            checkSustainedHighIntensity(steps, ftpWatts, issues)
            checkSprintRecoveryTooShort(steps, ftpWatts, issues)
            checkRampTooSteep(steps, issues)
        }

        return SanityCheckResult(issues = issues)
    }

    private fun checkMissingWarmup(
        steps: List<CompiledEwoWorkoutStep>,
        ftpWatts: Int,
        issues: MutableList<SanityIssue>,
    ) {
        var easyAccumulatedSec = 0
        var totalWindowSec = 0
        var weightedWatts = 0.0
        var hardBeforeWarmupReported = false

        for (step in steps) {
            if (step.startOffsetSec >= WARMUP_WINDOW_SEC) break
            val watts = when (step) {
                is CompiledEwoWorkoutStep.PowerSteady -> step.watts
                is CompiledEwoWorkoutStep.PowerRamp -> (step.fromWatts + step.toWatts) / 2
                is CompiledEwoWorkoutStep.HeartRateSteady -> continue
                is CompiledEwoWorkoutStep.FreeRide -> continue
                else -> continue
            }
            val stepEnd = step.startOffsetSec + step.durationSec
            val contributionSec = minOf(stepEnd, WARMUP_WINDOW_SEC) - step.startOffsetSec

            val intensity = watts.toDouble() / ftpWatts

            if (!hardBeforeWarmupReported &&
                intensity >= WARMUP_HARD_THRESHOLD_FTP &&
                easyAccumulatedSec < WARMUP_REQUIRED_EASY_SEC
            ) {
                val easyMin = WARMUP_REQUIRED_EASY_SEC / 60
                issues += SanityIssue(
                    code = SanityIssueCode.MISSING_WARMUP,
                    severity = SanitySeverity.WARNING,
                    message = "Segment '${step.origin.sourceSegmentId}' reaches " +
                        "${formatPercent(intensity * 100)}% FTP before $easyMin min of easy " +
                        "riding (< ${(WARMUP_EASY_THRESHOLD_FTP * 100).toInt()}% FTP) " +
                        "has accumulated. Consider a proper warmup block.",
                    segmentId = step.origin.sourceSegmentId,
                )
                hardBeforeWarmupReported = true
            }

            if (intensity < WARMUP_EASY_THRESHOLD_FTP) {
                easyAccumulatedSec += contributionSec
            }

            totalWindowSec += contributionSec
            weightedWatts += watts * contributionSec
        }

        if (!hardBeforeWarmupReported && totalWindowSec > 0) {
            val avgIntensity = weightedWatts / totalWindowSec / ftpWatts
            if (avgIntensity >= WARMUP_AVG_WARN_THRESHOLD_FTP) {
                issues += SanityIssue(
                    code = SanityIssueCode.MISSING_WARMUP,
                    severity = SanitySeverity.WARNING,
                    message = "Workout begins too aggressively. The first " +
                        "${WARMUP_WINDOW_SEC / 60} minutes average " +
                        "${formatPercent(avgIntensity * 100)}% FTP, which suggests " +
                        "insufficient warmup.",
                    segmentId = steps.firstOrNull()?.origin?.sourceSegmentId,
                )
            }
        }
    }

    private fun checkSustainedHighIntensity(
        steps: List<CompiledEwoWorkoutStep>,
        ftpWatts: Int,
        issues: MutableList<SanityIssue>,
    ) {
        for (step in steps) {
            if (step !is CompiledEwoWorkoutStep.PowerSteady) continue
            val intensity = step.watts.toDouble() / ftpWatts
            for ((threshold, minDurationSec) in SUSTAINED_TIERS) {
                if (intensity > threshold && step.durationSec >= minDurationSec) {
                    issues += SanityIssue(
                        code = SanityIssueCode.SUSTAINED_HIGH_INTENSITY,
                        severity = SanitySeverity.WARNING,
                        message = "Segment '${step.origin.sourceSegmentId}' sustains " +
                            "${formatPercent(intensity * 100)}% FTP for " +
                            "${step.durationSec / 60} min. This is unusually high " +
                            "for that duration.",
                        segmentId = step.origin.sourceSegmentId,
                    )
                    break
                }
            }
        }
    }

    private fun checkSprintRecoveryTooShort(
        steps: List<CompiledEwoWorkoutStep>,
        ftpWatts: Int,
        issues: MutableList<SanityIssue>,
    ) {
        for (i in 0 until steps.size - 1) {
            val work = steps[i]
            val recovery = steps[i + 1]
            if (work !is CompiledEwoWorkoutStep.PowerSteady) continue
            if (recovery !is CompiledEwoWorkoutStep.PowerSteady) continue
            if (work.durationSec > SPRINT_MAX_DURATION_SEC) continue
            val workIntensity = work.watts.toDouble() / ftpWatts
            if (workIntensity < SPRINT_INTENSITY_THRESHOLD_FTP) continue
            if (recovery.durationSec >= SPRINT_MIN_RECOVERY_SEC) continue
            issues += SanityIssue(
                code = SanityIssueCode.SPRINT_RECOVERY_TOO_SHORT,
                severity = SanitySeverity.WARNING,
                message = "Sprint step '${work.origin.sourceSegmentId}' " +
                    "(${work.durationSec}s at ${formatPercent(workIntensity * 100)}% FTP) " +
                    "is followed by only ${recovery.durationSec}s recovery. " +
                    "Consider at least ${SPRINT_MIN_RECOVERY_SEC}s recovery " +
                    "for sprint-length efforts.",
                segmentId = recovery.origin.sourceSegmentId,
            )
        }
    }

    private fun checkHrIntervalTooShort(
        steps: List<CompiledEwoWorkoutStep>,
        issues: MutableList<SanityIssue>,
    ) {
        for (step in steps) {
            if (step !is CompiledEwoWorkoutStep.HeartRateSteady) continue
            if (step.durationSec >= HR_MIN_INTERVAL_SEC) continue
            issues += SanityIssue(
                code = SanityIssueCode.HR_INTERVAL_TOO_SHORT,
                severity = SanitySeverity.WARNING,
                message = "HR-guided segment '${step.origin.sourceSegmentId}' is only " +
                    "${step.durationSec}s long. HR control is typically unreliable " +
                    "below ${HR_MIN_INTERVAL_SEC}s because heart rate responds slowly.",
                segmentId = step.origin.sourceSegmentId,
            )
        }
    }

    private fun checkRampTooSteep(
        steps: List<CompiledEwoWorkoutStep>,
        issues: MutableList<SanityIssue>,
    ) {
        for (step in steps) {
            if (step !is CompiledEwoWorkoutStep.PowerRamp) continue
            if (step.durationSec <= 0) continue
            val deltaWatts = abs(step.toWatts - step.fromWatts).toDouble()
            val wattsPerMin = deltaWatts / (step.durationSec / 60.0)
            val tooSteep = wattsPerMin > RAMP_ALWAYS_STEEP_WATTS_PER_MIN ||
                (wattsPerMin > RAMP_LONG_STEEP_WATTS_PER_MIN &&
                    step.durationSec >= RAMP_LONG_STEEP_MIN_DURATION_SEC)
            if (!tooSteep) continue
            issues += SanityIssue(
                code = SanityIssueCode.RAMP_TOO_STEEP_FOR_DURATION,
                severity = SanitySeverity.WARNING,
                message = "Ramp '${step.origin.sourceSegmentId}' rises by " +
                    "${formatPercent(wattsPerMin)} W/min, which is steeper than " +
                    "typical training ramps.",
                segmentId = step.origin.sourceSegmentId,
            )
        }
    }

    /** KMP-compatible replacement for `"%.0f".format(value)`. */
    private fun formatPercent(value: Double): String {
        val rounded = (value + 0.5).toLong()
        return rounded.toString()
    }
}
