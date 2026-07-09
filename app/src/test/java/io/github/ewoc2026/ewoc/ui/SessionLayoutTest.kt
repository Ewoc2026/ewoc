package io.github.ewoc2026.ewoc.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLayoutTest {

    @Test
    fun sideBySideTabletSplitUsesDenseTopRail() {
        assertTrue(useDenseSessionTopRail(width = 476.dp, height = 552.dp))
    }

    @Test
    fun regularPhonePortraitKeepsFullTopRail() {
        assertFalse(useDenseSessionTopRail(width = 412.dp, height = 800.dp))
    }

    @Test
    fun landscapeIsHandledByItsExistingDenseLayoutRule() {
        assertFalse(useDenseSessionTopRail(width = 700.dp, height = 400.dp))
    }
}
