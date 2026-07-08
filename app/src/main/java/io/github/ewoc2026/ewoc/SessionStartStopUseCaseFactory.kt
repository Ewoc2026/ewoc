package io.github.ewoc2026.ewoc

/**
 * Hooks that keep pre/post ordering around session start/stop and permission callbacks explicit.
 */
internal data class SessionStartStopHooks(
    val onBeforeStartSessionConnection: () -> Unit,
    val onBeforeEndSessionToSummary: () -> Unit,
    val onAfterEndSessionToSummary: () -> Unit,
    val onAfterBluetoothPermissionResult: () -> Unit,
)

/**
 * Builds session start/stop hooks with stable side-effect ordering.
 */
internal fun createSessionStartStopHooks(
    onSessionStarted: () -> Unit,
    cancelTrainerStatusProbeScan: () -> Unit,
    cancelHrStatusProbeScan: () -> Unit,
    clearSessionFitExportStatus: () -> Unit,
    refreshSummaryRecommendations: () -> Unit,
    refreshRecommendationsAfterPermission: () -> Unit,
): SessionStartStopHooks {
    return SessionStartStopHooks(
        onBeforeStartSessionConnection = {
            onSessionStarted()
            cancelTrainerStatusProbeScan()
            cancelHrStatusProbeScan()
        },
        onBeforeEndSessionToSummary = {
            clearSessionFitExportStatus()
        },
        onAfterEndSessionToSummary = {
            refreshSummaryRecommendations()
        },
        onAfterBluetoothPermissionResult = {
            refreshRecommendationsAfterPermission()
        },
    )
}

/**
 * Full dependency bundle for creating the session start/stop use-case.
 */
internal data class SessionStartStopDependencies(
    val canStartSession: () -> Boolean,
    val hooks: SessionStartStopHooks,
    val sessionControlPort: SessionStartStopPort,
)

/**
 * Creates the session start/stop use-case with stable ordering and an explicit control port.
 */
internal fun createSessionStartStopUseCase(
    dependencies: SessionStartStopDependencies,
): SessionStartStopUseCase {
    return SessionCoordinator(
        canStartSession = dependencies.canStartSession,
        onBeforeStartSessionConnection = dependencies.hooks.onBeforeStartSessionConnection,
        onBeforeEndSessionToSummary = dependencies.hooks.onBeforeEndSessionToSummary,
        onAfterEndSessionToSummary = dependencies.hooks.onAfterEndSessionToSummary,
        onAfterBluetoothPermissionResult = dependencies.hooks.onAfterBluetoothPermissionResult,
        sessionControlPort = dependencies.sessionControlPort,
    )
}
