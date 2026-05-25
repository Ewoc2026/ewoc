package com.example.ergometerapp.compat

import com.example.ergometerapp.compat.quirks.CompatibilityQuirks

/**
 * Executes the bounded Compatibility Mode v1 control-flow.
 *
 * Invariants:
 * - global deadline is authoritative across all steps
 * - cleanup path prefers `Set Target Power(0)` and degrades to immediate disconnect
 * - retry behavior follows locked v1 policies and is isolated from normal session flows
 */
class CompatibilityCheckOrchestrator(
    private val deviceGateway: CompatibilityDeviceGateway,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {

    fun run(quirks: CompatibilityQuirks): CompatibilityCheckResult {
        val startedAt = nowEpochMs()
        val timeline = mutableListOf<CompatibilityTimelineEvent>()
        val degradationSignals = mutableListOf<CompatibilityDegradationSignal>()

        fun emit(
            category: String,
            event: String,
            status: String,
            details: Map<String, String> = emptyMap(),
        ) {
            timeline += CompatibilityTimelineEvent(
                tsEpochMs = nowEpochMs(),
                category = category,
                event = event,
                status = status,
                details = details,
            )
        }

        fun finalize(
            status: CompatibilitySummaryStatus,
            failureCode: CompatibilityFailureCode?,
            failureDetail: String?,
        ): CompatibilityCheckResult {
            val endedAt = nowEpochMs()
            val elapsed = (endedAt - startedAt).coerceAtLeast(0L)
            val classification = failureCode?.let { CompatibilityFailureClassifier.classify(it) }
            return CompatibilityCheckResult(
                summary = CompatibilitySummaryOutput(
                    status = status,
                    startedAtEpochMs = startedAt,
                    endedAtEpochMs = endedAt,
                    elapsedMs = elapsed,
                    totalBudgetMs = CompatibilityV1Constants.TOTAL_TEST_BUDGET_MS,
                    quirksId = quirks.id,
                    quirksMatchConfidence = quirks.matchConfidence,
                    degradationSignals = degradationSignals.toList(),
                    failureCode = failureCode,
                    failureCategory = classification?.category,
                    failureReasonKey = classification?.reasonKey,
                    failureDetail = failureDetail,
                ),
                timeline = timeline.toList(),
            )
        }

        fun fail(
            code: CompatibilityFailureCode,
            detail: String?,
        ): CompatibilityCheckResult {
            emit(
                category = "orchestrator",
                event = "failed",
                status = "failed",
                details = buildMap {
                    put("failureCode", code.name.lowercase())
                    detail?.let { put("detail", it) }
                },
            )
            return finalize(
                status = CompatibilitySummaryStatus.FAIL,
                failureCode = code,
                failureDetail = detail,
            )
        }

        emit(
            category = "orchestrator",
            event = "started",
            status = "started",
            details = mapOf(
                "quirksId" to quirks.id,
                "totalBudgetMs" to CompatibilityV1Constants.TOTAL_TEST_BUDGET_MS.toString(),
            ),
        )

        val connectResult = runConnectStep(
            quirks = quirks,
            startedAt = startedAt,
            emit = ::emit,
        )
        if (!connectResult.success) {
            return fail(
                code = connectResult.failureCode,
                detail = connectResult.detail,
            )
        }

        val requestControlResult = runRequestControlStep(
            startedAt = startedAt,
            quirks = quirks,
            stepEvent = "request_control",
            emit = ::emit,
        )
        if (!requestControlResult.success) {
            return fail(
                code = requestControlResult.failureCode,
                detail = requestControlResult.detail,
            )
        }

        if (quirks.enableResetOptional) {
            val resetTimeout = deadlineAwareTimeout(startedAt, quirks.cpAckTimeoutMs)
            if (resetTimeout == null) {
                return fail(
                    code = CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                    detail = "Deadline exceeded before optional reset probe",
                )
            }
            emit(
                category = "control",
                event = "reset_optional_started",
                status = "started",
                details = mapOf("timeoutMs" to resetTimeout.toString()),
            )
            val resetResult = deviceGateway.reset(resetTimeout)
            emit(
                category = "control",
                event = "reset_optional_completed",
                status = resetResult.status.name.lowercase(),
                details = buildMap {
                    resetResult.detail?.let { put("detail", it) }
                    putAll(controlPointTraceDetails(resetResult.controlPointTrace))
                },
            )
            if (!resetResult.isSuccess) {
                degradationSignals += CompatibilityDegradationSignal.RESET_OPTIONAL
                emit(
                    category = "control",
                    event = "reset_optional_degraded",
                    status = "degraded",
                )
            }

            if (resetResult.isSuccess) {
                val reacquireControlResult = runRequestControlStep(
                    startedAt = startedAt,
                    quirks = quirks,
                    stepEvent = "request_control_after_reset",
                    emit = ::emit,
                )
                if (!reacquireControlResult.success) {
                    return fail(
                        code = reacquireControlResult.failureCode,
                        detail = reacquireControlResult.detail,
                    )
                }
            }
        }

        val powerSteps = listOf(
            CompatibilityV1Constants.STEP_A_WATTS,
            CompatibilityV1Constants.STEP_B_WATTS,
            CompatibilityV1Constants.STEP_C_WATTS,
        )

        for ((index, watts) in powerSteps.withIndex()) {
            val clampedWatts = watts.coerceIn(quirks.clampWattsMin, quirks.clampWattsMax)
            val stepResult = runRetriableControlPointStep(
                startedAt = startedAt,
                stepCategory = "power_step",
                stepEvent = "set_target_power",
                timeoutMsProvider = { deadlineAwareTimeout(startedAt, quirks.cpAckTimeoutMs) },
                retryOnTimeoutOrWriteFailure = CompatibilityV1Constants.CP_ACK_RETRY_ON_TIMEOUT_OR_WRITE_FAILURE,
                retryOnReject = CompatibilityV1Constants.CP_ACK_RETRY_ON_REJECT,
                command = { timeoutMs ->
                    deviceGateway.setTargetPower(
                        watts = clampedWatts,
                        timeoutMs = timeoutMs,
                    )
                },
                mapFailureCode = ::powerStepFailureCode,
                emit = { category, event, status, details ->
                    emit(
                        category = category,
                        event = event,
                        status = status,
                        details = details + mapOf("stepIndex" to index.toString(), "watts" to clampedWatts.toString()),
                    )
                },
            )
            if (!stepResult.success) {
                return fail(
                    code = stepResult.failureCode,
                    detail = stepResult.detail,
                )
            }

            val requiredHoldMs = CompatibilityV1Constants.STEP_HOLD_MS + CompatibilityV1Constants.STEP_SETTLE_MS
            val remainingBeforeHold = remainingBudgetMs(startedAt)
            if (remainingBeforeHold <= 0L) {
                return fail(
                    code = CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                    detail = "Deadline exceeded before hold for power step $index",
                )
            }
            val holdDurationMs = minOf(requiredHoldMs, remainingBeforeHold)
            emit(
                category = "power_step",
                event = "hold_started",
                status = "started",
                details = mapOf(
                    "stepIndex" to index.toString(),
                    "watts" to clampedWatts.toString(),
                    "durationMs" to holdDurationMs.toString(),
                ),
            )
            deviceGateway.hold(holdDurationMs)
            emit(
                category = "power_step",
                event = "hold_completed",
                status = "ok",
                details = mapOf("stepIndex" to index.toString()),
            )

            if (holdDurationMs < requiredHoldMs) {
                return fail(
                    code = CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                    detail = "Deadline exceeded during hold for power step $index",
                )
            }
        }

        val stopResult = runRetriableControlPointStep(
            startedAt = startedAt,
            stepCategory = "cleanup",
            stepEvent = "stop_workout",
            timeoutMsProvider = { deadlineAwareTimeout(startedAt, CompatibilityV1Constants.STOP_ACK_TIMEOUT_MS) },
            retryOnTimeoutOrWriteFailure = CompatibilityV1Constants.STOP_RETRY_COUNT,
            retryOnReject = 0,
            command = { timeoutMs -> deviceGateway.stopWorkout(timeoutMs) },
            mapFailureCode = ::stopFailureCode,
            emit = ::emit,
        )

        if (!stopResult.success) {
            return handleStopFailureWithCleanup(
                quirks = quirks,
                startedAt = startedAt,
                stopFailure = stopResult,
                emit = ::emit,
                fail = ::fail,
            )
        }

        val disconnectTimeoutMs = deadlineAwareTimeout(startedAt, CompatibilityV1Constants.CLEANUP_DISCONNECT_TIMEOUT_MS)
            ?: 0L
        emit(
            category = "cleanup",
            event = "disconnect_started",
            status = "started",
            details = mapOf("timeoutMs" to disconnectTimeoutMs.toString()),
        )
        val disconnectResult = deviceGateway.disconnect(disconnectTimeoutMs)
        emit(
            category = "cleanup",
            event = "disconnect_completed",
            status = disconnectResult.status.name.lowercase(),
            details = buildMap {
                disconnectResult.detail?.let { put("detail", it) }
            },
        )
        if (!disconnectResult.isSuccess) {
            return fail(
                code = CompatibilityFailureCode.CLEANUP_DISCONNECT_FAILED,
                detail = disconnectResult.detail,
            )
        }

        emit(
            category = "orchestrator",
            event = "completed",
            status = "ok",
        )
        return finalize(
            status = CompatibilitySummaryStatus.PASS,
            failureCode = null,
            failureDetail = null,
        )
    }

    private fun runConnectStep(
        quirks: CompatibilityQuirks,
        startedAt: Long,
        emit: (String, String, String, Map<String, String>) -> Unit,
    ): StepResult {
        val totalAttempts = quirks.maxReconnectRetries.coerceAtLeast(0) + 1
        var lastFailureCode = CompatibilityFailureCode.CONNECT_FAILED
        var lastDetail: String? = null

        for (attempt in 1..totalAttempts) {
            val timeoutMs = deadlineAwareTimeout(startedAt, quirks.connectTimeoutMs)
                ?: return StepResult.failure(
                    code = CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                    detail = "Deadline exceeded before connect attempt $attempt",
                )
            emit(
                "connect",
                "attempt_started",
                "started",
                mapOf("attempt" to attempt.toString(), "timeoutMs" to timeoutMs.toString()),
            )
            val result = deviceGateway.connect(timeoutMs)
            emit(
                "connect",
                "attempt_completed",
                result.status.name.lowercase(),
                buildMap {
                    put("attempt", attempt.toString())
                    result.detail?.let { put("detail", it) }
                },
            )
            if (result.isSuccess) {
                return StepResult.success()
            }
            lastFailureCode = connectFailureCode(result.status)
            lastDetail = result.detail
            if (attempt < totalAttempts) {
                emit(
                    "connect",
                    "retry_scheduled",
                    "retry",
                    mapOf("nextAttempt" to (attempt + 1).toString()),
                )
            }
        }

        return StepResult.failure(
            code = lastFailureCode,
            detail = lastDetail,
        )
    }

    private fun runRetriableControlPointStep(
        startedAt: Long,
        stepCategory: String,
        stepEvent: String,
        timeoutMsProvider: () -> Long?,
        retryOnTimeoutOrWriteFailure: Int,
        retryOnReject: Int,
        command: (Long) -> CompatibilityCommandResult,
        mapFailureCode: (CompatibilityCommandStatus) -> CompatibilityFailureCode,
        emit: (String, String, String, Map<String, String>) -> Unit,
    ): StepResult {
        var timeoutOrWriteRetriesRemaining = retryOnTimeoutOrWriteFailure.coerceAtLeast(0)
        var rejectRetriesRemaining = retryOnReject.coerceAtLeast(0)
        var attempt = 1

        while (true) {
            val timeoutMs = timeoutMsProvider()
                ?: return StepResult.failure(
                    code = CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                    detail = "Deadline exceeded before $stepEvent attempt $attempt",
                )
            emit(
                stepCategory,
                "${stepEvent}_attempt_started",
                "started",
                mapOf("attempt" to attempt.toString(), "timeoutMs" to timeoutMs.toString()),
            )

            val result = command(timeoutMs)
            emit(
                stepCategory,
                "${stepEvent}_attempt_completed",
                result.status.name.lowercase(),
                buildMap {
                    put("attempt", attempt.toString())
                    result.detail?.let { put("detail", it) }
                    putAll(controlPointTraceDetails(result.controlPointTrace))
                },
            )
            if (result.isSuccess) {
                return StepResult.success()
            }

            val shouldRetry = when (result.status) {
                CompatibilityCommandStatus.TIMEOUT,
                CompatibilityCommandStatus.WRITE_NOT_STARTED,
                -> {
                    if (timeoutOrWriteRetriesRemaining > 0) {
                        timeoutOrWriteRetriesRemaining -= 1
                        true
                    } else {
                        false
                    }
                }

                CompatibilityCommandStatus.REJECTED -> {
                    if (rejectRetriesRemaining > 0) {
                        rejectRetriesRemaining -= 1
                        true
                    } else {
                        false
                    }
                }

                CompatibilityCommandStatus.DISCONNECTED,
                CompatibilityCommandStatus.FAILED,
                CompatibilityCommandStatus.SUCCESS,
                -> false
            }

            if (!shouldRetry) {
                return StepResult.failure(
                    code = mapFailureCode(result.status),
                    detail = result.detail,
                )
            }

            emit(
                stepCategory,
                "${stepEvent}_retry_scheduled",
                "retry",
                mapOf("nextAttempt" to (attempt + 1).toString()),
            )
            attempt += 1

            if (remainingBudgetMs(startedAt) <= 0L) {
                return StepResult.failure(
                    code = CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                    detail = "Deadline exceeded while retrying $stepEvent",
                )
            }
        }
    }

    private fun runRequestControlStep(
        startedAt: Long,
        quirks: CompatibilityQuirks,
        stepEvent: String,
        emit: (String, String, String, Map<String, String>) -> Unit,
    ): StepResult {
        return runRetriableControlPointStep(
            startedAt = startedAt,
            stepCategory = "control",
            stepEvent = stepEvent,
            timeoutMsProvider = { deadlineAwareTimeout(startedAt, quirks.requestControlTimeoutMs) },
            retryOnTimeoutOrWriteFailure = CompatibilityV1Constants.REQUEST_CONTROL_RETRY_ON_TIMEOUT_OR_WRITE_FAILURE,
            retryOnReject = CompatibilityV1Constants.REQUEST_CONTROL_RETRY_ON_REJECT,
            command = { timeoutMs -> deviceGateway.requestControl(timeoutMs) },
            mapFailureCode = ::requestControlFailureCode,
            emit = emit,
        )
    }

    private fun handleStopFailureWithCleanup(
        quirks: CompatibilityQuirks,
        startedAt: Long,
        stopFailure: StepResult,
        emit: (String, String, String, Map<String, String>) -> Unit,
        fail: (CompatibilityFailureCode, String?) -> CompatibilityCheckResult,
    ): CompatibilityCheckResult {
        emit(
            "cleanup",
            "fallback_started",
            "started",
            mapOf("stopFailureCode" to stopFailure.failureCode.name.lowercase()),
        )

        if (remainingBudgetMs(startedAt) <= 0L) {
            forceImmediateDisconnect(
                reason = "deadline_exceeded_before_cleanup",
                emit = emit,
            )
            return fail(
                CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                "Deadline exceeded before cleanup fallback",
            )
        }

        val fallbackWatts = quirks.fallbackPowerWatts
        if (fallbackWatts != null) {
            val fallbackTimeoutMs = deadlineAwareTimeout(startedAt, quirks.cpAckTimeoutMs)
                ?: 0L
            if (fallbackTimeoutMs <= 0L) {
                forceImmediateDisconnect(
                    reason = "deadline_exceeded_before_cleanup_fallback_watts",
                    emit = emit,
                )
                return fail(
                    CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                    "Deadline exceeded before cleanup fallback command",
                )
            }

            emit(
                "cleanup",
                "fallback_watts_started",
                "started",
                mapOf(
                    "watts" to fallbackWatts.toString(),
                    "timeoutMs" to fallbackTimeoutMs.toString(),
                ),
            )
            val fallbackResult = deviceGateway.setTargetPower(
                watts = fallbackWatts,
                timeoutMs = fallbackTimeoutMs,
            )
            emit(
                "cleanup",
                "fallback_watts_completed",
                fallbackResult.status.name.lowercase(),
                buildMap {
                    fallbackResult.detail?.let { put("detail", it) }
                    putAll(controlPointTraceDetails(fallbackResult.controlPointTrace))
                },
            )
            if (!fallbackResult.isSuccess) {
                forceImmediateDisconnect(
                    reason = "cleanup_fallback_command_failed",
                    emit = emit,
                )
                return fail(
                    cleanupFallbackFailureCode(fallbackResult.status),
                    fallbackResult.detail,
                )
            }
        }

        val disconnectTimeoutMs = deadlineAwareTimeout(startedAt, CompatibilityV1Constants.CLEANUP_DISCONNECT_TIMEOUT_MS)
            ?: 0L
        if (disconnectTimeoutMs <= 0L) {
            forceImmediateDisconnect(
                reason = "deadline_exceeded_before_cleanup_disconnect",
                emit = emit,
            )
            return fail(
                CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED,
                "Deadline exceeded before cleanup disconnect",
            )
        }

        emit(
            "cleanup",
            "fallback_disconnect_started",
            "started",
            mapOf("timeoutMs" to disconnectTimeoutMs.toString()),
        )
        val disconnectResult = deviceGateway.disconnect(disconnectTimeoutMs)
        emit(
            "cleanup",
            "fallback_disconnect_completed",
            disconnectResult.status.name.lowercase(),
            buildMap {
                disconnectResult.detail?.let { put("detail", it) }
            },
        )
        if (!disconnectResult.isSuccess) {
            return fail(
                CompatibilityFailureCode.CLEANUP_DISCONNECT_FAILED,
                disconnectResult.detail,
            )
        }

        return fail(
            stopFailure.failureCode,
            stopFailure.detail,
        )
    }

    private fun forceImmediateDisconnect(
        reason: String,
        emit: (String, String, String, Map<String, String>) -> Unit,
    ) {
        emit(
            "cleanup",
            "forced_disconnect_started",
            "started",
            mapOf("reason" to reason),
        )
        val result = deviceGateway.disconnect(timeoutMs = 0L)
        emit(
            "cleanup",
            "forced_disconnect_completed",
            result.status.name.lowercase(),
            buildMap {
                result.detail?.let { put("detail", it) }
            },
        )
    }

    private fun remainingBudgetMs(startedAt: Long): Long {
        return CompatibilityV1Constants.TOTAL_TEST_BUDGET_MS - (nowEpochMs() - startedAt)
    }

    private fun controlPointTraceDetails(
        trace: CompatibilityControlPointTrace?,
    ): Map<String, String> {
        val resolvedTrace = trace ?: return emptyMap()
        return buildMap {
            resolvedTrace.correlationId?.let { put("correlationId", it) }
            resolvedTrace.requestOpcode?.let { put("requestOpcode", formatOpcode(it)) }
            resolvedTrace.expectedOpcode?.let { put("expectedOpcode", formatOpcode(it)) }
            resolvedTrace.receivedOpcode?.let { put("receivedOpcode", formatOpcode(it)) }
            resolvedTrace.resultCode?.let { put("resultCode", formatOpcode(it)) }
            resolvedTrace.stage?.let { put("controlPointStage", it) }
        }
    }

    private fun formatOpcode(value: Int): String {
        return "0x" + value.toString(16).padStart(2, '0')
    }

    private fun deadlineAwareTimeout(startedAt: Long, requestedTimeoutMs: Long): Long? {
        val remaining = remainingBudgetMs(startedAt)
        if (remaining <= 0L) return null
        return minOf(remaining, requestedTimeoutMs)
    }

    private fun connectFailureCode(status: CompatibilityCommandStatus): CompatibilityFailureCode {
        return when (status) {
            CompatibilityCommandStatus.TIMEOUT -> CompatibilityFailureCode.CONNECT_TIMEOUT
            CompatibilityCommandStatus.DISCONNECTED -> CompatibilityFailureCode.CONNECT_DISCONNECTED
            CompatibilityCommandStatus.WRITE_NOT_STARTED,
            CompatibilityCommandStatus.REJECTED,
            CompatibilityCommandStatus.FAILED,
            CompatibilityCommandStatus.SUCCESS,
            -> CompatibilityFailureCode.CONNECT_FAILED
        }
    }

    private fun requestControlFailureCode(status: CompatibilityCommandStatus): CompatibilityFailureCode {
        return when (status) {
            CompatibilityCommandStatus.TIMEOUT -> CompatibilityFailureCode.REQUEST_CONTROL_TIMEOUT
            CompatibilityCommandStatus.WRITE_NOT_STARTED -> CompatibilityFailureCode.REQUEST_CONTROL_WRITE_NOT_STARTED
            CompatibilityCommandStatus.REJECTED -> CompatibilityFailureCode.REQUEST_CONTROL_REJECTED
            CompatibilityCommandStatus.DISCONNECTED,
            CompatibilityCommandStatus.FAILED,
            CompatibilityCommandStatus.SUCCESS,
            -> CompatibilityFailureCode.REQUEST_CONTROL_FAILED
        }
    }

    private fun powerStepFailureCode(status: CompatibilityCommandStatus): CompatibilityFailureCode {
        return when (status) {
            CompatibilityCommandStatus.TIMEOUT -> CompatibilityFailureCode.POWER_STEP_TIMEOUT
            CompatibilityCommandStatus.WRITE_NOT_STARTED -> CompatibilityFailureCode.POWER_STEP_WRITE_NOT_STARTED
            CompatibilityCommandStatus.REJECTED -> CompatibilityFailureCode.POWER_STEP_REJECTED
            CompatibilityCommandStatus.DISCONNECTED,
            CompatibilityCommandStatus.FAILED,
            CompatibilityCommandStatus.SUCCESS,
            -> CompatibilityFailureCode.POWER_STEP_FAILED
        }
    }

    private fun stopFailureCode(status: CompatibilityCommandStatus): CompatibilityFailureCode {
        return when (status) {
            CompatibilityCommandStatus.TIMEOUT -> CompatibilityFailureCode.STOP_TIMEOUT
            CompatibilityCommandStatus.WRITE_NOT_STARTED -> CompatibilityFailureCode.STOP_WRITE_NOT_STARTED
            CompatibilityCommandStatus.REJECTED -> CompatibilityFailureCode.STOP_REJECTED
            CompatibilityCommandStatus.DISCONNECTED,
            CompatibilityCommandStatus.FAILED,
            CompatibilityCommandStatus.SUCCESS,
            -> CompatibilityFailureCode.STOP_FAILED
        }
    }

    private fun cleanupFallbackFailureCode(status: CompatibilityCommandStatus): CompatibilityFailureCode {
        return when (status) {
            CompatibilityCommandStatus.TIMEOUT -> CompatibilityFailureCode.CLEANUP_FALLBACK_TIMEOUT
            CompatibilityCommandStatus.WRITE_NOT_STARTED -> CompatibilityFailureCode.CLEANUP_FALLBACK_WRITE_NOT_STARTED
            CompatibilityCommandStatus.REJECTED,
            CompatibilityCommandStatus.DISCONNECTED,
            CompatibilityCommandStatus.FAILED,
            CompatibilityCommandStatus.SUCCESS,
            -> CompatibilityFailureCode.CLEANUP_FALLBACK_FAILED
        }
    }

    private data class StepResult(
        val success: Boolean,
        val failureCode: CompatibilityFailureCode,
        val detail: String?,
    ) {
        companion object {
            fun success(): StepResult {
                return StepResult(
                    success = true,
                    failureCode = CompatibilityFailureCode.UNKNOWN_FAILURE,
                    detail = null,
                )
            }

            fun failure(code: CompatibilityFailureCode, detail: String?): StepResult {
                return StepResult(
                    success = false,
                    failureCode = code,
                    detail = detail,
                )
            }
        }
    }
}
