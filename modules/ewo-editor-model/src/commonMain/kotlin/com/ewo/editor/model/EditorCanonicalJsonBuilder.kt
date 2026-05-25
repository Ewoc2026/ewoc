package com.ewo.editor.model

/**
 * Builds canonical `.ewo` JSON from an [EditorWorkoutDocument].
 *
 * This is a minimal serializer for preview and save operations.
 * It produces well-formed JSON that can be parsed back by `EwoEngine.parse()`.
 */
fun buildCanonicalJson(doc: EditorWorkoutDocument): String {
    val sb = StringBuilder()
    sb.appendLine("{")
    sb.appendLine("""  "format": "ewo",""")
    sb.appendLine("""  "version": "${doc.version}",""")
    if (doc.uid != null) {
        sb.appendLine("""  "uid": "${escapeJson(doc.uid)}",""")
    }
    if (doc.revision != null) {
        sb.appendLine("""  "revision": ${doc.revision},""")
    }
    sb.appendLine("""  "title": "${escapeJson(doc.title)}",""")
    if (doc.description.isNotEmpty()) {
        sb.appendLine("""  "description": "${escapeJson(doc.description)}",""")
    }
    if (doc.titleLocalized != null) {
        sb.appendLine("""  "title_localized": ${localizedTextToJson(doc.titleLocalized.defaultText, doc.titleLocalized.translations)},""")
    }
    if (doc.descriptionLocalized != null) {
        sb.appendLine("""  "description_localized": ${localizedTextToJson(doc.descriptionLocalized.defaultText, doc.descriptionLocalized.translations)},""")
    }
    if (doc.difficulty != null) {
        sb.appendLine("""  "difficulty": "${doc.difficulty}",""")
    }
    if (doc.tags.isNotEmpty()) {
        sb.appendLine("""  "tags": [${doc.tags.joinToString(", ") { "\"${escapeJson(it)}\"" }}],""")
    }
    if (doc.control != null) {
        sb.appendLine(controlToJson(doc.control))
    }
    appendMessages(sb, doc.messages, pad = "  ")
    val exportableSegments = filterExportableSegments(doc.segments)
    sb.appendLine("""  "segments": [""")
    exportableSegments.forEachIndexed { i, seg ->
        sb.append(segmentToJson(seg, indent = 4))
        if (i < exportableSegments.size - 1) sb.appendLine(",") else sb.appendLine()
    }
    sb.appendLine("  ]")
    sb.appendLine("}")
    return sb.toString()
}

private fun segmentToJson(seg: EditorSegment, indent: Int): String {
    val pad = " ".repeat(indent)
    val sb = StringBuilder()
    sb.appendLine("${pad}{")
    sb.appendLine("""$pad  "id": "${escapeJson(seg.segmentId)}",""")
    val label = seg.label
    if (label != null) {
        sb.appendLine("""$pad  "label": "${escapeJson(label)}",""")
    }
    val note = seg.note
    if (note != null) {
        sb.appendLine("""$pad  "note": "${escapeJson(note)}",""")
    }
    when (seg) {
        is EditorSegment.Steady -> {
            sb.appendLine("""$pad  "type": "steady",""")
            sb.appendLine("""$pad  "duration_sec": ${seg.durationSec},""")
            val target = seg.target
            if (target != null) {
                sb.append(targetToJson(target, pad))
            }
            val cadence = seg.cadence
            if (cadence != null) {
                sb.appendLine("""$pad  "cadence": { "low": ${cadence.low}, "high": ${cadence.high} },""")
            }
            appendMessages(sb, seg.messages, pad)
        }
        is EditorSegment.Ramp -> {
            sb.appendLine("""$pad  "type": "ramp",""")
            sb.appendLine("""$pad  "duration_sec": ${seg.durationSec},""")
            val from = seg.fromTarget
            val to = seg.toTarget
            if (from != null) sb.append(targetToJson(from, pad, prefix = "from_"))
            if (to != null) sb.append(targetToJson(to, pad, prefix = "to_"))
            val cadence = seg.cadence
            if (cadence != null) {
                sb.appendLine("""$pad  "cadence": { "low": ${cadence.low}, "high": ${cadence.high} },""")
            }
            appendMessages(sb, seg.messages, pad)
        }
        is EditorSegment.FreeRide -> {
            sb.appendLine("""$pad  "type": "free_ride",""")
            sb.appendLine("""$pad  "duration_sec": ${seg.durationSec},""")
            val cadence = seg.cadence
            if (cadence != null) {
                sb.appendLine("""$pad  "cadence": { "low": ${cadence.low}, "high": ${cadence.high} },""")
            }
            appendMessages(sb, seg.messages, pad)
        }
        is EditorSegment.Repeat -> {
            sb.appendLine("""$pad  "type": "repeat",""")
            sb.appendLine("""$pad  "count": ${seg.count},""")
            appendMessages(sb, seg.messages, pad)
            sb.appendLine("""$pad  "segments": [""")
            seg.segments.forEachIndexed { i, child ->
                sb.append(segmentToJson(child, indent + 4))
                if (i < seg.segments.size - 1) sb.appendLine(",") else sb.appendLine()
            }
            sb.appendLine("$pad  ]")
        }
    }
    // Remove trailing comma from last field before closing brace
    val result = sb.toString().trimEnd()
    val cleaned = if (result.endsWith(",")) result.dropLast(1) else result
    return "$cleaned\n$pad}"
}

