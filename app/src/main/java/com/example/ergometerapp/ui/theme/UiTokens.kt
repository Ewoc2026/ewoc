package com.example.ergometerapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing tokens for destination-level layouts.
 *
 * Centralizing these values keeps spacing rhythm consistent while UI screens
 * are iteratively refactored across multiple commits.
 */
internal object UiSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
}

/**
 * Semantic status colors that should stay stable across light/dark palettes.
 *
 * These are intentionally mapped to setup-readiness semantics instead of
 * one-off component styling so riders can recognize status quickly.
 */
internal object UiSemanticColor {
    val setupPending: Color = Color(0xFFFFB300)
    val setupReady: Color = Color(0xFF4CAF50)
}
