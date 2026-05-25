package com.example.ergometerapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.session.SessionSample

private val PowerLaneColor = Color(0xFF2FBF71)
private val HrLaneColor = Color(0xFFE57373)
private val CadenceLaneColor = Color(0xFF4FC3F7)
private val LaneDividerColor = Color(0x33FFFFFF)

private const val POWER_MAX_RELATIVE = 1.5
private const val HR_MIN = 50f
private const val HR_MAX = 200f
private const val CADENCE_MIN = 0f
private const val CADENCE_MAX = 130f
private const val LABEL_TEXT_SIZE_SP = 13f
private const val LABEL_BOLD_TEXT_SIZE_SP = 15f

/**
 * Live telemetry strip chart for telemetry-only sessions.
 *
 * Renders up to three stacked lanes (Power, HR, Cadence) as filled area traces
 * over a scrolling time window. Lanes with no data across the visible window
 * are hidden automatically.
 */
@Composable
internal fun LiveTelemetryChart(
    samples: List<SessionSample>,
    ftpWatts: Int,
    windowSeconds: Int = 120,
    compact: Boolean = false,
    maxHeight: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val newestTimestamp = remember(samples) { samples.lastOrNull()?.timestampMillis }
    val oldestVisible = remember(newestTimestamp, windowSeconds) {
        newestTimestamp?.minus(windowSeconds.coerceAtLeast(10) * 1000L)
    }
    val visibleLanes = remember(samples, oldestVisible) {
        resolveVisibleTelemetryLanes(samples = samples, oldestVisible = oldestVisible)
    }
    val hasPower = visibleLanes.hasPower
    val hasHr = visibleLanes.hasHeartRate
    val hasCadence = visibleLanes.hasCadence

    if (!hasPower && !hasHr && !hasCadence) return

    val laneCount = listOf(hasPower, hasHr, hasCadence).count { it }
    val defaultLaneHeight: Dp = if (compact) {
        if (laneCount == 1) 100.dp else 60.dp
    } else {
        when (laneCount) {
            1 -> 120.dp
            2 -> 96.dp
            else -> 88.dp
        }
    }
    val laneHeight = maxHeight?.let { availableHeight ->
        (availableHeight / laneCount.toFloat()).coerceAtMost(defaultLaneHeight)
    } ?: defaultLaneHeight
    val totalHeight = laneHeight * laneCount

    val density = LocalDensity.current
    val laneHeightPx = with(density) { laneHeight.toPx() }
    val powerMaxWatts = remember(ftpWatts) { (ftpWatts * POWER_MAX_RELATIVE).toFloat() }

    val latestPower = remember(samples) { samples.lastOrNull()?.powerWatts }
    val latestHr = remember(samples) { samples.lastOrNull()?.heartRateBpm }
    val latestCadence = remember(samples) { samples.lastOrNull()?.cadenceRpm }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight),
        ) {
            if (samples.isEmpty()) return@Canvas

            val windowSec = windowSeconds.coerceAtLeast(10)
            val newestTimestamp = samples.last().timestampMillis
            val oldestVisible = newestTimestamp - windowSec * 1000L

            var laneIndex = 0

            if (hasPower) {
                val laneTop = laneIndex * laneHeightPx
                drawPowerLane(
                    samples = samples,
                    oldestVisible = oldestVisible,
                    newestTimestamp = newestTimestamp,
                    laneTop = laneTop,
                    laneHeight = laneHeightPx,
                    powerMaxWatts = powerMaxWatts,
                    ftpWatts = ftpWatts,
                )
                drawCurrentValueLabel(
                    value = latestPower?.let { "${it}W" },
                    laneTop = laneTop,
                    laneHeight = laneHeightPx,
                    color = Color.White,
                )
                laneIndex++
            }

            if (hasHr) {
                val laneTop = laneIndex * laneHeightPx
                drawSignalLane(
                    samples = samples,
                    oldestVisible = oldestVisible,
                    newestTimestamp = newestTimestamp,
                    laneTop = laneTop,
                    laneHeight = laneHeightPx,
                    valueExtractor = { it.heartRateBpm?.toFloat() },
                    minValue = HR_MIN,
                    maxValue = HR_MAX,
                    fillColor = HrLaneColor.copy(alpha = 0.35f),
                    lineColor = HrLaneColor,
                )
                drawCurrentValueLabel(
                    value = latestHr?.let { "${it} bpm" },
                    laneTop = laneTop,
                    laneHeight = laneHeightPx,
                    color = HrLaneColor,
                )
                laneIndex++
            }

            if (hasCadence) {
                val laneTop = laneIndex * laneHeightPx
                drawSignalLane(
                    samples = samples,
                    oldestVisible = oldestVisible,
                    newestTimestamp = newestTimestamp,
                    laneTop = laneTop,
                    laneHeight = laneHeightPx,
                    valueExtractor = { it.cadenceRpm?.toFloat() },
                    minValue = CADENCE_MIN,
                    maxValue = CADENCE_MAX,
                    fillColor = CadenceLaneColor.copy(alpha = 0.3f),
                    lineColor = CadenceLaneColor,
                )
                drawCurrentValueLabel(
                    value = latestCadence?.let { "${it} rpm" },
                    laneTop = laneTop,
                    laneHeight = laneHeightPx,
                    color = CadenceLaneColor,
                )
            }
        }
    }
}