private fun controlToJson(control: EditorControl): String {
    return """
  "control": {
    "initial_power_watts": ${control.initialPowerWatts},
    "min_power_watts": ${control.minPowerWatts},
    "max_power_watts": ${control.maxPowerWatts},
    "signal_loss_power_watts": ${control.signalLossPowerWatts},
    "hr_upper_cap_bpm": ${control.hrUpperCapBpm}
  },
""".trimIndent()
}

private fun targetToJson(target: EditorTarget, pad: String, prefix: String = ""): String {
    return when (target) {
        is EditorTarget.Power -> """$pad  "${prefix}target": { "metric": "power", "value": ${target.watts} },""" + "\n"
        is EditorTarget.FtpPercent -> """$pad  "${prefix}target": { "metric": "ftp_percent", "value": ${target.fraction} },""" + "\n"
        is EditorTarget.HeartRate -> """$pad  "${prefix}target": { "metric": "heart_rate", "range": { "low": ${target.lowBpm}, "high": ${target.highBpm} } },""" + "\n"
        is EditorTarget.HeartRateRelative -> """$pad  "${prefix}target": { "metric": "heart_rate_relative", "reference": "${target.reference.stableCode}", "range": { "low": ${target.lowFraction}, "high": ${target.highFraction} } },""" + "\n"
    }
}

private fun appendMessages(sb: StringBuilder, messages: List<EditorMessage>, pad: String) {
    if (messages.isNotEmpty()) {
        sb.appendLine("""$pad  "messages": [""")
        messages.forEachIndexed { i, msg ->
            sb.append("$pad    { ")
            sb.append(""""kind": "${escapeJson(msg.kind)}", """)
            sb.append(""""when": ${timingToJson(msg.timing)}, """)
            sb.append(""""text": ${localizedTextToJson(msg.defaultText, msg.translations)} }""")
            if (i < messages.size - 1) sb.appendLine(",") else sb.appendLine()
        }
        sb.appendLine("$pad  ],")
    }
}

private fun timingToJson(timing: EditorMessageTiming): String {
    return if (timing.anchor == EditorMessageAnchor.START && timing.offsetSec == 0) {
        "\"start\""
    } else {
        """{ "anchor": "${timing.anchor.name.lowercase()}", "offset_sec": ${timing.offsetSec} }"""
    }
}

private fun localizedTextToJson(defaultText: String, translations: Map<String, String>): String {
    return buildString {
        append("""{ "default": "${escapeJson(defaultText)}"""")
        if (translations.isNotEmpty()) {
            append(""", "translations": { """)
            append(
                translations.entries.joinToString(", ") { (locale, text) ->
                    """"${escapeJson(locale)}": "${escapeJson(text)}""""
                },
            )
            append(" }")
        }
        append(" }")
    }
}

/**
 * Filters out segments that cannot be represented in valid `.ewo` format.
 *
 * Repeat segments require at least two children; incomplete repeats are dropped
 * so the remaining document can round-trip through the parser.
 */
private fun filterExportableSegments(segments: List<EditorSegment>): List<EditorSegment> =
    segments.mapNotNull { seg ->
        when (seg) {
            is EditorSegment.Repeat -> {
                val validChildren = filterExportableSegments(seg.segments)
                if (validChildren.size >= 2) seg.copy(segments = validChildren) else null
            }
            else -> seg
        }
    }

private fun escapeJson(s: String): String = s
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
