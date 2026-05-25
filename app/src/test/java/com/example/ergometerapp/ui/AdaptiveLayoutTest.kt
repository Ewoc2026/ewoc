package com.example.ergometerapp.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun resolvesSinglePaneForCompactWidth() {
        val mode = resolveAdaptiveLayoutMode(
            width = 599.dp,
            height = 1000.dp,
        )

        assertEquals(AdaptiveLayoutMode.SINGLE_PANE, mode)
    }

    @Test
    fun resolvesDenseSinglePaneForCompactHeightEvenWhenWidthIsLarge() {
        val mode = resolveAdaptiveLayoutMode(
            width = 900.dp,
            height = 400.dp,
        )

        assertEquals(AdaptiveLayoutMode.SINGLE_PANE_DENSE, mode)
    }

    @Test
    fun resolvesTwoPaneMediumForMediumWindow() {
        val mode = resolveAdaptiveLayoutMode(
            width = 700.dp,
            height = 700.dp,
        )

        assertEquals(AdaptiveLayoutMode.TWO_PANE_MEDIUM, mode)
    }

    @Test
    fun resolvesTwoPaneExpandedForExpandedWindow() {
        val mode = resolveAdaptiveLayoutMode(
            width = 900.dp,
            height = 900.dp,
        )

        assertEquals(AdaptiveLayoutMode.TWO_PANE_EXPANDED, mode)
    }

    @Test
    fun resolvesWidthClassBreakpoints() {
        assertEquals(AdaptiveWidthClass.COMPACT, resolveAdaptiveWidthClass(599.dp))
        assertEquals(AdaptiveWidthClass.MEDIUM, resolveAdaptiveWidthClass(600.dp))
        assertEquals(AdaptiveWidthClass.EXPANDED, resolveAdaptiveWidthClass(840.dp))
    }

    @Test
    fun resolvesHeightClassBreakpoints() {
        assertEquals(AdaptiveHeightClass.COMPACT, resolveAdaptiveHeightClass(479.dp))
        assertEquals(AdaptiveHeightClass.MEDIUM, resolveAdaptiveHeightClass(480.dp))
        assertEquals(AdaptiveHeightClass.EXPANDED, resolveAdaptiveHeightClass(900.dp))
    }

    @Test
    fun returnsExpectedPaneWeights() {
        assertEquals(
            AdaptivePaneWeights(left = 0.46f, right = 0.54f),
            AdaptiveLayoutMode.TWO_PANE_MEDIUM.paneWeights(),
        )
        assertEquals(
            AdaptivePaneWeights(left = 0.38f, right = 0.62f),
            AdaptiveLayoutMode.TWO_PANE_EXPANDED.paneWeights(),
        )
    }
}
