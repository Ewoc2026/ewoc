package io.github.ewoc2026.ewoc.session.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseRampDeciderTest {

    private val traceEvents = mutableListOf<ReleaseRampTraceEvent>()
    private val decider = ReleaseRampDecider(
        policy = TrainerReleaseProfiles.tunturiBaseline.releasePolicy,
        traceEmitter = ReleaseRampTraceEmitter { event -> traceEvents += event },
    )

    @Test
    fun returnsNoNeedWhenDisconnectIsNotRequired() {
        traceEvents.clear()
        val decision = decider.decide(
            context = baseContext(
                disconnectRequired = false,
            ),
        )

        assertEquals(ReleaseRampDecision.NoNeed, decision)
        assertEquals(2, traceEvents.size)
    }

    @Test
    fun returnsNoNeedWhenCadenceIsAtOrBelowLowRiskThreshold() {
        traceEvents.clear()
        val decision = decider.decide(
            context = baseContext(
                cadenceRpm = 5,
            ),
        )

        assertEquals(ReleaseRampDecision.NoNeed, decision)
    }

    @Test
    fun returnsNotPossibleWhenRiderControlsLoadAndCadenceIsDangerous() {
        traceEvents.clear()
        val decision = decider.decide(
            context = baseContext(
                authority = TrainerControlAuthority.RIDER_CONTROLLED,
                cadenceRpm = 85,
            ),
        )

        assertEquals(
            ReleaseRampDecision.NotPossible(
                reason = ReleaseRampDecision.NotPossible.Reason.RIDER_CONTROLS_LOAD,
            ),
            decision,
        )
    }

    @Test
    fun returnsExecuteForAppControlledHighCadenceDisconnect() {
        traceEvents.clear()
        val decision = decider.decide(
            context = baseContext(
                cadenceRpm = 85,
                knownAppTargetPowerW = 180,
            ),
        )

        assertTrue(decision is ReleaseRampDecision.Execute)
        val execute = decision as ReleaseRampDecision.Execute
        assertEquals(180, execute.plan.startTargetPowerW)
        assertEquals(25, execute.plan.endTargetPowerW)
        assertEquals(3_000L, execute.plan.durationMs)
        assertEquals(500L, execute.plan.floorHoldMs)
        assertEquals(250L, execute.plan.tickMs)
    }

    @Test
    fun emitsStructuredTraceForExecuteDecision() {
        traceEvents.clear()

        decider.decide(
            context = baseContext(
                cadenceRpm = 85,
                knownAppTargetPowerW = 180,
            ),
        )

        assertEquals(2, traceEvents.size)
        val evaluate = traceEvents[0] as ReleaseRampTraceEvent.Evaluate
        assertEquals(ReleaseIntent.CONTINUE_RIDE_HANDOFF, evaluate.intent)
        assertEquals(TrainerControlAuthority.APP_CONTROLLED, evaluate.authority)
        assertTrue(evaluate.cadenceNeedsRamp)

        val decision = traceEvents[1] as ReleaseRampTraceEvent.Decision
        assertEquals(ReleaseRampTraceEvent.Outcome.EXECUTE, decision.outcome)
        assertEquals(180, decision.plan?.startTargetPowerW)
        assertEquals(25, decision.plan?.endTargetPowerW)
    }

    @Test
    fun emitsStructuredTraceForNotPossibleDecision() {
        traceEvents.clear()

        decider.decide(
            context = baseContext(
                authority = TrainerControlAuthority.RIDER_CONTROLLED,
                cadenceRpm = 85,
            ),
        )

        assertEquals(2, traceEvents.size)
        val decision = traceEvents[1] as ReleaseRampTraceEvent.Decision
        assertEquals(ReleaseRampTraceEvent.Outcome.NOT_POSSIBLE, decision.outcome)
        assertEquals(
            ReleaseRampDecision.NotPossible.Reason.RIDER_CONTROLS_LOAD,
            decision.notPossibleReason,
        )
    }

    private fun baseContext(
        intent: ReleaseIntent = ReleaseIntent.CONTINUE_RIDE_HANDOFF,
        authority: TrainerControlAuthority = TrainerControlAuthority.APP_CONTROLLED,
        disconnectRequired: Boolean = true,
        ftmsReady: Boolean = true,
        ftmsControlGranted: Boolean = true,
        cadenceRpm: Int? = 85,
        instantaneousPowerW: Int? = 180,
        knownAppTargetPowerW: Int? = 180,
    ): ReleaseContext {
        return ReleaseContext(
            intent = intent,
            authority = authority,
            disconnectRequired = disconnectRequired,
            ftmsReady = ftmsReady,
            ftmsControlGranted = ftmsControlGranted,
            cadenceRpm = cadenceRpm,
            instantaneousPowerW = instantaneousPowerW,
            knownAppTargetPowerW = knownAppTargetPowerW,
        )
    }
}
