package com.example.ergometerapp.compat

import com.example.ergometerapp.compat.quirks.MatchConfidence

/**
 * Command execution status surfaced by Compatibility Mode adapters.
 */
enum class CompatibilityCommandStatus {
    SUCCESS,
    TIMEOUT,
    WRITE_NOT_STARTED,
    REJECTED,
    DISCONNECTED,
    FAILED,
}

/**
 * Structured result of one Compatibility Mode command operation.
 */
data class CompatibilityCommandResult(
    val status: CompatibilityCommandStatus,
    val detail: String? = null,
    val controlPointTrace: CompatibilityControlPointTrace? = null,
) {
    val isSuccess: Boolean
        get() = status == CompatibilityCommandStatus.SUCCESS

    companion object {
        val Success = CompatibilityCommandResult(status = CompatibilityCommandStatus.SUCCESS)
    }
}

/**
 * Support-facing FTMS control-point details captured for one compatibility command attempt.
 *
 * The trace is intentionally partial: when the transport never became ready, only the
 * expected opcode may be known. This still helps bundle consumers separate protocol
 * failures from earlier transport/setup failures without reverse-engineering free text.
 */
data class CompatibilityControlPointTrace(
    val correlationId: String? = null,
    val requestOpcode: Int? = null,
    val expectedOpcode: Int? = null,
    val receivedOpcode: Int? = null,
    val resultCode: Int? = null,
    val stage: String? = null,
)

/**
 * Non-fatal degradation signals kept in the summary for support context.
 */
enum class CompatibilityDegradationSignal {
    RESET_OPTIONAL,
}

/**
 * Terminal status of one Compatibility Mode execution.
 */
enum class CompatibilitySummaryStatus {
    PASS,
    FAIL,
}

/**
 * Coarse-grained event stream for support timeline diagnostics.
 */
data class CompatibilityTimelineEvent(
    val tsEpochMs: Long,
    val category: String,
    val event: String,
    val status: String,
    val details: Map<String, String> = emptyMap(),
)

/**
 * Stable summary payload used by on-device UI and export artifacts.
 */
data class CompatibilitySummaryOutput(
    val status: CompatibilitySummaryStatus,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val elapsedMs: Long,
    val totalBudgetMs: Long,
    val quirksId: String,
    val quirksMatchConfidence: MatchConfidence,
    val degradationSignals: List<CompatibilityDegradationSignal>,
    val failureCode: CompatibilityFailureCode?,
    val failureCategory: CompatibilityFailureCategory?,
    val failureReasonKey: String?,
    val failureDetail: String?,
)

/**
 * Full Compatibility Mode output with timeline traces.
 */
data class CompatibilityCheckResult(
    val summary: CompatibilitySummaryOutput,
    val timeline: List<CompatibilityTimelineEvent>,
)

/**
 * Abstracts BLE/control operations so the orchestrator stays deterministic and testable.
 */
interface CompatibilityDeviceGateway {
    fun connect(timeoutMs: Long): CompatibilityCommandResult
    fun requestControl(timeoutMs: Long): CompatibilityCommandResult
    fun reset(timeoutMs: Long): CompatibilityCommandResult
    fun setTargetPower(watts: Int, timeoutMs: Long): CompatibilityCommandResult
    fun stopWorkout(timeoutMs: Long): CompatibilityCommandResult
    fun disconnect(timeoutMs: Long): CompatibilityCommandResult
    fun hold(durationMs: Long)
}
