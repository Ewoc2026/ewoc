package com.example.ergometerapp.ewoeditor

import com.ewo.editor.model.EditorCadenceRange
import com.ewo.editor.model.EditorSegment
import com.ewo.editor.model.EditorTarget
import com.ewo.editor.model.allNodeIds
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.WorkoutTextEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZwoToEditorConverterTest {

    @Test
    fun convertsSteadyState() {
        val wf = WorkoutFile(
            name = "Test",
            description = "Desc",
            author = null,
            tags = listOf("endurance"),
            steps = listOf(Step.SteadyState(durationSec = 600, power = 0.75, cadence = 90)),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        assertEquals("Test", doc.title)
        assertEquals("Desc", doc.description)
        assertEquals(listOf("endurance"), doc.tags)
        assertEquals(1, doc.segments.size)

        val seg = doc.segments[0] as EditorSegment.Steady
        assertEquals(600, seg.durationSec)
        assertEquals(EditorTarget.FtpPercent(0.75), seg.target)
        assertEquals(EditorCadenceRange(90, 90), seg.cadence)
    }

    @Test
    fun convertsWarmupToRamp() {
        val wf = WorkoutFile(
            name = "Warmup",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(Step.Warmup(durationSec = 300, powerLow = 0.25, powerHigh = 0.75, cadence = null)),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        assertEquals(1, doc.segments.size)
        val seg = doc.segments[0] as EditorSegment.Ramp
        assertEquals(300, seg.durationSec)
        assertEquals(EditorTarget.FtpPercent(0.25), seg.fromTarget)
        assertEquals(EditorTarget.FtpPercent(0.75), seg.toTarget)
        assertNull(seg.cadence)
    }

    @Test
    fun convertsCooldownToRamp() {
        val wf = WorkoutFile(
            name = null,
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(Step.Cooldown(durationSec = 300, powerLow = 0.75, powerHigh = 0.25, cadence = null)),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        val seg = doc.segments[0] as EditorSegment.Ramp
        assertEquals(EditorTarget.FtpPercent(0.75), seg.fromTarget)
        assertEquals(EditorTarget.FtpPercent(0.25), seg.toTarget)
    }

    @Test
    fun convertsRampStep() {
        val wf = WorkoutFile(
            name = null,
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(Step.Ramp(durationSec = 600, powerLow = 0.5, powerHigh = 1.0, cadence = null)),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        val seg = doc.segments[0] as EditorSegment.Ramp
        assertEquals(600, seg.durationSec)
    }

    @Test
    fun convertsIntervalsTToRepeat() {
        val wf = WorkoutFile(
            name = "Intervals",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.IntervalsT(
                    onDurationSec = 30,
                    offDurationSec = 60,
                    onPower = 1.2,
                    offPower = 0.5,
                    repeat = 5,
                    cadence = null,
                ),
            ),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        assertEquals(1, doc.segments.size)
        val repeat = doc.segments[0] as EditorSegment.Repeat
        assertEquals(5, repeat.count)
        assertEquals(2, repeat.segments.size)

        val onSeg = repeat.segments[0] as EditorSegment.Steady
        assertEquals(30, onSeg.durationSec)
        assertEquals(EditorTarget.FtpPercent(1.2), onSeg.target)

        val offSeg = repeat.segments[1] as EditorSegment.Steady
        assertEquals(60, offSeg.durationSec)
        assertEquals(EditorTarget.FtpPercent(0.5), offSeg.target)

        // Repeat node should be auto-expanded
        assertTrue(doc.expandedNodeIds.contains(repeat.nodeId))
    }

    @Test
    fun convertsFreeRide() {
        val wf = WorkoutFile(
            name = null,
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(Step.FreeRide(durationSec = 120, cadence = 80)),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        val seg = doc.segments[0] as EditorSegment.FreeRide
        assertEquals(120, seg.durationSec)
        assertEquals(EditorCadenceRange(80, 80), seg.cadence)
    }

    @Test
    fun convertsUnknownStepToFreeRide() {
        val wf = WorkoutFile(
            name = null,
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(Step.Unknown(tagName = "CustomStep", attributes = mapOf("Duration" to "180"))),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        val seg = doc.segments[0] as EditorSegment.FreeRide
        assertEquals(180, seg.durationSec)
        assertNotNull(seg.note)
        assertTrue(seg.note!!.contains("CustomStep"))
    }

    @Test
    fun convertsTextEventsToMessages() {
        val wf = WorkoutFile(
            name = "Events",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(Step.SteadyState(durationSec = 300, power = 0.75, cadence = null)),
            textEvents = listOf(
                WorkoutTextEvent(timeOffsetSec = 0, message = "Let's go!", durationSec = null),
                WorkoutTextEvent(timeOffsetSec = 120, message = "Keep pushing", durationSec = 10),
            ),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        assertEquals(2, doc.messages.size)
        assertEquals("Let's go!", doc.messages[0].defaultText)
        assertEquals(0, doc.messages[0].timing.offsetSec)
        assertEquals("instruction", doc.messages[0].kind)
        assertEquals("Keep pushing", doc.messages[1].defaultText)
        assertEquals(120, doc.messages[1].timing.offsetSec)
    }

    @Test
    fun nullDefaultsHandled() {
        val wf = WorkoutFile(
            name = null,
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.SteadyState(durationSec = null, power = null, cadence = null),
            ),
        )
        val doc = ZwoToEditorConverter.convert(wf)

        assertEquals("Imported Workout", doc.title)
        assertEquals("", doc.description)
        val seg = doc.segments[0] as EditorSegment.Steady
        assertEquals(300, seg.durationSec)
        assertNull(seg.target)
        assertNull(seg.cadence)
    }

    @Test
    fun allNodeIdsAreUnique() {
        val wf = WorkoutFile(
            name = "Multi",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.Warmup(durationSec = 300, powerLow = 0.25, powerHigh = 0.75, cadence = null),
                Step.IntervalsT(
                    onDurationSec = 30, offDurationSec = 60,
                    onPower = 1.0, offPower = 0.5,
                    repeat = 3, cadence = null,
                ),
                Step.SteadyState(durationSec = 600, power = 0.85, cadence = null),
                Step.FreeRide(durationSec = 120, cadence = null),
            ),
            textEvents = listOf(
                WorkoutTextEvent(timeOffsetSec = 0, message = "Go!", durationSec = null),
            ),
        )
        val doc = ZwoToEditorConverter.convert(wf)
        val allIds = doc.allNodeIds()
        // Verify no duplicates: set size should match count
        val allIdsList = mutableListOf(doc.rootNodeId)
        doc.messages.forEach { allIdsList += it.nodeId }
        fun collectSegmentIds(segments: List<EditorSegment>) {
            for (seg in segments) {
                allIdsList += seg.nodeId
                seg.messages.forEach { allIdsList += it.nodeId }
                if (seg is EditorSegment.Repeat) collectSegmentIds(seg.segments)
            }
        }
        collectSegmentIds(doc.segments)

        assertEquals(allIdsList.size, allIdsList.toSet().size)
    }
}
