package com.example.ergometerapp.compat

/**
 * Centralized default timing, retry, and power bounds for Compatibility Mode v1.
 *
 * These values are intentionally conservative so compatibility checks remain
 * deterministic, short, and low-risk on heterogeneous FTMS trainers.
 */
object CompatibilityV1Constants {
    const val TOTAL_TEST_BUDGET_MS = 45_000L
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val CONNECT_RETRY_COUNT = 1

    const val REQUEST_CONTROL_TIMEOUT_MS = 2_500L
    const val REQUEST_CONTROL_RETRY_ON_TIMEOUT_OR_WRITE_FAILURE = 1
    const val REQUEST_CONTROL_RETRY_ON_REJECT = 0

    const val CP_ACK_TIMEOUT_MS = 2_500L
    const val CP_ACK_RETRY_ON_TIMEOUT_OR_WRITE_FAILURE = 1
    const val CP_ACK_RETRY_ON_REJECT = 0

    const val PRECHECK_TELEMETRY_WINDOW_MS = 3_000L
    const val PRECHECK_TELEMETRY_MIN_PACKETS = 3
    const val TELEMETRY_STALL_WINDOW_MS = 2_000L

    const val STEP_HOLD_MS = 4_000L
    const val STEP_SETTLE_MS = 1_500L
    const val STEP_MIN_WATTS = 50
    const val STEP_MAX_WATTS = 180
    const val STEP_A_WATTS = 80
    const val STEP_B_WATTS = 140
    const val STEP_C_WATTS = 100

    const val STOP_ACK_TIMEOUT_MS = 2_500L
    const val STOP_RETRY_COUNT = 1
    const val STOP_FALLBACK_TARGET_WATTS = 0
    const val CLEANUP_DISCONNECT_TIMEOUT_MS = 4_000L
}
