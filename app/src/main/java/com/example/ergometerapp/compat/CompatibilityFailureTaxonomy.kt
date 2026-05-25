package com.example.ergometerapp.compat

/**
 * High-level failure group used by Compatibility Mode summaries and support triage.
 */
enum class CompatibilityFailureCategory {
    CONNECTION,
    CONTROL_POINT,
    POWER_STEP,
    CLEANUP,
    DEADLINE,
    UNKNOWN,
}

/**
 * Stable failure code emitted by Compatibility Mode.
 *
 * These codes are intentionally explicit so support flows can route users to the
 * right troubleshooting path without relying on fragile log parsing.
 */
enum class CompatibilityFailureCode {
    CONNECT_TIMEOUT,
    CONNECT_DISCONNECTED,
    CONNECT_FAILED,
    REQUEST_CONTROL_TIMEOUT,
    REQUEST_CONTROL_WRITE_NOT_STARTED,
    REQUEST_CONTROL_REJECTED,
    REQUEST_CONTROL_FAILED,
    POWER_STEP_TIMEOUT,
    POWER_STEP_WRITE_NOT_STARTED,
    POWER_STEP_REJECTED,
    POWER_STEP_FAILED,
    STOP_TIMEOUT,
    STOP_WRITE_NOT_STARTED,
    STOP_REJECTED,
    STOP_FAILED,
    CLEANUP_FALLBACK_TIMEOUT,
    CLEANUP_FALLBACK_WRITE_NOT_STARTED,
    CLEANUP_FALLBACK_FAILED,
    CLEANUP_DISCONNECT_FAILED,
    GLOBAL_DEADLINE_EXCEEDED,
    UNKNOWN_FAILURE,
}

/**
 * Derived classification metadata exported with each failure code.
 */
data class CompatibilityFailureClassification(
    val category: CompatibilityFailureCategory,
    val reasonKey: String,
)

/**
 * Maps stable failure codes to support-facing category + reason-key outputs.
 */
object CompatibilityFailureClassifier {
    fun classify(code: CompatibilityFailureCode): CompatibilityFailureClassification {
        return when (code) {
            CompatibilityFailureCode.CONNECT_TIMEOUT -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CONNECTION,
                    reasonKey = "connect_timeout",
                )
            }

            CompatibilityFailureCode.CONNECT_DISCONNECTED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CONNECTION,
                    reasonKey = "connect_disconnected",
                )
            }

            CompatibilityFailureCode.CONNECT_FAILED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CONNECTION,
                    reasonKey = "connect_failed",
                )
            }

            CompatibilityFailureCode.REQUEST_CONTROL_TIMEOUT -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CONTROL_POINT,
                    reasonKey = "request_control_timeout",
                )
            }

            CompatibilityFailureCode.REQUEST_CONTROL_WRITE_NOT_STARTED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CONTROL_POINT,
                    reasonKey = "request_control_write_not_started",
                )
            }

            CompatibilityFailureCode.REQUEST_CONTROL_REJECTED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CONTROL_POINT,
                    reasonKey = "request_control_rejected",
                )
            }

            CompatibilityFailureCode.REQUEST_CONTROL_FAILED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CONTROL_POINT,
                    reasonKey = "request_control_failed",
                )
            }

            CompatibilityFailureCode.POWER_STEP_TIMEOUT -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.POWER_STEP,
                    reasonKey = "power_step_timeout",
                )
            }

            CompatibilityFailureCode.POWER_STEP_WRITE_NOT_STARTED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.POWER_STEP,
                    reasonKey = "power_step_write_not_started",
                )
            }

            CompatibilityFailureCode.POWER_STEP_REJECTED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.POWER_STEP,
                    reasonKey = "power_step_rejected",
                )
            }

            CompatibilityFailureCode.POWER_STEP_FAILED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.POWER_STEP,
                    reasonKey = "power_step_failed",
                )
            }

            CompatibilityFailureCode.STOP_TIMEOUT -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CLEANUP,
                    reasonKey = "stop_timeout",
                )
            }

            CompatibilityFailureCode.STOP_WRITE_NOT_STARTED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CLEANUP,
                    reasonKey = "stop_write_not_started",
                )
            }

            CompatibilityFailureCode.STOP_REJECTED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CLEANUP,
                    reasonKey = "stop_rejected",
                )
            }

            CompatibilityFailureCode.STOP_FAILED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CLEANUP,
                    reasonKey = "stop_failed",
                )
            }

            CompatibilityFailureCode.CLEANUP_FALLBACK_TIMEOUT -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CLEANUP,
                    reasonKey = "cleanup_fallback_timeout",
                )
            }

            CompatibilityFailureCode.CLEANUP_FALLBACK_WRITE_NOT_STARTED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CLEANUP,
                    reasonKey = "cleanup_fallback_write_not_started",
                )
            }

            CompatibilityFailureCode.CLEANUP_FALLBACK_FAILED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CLEANUP,
                    reasonKey = "cleanup_fallback_failed",
                )
            }

            CompatibilityFailureCode.CLEANUP_DISCONNECT_FAILED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.CLEANUP,
                    reasonKey = "cleanup_disconnect_failed",
                )
            }

            CompatibilityFailureCode.GLOBAL_DEADLINE_EXCEEDED -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.DEADLINE,
                    reasonKey = "global_deadline_exceeded",
                )
            }

            CompatibilityFailureCode.UNKNOWN_FAILURE -> {
                CompatibilityFailureClassification(
                    category = CompatibilityFailureCategory.UNKNOWN,
                    reasonKey = "unknown_failure",
                )
            }
        }
    }
}
