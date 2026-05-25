package com.example.ergometerapp.session

import android.util.Log
import com.example.ergometerapp.AppFailure
import com.example.ergometerapp.AppFailureFactory
import com.example.ergometerapp.AppUiState
import com.example.ergometerapp.workout.ImportedErgoWorkout
import com.example.ergometerapp.workout.ImportedErgoWorkoutExecutionMapper
import com.example.ergometerapp.workout.ImportedErgoWorkoutStep
import com.example.ergometerapp.workout.ImportedHrExecutionCapability
import com.example.ergometerapp.workout.ImportedHrExecutionCapabilitySnapshot
import com.example.ergometerapp.workout.ExecutionWorkoutMapper
import com.example.ergometerapp.workout.MappingErrorCode
import com.example.ergometerapp.workout.MappingResult
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.runner.ImportedHrRuntimeCommand

/**
 * Evaluates whether a selected workout is ready for execution under the
 * current strict/fallback mapping policy and updates UI readiness state.
 *
 * Owns the mapping-failure signaling deduplication so the orchestrator
 * does not need to track whether a failure was already surfaced.
 */
internal class SessionWorkoutReadinessEvaluator(
    private val uiState: AppUiState,
    private val currentFtpWatts: () -> Int,
    private val allowLegacyWorkoutFallback: Boolean,
    private val toUserMessage: (AppFailure) -> String,
    private val onExecutionMappingFailure: () -> Unit,
    private val recordDiagnostics: (category: String, event: String, context: Map<String, String>) -> Unit,
) {
    private var lastExecutionFailureSignalKey: String? = null

    fun clearLastFailureSignal() {
        lastExecutionFailureSignalKey = null
    }

    fun evaluateWorkoutExecutionEligibility(workout: WorkoutFile): Boolean {
        val ftpWatts = currentFtpWatts().coerceAtLeast(1)
        return evaluateMappedWorkoutExecutionEligibility(
            mapped = ExecutionWorkoutMapper.map(workout, ftp = ftpWatts),
            source = "eligibility_check",
        )
    }

    fun evaluateImportedWorkoutExecutionEligibility(
        workout: ImportedErgoWorkout,
        source: String,
    ): Boolean {
        val mapped = ImportedErgoWorkoutExecutionMapper.map(workout)
        if (mapped is MappingResult.Success && hasImportedHeartRateSteps(workout)) {
            uiState.workoutExecutionModeMessage.value = null
            uiState.workoutExecutionModeIsError.value = false
            lastExecutionFailureSignalKey = null
            recordDiagnostics(
                "workout_execution",
                "mapping_policy_applied",
                mapOf(
                    "reason" to "imported_hr_runtime_deferred_to_session_start",
                    "source" to source,
                    "legacyFallbackEnabled" to false.toString(),
                    "summary" to "IMPORTED_HR_RUNTIME_DEFERRED_TO_SESSION_START",
                ),
            )
            return true
        }
        val failureContext = if (mapped is MappingResult.Failure) {
            importedWorkoutExecutionFailureContext(workout, mapped)
        } else {
            null
        }
        return evaluateMappedWorkoutExecutionEligibility(
            mapped = mapped,
            source = source,
            allowLegacyFallback = false,
            failureDetail = failureContext?.detail,
            diagnosticsContext = failureContext?.diagnosticsContext.orEmpty(),
        )
    }

    fun blockImportedHeartRateExecutionUntilRuntimeEnabled(
        workout: ImportedErgoWorkout,
        source: String,
    ): Boolean {
        val diagnosticsContext = mutableMapOf<String, String>()
        val detail = importedHrExecutionDetail(
            workout = workout,
            diagnosticsContext = diagnosticsContext,
        )
        diagnosticsContext["summary"] = MappingErrorCode.UNSUPPORTED_HEART_RATE_TARGET.name
        return handleExecutionMappingFailure(
            summary = MappingErrorCode.UNSUPPORTED_HEART_RATE_TARGET.name,
            source = source,
            allowLegacyFallback = false,
            detail = detail,
            diagnosticsContext = diagnosticsContext.toMap(),
        )
    }

    fun evaluateMappedWorkoutExecutionEligibility(
        mapped: MappingResult,
        source: String,
        allowLegacyFallback: Boolean = allowLegacyWorkoutFallback,
        failureDetail: String? = null,
        diagnosticsContext: Map<String, String> = emptyMap(),
    ): Boolean {
        return when (mapped) {
            is MappingResult.Success -> {
                uiState.workoutExecutionModeMessage.value = null
                uiState.workoutExecutionModeIsError.value = false
                lastExecutionFailureSignalKey = null
                true
            }

            is MappingResult.Failure -> {
                handleExecutionMappingFailure(
                    summary = mappingFailureSummary(mapped),
                    source = source,
                    allowLegacyFallback = allowLegacyFallback,
                    detail = failureDetail,
                    diagnosticsContext = diagnosticsContext,
                )
            }
        }
    }

    /**
     * Applies strict/degraded execution policy consistently and emits diagnostics
     * so operators can distinguish blocked starts from permissive fallback runs.
     */
    fun handleExecutionMappingFailure(
        summary: String,
        source: String,
        allowLegacyFallback: Boolean = allowLegacyWorkoutFallback,
        detail: String? = null,
        diagnosticsContext: Map<String, String> = emptyMap(),
    ): Boolean {
        val resolvedFailure = if (allowLegacyFallback) {
            AppFailureFactory.workoutExecutionMappingDegraded(
                mappingSummary = summary,
                detail = detail,
            )
        } else {
            AppFailureFactory.workoutExecutionMappingBlocked(
                mappingSummary = summary,
                detail = detail,
            )
        }
        uiState.workoutExecutionModeMessage.value = toUserMessage(resolvedFailure)
        uiState.workoutExecutionModeIsError.value = true
        signalExecutionMappingFailure(signalKey = "${resolvedFailure.reason.stableCode}:$summary")
        recordDiagnostics(
            "workout_execution",
            "mapping_policy_applied",
            mapOf(
                "reason" to resolvedFailure.reason.stableCode,
                "source" to source,
                "legacyFallbackEnabled" to allowLegacyFallback.toString(),
                "summary" to summary,
            ) + diagnosticsContext,
        )
        if (allowLegacyFallback) {
            Log.w(
                "WORKOUT",
                "Execution mapping failed; degraded mode active with legacy fallback: $summary detail=$detail",
            )
            return true
        }
        Log.e(
            "WORKOUT",
            "Execution mapping failed with fallback disabled; execution blocked: $summary detail=$detail",
        )
        return false
    }

    fun importedWorkoutExecutionFailureContext(
        workout: ImportedErgoWorkout,
        mapped: MappingResult.Failure,
    ): ImportedWorkoutExecutionFailureContext {
        val baseSummary = mappingFailureSummary(mapped)
        val diagnosticsContext = mutableMapOf<String, String>()
        val detail = importedHrExecutionDetail(
            workout = workout,
            diagnosticsContext = diagnosticsContext,
        )
        diagnosticsContext["summary"] = baseSummary
        return ImportedWorkoutExecutionFailureContext(
            detail = detail,
            diagnosticsContext = diagnosticsContext.toMap(),
        )
    }

    private fun importedHrExecutionDetail(
        workout: ImportedErgoWorkout,
        diagnosticsContext: MutableMap<String, String>,
    ): String? {
        val step = workout.steps.firstOrNull { it is ImportedErgoWorkoutStep.HeartRateSteady }
            as? ImportedErgoWorkoutStep.HeartRateSteady
            ?: return null
        val snapshot = ImportedHrExecutionCapabilitySnapshot(
            hasHeartRateSignal = currentHeartRateSignalAvailable(),
            hasTrainerControl = uiState.ftmsControlGranted.value,
        )
        diagnosticsContext["heartRateSignalAvailable"] = snapshot.hasHeartRateSignal.toString()
        diagnosticsContext["trainerControlGranted"] = snapshot.hasTrainerControl.toString()
        return when (
            val preflight = ImportedHrRuntimePreflightAdapter.evaluate(
                workout = workout,
                step = step,
                snapshot = snapshot,
            )
        ) {
            is ImportedHrRuntimePreflightResult.MissingCanonicalControl -> {
                diagnosticsContext["hrPolicyStatus"] = "missing_canonical_control"
                "Imported HR execution is still blocked because the runtime policy cannot be fully resolved from canonical control metadata."
            }

            is ImportedHrRuntimePreflightResult.Ready -> {
                diagnosticsContext["hrPolicyStatus"] = "policy_available"
                diagnosticsContext["hrPolicyReadyAtStart"] = true.toString()
                diagnosticsContext["hrRequiredCapabilities"] = joinCapabilities(
                    preflight.policy.requiredCapabilities,
                )
                diagnosticsContext["hrMissingCapabilities"] = "none"
                diagnosticsContext["hrStartEvent"] = preflight.transition.event.name
                diagnosticsContext["hrStartCommands"] = joinImportedHrRuntimeCommands(
                    preflight.transition.commands,
                )
                "Imported HR execution resolves a v1 policy that requires ${joinCapabilities(preflight.policy.requiredCapabilities)} at segment start. Current capability snapshot already satisfies those start requirements. Runner support for HR-controlled imported steps is still disabled."
            }

            is ImportedHrRuntimePreflightResult.Blocked -> {
                diagnosticsContext["hrPolicyStatus"] = "policy_available"
                diagnosticsContext["hrPolicyReadyAtStart"] = false.toString()
                diagnosticsContext["hrRequiredCapabilities"] = joinCapabilities(
                    preflight.policy.requiredCapabilities,
                )
                diagnosticsContext["hrMissingCapabilities"] = joinCapabilities(
                    preflight.failureState.missingCapabilities,
                )
                diagnosticsContext["hrStartEvent"] = preflight.transition.event.name
                diagnosticsContext["hrStartFailureReason"] = preflight.failureState.reason.name
                diagnosticsContext["hrStartCommands"] = joinImportedHrRuntimeCommands(
                    preflight.transition.commands,
                )
                "Imported HR execution resolves a v1 policy that requires ${joinCapabilities(preflight.policy.requiredCapabilities)} at segment start. Current capability snapshot is still missing ${joinCapabilities(preflight.failureState.missingCapabilities)}. Runner support for HR-controlled imported steps is still disabled."
            }
        }
    }

    private fun currentHeartRateSignalAvailable(): Boolean {
        return uiState.heartRate.value != null || uiState.bikeData.value?.heartRateBpm != null
    }

    private fun hasImportedHeartRateSteps(workout: ImportedErgoWorkout): Boolean {
        return workout.steps.any { it is ImportedErgoWorkoutStep.HeartRateSteady }
    }

    private fun signalExecutionMappingFailure(signalKey: String) {
        val normalizedSignalKey = signalKey.trim()
        if (normalizedSignalKey.isEmpty()) return
        if (lastExecutionFailureSignalKey == normalizedSignalKey) return
        lastExecutionFailureSignalKey = normalizedSignalKey
        onExecutionMappingFailure()
    }

    data class ImportedWorkoutExecutionFailureContext(
        val detail: String?,
        val diagnosticsContext: Map<String, String>,
    )
}

