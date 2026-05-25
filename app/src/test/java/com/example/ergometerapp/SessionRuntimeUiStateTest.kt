package com.example.ergometerapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRuntimeUiStateTest {

    @Test
    fun appUiStateForwardersReuseSharedSessionRuntimeOwner() {
        val uiState = AppUiState()
        val runtimeState = uiState.sessionRuntimeUiState

        assertSame(runtimeState.ftmsReady, uiState.ftmsReady)
        assertSame(runtimeState.ftmsControlGranted, uiState.ftmsControlGranted)
        assertSame(runtimeState.lastTargetPower, uiState.lastTargetPower)
        assertSame(runtimeState.trainerControlAuthority, uiState.trainerControlAuthority)
        assertSame(runtimeState.lastAppControlledTargetPower, uiState.lastAppControlledTargetPower)
        assertSame(runtimeState.workoutReady, uiState.workoutReady)
        assertSame(runtimeState.stopFlowState, uiState.stopFlowState)

        uiState.pendingSessionStartAfterPermission = true
        uiState.pendingCadenceStartAfterControlGranted = true
        uiState.autoPausedByZeroCadence = true

        assertTrue(runtimeState.pendingSessionStartAfterPermission)
        assertTrue(runtimeState.pendingCadenceStartAfterControlGranted)
        assertTrue(runtimeState.autoPausedByZeroCadence)

        runtimeState.pendingSessionStartAfterPermission = false
        runtimeState.pendingCadenceStartAfterControlGranted = false
        runtimeState.autoPausedByZeroCadence = false

        assertFalse(uiState.pendingSessionStartAfterPermission)
        assertFalse(uiState.pendingCadenceStartAfterControlGranted)
        assertFalse(uiState.autoPausedByZeroCadence)
    }
}