internal data class VisibleTelemetryLanes(
    val hasPower: Boolean,
    val hasHeartRate: Boolean,
    val hasCadence: Boolean,
)

internal fun resolveVisibleTelemetryLanes(
    samples: List<SessionSample>,
    oldestVisible: Long?,
): VisibleTelemetryLanes {
    if (samples.isEmpty()) {
        return VisibleTelemetryLanes(
            hasPower = false,
            hasHeartRate = false,
            hasCadence = false,
        )
    }
    val visibleSamples = if (oldestVisible == null) {
        samples
    } else {
        samples.filter { it.timestampMillis >= oldestVisible }
    }
    return VisibleTelemetryLanes(
        hasPower = visibleSamples.any { it.powerWatts != null },
        hasHeartRate = visibleSamples.any { it.heartRateBpm != null },
        hasCadence = visibleSamples.any { it.cadenceRpm != null },
    )
}

private fun DrawScope.drawPowerLane(
    samples: List<SessionSample>,
    oldestVisible: Long,
    newestTimestamp: Long,
    laneTop: Float,
    laneHeight: Float,
    powerMaxWatts: Float,
    ftpWatts: Int,
) {
    val laneBottom = laneTop + laneHeight
    val windowMs = (newestTimestamp - oldestVisible).toFloat().coerceAtLeast(1f)

    // Background
    drawRect(
        color = Color(0xFF1A1A2E),
        topLeft = Offset(0f, laneTop),
        size = Size(size.width, laneHeight),
    )

    // Draw zone-colored filled segments
    val visibleSamples = samples.filter { it.timestampMillis >= oldestVisible }
    if (visibleSamples.isEmpty()) return

    for (i in 0 until visibleSamples.size - 1) {
        val current = visibleSamples[i]
        val next = visibleSamples[i + 1]
        val power = current.powerWatts?.toFloat() ?: continue

        val x1 = ((current.timestampMillis - oldestVisible) / windowMs) * size.width
        val x2 = ((next.timestampMillis - oldestVisible) / windowMs) * size.width
        val normalized = (power / powerMaxWatts).coerceIn(0f, 1f)
        val y = laneBottom - normalized * laneHeight
        val relativePower = if (ftpWatts > 0) power.toDouble() / ftpWatts else 0.0

        drawRect(
            color = zoneColor(relativePower),
            topLeft = Offset(x1, y),
            size = Size((x2 - x1).coerceAtLeast(1f), laneBottom - y),
        )
    }

    // Last sample
    val last = visibleSamples.last()
    val lastPower = last.powerWatts?.toFloat()
    if (lastPower != null) {
        val x = ((last.timestampMillis - oldestVisible) / windowMs) * size.width
        val normalized = (lastPower / powerMaxWatts).coerceIn(0f, 1f)
        val y = laneBottom - normalized * laneHeight
        val relativePower = if (ftpWatts > 0) lastPower.toDouble() / ftpWatts else 0.0
        drawRect(
            color = zoneColor(relativePower),
            topLeft = Offset(x, y),
            size = Size((size.width - x).coerceAtLeast(1f), laneBottom - y),
        )
    }
}

private fun DrawScope.drawSignalLane(
    samples: List<SessionSample>,
    oldestVisible: Long,
    newestTimestamp: Long,
    laneTop: Float,
    laneHeight: Float,
    valueExtractor: (SessionSample) -> Float?,
    minValue: Float,
    maxValue: Float,
    fillColor: Color,
    lineColor: Color,
) {
    val laneBottom = laneTop + laneHeight
    val windowMs = (newestTimestamp - oldestVisible).toFloat().coerceAtLeast(1f)
    val range = (maxValue - minValue).coerceAtLeast(1f)

    // Background
    drawRect(
        color = Color(0xFF1A1A2E),
        topLeft = Offset(0f, laneTop),
        size = Size(size.width, laneHeight),
    )

    val visibleSamples = samples.filter { it.timestampMillis >= oldestVisible }
    val points = visibleSamples.mapNotNull { sample ->
        val value = valueExtractor(sample) ?: return@mapNotNull null
        val x = ((sample.timestampMillis - oldestVisible) / windowMs) * size.width
        val normalized = ((value - minValue) / range).coerceIn(0f, 1f)
        val y = laneBottom - normalized * laneHeight
        Offset(x, y)
    }

    if (points.size < 2) return

    // Filled area
    val fillPath = Path().apply {
        moveTo(points.first().x, laneBottom)
        points.forEach { lineTo(it.x, it.y) }
        lineTo(points.last().x, laneBottom)
        close()
    }
    drawPath(fillPath, fillColor, style = Fill)

    // Line on top
    for (i in 0 until points.size - 1) {
        drawLine(
            color = lineColor,
            start = points[i],
            end = points[i + 1],
            strokeWidth = 2f,
        )
    }
}

private fun DrawScope.drawCurrentValueLabel(
    value: String?,
    laneTop: Float,
    laneHeight: Float,
    color: Color,
) {
    if (value == null) return
    val paint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
        textSize = LABEL_BOLD_TEXT_SIZE_SP * density
        isFakeBoldText = true
        isAntiAlias = true
        setShadowLayer(3f * density, 1f, 1f, android.graphics.Color.BLACK)
    }
    val textWidth = paint.measureText(value)
    val x = size.width - textWidth - 8f * density
    val y = laneTop + laneHeight / 2f + paint.textSize / 3f
    drawContext.canvas.nativeCanvas.drawText(value, x, y, paint)
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
