package io.github.ewoc2026.ewoc.session.release

/**
 * Decision returned before any disconnect-sensitive release action is executed.
 *
 * `NoNeed` means release can continue without an app-driven ramp.
 * `NotPossible` means a ramp would be preferred but the app must not or cannot execute it.
 * `Execute` means the caller should run the supplied ramp plan before teardown continues.
 */
internal sealed interface ReleaseRampDecision {
    data object NoNeed : ReleaseRampDecision

    data class NotPossible(
        val reason: Reason,
    ) : ReleaseRampDecision {
        internal enum class Reason {
            RIDER_CONTROLS_LOAD,
            UNKNOWN_APP_TARGET,
            FTMS_NOT_READY,
            CONTROL_NOT_GRANTED,
        }
    }

    data class Execute(
        val plan: ReleaseRampPlan,
    ) : ReleaseRampDecision
}
