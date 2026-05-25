package com.example.ergometerapp.session.export

/**
 * Typed result for session FIT export so UI can show deterministic feedback.
 */
sealed class FitExportResult {
    data object Success : FitExportResult()
    data class Failure(
        val reason: FitExportFailureReason,
        val detail: String? = null,
    ) : FitExportResult()
}

enum class FitExportFailureReason {
    NO_SUMMARY,
    INVALID_TIMESTAMPS,
    OUTPUT_STREAM_UNAVAILABLE,
    WRITE_FAILED,
}

