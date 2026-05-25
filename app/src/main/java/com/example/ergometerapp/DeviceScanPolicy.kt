package com.example.ergometerapp

import android.bluetooth.le.ScanSettings

/**
 * Centralized policy for scanner mode and picker scan completion decisions.
 *
 * Keeping this logic pure makes scan behavior regression-testable without BLE
 * hardware or Android scanner callbacks.
 */
internal object DeviceScanPolicy {
    internal enum class Purpose {
        PICKER,
        MENU_PROBE,
    }

    internal enum class Completion {
        ERROR,
        NO_RESULTS,
        DONE,
    }

    /**
     * Returns scan mode for each scanner purpose.
     */
    fun scanModeFor(purpose: Purpose): Int {
        return when (purpose) {
            Purpose.PICKER -> ScanSettings.SCAN_MODE_LOW_LATENCY
            Purpose.MENU_PROBE -> ScanSettings.SCAN_MODE_BALANCED
        }
    }

    /**
     * Returns true when a picker completion should retry automatically.
     *
     * Retries are intentionally limited to Android's "scanning too frequently"
     * error and only while the same picker is still active.
     */
    fun shouldRetryTooFrequent(
        allowRetryOnTooFrequent: Boolean,
        errorMessage: String?,
        isSelectionStillActive: Boolean,
    ): Boolean {
        return allowRetryOnTooFrequent &&
            isSelectionStillActive &&
            errorMessage?.contains("code=6") == true
    }

    /**
     * Classifies the non-retry completion into a deterministic UI status path.
     */
    fun classifyCompletion(errorMessage: String?, resultCount: Int): Completion {
        return when {
            errorMessage != null -> Completion.ERROR
            resultCount <= 0 -> Completion.NO_RESULTS
            else -> Completion.DONE
        }
    }
}
