package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.workout.runner.RunnerState
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLifecycleStateTest {

    @Test
    fun connectionIssueAtMenuMapsToFailed() {
        val snapshot = baseSnapshot(
            connectionIssueVisible = true,
        )

        assertEquals(SessionLifecycleState.FAILED, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun pendingPermissionMapsToPreparingBeforeConnectingScreen() {
        val snapshot = baseSnapshot(
            pendingSessionStartAfterPermission = true,
        )

        assertEquals(SessionLifecycleState.PREPARING, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun readyConnectingWithoutControlMapsToAwaitingControl() {
        val snapshot = baseSnapshot(
            screen = AppScreen.CONNECTING,
            ftmsReady = true,
            ftmsControlGranted = false,
        )

        assertEquals(SessionLifecycleState.AWAITING_CONTROL, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun pausedRunnerDuringSessionMapsToPaused() {
        val snapshot = baseSnapshot(
            screen = AppScreen.SESSION,
            ftmsReady = true,
            ftmsControlGranted = true,
            runnerState = RunnerState(
                running = true,
                paused = true,
                done = false,
                label = "Recovery",
                targetPowerWatts = 180,
                targetCadence = 90,
                workoutElapsedSec = 120,
                stepRemainingSec = 30,
                intervalPart = null,
            ),
        )

        assertEquals(SessionLifecycleState.PAUSED, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun stopFlowWinsOverSummaryAndMenuSignals() {
        val snapshot = baseSnapshot(
            screen = AppScreen.STOPPING,
            stopFlowState = StopFlowState.STOPPING_AWAIT_ACK,
            summaryAvailable = true,
            connectionIssueVisible = true,
        )

        assertEquals(SessionLifecycleState.STOPPING, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun completedWinsOverFailureSignalOnSummaryScreen() {
        val snapshot = baseSnapshot(
            screen = AppScreen.SUMMARY,
            summaryAvailable = true,
            connectionIssueVisible = true,
        )

        assertEquals(SessionLifecycleState.COMPLETED, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun summaryScreenWithSummaryMapsToCompleted() {
        val snapshot = baseSnapshot(
            screen = AppScreen.SUMMARY,
            summaryAvailable = true,
        )

        assertEquals(SessionLifecycleState.COMPLETED, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun appUiStateExtensionUsesCurrentFlags() {
        val uiState = AppUiState().apply {
            screen.value = AppScreen.CONNECTING
            ftmsReady.value = true
            ftmsControlGranted.value = false
        }

        assertEquals(SessionLifecycleState.AWAITING_CONTROL, uiState.deriveSessionLifecycleState())
    }

    @Test
    fun connectingScreenWithoutReadyMapsToConnecting() {
        val snapshot = baseSnapshot(
            screen = AppScreen.CONNECTING,
            ftmsReady = false,
        )

        assertEquals(SessionLifecycleState.CONNECTING, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun pendingPermissionStillMapsToPreparingEvenIfSessionScreenWasAlreadyRequested() {
        val snapshot = baseSnapshot(
            screen = AppScreen.SESSION,
            pendingSessionStartAfterPermission = true,
            ftmsReady = true,
            ftmsControlGranted = true,
        )

        assertEquals(SessionLifecycleState.PREPARING, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun sessionScreenWithControlLostMapsToAwaitingControl() {
        val snapshot = baseSnapshot(
            screen = AppScreen.SESSION,
            ftmsReady = true,
            ftmsControlGranted = false,
        )

        assertEquals(SessionLifecycleState.AWAITING_CONTROL, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun postWorkoutFreerideSessionMapsToRunningWithoutControl() {
        val snapshot = baseSnapshot(
            screen = AppScreen.SESSION,
            ftmsReady = true,
            ftmsControlGranted = false,
            postWorkoutFreerideModeActive = true,
        )

        assertEquals(SessionLifecycleState.RUNNING, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun sessionScreenWithActiveRunnerMapsToRunning() {
        val snapshot = baseSnapshot(
            screen = AppScreen.SESSION,
            ftmsReady = true,
            ftmsControlGranted = true,
            runnerState = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = "Tempo",
                targetPowerWatts = 250,
                targetCadence = 95,
                workoutElapsedSec = 300,
                stepRemainingSec = 120,
                intervalPart = null,
            ),
        )

        assertEquals(SessionLifecycleState.RUNNING, deriveSessionLifecycleState(snapshot))
    }

    @Test
    fun menuScreenWithNoActiveSignalMapsToIdle() {
        val snapshot = baseSnapshot()

        assertEquals(SessionLifecycleState.IDLE, deriveSessionLifecycleState(snapshot))
    }

    private fun baseSnapshot(
        screen: AppScreen = AppScreen.MENU,
        stopFlowState: StopFlowState = StopFlowState.IDLE,
        ftmsReady: Boolean = false,
        ftmsControlGranted: Boolean = false,
        postWorkoutFreerideModeActive: Boolean = false,
        pendingSessionStartAfterPermission: Boolean = false,
        runnerState: RunnerState = RunnerState.stopped(),
        summaryAvailable: Boolean = false,
        connectionIssueVisible: Boolean = false,
    ): SessionLifecycleSnapshot {
        return SessionLifecycleSnapshot(
            screen = screen,
            stopFlowState = stopFlowState,
            ftmsReady = ftmsReady,
            ftmsControlGranted = ftmsControlGranted,
            postWorkoutFreerideModeActive = postWorkoutFreerideModeActive,
            pendingSessionStartAfterPermission = pendingSessionStartAfterPermission,
            runnerState = runnerState,
            summaryAvailable = summaryAvailable,
            connectionIssueVisible = connectionIssueVisible,
        )
    }
}
