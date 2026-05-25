package com.ewo.editor.desktop.theme

import androidx.compose.ui.graphics.Color

/**
 * Segment-type colors used consistently across the tree panel, chart, and
 * property inspector. Based on standard cycling training-zone conventions.
 */
object SegmentColors {
    val steady = Color(0xFF42A5F5)       // blue — constant effort
    val ramp = Color(0xFFAB47BC)         // purple — progressive
    val freeRide = Color(0xFF66BB6A)     // green — unstructured
    val repeat = Color(0xFFFF7043)       // deep orange — structural
    val hr = Color(0xFFEF5350)           // red — HR-driven
}

/**
 * FTP-relative power zone colors following a 7-zone model.
 * Used in the workout profile chart for power-based bars.
 */
object ZoneColors {
    val recovery = Color(0xFF78909C)     // Z1: < 55% FTP
    val endurance = Color(0xFF42A5F5)    // Z2: 55–75%
    val tempo = Color(0xFF66BB6A)        // Z3: 76–90%
    val threshold = Color(0xFFFFCA28)    // Z4: 91–105%
    val vo2max = Color(0xFFFF7043)       // Z5: 106–120%
    val anaerobic = Color(0xFFEF5350)    // Z6: 121–150%
    val neuromuscular = Color(0xFFAB47BC) // Z7: > 150%

    /** Returns the zone color for [watts] given an optional [ftpWatts]. */
    fun forPower(watts: Int, ftpWatts: Int?): Color {
        if (ftpWatts == null || ftpWatts <= 0) return endurance
        val pct = watts.toDouble() / ftpWatts * 100
        return when {
            pct < 55 -> recovery
            pct < 76 -> endurance
            pct < 91 -> tempo
            pct < 106 -> threshold
            pct < 121 -> vo2max
            pct < 151 -> anaerobic
            else -> neuromuscular
        }
    }
}
