package com.ewo.editor.model

import com.ewo.core.CompiledEwoWorkout
import com.ewo.core.CompiledEwoWorkoutStep
import com.ewo.core.EwoCompileError
import com.ewo.core.SanityCheckResult
import com.ewo.core.SanityIssue
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A single step in the compiled preview, derived from [CompiledEwoWorkoutStep].
 */
data class EditorPreviewStep(
    val index: Int,
    val segmentId: String,
    val repeatInfo: String?,
    val durationSec: Int,
    val label: String,
)

/** Unit of the Y-axis values in [EditorChartBar.powerLow] / [EditorChartBar.powerHigh]. */
enum class ChartPowerUnit {
    /** Values are absolute watts (from compiled data). */
    WATTS,

    /** Values are FTP-percentage integers, e.g. 75 = 75% FTP (from uncompiled segments). */
    FTP_PERCENT,
}

/**
 * Preview of the compiled workout, derived from the compiled model.
 *
 * This is editor-only metadata — never stored in canonical `.ewo`.
 * IF/TSS are suppressed (null) when FTP is missing or the workout contains
 * HR-guided steps where power is not meaningfully resolvable.
 */
data class EditorPreview(
    val steps: List<EditorPreviewStep>,
    val totalDurationSec: Int,
    val intensityFactor: Double?,
    val tss: Double?,
    val sanityWarnings: List<EditorPreviewSanityWarning>,
    val compileErrors: List<String>,
    val chartBars: List<EditorChartBar> = emptyList(),
    val chartPowerUnit: ChartPowerUnit = ChartPowerUnit.WATTS,
)

data class EditorPreviewSanityWarning(
    val code: String,
    val message: String,
)

/** Type of chart bar, used for coloring in the workout profile chart. */
enum class EditorChartBarType {
    STEADY,
    RAMP,
    FREE_RIDE,
    HR,
}

/**
 * A single bar/trapezoid in the workout profile chart.
 * Power values are in watts; for ramps [powerLow] and [powerHigh] differ.
 */
data class EditorChartBar(
    val startSec: Int,
    val endSec: Int,
    val powerLow: Int,
    val powerHigh: Int,
    val type: EditorChartBarType,
    /** Links this bar back to the editor segment that produced it. */
    val segmentId: String? = null,
)

/**
 * Builds an [EditorPreview] from a compiled workout result.
 *
 * @param compiled The compiled workout, or null if compilation failed (NeedsCompileContext).
 * @param sanityResult Sanity check result from the parse.
 * @param compileErrors Compile errors when athlete profile is missing.
 * @param ftpWatts User-provided FTP for IF/TSS computation; null suppresses IF/TSS.
 */
fun buildEditorPreview(
    compiled: CompiledEwoWorkout?,
    sanityResult: SanityCheckResult,
    compileErrors: List<EwoCompileError> = emptyList(),
    ftpWatts: Int? = null,
): EditorPreview {
    if (compiled == null) {
        return EditorPreview(
            steps = emptyList(),
            totalDurationSec = 0,
            intensityFactor = null,
            tss = null,
            sanityWarnings = mapSanityWarnings(sanityResult),
            compileErrors = compileErrors.map { it.message },
        )
    }

    val steps = compiled.steps.map { step ->
        EditorPreviewStep(
            index = step.stepIndex,
            segmentId = step.origin.sourceSegmentId,
            repeatInfo = step.origin.enclosingRepeatSegmentId?.let { repeatId ->
                val iter = step.origin.repeatIterationIndex?.let { it + 1 } ?: 1
                "$repeatId #$iter"
            },
            durationSec = step.durationSec,
            label = stepLabel(step),
        )
    }

    val ifTss = computeIfTss(compiled, ftpWatts)
    val chartBars = buildChartBars(compiled)

    return EditorPreview(
        steps = steps,
        totalDurationSec = compiled.totalDurationSec,
        intensityFactor = ifTss?.first,
        tss = ifTss?.second,
        sanityWarnings = mapSanityWarnings(sanityResult),
        compileErrors = compileErrors.map { it.message },
        chartBars = chartBars,
    )
}

