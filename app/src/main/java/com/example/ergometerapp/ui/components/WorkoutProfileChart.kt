package com.example.ergometerapp.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ewo.editor.model.ChartPowerUnit
import com.ewo.editor.model.EditorChartBar
import com.ewo.editor.model.EditorChartBarType
import com.example.ergometerapp.workout.ImportedErgoWorkout
import com.example.ergometerapp.workout.ImportedErgoWorkoutExecutionMapper
import com.example.ergometerapp.workout.ExecutionSegment
import com.example.ergometerapp.workout.ExecutionWorkout
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import kotlin.math.roundToInt

private const val CHART_HEIGHT_DP = 160
private const val MAX_RELATIVE_POWER_DATA = 2.0
private const val DEFAULT_RENDER_MAX_RELATIVE_POWER = 1.5
private const val HIGH_INTENSITY_RENDER_MAX_RELATIVE_POWER = 2.0
private const val RAMP_SLICES = 8
private val CHART_LEFT_AXIS_WIDTH = 64.dp
private val CHART_RIGHT_AXIS_WIDTH = 82.dp
private val CHART_AXIS_LABEL_TEXT_SIZE = 12.dp
private val CHART_BOTTOM_PADDING = 8.dp
private val BASE_GUIDE_RELATIVE_POWERS = listOf(0.5, 0.75, 1.0, 1.25, 1.5)
private val CHART_HEADER_TOP_PADDING = 2.dp
private val CHART_HEADER_BAND_HEIGHT = 18.dp
private val CHART_HEADER_TO_TELEMETRY_GAP = 4.dp
private const val CURSOR_TELEMETRY_SCALE = 1.2f
private val TELEMETRY_TEXT_SIZE = 12.dp * CURSOR_TELEMETRY_SCALE
private val TELEMETRY_HORIZONTAL_PADDING = 6.dp * CURSOR_TELEMETRY_SCALE
private val TELEMETRY_VERTICAL_PADDING = 3.dp * CURSOR_TELEMETRY_SCALE
private val TELEMETRY_HORIZONTAL_MARGIN = 8.dp * CURSOR_TELEMETRY_SCALE
private val TELEMETRY_CORNER_RADIUS = 7.dp * CURSOR_TELEMETRY_SCALE
private val TELEMETRY_LABEL_GAP = 3.dp * CURSOR_TELEMETRY_SCALE
private val TELEMETRY_EDGE_INSET = 2.dp * CURSOR_TELEMETRY_SCALE
private val TELEMETRY_BOTTOM_GAP_FROM_CURSOR_TOP = 10.dp * CURSOR_TELEMETRY_SCALE
private const val CURSOR_TELEMETRY_LINE_COUNT = 2

internal enum class SegmentKind {
    RAMP,
    STEADY,
    FREERIDE,
}

internal data class WorkoutProfileSegment(
    val startSec: Int,
    val durationSec: Int,
    val startPowerRelFtp: Double?,
    val endPowerRelFtp: Double?,
    val kind: SegmentKind,
)

internal data class RampRenderSlice(
    val startFraction: Float,
    val endFraction: Float,
    val startPowerRelFtp: Double,
    val endPowerRelFtp: Double,
    val color: Color,
)

/**
 * Renders a static workout profile chart from parsed workout steps.
 *
 * The chart uses relative FTP values from the source workout (`0.75 == 75% FTP`)
 * so the shape is deterministic across devices.
 */
@Composable
internal fun WorkoutProfileChart(
    workout: WorkoutFile,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
    elapsedSec: Int? = null,
    currentTargetWatts: Int? = null,
    targetPowerWatts: Int? = null,
    instantPowerValue: String? = null,
    instantCadenceValue: String? = null,
    kcalValue: String? = null,
    heartRateValue: String? = null,
    heartRateZoneValue: String? = null,
    topCenterLabel: String? = null,
    highlightedSegmentIndices: Set<Int> = emptySet(),
    onSegmentTap: ((Int) -> Unit)? = null,
    chartHeight: Dp = CHART_HEIGHT_DP.dp,
) {
    val segments = remember(workout) { buildWorkoutProfileSegments(workout) }
    WorkoutProfileChart(
        segments = segments,
        ftpWatts = ftpWatts,
        modifier = modifier,
        elapsedSec = elapsedSec,
        currentTargetWatts = currentTargetWatts,
        targetPowerWatts = targetPowerWatts,
        instantPowerValue = instantPowerValue,
        instantCadenceValue = instantCadenceValue,
        kcalValue = kcalValue,
        heartRateValue = heartRateValue,
        heartRateZoneValue = heartRateZoneValue,
        topCenterLabel = topCenterLabel,
        highlightedSegmentIndices = highlightedSegmentIndices,
        onSegmentTap = onSegmentTap,
        chartHeight = chartHeight,
    )
}

