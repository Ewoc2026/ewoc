package com.example.ergometerapp

import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Owns MainActivity system-UI policy so screen-specific navigation-bar behavior stays testable
 * outside the Compose runtime and window controller implementation.
 *
 * Invariants:
 * - Session, connecting, and stopping screens must keep navigation bars hidden with transient-swipe
 *   recovery so workout telemetry stays immersive without permanently trapping system navigation.
 * - Non-session screens must restore navigation-bar visibility instead of inheriting immersive state
 *   from a previous ride-oriented destination.
 */
internal class MainActivitySystemUiCoordinator(
    private val navigationBarsType: Int = WindowInsetsCompat.Type.navigationBars(),
    private val transientBarsBehavior: Int =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
) {

    fun applyForScreen(
        currentScreen: AppScreen,
        setSystemBarsBehavior: (Int) -> Unit,
        hideInsets: (Int) -> Unit,
        showInsets: (Int) -> Unit,
    ) {
        if (currentScreen.isImmersiveNavigationBarScreen()) {
            setSystemBarsBehavior(transientBarsBehavior)
            hideInsets(navigationBarsType)
            return
        }
        showInsets(navigationBarsType)
    }

    private fun AppScreen.isImmersiveNavigationBarScreen(): Boolean {
        return this == AppScreen.SESSION ||
            this == AppScreen.CONNECTING ||
            this == AppScreen.STOPPING
    }
}
