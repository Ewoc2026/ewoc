package com.ewo.editor.model

import com.ewo.core.CompiledEwoWorkoutStep
import com.ewo.core.EwoCompileError
import com.ewo.core.EwoCompileContext
import com.ewo.core.EwoCadenceRange
import com.ewo.core.EwoEngine
import com.ewo.core.EwoMessage
import com.ewo.core.EwoMessageAnchor
import com.ewo.core.EwoWorkoutParseResult
import com.ewo.core.NormalizedEwoWorkout
import com.ewo.core.NormalizedEwoWorkoutRepeatSegment
import com.ewo.core.NormalizedEwoWorkoutSegment
import com.ewo.core.SanityCheckResult
import com.ewo.core.SanityIssue

/**
 * Creates [EditorWorkoutDocument] instances from parsed `.ewo` data or from scratch.
 */
object EditorDocumentFactory {

    /**
     * Creates an editor document from a successfully parsed `.ewo` file.
     *
     * Maps the normalized (pre-compilation) tree structure into editor segments
     * with stable node IDs. The compiled model is used only for validation markers.
     */
    fun fromParseResult(result: EwoWorkoutParseResult.Success): EditorWorkoutDocument {
        var counter = 1
        fun nextId(): EditorNodeId {
            val id = EditorNodeId("node_$counter")
            counter++
            return id
        }

        val rootNodeId = nextId()
        val normalized = result.normalized

        val editorMessages = normalized.messages.map { msg ->
            mapMessage(msg, nextId())
        }

        val editorSegments = normalized.segments.map { segment ->
            mapSegment(segment, ::nextId)
        }

        val sanityMarkers = mapSanityToMarkers(result.sanityResult, editorSegments)
        val compileErrorMarkers = when (result) {
            is EwoWorkoutParseResult.Success.NeedsCompileContext ->
                mapCompileErrorsToMarkers(result.compileErrors, editorSegments)
            is EwoWorkoutParseResult.Success.Compiled -> emptyList()
        }
        val validationMarkers = sanityMarkers + compileErrorMarkers

        return EditorWorkoutDocument(
            version = result.parsed.version,
            uid = normalized.uid,
            revision = normalized.revision,
            title = normalized.title,
            description = normalized.description ?: "",
            titleLocalized = normalized.titleLocalized?.let {
                EditorLocalizedText(defaultText = it.defaultText, translations = it.translations)
            },
            descriptionLocalized = normalized.descriptionLocalized?.let {
                EditorLocalizedText(defaultText = it.defaultText, translations = it.translations)
            },
            difficulty = result.parsed.difficulty,
            tags = normalized.tags,
            control = normalized.control?.let {
                EditorControl(
                    initialPowerWatts = it.initialPowerWatts,
                    minPowerWatts = it.minPowerWatts,
                    maxPowerWatts = it.maxPowerWatts,
                    signalLossPowerWatts = it.signalLossPowerWatts,
                    hrUpperCapBpm = it.hrUpperCapBpm,
                )
            },
            messages = editorMessages,
            segments = editorSegments,
            rootNodeId = rootNodeId,
            selectedNodeId = null,
            expandedNodeIds = collectRepeatNodeIds(editorSegments),
            isDirty = false,
            validationMarkers = validationMarkers,
            nextNodeCounter = counter,
        )
    }

    /**
     * Creates an empty editor document for a new workout.
     */
    fun empty(version: String = "1.6"): EditorWorkoutDocument {
        val rootNodeId = EditorNodeId("node_1")

        val document = EditorWorkoutDocument(
            version = version,
            uid = null,
            revision = null,
            title = "",
            description = "",
            titleLocalized = null,
            descriptionLocalized = null,
            difficulty = null,
            tags = emptyList(),
            control = null,
            messages = emptyList(),
            segments = emptyList(),
            rootNodeId = rootNodeId,
            selectedNodeId = null,
            expandedNodeIds = emptySet(),
            isDirty = false,
            validationMarkers = emptyList(),
            nextNodeCounter = 2,
        )
        return document.copy(validationMarkers = recomputeValidationMarkers(document))
    }

