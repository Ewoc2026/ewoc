package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.compat.CompatibilityCheckResult
import io.github.ewoc2026.ewoc.compat.CompatibilityFailureClassifier
import io.github.ewoc2026.ewoc.compat.CompatibilityFailureCode
import io.github.ewoc2026.ewoc.compat.CompatibilitySummaryOutput
import io.github.ewoc2026.ewoc.compat.CompatibilitySummaryStatus
import io.github.ewoc2026.ewoc.compat.quirks.CompatibilityQuirks
import io.github.ewoc2026.ewoc.compat.quirks.MatchConfidence
import io.github.ewoc2026.ewoc.compat.quirks.StopStrategy
import io.github.ewoc2026.ewoc.compat.quirks.TrainerFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityCheckExecutorTest {

    @Test
    fun runAndPersist_returnsArtifactsAndPersistenceFlagWhenRunSucceeds() {
        var observedFingerprint: TrainerFingerprint? = null
        var observedMac: String? = null
        var persistedArtifactsRunId: String? = null
        val expectedResult = passResult()
        val executor = RealCompatibilityCheckExecutor(
            nowEpochMillis = fixedNow(1_000L, 1_300L),
            androidManufacturer = { "Acme" },
            androidModel = { "RoadBook" },
            resolveQuirks = { fingerprint ->
                observedFingerprint = fingerprint
                CompatibilityQuirks(
                    id = "default",
                    matchConfidence = MatchConfidence.HIGH,
                    notes = "Default profile",
                )
            },
            runCheck = { trainerMacAddress, _ ->
                observedMac = trainerMacAddress
                expectedResult
            },
            persist = { artifacts ->
                persistedArtifactsRunId = artifacts.runId
                true
            },
        )

        val execution = executor.runAndPersist(
            CompatibilityCheckExecutionRequest(
                trainerMacAddress = "AA:BB:CC:DD:EE:FF",
                trainerAlias = "  Trainer Pro  ",
            ),
        )

        assertTrue(execution.persisted)
        assertEquals("compat-1000", execution.artifacts.runId)
        assertEquals(1_300L, execution.artifacts.capturedAtEpochMs)
        assertEquals("AA:BB:CC:DD:EE:FF", execution.artifacts.trainerIdentity)
        assertEquals("  Trainer Pro  ", execution.artifacts.trainerAlias)
        assertEquals("Acme", execution.artifacts.androidManufacturer)
        assertEquals("RoadBook", execution.artifacts.androidModel)
        assertEquals("Default profile", execution.artifacts.quirksNotes)
        assertEquals(CompatibilitySummaryStatus.PASS, execution.artifacts.result.summary.status)
        assertEquals("AA:BB:CC:DD:EE:FF", observedMac)
        assertEquals("compat-1000", persistedArtifactsRunId)
        assertNotNull(observedFingerprint)
        assertEquals("trainer pro", observedFingerprint?.advNameNormalized)
        assertEquals("Acme", observedFingerprint?.androidManufacturer)
        assertEquals("RoadBook", observedFingerprint?.androidModel)
    }

    @Test
    fun runAndPersist_mapsUnexpectedExceptionToUnknownFailureSummary() {
        val quirks = CompatibilityQuirks(
            id = "custom",
            matchConfidence = MatchConfidence.LOW,
            notes = "Custom profile",
            stopStrategy = StopStrategy.DisconnectOnly,
        )
        val executor = RealCompatibilityCheckExecutor(
            nowEpochMillis = fixedNow(2_000L, 2_300L),
            androidManufacturer = { "Acme" },
            androidModel = { "RoadBook" },
            resolveQuirks = { quirks },
            runCheck = { _, _ -> throw IllegalStateException("boom") },
            persist = { false },
        )

        val execution = executor.runAndPersist(
            CompatibilityCheckExecutionRequest(
                trainerMacAddress = "11:22:33:44:55:66",
                trainerAlias = null,
            ),
        )

        val summary = execution.artifacts.result.summary
        val timeline = execution.artifacts.result.timeline
        val expectedClassification = CompatibilityFailureClassifier.classify(
            CompatibilityFailureCode.UNKNOWN_FAILURE,
        )

        assertFalse(execution.persisted)
        assertEquals("compat-2000", execution.artifacts.runId)
        assertEquals(2_300L, execution.artifacts.capturedAtEpochMs)
        assertEquals("11:22:33:44:55:66", execution.artifacts.trainerIdentity)
        assertNull(execution.artifacts.trainerAlias)
        assertEquals(CompatibilitySummaryStatus.FAIL, summary.status)
        assertEquals(2_000L, summary.startedAtEpochMs)
        assertEquals(2_000L, summary.endedAtEpochMs)
        assertEquals(0L, summary.elapsedMs)
        assertEquals(CompatibilityFailureCode.UNKNOWN_FAILURE, summary.failureCode)
        assertEquals(expectedClassification.category, summary.failureCategory)
        assertEquals(expectedClassification.reasonKey, summary.failureReasonKey)
        assertEquals("boom", summary.failureDetail)
        assertEquals("custom", summary.quirksId)
        assertEquals(MatchConfidence.LOW, summary.quirksMatchConfidence)
        assertEquals(1, timeline.size)
        assertEquals("orchestrator", timeline.first().category)
        assertEquals("exception", timeline.first().event)
        assertEquals("failed", timeline.first().status)
        assertEquals("boom", timeline.first().details["detail"])
    }

    private fun passResult(): CompatibilityCheckResult {
        return CompatibilityCheckResult(
            summary = CompatibilitySummaryOutput(
                status = CompatibilitySummaryStatus.PASS,
                startedAtEpochMs = 10L,
                endedAtEpochMs = 110L,
                elapsedMs = 100L,
                totalBudgetMs = 45_000L,
                quirksId = "default",
                quirksMatchConfidence = MatchConfidence.HIGH,
                degradationSignals = emptyList(),
                failureCode = null,
                failureCategory = null,
                failureReasonKey = null,
                failureDetail = null,
            ),
            timeline = emptyList(),
        )
    }

    private fun fixedNow(vararg values: Long): () -> Long {
        check(values.isNotEmpty())
        var index = 0
        return {
            val value = values[minOf(index, values.lastIndex)]
            index += 1
            value
        }
    }
}