@Composable
internal fun WorkoutProfileChart(
    segments: List<WorkoutProfileSegment>,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
    elapsedSec: Int? = null,
    currentTargetWatts: Int? = null,
    targetPowerWatts: Int? = null,
    instantPowerValue: String? = null,
    instantCadenceValue: String? = null,
    kcalValue: String? = null,
    heartRateValue: String? = null,
    heartRateZoneValue: String? = null,
    topCenterLabel: String? = null,
    highlightedSegmentIndices: Set<Int> = emptySet(),
    onSegmentTap: ((Int) -> Unit)? = null,
    chartHeight: Dp = CHART_HEIGHT_DP.dp,
) {
    val guideRelativePowers = remember(segments) { guideRelativePowersForSegments(segments) }
    val renderMaxRelativePower = guideRelativePowers.lastOrNull() ?: DEFAULT_RENDER_MAX_RELATIVE_POWER
    val totalDurationSec = remember(segments) { segments.sumOf { it.durationSec }.coerceAtLeast(1) }
    val semanticsDescription =
        "Workout profile chart, ${segments.size} segments, ftp ${ftpWatts.coerceAtLeast(1)} watts"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .semantics { contentDescription = semanticsDescription }
    ) {
        if (segments.isEmpty()) {
            Text(
                text = "No chart data",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            return@Box
        }

        val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        val freeRideColor = MaterialTheme.colorScheme.surfaceVariant
        val cursorColor = MaterialTheme.colorScheme.primary
        val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val telemetryTextColor = MaterialTheme.colorScheme.onSurface
        val telemetryBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .let { canvasModifier ->
                    if (onSegmentTap == null) {
                        canvasModifier
                    } else {
                        canvasModifier.pointerInput(segments, totalDurationSec) {
                            detectTapGestures { tapOffset ->
                                val leftAxisWidth = CHART_LEFT_AXIS_WIDTH.value * density
                                val rightAxisWidth = CHART_RIGHT_AXIS_WIDTH.value * density
                                val plotLeft = leftAxisWidth
                                val plotRight = (size.width - rightAxisWidth).coerceAtLeast(plotLeft + 1f)
                                val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
                                if (tapOffset.x !in plotLeft..plotRight) return@detectTapGestures

                                val relativeX = ((tapOffset.x - plotLeft) / plotWidth).coerceIn(0f, 1f)
                                val tapSecond = (relativeX * totalDurationSec.toFloat()).toInt()
                                    .coerceIn(0, totalDurationSec - 1)
                                val tappedIndex = segmentIndexAtSecond(
                                    segments = segments,
                                    elapsedSec = tapSecond,
                                )
                                if (tappedIndex >= 0) {
                                    onSegmentTap(tappedIndex)
                                }
                            }
                        }
                    }
                }
        ) {
            val leftAxisWidth = CHART_LEFT_AXIS_WIDTH.toPx()
            val rightAxisWidth = CHART_RIGHT_AXIS_WIDTH.toPx()
            val bottomPadding = CHART_BOTTOM_PADDING.toPx()
            val chartHeaderBandHeight = if (topCenterLabel.isNullOrBlank()) 0f else {
                CHART_HEADER_BAND_HEIGHT.toPx() + CHART_HEADER_TO_TELEMETRY_GAP.toPx()
            }
            val telemetryBandHeight = cursorTelemetryPanelHeightPx(lineCount = CURSOR_TELEMETRY_LINE_COUNT)
            val telemetryBottomGap = TELEMETRY_BOTTOM_GAP_FROM_CURSOR_TOP.toPx()
            val telemetryEdgeInset = TELEMETRY_EDGE_INSET.toPx()
            val plotLeft = leftAxisWidth
            val plotRight = (size.width - rightAxisWidth).coerceAtLeast(plotLeft + 1f)
            // Keep a dedicated telemetry band above the plot so moving badges never cover axis labels.
            val maxPlotTop = (size.height - bottomPadding - 1f).coerceAtLeast(0f)
            val plotTop = (chartHeaderBandHeight + telemetryBandHeight + telemetryBottomGap + telemetryEdgeInset)
                .coerceAtMost(maxPlotTop)
            val plotBottom = (size.height - bottomPadding).coerceAtLeast(plotTop + 1f)
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)

            drawGuides(
                color = guideColor,
                guideRelativePowers = guideRelativePowers,
                renderMaxRelativePower = renderMaxRelativePower,
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = plotTop,
                plotBottom = plotBottom,
                guideOverhang = 26.dp.toPx(),
            )

            segments.forEachIndexed { segmentIndex, segment ->
                val startX = plotLeft +
                    (segment.startSec.toFloat() / totalDurationSec.toFloat()) * plotWidth
                val endX = plotLeft +
                    ((segment.startSec + segment.durationSec).toFloat() / totalDurationSec.toFloat()) * plotWidth
                if (endX <= startX) return@forEachIndexed

                when (segment.kind) {
                    SegmentKind.FREERIDE -> {
                        val topY = yForPower(
                            relativePower = 0.12,
                            renderMaxRelativePower = renderMaxRelativePower,
                            chartTop = plotTop,
                            chartBottom = plotBottom,
                        )
                        drawRect(
                            color = freeRideColor,
                            topLeft = androidx.compose.ui.geometry.Offset(startX, topY),
                            size = androidx.compose.ui.geometry.Size(endX - startX, plotBottom - topY),
                        )
                    }

                    SegmentKind.RAMP,
                    SegmentKind.STEADY -> {
                        val startRel = segment.startPowerRelFtp ?: return@forEachIndexed
                        val endRel = segment.endPowerRelFtp ?: return@forEachIndexed
                        if (segment.kind == SegmentKind.RAMP) {
                            drawRampSlices(
                                startX = startX,
                                endX = endX,
                                plotBottom = plotBottom,
                                chartTop = plotTop,
                                chartBottom = plotBottom,
                                renderMaxRelativePower = renderMaxRelativePower,
                                startPowerRelFtp = startRel,
                                endPowerRelFtp = endRel,
                            )
                        } else {
                            val startY = yForPower(
                                relativePower = startRel,
                                renderMaxRelativePower = renderMaxRelativePower,
                                chartTop = plotTop,
                                chartBottom = plotBottom,
                            )
                            val endY = yForPower(
                                relativePower = endRel,
                                renderMaxRelativePower = renderMaxRelativePower,
                                chartTop = plotTop,
                                chartBottom = plotBottom,
                            )
                            val color = zoneColor((startRel + endRel) / 2.0)

                            val path = Path().apply {
                                moveTo(startX, plotBottom)
                                lineTo(startX, startY)
                                lineTo(endX, endY)
                                lineTo(endX, plotBottom)
                                close()
                            }
                            drawPath(path = path, color = color)
                        }
                    }
                }

                if (segmentIndex in highlightedSegmentIndices) {
                    val highlightColor = cursorColor.copy(alpha = 0.26f)
                    drawRect(
                        color = highlightColor,
                        topLeft = Offset(startX, plotTop),
                        size = androidx.compose.ui.geometry.Size(
                            width = endX - startX,
                            height = plotBottom - plotTop,
                        ),
                    )
                }
            }

            drawGuideAxisLabels(
                ftpWatts = ftpWatts,
                guideRelativePowers = guideRelativePowers,
                renderMaxRelativePower = renderMaxRelativePower,
                textColor = axisLabelColor,
                leftX = plotLeft - 16.dp.toPx(),
                rightX = plotRight + 16.dp.toPx(),
                plotTop = plotTop,
                plotBottom = plotBottom,
            )

            if (elapsedSec != null && elapsedSec >= 0) {
                val cursorX = plotLeft + (elapsedSec.toFloat() / totalDurationSec.toFloat())
                    .coerceIn(0f, 1f) * plotWidth
                drawLine(
                    color = cursorColor,
                    start = androidx.compose.ui.geometry.Offset(cursorX, plotTop),
                    end = androidx.compose.ui.geometry.Offset(cursorX, plotBottom),
                    strokeWidth = 3.dp.toPx(),
                )

                val leftTelemetryLabels = listOf(
                    instantCadenceValue?.trim()?.takeIf { it.isNotEmpty() } ?: "--",
                    heartRateValue?.trim()?.takeIf { it.isNotEmpty() } ?: "--",
                )
                drawCursorTelemetryPanel(
                    labels = leftTelemetryLabels,
                    side = TelemetryPanelSide.LEFT,
                    textColor = telemetryTextColor,
                    backgroundColor = telemetryBackgroundColor,
                    cursorX = cursorX,
                    plotTop = plotTop,
                )

                val resolvedTargetWatts = targetPowerWatts ?: currentTargetWatts
                val ftpPercentLabel = if (resolvedTargetWatts != null && resolvedTargetWatts > 0) {
                    targetFtpPercentTelemetryLabel(targetWatts = resolvedTargetWatts, ftpWatts = ftpWatts)
                } else {
                    "--"
                }
                val rightTelemetryLabels = listOf(
                    ftpPercentLabel,
                    instantPowerValue?.trim()?.takeIf { it.isNotEmpty() } ?: "--",
                )
                drawCursorTelemetryPanel(
                    labels = rightTelemetryLabels,
                    side = TelemetryPanelSide.RIGHT,
                    textColor = telemetryTextColor,
                    backgroundColor = telemetryBackgroundColor,
                    cursorX = cursorX,
                    plotTop = plotTop,
                )
            }
        }

        if (!topCenterLabel.isNullOrBlank()) {
            Text(
                text = topCenterLabel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        start = CHART_LEFT_AXIS_WIDTH,
                        end = CHART_RIGHT_AXIS_WIDTH,
                        top = CHART_HEADER_TOP_PADDING,
                    )
                    .widthIn(max = 260.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun segmentIndexAtSecond(
    segments: List<WorkoutProfileSegment>,
    elapsedSec: Int,
): Int {
    if (segments.isEmpty()) return -1
    return segments.indexOfFirst { segment ->
        elapsedSec >= segment.startSec && elapsedSec < segment.startSec + segment.durationSec
    }.takeIf { it >= 0 } ?: (segments.lastIndex)
}

internal fun buildWorkoutProfileSegments(workout: WorkoutFile): List<WorkoutProfileSegment> {
    val segments = mutableListOf<WorkoutProfileSegment>()
    var startSec = 0

    fun appendSegment(
        durationSec: Int,
        startPowerRelFtp: Double?,
        endPowerRelFtp: Double?,
        kind: SegmentKind,
    ) {
        if (durationSec <= 0) return
        segments += WorkoutProfileSegment(
            startSec = startSec,
            durationSec = durationSec,
            startPowerRelFtp = startPowerRelFtp,
            endPowerRelFtp = endPowerRelFtp,
            kind = kind,
        )
        startSec += durationSec
    }

    workout.steps.forEach { step ->
        when (step) {
            is Step.Warmup -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                val low = validPower(step.powerLow) ?: return@forEach
                val high = validPower(step.powerHigh) ?: return@forEach
                appendSegment(duration, low, high, SegmentKind.RAMP)
            }

            is Step.Cooldown -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                val low = validPower(step.powerLow) ?: return@forEach
                val high = validPower(step.powerHigh) ?: return@forEach
                val start = maxOf(low, high)
                val end = minOf(low, high)
                appendSegment(duration, start, end, SegmentKind.RAMP)
            }

            is Step.Ramp -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                val low = validPower(step.powerLow) ?: return@forEach
                val high = validPower(step.powerHigh) ?: return@forEach
                appendSegment(duration, low, high, SegmentKind.RAMP)
            }

            is Step.SteadyState -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                val power = validPower(step.power) ?: return@forEach
                appendSegment(duration, power, power, SegmentKind.STEADY)
            }

            is Step.IntervalsT -> {
                val onDuration = validDuration(step.onDurationSec) ?: return@forEach
                val offDuration = validDuration(step.offDurationSec) ?: return@forEach
                val onPower = validPower(step.onPower) ?: return@forEach
                val offPower = validPower(step.offPower) ?: return@forEach
                val repeatCount = step.repeat?.takeIf { it > 0 } ?: return@forEach

                // Expand explicitly so rendering stays time-true without hidden loop state.
                repeat(repeatCount) {
                    appendSegment(onDuration, onPower, onPower, SegmentKind.STEADY)
                    appendSegment(offDuration, offPower, offPower, SegmentKind.STEADY)
                }
            }

            is Step.FreeRide -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                appendSegment(duration, null, null, SegmentKind.FREERIDE)
            }

            is Step.Unknown -> {
                // Unknown tags are intentionally ignored in MVP chart rendering.
            }
        }
    }

    return segments
}

