package io.github.ewoc2026.ewoc.session.release

/**
 * Decides whether disconnect-sensitive release should run an app-driven safety ramp first.
 *
 * The decision is intentionally policy-driven so trainer-specific release behavior can evolve
 * without pushing more branching logic into `SessionOrchestrator`.
 */
internal class ReleaseRampDecider(
    private val policy: TrainerReleasePolicy,
    private val traceEmitter: ReleaseRampTraceEmitter = ReleaseRampTraceEmitter { },
) {
    fun decide(context: ReleaseContext): ReleaseRampDecision {
        val cadenceRpm = context.cadenceRpm ?: 0
        val cadenceNeedsRamp = cadenceRpm > policy.safety.lowRiskCadenceThresholdRpm
        traceEmitter.emit(
            ReleaseRampTraceEvent.Evaluate(
                intent = context.intent,
                authority = context.authority,
                disconnectRequired = context.disconnectRequired,
                ftmsReady = context.ftmsReady,
                ftmsControlGranted = context.ftmsControlGranted,
                cadenceRpm = context.cadenceRpm,
                instantaneousPowerW = context.instantaneousPowerW,
                knownAppTargetPowerW = context.knownAppTargetPowerW,
                lowRiskCadenceThresholdRpm = policy.safety.lowRiskCadenceThresholdRpm,
                preferRampEvenAtLowCadence = policy.safety.preferRampEvenAtLowCadence,
                cadenceNeedsRamp = cadenceNeedsRamp,
            ),
        )

        if (!context.disconnectRequired) {
            return traceDecision(
                context = context,
                cadenceNeedsRamp = cadenceNeedsRamp,
                decision = ReleaseRampDecision.NoNeed,
            )
        }

        if (!cadenceNeedsRamp && !policy.safety.preferRampEvenAtLowCadence) {
            return traceDecision(
                context = context,
                cadenceNeedsRamp = cadenceNeedsRamp,
                decision = ReleaseRampDecision.NoNeed,
            )
        }

        return when (context.authority) {
            TrainerControlAuthority.RIDER_CONTROLLED -> {
                traceDecision(
                    context = context,
                    cadenceNeedsRamp = cadenceNeedsRamp,
                    decision = ReleaseRampDecision.NotPossible(
                        reason = ReleaseRampDecision.NotPossible.Reason.RIDER_CONTROLS_LOAD,
                    ),
                )
            }

            TrainerControlAuthority.APP_CONTROLLED -> {
                if (!context.ftmsReady) {
                    return traceDecision(
                        context = context,
                        cadenceNeedsRamp = cadenceNeedsRamp,
                        decision = ReleaseRampDecision.NotPossible(
                            reason = ReleaseRampDecision.NotPossible.Reason.FTMS_NOT_READY,
                        ),
                    )
                }
                if (!context.ftmsControlGranted) {
                    return traceDecision(
                        context = context,
                        cadenceNeedsRamp = cadenceNeedsRamp,
                        decision = ReleaseRampDecision.NotPossible(
                            reason = ReleaseRampDecision.NotPossible.Reason.CONTROL_NOT_GRANTED,
                        ),
                    )
                }

                val startTargetPowerW = context.knownAppTargetPowerW
                    ?: return traceDecision(
                        context = context,
                        cadenceNeedsRamp = cadenceNeedsRamp,
                        decision = ReleaseRampDecision.NotPossible(
                            reason = ReleaseRampDecision.NotPossible.Reason.UNKNOWN_APP_TARGET,
                        ),
                    )

                if (startTargetPowerW <= policy.requestedRamp.rampFloorPowerW) {
                    return traceDecision(
                        context = context,
                        cadenceNeedsRamp = cadenceNeedsRamp,
                        decision = ReleaseRampDecision.NoNeed,
                    )
                }

                return traceDecision(
                    context = context,
                    cadenceNeedsRamp = cadenceNeedsRamp,
                    decision = ReleaseRampDecision.Execute(
                        plan = createPlan(startTargetPowerW = startTargetPowerW),
                    ),
                )
            }
        }
    }

    private fun traceDecision(
        context: ReleaseContext,
        cadenceNeedsRamp: Boolean,
        decision: ReleaseRampDecision,
    ): ReleaseRampDecision {
        traceEmitter.emit(
            ReleaseRampTraceEvent.Decision(
                intent = context.intent,
                authority = context.authority,
                disconnectRequired = context.disconnectRequired,
                ftmsReady = context.ftmsReady,
                ftmsControlGranted = context.ftmsControlGranted,
                cadenceRpm = context.cadenceRpm,
                knownAppTargetPowerW = context.knownAppTargetPowerW,
                cadenceNeedsRamp = cadenceNeedsRamp,
                outcome = decision.traceOutcome(),
                notPossibleReason = (decision as? ReleaseRampDecision.NotPossible)?.reason,
                plan = (decision as? ReleaseRampDecision.Execute)?.plan,
            ),
        )
        return decision
    }

    private fun createPlan(startTargetPowerW: Int): ReleaseRampPlan {
        // Keep duration buckets anchored to the earlier Tunturi-proven unload profile so
        // changing the final floor target can be tested separately from ramp timing.
        val durationDeltaW = startTargetPowerW - policy.requestedRamp.rampDurationReferenceFloorPowerW
        val durationMs = when {
            durationDeltaW <= policy.safety.nearInstantDeltaThresholdW -> policy.safety.minRampDurationMs
            durationDeltaW <= 40 -> 500L
            durationDeltaW <= 80 -> 1_500L
            durationDeltaW <= 140 -> 3_000L
            else -> policy.safety.maxRampDurationMs
        }

        return ReleaseRampPlan(
            startTargetPowerW = startTargetPowerW,
            endTargetPowerW = policy.requestedRamp.rampFloorPowerW,
            durationMs = durationMs.coerceIn(
                minimumValue = policy.safety.minRampDurationMs,
                maximumValue = policy.safety.maxRampDurationMs,
            ),
            floorHoldMs = if (durationDeltaW > policy.safety.nearInstantDeltaThresholdW) {
                policy.requestedRamp.rampFloorHoldMs
            } else {
                0L
            },
            tickMs = policy.requestedRamp.rampTickMs,
        )
    }
}

private fun ReleaseRampDecision.traceOutcome(): ReleaseRampTraceEvent.Outcome {
    return when (this) {
        ReleaseRampDecision.NoNeed -> ReleaseRampTraceEvent.Outcome.NO_NEED
        is ReleaseRampDecision.NotPossible -> ReleaseRampTraceEvent.Outcome.NOT_POSSIBLE
        is ReleaseRampDecision.Execute -> ReleaseRampTraceEvent.Outcome.EXECUTE
    }
}
