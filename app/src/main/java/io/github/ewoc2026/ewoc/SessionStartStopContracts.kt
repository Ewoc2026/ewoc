package io.github.ewoc2026.ewoc

/**
 * Use-case boundary for session start/stop and Bluetooth permission intent handling.
 */
internal interface SessionStartStopUseCase {
    fun onStartSession()
    fun onEndSessionAndGoToSummary()
    val pendingBluetoothPermissionRequestId: Long
    fun onBluetoothPermissionResult(granted: Boolean, requestId: Long)
}

/**
 * Port that encapsulates orchestrator-facing session control operations.
 */
internal interface SessionStartStopPort {
    fun startSessionConnection()
    fun endSessionAndGoToSummary()

    /**
     * The permission request ID for the currently pending Bluetooth permission wait,
     * or 0 if no permission wait is active.
     *
     * Callers should capture this value when forwarding Android permission callbacks
     * so the orchestrator can reject stale callbacks from superseded requests.
     */
    val pendingBluetoothPermissionRequestId: Long

    fun onBluetoothPermissionResult(granted: Boolean, requestId: Long)
}
