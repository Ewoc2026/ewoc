package com.ewo.editor.model

import com.ewo.core.EwoCompileContext
import com.ewo.core.EwoEngine
import com.ewo.core.EwoWorkoutParseResult
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class EwoRoundTripTest {

    @Test
    fun titledDocumentWithSteadySegmentRoundTrips() {
        val doc = EditorDocumentFactory.empty().copy(
            title = "My Workout",
            segments = listOf(
                EditorSegment.Steady(
                    nodeId = EditorNodeId("node_2"),
                    segmentId = "warmup",
                    label = "Warmup",
                    note = null,
                    messages = emptyList(),
                    durationSec = 300,
                    target = EditorTarget.Power(watts = 100),
                    cadence = null,
                ),
            ),
        )
        val json = buildCanonicalJson(doc)
        val result = EwoEngine.parse(json)
        assertTrue(result is EwoWorkoutParseResult.Success, "Expected parse to succeed but got: $result")
    }

    @Test
    fun multiSegmentPowerDocumentRoundTrips() {
        val doc = EditorDocumentFactory.empty().copy(
            title = "Complex Workout",
            description = "Test workout",
            segments = listOf(
                EditorSegment.Steady(
                    nodeId = EditorNodeId("node_10"),
                    segmentId = "warmup",
                    label = "Warmup",
                    note = null,
                    messages = emptyList(),
                    durationSec = 300,
                    target = EditorTarget.Power(watts = 100),
                    cadence = null,
                ),
                EditorSegment.Ramp(
                    nodeId = EditorNodeId("node_11"),
                    segmentId = "ramp_up",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = 600,
                    fromTarget = EditorTarget.Power(watts = 100),
                    toTarget = EditorTarget.Power(watts = 200),
                    cadence = null,
                ),
                EditorSegment.FreeRide(
                    nodeId = EditorNodeId("node_12"),
                    segmentId = "recovery",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = 180,
                    cadence = null,
                ),
            ),
        )
        val json = buildCanonicalJson(doc)
        val result = EwoEngine.parse(json)
        assertTrue(result is EwoWorkoutParseResult.Success, "Expected parse to succeed but got: $result")
    }

    @Test
    fun emptyRepeatIsFilteredDuringExportSoDocumentRoundTrips() {
        val doc = EditorDocumentFactory.empty().copy(
            title = "With Empty Repeat",
            segments = listOf(
                EditorSegment.Ramp(
                    nodeId = EditorNodeId("node_10"),
                    segmentId = "ramp_up",
                    label = "Warmup",
                    note = null,
                    messages = emptyList(),
                    durationSec = 300,
                    fromTarget = EditorTarget.Power(watts = 100),
                    toTarget = EditorTarget.Power(watts = 200),
                    cadence = null,
                ),
                EditorSegment.Repeat(
                    nodeId = EditorNodeId("node_11"),
                    segmentId = "empty_repeat",
                    label = "First set",
                    note = null,
                    messages = emptyList(),
                    count = 4,
                    segments = emptyList(),
                ),
                EditorSegment.Steady(
                    nodeId = EditorNodeId("node_12"),
                    segmentId = "recovery",
                    label = "Recovery",
                    note = null,
                    messages = emptyList(),
                    durationSec = 240,
                    target = EditorTarget.Power(watts = 150),
                    cadence = null,
                ),
            ),
        )
        val json = buildCanonicalJson(doc)
        // Empty repeat must be filtered out of exported JSON
        assertTrue("segments" in json)
        assertTrue("empty_repeat" !in json, "Empty repeat should be filtered from export")

        val result = EwoEngine.parse(json)
        assertTrue(result is EwoWorkoutParseResult.Success, "Expected parse to succeed but got: $result")
        val restored = EditorDocumentFactory.fromParseResult(result as EwoWorkoutParseResult.Success)
        assertEquals(2, restored.segments.size, "Only non-empty segments should survive round-trip")
    }

    @Test
    fun tabataLightEditedFileParses() {
        val json = """
{
  "format": "ewo",
  "version": "1.5",
  "title": "Tabata light",
  "description": "Tabata light ewo workout\n",
"control": {
  "initial_power_watts": 70,
  "min_power_watts": 70,
  "max_power_watts": 200,
  "signal_loss_power_watts": 70,
  "hr_upper_cap_bpm": 138
},
  "segments": [
    {
      "id": "segment_4",
      "label": "Beginning. Build cadence slowly and warm up.",
      "type": "ramp",
      "duration_sec": 300,
      "from_target": { "metric": "ftp_percent", "value": 0.5 },
      "to_target": { "metric": "ftp_percent", "value": 1.0 },
      "messages": [
        { "kind": "instruction", "when": { "anchor": "start", "offset_sec": 25 }, "text": { "default": "Test message 1 @ 25 seconds " } }
      ]
    },
    {
      "id": "segment_7",
      "label": "First set",
      "type": "repeat",
      "count": 4,
      "messages": [
        { "kind": "instruction", "when": { "anchor": "start", "offset_sec": 10 }, "text": { "default": "This is the first set of 4 on 20s / off 10s intervals" } },
        { "kind": "instruction", "when": { "anchor": "end", "offset_sec": 30 }, "text": { "default": "Keep going, first set is almost done." } }
      ],
      "segments": [
        {
          "id": "segment_8",
          "label": "ON",
          "type": "steady",
          "duration_sec": 20,
          "target": { "metric": "ftp_percent", "value": 1.5 }
        },
        {
          "id": "segment_9",
          "label": "OFF",
          "type": "steady",
          "duration_sec": 10,
          "target": { "metric": "ftp_percent", "value": 0.75 }
        }
      ]
    },
    {
      "id": "segment_13",
      "label": "Recovery for 4 minutes.",
      "type": "steady",
      "duration_sec": 240,
      "target": { "metric": "ftp_percent", "value": 0.75 },
      "messages": [
        { "kind": "instruction", "when": { "anchor": "start", "offset_sec": 3 }, "text": { "default": "Steady pedaling for 4 minutes,, focus on breathing." } }
      ]
    }
  ]
}
        """.trimIndent()

        val context = EwoCompileContext(ftpWatts = 200, hrMaxBpm = 180)
        val result = EwoEngine.parse(json, context)
        when (result) {
            is EwoWorkoutParseResult.Failure ->
                println("FAILURE: ${result.error.code} - ${result.error.message} at ${result.error.fieldPath}")
            is EwoWorkoutParseResult.Success -> {
                println("SUCCESS: ${result.normalized.segments.size} segments")
                val doc = EditorDocumentFactory.fromParseResult(result)
                println("Editor doc: title='${doc.title}', segments=${doc.segments.size}")
                assertEquals(3, doc.segments.size)
                assertEquals("Tabata light", doc.title)
            }
        }
        assertTrue(result is EwoWorkoutParseResult.Success, "Expected parse to succeed but got: $result")
    }

    @Test
    fun roundTrippedDocumentPreservesContent() {
        val original = EditorDocumentFactory.empty().copy(
            title = "Preserved Workout",
            description = "Keep this",
            segments = listOf(
                EditorSegment.Steady(
                    nodeId = EditorNodeId("node_2"),
                    segmentId = "main",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = 300,
                    target = EditorTarget.Power(watts = 150),
                    cadence = null,
                ),
            ),
        )
        val json = buildCanonicalJson(original)
        val result = EwoEngine.parse(json)
        assertTrue(result is EwoWorkoutParseResult.Success)
        val restored = EditorDocumentFactory.fromParseResult(result as EwoWorkoutParseResult.Success)
        assertEquals(original.title, restored.title)
        assertEquals(original.description, restored.description)
        assertEquals(original.segments.size, restored.segments.size)
    }
}
