package io.github.ewoc2026.ewoc.session

/**
 * Owns the FreeRide release/reacquire protocol for structured FTMS target writes.
 *
 * The coordinator keeps rider-controlled FreeRide semantics separate from the surrounding
 * session flow: entering FreeRide releases ERG ownership when needed, and the first
 * structured target after FreeRide is delayed until control can be reacquired safely.
 */
internal class SessionFreeRideControlCoordinator(
    private val requestControl: () -> Unit,
    private val resetTrainer: () -> Unit,
    private val setTargetPower: (Int) -> Unit,
) {
    private var freeRideModeActive = false
    private var controlReleaseInFlight = false
    private var controlReleased = false
    private var pendingTargetAfterFreeRide: Int? = null
    private var controlReacquireRequested = false
    private var ignoreNextNullTargetWrite = false

    /**
     * Clears protocol state when the surrounding session flow is torn down.
     */
    fun clear() {
        freeRideModeActive = false
        controlReleaseInFlight = false
        controlReleased = false
        pendingTargetAfterFreeRide = null
        controlReacquireRequested = false
        ignoreNextNullTargetWrite = false
    }

    /**
     * Suppresses the runner's terminal null write after an explicit stop/reset path.
     */
    fun ignoreNextNullTargetWriteOnce() {
        ignoreNextNullTargetWrite = true
    }

    /**
     * During pure FreeRide we intentionally avoid auto-requesting control so trainer-side
     * manual adjustment remains available.
     */
    fun shouldRequestSessionControlOnReady(): Boolean {
        if (!freeRideModeActive) return true
        return pendingTargetAfterFreeRide != null
    }

    /**
     * Routes runner target updates through the FreeRide release/reacquire protocol.
     */
    fun onRunnerTargetWrite(
        targetWatts: Int?,
        ftmsReady: Boolean,
        controlGranted: Boolean,
    ) {
        if (!ftmsReady) return

        if (targetWatts == null) {
            if (ignoreNextNullTargetWrite) {
                ignoreNextNullTargetWrite = false
                return
            }
            enterFreeRideMode(controlGranted = controlGranted)
            return
        }
        ignoreNextNullTargetWrite = false

        if (
            freeRideModeActive ||
            controlReleaseInFlight ||
            controlReleased ||
            pendingTargetAfterFreeRide != null
        ) {
            exitFreeRideModeWithTarget(
                targetWatts = targetWatts,
                ftmsReady = ftmsReady,
                controlGranted = controlGranted,
            )
            return
        }

        if (controlGranted) {
            setTargetPower(targetWatts)
        }
    }

    /**
     * Tracks control ownership changes that arrive from BLE while FreeRide state is active.
     */
    fun onControlOwnershipChanged(
        controlGranted: Boolean,
        ftmsReady: Boolean,
    ) {
        if (!controlGranted) {
            controlReacquireRequested = false
            if (freeRideModeActive || controlReleaseInFlight || pendingTargetAfterFreeRide != null) {
                controlReleaseInFlight = false
                controlReleased = true
                maybeResumeStructuredControlAfterFreeRide(
                    ftmsReady = ftmsReady,
                    controlGranted = false,
                )
            } else {
                controlReleased = false
            }
            return
        }

        controlReacquireRequested = false
        maybeResumeStructuredControlAfterFreeRide(
            ftmsReady = ftmsReady,
            controlGranted = true,
        )
        if (!freeRideModeActive && pendingTargetAfterFreeRide == null) {
            controlReleaseInFlight = false
            controlReleased = false
        }
    }

    /**
     * Applies pending post-FreeRide work once Request Control succeeds.
     */
    fun onRequestControlSucceeded(
        ftmsReady: Boolean,
        controlGranted: Boolean,
    ) {
        controlReacquireRequested = false
        maybeResumeStructuredControlAfterFreeRide(
            ftmsReady = ftmsReady,
            controlGranted = controlGranted,
        )
    }

    /**
     * Marks the soft release as complete after RESET succeeds.
     */
    fun onFreeRideResetSucceeded(
        ftmsReady: Boolean,
        controlGranted: Boolean,
    ) {
        controlReleaseInFlight = false
        if (freeRideModeActive || pendingTargetAfterFreeRide != null) {
            controlReleased = true
            maybeResumeStructuredControlAfterFreeRide(
                ftmsReady = ftmsReady,
                controlGranted = controlGranted,
            )
        }
    }

    /**
     * Falls back to ownership-based release state when RESET times out.
     */
    fun onFreeRideResetTimeout(
        ftmsReady: Boolean,
        controlGranted: Boolean,
    ) {
        controlReleaseInFlight = false
        controlReleased = !controlGranted
        maybeResumeStructuredControlAfterFreeRide(
            ftmsReady = ftmsReady,
            controlGranted = controlGranted,
        )
    }

    /**
     * FreeRide entry uses RESET because FTMS does not define a separate "clear target power"
     * release procedure and explicit zero-target writes can feel unsafe on the real trainer.
     */
    private fun enterFreeRideMode(controlGranted: Boolean) {
        freeRideModeActive = true
        pendingTargetAfterFreeRide = null
        controlReacquireRequested = false

        if (!controlGranted) {
            controlReleaseInFlight = false
            controlReleased = true
            return
        }

        if (controlReleaseInFlight || controlReleased) return
        controlReleaseInFlight = true
        resetTrainer()
    }

    /**
     * First structured target after FreeRide is held until control ownership is available.
     */
    private fun exitFreeRideModeWithTarget(
        targetWatts: Int,
        ftmsReady: Boolean,
        controlGranted: Boolean,
    ) {
        freeRideModeActive = false
        pendingTargetAfterFreeRide = targetWatts
        maybeResumeStructuredControlAfterFreeRide(
            ftmsReady = ftmsReady,
            controlGranted = controlGranted,
        )
    }

    /**
     * Reacquires control when needed and applies the first post-FreeRide ERG target once.
     */
    private fun maybeResumeStructuredControlAfterFreeRide(
        ftmsReady: Boolean,
        controlGranted: Boolean,
    ) {
        val target = pendingTargetAfterFreeRide ?: return
        if (!ftmsReady) return
        if (controlReleaseInFlight) return

        if (!controlGranted) {
            if (!controlReacquireRequested) {
                controlReacquireRequested = true
                requestControl()
            }
            return
        }

        pendingTargetAfterFreeRide = null
        controlReacquireRequested = false
        controlReleaseInFlight = false
        controlReleased = false
        setTargetPower(target)
    }
}