private fun stepLabel(step: CompiledEwoWorkoutStep): String = when (step) {
    is CompiledEwoWorkoutStep.PowerSteady -> "${step.watts}W"
    is CompiledEwoWorkoutStep.PowerRamp -> "${step.fromWatts}→${step.toWatts}W"
    is CompiledEwoWorkoutStep.HeartRateSteady -> "${step.lowBpm}–${step.highBpm} bpm"
    is CompiledEwoWorkoutStep.FreeRide -> "Free Ride"
}

private fun mapSanityWarnings(result: SanityCheckResult): List<EditorPreviewSanityWarning> {
    if (result == SanityCheckResult.clean) return emptyList()
    return result.issues.map { issue ->
        EditorPreviewSanityWarning(
            code = issue.code.stableCode,
            message = issue.message,
        )
    }
}

/**
 * Computes IF and TSS from the compiled step list.
 *
 * Returns null (suppressed) when:
 * - [ftpWatts] is null or zero
 * - The workout contains any HR-guided steps (power not meaningfully resolvable)
 */
private fun computeIfTss(compiled: CompiledEwoWorkout, ftpWatts: Int?): Pair<Double, Double>? {
    if (ftpWatts == null || ftpWatts <= 0) return null

    // Suppress for workouts containing HR steps
    val hasHrSteps = compiled.steps.any { it is CompiledEwoWorkoutStep.HeartRateSteady }
    if (hasHrSteps) return null

    if (compiled.steps.isEmpty()) return null

    // Compute weighted average power^4 for NP approximation
    var weightedPower4Sum = 0.0
    var totalSec = 0

    for (step in compiled.steps) {
        val duration = step.durationSec
        if (duration <= 0) continue
        totalSec += duration

        when (step) {
            is CompiledEwoWorkoutStep.PowerSteady -> {
                weightedPower4Sum += step.watts.toDouble().pow(4) * duration
            }
            is CompiledEwoWorkoutStep.PowerRamp -> {
                // Use average of from and to power for ramp approximation
                val avgWatts = (step.fromWatts + step.toWatts) / 2.0
                weightedPower4Sum += avgWatts.pow(4) * duration
            }
            is CompiledEwoWorkoutStep.HeartRateSteady -> {
                // Already filtered above, but compiler needs exhaustive when
                return null
            }
            is CompiledEwoWorkoutStep.FreeRide -> {
                weightedPower4Sum += 0.0
            }
        }
    }

    if (totalSec == 0) return null

    val np = (weightedPower4Sum / totalSec).pow(0.25)
    val intensityFactor = np / ftpWatts
    val tss = (totalSec * np * intensityFactor) / (ftpWatts * 3600.0) * 100.0

    // Round for display
    val ifRounded = (intensityFactor * 100).roundToInt() / 100.0
    val tssRounded = (tss * 10).roundToInt() / 10.0

    return Pair(ifRounded, tssRounded)
}

/**
 * Builds chart bars directly from editor segments without compilation.
 *
 * Used when compilation is unavailable but the raw segment structure is still
 * useful as a visual outline.
 *
 * `ftp_percent` segments stay relative, absolute power segments stay in watts,
 * and HR / HR-relative steady segments are kept visible as HR-colored bars so
 * they do not disappear from the chart during fallback rendering.
 */
fun buildChartBarsFromSegments(segments: List<EditorSegment>): List<EditorChartBar> {
    val bars = mutableListOf<EditorChartBar>()
    var timeSec = 0
    for (seg in segments) {
        timeSec = appendSegmentBars(seg, bars, timeSec)
    }
    return bars
}