    /**
     * Rebuilds export-blocking and advisory markers for the current editor document.
     *
     * The editor keeps allowing temporary invalid state while the user types, so
     * this validator must stay lightweight and deterministic. We round-trip the
     * current canonical JSON through the shared parser to reuse one semantic
     * source of truth for title/segment/message rules, while compile-context
     * gaps remain warnings just like on open.
     */
    fun recomputeValidationMarkers(document: EditorWorkoutDocument): List<EditorValidationMarker> {
        val result = EwoEngine.parseDroppingInvalidTags(
            buildCanonicalJson(document),
            EwoCompileContext(),
        ).parseResult
        return when (result) {
            is EwoWorkoutParseResult.Success -> {
                val sanityMarkers = mapSanityToMarkers(result.sanityResult, document.segments)
                val compileErrorMarkers = when (result) {
                    is EwoWorkoutParseResult.Success.NeedsCompileContext ->
                        mapCompileErrorsToMarkers(result.compileErrors, document.segments)
                    is EwoWorkoutParseResult.Success.Compiled -> emptyList()
                }
                sanityMarkers + compileErrorMarkers
            }

            is EwoWorkoutParseResult.Failure -> {
                listOf(mapParseFailureToMarker(result.error))
            }
        }
    }

    // --- Internal mapping ---

