package io.github.ewoc2026.ewoc.ui.components

import com.ewo.editor.model.ChartPowerUnit
import com.ewo.editor.model.EditorChartBar
import com.ewo.editor.model.EditorChartBarType
import io.github.ewoc2026.ewoc.workout.CadenceTarget
import io.github.ewoc2026.ewoc.workout.ExecutionSegment
import io.github.ewoc2026.ewoc.workout.ExecutionWorkout
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutStep
import io.github.ewoc2026.ewoc.workout.Step
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutProfileChartMappingTest {

    @Test
    fun cooldownRampUsesPowerHighToPowerLowDirection() {
        val workout = workoutWithSteps(
            Step.Cooldown(
                durationSec = 120,
                powerLow = 0.10,
                powerHigh = 0.60,
                cadence = 85,
            ),
        )

        val segments = buildWorkoutProfileSegments(workout)

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals(SegmentKind.RAMP, segment.kind)
        assertEquals(0, segment.startSec)
        assertEquals(120, segment.durationSec)
        assertEquals(0.60, segment.startPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(0.10, segment.endPowerRelFtp ?: 0.0, 0.0001)
    }

    @Test
    fun cooldownRampUsesHigherToLowerEvenWhenFieldsAreSwapped() {
        val workout = workoutWithSteps(
            Step.Cooldown(
                durationSec = 120,
                powerLow = 0.80,
                powerHigh = 0.40,
                cadence = 85,
            ),
        )

        val segments = buildWorkoutProfileSegments(workout)

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals(SegmentKind.RAMP, segment.kind)
        assertEquals(0, segment.startSec)
        assertEquals(120, segment.durationSec)
        assertEquals(0.80, segment.startPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(0.40, segment.endPowerRelFtp ?: 0.0, 0.0001)
    }

    @Test
    fun intervalsExpandToAlternatingOnOffSegmentsWithCorrectTimeline() {
        val workout = workoutWithSteps(
            Step.IntervalsT(
                onDurationSec = 30,
                offDurationSec = 15,
                onPower = 1.20,
                offPower = 0.50,
                repeat = 3,
                cadence = 95,
            ),
        )

        val segments = buildWorkoutProfileSegments(workout)

        assertEquals(6, segments.size)
        assertEquals(135, segments.sumOf { it.durationSec })
        assertEquals(listOf(0, 30, 45, 75, 90, 120), segments.map { it.startSec })
        assertEquals(
            listOf(1.20, 0.50, 1.20, 0.50, 1.20, 0.50),
            segments.map { it.startPowerRelFtp ?: 0.0 },
        )
        assertTrue(segments.all { it.kind == SegmentKind.STEADY })
    }

    @Test
    fun invalidStepsAreSkippedWithoutAdvancingTimeline() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = null,
                power = 0.8,
                cadence = null,
            ),
            Step.Ramp(
                durationSec = 60,
                powerLow = null,
                powerHigh = 1.1,
                cadence = null,
            ),
            Step.FreeRide(
                durationSec = -10,
                cadence = null,
            ),
            Step.Unknown(
                tagName = "CustomStep",
                attributes = mapOf("Duration" to "30"),
            ),
            Step.SteadyState(
                durationSec = 45,
                power = 0.7,
                cadence = 90,
            ),
        )

        val segments = buildWorkoutProfileSegments(workout)

        assertEquals(1, segments.size)
        val only = segments.single()
        assertEquals(0, only.startSec)
        assertEquals(45, only.durationSec)
        assertEquals(SegmentKind.STEADY, only.kind)
        assertEquals(0.7, only.startPowerRelFtp ?: 0.0, 0.0001)
    }

    @Test
    fun executionWorkoutUsesAbsoluteWattsRelativeToFtp() {
        val workout = ExecutionWorkout(
            name = "Imported EWO",
            description = "",
            author = "",
            tags = emptyList(),
            totalDurationSec = 210,
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 60,
                    targetWatts = 180,
                    cadence = CadenceTarget.AnyCadence,
                ),
                ExecutionSegment.Ramp(
                    sourceStepIndex = 1,
                    durationSec = 90,
                    startWatts = 200,
                    endWatts = 260,
                    cadence = CadenceTarget.AnyCadence,
                ),
                ExecutionSegment.FreeRide(
                    sourceStepIndex = 2,
                    durationSec = 60,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
        )

        val segments = buildWorkoutProfileSegments(workout = workout, ftpWatts = 200)

        assertEquals(3, segments.size)
        assertEquals(listOf(0, 60, 150), segments.map { it.startSec })
        assertEquals(SegmentKind.STEADY, segments[0].kind)
        assertEquals(0.9, segments[0].startPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(SegmentKind.RAMP, segments[1].kind)
        assertEquals(1.0, segments[1].startPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(1.3, segments[1].endPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(SegmentKind.FREERIDE, segments[2].kind)
        assertEquals(null, segments[2].startPowerRelFtp)
    }

    @Test
    fun executionWorkoutReturnsEmptySegmentsWhenFtpIsInvalid() {
        val workout = ExecutionWorkout(
            name = "Imported EWO",
            description = "",
            author = "",
            tags = emptyList(),
            totalDurationSec = 60,
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 60,
                    targetWatts = 180,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
        )

        val segments = buildWorkoutProfileSegments(workout = workout, ftpWatts = 0)

        assertTrue(segments.isEmpty())
    }

    @Test
    fun executionWorkoutUsesInitialPowerForHeartRateSteadyChartPreview() {
        val workout = ExecutionWorkout(
            name = "Imported HR EWO",
            description = "",
            author = "",
            tags = emptyList(),
            totalDurationSec = 300,
            segments = listOf(
                ExecutionSegment.HeartRateSteady(
                    sourceStepIndex = 0,
                    durationSec = 300,
                    targetLowBpm = 140,
                    targetHighBpm = 150,
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                    hrUpperCapBpm = 185,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
        )

        val segments = buildWorkoutProfileSegments(workout = workout, ftpWatts = 200)

        assertEquals(1, segments.size)
        assertEquals(SegmentKind.STEADY, segments[0].kind)
        assertEquals(0.9, segments[0].startPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(0.9, segments[0].endPowerRelFtp ?: 0.0, 0.0001)
    }

    @Test
    fun importedErgoWorkoutMapsToChartSegmentsThroughSharedPreviewHelper() {
        val workout = ImportedErgoWorkout(
            title = "Imported EWO",
            description = "Shared menu/session preview coverage",
            totalDurationSec = 180,
            steps = listOf(
                ImportedErgoWorkoutStep.PowerSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 60,
                    watts = 150,
                ),
                ImportedErgoWorkoutStep.PowerRamp(
                    stepIndex = 1,
                    startOffsetSec = 60,
                    durationSec = 120,
                    fromWatts = 180,
                    toWatts = 240,
                ),
            ),
        )

        val segments = buildWorkoutProfileSegments(workout = workout, ftpWatts = 200)

        assertEquals(2, segments.size)
        assertEquals(listOf(0, 60), segments.map { it.startSec })
        assertEquals(SegmentKind.STEADY, segments[0].kind)
        assertEquals(0.75, segments[0].startPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(SegmentKind.RAMP, segments[1].kind)
        assertEquals(0.9, segments[1].startPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(1.2, segments[1].endPowerRelFtp ?: 0.0, 0.0001)
    }

    @Test
    fun rampRenderSlicesExposeZoneTransitionsWithinOneRamp() {
        val slices = buildRampRenderSlices(
            startPowerRelFtp = 0.50,
            endPowerRelFtp = 1.10,
            sliceCount = 8,
        )

        assertEquals(8, slices.size)
        assertEquals(0.50, slices.first().startPowerRelFtp, 0.0001)
        assertEquals(1.10, slices.last().endPowerRelFtp, 0.0001)
        assertEquals(Color(0xFF9AA6B2), slices[0].color)
        assertEquals(Color(0xFF23A6D5), slices[1].color)
        assertEquals(Color(0xFF2FBF71), slices[3].color)
        assertEquals(Color(0xFFF3B400), slices[5].color)
        assertEquals(Color(0xFFFF7A3D), slices[7].color)
    }

    @Test
    fun editorChartBarColorUsesFtpForWattBasedBars() {
        val color = editorChartBarColor(
            bar = EditorChartBar(
                startSec = 0,
                endSec = 60,
                powerLow = 180,
                powerHigh = 180,
                type = EditorChartBarType.STEADY,
            ),
            ftpWatts = 200,
            powerUnit = ChartPowerUnit.WATTS,
            fallbackChartMax = 260,
        )

        assertEquals(Color(0xFF2FBF71), color)
    }

    @Test
    fun editorChartBarColorUsesPercentScaleWhenPreviewIsFtpBased() {
        val color = editorChartBarColor(
            bar = EditorChartBar(
                startSec = 0,
                endSec = 60,
                powerLow = 105,
                powerHigh = 105,
                type = EditorChartBarType.STEADY,
            ),
            ftpWatts = null,
            powerUnit = ChartPowerUnit.FTP_PERCENT,
            fallbackChartMax = 140,
        )

        assertEquals(Color(0xFFF3B400), color)
    }

    private fun workoutWithSteps(vararg steps: Step): WorkoutFile {
        return WorkoutFile(
            name = "Test workout",
            description = null,
            author = null,
            tags = emptyList(),
            steps = steps.toList(),
        )
    }
}
