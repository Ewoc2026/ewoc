package com.ewo.core

import kotlin.math.roundToInt

/**
 * Flattens normalized canonical `.ewo` segments into a deterministic runtime timeline while
 * preserving authored segment identity for repeat-expanded steps.
 *
 * [ftpWatts] is required when the workout contains any `ftp_percent` targets; if absent and
 * a segment requests FTP resolution the compiler fails with [EwoWorkoutValidationErrorCode.FTP_REQUIRED_FOR_FTP_PERCENT].
 */
internal object EwoWorkoutRepeatExpansionCompiler {
    private const val FTP_MIN_WATTS = 80
    private const val FTP_MAX_WATTS = 600

    fun compile(workout: NormalizedEwoWorkout, context: EwoCompileContext): CompiledEwoWorkout {
        val ftpWatts = context.ftpWatts
        val compiledSteps = mutableListOf<CompiledEwoWorkoutStep>()
        var nextOffsetSec = 0

        fun emitPowerSteady(
            segmentId: String,
            label: String?,
            note: String?,
            durationSec: Int,
            watts: Int,
            messages: List<EwoMessage>,
            cadence: EwoCadenceRange?,
            enclosingRepeatSegmentId: String?,
            repeatIterationIndex: Int?,
        ) {
            compiledSteps += CompiledEwoWorkoutStep.PowerSteady(
                stepIndex = compiledSteps.size,
                startOffsetSec = nextOffsetSec,
                durationSec = durationSec,
                watts = watts,
                messages = messages,
                cadence = cadence,
                origin = CompiledEwoSegmentOrigin(
                    sourceSegmentId = segmentId,
                    sourceSegmentLabel = label,
                    sourceSegmentNote = note,
                    enclosingRepeatSegmentId = enclosingRepeatSegmentId,
                    repeatIterationIndex = repeatIterationIndex,
                ),
            )
            nextOffsetSec = safeAdd(nextOffsetSec, durationSec)
        }

        fun emitHeartRateSteady(
            segmentId: String,
            label: String?,
            note: String?,
            durationSec: Int,
            lowBpm: Int,
            highBpm: Int,
            messages: List<EwoMessage>,
            cadence: EwoCadenceRange?,
            enclosingRepeatSegmentId: String?,
            repeatIterationIndex: Int?,
        ) {
            val control = workout.control
                ?: throw IllegalStateException("Heart-rate steps require validated control state.")
            compiledSteps += CompiledEwoWorkoutStep.HeartRateSteady(
                stepIndex = compiledSteps.size,
                startOffsetSec = nextOffsetSec,
                durationSec = durationSec,
                lowBpm = lowBpm,
                highBpm = highBpm,
                initialPowerWatts = control.initialPowerWatts,
                minPowerWatts = control.minPowerWatts,
                maxPowerWatts = control.maxPowerWatts,
                signalLossPowerWatts = control.signalLossPowerWatts,
                hrUpperCapBpm = control.hrUpperCapBpm,
                messages = messages,
                cadence = cadence,
                origin = CompiledEwoSegmentOrigin(
                    sourceSegmentId = segmentId,
                    sourceSegmentLabel = label,
                    sourceSegmentNote = note,
                    enclosingRepeatSegmentId = enclosingRepeatSegmentId,
                    repeatIterationIndex = repeatIterationIndex,
                ),
            )
            nextOffsetSec = safeAdd(nextOffsetSec, durationSec)
        }

        fun emitPowerRamp(
            segmentId: String,
            label: String?,
            note: String?,
            durationSec: Int,
            fromWatts: Int,
            toWatts: Int,
            messages: List<EwoMessage>,
            cadence: EwoCadenceRange?,
        ) {
            compiledSteps += CompiledEwoWorkoutStep.PowerRamp(
                stepIndex = compiledSteps.size,
                startOffsetSec = nextOffsetSec,
                durationSec = durationSec,
                fromWatts = fromWatts,
                toWatts = toWatts,
                messages = messages,
                cadence = cadence,
                origin = CompiledEwoSegmentOrigin(
                    sourceSegmentId = segmentId,
                    sourceSegmentLabel = label,
                    sourceSegmentNote = note,
                    enclosingRepeatSegmentId = null,
                    repeatIterationIndex = null,
                ),
            )
            nextOffsetSec = safeAdd(nextOffsetSec, durationSec)
        }

        fun emitFreeRide(
            segmentId: String,
            label: String?,
            note: String?,
            durationSec: Int,
            messages: List<EwoMessage>,
            cadence: EwoCadenceRange?,
            enclosingRepeatSegmentId: String?,
            repeatIterationIndex: Int?,
        ) {
            compiledSteps += CompiledEwoWorkoutStep.FreeRide(
                stepIndex = compiledSteps.size,
                startOffsetSec = nextOffsetSec,
                durationSec = durationSec,
                messages = messages,
                cadence = cadence,
                origin = CompiledEwoSegmentOrigin(
                    sourceSegmentId = segmentId,
                    sourceSegmentLabel = label,
                    sourceSegmentNote = note,
                    enclosingRepeatSegmentId = enclosingRepeatSegmentId,
                    repeatIterationIndex = repeatIterationIndex,
                ),
            )
            nextOffsetSec = safeAdd(nextOffsetSec, durationSec)
        }

        workout.segments.forEach { segment ->
            when (segment) {
                is NormalizedEwoWorkoutSegment.PowerSteady -> emitPowerSteady(
                    segmentId = segment.id,
                    label = segment.label,
                    note = segment.note,
                    durationSec = segment.durationSec,
                    watts = segment.watts,
                    messages = segment.messages,
                    cadence = segment.cadence,
                    enclosingRepeatSegmentId = null,
                    repeatIterationIndex = null,
                )

                is NormalizedEwoWorkoutSegment.FtpPercentSteady -> emitPowerSteady(
                    segmentId = segment.id,
                    label = segment.label,
                    note = segment.note,
                    durationSec = segment.durationSec,
                    watts = resolveFtpPercent(segment.fraction, ftpWatts, segment.id),
                    messages = segment.messages,
                    cadence = segment.cadence,
                    enclosingRepeatSegmentId = null,
                    repeatIterationIndex = null,
                )

                is NormalizedEwoWorkoutSegment.HeartRateSteady -> emitHeartRateSteady(
                    segmentId = segment.id,
                    label = segment.label,
                    note = segment.note,
                    durationSec = segment.durationSec,
                    lowBpm = segment.lowBpm,
                    highBpm = segment.highBpm,
                    messages = segment.messages,
                    cadence = segment.cadence,
                    enclosingRepeatSegmentId = null,
                    repeatIterationIndex = null,
                )

                is NormalizedEwoWorkoutSegment.HeartRateRelativeSteady -> {
                    val resolved = resolveHrRelative(segment.reference, segment.lowFraction, segment.highFraction, context, segment.id)
                    emitHeartRateSteady(
                        segmentId = segment.id,
                        label = segment.label,
                        note = segment.note,
                        durationSec = segment.durationSec,
                        lowBpm = resolved.first,
                        highBpm = resolved.second,
                        messages = segment.messages,
                        cadence = segment.cadence,
                        enclosingRepeatSegmentId = null,
                        repeatIterationIndex = null,
                    )
                }

                is NormalizedEwoWorkoutSegment.PowerRamp -> emitPowerRamp(
                    segmentId = segment.id,
                    label = segment.label,
                    note = segment.note,
                    durationSec = segment.durationSec,
                    fromWatts = segment.fromWatts,
                    toWatts = segment.toWatts,
                    messages = segment.messages,
                    cadence = segment.cadence,
                )

                is NormalizedEwoWorkoutSegment.FtpPercentRamp -> emitPowerRamp(
                    segmentId = segment.id,
                    label = segment.label,
                    note = segment.note,
                    durationSec = segment.durationSec,
                    fromWatts = resolveFtpPercent(segment.fromFraction, ftpWatts, segment.id),
                    toWatts = resolveFtpPercent(segment.toFraction, ftpWatts, segment.id),
                    messages = segment.messages,
                    cadence = segment.cadence,
                )

                is NormalizedEwoWorkoutSegment.FreeRide -> emitFreeRide(
                    segmentId = segment.id,
                    label = segment.label,
                    note = segment.note,
                    durationSec = segment.durationSec,
                    messages = segment.messages,
                    cadence = segment.cadence,
                    enclosingRepeatSegmentId = null,
                    repeatIterationIndex = null,
                )

                is NormalizedEwoWorkoutSegment.Repeat -> {
                    repeat(segment.count) { iterationIndex ->
                        segment.segments.forEach { child ->
                            when (child) {
                                is NormalizedEwoWorkoutRepeatSegment.PowerSteady -> emitPowerSteady(
                                    segmentId = child.id,
                                    label = child.label,
                                    note = child.note,
                                    durationSec = child.durationSec,
                                    watts = child.watts,
                                    messages = child.messages,
                                    cadence = child.cadence,
                                    enclosingRepeatSegmentId = segment.id,
                                    repeatIterationIndex = iterationIndex,
                                )

                                is NormalizedEwoWorkoutRepeatSegment.FtpPercentSteady -> emitPowerSteady(
                                    segmentId = child.id,
                                    label = child.label,
                                    note = child.note,
                                    durationSec = child.durationSec,
                                    watts = resolveFtpPercent(child.fraction, ftpWatts, child.id),
                                    messages = child.messages,
                                    cadence = child.cadence,
                                    enclosingRepeatSegmentId = segment.id,
                                    repeatIterationIndex = iterationIndex,
                                )

                                is NormalizedEwoWorkoutRepeatSegment.HeartRateSteady -> emitHeartRateSteady(
                                    segmentId = child.id,
                                    label = child.label,
                                    note = child.note,
                                    durationSec = child.durationSec,
                                    lowBpm = child.lowBpm,
                                    highBpm = child.highBpm,
                                    messages = child.messages,
                                    cadence = child.cadence,
                                    enclosingRepeatSegmentId = segment.id,
                                    repeatIterationIndex = iterationIndex,
                                )

                                is NormalizedEwoWorkoutRepeatSegment.HeartRateRelativeSteady -> {
                                    val resolved = resolveHrRelative(child.reference, child.lowFraction, child.highFraction, context, child.id)
                                    emitHeartRateSteady(
                                        segmentId = child.id,
                                        label = child.label,
                                        note = child.note,
                                        durationSec = child.durationSec,
                                        lowBpm = resolved.first,
                                        highBpm = resolved.second,
                                        messages = child.messages,
                                        cadence = child.cadence,
                                        enclosingRepeatSegmentId = segment.id,
                                        repeatIterationIndex = iterationIndex,
                                    )
                                }

                                is NormalizedEwoWorkoutRepeatSegment.FreeRide -> emitFreeRide(
                                    segmentId = child.id,
                                    label = child.label,
                                    note = child.note,
                                    durationSec = child.durationSec,
                                    messages = child.messages,
                                    cadence = child.cadence,
                                    enclosingRepeatSegmentId = segment.id,
                                    repeatIterationIndex = iterationIndex,
                                )
                            }
                        }
                    }
                }
            }
        }

        return CompiledEwoWorkout(
            uid = workout.uid,
            revision = workout.revision,
            title = workout.title,
            description = workout.description,
            titleLocalized = workout.titleLocalized,
            descriptionLocalized = workout.descriptionLocalized,
            difficulty = workout.difficulty,
            tags = workout.tags,
            messages = workout.messages,
            steps = compiledSteps,
            totalDurationSec = nextOffsetSec,
        )
    }

