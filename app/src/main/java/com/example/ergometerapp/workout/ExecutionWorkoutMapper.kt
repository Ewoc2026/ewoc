package com.example.ergometerapp.workout

import kotlin.math.round

/**
 * Strict mapper from parsed ZWO content to execution segments.
 * Supports SteadyState, Ramp, Warmup, Cooldown, IntervalsT, and FreeRide.
 */
object ExecutionWorkoutMapper {
    /**
     * Maps a parsed workout and explicit FTP to a deterministic execution model.
     *
     * Every step is validated, and all discovered errors are returned together.
     */
    fun map(workoutFile: WorkoutFile, ftp: Int): MappingResult {
        val errors = mutableListOf<MappingError>()
        val segments = mutableListOf<ExecutionSegment>()

        val ftpValid = ftp > 0
        if (!ftpValid) {
            return MappingResult.Failure(
                errors = listOf(
                    MappingError(
                        code = MappingErrorCode.INVALID_FTP,
                        message = "FTP must be greater than 0.",
                    ),
                ),
            )
        }

        workoutFile.steps.forEachIndexed { stepIndex, step ->
            when (step) {
                is Step.SteadyState -> mapSteadyState(
                    stepIndex = stepIndex,
                    step = step,
                    ftp = ftp,
                    ftpValid = ftpValid,
                    segments = segments,
                    errors = errors,
                )

                is Step.Ramp -> mapRamp(
                    stepIndex = stepIndex,
                    stepType = "Ramp",
                    durationSec = step.durationSec,
                    powerLow = step.powerLow,
                    powerHigh = step.powerHigh,
                    cadence = step.cadence,
                    ftp = ftp,
                    ftpValid = ftpValid,
                    segments = segments,
                    errors = errors,
                )

                is Step.Warmup -> mapRamp(
                    stepIndex = stepIndex,
                    stepType = "Warmup",
                    durationSec = step.durationSec,
                    powerLow = step.powerLow,
                    powerHigh = step.powerHigh,
                    cadence = step.cadence,
                    ftp = ftp,
                    ftpValid = ftpValid,
                    segments = segments,
                    errors = errors,
                )

                is Step.Cooldown -> {
                    val orderedPowerLow =
                        if (step.powerLow != null && step.powerHigh != null) maxOf(step.powerLow, step.powerHigh)
                        else step.powerLow
                    val orderedPowerHigh =
                        if (step.powerLow != null && step.powerHigh != null) minOf(step.powerLow, step.powerHigh)
                        else step.powerHigh
                    mapRamp(
                        stepIndex = stepIndex,
                        stepType = "Cooldown",
                        durationSec = step.durationSec,
                        powerLow = orderedPowerLow,
                        powerHigh = orderedPowerHigh,
                        cadence = step.cadence,
                        ftp = ftp,
                        ftpValid = ftpValid,
                        segments = segments,
                        errors = errors,
                    )
                }

                is Step.IntervalsT -> mapIntervalsT(
                    stepIndex = stepIndex,
                    step = step,
                    ftp = ftp,
                    ftpValid = ftpValid,
                    segments = segments,
                    errors = errors,
                )

                is Step.FreeRide -> mapFreeRide(
                    stepIndex = stepIndex,
                    step = step,
                    segments = segments,
                    errors = errors,
                )

                is Step.Unknown,
                -> errors += MappingError(
                    code = MappingErrorCode.UNSUPPORTED_STEP,
                    message = "Unsupported step type: ${stepTypeName(step)}.",
                    stepIndex = stepIndex,
                    stepType = stepTypeName(step),
                )
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
        if (totalDurationSec == null) {
            return MappingResult.Failure(
                errors = listOf(
                    MappingError(
                        code = MappingErrorCode.TOTAL_DURATION_OVERFLOW,
                        message = "Total workout duration exceeds Int range.",
                    ),
                ),
            )
        }

        return MappingResult.Success(
            workout = ExecutionWorkout(
                name = workoutFile.name.orEmpty(),
                description = workoutFile.description.orEmpty(),
                author = workoutFile.author.orEmpty(),
                tags = workoutFile.tags.toList(),
                segments = segments.toList(),
                totalDurationSec = totalDurationSec,
            ),
        )
    }

    private fun mapSteadyState(
        stepIndex: Int,
        step: Step.SteadyState,
        ftp: Int,
        ftpValid: Boolean,
        segments: MutableList<ExecutionSegment>,
        errors: MutableList<MappingError>,
    ) {
        val errorCountBefore = errors.size
        val stepType = "SteadyState"

        val durationSec = when {
            step.durationSec == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_DURATION,
                    message = "Duration is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            step.durationSec <= 0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_DURATION,
                    message = "Duration must be greater than 0.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> step.durationSec
        }

        val power = when {
            step.power == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_POWER,
                    message = "Power is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            !step.power.isFinite() || step.power < 0.0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_POWER,
                    message = "Power must be finite and non-negative.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> step.power
        }

        if (!ftpValid || errors.size != errorCountBefore) {
            return
        }

        val targetWatts = roundToWatts(
            ratio = power!!,
            ftp = ftp,
            stepIndex = stepIndex,
            stepType = stepType,
            errors = errors,
        ) ?: return

        segments += ExecutionSegment.Steady(
            sourceStepIndex = stepIndex,
            durationSec = durationSec!!,
            targetWatts = targetWatts,
            cadence = cadenceTarget(step.cadence),
        )
    }

    private fun mapRamp(
        stepIndex: Int,
        stepType: String,
        durationSec: Int?,
        powerLow: Double?,
        powerHigh: Double?,
        cadence: Int?,
        ftp: Int,
        ftpValid: Boolean,
        segments: MutableList<ExecutionSegment>,
        errors: MutableList<MappingError>,
    ) {
        val errorCountBefore = errors.size

        val durationSecValid = when {
            durationSec == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_DURATION,
                    message = "Duration is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            durationSec <= 0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_DURATION,
                    message = "Duration must be greater than 0.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> durationSec
        }

        val powerLowValid = when {
            powerLow == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_POWER_LOW,
                    message = "PowerLow is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            !powerLow.isFinite() || powerLow < 0.0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_POWER,
                    message = "PowerLow must be finite and non-negative.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> powerLow
        }

        val powerHighValid = when {
            powerHigh == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_POWER_HIGH,
                    message = "PowerHigh is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            !powerHigh.isFinite() || powerHigh < 0.0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_POWER,
                    message = "PowerHigh must be finite and non-negative.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> powerHigh
        }

        if (!ftpValid || errors.size != errorCountBefore) {
            return
        }

        val startWatts = roundToWatts(
            ratio = powerLowValid!!,
            ftp = ftp,
            stepIndex = stepIndex,
            stepType = stepType,
            errors = errors,
        )
        val endWatts = roundToWatts(
            ratio = powerHighValid!!,
            ftp = ftp,
            stepIndex = stepIndex,
            stepType = stepType,
            errors = errors,
        )
        if (startWatts == null || endWatts == null) {
            return
        }

        segments += ExecutionSegment.Ramp(
            sourceStepIndex = stepIndex,
            durationSec = durationSecValid!!,
            startWatts = startWatts,
            endWatts = endWatts,
            cadence = cadenceTarget(cadence),
        )
    }

    private fun mapIntervalsT(
        stepIndex: Int,
        step: Step.IntervalsT,
        ftp: Int,
        ftpValid: Boolean,
        segments: MutableList<ExecutionSegment>,
        errors: MutableList<MappingError>,
    ) {
        val errorCountBefore = errors.size
        val stepType = "IntervalsT"

        val repeatCount = when {
            step.repeat == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_REPEAT,
                    message = "Repeat is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            step.repeat <= 0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_REPEAT,
                    message = "Repeat must be greater than 0.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> step.repeat
        }

        val onDurationSec = when {
            step.onDurationSec == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_DURATION,
                    message = "OnDuration is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            step.onDurationSec <= 0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_DURATION,
                    message = "OnDuration must be greater than 0.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> step.onDurationSec
        }

        val offDurationSec = when {
            step.offDurationSec == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_DURATION,
                    message = "OffDuration is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            step.offDurationSec <= 0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_DURATION,
                    message = "OffDuration must be greater than 0.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> step.offDurationSec
        }

        val onPower = when {
            step.onPower == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_POWER,
                    message = "OnPower is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            !step.onPower.isFinite() || step.onPower < 0.0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_POWER,
                    message = "OnPower must be finite and non-negative.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> step.onPower
        }

        val offPower = when {
            step.offPower == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_POWER,
                    message = "OffPower is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            !step.offPower.isFinite() || step.offPower < 0.0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_POWER,
                    message = "OffPower must be finite and non-negative.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> step.offPower
        }

        if (!ftpValid || errors.size != errorCountBefore) {
            return
        }

        val onTargetWatts = roundToWatts(
            ratio = onPower!!,
            ftp = ftp,
            stepIndex = stepIndex,
            stepType = stepType,
            errors = errors,
        )
        val offTargetWatts = roundToWatts(
            ratio = offPower!!,
            ftp = ftp,
            stepIndex = stepIndex,
            stepType = stepType,
            errors = errors,
        )
        if (onTargetWatts == null || offTargetWatts == null) {
            return
        }

        val cadence = cadenceTarget(step.cadence)
        repeat(repeatCount!!) {
            segments += ExecutionSegment.Steady(
                sourceStepIndex = stepIndex,
                durationSec = onDurationSec!!,
                targetWatts = onTargetWatts,
                cadence = cadence,
                intervalMetadata = IntervalSegmentMetadata(
                    phase = IntervalPhase.ON,
                    repIndex = it + 1,
                    repTotal = repeatCount,
                ),
            )
            segments += ExecutionSegment.Steady(
                sourceStepIndex = stepIndex,
                durationSec = offDurationSec!!,
                targetWatts = offTargetWatts,
                cadence = cadence,
                intervalMetadata = IntervalSegmentMetadata(
                    phase = IntervalPhase.OFF,
                    repIndex = it + 1,
                    repTotal = repeatCount,
                ),
            )
        }
    }

    private fun mapFreeRide(
        stepIndex: Int,
        step: Step.FreeRide,
        segments: MutableList<ExecutionSegment>,
        errors: MutableList<MappingError>,
    ) {
        val stepType = "FreeRide"
        val durationSec = when {
            step.durationSec == null -> {
                errors += MappingError(
                    code = MappingErrorCode.MISSING_DURATION,
                    message = "Duration is required.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            step.durationSec <= 0 -> {
                errors += MappingError(
                    code = MappingErrorCode.INVALID_DURATION,
                    message = "Duration must be greater than 0.",
                    stepIndex = stepIndex,
                    stepType = stepType,
                )
                null
            }

            else -> step.durationSec
        }

        if (durationSec == null) {
            return
        }

        segments += ExecutionSegment.FreeRide(
            sourceStepIndex = stepIndex,
            durationSec = durationSec,
            cadence = cadenceTarget(step.cadence),
        )
    }

    private fun roundToWatts(
        ratio: Double,
        ftp: Int,
        stepIndex: Int,
        stepType: String,
        errors: MutableList<MappingError>,
    ): Int? {
        val roundedWatts = round(ratio * ftp.toDouble())
        if (
            !roundedWatts.isFinite() ||
            roundedWatts < Int.MIN_VALUE.toDouble() ||
            roundedWatts > Int.MAX_VALUE.toDouble()
        ) {
            errors += MappingError(
                code = MappingErrorCode.WATTS_OVERFLOW,
                message = "Rounded watts value is outside Int range.",
                stepIndex = stepIndex,
                stepType = stepType,
            )
            return null
        }
        return roundedWatts.toInt()
    }

    private fun cadenceTarget(cadence: Int?): CadenceTarget {
        return if (cadence != null && cadence > 0) {
            CadenceTarget.FixedCadence(cadence)
        } else {
            CadenceTarget.AnyCadence
        }
    }

    private fun sumDurationSec(segments: List<ExecutionSegment>): Int? {
        var total = 0L
        for (segment in segments) {
            total += segment.durationSec.toLong()
            if (total > Int.MAX_VALUE) return null
        }
        return total.toInt()
    }

    private fun stepTypeName(step: Step): String = when (step) {
        is Step.Warmup -> "Warmup"
        is Step.Cooldown -> "Cooldown"
        is Step.SteadyState -> "SteadyState"
        is Step.Ramp -> "Ramp"
        is Step.IntervalsT -> "IntervalsT"
        is Step.FreeRide -> "FreeRide"
        is Step.Unknown -> "Unknown(${step.tagName})"
    }
}
