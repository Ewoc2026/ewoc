package com.example.ergometerapp

import android.view.WindowManager

/**
 * Owns MainActivity screen-awake flag policy so session visibility rules stay testable outside the
 * Android window implementation.
 *
 * Invariants:
 * - Session-driven wake requests must always target the keep-screen-on window flag instead of
 *   mutating unrelated Activity window state.
 * - Releasing the wake lock must clear the same flag value that activation set so stop/summary
 *   transitions do not leave stale keep-awake state behind.
 */
internal class MainActivityScreenWakeCoordinator(
    private val addWindowFlags: (Int) -> Unit,
    private val clearWindowFlags: (Int) -> Unit,
    private val keepScreenOnFlag: Int = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
) {

    fun keepScreenOn() {
        addWindowFlags(keepScreenOnFlag)
    }

    fun allowScreenOff() {
        clearWindowFlags(keepScreenOnFlag)
    }
}