internal fun buildWorkoutProfileSegments(
    workout: ImportedErgoWorkout,
    ftpWatts: Int,
): List<WorkoutProfileSegment> {
    return when (val mapped = ImportedErgoWorkoutExecutionMapper.map(workout)) {
        is com.example.ergometerapp.workout.MappingResult.Success ->
            buildWorkoutProfileSegments(mapped.workout, ftpWatts)

        is com.example.ergometerapp.workout.MappingResult.Failure -> emptyList()
    }
}

internal fun buildWorkoutProfileSegments(
    workout: ExecutionWorkout,
    ftpWatts: Int,
): List<WorkoutProfileSegment> {
    val safeFtpWatts = ftpWatts.takeIf { it > 0 } ?: return emptyList()
    val segments = mutableListOf<WorkoutProfileSegment>()
    var startSec = 0

    fun appendSegment(
        durationSec: Int,
        startPowerRelFtp: Double?,
        endPowerRelFtp: Double?,
        kind: SegmentKind,
    ) {
        if (durationSec <= 0) return
        segments += WorkoutProfileSegment(
            startSec = startSec,
            durationSec = durationSec,
            startPowerRelFtp = startPowerRelFtp,
            endPowerRelFtp = endPowerRelFtp,
            kind = kind,
        )
        startSec += durationSec
    }

    workout.segments.forEach { segment ->
        when (segment) {
            is ExecutionSegment.Steady -> {
                val duration = validDuration(segment.durationSec) ?: return@forEach
                val power = absolutePowerToRelativeFtp(
                    watts = segment.targetWatts,
                    ftpWatts = safeFtpWatts,
                ) ?: return@forEach
                appendSegment(duration, power, power, SegmentKind.STEADY)
            }

            is ExecutionSegment.Ramp -> {
                val duration = validDuration(segment.durationSec) ?: return@forEach
                val startPower = absolutePowerToRelativeFtp(
                    watts = segment.startWatts,
                    ftpWatts = safeFtpWatts,
                ) ?: return@forEach
                val endPower = absolutePowerToRelativeFtp(
                    watts = segment.endWatts,
                    ftpWatts = safeFtpWatts,
                ) ?: return@forEach
                appendSegment(duration, startPower, endPower, SegmentKind.RAMP)
            }

            is ExecutionSegment.HeartRateSteady -> {
                val duration = validDuration(segment.durationSec) ?: return@forEach
                val power = absolutePowerToRelativeFtp(
                    watts = segment.initialPowerWatts,
                    ftpWatts = safeFtpWatts,
                ) ?: return@forEach
                appendSegment(duration, power, power, SegmentKind.STEADY)
            }

            is ExecutionSegment.FreeRide -> {
                val duration = validDuration(segment.durationSec) ?: return@forEach
                appendSegment(duration, null, null, SegmentKind.FREERIDE)
            }
        }
    }

    return segments
}

