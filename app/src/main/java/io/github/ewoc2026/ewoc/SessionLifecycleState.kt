package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.workout.runner.RunnerState

/**
 * High-level read model for the session journey as the user experiences it.
 *
 * This stays derived from existing UI/session signals so we can name the current
 * phase explicitly without rewriting the underlying orchestration logic first.
 */
enum class SessionLifecycleState {
    IDLE,
    PREPARING,
    CONNECTING,
    AWAITING_CONTROL,
    RUNNING,
    PAUSED,
    STOPPING,
    COMPLETED,
    FAILED,
}

internal data class SessionLifecycleSnapshot(
    val screen: AppScreen,
    val stopFlowState: StopFlowState,
    val ftmsReady: Boolean,
    val ftmsControlGranted: Boolean,
    val postWorkoutFreerideModeActive: Boolean,
    val pendingSessionStartAfterPermission: Boolean,
    val runnerState: RunnerState,
    val summaryAvailable: Boolean,
    val connectionIssueVisible: Boolean,
)

/**
 * Order matters here because terminal and failure states must win over the raw
 * screen value, otherwise MENU could hide whether the previous flow completed or failed.
 */
internal fun deriveSessionLifecycleState(
    snapshot: SessionLifecycleSnapshot,
): SessionLifecycleState {
    return when {
        snapshot.stopFlowState == StopFlowState.STOPPING_AWAIT_ACK ||
            snapshot.screen == AppScreen.STOPPING -> SessionLifecycleState.STOPPING

        snapshot.screen == AppScreen.SUMMARY && snapshot.summaryAvailable -> SessionLifecycleState.COMPLETED

        snapshot.connectionIssueVisible -> SessionLifecycleState.FAILED

        snapshot.pendingSessionStartAfterPermission -> SessionLifecycleState.PREPARING

        snapshot.screen == AppScreen.CONNECTING && snapshot.ftmsReady && !snapshot.ftmsControlGranted ->
            SessionLifecycleState.AWAITING_CONTROL

        snapshot.screen == AppScreen.CONNECTING -> SessionLifecycleState.CONNECTING

        snapshot.screen == AppScreen.SESSION && snapshot.postWorkoutFreerideModeActive ->
            SessionLifecycleState.RUNNING

        snapshot.screen == AppScreen.SESSION && !snapshot.ftmsControlGranted ->
            SessionLifecycleState.AWAITING_CONTROL

        snapshot.screen == AppScreen.SESSION && snapshot.runnerState.running && snapshot.runnerState.paused ->
            SessionLifecycleState.PAUSED

        snapshot.screen == AppScreen.SESSION -> SessionLifecycleState.RUNNING

        else -> SessionLifecycleState.IDLE
    }
}

internal fun AppUiState.deriveSessionLifecycleState(): SessionLifecycleState {
    val runtimeState = sessionRuntimeUiState
    val recoveryState = connectionRecoveryUiState
    return deriveSessionLifecycleState(
        SessionLifecycleSnapshot(
            screen = screen.value,
            stopFlowState = runtimeState.stopFlowState.value,
            ftmsReady = runtimeState.ftmsReady.value,
            ftmsControlGranted = runtimeState.ftmsControlGranted.value,
            postWorkoutFreerideModeActive = runtimeState.postWorkoutFreerideModeActive,
            pendingSessionStartAfterPermission = runtimeState.pendingSessionStartAfterPermission,
            runnerState = runner.value,
            summaryAvailable = summary.value != null,
            connectionIssueVisible = !recoveryState.connectionIssueMessageState.value.isNullOrBlank(),
        ),
    )
}
