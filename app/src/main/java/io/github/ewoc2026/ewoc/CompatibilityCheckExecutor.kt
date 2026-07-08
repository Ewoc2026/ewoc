package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.compat.CompatibilityCheckResult
import io.github.ewoc2026.ewoc.compat.CompatibilityFailureClassifier
import io.github.ewoc2026.ewoc.compat.CompatibilityFailureCode
import io.github.ewoc2026.ewoc.compat.CompatibilityRunArtifacts
import io.github.ewoc2026.ewoc.compat.CompatibilitySummaryOutput
import io.github.ewoc2026.ewoc.compat.CompatibilitySummaryStatus
import io.github.ewoc2026.ewoc.compat.CompatibilityTimelineEvent
import io.github.ewoc2026.ewoc.compat.quirks.CompatibilityQuirks
import io.github.ewoc2026.ewoc.compat.quirks.MatchConfidence
import io.github.ewoc2026.ewoc.compat.quirks.TrainerFingerprint

/**
 * Input payload for one Compatibility Mode execution.
 */
internal data class CompatibilityCheckExecutionRequest(
    val trainerMacAddress: String,
    val trainerAlias: String?,
)

/**
 * Execution output consumed by UI state coordination.
 */
internal data class CompatibilityCheckExecutionResult(
    val artifacts: CompatibilityRunArtifacts,
    val persisted: Boolean,
)

/**
 * Boundary for running and persisting one Compatibility Mode check.
 */
internal interface CompatibilityCheckExecutor {
    fun runAndPersist(request: CompatibilityCheckExecutionRequest): CompatibilityCheckExecutionResult
}

/**
 * Keeps run artifact construction and persistence out of MainViewModel.
 *
 * Invariants:
 * - Every execution creates one `CompatibilityRunArtifacts` payload.
 * - Unexpected exceptions are mapped into a stable unknown-failure summary.
 */
internal class RealCompatibilityCheckExecutor(
    private val nowEpochMillis: () -> Long,
    private val androidManufacturer: () -> String,
    private val androidModel: () -> String,
    private val resolveQuirks: (TrainerFingerprint) -> CompatibilityQuirks,
    private val runCheck: (trainerMacAddress: String, quirks: CompatibilityQuirks) -> CompatibilityCheckResult,
    private val persist: (CompatibilityRunArtifacts) -> Boolean,
) : CompatibilityCheckExecutor {

    override fun runAndPersist(request: CompatibilityCheckExecutionRequest): CompatibilityCheckExecutionResult {
        val startedAtEpochMs = nowEpochMillis()
        val runId = "compat-$startedAtEpochMs"
        val fingerprint = TrainerFingerprint(
            advNameNormalized = TrainerFingerprint.normalizeAdvertisementName(request.trainerAlias),
            manufacturer = null,
            model = null,
            ftmsServicePresent = true,
            has2ad2 = false,
            has2ad9 = false,
            androidManufacturer = androidManufacturer(),
            androidModel = androidModel(),
        )
        val quirks = resolveQuirks(fingerprint)
        val result = runCatching {
            runCheck(request.trainerMacAddress, quirks)
        }.getOrElse { throwable ->
            unexpectedCompatibilityCheckResult(
                startedAtEpochMs = startedAtEpochMs,
                quirksId = quirks.id,
                quirksMatchConfidence = quirks.matchConfidence,
                throwable = throwable,
            )
        }

        val artifacts = CompatibilityRunArtifacts(
            runId = runId,
            capturedAtEpochMs = nowEpochMillis(),
            trainerIdentity = request.trainerMacAddress,
            trainerAlias = request.trainerAlias,
            androidManufacturer = fingerprint.androidManufacturer,
            androidModel = fingerprint.androidModel,
            quirksNotes = quirks.notes,
            result = result,
        )
        return CompatibilityCheckExecutionResult(
            artifacts = artifacts,
            persisted = persist(artifacts),
        )
    }

    private fun unexpectedCompatibilityCheckResult(
        startedAtEpochMs: Long,
        quirksId: String,
        quirksMatchConfidence: MatchConfidence,
        throwable: Throwable,
    ): CompatibilityCheckResult {
        val failureCode = CompatibilityFailureCode.UNKNOWN_FAILURE
        val classification = CompatibilityFailureClassifier.classify(failureCode)
        val detail = throwable.message?.trim()?.takeIf { it.isNotEmpty() }
            ?: throwable.javaClass.simpleName
        val event = CompatibilityTimelineEvent(
            tsEpochMs = startedAtEpochMs,
            category = "orchestrator",
            event = "exception",
            status = "failed",
            details = mapOf("detail" to detail),
        )
        return CompatibilityCheckResult(
            summary = CompatibilitySummaryOutput(
                status = CompatibilitySummaryStatus.FAIL,
                startedAtEpochMs = startedAtEpochMs,
                endedAtEpochMs = startedAtEpochMs,
                elapsedMs = 0L,
                totalBudgetMs = 0L,
                quirksId = quirksId,
                quirksMatchConfidence = quirksMatchConfidence,
                degradationSignals = emptyList(),
                failureCode = failureCode,
                failureCategory = classification.category,
                failureReasonKey = classification.reasonKey,
                failureDetail = detail,
            ),
            timeline = listOf(event),
        )
    }
}
