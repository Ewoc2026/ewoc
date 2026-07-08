package io.github.ewoc2026.ewoc.compat

import io.github.ewoc2026.ewoc.compat.quirks.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityRunArtifactsStorageTest {

    @Test
    fun buildSummaryJson_includesRunAndFailureMetadata() {
        val summaryJson = CompatibilityRunArtifactsStorage.buildSummaryJson(sampleArtifacts())

        assertTrue(summaryJson.contains("\"run_id\":\"compat-123\""))
        assertTrue(summaryJson.contains("\"android_manufacturer\":\"Acme\""))
        assertTrue(summaryJson.contains("\"quirks_notes\":\"Default FTMS profile\""))
        assertTrue(summaryJson.contains("\"failure_reason_key\":\"request_control_timeout\""))
        assertTrue(summaryJson.contains("\"timeline_event_count\":2"))
    }

    @Test
    fun buildTimelineJsonl_includesStatusAndDetailsPerEvent() {
        val timelineJsonl = CompatibilityRunArtifactsStorage.buildTimelineJsonl(sampleArtifacts())

        assertTrue(timelineJsonl.contains("\"status\":\"started\""))
        assertTrue(timelineJsonl.contains("\"status\":\"failed\""))
        assertTrue(timelineJsonl.contains("\"attempt\":\"1\""))
    }

    @Test
    fun rebuildArtifactsFromPersistedJson_roundTripsSummaryAndTimeline() {
        val artifacts = sampleArtifacts()
        val summaryJson = CompatibilityRunArtifactsStorage.buildSummaryJson(artifacts)
        val timelineJsonl = CompatibilityRunArtifactsStorage.buildTimelineJsonl(artifacts)

        val restored = CompatibilityRunArtifactsStorage.rebuildArtifactsFromPersistedJson(
            summaryJson = summaryJson,
            timelineJsonl = timelineJsonl,
        )

        assertNotNull(restored)
        assertEquals(artifacts.runId, restored?.runId)
        assertEquals(artifacts.capturedAtEpochMs, restored?.capturedAtEpochMs)
        assertEquals(artifacts.androidManufacturer, restored?.androidManufacturer)
        assertEquals(artifacts.androidModel, restored?.androidModel)
        assertEquals(artifacts.quirksNotes, restored?.quirksNotes)
        assertEquals(artifacts.result.summary.status, restored?.result?.summary?.status)
        assertEquals(artifacts.result.summary.failureCode, restored?.result?.summary?.failureCode)
        assertEquals(artifacts.result.timeline.size, restored?.result?.timeline?.size)
        assertEquals("timeout", restored?.result?.timeline?.last()?.details?.get("detail"))
    }

    @Test
    fun rebuildArtifactsFromPersistedJson_handlesDeviceCapturedFailureRun() {
        val summaryJson = """
            {"run_id":"compat-1772490977401","captured_at_epoch_ms":1772490987509,"trainer_identity":"E0:DF:01:46:14:2F","trainer_alias":"Tunturi E80-197","status":"fail","started_at_epoch_ms":1772490977409,"ended_at_epoch_ms":1772490987508,"elapsed_ms":10099,"total_budget_ms":45000,"quirks_id":"default","quirks_match_confidence":"high","degradation_signals":[],"failure_code":"connect_disconnected","failure_category":"connection","failure_reason_key":"connect_disconnected","failure_detail":"FTMS transport disconnected before becoming ready.","timeline_event_count":7}
        """.trimIndent()
        val timelineJsonl = """
            {"run_id":"compat-1772490977401","ts_epoch_ms":1772490977409,"category":"orchestrator","event":"started","status":"started","details":{"quirksId":"default","totalBudgetMs":"45000"}}
            {"run_id":"compat-1772490977401","ts_epoch_ms":1772490977409,"category":"connect","event":"attempt_started","status":"started","details":{"attempt":"1","timeoutMs":"15000"}}
            {"run_id":"compat-1772490977401","ts_epoch_ms":1772490982470,"category":"connect","event":"attempt_completed","status":"disconnected","details":{"attempt":"1","detail":"FTMS transport disconnected before becoming ready."}}
            {"run_id":"compat-1772490977401","ts_epoch_ms":1772490982471,"category":"connect","event":"retry_scheduled","status":"retry","details":{"nextAttempt":"2"}}
            {"run_id":"compat-1772490977401","ts_epoch_ms":1772490982471,"category":"connect","event":"attempt_started","status":"started","details":{"attempt":"2","timeoutMs":"15000"}}
            {"run_id":"compat-1772490977401","ts_epoch_ms":1772490987507,"category":"connect","event":"attempt_completed","status":"disconnected","details":{"attempt":"2","detail":"FTMS transport disconnected before becoming ready."}}
            {"run_id":"compat-1772490977401","ts_epoch_ms":1772490987508,"category":"orchestrator","event":"failed","status":"failed","details":{"failureCode":"connect_disconnected","detail":"FTMS transport disconnected before becoming ready."}}
        """.trimIndent()

        val restored = CompatibilityRunArtifactsStorage.rebuildArtifactsFromPersistedJson(
            summaryJson = summaryJson,
            timelineJsonl = timelineJsonl,
        )

        assertNotNull(restored)
        assertEquals("compat-1772490977401", restored?.runId)
        assertEquals(CompatibilitySummaryStatus.FAIL, restored?.result?.summary?.status)
        assertEquals(CompatibilityFailureCode.CONNECT_DISCONNECTED, restored?.result?.summary?.failureCode)
        assertEquals(7, restored?.result?.timeline?.size)
        assertEquals("45000", restored?.result?.timeline?.first()?.details?.get("totalBudgetMs"))
    }

    @Test
    fun rebuildArtifactsFromPersistedJson_keepsSummaryWhenTimelineIsMalformed() {
        val summaryJson = CompatibilityRunArtifactsStorage.buildSummaryJson(sampleArtifacts())
        val malformedTimeline = "{\"run_id\":\"compat-123\",\"details\":{bad-json}}"

        val restored = CompatibilityRunArtifactsStorage.rebuildArtifactsFromPersistedJson(
            summaryJson = summaryJson,
            timelineJsonl = malformedTimeline,
        )

        assertNotNull(restored)
        assertEquals("compat-123", restored?.runId)
        assertEquals(CompatibilitySummaryStatus.FAIL, restored?.result?.summary?.status)
        assertTrue(restored?.result?.timeline?.isEmpty() == true)
    }

    @Test
    fun rebuildArtifactsFromPersistedJson_keepsSummaryWhenTimelineMissing() {
        val summaryJson = CompatibilityRunArtifactsStorage.buildSummaryJson(sampleArtifacts())

        val restored = CompatibilityRunArtifactsStorage.rebuildArtifactsFromPersistedJson(
            summaryJson = summaryJson,
            timelineJsonl = "",
        )

        assertNotNull(restored)
        assertEquals("compat-123", restored?.runId)
        assertEquals(CompatibilitySummaryStatus.FAIL, restored?.result?.summary?.status)
        assertTrue(restored?.result?.timeline?.isEmpty() == true)
    }

    @Test
    fun rebuildArtifactsFromPersistedJson_skipsTimelineRowsWithMissingFieldsOrInvalidDetailsShape() {
        val summaryJson = CompatibilityRunArtifactsStorage.buildSummaryJson(sampleArtifacts())
        val timelineJsonl = """
            {"run_id":"compat-123","ts_epoch_ms":1000,"category":"control","event":"attempt_started","status":"started","details":{"attempt":"1"}}
            {"run_id":"compat-123","ts_epoch_ms":1200,"category":"control","event":"attempt_completed","details":{"attempt":"1"}}
            {"run_id":"compat-123","ts_epoch_ms":1300,"category":"control","event":"attempt_completed","status":"failed","details":[]}
        """.trimIndent()

        val restored = CompatibilityRunArtifactsStorage.rebuildArtifactsFromPersistedJson(
            summaryJson = summaryJson,
            timelineJsonl = timelineJsonl,
        )

        assertNotNull(restored)
        assertEquals(1, restored?.result?.timeline?.size)
        assertEquals("started", restored?.result?.timeline?.first()?.status)
    }

    @Test
    fun rebuildArtifactsFromPersistedJson_handlesMixedShapeDetailsAndLegacyQuotedPayload() {
        val summaryJson = CompatibilityRunArtifactsStorage.buildSummaryJson(sampleArtifacts())
        val timelineJsonl = """
            {"run_id":"compat-123","ts_epoch_ms":1000,"category":"control","event":"attempt_started","status":"started","details":{"attempt":1,"isRetry":false,"nullable":null,"nested":{"reason":"timeout"},"hints":["a","b"]}}
            {"run_id":"compat-123","ts_epoch_ms":1100,"category":"control","event":"attempt_started","status":"started","details":"{\"legacyKey\":\"legacyValue\",\"attempt\":\"2\"}"}
        """.trimIndent()

        val restored = CompatibilityRunArtifactsStorage.rebuildArtifactsFromPersistedJson(
            summaryJson = summaryJson,
            timelineJsonl = timelineJsonl,
        )

        assertNotNull(restored)
        assertEquals(2, restored?.result?.timeline?.size)

        val mixedShapeDetails = restored?.result?.timeline?.first()?.details
        assertEquals("1", mixedShapeDetails?.get("attempt"))
        assertEquals("false", mixedShapeDetails?.get("isRetry"))
        assertEquals("null", mixedShapeDetails?.get("nullable"))
        assertEquals("{\"reason\":\"timeout\"}", mixedShapeDetails?.get("nested"))
        assertEquals("[\"a\",\"b\"]", mixedShapeDetails?.get("hints"))

        val legacyDetails = restored?.result?.timeline?.get(1)?.details
        assertEquals("legacyValue", legacyDetails?.get("legacyKey"))
        assertEquals("2", legacyDetails?.get("attempt"))
    }

    private fun sampleArtifacts(): CompatibilityRunArtifacts {
        val result = CompatibilityCheckResult(
            summary = CompatibilitySummaryOutput(
                status = CompatibilitySummaryStatus.FAIL,
                startedAtEpochMs = 1_000L,
                endedAtEpochMs = 3_500L,
                elapsedMs = 2_500L,
                totalBudgetMs = 45_000L,
                quirksId = "default",
                quirksMatchConfidence = MatchConfidence.HIGH,
                degradationSignals = emptyList(),
                failureCode = CompatibilityFailureCode.REQUEST_CONTROL_TIMEOUT,
                failureCategory = CompatibilityFailureCategory.CONTROL_POINT,
                failureReasonKey = "request_control_timeout",
                failureDetail = "Timed out while waiting for control response.",
            ),
            timeline = listOf(
                CompatibilityTimelineEvent(
                    tsEpochMs = 1_000L,
                    category = "control",
                    event = "request_control_attempt_started",
                    status = "started",
                    details = mapOf("attempt" to "1"),
                ),
                CompatibilityTimelineEvent(
                    tsEpochMs = 3_500L,
                    category = "control",
                    event = "request_control_attempt_completed",
                    status = "failed",
                    details = mapOf("attempt" to "1", "detail" to "timeout"),
                ),
            ),
        )
        return CompatibilityRunArtifacts(
            runId = "compat-123",
            capturedAtEpochMs = 4_000L,
            trainerIdentity = "AA:BB:CC:DD:EE:FF",
            trainerAlias = "Trainer",
            androidManufacturer = "Acme",
            androidModel = "RoadBook",
            quirksNotes = "Default FTMS profile",
            result = result,
        )
    }
}