private fun validDuration(durationSec: Int?): Int? {
    return durationSec?.takeIf { it > 0 }
}

private fun validPower(powerRelFtp: Double?): Double? {
    val value = powerRelFtp ?: return null
    if (!value.isFinite() || value < 0.0) return null
    return value.coerceIn(0.0, MAX_RELATIVE_POWER_DATA)
}

private fun absolutePowerToRelativeFtp(
    watts: Int,
    ftpWatts: Int,
): Double? {
    if (watts < 0 || ftpWatts <= 0) return null
    return validPower(watts.toDouble() / ftpWatts.toDouble())
}

private fun guideRelativePowersForSegments(segments: List<WorkoutProfileSegment>): List<Double> {
    val maxRelativePower = segments.maxOfOrNull { segment ->
        maxOf(
            segment.startPowerRelFtp ?: 0.0,
            segment.endPowerRelFtp ?: 0.0,
        )
    } ?: 0.0
    return if (maxRelativePower > DEFAULT_RENDER_MAX_RELATIVE_POWER) {
        BASE_GUIDE_RELATIVE_POWERS + HIGH_INTENSITY_RENDER_MAX_RELATIVE_POWER
    } else {
        BASE_GUIDE_RELATIVE_POWERS
    }
}

private fun DrawScope.drawGuides(
    color: Color,
    guideRelativePowers: List<Double>,
    renderMaxRelativePower: Double,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    guideOverhang: Float,
) {
    val guideStartX = (plotLeft - guideOverhang).coerceAtLeast(0f)
    val guideEndX = (plotRight + guideOverhang).coerceAtMost(size.width)
    guideRelativePowers.forEach { guideRel ->
        val y = yForPower(
            relativePower = guideRel,
            renderMaxRelativePower = renderMaxRelativePower,
            chartTop = plotTop,
            chartBottom = plotBottom,
        )
        drawLine(
            color = color,
            start = Offset(guideStartX, y),
            end = Offset(guideEndX, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun DrawScope.drawGuideAxisLabels(
    ftpWatts: Int,
    guideRelativePowers: List<Double>,
    renderMaxRelativePower: Double,
    textColor: Color,
    leftX: Float,
    rightX: Float,
    plotTop: Float,
    plotBottom: Float,
) {
    val safeFtpWatts = ftpWatts.coerceAtLeast(1)
    val leftPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = CHART_AXIS_LABEL_TEXT_SIZE.toPx()
        textAlign = Paint.Align.RIGHT
    }
    val rightPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = CHART_AXIS_LABEL_TEXT_SIZE.toPx()
        textAlign = Paint.Align.LEFT
    }
    val leftBaselineOffset = -(leftPaint.fontMetrics.ascent + leftPaint.fontMetrics.descent) / 2f
    val rightBaselineOffset = -(rightPaint.fontMetrics.ascent + rightPaint.fontMetrics.descent) / 2f

    guideRelativePowers.forEach { relativePower ->
        val y = yForPower(
            relativePower = relativePower,
            renderMaxRelativePower = renderMaxRelativePower,
            chartTop = plotTop,
            chartBottom = plotBottom,
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${(relativePower * 100).roundToInt()}%",
            leftX,
            y + leftBaselineOffset,
            leftPaint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${(safeFtpWatts * relativePower).roundToInt()} W",
            rightX,
            y + rightBaselineOffset,
            rightPaint,
        )
    }
}

private enum class TelemetryPanelSide {
    LEFT,
    RIGHT,
}

private fun DrawScope.drawCursorTelemetryPanel(
    labels: List<String>,
    side: TelemetryPanelSide,
    textColor: Color,
    backgroundColor: Color,
    cursorX: Float,
    plotTop: Float,
) {
    if (labels.isEmpty()) return
    val textAlignment = when (side) {
        TelemetryPanelSide.LEFT -> Paint.Align.RIGHT
        TelemetryPanelSide.RIGHT -> Paint.Align.LEFT
    }
    val labelPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = TELEMETRY_TEXT_SIZE.toPx()
        this.textAlign = textAlignment
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD,
        )
    }
    val resolvedLabels = labels.map { it.ifBlank { "--" } }
    val maxLabelWidth = resolvedLabels.maxOf { labelPaint.measureText(it) }
    val fontMetrics = labelPaint.fontMetrics
    val labelHeight = fontMetrics.descent - fontMetrics.ascent
    val lineCount = resolvedLabels.size
    val horizontalPadding = TELEMETRY_HORIZONTAL_PADDING.toPx()
    val verticalPadding = TELEMETRY_VERTICAL_PADDING.toPx()
    val margin = TELEMETRY_HORIZONTAL_MARGIN.toPx()
    val corner = TELEMETRY_CORNER_RADIUS.toPx()
    val labelGap = TELEMETRY_LABEL_GAP.toPx()
    val panelBottomGap = TELEMETRY_BOTTOM_GAP_FROM_CURSOR_TOP.toPx()
    val boxWidth = maxLabelWidth + horizontalPadding * 2
    val boxHeight = labelHeight * lineCount + labelGap * (lineCount - 1).coerceAtLeast(0) + verticalPadding * 2

    val edgeInset = TELEMETRY_EDGE_INSET.toPx()
    val minLeft = edgeInset
    val maxLeft = (size.width - boxWidth - edgeInset).coerceAtLeast(minLeft)
    val desiredLeft = when (side) {
        TelemetryPanelSide.LEFT -> cursorX - margin - boxWidth
        TelemetryPanelSide.RIGHT -> cursorX + margin
    }
    val boxLeft = desiredLeft.coerceIn(minLeft, maxLeft)
    val boxTop = (plotTop - panelBottomGap - boxHeight).coerceAtLeast(edgeInset)

    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(boxLeft, boxTop),
        size = androidx.compose.ui.geometry.Size(
            width = boxWidth,
            height = boxHeight,
        ),
        cornerRadius = CornerRadius(corner, corner),
    )

    resolvedLabels.forEachIndexed { index, label ->
        val baseline = boxTop + verticalPadding - fontMetrics.ascent + index * (labelHeight + labelGap)
        val textX = when (side) {
            TelemetryPanelSide.LEFT -> boxLeft + boxWidth - horizontalPadding
            TelemetryPanelSide.RIGHT -> boxLeft + horizontalPadding
        }
        drawContext.canvas.nativeCanvas.drawText(
            label,
            textX,
            baseline,
            labelPaint,
        )
    }
}

