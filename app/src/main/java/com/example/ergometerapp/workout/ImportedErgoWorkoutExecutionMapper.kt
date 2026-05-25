package com.example.ergometerapp.workout

/**
 * Maps imported `ergo_workout` timelines into the native execution model.
 *
 * The mapper intentionally accepts only absolute-power steps for now so session
 * execution can ship without implying heart-rate control behavior that does not
 * yet exist in the runner or trainer protocol layer.
 */
object ImportedErgoWorkoutExecutionMapper {
    /**
     * Converts [workout] to deterministic execution segments or returns stable
     * mapper errors when the imported timeline still needs unsupported control.
     */
    fun map(workout: ImportedErgoWorkout): MappingResult {
        val segments = mutableListOf<ExecutionSegment>()
        val errors = mutableListOf<MappingError>()

        workout.steps.forEach { step ->
            when (step) {
                is ImportedErgoWorkoutStep.PowerSteady -> {
                    if (step.durationSec <= 0) {
                        errors += MappingError(
                            code = MappingErrorCode.INVALID_DURATION,
                            message = "Duration must be greater than 0.",
                            stepIndex = step.stepIndex,
                            stepType = "PowerSteady",
                        )
                    } else {
                        segments += ExecutionSegment.Steady(
                            sourceStepIndex = step.stepIndex,
                            durationSec = step.durationSec,
                            targetWatts = step.watts,
                            cadence = CadenceTarget.AnyCadence,
                        )
                    }
                }

                is ImportedErgoWorkoutStep.PowerRamp -> {
                    if (step.durationSec <= 0) {
                        errors += MappingError(
                            code = MappingErrorCode.INVALID_DURATION,
                            message = "Duration must be greater than 0.",
                            stepIndex = step.stepIndex,
                            stepType = "PowerRamp",
                        )
                    } else {
                        segments += ExecutionSegment.Ramp(
                            sourceStepIndex = step.stepIndex,
                            durationSec = step.durationSec,
                            startWatts = step.fromWatts,
                            endWatts = step.toWatts,
                            cadence = CadenceTarget.AnyCadence,
                        )
                    }
                }

                is ImportedErgoWorkoutStep.HeartRateSteady -> {
                    if (step.durationSec <= 0) {
                        errors += MappingError(
                            code = MappingErrorCode.INVALID_DURATION,
                            message = "Duration must be greater than 0.",
                            stepIndex = step.stepIndex,
                            stepType = "HeartRateSteady",
                        )
                    } else {
                        when (
                            val resolution = ImportedErgoWorkoutExecutionPolicy.resolveHeartRateExecutionPolicy(
                                workout = workout,
                                step = step,
                            )
                        ) {
                            ImportedHrExecutionPolicyResolution.MissingCanonicalControl -> {
                                errors += MappingError(
                                    code = MappingErrorCode.UNSUPPORTED_HEART_RATE_TARGET,
                                    message = "Heart-rate-controlled imported steps are preserved at import time, but canonical workout-level control metadata is required before runtime policy can be resolved.",
                                    stepIndex = step.stepIndex,
                                    stepType = "HeartRateSteady",
                                )
                            }

                            is ImportedHrExecutionPolicyResolution.Available -> {
                                segments += ExecutionSegment.HeartRateSteady(
                                    sourceStepIndex = step.stepIndex,
                                    durationSec = step.durationSec,
                                    targetLowBpm = resolution.policy.targetLowBpm,
                                    targetHighBpm = resolution.policy.targetHighBpm,
                                    initialPowerWatts = resolution.policy.initialPowerWatts,
                                    minPowerWatts = resolution.policy.minPowerWatts,
                                    maxPowerWatts = resolution.policy.maxPowerWatts,
                                    signalLossPowerWatts = resolution.policy.signalLossPowerWatts,
                                    hrUpperCapBpm = resolution.policy.hrUpperCapBpm,
                                    cadence = CadenceTarget.AnyCadence,
                                )
                            }
                        }
                    }
                }

                is ImportedErgoWorkoutStep.FreeRide -> {
                    if (step.durationSec <= 0) {
                        errors += MappingError(
                            code = MappingErrorCode.INVALID_DURATION,
                            message = "Duration must be greater than 0.",
                            stepIndex = step.stepIndex,
                            stepType = "FreeRide",
                        )
                    } else {
                        segments += ExecutionSegment.FreeRide(
                            sourceStepIndex = step.stepIndex,
                            durationSec = step.durationSec,
                            cadence = CadenceTarget.AnyCadence,
                        )
                    }
                }
            }
        }

        if (errors.isNotEmpty()) {
            return MappingResult.Failure(errors = errors.toList())
        }

        if (segments.isEmpty()) {
            return MappingResult.Failure(
                errors = listOf(
                    MappingError(
                        code = MappingErrorCode.NO_SUPPORTED_STEPS,
                        message = "Workout does not contain supported executable steps.",
                    ),
                ),
            )
        }

        val totalDurationSec = sumDurationSec(segments)
            ?: return MappingResult.Failure(
                errors = listOf(
                    MappingError(
                        code = MappingErrorCode.TOTAL_DURATION_OVERFLOW,
                        message = "Total workout duration exceeds Int range.",
                    ),
                ),
            )

        return MappingResult.Success(
            workout = ExecutionWorkout(
                name = workout.title,
                description = workout.description.orEmpty(),
                author = "",
                tags = emptyList(),
                segments = segments.toList(),
                totalDurationSec = totalDurationSec,
            ),
        )
    }

    private fun sumDurationSec(segments: List<ExecutionSegment>): Int? {
        var total = 0L
        segments.forEach { segment ->
            total += segment.durationSec.toLong()
            if (total > Int.MAX_VALUE.toLong()) {
                return null
            }
        }
        return total.toInt()
    }
}