    private fun mapSegment(
        segment: NormalizedEwoWorkoutSegment,
        nextId: () -> EditorNodeId,
    ): EditorSegment = when (segment) {
        is NormalizedEwoWorkoutSegment.PowerSteady -> EditorSegment.Steady(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            target = EditorTarget.Power(watts = segment.watts),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutSegment.FtpPercentSteady -> EditorSegment.Steady(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            target = EditorTarget.FtpPercent(fraction = segment.fraction),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutSegment.HeartRateSteady -> EditorSegment.Steady(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            target = EditorTarget.HeartRate(lowBpm = segment.lowBpm, highBpm = segment.highBpm),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutSegment.HeartRateRelativeSteady -> EditorSegment.Steady(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            target = EditorTarget.HeartRateRelative(
                reference = segment.reference,
                lowFraction = segment.lowFraction,
                highFraction = segment.highFraction,
            ),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutSegment.PowerRamp -> EditorSegment.Ramp(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            fromTarget = EditorTarget.Power(watts = segment.fromWatts),
            toTarget = EditorTarget.Power(watts = segment.toWatts),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutSegment.FtpPercentRamp -> EditorSegment.Ramp(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            fromTarget = EditorTarget.FtpPercent(fraction = segment.fromFraction),
            toTarget = EditorTarget.FtpPercent(fraction = segment.toFraction),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutSegment.FreeRide -> EditorSegment.FreeRide(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutSegment.Repeat -> EditorSegment.Repeat(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            count = segment.count,
            segments = segment.segments.map { child -> mapRepeatChild(child, nextId) },
        )
    }

    private fun mapRepeatChild(
        segment: NormalizedEwoWorkoutRepeatSegment,
        nextId: () -> EditorNodeId,
    ): EditorSegment = when (segment) {
        is NormalizedEwoWorkoutRepeatSegment.PowerSteady -> EditorSegment.Steady(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            target = EditorTarget.Power(watts = segment.watts),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutRepeatSegment.FtpPercentSteady -> EditorSegment.Steady(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            target = EditorTarget.FtpPercent(fraction = segment.fraction),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutRepeatSegment.HeartRateSteady -> EditorSegment.Steady(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            target = EditorTarget.HeartRate(lowBpm = segment.lowBpm, highBpm = segment.highBpm),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutRepeatSegment.HeartRateRelativeSteady -> EditorSegment.Steady(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            target = EditorTarget.HeartRateRelative(
                reference = segment.reference,
                lowFraction = segment.lowFraction,
                highFraction = segment.highFraction,
            ),
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )

        is NormalizedEwoWorkoutRepeatSegment.FreeRide -> EditorSegment.FreeRide(
            nodeId = nextId(),
            segmentId = segment.id,
            label = segment.label,
            note = segment.note,
            messages = segment.messages.map { mapMessage(it, nextId()) },
            durationSec = segment.durationSec,
            cadence = segment.cadence?.let { EditorCadenceRange(it.low, it.high) },
        )
    }

    private fun mapMessage(msg: EwoMessage, nodeId: EditorNodeId): EditorMessage = EditorMessage(
        nodeId = nodeId,
        kind = msg.kind.name.lowercase(),
        timing = EditorMessageTiming(
            anchor = when (msg.timing.anchor) {
                EwoMessageAnchor.START -> EditorMessageAnchor.START
                EwoMessageAnchor.END -> EditorMessageAnchor.END
            },
            offsetSec = msg.timing.offsetSec,
        ),
        defaultText = msg.text.defaultText,
        translations = msg.text.translations,
    )

    /** Collect repeat node IDs so repeats start expanded in the tree view. */
    private fun collectRepeatNodeIds(segments: List<EditorSegment>): Set<EditorNodeId> {
        val ids = mutableSetOf<EditorNodeId>()
        for (segment in segments) {
            if (segment is EditorSegment.Repeat) {
                ids += segment.nodeId
                ids += collectRepeatNodeIds(segment.segments)
            }
        }
        return ids
    }

    /** Maps sanity issues to editor validation markers by matching segment IDs to node IDs. */
    private fun mapSanityToMarkers(
        sanityResult: SanityCheckResult,
        editorSegments: List<EditorSegment>,
    ): List<EditorValidationMarker> {
        val segmentIdToNodeId = buildSegmentIdIndex(editorSegments)
        return sanityResult.issues.map { issue ->
            EditorValidationMarker(
                nodeId = issue.segmentId?.let { segmentIdToNodeId[it] },
                field = null,
                severity = EditorValidationSeverity.WARNING,
                message = issue.message,
                code = issue.code.stableCode,
            )
        }
    }

    /** Maps compile errors to editor validation markers with WARNING severity. */
    private fun mapCompileErrorsToMarkers(
        compileErrors: List<EwoCompileError>,
        editorSegments: List<EditorSegment>,
    ): List<EditorValidationMarker> {
        val segmentIdToNodeId = buildSegmentIdIndex(editorSegments)
        return compileErrors.map { error ->
            EditorValidationMarker(
                nodeId = error.segmentId?.let { segmentIdToNodeId[it] },
                field = null,
                severity = EditorValidationSeverity.WARNING,
                message = error.message,
                code = error.code.stableCode,
            )
        }
    }

    private fun mapParseFailureToMarker(error: com.ewo.core.EwoWorkoutValidationError): EditorValidationMarker {
        val field = when {
            error.fieldPath == "$.title" -> "title"
            error.fieldPath.endsWith(".label") -> "label"
            error.fieldPath.endsWith(".note") -> "note"
            error.fieldPath.endsWith(".duration_sec") -> "duration_sec"
            error.fieldPath.endsWith(".count") -> "count"
            error.fieldPath.endsWith(".when") -> "when"
            else -> null
        }
        return EditorValidationMarker(
            nodeId = null,
            field = field,
            severity = EditorValidationSeverity.ERROR,
            message = error.message,
            code = error.code.stableCode,
        )
    }

    private fun buildSegmentIdIndex(segments: List<EditorSegment>): Map<String, EditorNodeId> {
        val index = mutableMapOf<String, EditorNodeId>()
        fun collect(segs: List<EditorSegment>) {
            for (seg in segs) {
                index[seg.segmentId] = seg.nodeId
                if (seg is EditorSegment.Repeat) collect(seg.segments)
            }
        }
        collect(segments)
        return index
    }
}
