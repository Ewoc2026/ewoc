package com.ewo.editor.model

import com.ewo.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorPreviewTest {

    @Test
    fun previewFromCompiledPowerWorkout() {
        val result = EwoEngine.parse(POWER_WORKOUT, ftpWatts = 200)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult, ftpWatts = 200)

        assertEquals(2, preview.steps.size)
        assertEquals("warmup", preview.steps[0].segmentId)
        assertEquals("100W", preview.steps[0].label)
        assertEquals(300, preview.steps[0].durationSec)

        assertEquals("work", preview.steps[1].segmentId)
        assertEquals("200W", preview.steps[1].label)
        assertEquals(600, preview.steps[1].durationSec)

        assertEquals(900, preview.totalDurationSec)
        assertTrue(preview.sanityWarnings.isEmpty())
        assertTrue(preview.compileErrors.isEmpty())
    }

    @Test
    fun ifTssComputedWhenFtpProvided() {
        val result = EwoEngine.parse(POWER_WORKOUT, ftpWatts = 200)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult, ftpWatts = 200)

        assertNotNull(preview.intensityFactor)
        assertNotNull(preview.tss)
        assertTrue(preview.intensityFactor!! > 0)
        assertTrue(preview.tss!! > 0)
    }

    @Test
    fun ifTssSuppressedWhenFtpMissing() {
        val result = EwoEngine.parse(POWER_WORKOUT)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult, ftpWatts = null)

        assertNull(preview.intensityFactor)
        assertNull(preview.tss)
    }

    @Test
    fun ifTssSuppressedForHrGuidedWorkout() {
        val result = EwoEngine.parse(HR_WORKOUT, EwoCompileContext(hrMaxBpm = 190))
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult, ftpWatts = 200)

        assertNull(preview.intensityFactor, "IF should be suppressed for HR-guided workouts")
        assertNull(preview.tss, "TSS should be suppressed for HR-guided workouts")
    }

    @Test
    fun previewFromNeedsCompileContextHasErrors() {
        val result = EwoEngine.parse(HR_WORKOUT, EwoCompileContext())
        val needsContext = result as EwoWorkoutParseResult.Success.NeedsCompileContext
        val preview = buildEditorPreview(
            compiled = null,
            sanityResult = needsContext.sanityResult,
            compileErrors = needsContext.compileErrors,
        )

        assertTrue(preview.steps.isEmpty())
        assertTrue(preview.compileErrors.isNotEmpty())
        assertNull(preview.intensityFactor)
        assertNull(preview.tss)
    }

    @Test
    fun previewIncludesRepeatInfo() {
        val result = EwoEngine.parse(REPEAT_WORKOUT)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult)

        // warmup + 2x(work, recover) + cooldown = 6 steps
        assertEquals(6, preview.steps.size)

        // Steps from repeat block should have repeatInfo
        val repeatSteps = preview.steps.filter { it.repeatInfo != null }
        assertEquals(4, repeatSteps.size)
    }

    @Test
    fun previewRampStepLabel() {
        val result = EwoEngine.parse(RAMP_WORKOUT)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult)

        val rampStep = preview.steps.first { it.label.contains("→") }
        assertEquals("100→200W", rampStep.label)
    }

    @Test
    fun previewIncludesSanityWarnings() {
        // No-warmup workout with high power should trigger MISSING_WARMUP
        val result = EwoEngine.parse(NO_WARMUP_WORKOUT, ftpWatts = 200)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled)
        val preview = buildEditorPreview(compiled.compiled, compiled.sanityResult, ftpWatts = 200)

        val warmupWarnings = preview.sanityWarnings.filter { it.code == "missing_warmup" }
        assertTrue(warmupWarnings.isNotEmpty(), "Expected MISSING_WARMUP sanity warning")
    }

    @Test
    fun chartBarsComputedFromCompiledWorkout() {
        val result = EwoEngine.parse(POWER_WORKOUT, ftpWatts = 200)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult, ftpWatts = 200)

        assertEquals(2, preview.chartBars.size)
        val bar1 = preview.chartBars[0]
        assertEquals(0, bar1.startSec)
        assertEquals(300, bar1.endSec)
        assertEquals(100, bar1.powerLow)
        assertEquals(100, bar1.powerHigh)
        assertEquals(EditorChartBarType.STEADY, bar1.type)

        val bar2 = preview.chartBars[1]
        assertEquals(300, bar2.startSec)
        assertEquals(900, bar2.endSec)
        assertEquals(200, bar2.powerLow)
        assertEquals(200, bar2.powerHigh)
    }

    @Test
    fun chartBarsIncludeRampType() {
        val result = EwoEngine.parse(RAMP_WORKOUT)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult)

        assertEquals(1, preview.chartBars.size)
        val bar = preview.chartBars[0]
        assertEquals(EditorChartBarType.RAMP, bar.type)
        assertEquals(100, bar.powerLow)
        assertEquals(200, bar.powerHigh)
    }

    @Test
    fun chartBarsEmptyWhenNullCompiled() {
        val result = EwoEngine.parse(HR_WORKOUT, EwoCompileContext())
        val needsContext = result as EwoWorkoutParseResult.Success.NeedsCompileContext
        val preview = buildEditorPreview(
            compiled = null,
            sanityResult = needsContext.sanityResult,
            compileErrors = needsContext.compileErrors,
        )
        assertTrue(preview.chartBars.isEmpty())
    }

    @Test
    fun fallbackChartBarsKeepHeartRateSegmentsVisible() {
        val bars = buildChartBarsFromSegments(
            listOf(
                EditorSegment.Ramp(
                    nodeId = EditorNodeId("node_1"),
                    segmentId = "build",
                    durationSec = 300,
                    fromTarget = EditorTarget.FtpPercent(0.45),
                    toTarget = EditorTarget.FtpPercent(0.65),
                ),
                EditorSegment.Steady(
                    nodeId = EditorNodeId("node_2"),
                    segmentId = "hr_band",
                    durationSec = 240,
                    target = EditorTarget.HeartRate(130, 145),
                ),
                EditorSegment.Steady(
                    nodeId = EditorNodeId("node_3"),
                    segmentId = "hr_rel",
                    durationSec = 240,
                    target = EditorTarget.HeartRateRelative(
                        reference = HrReference.HR_MAX,
                        lowFraction = 0.72,
                        highFraction = 0.80,
                    ),
                ),
            ),
        )

        assertEquals(3, bars.size)
        assertEquals(EditorChartBarType.RAMP, bars[0].type)
        assertEquals(EditorChartBarType.HR, bars[1].type)
        assertEquals(130, bars[1].powerLow)
        assertEquals(145, bars[1].powerHigh)
        assertEquals(EditorChartBarType.HR, bars[2].type)
        assertEquals(72, bars[2].powerLow)
        assertEquals(80, bars[2].powerHigh)
    }

    @Test
    fun previewLabelsFreeRideSteps() {
        val result = EwoEngine.parse(FREE_RIDE_WORKOUT)
        val compiled = (result as EwoWorkoutParseResult.Success.Compiled).compiled
        val preview = buildEditorPreview(compiled, result.sanityResult)

        assertEquals(1, preview.steps.size)
        assertEquals("Free Ride", preview.steps.single().label)
    }

    companion object {
        private val POWER_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Power test",
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": { "metric": "power", "value": 100 }
                },
                {
                  "id": "work",
                  "type": "steady",
                  "duration_sec": 600,
                  "target": { "metric": "power", "value": 200 }
                }
              ]
            }
        """.trimIndent()

        private val HR_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.4",
              "title": "HR test",
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

        private val REPEAT_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Repeat test",
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": { "metric": "power", "value": 100 }
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

        private val RAMP_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Ramp test",
              "segments": [
                {
                  "id": "warmup",
                  "type": "ramp",
                  "duration_sec": 600,
                  "from_target": { "metric": "power", "value": 100 },
                  "to_target": { "metric": "power", "value": 200 }
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

        private val FREE_RIDE_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.5",
              "title": "Free ride preview",
              "segments": [
                {
                  "id": "spinout",
                  "type": "free_ride",
                  "duration_sec": 90
                }
              ]
            }
        """.trimIndent()
    }
}
