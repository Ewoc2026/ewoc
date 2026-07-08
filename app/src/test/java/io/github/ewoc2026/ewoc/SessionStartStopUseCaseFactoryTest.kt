package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStartStopUseCaseFactoryTest {
    @Test
    fun createSessionStartStopHooks_preservesHookOrdering() {
        val events = mutableListOf<String>()
        val hooks = createSessionStartStopHooks(
            onSessionStarted = { events += "sessionStarted" },
            cancelTrainerStatusProbeScan = { events += "cancelTrainerProbe" },
            cancelHrStatusProbeScan = { events += "cancelHrProbe" },
            clearSessionFitExportStatus = { events += "clearFitExportStatus" },
            refreshSummaryRecommendations = { events += "refreshSummary" },
            refreshRecommendationsAfterPermission = { events += "refreshPermission" },
        )

        hooks.onBeforeStartSessionConnection()
        hooks.onBeforeEndSessionToSummary()
        hooks.onAfterEndSessionToSummary()
        hooks.onAfterBluetoothPermissionResult()

        assertEquals(
            listOf(
                "sessionStarted",
                "cancelTrainerProbe",
                "cancelHrProbe",
                "clearFitExportStatus",
                "refreshSummary",
                "refreshPermission",
            ),
            events,
        )
    }

    @Test
    fun createSessionStartStopUseCase_preservesStartAndPermissionOrdering() {
        val events = mutableListOf<String>()
        val useCase = createSessionStartStopUseCase(
            dependencies = SessionStartStopDependencies(
                canStartSession = { true },
                hooks = SessionStartStopHooks(
                    onBeforeStartSessionConnection = { events += "beforeStart" },
                    onBeforeEndSessionToSummary = { events += "beforeEnd" },
                    onAfterEndSessionToSummary = { events += "afterEnd" },
                    onAfterBluetoothPermissionResult = { events += "afterPermission" },
                ),
                sessionControlPort = object : SessionStartStopPort {
                    override fun startSessionConnection() {
                        events += "start"
                    }

                    override fun endSessionAndGoToSummary() {
                        events += "end"
                    }

                    override val pendingBluetoothPermissionRequestId: Long = 0L

                    override fun onBluetoothPermissionResult(granted: Boolean, requestId: Long) {
                        events += "permission:$granted"
                    }
                },
            ),
        )

        useCase.onStartSession()
        useCase.onBluetoothPermissionResult(true, requestId = 0L)

        assertEquals(
            listOf("beforeStart", "start", "permission:true", "afterPermission"),
            events,
        )
    }

    @Test
    fun createSessionStartStopUseCase_preservesFullPipelineOrdering() {
        val events = mutableListOf<String>()
        val useCase = createSessionStartStopUseCase(
            dependencies = SessionStartStopDependencies(
                canStartSession = { true },
                hooks = SessionStartStopHooks(
                    onBeforeStartSessionConnection = { events += "beforeStart" },
                    onBeforeEndSessionToSummary = { events += "beforeEnd" },
                    onAfterEndSessionToSummary = { events += "afterEnd" },
                    onAfterBluetoothPermissionResult = { events += "afterPermission" },
                ),
                sessionControlPort = object : SessionStartStopPort {
                    override fun startSessionConnection() {
                        events += "start"
                    }

                    override fun endSessionAndGoToSummary() {
                        events += "end"
                    }

                    override val pendingBluetoothPermissionRequestId: Long = 0L

                    override fun onBluetoothPermissionResult(granted: Boolean, requestId: Long) {
                        events += "permission:$granted"
                    }
                },
            ),
        )

        useCase.onStartSession()
        useCase.onEndSessionAndGoToSummary()
        useCase.onBluetoothPermissionResult(true, requestId = 0L)

        assertEquals(
            listOf(
                "beforeStart",
                "start",
                "beforeEnd",
                "end",
                "afterEnd",
                "permission:true",
                "afterPermission",
            ),
            events,
        )
    }

    @Test
    fun createSessionStartStopUseCase_startGateOnlyBlocksStartPath() {
        val events = mutableListOf<String>()
        val useCase = createSessionStartStopUseCase(
            dependencies = SessionStartStopDependencies(
                canStartSession = { false },
                hooks = SessionStartStopHooks(
                    onBeforeStartSessionConnection = { events += "beforeStart" },
                    onBeforeEndSessionToSummary = { events += "beforeEnd" },
                    onAfterEndSessionToSummary = { events += "afterEnd" },
                    onAfterBluetoothPermissionResult = { events += "afterPermission" },
                ),
                sessionControlPort = object : SessionStartStopPort {
                    override fun startSessionConnection() {
                        events += "start"
                    }

                    override fun endSessionAndGoToSummary() {
                        events += "end"
                    }

                    override val pendingBluetoothPermissionRequestId: Long = 0L

                    override fun onBluetoothPermissionResult(granted: Boolean, requestId: Long) {
                        events += "permission:$granted"
                    }
                },
            ),
        )

        useCase.onStartSession()
        useCase.onEndSessionAndGoToSummary()
        useCase.onBluetoothPermissionResult(false, requestId = 0L)

        assertEquals(
            listOf("beforeEnd", "end", "afterEnd", "permission:false", "afterPermission"),
            events,
        )
    }
}
