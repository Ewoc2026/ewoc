package com.example.ergometerapp

/**
 * Accepts exactly one rider signal per visible probe instance so live validation keeps one
 * decisive answer per prompt instead of mixing pre- and post-transition taps together.
 */
internal fun canAcceptSessionDebugProbeSignal(
    probeVisible: Boolean,
    priorSignalCount: Int,
): Boolean = probeVisible && priorSignalCount == 0

/**
 * Names the first arm-path condition that still blocks auto-show so live verification can
 * distinguish command-delivery failures from normal wait states such as "session not back yet".
 */
internal enum class SessionDebugProbeAutoShowBlocker(val wireName: String) {
    NOT_ARMED("not_armed"),
    SCREEN_NOT_SESSION("screen_not_session"),
    FTMS_NOT_READY("ftms_not_ready"),
    NONE("none"),
}

/**
 * Captures whether an armed probe could surface right now and, when it cannot, which invariant
 * is still missing from the current runtime snapshot.
 */
internal data class SessionDebugProbeAutoShowGate(
    val ready: Boolean,
    val blocker: SessionDebugProbeAutoShowBlocker,
)

internal fun evaluateSessionDebugProbeAutoShowGate(
    probeArmed: Boolean,
    screen: AppScreen,
    ftmsReady: Boolean,
): SessionDebugProbeAutoShowGate {
    if (!probeArmed) {
        return SessionDebugProbeAutoShowGate(
            ready = false,
            blocker = SessionDebugProbeAutoShowBlocker.NOT_ARMED,
        )
    }
    if (screen != AppScreen.SESSION) {
        return SessionDebugProbeAutoShowGate(
            ready = false,
            blocker = SessionDebugProbeAutoShowBlocker.SCREEN_NOT_SESSION,
        )
    }
    if (!ftmsReady) {
        return SessionDebugProbeAutoShowGate(
            ready = false,
            blocker = SessionDebugProbeAutoShowBlocker.FTMS_NOT_READY,
        )
    }
    return SessionDebugProbeAutoShowGate(
        ready = true,
        blocker = SessionDebugProbeAutoShowBlocker.NONE,
    )
}

/**
 * Preserves an active probe only for the internal continuation handoff that intentionally routes
 * through MENU as an implementation detail; ordinary user-facing menu returns should still clear
 * probe state so one validation prompt does not leak into unrelated navigation.
 */
internal fun shouldPreserveSessionDebugProbeAcrossInternalMenuReturn(
    preserveRequested: Boolean,
    postWorkoutContinuationHandoffVisible: Boolean,
    probeVisible: Boolean,
    probeArmed: Boolean,
): Boolean {
    return preserveRequested &&
        postWorkoutContinuationHandoffVisible &&
        (probeVisible || probeArmed)
}
