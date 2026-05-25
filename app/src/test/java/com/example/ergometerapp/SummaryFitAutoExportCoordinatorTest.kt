package com.example.ergometerapp

import com.example.ergometerapp.session.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryFitAutoExportCoordinatorTest {

    @Test
    fun tryAutoExportIfNeeded_skipsWhenAutoSaveIsDisabled() {
        val statePort = FakeSummaryFitAutoExportStatePort(
            preference = FitExportPreference.ASK_EVERY_TIME,
            summary = sampleSummary(),
        )
        val record = AutoExportRecord()
        val coordinator = coordinator(
            statePort = statePort,
            record = record,
        )

        coordinator.tryAutoExportIfNeeded(statePort)

        assertTrue(record.events.isEmpty())
        assertNull(statePort.lastExportedSummaryFingerprint)
    }

    @Test
    fun tryAutoExportIfNeeded_skipsWhenSummaryIsMissing() {
        val statePort = FakeSummaryFitAutoExportStatePort(
            preference = FitExportPreference.AUTO_SAVE,
            summary = null,
        )
        val record = AutoExportRecord()
        val coordinator = coordinator(
            statePort = statePort,
            record = record,
        )

        coordinator.tryAutoExportIfNeeded(statePort)

        assertTrue(record.events.isEmpty())
        assertNull(statePort.lastExportedSummaryFingerprint)
    }

    @Test
    fun tryAutoExportIfNeeded_skipsWhenFingerprintWasAlreadyProcessed() {
        val summary = sampleSummary()
        val statePort = FakeSummaryFitAutoExportStatePort(
            preference = FitExportPreference.AUTO_SAVE,
            summary = summary,
            lastExportedSummaryFingerprint = "fingerprint:${summary.startTimestampMillis}",
        )
        val record = AutoExportRecord()
        val coordinator = coordinator(
            statePort = statePort,
            record = record,
        )

        coordinator.tryAutoExportIfNeeded(statePort)

        assertTrue(record.events.isEmpty())
        assertEquals("fingerprint:${summary.startTimestampMillis}", statePort.lastExportedSummaryFingerprint)
    }

    @Test
    fun tryAutoExportIfNeeded_recordsFingerprintBeforePreparingExportAndRoutesToDocumentsFolderWrite() {
        val summary = sampleSummary()
        val statePort = FakeSummaryFitAutoExportStatePort(
            preference = FitExportPreference.AUTO_SAVE,
            summary = summary,
        )
        val record = AutoExportRecord()
        val coordinator = coordinator(
            statePort = statePort,
            record = record,
        )

        coordinator.tryAutoExportIfNeeded(statePort)

        assertEquals("fingerprint:${summary.startTimestampMillis}", statePort.lastExportedSummaryFingerprint)
        assertEquals(
            listOf(
                "prepare_export:last=fingerprint:${summary.startTimestampMillis}",
                "try_export:session.fit:false",
            ),
            record.events,
        )
    }

    @Test
    fun tryAutoExportIfNeeded_keepsFingerprintWhenPrepareExportFails() {
        val summary = sampleSummary()
        val statePort = FakeSummaryFitAutoExportStatePort(
            preference = FitExportPreference.AUTO_SAVE,
            summary = summary,
        )
        val record = AutoExportRecord()
        val coordinator = coordinator(
            statePort = statePort,
            record = record,
            prepareExport = { null },
        )

        coordinator.tryAutoExportIfNeeded(statePort)

        assertEquals("fingerprint:${summary.startTimestampMillis}", statePort.lastExportedSummaryFingerprint)
        assertEquals(
            listOf("prepare_export:last=fingerprint:${summary.startTimestampMillis}"),
            record.events,
        )
    }

    private fun coordinator(
        statePort: FakeSummaryFitAutoExportStatePort,
        record: AutoExportRecord,
        prepareExport: () -> String? = { "session.fit" },
    ): SummaryFitAutoExportCoordinator {
        return SummaryFitAutoExportCoordinator(
            createFingerprint = { summary ->
                "fingerprint:${summary.startTimestampMillis}"
            },
            prepareExport = {
                record.events += "prepare_export:last=${statePort.lastExportedSummaryFingerprint}"
                prepareExport()
            },
            tryExportPendingToDocumentsFolder = { suggestedFileName, allowPickerFallback ->
                record.events += "try_export:$suggestedFileName:$allowPickerFallback"
            },
        )
    }

    private fun sampleSummary(startMillis: Long = 1_000L): SessionSummary {
        return SessionSummary(
            startTimestampMillis = startMillis,
            stopTimestampMillis = startMillis + 1_000L,
            durationSeconds = 1,
            actualTss = 42.0,
            avgPower = 210,
            maxPower = 420,
            avgCadence = 88,
            maxCadence = 101,
            avgHeartRate = 145,
            maxHeartRate = 168,
            distanceMeters = 12_345,
            totalEnergyKcal = 678,
        )
    }

    private class FakeSummaryFitAutoExportStatePort(
        override val preference: FitExportPreference?,
        override val summary: SessionSummary?,
        override var lastExportedSummaryFingerprint: String? = null,
    ) : SummaryFitAutoExportStatePort

    private class AutoExportRecord {
        val events = mutableListOf<String>()
    }
}
