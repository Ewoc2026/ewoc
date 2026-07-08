package io.github.ewoc2026.ewoc.session.release

/**
 * Why the app is trying to leave the current trainer-control state.
 */
internal enum class ReleaseIntent {
    NORMAL_END,
    CONTINUE_RIDE_HANDOFF,
    DEBUG_DISCONNECT_PROBE,
    RECOVERY_RESET,
}

/**
 * Who currently owns trainer resistance decisions.
 *
 * APP_CONTROLLED means the app has sent load commands and disconnect has not yet cleared
 * that ownership. RIDER_CONTROLLED means the app must not change load as part of release.
 */
internal enum class TrainerControlAuthority {
    APP_CONTROLLED,
    RIDER_CONTROLLED,
}

/**
 * Stable runtime facts used to decide whether a pre-disconnect safety ramp is needed.
 */
internal data class ReleaseContext(
    val intent: ReleaseIntent,
    val authority: TrainerControlAuthority,
    val disconnectRequired: Boolean,
    val ftmsReady: Boolean,
    val ftmsControlGranted: Boolean,
    val cadenceRpm: Int?,
    val instantaneousPowerW: Int?,
    val knownAppTargetPowerW: Int?,
)

/**
 * General rider-safety rules for disconnect-sensitive trainer release.
 *
 * These are centered on "are we still meaningfully pedaling?" and "how quickly may load change?"
 * rather than on one trainer family's quirks.
 */
internal data class GeneralSafetyReleasePolicy(
    val lowRiskCadenceThresholdRpm: Int = 5,
    val nearInstantDeltaThresholdW: Int = 20,
    val minRampDurationMs: Long = 250L,
    val maxRampDurationMs: Long = 5_000L,
    val preferRampEvenAtLowCadence: Boolean = false,
)

/**
 * App-requested low-load target and command cadence for the release ramp.
 *
 * The app asks for a conservative low-load floor before STOP / disconnect, while the trainer may
 * still clamp that request to its own device minimum internally.
 */
internal data class RequestedReleaseRampPolicy(
    val rampFloorPowerW: Int = 25,
    val rampDurationReferenceFloorPowerW: Int = 60,
    val rampFloorHoldMs: Long = 500L,
    val rampTickMs: Long = 250L,
)

/**
 * How the app currently believes trainer-owned control release works.
 *
 * Tunturi evidence suggests app-owned resistance may remain latched until disconnect even after the
 * rider would like manual control back, so this seam is intentionally separate from rider-safety
 * ramp shaping.
 */
internal data class TrainerControlReleaseSemantics(
    val authorityClearsOnDisconnectOnly: Boolean = true,
)

/**
 * Release-policy groups for one trainer-family profile.
 *
 * Keep the values profile-scoped instead of treating one trainer-proven tuning set as a universal
 * FTMS default.
 */
internal data class TrainerReleasePolicy(
    val safety: GeneralSafetyReleasePolicy = GeneralSafetyReleasePolicy(),
    val requestedRamp: RequestedReleaseRampPolicy = RequestedReleaseRampPolicy(),
    val controlRelease: TrainerControlReleaseSemantics = TrainerControlReleaseSemantics(),
) {
    val lowRiskCadenceThresholdRpm: Int
        get() = safety.lowRiskCadenceThresholdRpm

    val nearInstantDeltaThresholdW: Int
        get() = safety.nearInstantDeltaThresholdW

    val minRampDurationMs: Long
        get() = safety.minRampDurationMs

    val maxRampDurationMs: Long
        get() = safety.maxRampDurationMs

    val preferRampEvenAtLowCadence: Boolean
        get() = safety.preferRampEvenAtLowCadence

    val rampFloorPowerW: Int
        get() = requestedRamp.rampFloorPowerW

    val rampDurationReferenceFloorPowerW: Int
        get() = requestedRamp.rampDurationReferenceFloorPowerW

    val rampFloorHoldMs: Long
        get() = requestedRamp.rampFloorHoldMs

    val rampTickMs: Long
        get() = requestedRamp.rampTickMs

    val authorityClearsOnDisconnectOnly: Boolean
        get() = controlRelease.authorityClearsOnDisconnectOnly
}
