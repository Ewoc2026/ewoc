package io.github.ewoc2026.ewoc.session

import io.github.ewoc2026.ewoc.AppFailure
import io.github.ewoc2026.ewoc.AppUiState
import io.github.ewoc2026.ewoc.workout.ExecutionWorkout
import io.github.ewoc2026.ewoc.workout.MappingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionWorkoutReadinessEvaluatorTest {

    private data class DiagnosticsRecord(
        val category: String,
        val event: String,
        val context: Map<String, String>,
    )

    private class Harness {
        val uiState = AppUiState()
        val diagnostics = mutableListOf<DiagnosticsRecord>()
        var executionMappingFailureSignalCount = 0

        val evaluator = SessionWorkoutReadinessEvaluator(
            uiState = uiState,
            currentFtpWatts = { 200 },
            allowLegacyWorkoutFallback = false,
            toUserMessage = { failure -> "msg:${failure.reason.stableCode}" },
            onExecutionMappingFailure = { executionMappingFailureSignalCount++ },
            recordDiagnostics = { category, event, context ->
                diagnostics += DiagnosticsRecord(category, event, context)
            },
        )
    }

    @Test
    fun successfulMappingClearsFailureState() {
        val harness = Harness()
        harness.uiState.workoutExecutionModeMessage.value = "old error"
        harness.uiState.workoutExecutionModeIsError.value = true

        val result = harness.evaluator.evaluateMappedWorkoutExecutionEligibility(
            mapped = MappingResult.Success(
                ExecutionWorkout(name = "", description = "", author = "", tags = emptyList(), segments = emptyList(), totalDurationSec = 0),
            ),
            source = "test",
        )

        assertTrue(result)
        assertNull(harness.uiState.workoutExecutionModeMessage.value)
        assertFalse(harness.uiState.workoutExecutionModeIsError.value)
    }

    @Test
    fun blockedMappingFailureSetsErrorStateAndEmitsDiagnostics() {
        val harness = Harness()

        val result = harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "eligibility_check",
            allowLegacyFallback = false,
        )

        assertFalse(result)
        assertEquals(
            "msg:workout.execution_mapping_blocked",
            harness.uiState.workoutExecutionModeMessage.value,
        )
        assertTrue(harness.uiState.workoutExecutionModeIsError.value)
        assertEquals(1, harness.diagnostics.size)
        assertEquals("mapping_policy_applied", harness.diagnostics[0].event)
        assertEquals("false", harness.diagnostics[0].context["legacyFallbackEnabled"])
    }

    @Test
    fun degradedMappingFailureAllowsExecution() {
        val harness = Harness()

        val result = harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "runner_creation",
            allowLegacyFallback = true,
        )

        assertTrue(result)
        assertTrue(harness.uiState.workoutExecutionModeIsError.value)
        assertEquals(1, harness.diagnostics.size)
        assertEquals("true", harness.diagnostics[0].context["legacyFallbackEnabled"])
    }

    @Test
    fun repeatedIdenticalFailureSignalIsDeduplicatedToOneCallback() {
        val harness = Harness()

        harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "check1",
            allowLegacyFallback = false,
        )
        harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "check2",
            allowLegacyFallback = false,
        )

        assertEquals(1, harness.executionMappingFailureSignalCount)
    }

    @Test
    fun differentFailureSignalEmitsSecondCallback() {
        val harness = Harness()

        harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "check1",
            allowLegacyFallback = false,
        )
        harness.evaluator.handleExecutionMappingFailure(
            summary = "DIFFERENT_ERROR",
            source = "check2",
            allowLegacyFallback = false,
        )

        assertEquals(2, harness.executionMappingFailureSignalCount)
    }

    @Test
    fun clearLastFailureSignalAllowsResignal() {
        val harness = Harness()

        harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "check1",
            allowLegacyFallback = false,
        )
        harness.evaluator.clearLastFailureSignal()
        harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "check2",
            allowLegacyFallback = false,
        )

        assertEquals(2, harness.executionMappingFailureSignalCount)
    }

    @Test
    fun successAfterFailureClearsSignalState() {
        val harness = Harness()

        harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "check1",
            allowLegacyFallback = false,
        )

        harness.evaluator.evaluateMappedWorkoutExecutionEligibility(
            mapped = MappingResult.Success(
                ExecutionWorkout(name = "", description = "", author = "", tags = emptyList(), segments = emptyList(), totalDurationSec = 0),
            ),
            source = "recheck",
        )

        // Same failure after success should signal again since success cleared the key
        harness.evaluator.handleExecutionMappingFailure(
            summary = "UNSUPPORTED_STEP",
            source = "check3",
            allowLegacyFallback = false,
        )

        assertEquals(2, harness.executionMappingFailureSignalCount)
    }
}
