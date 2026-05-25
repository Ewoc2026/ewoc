package com.example.ergometerapp.ui

import com.example.ergometerapp.AppScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityContentTest {

    @Test
    fun globalSessionDebugProbeRendersOutsideSessionWhenVisible() {
        assertTrue(
            shouldRenderGlobalSessionDebugProbe(
                screen = AppScreen.MENU,
                sessionDebugProbeVisible = true,
            )
        )
    }

    @Test
    fun globalSessionDebugProbeSkipsSessionScreenToAvoidDuplicateOverlay() {
        assertFalse(
            shouldRenderGlobalSessionDebugProbe(
                screen = AppScreen.SESSION,
                sessionDebugProbeVisible = true,
            )
        )
    }

    @Test
    fun globalSessionDebugProbeSkipsHiddenProbeState() {
        assertFalse(
            shouldRenderGlobalSessionDebugProbe(
                screen = AppScreen.MENU,
                sessionDebugProbeVisible = false,
            )
        )
    }

    @Test
    fun sessionDebugProbeLocksButtonsAfterFirstReceipt() {
        assertTrue(isSessionDebugProbeSignalLocked("Received: Ready #1"))
    }

    @Test
    fun sessionDebugProbeKeepsButtonsEnabledBeforeAnyReceipt() {
        assertFalse(isSessionDebugProbeSignalLocked(null))
    }
}
