package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivitySystemUiCoordinatorTest {
    @Test
    fun applyForScreen_hidesNavigationBarsForSessionScreens() {
        val sessionEvents = mutableListOf<String>()
        val connectingEvents = mutableListOf<String>()
        val stoppingEvents = mutableListOf<String>()
        val coordinator = createCoordinator()

        coordinator.applyForScreen(
            currentScreen = AppScreen.SESSION,
            setSystemBarsBehavior = { behavior -> sessionEvents += "behavior:$behavior" },
            hideInsets = { type -> sessionEvents += "hide:$type" },
            showInsets = { type -> sessionEvents += "show:$type" },
        )
        coordinator.applyForScreen(
            currentScreen = AppScreen.CONNECTING,
            setSystemBarsBehavior = { behavior -> connectingEvents += "behavior:$behavior" },
            hideInsets = { type -> connectingEvents += "hide:$type" },
            showInsets = { type -> connectingEvents += "show:$type" },
        )
        coordinator.applyForScreen(
            currentScreen = AppScreen.STOPPING,
            setSystemBarsBehavior = { behavior -> stoppingEvents += "behavior:$behavior" },
            hideInsets = { type -> stoppingEvents += "hide:$type" },
            showInsets = { type -> stoppingEvents += "show:$type" },
        )

        assertEquals(listOf("behavior:77", "hide:55"), sessionEvents)
        assertEquals(listOf("behavior:77", "hide:55"), connectingEvents)
        assertEquals(listOf("behavior:77", "hide:55"), stoppingEvents)
    }

    @Test
    fun applyForScreen_showsNavigationBarsForNonSessionScreens() {
        val screens = listOf(
            AppScreen.MENU,
            AppScreen.EWO_EDITOR,
            AppScreen.SUMMARY,
        )
        val coordinator = createCoordinator()

        screens.forEach { screen ->
            val events = mutableListOf<String>()

            coordinator.applyForScreen(
                currentScreen = screen,
                setSystemBarsBehavior = { behavior -> events += "behavior:$behavior" },
                hideInsets = { type -> events += "hide:$type" },
                showInsets = { type -> events += "show:$type" },
            )

            assertEquals(listOf("show:55"), events)
        }
    }

    private fun createCoordinator(): MainActivitySystemUiCoordinator {
        return MainActivitySystemUiCoordinator(
            navigationBarsType = 55,
            transientBarsBehavior = 77,
        )
    }
}