private fun mappingFailureSummary(mapped: MappingResult.Failure): String {
    return mapped.errors.joinToString(separator = ", ") { it.code.name }
}

private fun joinCapabilities(
    capabilities: Collection<ImportedHrExecutionCapability>,
): String {
    if (capabilities.isEmpty()) return "none"
    return capabilities.joinToString(separator = ", ") { capability ->
        capability.name
    }
}

private fun joinImportedHrRuntimeCommands(
    commands: Collection<ImportedHrRuntimeCommand>,
): String {
    if (commands.isEmpty()) return "none"
    return commands.joinToString(separator = ", ") { command ->
        when (command) {
            is ImportedHrRuntimeCommand.SetPower -> "SET_POWER(${command.watts})"
            ImportedHrRuntimeCommand.BlockIncrease -> "BLOCK_INCREASE"
            ImportedHrRuntimeCommand.FailStart -> "FAIL_START"
            ImportedHrRuntimeCommand.StopWorkout -> "STOP_WORKOUT"
            ImportedHrRuntimeCommand.RequireUserAcknowledgement -> {
                "REQUIRE_USER_ACKNOWLEDGEMENT"
            }

            is ImportedHrRuntimeCommand.ReportUnreachableTarget -> {
                "REPORT_UNREACHABLE_TARGET(${command.status.name})"
            }

            ImportedHrRuntimeCommand.ClearUnreachableTargetStatus -> {
                "CLEAR_UNREACHABLE_TARGET_STATUS"
            }
        }
    }
}