private fun DrawScope.cursorTelemetryPanelHeightPx(lineCount: Int): Float {
    if (lineCount <= 0) return 0f
    val textPaint = Paint().apply { textSize = TELEMETRY_TEXT_SIZE.toPx() }
    val fontMetrics = textPaint.fontMetrics
    val labelHeight = fontMetrics.descent - fontMetrics.ascent
    val verticalPadding = TELEMETRY_VERTICAL_PADDING.toPx()
    val labelGap = TELEMETRY_LABEL_GAP.toPx()
    return labelHeight * lineCount +
        labelGap * (lineCount - 1).coerceAtLeast(0) +
        verticalPadding * 2
}

private fun targetFtpPercentTelemetryLabel(
    targetWatts: Int,
    ftpWatts: Int,
): String {
    val safeFtpWatts = ftpWatts.coerceAtLeast(1)
    return "${((targetWatts.toDouble() / safeFtpWatts.toDouble()) * 100.0).roundToInt()}%"
}

private fun yForPower(
    relativePower: Double,
    renderMaxRelativePower: Double,
    chartTop: Float,
    chartBottom: Float,
): Float {
    val normalized =
        (relativePower.coerceIn(0.0, renderMaxRelativePower) / renderMaxRelativePower).toFloat()
    return chartTop + (chartBottom - chartTop) * (1f - normalized)
}

