package com.example.ergometerapp

/**
 * Owns MainActivity teardown policy so lifecycle cleanup rules remain testable outside Android.
 *
 * Invariants:
 * - Configuration-change teardown must only unbind Activity callbacks because the replacement
 *   Activity instance is about to rebind them.
 * - Finishing teardown must stop and close the app session instead of leaving transports alive
 *   after the last Activity instance disappears.
 * - Non-finishing process-driven teardown still unbinds callbacks to avoid leaking stale Activity
 *   references when Android destroys the window for reasons other than an explicit finish.
 */
internal class MainActivityLifecycleCoordinator(
    private val unbindActivityCallbacks: () -> Unit,
    private val stopAndClose: () -> Unit,
) {

    fun handleOnDestroy(
        isChangingConfigurations: Boolean,
        isFinishing: Boolean,
    ) {
        if (isChangingConfigurations) {
            unbindActivityCallbacks()
            return
        }
        if (isFinishing) {
            stopAndClose()
            return
        }
        unbindActivityCallbacks()
    }
}
