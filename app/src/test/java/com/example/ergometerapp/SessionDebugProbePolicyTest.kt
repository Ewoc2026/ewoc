package com.example.ergometerapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDebugProbePolicyTest {

    @Test
    fun acceptsFirstSignalWhileProbeVisible() {
        assertTrue(
            canAcceptSessionDebugProbeSignal(
                probeVisible = true,
                priorSignalCount = 0,
            )
        )
    }

    @Test
    fun rejectsFollowUpSignalAfterFirstAnswer() {
        assertFalse(
            canAcceptSessionDebugProbeSignal(
                probeVisible = true,
                priorSignalCount = 1,
            )
        )
    }

    @Test
    fun rejectsSignalWhenProbeIsHidden() {
        assertFalse(
            canAcceptSessionDebugProbeSignal(
                probeVisible = false,
                priorSignalCount = 0,
            )
        )
    }

    @Test
    fun autoShowGateReportsNotArmedBeforeAnyProbeIsQueued() {
        val gate = evaluateSessionDebugProbeAutoShowGate(
            probeArmed = false,
            screen = AppScreen.SESSION,
            ftmsReady = true,
        )

        assertFalse(gate.ready)
        assertTrue(gate.blocker == SessionDebugProbeAutoShowBlocker.NOT_ARMED)
    }

    @Test
    fun autoShowGateTurnsReadyBeforeFirstBikePacketOnceSessionAndFtmsAreReady() {
        val gate = evaluateSessionDebugProbeAutoShowGate(
            probeArmed = true,
            screen = AppScreen.SESSION,
            ftmsReady = true,
        )

        assertTrue(gate.ready)
        assertTrue(gate.blocker == SessionDebugProbeAutoShowBlocker.NONE)
    }

    @Test
    fun autoShowGateTurnsReadyEvenWhenTelemetryStaysAtZero() {
        val gate = evaluateSessionDebugProbeAutoShowGate(
            probeArmed = true,
            screen = AppScreen.SESSION,
            ftmsReady = true,
        )

        assertTrue(gate.ready)
        assertTrue(gate.blocker == SessionDebugProbeAutoShowBlocker.NONE)
    }

    @Test
    fun autoShowGateStaysReadyWhenLiveTelemetryAlreadyExists() {
        val gate = evaluateSessionDebugProbeAutoShowGate(
            probeArmed = true,
            screen = AppScreen.SESSION,
            ftmsReady = true,
        )

        assertTrue(gate.ready)
        assertTrue(gate.blocker == SessionDebugProbeAutoShowBlocker.NONE)
    }

    @Test
    fun internalContinuationMenuReturnPreservesVisibleProbe() {
        assertTrue(
            shouldPreserveSessionDebugProbeAcrossInternalMenuReturn(
                preserveRequested = true,
                postWorkoutContinuationHandoffVisible = true,
                probeVisible = true,
                probeArmed = false,
            )
        )
    }

    @Test
    fun ordinaryMenuReturnStillClearsProbeState() {
        assertFalse(
            shouldPreserveSessionDebugProbeAcrossInternalMenuReturn(
                preserveRequested = false,
                postWorkoutContinuationHandoffVisible = true,
                probeVisible = true,
                probeArmed = true,
            )
        )
        assertFalse(
            shouldPreserveSessionDebugProbeAcrossInternalMenuReturn(
                preserveRequested = true,
                postWorkoutContinuationHandoffVisible = false,
                probeVisible = true,
                probeArmed = true,
            )
        )
    }
}
