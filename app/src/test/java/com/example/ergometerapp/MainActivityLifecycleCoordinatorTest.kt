package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityLifecycleCoordinatorTest {
    @Test
    fun handleOnDestroy_unbindsDuringConfigurationChange() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(events)

        coordinator.handleOnDestroy(
            isChangingConfigurations = true,
            isFinishing = false,
        )

        assertEquals(listOf("unbind"), events)
    }

    @Test
    fun handleOnDestroy_stopsAndClosesWhenActivityIsFinishing() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(events)

        coordinator.handleOnDestroy(
            isChangingConfigurations = false,
            isFinishing = true,
        )

        assertEquals(listOf("stop"), events)
    }

    @Test
    fun handleOnDestroy_unbindsWhenSystemDestroysNonFinishingActivity() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(events)

        coordinator.handleOnDestroy(
            isChangingConfigurations = false,
            isFinishing = false,
        )

        assertEquals(listOf("unbind"), events)
    }

    @Test
    fun handleOnDestroy_prioritizesConfigurationChangeOverFinish() {
        val events = mutableListOf<String>()
        val coordinator = createCoordinator(events)

        coordinator.handleOnDestroy(
            isChangingConfigurations = true,
            isFinishing = true,
        )

        assertEquals(listOf("unbind"), events)
    }

    private fun createCoordinator(events: MutableList<String>): MainActivityLifecycleCoordinator {
        return MainActivityLifecycleCoordinator(
            unbindActivityCallbacks = {
                events += "unbind"
            },
            stopAndClose = {
                events += "stop"
            },
        )
    }
}
