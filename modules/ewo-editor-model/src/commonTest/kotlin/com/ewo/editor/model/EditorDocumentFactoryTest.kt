package com.ewo.editor.model

import com.ewo.core.EwoCompileContext
import com.ewo.core.EwoEngine
import com.ewo.core.EwoWorkoutParseResult
import com.ewo.core.HrReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorDocumentFactoryTest {

    @Test
    fun emptyDocumentHasDefaultStructure() {
        val doc = EditorDocumentFactory.empty()
        assertEquals("1.6", doc.version)
        assertEquals("", doc.title)
        assertEquals(0, doc.segments.size)
        assertFalse(doc.isDirty)
        assertNull(doc.selectedNodeId)
        assertTrue(doc.validationMarkers.isEmpty())
    }

    @Test
    fun emptyDocumentHasUniqueNodeIds() {
        val doc = EditorDocumentFactory.empty()
        val allIds = doc.allNodeIds()
        // root node only, no default segments
        assertEquals(1, allIds.size)
    }

    @Test
    fun fromParseResultMapsBasicWorkout() {
        val result = EwoEngine.parse(MINIMAL_POWER_WORKOUT)
        val success = result as EwoWorkoutParseResult.Success
        val doc = EditorDocumentFactory.fromParseResult(success)

        assertEquals("1.0", doc.version)
        assertEquals("Minimal power steady", doc.title)
        assertEquals(1, doc.segments.size)
        assertFalse(doc.isDirty)

        val segment = doc.segments[0] as EditorSegment.Steady
        assertEquals("warmup", segment.segmentId)
        assertNull(segment.label)
        assertNull(segment.note)
        assertEquals(900, segment.durationSec)
        assertEquals(EditorTarget.Power(watts = 150), segment.target)
    }

    @Test
    fun fromParseResultMapsRepeatWorkout() {
        val result = EwoEngine.parse(REPEAT_WORKOUT)
        val success = result as EwoWorkoutParseResult.Success
        val doc = EditorDocumentFactory.fromParseResult(success)

        assertEquals(3, doc.segments.size)
        val repeat = doc.segments[1] as EditorSegment.Repeat
        assertEquals("main_set", repeat.segmentId)
        assertEquals(2, repeat.count)
        assertEquals(2, repeat.segments.size)

        // Repeat should be expanded by default
        assertTrue(repeat.nodeId in doc.expandedNodeIds)
    }

    @Test
    fun fromParseResultAssignsUniqueNodeIds() {
        val result = EwoEngine.parse(REPEAT_WORKOUT)
        val success = result as EwoWorkoutParseResult.Success
        val doc = EditorDocumentFactory.fromParseResult(success)

        val allIds = doc.allNodeIds()
        val allSegmentNodeIds = mutableSetOf<EditorNodeId>()
        fun collect(segs: List<EditorSegment>) {
            for (seg in segs) {
                allSegmentNodeIds += seg.nodeId
                if (seg is EditorSegment.Repeat) collect(seg.segments)
            }
        }
        collect(doc.segments)

        // Every segment has a distinct node ID
        assertEquals(doc.allSegmentIds().size, allSegmentNodeIds.size)
    }

    @Test
    fun fromParseResultMapsMessages() {
        val result = EwoEngine.parse(MINIMAL_POWER_WORKOUT)
        val success = result as EwoWorkoutParseResult.Success
        val doc = EditorDocumentFactory.fromParseResult(success)

        // Root-level message
        assertEquals(1, doc.messages.size)
        assertEquals("intro", doc.messages[0].kind)
        assertEquals("Get ready.", doc.messages[0].defaultText)
        assertEquals(EditorMessageAnchor.START, doc.messages[0].timing.anchor)

        // Segment-level message
        val segment = doc.segments[0] as EditorSegment.Steady
        assertEquals(1, segment.messages.size)
        assertEquals("instruction", segment.messages[0].kind)
    }

    @Test
    fun fromParseResultMapsSanityIssuesToMarkers() {
        // Workout with no warmup — should trigger MISSING_WARMUP
        val result = EwoEngine.parse(NO_WARMUP_WORKOUT, ftpWatts = 200)
        val success = result as EwoWorkoutParseResult.Success
        val doc = EditorDocumentFactory.fromParseResult(success)

        val warmupMarkers = doc.validationMarkers.filter { it.code == "missing_warmup" }
        assertTrue(warmupMarkers.isNotEmpty(), "Expected MISSING_WARMUP sanity marker")
        assertEquals(EditorValidationSeverity.WARNING, warmupMarkers[0].severity)
        assertFalse(warmupMarkers[0].blocksExport)
    }

    @Test
    fun fromParseResultMapsHeartRateRelativeTarget() {
        val result = EwoEngine.parse(HR_RELATIVE_WORKOUT, EwoCompileContext(hrMaxBpm = 190))
        val success = result as EwoWorkoutParseResult.Success
        val doc = EditorDocumentFactory.fromParseResult(success)

        val segment = doc.segments[1] as EditorSegment.Steady
        val target = segment.target as EditorTarget.HeartRateRelative
        assertEquals(HrReference.HR_MAX, target.reference)
        assertEquals(0.72, target.lowFraction)
        assertEquals(0.80, target.highFraction)
    }

    @Test
    fun fromNeedsCompileContextMapsCompileErrorMarkers() {
        val result = EwoEngine.parse(HR_RELATIVE_WORKOUT, EwoCompileContext())
        val success = result as EwoWorkoutParseResult.Success.NeedsCompileContext
        val doc = EditorDocumentFactory.fromParseResult(success)

        val compileMarkers = doc.validationMarkers.filter { it.code == "missing_hr_max" }
        assertTrue(compileMarkers.isNotEmpty(), "Expected compile error marker for missing_hr_max")
        assertEquals(EditorValidationSeverity.WARNING, compileMarkers[0].severity)
    }

    @Test
    fun fromParseResultMapsV15IdentityAndFreeRideMetadata() {
        val result = EwoEngine.parse(
            """
            {
              "format": "ewo",
              "version": "1.5",
              "uid": "doc-1",
              "revision": 4,
              "title": "Free Ride Document",
              "segments": [
                {
                  "id": "spinout",
                  "type": "free_ride",
                  "label": "Spin Out",
                  "note": "Keep the rider in control.",
                  "duration_sec": 120
                }
              ]
            }
            """.trimIndent(),
        )
        val success = result as EwoWorkoutParseResult.Success
        val doc = EditorDocumentFactory.fromParseResult(success)

        assertEquals("doc-1", doc.uid)
        assertEquals(4, doc.revision)
        val freeRide = doc.segments.single() as EditorSegment.FreeRide
        assertEquals("Spin Out", freeRide.label)
        assertEquals("Keep the rider in control.", freeRide.note)
    }

    @Test
    fun findSegmentReturnsCorrectNode() {
        val result = EwoEngine.parse(REPEAT_WORKOUT)
        val success = result as EwoWorkoutParseResult.Success
        val doc = EditorDocumentFactory.fromParseResult(success)

        val repeat = doc.segments[1] as EditorSegment.Repeat
        val child = repeat.segments[0]

        // Find the child inside the repeat
        val found = doc.findSegment(child.nodeId)
        assertNotNull(found)
        assertEquals(child.segmentId, found.segmentId)
    }

    @Test
    fun findSegmentReturnsNullForUnknownId() {
        val doc = EditorDocumentFactory.empty()
        assertNull(doc.findSegment(EditorNodeId("nonexistent")))
    }

    @Test
    fun generateNodeIdReturnsUniqueIds() {
        val doc = EditorDocumentFactory.empty()
        val (id1, counter1) = doc.generateNodeId()
        val (id2, _) = doc.copy(nextNodeCounter = counter1).generateNodeId()
        assertTrue(id1 != id2)
    }

    companion object {
        private val MINIMAL_POWER_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "  Minimal power steady  ",
              "messages": [
                {
                  "kind": "intro",
                  "when": "start",
                  "text": { "default": "  Get ready.  " }
                }
              ],
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 900,
                  "target": { "metric": "power", "value": 150 },
                  "messages": [
                    {
                      "kind": "instruction",
                      "when": "start",
                      "text": { "default": "  Settle into the effort.  " }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        private val REPEAT_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Repeat workout",
              "control": {
                "initial_power_watts": 110,
                "min_power_watts": 90,
                "max_power_watts": 220,
                "signal_loss_power_watts": 100,
                "hr_upper_cap_bpm": 160
              },
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": { "metric": "power", "value": 130 }
                },
                {
                  "id": "main_set",
                  "type": "repeat",
                  "count": 2,
                  "segments": [
                    {
                      "id": "work",
                      "type": "steady",
                      "duration_sec": 60,
                      "target": { "metric": "power", "value": 200 }
                    },
                    {
                      "id": "recover",
                      "type": "steady",
                      "duration_sec": 60,
                      "target": { "metric": "power", "value": 100 }
                    }
                  ]
                },
                {
                  "id": "cooldown",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": { "metric": "power", "value": 100 }
                }
              ]
            }
        """.trimIndent()

        private val HR_RELATIVE_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.4",
              "title": "HR Relative",
              "control": {
                "initial_power_watts": 110,
                "min_power_watts": 90,
                "max_power_watts": 220,
                "signal_loss_power_watts": 100,
                "hr_upper_cap_bpm": 160
              },
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": { "metric": "power", "value": 100 }
                },
                {
                  "id": "hr_zone",
                  "type": "steady",
                  "duration_sec": 600,
                  "target": {
                    "metric": "heart_rate_relative",
                    "reference": "hr_max",
                    "range": { "low": 0.72, "high": 0.80 }
                  }
                }
              ]
            }
        """.trimIndent()

        private val NO_WARMUP_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "No warmup",
              "segments": [
                {
                  "id": "hard_start",
                  "type": "steady",
                  "duration_sec": 600,
                  "target": { "metric": "power", "value": 250 }
                }
              ]
            }
        """.trimIndent()
    }
}

class EditorWorkoutDocumentHrTargetsTest {

    @Test
    fun hasHrTargetsReturnsFalseForPowerOnly() {
        val doc = EditorDocumentFactory.empty()
        assertFalse(doc.hasHrTargets())
    }

    @Test
    fun hasHrTargetsReturnsTrueForHrTarget() {
        val doc = EditorDocumentFactory.empty().copy(
            segments = listOf(
                EditorSegment.Steady(
                    nodeId = EditorNodeId("n1"),
                    segmentId = "hr_seg",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = 300,
                    target = EditorTarget.HeartRate(lowBpm = 130, highBpm = 150),
                    cadence = null,
                ),
            ),
        )
        assertTrue(doc.hasHrTargets())
    }

    @Test
    fun hasHrTargetsReturnsTrueForNestedHr() {
        val doc = EditorDocumentFactory.empty().copy(
            segments = listOf(
                EditorSegment.Repeat(
                    nodeId = EditorNodeId("r1"),
                    segmentId = "repeat",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    count = 3,
                    segments = listOf(
                        EditorSegment.Steady(
                            nodeId = EditorNodeId("n1"),
                            segmentId = "hr_seg",
                            label = null,
                            note = null,
                            messages = emptyList(),
                            durationSec = 60,
                            target = EditorTarget.HeartRateRelative(
                                reference = com.ewo.core.HrReference.HR_MAX,
                                lowFraction = 0.7,
                                highFraction = 0.8,
                            ),
                            cadence = null,
                        ),
                    ),
                ),
            ),
        )
        assertTrue(doc.hasHrTargets())
    }
}
