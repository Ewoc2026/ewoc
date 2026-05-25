package com.example.ergometerapp

import androidx.compose.runtime.mutableStateOf
import com.example.ergometerapp.session.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SummaryFitUiStateTest {

    @Test
    fun preferenceAndAutoExportPortsShareSummaryFitBackingState() {
        val uiState = SummaryFitUiState()
        val statusMessageState = mutableStateOf<String?>(null)
        val statusIsErrorState = mutableStateOf(false)
        val summary = sampleSummary()
        val preferencePort = uiState.exportPreferenceStatePort(
            statusMessageState = statusMessageState,
            statusIsErrorState = statusIsErrorState,
        )
        val autoExportPort = uiState.autoExportStatePort(summaryProvider = { summary })

        preferencePort.preference = FitExportPreference.AUTO_SAVE
        preferencePort.statusMessage = "Saved"
        preferencePort.statusIsError = true
        autoExportPort.lastExportedSummaryFingerprint = "fingerprint-1"

        assertEquals(FitExportPreference.AUTO_SAVE, uiState.preferenceState.value)
        assertEquals("Saved", statusMessageState.value)
        assertEquals(true, statusIsErrorState.value)
        assertEquals(FitExportPreference.AUTO_SAVE, autoExportPort.preference)
        assertSame(summary, autoExportPort.summary)
        assertEquals("fingerprint-1", uiState.lastAutoExportSummaryFingerprint)
    }

    private fun sampleSummary(): SessionSummary {
        return SessionSummary(
            startTimestampMillis = 1_000L,
            stopTimestampMillis = 2_000L,
            durationSeconds = 1,
            actualTss = 4.2,
            avgPower = 180,
            maxPower = 220,
            avgCadence = 84,
            maxCadence = 92,
            avgHeartRate = 138,
            maxHeartRate = 151,
            distanceMeters = 700,
            totalEnergyKcal = 22,
        )
    }
}