    /**
     * Resolves an ftp_percent fraction to absolute watts using the rider's configured FTP.
     *
     * Fails at compile time if FTP is absent or outside reasonable bounds, so the runtime
     * never receives a nonsensical power target.
     */
    private fun resolveFtpPercent(fraction: Double, ftpWatts: Int?, segmentId: String): Int {
        if (ftpWatts == null || ftpWatts <= 0) {
            throw EwoCompileContextException(
                EwoCompileError(
                    code = EwoCompileErrorCode.MISSING_FTP,
                    message = "FTP is not configured. Add FTP in the preview rider profile to resolve FTP-based targets.",
                    segmentId = segmentId,
                ),
            )
        }
        if (ftpWatts < FTP_MIN_WATTS || ftpWatts > FTP_MAX_WATTS) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.FTP_VALUE_OUT_OF_REASONABLE_RANGE,
                message = "Configured FTP (${ftpWatts}W) is outside the supported range " +
                    "(${FTP_MIN_WATTS}–${FTP_MAX_WATTS}W). Update your FTP in app settings.",
                fieldPath = "$.segments",
            )
        }
        return maxOf(1, (ftpWatts * fraction).roundToInt())
    }

    /**
     * Resolves heart_rate_relative fractions to absolute BPM using athlete profile data.
     * Throws [EwoCompileContextException] if the required profile field is missing.
     */
    private fun resolveHrRelative(
        reference: HrReference,
        lowFraction: Double,
        highFraction: Double,
        context: EwoCompileContext,
        segmentId: String,
    ): Pair<Int, Int> {
        return when (reference) {
            HrReference.HR_MAX -> {
                val hrMax = context.hrMaxBpm ?: throw EwoCompileContextException(
                    EwoCompileError(
                        code = EwoCompileErrorCode.MISSING_HR_MAX,
                        message = "HR max is required to resolve heart_rate_relative targets with hr_max reference.",
                        segmentId = segmentId,
                    ),
                )
                Pair((hrMax * lowFraction).roundToInt(), (hrMax * highFraction).roundToInt())
            }
            HrReference.HEART_RATE_RESERVE -> {
                val hrMax = context.hrMaxBpm ?: throw EwoCompileContextException(
                    EwoCompileError(
                        code = EwoCompileErrorCode.MISSING_HR_MAX,
                        message = "HR max is required to resolve heart_rate_relative targets with heart_rate_reserve reference.",
                        segmentId = segmentId,
                    ),
                )
                val restingHr = context.restingHrBpm ?: throw EwoCompileContextException(
                    EwoCompileError(
                        code = EwoCompileErrorCode.MISSING_RESTING_HR,
                        message = "Resting HR is required to resolve heart_rate_relative targets with heart_rate_reserve reference.",
                        segmentId = segmentId,
                    ),
                )
                val reserve = hrMax - restingHr
                Pair(
                    (restingHr + reserve * lowFraction).roundToInt(),
                    (restingHr + reserve * highFraction).roundToInt(),
                )
            }
            HrReference.LTHR -> {
                val lthr = context.lthrBpm ?: throw EwoCompileContextException(
                    EwoCompileError(
                        code = EwoCompileErrorCode.MISSING_LTHR,
                        message = "LTHR is required to resolve heart_rate_relative targets with lthr reference.",
                        segmentId = segmentId,
                    ),
                )
                Pair((lthr * lowFraction).roundToInt(), (lthr * highFraction).roundToInt())
            }
        }
    }

    private fun safeAdd(current: Int, increment: Int): Int {
        val result = current.toLong() + increment.toLong()
        if (result > Int.MAX_VALUE) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.TOTAL_DURATION_OVERFLOW,
                message = "Compiled workout duration exceeds the supported range.",
                fieldPath = "$.segments",
            )
        }
        return result.toInt()
    }
}
