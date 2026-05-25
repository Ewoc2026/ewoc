package com.example.ergometerapp.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CompactWidthBreakpoint = 600.dp
private val ExpandedWidthBreakpoint = 840.dp
private val CompactHeightBreakpoint = 480.dp
private val ExpandedHeightBreakpoint = 900.dp

internal enum class AdaptiveWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal enum class AdaptiveHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/**
 * Shared adaptive layout contract for the app's primary screens.
 *
 * Invariant: layout mode is derived from the current window size only, so orientation
 * and device type are never used as direct branching inputs.
 */
internal enum class AdaptiveLayoutMode {
    SINGLE_PANE,
    SINGLE_PANE_DENSE,
    TWO_PANE_MEDIUM,
    TWO_PANE_EXPANDED,
}

internal data class AdaptivePaneWeights(
    val left: Float,
    val right: Float,
)

internal fun resolveAdaptiveWidthClass(width: Dp): AdaptiveWidthClass {
    return when {
        width < CompactWidthBreakpoint -> AdaptiveWidthClass.COMPACT
        width < ExpandedWidthBreakpoint -> AdaptiveWidthClass.MEDIUM
        else -> AdaptiveWidthClass.EXPANDED
    }
}

internal fun resolveAdaptiveHeightClass(height: Dp): AdaptiveHeightClass {
    return when {
        height < CompactHeightBreakpoint -> AdaptiveHeightClass.COMPACT
        height < ExpandedHeightBreakpoint -> AdaptiveHeightClass.MEDIUM
        else -> AdaptiveHeightClass.EXPANDED
    }
}

/**
 * Resolves the app-level layout mode from window bounds.
 *
 * Rules:
 * 1) Compact width is always single-pane.
 * 2) Compact height forces dense single-pane to avoid cramped split layouts.
 * 3) Medium/expanded width with enough height uses two-pane layouts.
 */
internal fun resolveAdaptiveLayoutMode(
    width: Dp,
    height: Dp,
): AdaptiveLayoutMode {
    val widthClass = resolveAdaptiveWidthClass(width)
    val heightClass = resolveAdaptiveHeightClass(height)
    return when {
        widthClass == AdaptiveWidthClass.COMPACT -> AdaptiveLayoutMode.SINGLE_PANE
        heightClass == AdaptiveHeightClass.COMPACT -> AdaptiveLayoutMode.SINGLE_PANE_DENSE
        widthClass == AdaptiveWidthClass.MEDIUM -> AdaptiveLayoutMode.TWO_PANE_MEDIUM
        else -> AdaptiveLayoutMode.TWO_PANE_EXPANDED
    }
}

internal fun AdaptiveLayoutMode.isTwoPane(): Boolean {
    return this == AdaptiveLayoutMode.TWO_PANE_MEDIUM || this == AdaptiveLayoutMode.TWO_PANE_EXPANDED
}

internal fun AdaptiveLayoutMode.paneWeights(): AdaptivePaneWeights {
    return when (this) {
        AdaptiveLayoutMode.TWO_PANE_MEDIUM -> AdaptivePaneWeights(left = 0.46f, right = 0.54f)
        AdaptiveLayoutMode.TWO_PANE_EXPANDED -> AdaptivePaneWeights(left = 0.38f, right = 0.62f)
        AdaptiveLayoutMode.SINGLE_PANE,
        AdaptiveLayoutMode.SINGLE_PANE_DENSE,
        -> AdaptivePaneWeights(left = 1f, right = 1f)
    }
}

/**
 * Resolves adaptive layout mode while keeping two-pane layout stable during IME transitions.
 *
 * On landscape devices, IME open can temporarily reduce height enough to trigger
 * `SINGLE_PANE_DENSE`. That re-structures the composition and often drops focus,
 * causing keyboard flicker. This helper preserves the latest two-pane mode while
 * IME is visible.
 */
@Composable
internal fun rememberImeStableAdaptiveLayoutMode(
    width: Dp,
    height: Dp,
): AdaptiveLayoutMode {
    val rawMode = resolveAdaptiveLayoutMode(width = width, height = height)
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var lastTwoPaneMode by remember { mutableStateOf<AdaptiveLayoutMode?>(null) }
    if (rawMode.isTwoPane()) {
        lastTwoPaneMode = rawMode
    }
    return if (
        imeVisible &&
        rawMode == AdaptiveLayoutMode.SINGLE_PANE_DENSE &&
        lastTwoPaneMode != null
    ) {
        lastTwoPaneMode!!
    } else {
        rawMode
    }
}