/**
 * Lightweight editor-only workout profile chart rendered from [EditorChartBar] data.
 * No cursor, no telemetry — just the power profile shape.
 */
@Composable
internal fun EditorWorkoutProfileChart(
    chartBars: List<EditorChartBar>,
    ftpWatts: Int?,
    powerUnit: ChartPowerUnit,
    modifier: Modifier = Modifier,
) {
    if (chartBars.isEmpty()) return

    val isPercentMode = powerUnit == ChartPowerUnit.FTP_PERCENT
    val maxPower = chartBars.maxOf { maxOf(it.powerLow, it.powerHigh) }.coerceAtLeast(1)
    val totalDurationSec = chartBars.lastOrNull()?.endSec ?: return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val plotLeft = 0f
        val plotRight = size.width
        val plotTop = 4.dp.toPx()
        val plotBottom = size.height
        val plotWidth = plotRight - plotLeft

        for (bar in chartBars) {
            val startX = plotLeft + (bar.startSec.toFloat() / totalDurationSec) * plotWidth
            val endX = plotLeft + (bar.endSec.toFloat() / totalDurationSec) * plotWidth
            if (endX <= startX) continue

            val color = editorChartBarColor(
                bar = bar,
                ftpWatts = ftpWatts,
                powerUnit = powerUnit,
                fallbackChartMax = maxPower,
            )

            when (bar.type) {
                EditorChartBarType.FREE_RIDE -> {
                    val topY = plotBottom - (plotBottom - plotTop) * 0.08f
                    drawRect(
                        color = color,
                        topLeft = Offset(startX, topY),
                        size = androidx.compose.ui.geometry.Size(endX - startX, plotBottom - topY),
                    )
                }
                EditorChartBarType.RAMP -> {
                    drawEditorRampSlices(
                        startX = startX,
                        endX = endX,
                        plotBottom = plotBottom,
                        chartTop = plotTop,
                        chartBottom = plotBottom,
                        renderMaxChartValue = maxPower.toDouble(),
                        startChartValue = bar.powerLow.toDouble(),
                        endChartValue = bar.powerHigh.toDouble(),
                        ftpWatts = ftpWatts,
                        isPercentMode = isPercentMode,
                    )
                }
                else -> {
                    val lowY = plotBottom - (bar.powerLow.toFloat() / maxPower) * (plotBottom - plotTop)
                    val highY = plotBottom - (bar.powerHigh.toFloat() / maxPower) * (plotBottom - plotTop)
                    val path = Path().apply {
                        moveTo(startX, plotBottom)
                        lineTo(startX, lowY)
                        lineTo(endX, highY)
                        lineTo(endX, plotBottom)
                        close()
                    }
                    drawPath(path = path, color = color)
                }
            }
        }
    }
}

