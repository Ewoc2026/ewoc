package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoordinatorTest {

    @Test
    fun onStartSession_skipsCallbacksWhenStartGateFails() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(
            canStartSession = { false },
            onBeforeStartSessionConnection = { events += "beforeStart" },
            onBeforeEndSessionToSummary = { events += "beforeEnd" },
            onAfterEndSessionToSummary = { events += "afterEnd" },
            onAfterBluetoothPermissionResult = { events += "afterPermission" },
            sessionControlPort = createPort(events),
        )

        coordinator.onStartSession()

        assertTrue(events.isEmpty())
    }

    @Test
    fun onStartSession_runsPreStartHookBeforeOrchestratorStart() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(
            canStartSession = { true },
            onBeforeStartSessionConnection = { events += "beforeStart" },
            onBeforeEndSessionToSummary = { events += "beforeEnd" },
            onAfterEndSessionToSummary = { events += "afterEnd" },
            onAfterBluetoothPermissionResult = { events += "afterPermission" },
            sessionControlPort = createPort(events),
        )

        coordinator.onStartSession()

        assertEquals(listOf("beforeStart", "start"), events)
    }

    @Test
    fun onEndSessionAndGoToSummary_preservesHookOrdering() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(
            canStartSession = { true },
            onBeforeStartSessionConnection = { events += "beforeStart" },
            onBeforeEndSessionToSummary = { events += "beforeEnd" },
            onAfterEndSessionToSummary = { events += "afterEnd" },
            onAfterBluetoothPermissionResult = { events += "afterPermission" },
            sessionControlPort = createPort(events),
        )

        coordinator.onEndSessionAndGoToSummary()

        assertEquals(listOf("beforeEnd", "end", "afterEnd"), events)
    }

    @Test
    fun onBluetoothPermissionResult_forwardsValueBeforePostHook() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(
            canStartSession = { true },
            onBeforeStartSessionConnection = { events += "beforeStart" },
            onBeforeEndSessionToSummary = { events += "beforeEnd" },
            onAfterEndSessionToSummary = { events += "afterEnd" },
            onAfterBluetoothPermissionResult = { events += "afterPermission" },
            sessionControlPort = createPort(events),
        )

        coordinator.onBluetoothPermissionResult(granted = true, requestId = 0L)

        assertEquals(listOf("permission:true", "afterPermission"), events)
    }

    @Test
    fun onBluetoothPermissionResult_forwardsDeniedValueBeforePostHook() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(
            canStartSession = { true },
            onBeforeStartSessionConnection = { events += "beforeStart" },
            onBeforeEndSessionToSummary = { events += "beforeEnd" },
            onAfterEndSessionToSummary = { events += "afterEnd" },
            onAfterBluetoothPermissionResult = { events += "afterPermission" },
            sessionControlPort = createPort(events),
        )

        coordinator.onBluetoothPermissionResult(granted = false, requestId = 0L)

        assertEquals(listOf("permission:false", "afterPermission"), events)
    }

    private fun createCoordinator(
        canStartSession: () -> Boolean,
        onBeforeStartSessionConnection: () -> Unit,
        onBeforeEndSessionToSummary: () -> Unit,
        onAfterEndSessionToSummary: () -> Unit,
        onAfterBluetoothPermissionResult: () -> Unit,
        sessionControlPort: SessionStartStopPort,
    ): SessionCoordinator {
        return SessionCoordinator(
            canStartSession = canStartSession,
            onBeforeStartSessionConnection = onBeforeStartSessionConnection,
            onBeforeEndSessionToSummary = onBeforeEndSessionToSummary,
            onAfterEndSessionToSummary = onAfterEndSessionToSummary,
            onAfterBluetoothPermissionResult = onAfterBluetoothPermissionResult,
            sessionControlPort = sessionControlPort,
        )
    }

    private fun createPort(events: MutableList<String>): SessionStartStopPort {
        return object : SessionStartStopPort {
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
        }
    }
}
