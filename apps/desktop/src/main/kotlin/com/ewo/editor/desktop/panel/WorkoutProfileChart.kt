package com.ewo.editor.desktop.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Cursor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewo.editor.model.ChartPowerUnit
import com.ewo.editor.model.EditorChartBar
import com.ewo.editor.model.EditorChartBarType
import com.ewo.editor.desktop.formatDurationCompact
import com.ewo.editor.desktop.theme.SegmentColors
import com.ewo.editor.desktop.theme.ZoneColors

/** Number of vertical slices used to approximate a zone-colored ramp. */
private const val RAMP_SLICES = 8

/**
 * Renders a power/HR profile chart from compiled [EditorChartBar] data.
 *
 * Fills the space given by [modifier]. Caller controls the size — use
 * `Modifier.fillMaxSize()` in the center workspace or a fixed height elsewhere.
 */
@Composable
fun WorkoutProfileChart(
    bars: List<EditorChartBar>,
    totalDurationSec: Int,
    ftpWatts: Int?,
    powerUnit: ChartPowerUnit = ChartPowerUnit.WATTS,
    highlightedSegmentIds: Set<String> = emptySet(),
    compileErrors: List<String> = emptyList(),
    onBarClick: ((segmentId: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
            if (compileErrors.isNotEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Chart cannot render — missing athlete profile data:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    compileErrors.forEach { error ->
                        Text(
                            "\u2022 $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Set the required values in the Athlete Profile section below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    "Add segments to see the workout profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        return
    }

    val isPercentMode = powerUnit == ChartPowerUnit.FTP_PERCENT
    val maxPower = bars.maxOf { maxOf(it.powerLow, it.powerHigh) }.coerceAtLeast(if (isPercentMode) 110 else 100)
    val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = surfaceVariant)

    // In percent mode, zone coloring is based on % FTP (100 = FTP threshold)
    val effectiveFtpForZones = if (isPercentMode) 100 else ftpWatts

    Column(modifier = modifier) {
        // Canvas fills all available vertical space
        // Pre-compute bar pixel ranges for hit testing
        val barRanges = remember(bars, totalDurationSec) {
            if (totalDurationSec <= 0) emptyList()
            else bars.mapNotNull { bar ->
                bar.segmentId?.let { id ->
                    Triple(bar.startSec.toFloat() / totalDurationSec, bar.endSec.toFloat() / totalDurationSec, id)
                }
            }
        }

        val canvasModifier = if (onBarClick != null) {
            Modifier.fillMaxWidth().weight(1f)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                .pointerInput(barRanges) {
                detectTapGestures { offset ->
                    val paddingLeft = 48f
                    val chartW = size.width - paddingLeft
                    if (chartW <= 0 || offset.x < paddingLeft) return@detectTapGestures
                    val fraction = (offset.x - paddingLeft) / chartW
                    val hit = barRanges.firstOrNull { (start, end, _) -> fraction in start..end }
                    if (hit != null) onBarClick(hit.third)
                }
            }
        } else {
            Modifier.fillMaxWidth().weight(1f)
        }

        Canvas(modifier = canvasModifier) {
            val w = size.width
            val h = size.height
            val paddingBottom = 20f
            val paddingLeft = 48f
            val chartW = w - paddingLeft
            val chartH = h - paddingBottom

            if (chartW <= 0 || chartH <= 0) return@Canvas

            // Y-axis grid lines
            val ySteps = listOf(0.25, 0.5, 0.75, 1.0)
            for (frac in ySteps) {
                val y = chartH * (1 - frac).toFloat()
                drawLine(
                    color = gridColor,
                    start = Offset(paddingLeft, y),
                    end = Offset(w, y),
                    strokeWidth = 0.5f,
                )
                val value = (maxPower * frac).toInt()
                val label = if (isPercentMode) "$value%" else "${value}W"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = paddingLeft - measured.size.width - 4f,
                        y = y - measured.size.height / 2f,
                    ),
                )
            }

            // FTP / 100% reference line
            if (isPercentMode) {
                // In percent mode, draw FTP = 100% line
                if (100 <= maxPower) {
                    val ftpY = chartH * (1 - 100f / maxPower)
                    drawLine(
                        color = ZoneColors.threshold.copy(alpha = 0.6f),
                        start = Offset(paddingLeft, ftpY),
                        end = Offset(w, ftpY),
                        strokeWidth = 1.5f,
                    )
                    val ftpLabel = "FTP"
                    val ftpMeasured = textMeasurer.measure(ftpLabel, labelStyle.copy(color = ZoneColors.threshold))
                    drawText(
                        textLayoutResult = ftpMeasured,
                        topLeft = Offset(w - ftpMeasured.size.width - 4f, ftpY - ftpMeasured.size.height - 2f),
                    )
                }
            } else if (ftpWatts != null && ftpWatts > 0 && ftpWatts <= maxPower) {
                val ftpY = chartH * (1 - ftpWatts.toFloat() / maxPower)
                drawLine(
                    color = ZoneColors.threshold.copy(alpha = 0.6f),
                    start = Offset(paddingLeft, ftpY),
                    end = Offset(w, ftpY),
                    strokeWidth = 1.5f,
                )
                val ftpLabel = "FTP"
                val ftpMeasured = textMeasurer.measure(ftpLabel, labelStyle.copy(color = ZoneColors.threshold))
                drawText(
                    textLayoutResult = ftpMeasured,
                    topLeft = Offset(w - ftpMeasured.size.width - 4f, ftpY - ftpMeasured.size.height - 2f),
                )
            }

            // Draw bars
            val hasHighlight = highlightedSegmentIds.isNotEmpty()
            if (totalDurationSec > 0) {
                for (bar in bars) {
                    val x1 = paddingLeft + (bar.startSec.toFloat() / totalDurationSec) * chartW
                    val x2 = paddingLeft + (bar.endSec.toFloat() / totalDurationSec) * chartW
                    val barW = (x2 - x1).coerceAtLeast(1f)
                    val isHighlighted = !hasHighlight || bar.segmentId in highlightedSegmentIds
                    val dimAlpha = if (isHighlighted) 1f else 0.3f

                    when (bar.type) {
                        EditorChartBarType.FREE_RIDE -> {
                            drawRect(
                                color = SegmentColors.freeRide.copy(alpha = 0.2f * dimAlpha),
                                topLeft = Offset(x1, 0f),
                                size = Size(barW, chartH),
                            )
                        }
                        EditorChartBarType.RAMP -> {
                            drawRampSliced(
                                x1 = x1, x2 = x2,
                                powerLow = bar.powerLow, powerHigh = bar.powerHigh,
                                maxPower = maxPower, chartH = chartH,
                                ftpWatts = effectiveFtpForZones,
                                alpha = dimAlpha,
                            )
                        }
                        else -> {
                            val color = barColor(bar, effectiveFtpForZones).copy(alpha = dimAlpha)
                            val barH = (bar.powerLow.toFloat() / maxPower) * chartH
                            drawRect(
                                color = color,
                                topLeft = Offset(x1, chartH - barH),
                                size = Size(barW, barH),
                            )
                        }
                    }
                }
            }

            // Baseline
            drawLine(
                color = surfaceVariant,
                start = Offset(paddingLeft, chartH),
                end = Offset(w, chartH),
                strokeWidth = 1f,
            )
            // Left axis
            drawLine(
                color = surfaceVariant,
                start = Offset(paddingLeft, 0f),
                end = Offset(paddingLeft, chartH),
                strokeWidth = 1f,
            )
        }

        // Duration axis labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatDurationCompact(totalDurationSec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun barColor(bar: EditorChartBar, ftpWatts: Int?): Color = when (bar.type) {
    EditorChartBarType.STEADY -> ZoneColors.forPower(bar.powerLow, ftpWatts)
    EditorChartBarType.RAMP -> ZoneColors.forPower((bar.powerLow + bar.powerHigh) / 2, ftpWatts)
    EditorChartBarType.FREE_RIDE -> SegmentColors.freeRide
    EditorChartBarType.HR -> SegmentColors.hr
}

/**
 * Draws a ramp as multiple vertical slices, each colored by its zone.
 * This reveals zone transitions within a single ramp segment.
 */
private fun DrawScope.drawRampSliced(
    x1: Float, x2: Float,
    powerLow: Int, powerHigh: Int,
    maxPower: Int, chartH: Float,
    ftpWatts: Int?,
    alpha: Float = 1f,
) {
    val totalW = x2 - x1
    for (i in 0 until RAMP_SLICES) {
        val frac0 = i.toFloat() / RAMP_SLICES
        val frac1 = (i + 1).toFloat() / RAMP_SLICES
        val sliceX1 = x1 + totalW * frac0
        val sliceX2 = x1 + totalW * frac1

        val p0 = powerLow + (powerHigh - powerLow) * frac0
        val p1 = powerLow + (powerHigh - powerLow) * frac1
        val midPower = ((p0 + p1) / 2).toInt()
        val color = ZoneColors.forPower(midPower, ftpWatts).copy(alpha = alpha)

        val y0 = chartH - (p0 / maxPower) * chartH
        val y1 = chartH - (p1 / maxPower) * chartH

        val path = Path().apply {
            moveTo(sliceX1, chartH)
            lineTo(sliceX1, y0)
            lineTo(sliceX2, y1)
            lineTo(sliceX2, chartH)
            close()
        }
        drawPath(path, color, style = Fill)
    }
}
