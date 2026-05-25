package com.example.ergometerapp

import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.session.export.SessionExportSnapshot

/**
 * Concrete [SummaryExitStatePort] backed by the existing summary-related state owners.
 *
 * Why:
 * - Summary exit cleanup spans multiple extracted owners, so this adapter keeps the shared
 *   reset contract explicit without rebuilding another anonymous object inside `MainViewModel`.
 * - The adapter intentionally depends only on the narrow state surfaces already responsible for
 *   summary, export, and compatibility cleanup so the coordinator stays framework-light.
 */
internal class RealSummaryExitStatePort(
    private val uiState: AppUiState,
    private val documentsFolderUiState: DocumentsFolderUiState,
    private val compatibilityModeUiState: CompatibilityModeUiState,
    private val summaryFitUiState: SummaryFitUiState,
) : SummaryExitStatePort {
    override var summary: SessionSummary?
        get() = uiState.summary.value
        set(value) {
            uiState.summary.value = value
        }

    override var connectingTimeoutMessage: String?
        get() = uiState.connectionRecoveryUiState.connectingTimeoutMessageState.value
        set(value) {
            uiState.connectionRecoveryUiState.connectingTimeoutMessageState.value = value
        }

    override var fitPendingSnapshot: SessionExportSnapshot?
        get() = documentsFolderUiState.pendingFitExportSnapshot
        set(value) {
            documentsFolderUiState.pendingFitExportSnapshot = value
        }

    override var fitStatusMessage: String?
        get() = documentsFolderUiState.fitExportStatusMessageState.value
        set(value) {
            documentsFolderUiState.fitExportStatusMessageState.value = value
        }

    override var fitStatusIsError: Boolean
        get() = documentsFolderUiState.fitExportStatusIsErrorState.value
        set(value) {
            documentsFolderUiState.fitExportStatusIsErrorState.value = value
        }

    override var lastAutoExportSummaryFingerprint: String?
        get() = summaryFitUiState.lastAutoExportSummaryFingerprint
        set(value) {
            summaryFitUiState.lastAutoExportSummaryFingerprint = value
        }
}