internal fun editorChartBarColor(
    bar: EditorChartBar,
    ftpWatts: Int?,
    powerUnit: ChartPowerUnit,
    fallbackChartMax: Int,
): Color {
    return when (bar.type) {
        EditorChartBarType.STEADY -> zoneColor(
            editorChartZoneRelativePower(
                chartValue = bar.powerHigh,
                ftpWatts = ftpWatts,
                powerUnit = powerUnit,
                fallbackChartMax = fallbackChartMax,
            ),
        )
        EditorChartBarType.RAMP -> zoneColor(
            editorChartZoneRelativePower(
                chartValue = (bar.powerLow + bar.powerHigh) / 2,
                ftpWatts = ftpWatts,
                powerUnit = powerUnit,
                fallbackChartMax = fallbackChartMax,
            ),
        )
        EditorChartBarType.FREE_RIDE -> Color(0xFF9AA6B2).copy(alpha = 0.4f)
        EditorChartBarType.HR -> Color(0xFFE91E63).copy(alpha = 0.6f)
    }
}

private fun editorChartZoneRelativePower(
    chartValue: Int,
    ftpWatts: Int?,
    powerUnit: ChartPowerUnit,
    fallbackChartMax: Int,
): Double {
    return when (powerUnit) {
        ChartPowerUnit.FTP_PERCENT -> chartValue.toDouble() / 100.0
        ChartPowerUnit.WATTS -> {
            val safeFtp = ftpWatts?.takeIf { it > 0 }
            if (safeFtp != null) {
                chartValue.toDouble() / safeFtp.toDouble()
            } else {
                chartValue.toDouble() / fallbackChartMax.coerceAtLeast(1).toDouble()
            }
        }
    }
}

