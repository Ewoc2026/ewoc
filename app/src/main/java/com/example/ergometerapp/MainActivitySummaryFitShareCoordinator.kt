package com.example.ergometerapp

import android.content.Intent

/**
 * Owns MainActivity-side summary FIT share launch handling so Compose callbacks stay declarative.
 *
 * Invariants:
 * - Share launch is attempted only after the ViewModel prepares a chooser intent.
 * - Launch failures are always both logged and reported back to the ViewModel status seam.
 * - Missing prepared intents are treated as terminal no-ops because the ViewModel already updated
 *   user-facing status for the underlying preparation failure.
 */
internal class MainActivitySummaryFitShareCoordinator(
    private val prepareShareIntent: () -> Intent?,
    private val launchActivity: (Intent) -> Unit,
    private val onLaunchFailed: () -> Unit,
    private val logLaunchFailure: (Throwable) -> Unit,
    private val log: (String) -> Unit = {},
) {
    private var shareLaunchInFlight = false

    fun requestShare() {
        if (shareLaunchInFlight) {
            log("Ignoring summary FIT share request because chooser launch is already in flight.")
            return
        }
        val shareIntent = prepareShareIntent() ?: return
        shareLaunchInFlight = true
        val launched = runCatching {
            log("Launching summary FIT share chooser.")
            launchActivity(shareIntent)
            true
        }.getOrElse { error ->
            shareLaunchInFlight = false
            log("Summary FIT share launch failed; gate released.")
            logLaunchFailure(error)
            false
        }
        if (!launched) {
            onLaunchFailed()
        }
    }

    /**
     * Releases the share-launch gate after control returns to the host activity.
     *
     * The Android chooser/share target lives outside our process boundary, so the activity resume
     * callback is the narrowest practical seam that proves the current external launch epoch has
     * ended and a new user-initiated share request may start.
     */
    fun onHostResumed() {
        if (!shareLaunchInFlight) return
        shareLaunchInFlight = false
        log("Summary FIT share chooser returned; gate released.")
    }
}
