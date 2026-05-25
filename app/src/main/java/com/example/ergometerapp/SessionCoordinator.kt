package com.example.ergometerapp

/**
 * Keeps MainViewModel session entrypoint wiring behavior-stable while SessionOrchestrator
 * remains the owner of the FTMS/session state machine.
 *
 * Invariant:
 * - Start flow runs only when start gate passes.
 * - End flow preserves pre-cleanup -> orchestrator stop -> post-refresh ordering.
 * - Bluetooth permission callback always reaches orchestrator before post-refresh hooks.
 */
internal class SessionCoordinator(
    private val canStartSession: () -> Boolean,
    private val onBeforeStartSessionConnection: () -> Unit,
    private val onBeforeEndSessionToSummary: () -> Unit,
    private val onAfterEndSessionToSummary: () -> Unit,
    private val onAfterBluetoothPermissionResult: () -> Unit,
    private val sessionControlPort: SessionStartStopPort,
) : SessionStartStopUseCase {
    override fun onStartSession() {
        if (!canStartSession()) {
            return
        }
        onBeforeStartSessionConnection()
        sessionControlPort.startSessionConnection()
    }

    override fun onEndSessionAndGoToSummary() {
        onBeforeEndSessionToSummary()
        sessionControlPort.endSessionAndGoToSummary()
        onAfterEndSessionToSummary()
    }

    override val pendingBluetoothPermissionRequestId: Long
        get() = sessionControlPort.pendingBluetoothPermissionRequestId

    override fun onBluetoothPermissionResult(granted: Boolean, requestId: Long) {
        sessionControlPort.onBluetoothPermissionResult(granted, requestId)
        onAfterBluetoothPermissionResult()
    }
}
