package io.github.ewoc2026.ewoc.session.release

/**
 * Names one concrete trainer-family release profile and the policy it applies.
 *
 * Keeping the profile explicit avoids presenting one trainer-proven tuning set as if it were a
 * universal FTMS truth.
 */
internal data class TrainerReleaseProfile(
    val profileId: String,
    val releasePolicy: TrainerReleasePolicy,
)

/**
 * Current concrete trainer-release profiles known to the app.
 *
 * The app still uses one Tunturi-proven default today, but the profile seam makes that assumption
 * visible so later trainer families can diverge without rewriting the decision flow first.
 *
 * `requestedRamp` already acts as the trainer-specific slot for the app-requested low-load floor,
 * even though current evidence still supports one conservative shared value. Future trainers can
 * diverge there without forcing the rest of the release policy to split at the same time.
 */
internal object TrainerReleaseProfiles {
    val tunturiBaseline: TrainerReleaseProfile = TrainerReleaseProfile(
        profileId = "tunturi_baseline",
        releasePolicy = TrainerReleasePolicy(
            safety = GeneralSafetyReleasePolicy(
                lowRiskCadenceThresholdRpm = 5,
                nearInstantDeltaThresholdW = 20,
                minRampDurationMs = 250L,
                maxRampDurationMs = 5_000L,
                preferRampEvenAtLowCadence = false,
            ),
            requestedRamp = RequestedReleaseRampPolicy(
                rampFloorPowerW = 25,
                rampDurationReferenceFloorPowerW = 60,
                rampFloorHoldMs = 500L,
                rampTickMs = 250L,
            ),
            controlRelease = TrainerControlReleaseSemantics(
                authorityClearsOnDisconnectOnly = true,
            ),
        ),
    )
}
