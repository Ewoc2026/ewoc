package io.github.ewoc2026.ewoc.ui

import androidx.compose.runtime.Composable
import io.github.ewoc2026.ewoc.FitExportPreference

@Composable
private fun SummaryScreenPreviewContent(
    fitExportPreference: FitExportPreference?,
    fitExportStatusMessage: String?,
    fitExportStatusIsError: Boolean,
) {
    ScreenPreviewTheme {
        SummaryScreen(
            summary = previewSessionSummary(),
            fitExportStatusMessage = fitExportStatusMessage,
            fitExportStatusIsError = fitExportStatusIsError,
            fitExportPreference = fitExportPreference,
            aiAssistantMessage = "Nice pacing. Recovery stayed controlled through the cooldown.",
            aiAssistantIsError = false,
            onRequestFitExport = {},
            onRequestFitShare = {},
            onRequestFitAutoExport = {},
            onFitExportPreferenceSelected = {},
            onBackToMenu = {},
        )
    }
}

@DestinationScreenPreviews
@Composable
private fun SummaryScreenPreview() {
    SummaryScreenPreviewContent(
        fitExportPreference = FitExportPreference.ASK_EVERY_TIME,
        fitExportStatusMessage = "FIT export is ready to save or share.",
        fitExportStatusIsError = false,
    )
}

@DestinationScreenPreviews
@Composable
private fun SummaryScreenExportErrorPreview() {
    SummaryScreenPreviewContent(
        fitExportPreference = FitExportPreference.ASK_EVERY_TIME,
        fitExportStatusMessage = "FIT export failed because storage access is no longer available.",
        fitExportStatusIsError = true,
    )
}
