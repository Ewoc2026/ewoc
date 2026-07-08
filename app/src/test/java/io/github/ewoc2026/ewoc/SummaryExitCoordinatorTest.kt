package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.session.export.SessionExportSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryExitCoordinatorTest {

    @Test
    fun resetForMenuReturn_clearsSummaryScopedStateAndSummaryAiMessage() {
        val record = SummaryExitRecord()
        val statePort = FakeSummaryExitStatePort().apply {
            summary = sampleSummary()
            connectingTimeoutMessage = "timeout"
            fitPendingSnapshot = sampleSnapshot()
            fitStatusMessage = "export failed"
            fitStatusIsError = true
            lastAutoExportSummaryFingerprint = "fingerprint"
        }
        val coordinator = SummaryExitCoordinator(
            clearSummaryAiMessage = { record.events += "clear_summary_ai_message" },
        )

        coordinator.resetForMenuReturn(statePort)

        assertNull(statePort.summary)
        assertNull(statePort.connectingTimeoutMessage)
        assertNull(statePort.fitPendingSnapshot)
        assertNull(statePort.fitStatusMessage)
        assertFalse(statePort.fitStatusIsError)
        assertNull(statePort.lastAutoExportSummaryFingerprint)
        assertEquals(listOf("clear_summary_ai_message"), record.events)
    }

    private fun sampleSummary(startMillis: Long = 1_000L): SessionSummary {
        return SessionSummary(
            startTimestampMillis = startMillis,
            stopTimestampMillis = startMillis + 2_000L,
            durationSeconds = 2,
            actualTss = 12.5,
            avgPower = 200,
            maxPower = 300,
            avgCadence = 85,
            maxCadence = 96,
            avgHeartRate = 140,
            maxHeartRate = 155,
            distanceMeters = 1_500,
            totalEnergyKcal = 40,
        )
    }

    private fun sampleSnapshot(): SessionExportSnapshot {
        return SessionExportSnapshot(
            summary = sampleSummary(),
            timeline = emptyList(),
        )
    }

    private class FakeSummaryExitStatePort : SummaryExitStatePort {
        override var summary: SessionSummary? = null
        override var connectingTimeoutMessage: String? = null
        override var fitPendingSnapshot: SessionExportSnapshot? = null
        override var fitStatusMessage: String? = null
        override var fitStatusIsError: Boolean = false
        override var lastAutoExportSummaryFingerprint: String? = null
    }

    private class SummaryExitRecord {
        val events = mutableListOf<String>()
    }
}
