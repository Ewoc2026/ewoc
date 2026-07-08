package io.github.ewoc2026.ewoc.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedErgoWorkoutExecutionMapperTest {
    @Test
    fun mapsPowerOnlyImportedWorkoutToExecutionSegments() {
        val workout = ImportedErgoWorkout(
            title = "Power builder",
            description = "Absolute watts only",
            steps = listOf(
                ImportedErgoWorkoutStep.PowerSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 300,
                    watts = 210,
                    canonicalMetadata = ImportedErgoWorkoutStepCanonicalMetadata(
                        messages = listOf(
                            ImportedErgoWorkoutMessage(
                                kind = ImportedErgoWorkoutMessageKind.INSTRUCTION,
                                timing = ImportedErgoWorkoutMessageTiming(
                                    anchor = ImportedErgoWorkoutMessageAnchor.START,
                                    offsetSec = 0,
                                ),
                                text = ImportedErgoWorkoutLocalizedText(
                                    defaultText = "Settle in.",
                                    translations = emptyMap(),
                                ),
                            ),
                        ),
                        origin = ImportedErgoWorkoutStepOrigin(
                            sourceSegmentId = "steady-1",
                            sourceSegmentLabel = null,
                            sourceSegmentNote = null,
                            enclosingRepeatSegmentId = null,
                            repeatIterationIndex = null,
                        ),
                    ),
                ),
                ImportedErgoWorkoutStep.PowerRamp(
                    stepIndex = 1,
                    startOffsetSec = 300,
                    durationSec = 120,
                    fromWatts = 210,
                    toWatts = 260,
                ),
            ),
            totalDurationSec = 420,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = ImportedErgoWorkoutControl(
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 320,
                    signalLossPowerWatts = 160,
                    hrUpperCapBpm = 185,
                ),
                messages = listOf(
                    ImportedErgoWorkoutMessage(
                        kind = ImportedErgoWorkoutMessageKind.INTRO,
                        timing = ImportedErgoWorkoutMessageTiming(
                            anchor = ImportedErgoWorkoutMessageAnchor.START,
                            offsetSec = 0,
                        ),
                        text = ImportedErgoWorkoutLocalizedText(
                            defaultText = "Ready to work.",
                            translations = emptyMap(),
                        ),
                    ),
                ),
            ),
        )

        val result = ImportedErgoWorkoutExecutionMapper.map(workout)

        val success = result as? MappingResult.Success
            ?: throw AssertionError("Expected success, got $result")
        assertEquals(
            ImportedErgoWorkoutCanonicalControlUsage.PRESERVED_IMPORT_ONLY,
            ImportedErgoWorkoutExecutionPolicy.canonicalControlUsage(workout),
        )
        assertEquals("Power builder", success.workout.name)
        assertEquals("Absolute watts only", success.workout.description)
        assertEquals(2, success.workout.segments.size)
        assertEquals(420, success.workout.totalDurationSec)

        val steady = success.workout.segments[0] as ExecutionSegment.Steady
        assertEquals(0, steady.sourceStepIndex)
        assertEquals(300, steady.durationSec)
        assertEquals(210, steady.targetWatts)
        assertEquals(CadenceTarget.AnyCadence, steady.cadence)

        val ramp = success.workout.segments[1] as ExecutionSegment.Ramp
        assertEquals(1, ramp.sourceStepIndex)
        assertEquals(120, ramp.durationSec)
        assertEquals(210, ramp.startWatts)
        assertEquals(260, ramp.endWatts)
    }

    @Test
    fun blocksHeartRateControlledImportedWorkoutWithStableErrorCode() {
        val workout = ImportedErgoWorkout(
            title = "HR builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.HeartRateSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 600,
                    lowBpm = 140,
                    highBpm = 150,
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                ),
            ),
            totalDurationSec = 600,
        )

        val result = ImportedErgoWorkoutExecutionMapper.map(workout)

        val failure = result as? MappingResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(1, failure.errors.size)
        assertEquals(
            MappingErrorCode.UNSUPPORTED_HEART_RATE_TARGET,
            failure.errors.single().code,
        )
        assertTrue(
            failure.errors.single().message.contains(
                "canonical workout-level control metadata is required",
            ),
        )
    }

    @Test
    fun canonicalHeartRateControlMapsToHeartRateSteadyExecutionSegment() {
        val workout = ImportedErgoWorkout(
            title = "Canonical HR builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.HeartRateSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 600,
                    lowBpm = 140,
                    highBpm = 150,
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                ),
            ),
            totalDurationSec = 600,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = ImportedErgoWorkoutControl(
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                    hrUpperCapBpm = 185,
                ),
                messages = emptyList(),
            ),
        )

        val result = ImportedErgoWorkoutExecutionMapper.map(workout)

        val success = result as? MappingResult.Success
            ?: throw AssertionError("Expected success, got $result")
        assertEquals(
            ImportedErgoWorkoutCanonicalControlUsage.PRESERVED_IMPORT_ONLY,
            ImportedErgoWorkoutExecutionPolicy.canonicalControlUsage(workout),
        )
        assertEquals(1, success.workout.segments.size)
        val hrStep = success.workout.segments.single() as? ExecutionSegment.HeartRateSteady
            ?: throw AssertionError("Expected HR steady execution segment")
        assertEquals(140, hrStep.targetLowBpm)
        assertEquals(150, hrStep.targetHighBpm)
        assertEquals(180, hrStep.initialPowerWatts)
        assertEquals(120, hrStep.minPowerWatts)
        assertEquals(260, hrStep.maxPowerWatts)
        assertEquals(150, hrStep.signalLossPowerWatts)
        assertEquals(185, hrStep.hrUpperCapBpm)
    }

    @Test
    fun resolvesV1HeartRateExecutionPolicyFromCanonicalImportedWorkout() {
        val workout = ImportedErgoWorkout(
            title = "Canonical HR builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.HeartRateSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 600,
                    lowBpm = 140,
                    highBpm = 150,
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                ),
            ),
            totalDurationSec = 600,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = ImportedErgoWorkoutControl(
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                    hrUpperCapBpm = 185,
                ),
                messages = emptyList(),
            ),
        )

        val step = workout.steps.single() as ImportedErgoWorkoutStep.HeartRateSteady

        val resolution = ImportedErgoWorkoutExecutionPolicy.resolveHeartRateExecutionPolicy(
            workout = workout,
            step = step,
        )

        val available = resolution as? ImportedHrExecutionPolicyResolution.Available
            ?: throw AssertionError("Expected an available policy, got $resolution")
        assertEquals(140, available.policy.targetLowBpm)
        assertEquals(150, available.policy.targetHighBpm)
        assertEquals(180, available.policy.initialPowerWatts)
        assertEquals(120, available.policy.minPowerWatts)
        assertEquals(260, available.policy.maxPowerWatts)
        assertEquals(150, available.policy.signalLossPowerWatts)
        assertEquals(185, available.policy.hrUpperCapBpm)
        assertEquals(
            setOf(
                ImportedHrExecutionCapability.HEART_RATE_SIGNAL,
                ImportedHrExecutionCapability.TRAINER_CONTROL,
            ),
            available.policy.requiredCapabilities,
        )
        assertEquals(
            HrUnavailableAtStartBehavior.FAIL_START,
            available.policy.unavailableAtStartBehavior,
        )
        assertEquals(
            HrSignalLossBehavior.FALLBACK_THEN_STOP,
            available.policy.signalLossBehavior,
        )
        assertEquals(
            HrCapBehavior.THROTTLE_THEN_STOP,
            available.policy.capBehavior,
        )
        assertEquals(
            HrUnreachableTargetBehavior.HOLD_AT_BOUND_WITH_STATUS,
            available.policy.unreachableTargetBehavior,
        )
    }

    @Test
    fun canonicalHeartRatePolicyRequiresWorkoutLevelControlMetadata() {
        val workout = ImportedErgoWorkout(
            title = "Legacy HR builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.HeartRateSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 600,
                    lowBpm = 140,
                    highBpm = 150,
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                ),
            ),
            totalDurationSec = 600,
            canonicalMetadata = null,
        )

        val step = workout.steps.single() as ImportedErgoWorkoutStep.HeartRateSteady

        val resolution = ImportedErgoWorkoutExecutionPolicy.resolveHeartRateExecutionPolicy(
            workout = workout,
            step = step,
        )

        assertEquals(
            ImportedHrExecutionPolicyResolution.MissingCanonicalControl,
            resolution,
        )
    }

    @Test
    fun heartRateStartCapabilityEvaluationReportsMissingCapabilities() {
        val workout = ImportedErgoWorkout(
            title = "Canonical HR builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.HeartRateSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 600,
                    lowBpm = 140,
                    highBpm = 150,
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                ),
            ),
            totalDurationSec = 600,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = ImportedErgoWorkoutControl(
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                    hrUpperCapBpm = 185,
                ),
                messages = emptyList(),
            ),
        )

        val step = workout.steps.single() as ImportedErgoWorkoutStep.HeartRateSteady

        val evaluation = ImportedErgoWorkoutExecutionPolicy.evaluateHeartRateStartCapabilities(
            workout = workout,
            step = step,
            snapshot = ImportedHrExecutionCapabilitySnapshot(
                hasHeartRateSignal = false,
                hasTrainerControl = true,
            ),
        )

        val available = evaluation as? ImportedHrExecutionStartCapabilityEvaluation.PolicyAvailable
            ?: throw AssertionError("Expected a policy-backed evaluation, got $evaluation")
        assertFalse(available.isReadyAtStart)
        assertEquals(
            setOf(ImportedHrExecutionCapability.HEART_RATE_SIGNAL),
            available.missingCapabilities,
        )
    }

    @Test
    fun calculatesPlannedTssFromImportedExecutionWorkout() {
        val workout = ImportedErgoWorkout(
            title = "Tempo block",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.PowerSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 3600,
                    watts = 200,
                ),
            ),
            totalDurationSec = 3600,
        )

        val mapped = ImportedErgoWorkoutExecutionMapper.map(workout) as MappingResult.Success

        val plannedTss = WorkoutPlannedTssCalculator.calculate(
            workout = mapped.workout,
            ftpWatts = 250,
        )

        assertEquals(64.0, requireNotNull(plannedTss), 0.0)
    }
}
