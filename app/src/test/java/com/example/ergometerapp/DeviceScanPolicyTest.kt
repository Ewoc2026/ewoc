package com.example.ergometerapp

import android.bluetooth.le.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceScanPolicyTest {
    @Test
    fun pickerUsesLowLatencyAndProbeUsesBalancedMode() {
        assertEquals(
            ScanSettings.SCAN_MODE_LOW_LATENCY,
            DeviceScanPolicy.scanModeFor(DeviceScanPolicy.Purpose.PICKER),
        )
        assertEquals(
            ScanSettings.SCAN_MODE_BALANCED,
            DeviceScanPolicy.scanModeFor(DeviceScanPolicy.Purpose.MENU_PROBE),
        )
    }

    @Test
    fun retryTooFrequentRequiresAllowFlagActivePickerAndCodeSix() {
        assertTrue(
            DeviceScanPolicy.shouldRetryTooFrequent(
                allowRetryOnTooFrequent = true,
                errorMessage = "Scan failed (code=6).",
                isSelectionStillActive = true,
            ),
        )
        assertFalse(
            DeviceScanPolicy.shouldRetryTooFrequent(
                allowRetryOnTooFrequent = false,
                errorMessage = "Scan failed (code=6).",
                isSelectionStillActive = true,
            ),
        )
        assertFalse(
            DeviceScanPolicy.shouldRetryTooFrequent(
                allowRetryOnTooFrequent = true,
                errorMessage = "Scan failed (code=6).",
                isSelectionStillActive = false,
            ),
        )
        assertFalse(
            DeviceScanPolicy.shouldRetryTooFrequent(
                allowRetryOnTooFrequent = true,
                errorMessage = "Scan failed (code=2).",
                isSelectionStillActive = true,
            ),
        )
    }

    @Test
    fun completionClassificationMapsToExpectedUiStatusPath() {
        assertEquals(
            DeviceScanPolicy.Completion.ERROR,
            DeviceScanPolicy.classifyCompletion(
                errorMessage = "Scan failed (code=2).",
                resultCount = 0,
            ),
        )
        assertEquals(
            DeviceScanPolicy.Completion.NO_RESULTS,
            DeviceScanPolicy.classifyCompletion(
                errorMessage = null,
                resultCount = 0,
            ),
        )
        assertEquals(
            DeviceScanPolicy.Completion.DONE,
            DeviceScanPolicy.classifyCompletion(
                errorMessage = null,
                resultCount = 2,
            ),
        )
    }
}
