package com.ewo.editor.desktop

import com.ewo.editor.model.EditorSegment

/** Counts all segments in the tree, including children of repeats. */
fun countAllSegments(segments: List<EditorSegment>): Int {
    var count = 0
    for (seg in segments) {
        count++
        if (seg is EditorSegment.Repeat) count += countAllSegments(seg.segments)
    }
    return count
}

/** Computes raw total duration in seconds, expanding repeats by count. */
fun computeRawDuration(segments: List<EditorSegment>): Int {
    var total = 0
    for (seg in segments) {
        total += when (seg) {
            is EditorSegment.Steady -> seg.durationSec
            is EditorSegment.Ramp -> seg.durationSec
            is EditorSegment.FreeRide -> seg.durationSec
            is EditorSegment.Repeat -> computeRawDuration(seg.segments) * seg.count
        }
    }
    return total
}

/** Formats seconds as "H:MM:SS" or "M:SS". */
fun formatDurationCompact(totalSec: Int): String {
    val hours = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, min, sec)
        else -> "%d:%02d".format(min, sec)
    }
}

/** Formats seconds as "Xh Xm" or "Xm Xs" for human-readable display. */
fun formatDurationHuman(totalSec: Int): String {
    val hours = totalSec / 3600
    val min = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (hours > 0) "${hours}h ${min}m" else "${min}m ${sec}s"
}

/** Formats seconds as "Xm" or "XmYs" for tree row summaries. */
fun formatDurationShort(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return if (sec == 0) "${min}m" else "${min}m${sec}s"
}