private fun DrawScope.drawRampSlices(
    startX: Float,
    endX: Float,
    plotBottom: Float,
    chartTop: Float,
    chartBottom: Float,
    renderMaxRelativePower: Double,
    startPowerRelFtp: Double,
    endPowerRelFtp: Double,
) {
    buildRampRenderSlices(
        startPowerRelFtp = startPowerRelFtp,
        endPowerRelFtp = endPowerRelFtp,
    ).forEach { slice ->
        val sliceStartX = startX + (endX - startX) * slice.startFraction
        val sliceEndX = startX + (endX - startX) * slice.endFraction
        val sliceStartY = yForPower(
            relativePower = slice.startPowerRelFtp,
            renderMaxRelativePower = renderMaxRelativePower,
            chartTop = chartTop,
            chartBottom = chartBottom,
        )
        val sliceEndY = yForPower(
            relativePower = slice.endPowerRelFtp,
            renderMaxRelativePower = renderMaxRelativePower,
            chartTop = chartTop,
            chartBottom = chartBottom,
        )
        val path = Path().apply {
            moveTo(sliceStartX, plotBottom)
            lineTo(sliceStartX, sliceStartY)
            lineTo(sliceEndX, sliceEndY)
            lineTo(sliceEndX, plotBottom)
            close()
        }
        drawPath(path = path, color = slice.color)
    }
}

private fun DrawScope.drawEditorRampSlices(
    startX: Float,
    endX: Float,
    plotBottom: Float,
    chartTop: Float,
    chartBottom: Float,
    renderMaxChartValue: Double,
    startChartValue: Double,
    endChartValue: Double,
    ftpWatts: Int?,
    isPercentMode: Boolean,
) {
    val startRelativePower = if (isPercentMode) {
        startChartValue / 100.0
    } else {
        val safeFtp = ftpWatts?.takeIf { it > 0 }
        if (safeFtp != null) startChartValue / safeFtp else startChartValue / renderMaxChartValue
    }
    val endRelativePower = if (isPercentMode) {
        endChartValue / 100.0
    } else {
        val safeFtp = ftpWatts?.takeIf { it > 0 }
        if (safeFtp != null) endChartValue / safeFtp else endChartValue / renderMaxChartValue
    }
    buildRampRenderSlices(
        startPowerRelFtp = startRelativePower,
        endPowerRelFtp = endRelativePower,
    ).forEach { slice ->
        val sliceStartX = startX + (endX - startX) * slice.startFraction
        val sliceEndX = startX + (endX - startX) * slice.endFraction
        val sliceStartY = yForPower(
            relativePower = startChartValue + (endChartValue - startChartValue) * slice.startFraction,
            renderMaxRelativePower = renderMaxChartValue,
            chartTop = chartTop,
            chartBottom = chartBottom,
        )
        val sliceEndY = yForPower(
            relativePower = startChartValue + (endChartValue - startChartValue) * slice.endFraction,
            renderMaxRelativePower = renderMaxChartValue,
            chartTop = chartTop,
            chartBottom = chartBottom,
        )
        val path = Path().apply {
            moveTo(sliceStartX, plotBottom)
            lineTo(sliceStartX, sliceStartY)
            lineTo(sliceEndX, sliceEndY)
            lineTo(sliceEndX, plotBottom)
            close()
        }
        drawPath(path = path, color = slice.color)
    }
}

internal fun buildRampRenderSlices(
    startPowerRelFtp: Double,
    endPowerRelFtp: Double,
    sliceCount: Int = RAMP_SLICES,
): List<RampRenderSlice> {
    require(sliceCount > 0) { "sliceCount must be positive" }
    return List(sliceCount) { index ->
        val startFraction = index.toFloat() / sliceCount.toFloat()
        val endFraction = (index + 1).toFloat() / sliceCount.toFloat()
        val sliceStartPower = interpolateRampPower(
            startPowerRelFtp = startPowerRelFtp,
            endPowerRelFtp = endPowerRelFtp,
            fraction = startFraction.toDouble(),
        )
        val sliceEndPower = interpolateRampPower(
            startPowerRelFtp = startPowerRelFtp,
            endPowerRelFtp = endPowerRelFtp,
            fraction = endFraction.toDouble(),
        )
        val midpointPower = (sliceStartPower + sliceEndPower) / 2.0
        RampRenderSlice(
            startFraction = startFraction,
            endFraction = endFraction,
            startPowerRelFtp = sliceStartPower,
            endPowerRelFtp = sliceEndPower,
            color = zoneColor(midpointPower),
        )
    }
}

private fun interpolateRampPower(
    startPowerRelFtp: Double,
    endPowerRelFtp: Double,
    fraction: Double,
): Double {
    return startPowerRelFtp + (endPowerRelFtp - startPowerRelFtp) * fraction
}

private fun zoneColor(relativePower: Double): Color {
    return when {
        relativePower <= 0.55 -> Color(0xFF9AA6B2)
        relativePower <= 0.75 -> Color(0xFF23A6D5)
        relativePower <= 0.90 -> Color(0xFF2FBF71)
        relativePower <= 1.05 -> Color(0xFFF3B400)
        relativePower <= 1.20 -> Color(0xFFFF7A3D)
        else -> Color(0xFFE64A19)
    }
}
