package com.example.ergometerapp.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionWorkoutMapperTest {
    @Test
    fun steadyStateMapsSuccessfully() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = 60,
                power = 0.75,
                cadence = 90,
            ),
        )

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 200))
        val segment = success.workout.segments.single()

        assertEquals(60, success.workout.totalDurationSec)
        assertTrue(segment is ExecutionSegment.Steady)

        val steady = segment as ExecutionSegment.Steady
        assertEquals(0, steady.sourceStepIndex)
        assertEquals(60, steady.durationSec)
        assertEquals(150, steady.targetWatts)
        assertEquals(CadenceTarget.FixedCadence(90), steady.cadence)
    }

    @Test
    fun rampMapsSuccessfully() {
        val workout = workoutWithSteps(
            Step.Ramp(
                durationSec = 120,
                powerLow = 0.5,
                powerHigh = 1.0,
                cadence = null,
            ),
        )

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 250))
        val segment = success.workout.segments.single()

        assertEquals(120, success.workout.totalDurationSec)
        assertTrue(segment is ExecutionSegment.Ramp)

        val ramp = segment as ExecutionSegment.Ramp
        assertEquals(0, ramp.sourceStepIndex)
        assertEquals(120, ramp.durationSec)
        assertEquals(125, ramp.startWatts)
        assertEquals(250, ramp.endWatts)
        assertEquals(CadenceTarget.AnyCadence, ramp.cadence)
    }

    @Test
    fun warmupMapsToRampLowToHigh() {
        val workout = workoutWithSteps(
            Step.Warmup(
                durationSec = 30,
                powerLow = 0.5,
                powerHigh = 0.7,
                cadence = 90,
            ),
        )

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 200))
        val segment = success.workout.segments.single() as ExecutionSegment.Ramp

        assertEquals(30, segment.durationSec)
        assertEquals(100, segment.startWatts)
        assertEquals(140, segment.endWatts)
        assertEquals(CadenceTarget.FixedCadence(90), segment.cadence)
    }

    @Test
    fun cooldownMapsFromHigherPowerToLowerPowerEvenWhenFieldsAreSwapped() {
        val workout = workoutWithSteps(
            Step.Cooldown(
                durationSec = 40,
                powerLow = 0.2,
                powerHigh = 0.7,
                cadence = null,
            ),
        )

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 200))
        val segment = success.workout.segments.single() as ExecutionSegment.Ramp

        assertEquals(40, segment.durationSec)
        assertEquals(140, segment.startWatts)
        assertEquals(40, segment.endWatts)
    }

    @Test
    fun intervalsExpandToAlternatingSteadySegments() {
        val workout = workoutWithSteps(
            Step.IntervalsT(
                onDurationSec = 5,
                offDurationSec = 10,
                onPower = 0.9,
                offPower = 0.5,
                repeat = 3,
                cadence = 95,
            ),
        )

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 200))
        val segments = success.workout.segments

        assertEquals(6, segments.size)
        assertEquals(45, success.workout.totalDurationSec)
        assertTrue(segments.all { it is ExecutionSegment.Steady })

        val firstOn = segments[0] as ExecutionSegment.Steady
        val firstOff = segments[1] as ExecutionSegment.Steady
        assertEquals(5, firstOn.durationSec)
        assertEquals(180, firstOn.targetWatts)
        assertEquals(CadenceTarget.FixedCadence(95), firstOn.cadence)
        assertEquals(IntervalSegmentMetadata(IntervalPhase.ON, 1, 3), firstOn.intervalMetadata)
        assertEquals(10, firstOff.durationSec)
        assertEquals(100, firstOff.targetWatts)
        assertEquals(CadenceTarget.FixedCadence(95), firstOff.cadence)
        assertEquals(IntervalSegmentMetadata(IntervalPhase.OFF, 1, 3), firstOff.intervalMetadata)
    }

    @Test
    fun ftpLessThanOrEqualToZeroFailsWithInvalidFtp() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = 60,
                power = 0.8,
                cadence = 85,
            ),
        )

        listOf(0, -1).forEach { ftp ->
            val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = ftp))
            assertContainsErrorCode(failure, MappingErrorCode.INVALID_FTP)
        }
    }

    @Test
    fun freeRideMapsSuccessfully() {
        val workout = workoutWithSteps(
            Step.FreeRide(
                durationSec = 60,
                cadence = 88,
            ),
        )

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 200))
        val segment = success.workout.segments.single()
        assertTrue(segment is ExecutionSegment.FreeRide)

        val freeRide = segment as ExecutionSegment.FreeRide
        assertEquals(60, freeRide.durationSec)
        assertEquals(CadenceTarget.FixedCadence(88), freeRide.cadence)
        assertEquals(60, success.workout.totalDurationSec)
    }

    @Test
    fun parsedFreeRideWithPowerAttributeMapsWithoutDegradedFallback() {
        val xml = """
            <workout_file>
              <workout>
                <FreeRide Duration="20" Power="0.65" Cadence="65"/>
              </workout>
            </workout_file>
        """.trimIndent()
        val workout = parseZwo(xml)

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 200))
        val segment = success.workout.segments.single() as ExecutionSegment.FreeRide

        assertEquals(20, segment.durationSec)
        assertEquals(CadenceTarget.FixedCadence(65), segment.cadence)
    }

    @Test
    fun unknownStepFailsWithUnsupportedStep() {
        val workout = workoutWithSteps(
            Step.Unknown(
                tagName = "CustomStep",
                attributes = mapOf("Duration" to "60"),
            ),
        )

        val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = 200))
        assertContainsErrorCode(failure, MappingErrorCode.UNSUPPORTED_STEP)
    }

    @Test
    fun missingDurationFailsWithMissingDuration() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = null,
                power = 0.6,
                cadence = null,
            ),
        )

        val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = 200))
        assertContainsErrorCode(failure, MappingErrorCode.MISSING_DURATION)
    }

    @Test
    fun invalidPowerFailsWithInvalidPower() {
        listOf(Double.NaN, -0.1).forEach { power ->
            val workout = workoutWithSteps(
                Step.SteadyState(
                    durationSec = 45,
                    power = power,
                    cadence = null,
                ),
            )

            val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = 200))
            assertContainsErrorCode(failure, MappingErrorCode.INVALID_POWER)
        }
    }

    @Test
    fun emptyStepsFailsWithNoSupportedSteps() {
        val failure = requireFailure(ExecutionWorkoutMapper.map(workoutWithSteps(), ftp = 200))
        assertContainsErrorCode(failure, MappingErrorCode.NO_SUPPORTED_STEPS)
    }

    @Test
    fun missingRepeatFailsWithMissingRepeat() {
        val workout = workoutWithSteps(
            Step.IntervalsT(
                onDurationSec = 5,
                offDurationSec = 5,
                onPower = 0.8,
                offPower = 0.5,
                repeat = null,
                cadence = null,
            ),
        )

        val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = 200))
        assertContainsErrorCode(failure, MappingErrorCode.MISSING_REPEAT)
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

    private fun requireSuccess(result: MappingResult): MappingResult.Success {
        return result as? MappingResult.Success
            ?: throw AssertionError("Expected MappingResult.Success, got $result")
    }

    private fun requireFailure(result: MappingResult): MappingResult.Failure {
        return result as? MappingResult.Failure
            ?: throw AssertionError("Expected MappingResult.Failure, got $result")
    }

    private fun assertContainsErrorCode(failure: MappingResult.Failure, code: MappingErrorCode) {
        assertTrue("Expected error code $code, got ${failure.errors.map { it.code }}", failure.errors.any { it.code == code })
    }
}
