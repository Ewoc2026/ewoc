package com.example.ergometerapp.ewoeditor

import com.ewo.editor.model.*
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile

/**
 * Converts a parsed ZWO [WorkoutFile] into an [EditorWorkoutDocument].
 *
 * ZWO power values are FTP fractions (0.75 = 75% FTP). Warmup/Cooldown/Ramp
 * become [EditorSegment.Ramp]; IntervalsT becomes [EditorSegment.Repeat] wrapping
 * on/off [EditorSegment.Steady] children.
 */
internal object ZwoToEditorConverter {

    fun convert(workoutFile: WorkoutFile): EditorWorkoutDocument {
        var nodeCounter = 0
        fun nextNodeId(): EditorNodeId {
            val id = EditorNodeId("node_$nodeCounter")
            nodeCounter++
            return id
        }

        fun nextSegmentId(prefix: String, index: Int): String = "${prefix}_$index"

        val segments = mutableListOf<EditorSegment>()
        val expandedNodeIds = mutableSetOf<EditorNodeId>()

        workoutFile.steps.forEachIndexed { index, step ->
            when (step) {
                is Step.SteadyState -> segments += EditorSegment.Steady(
                    nodeId = nextNodeId(),
                    segmentId = nextSegmentId("steady", index),
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = step.durationSec ?: 300,
                    target = step.power?.let { EditorTarget.FtpPercent(it) },
                    cadence = step.cadence?.let { EditorCadenceRange(it, it) },
                )

                is Step.Warmup -> segments += EditorSegment.Ramp(
                    nodeId = nextNodeId(),
                    segmentId = nextSegmentId("warmup", index),
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = step.durationSec ?: 300,
                    fromTarget = step.powerLow?.let { EditorTarget.FtpPercent(it) },
                    toTarget = step.powerHigh?.let { EditorTarget.FtpPercent(it) },
                    cadence = step.cadence?.let { EditorCadenceRange(it, it) },
                )

                is Step.Cooldown -> segments += EditorSegment.Ramp(
                    nodeId = nextNodeId(),
                    segmentId = nextSegmentId("cooldown", index),
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = step.durationSec ?: 300,
                    fromTarget = step.powerLow?.let { EditorTarget.FtpPercent(it) },
                    toTarget = step.powerHigh?.let { EditorTarget.FtpPercent(it) },
                    cadence = step.cadence?.let { EditorCadenceRange(it, it) },
                )

                is Step.Ramp -> segments += EditorSegment.Ramp(
                    nodeId = nextNodeId(),
                    segmentId = nextSegmentId("ramp", index),
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = step.durationSec ?: 300,
                    fromTarget = step.powerLow?.let { EditorTarget.FtpPercent(it) },
                    toTarget = step.powerHigh?.let { EditorTarget.FtpPercent(it) },
                    cadence = step.cadence?.let { EditorCadenceRange(it, it) },
                )

                is Step.IntervalsT -> {
                    val repeatNodeId = nextNodeId()
                    val onSegment = EditorSegment.Steady(
                        nodeId = nextNodeId(),
                        segmentId = nextSegmentId("on", index),
                        label = null,
                        note = null,
                        messages = emptyList(),
                        durationSec = step.onDurationSec ?: 60,
                        target = step.onPower?.let { EditorTarget.FtpPercent(it) },
                        cadence = step.cadence?.let { EditorCadenceRange(it, it) },
                    )
                    val offSegment = EditorSegment.Steady(
                        nodeId = nextNodeId(),
                        segmentId = nextSegmentId("off", index),
                        label = null,
                        note = null,
                        messages = emptyList(),
                        durationSec = step.offDurationSec ?: 60,
                        target = step.offPower?.let { EditorTarget.FtpPercent(it) },
                        cadence = step.cadence?.let { EditorCadenceRange(it, it) },
                    )
                    val repeat = EditorSegment.Repeat(
                        nodeId = repeatNodeId,
                        segmentId = nextSegmentId("intervals", index),
                        label = null,
                        note = null,
                        messages = emptyList(),
                        count = step.repeat ?: 3,
                        segments = listOf(onSegment, offSegment),
                    )
                    segments += repeat
                    expandedNodeIds += repeatNodeId
                }

                is Step.FreeRide -> segments += EditorSegment.FreeRide(
                    nodeId = nextNodeId(),
                    segmentId = nextSegmentId("freeride", index),
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = step.durationSec ?: 300,
                    cadence = step.cadence?.let { EditorCadenceRange(it, it) },
                )

                is Step.Unknown -> {
                    // Map unknown steps as free ride with a note about the original tag
                    segments += EditorSegment.FreeRide(
                        nodeId = nextNodeId(),
                        segmentId = nextSegmentId("unknown", index),
                        label = null,
                        note = "Imported from unknown ZWO step: ${step.tagName}",
                        messages = emptyList(),
                        durationSec = step.attributes["Duration"]?.toIntOrNull() ?: 300,
                        cadence = null,
                    )
                }
            }
        }

        // Convert ZWO text events to workout-level messages
        val messages = workoutFile.textEvents.map { event ->
            EditorMessage(
                nodeId = nextNodeId(),
                kind = "instruction",
                timing = EditorMessageTiming(
                    anchor = EditorMessageAnchor.START,
                    offsetSec = event.timeOffsetSec,
                ),
                defaultText = event.message,
                translations = emptyMap(),
            )
        }

        val rootNodeId = nextNodeId()

        val document = EditorWorkoutDocument(
            version = "1.5",
            uid = null,
            revision = null,
            title = workoutFile.name ?: "Imported Workout",
            description = workoutFile.description ?: "",
            difficulty = null,
            tags = workoutFile.tags,
            control = null,
            messages = messages,
            segments = segments,
            rootNodeId = rootNodeId,
            selectedNodeId = null,
            expandedNodeIds = expandedNodeIds,
            isDirty = true,
            validationMarkers = emptyList(),
            nextNodeCounter = nodeCounter + 1,
        )
        return document.copy(validationMarkers = EditorDocumentFactory.recomputeValidationMarkers(document))
    }
}
