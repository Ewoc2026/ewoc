package com.example.ergometerapp.session

import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.AppUiState

/**
 * Applies rollback-to-menu recovery side effects in deterministic order.
 *
 * Why this exists:
 * - Rollback can be triggered from both CONNECTING-timeout actions and request-control failures.
 * - Callers need one seam that guarantees cleanup and UI transition ordering remain consistent.
 *
 * Invariants:
 * - Runtime cleanup executes before publishing connection-issue UI state.
 * - `allowScreenOff` is always applied before diagnostics/scope-end callbacks run.
 * - `onAfterRollbackApplied` runs after UI already reflects MENU rollback state.
 */
internal class SessionRollbackToMenuRecoverySideEffectAdapter(
    private val uiState: AppUiState,
    private val cancelConnectFlowTimeout: () -> Unit,
    private val cancelMockConnectTransition: () -> Unit,
    private val stopMockTrainerEngine: () -> Unit,
    private val stopWorkout: () -> Unit,
    private val clearWorkoutRunner: () -> Unit,
    private val stopSession: () -> Unit,
    private val resetStopFlowPolicy: () -> Unit,
    private val resetFtmsUiState: () -> Unit,
    private val allowScreenOff: () -> Unit,
    private val closeBleTransport: () -> Unit,
    private val onAfterRollbackApplied: (reason: String) -> Unit,
) {
    /**
     * Applies rollback cleanup and returns the UI to MENU with connection guidance.
     */
    fun rollbackToMenu(
        message: String,
        reason: String,
        suggestTrainerSearch: Boolean,
        suggestOpenSettings: Boolean,
    ) {
        cancelConnectFlowTimeout()
        cancelMockConnectTransition()
        stopMockTrainerEngine()
        stopWorkout()
        clearWorkoutRunner()
        stopSession()
        resetStopFlowPolicy()
        resetFtmsUiState()
        uiState.pendingSessionStartAfterPermission = false
        uiState.pendingCadenceStartAfterControlGranted = false
        uiState.autoPausedByZeroCadence = false
        uiState.connectingTimeoutMessage.value = null
        uiState.connectionIssueMessage.value = message
        uiState.suggestTrainerSearchAfterConnectionIssue.value = suggestTrainerSearch
        uiState.suggestOpenSettingsAfterConnectionIssue.value = suggestOpenSettings
        uiState.screen.value = AppScreen.MENU
        allowScreenOff()
        closeBleTransport()
        onAfterRollbackApplied(reason)
    }
}