private fun appendSegmentBars(
    seg: EditorSegment,
    bars: MutableList<EditorChartBar>,
    timeSec: Int,
): Int = when (seg) {
    is EditorSegment.Steady -> {
        val end = timeSec + seg.durationSec
        when (val target = seg.target) {
            is EditorTarget.FtpPercent -> {
                val value = (target.fraction * 100).roundToInt()
                bars += EditorChartBar(
                    startSec = timeSec, endSec = end,
                    powerLow = value, powerHigh = value,
                    type = EditorChartBarType.STEADY,
                    segmentId = seg.segmentId,
                )
            }
            is EditorTarget.Power -> {
                bars += EditorChartBar(
                    startSec = timeSec, endSec = end,
                    powerLow = target.watts, powerHigh = target.watts,
                    type = EditorChartBarType.STEADY,
                    segmentId = seg.segmentId,
                )
            }
            is EditorTarget.HeartRate -> {
                bars += EditorChartBar(
                    startSec = timeSec, endSec = end,
                    powerLow = target.lowBpm, powerHigh = target.highBpm,
                    type = EditorChartBarType.HR,
                    segmentId = seg.segmentId,
                )
            }
            is EditorTarget.HeartRateRelative -> {
                bars += EditorChartBar(
                    startSec = timeSec, endSec = end,
                    powerLow = (target.lowFraction * 100).roundToInt(),
                    powerHigh = (target.highFraction * 100).roundToInt(),
                    type = EditorChartBarType.HR,
                    segmentId = seg.segmentId,
                )
            }
            null -> {}
        }
        end
    }
    is EditorSegment.Ramp -> {
        val end = timeSec + seg.durationSec
        val fromVal = targetToChartValue(seg.fromTarget)
        val toVal = targetToChartValue(seg.toTarget)
        if (fromVal != null && toVal != null) {
            bars += EditorChartBar(
                startSec = timeSec, endSec = end,
                powerLow = fromVal, powerHigh = toVal,
                type = EditorChartBarType.RAMP,
                segmentId = seg.segmentId,
            )
        }
        end
    }
    is EditorSegment.FreeRide -> {
        val end = timeSec + seg.durationSec
        bars += EditorChartBar(
            startSec = timeSec, endSec = end,
            powerLow = 0, powerHigh = 0,
            type = EditorChartBarType.FREE_RIDE,
            segmentId = seg.segmentId,
        )
        end
    }
    is EditorSegment.Repeat -> {
        var t = timeSec
        repeat(seg.count) {
            for (child in seg.segments) {
                t = appendSegmentBars(child, bars, t)
            }
        }
        t
    }
}

/** Converts power-resolvable fallback targets to one chart value. */
private fun targetToChartValue(target: EditorTarget?): Int? = when (target) {
    is EditorTarget.FtpPercent -> (target.fraction * 100).roundToInt()
    is EditorTarget.Power -> target.watts
    is EditorTarget.HeartRate -> null
    is EditorTarget.HeartRateRelative -> null
    null -> null
}

private fun buildChartBars(compiled: CompiledEwoWorkout): List<EditorChartBar> {
    val bars = mutableListOf<EditorChartBar>()
    var timeSec = 0
    for (step in compiled.steps) {
        val end = timeSec + step.durationSec
        val sid = step.origin.sourceSegmentId
        val bar = when (step) {
            is CompiledEwoWorkoutStep.PowerSteady -> EditorChartBar(
                startSec = timeSec, endSec = end,
                powerLow = step.watts, powerHigh = step.watts,
                type = EditorChartBarType.STEADY, segmentId = sid,
            )
            is CompiledEwoWorkoutStep.PowerRamp -> EditorChartBar(
                startSec = timeSec, endSec = end,
                powerLow = step.fromWatts, powerHigh = step.toWatts,
                type = EditorChartBarType.RAMP, segmentId = sid,
            )
            is CompiledEwoWorkoutStep.HeartRateSteady -> EditorChartBar(
                startSec = timeSec, endSec = end,
                powerLow = step.lowBpm, powerHigh = step.highBpm,
                type = EditorChartBarType.HR, segmentId = sid,
            )
            is CompiledEwoWorkoutStep.FreeRide -> EditorChartBar(
                startSec = timeSec, endSec = end,
                powerLow = 0, powerHigh = 0,
                type = EditorChartBarType.FREE_RIDE, segmentId = sid,
            )
        }
        bars += bar
        timeSec = end
    }
    return bars
}
