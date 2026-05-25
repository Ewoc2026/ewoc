package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityScreenWakeCoordinatorTest {
    @Test
    fun keepScreenOn_addsKeepAwakeFlag() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(events)

        coordinator.keepScreenOn()

        assertEquals(listOf("add:123"), events)
    }

    @Test
    fun allowScreenOff_clearsKeepAwakeFlag() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(events)

        coordinator.allowScreenOff()

        assertEquals(listOf("clear:123"), events)
    }

    @Test
    fun keepAndRelease_useSameFlagValue() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(events)

        coordinator.keepScreenOn()
        coordinator.allowScreenOff()

        assertEquals(listOf("add:123", "clear:123"), events)
    }

    private fun createCoordinator(events: MutableList<String>): MainActivityScreenWakeCoordinator {
        return MainActivityScreenWakeCoordinator(
            addWindowFlags = { flags ->
                events += "add:$flags"
            },
            clearWindowFlags = { flags ->
                events += "clear:$flags"
            },
            keepScreenOnFlag = 123,
        )
    }
}
