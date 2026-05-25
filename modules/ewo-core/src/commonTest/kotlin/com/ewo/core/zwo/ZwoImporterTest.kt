package com.ewo.core.zwo

import com.ewo.core.ParsedEwoCadenceRange
import com.ewo.core.ParsedEwoSegment
import com.ewo.core.ParsedEwoTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZwoImporterTest {

    // --- Steady-state workouts ---

    @Test
    fun importSimpleSteadyWorkout() {
        val result = importZwo(
            """
            <workout_file>
              <name>Easy Spin</name>
              <description>A simple endurance ride.</description>
              <workout>
                <SteadyState Duration="1800" Power="0.65"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals("Easy Spin", success.workout.title)
        assertEquals("A simple endurance ride.", success.workout.description)
        assertEquals("ewo", success.workout.format)
        assertEquals("1.5", success.workout.version)

        assertEquals(1, success.workout.segments.size)
        val seg = assertIs<ParsedEwoSegment.Steady>(success.workout.segments[0])
        assertEquals(1800, seg.durationSec)
        val target = assertIs<ParsedEwoTarget.FtpPercent>(seg.target)
        assertEquals(0.65, target.fraction)

        assertTrue(success.warnings.isEmpty())
    }

    @Test
    fun importMultipleSteadySegments() {
        val result = importZwo(
            """
            <workout_file>
              <name>Two-Step</name>
              <workout>
                <SteadyState Duration="600" Power="0.5"/>
                <SteadyState Duration="600" Power="0.9"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals(2, success.workout.segments.size)

        val seg1 = assertIs<ParsedEwoSegment.Steady>(success.workout.segments[0])
        assertEquals(600, seg1.durationSec)
        assertEquals(0.5, (seg1.target as ParsedEwoTarget.FtpPercent).fraction)

        val seg2 = assertIs<ParsedEwoSegment.Steady>(success.workout.segments[1])
        assertEquals(600, seg2.durationSec)
        assertEquals(0.9, (seg2.target as ParsedEwoTarget.FtpPercent).fraction)
    }

    @Test
    fun steadyStateWithPowerLowHighUsesMidpoint() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="300" PowerLow="0.6" PowerHigh="0.8"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.Steady>(success.workout.segments[0])
        assertEquals(0.7, (seg.target as ParsedEwoTarget.FtpPercent).fraction, 0.001)
    }

    @Test
    fun solidStateIsTreatedAsSteadyState() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SolidState Duration="600" Power="0.75"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.Steady>(success.workout.segments[0])
        assertEquals(600, seg.durationSec)
        assertEquals(0.75, (seg.target as ParsedEwoTarget.FtpPercent).fraction)
    }

    // --- Ramp/Warmup/Cooldown workouts ---

    @Test
    fun importWarmupAscendingRamp() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <Warmup Duration="300" PowerLow="0.4" PowerHigh="0.75"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.Ramp>(success.workout.segments[0])
        assertEquals(300, seg.durationSec)
        assertEquals("Warmup", seg.label)
        assertEquals(0.4, (seg.fromTarget as ParsedEwoTarget.FtpPercent).fraction)
        assertEquals(0.75, (seg.toTarget as ParsedEwoTarget.FtpPercent).fraction)
    }

    @Test
    fun importCooldownDescendingRamp() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <Cooldown Duration="300" PowerLow="0.4" PowerHigh="0.75"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.Ramp>(success.workout.segments[0])
        assertEquals(300, seg.durationSec)
        assertEquals("Cooldown", seg.label)
        // Cooldown reverses: from high to low
        assertEquals(0.75, (seg.fromTarget as ParsedEwoTarget.FtpPercent).fraction)
        assertEquals(0.4, (seg.toTarget as ParsedEwoTarget.FtpPercent).fraction)
    }

    @Test
    fun importExplicitRamp() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <Ramp Duration="600" PowerLow="0.5" PowerHigh="1.0"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.Ramp>(success.workout.segments[0])
        assertEquals(600, seg.durationSec)
        assertEquals(0.5, (seg.fromTarget as ParsedEwoTarget.FtpPercent).fraction)
        assertEquals(1.0, (seg.toTarget as ParsedEwoTarget.FtpPercent).fraction)
    }

    // --- Interval / Repeat workouts ---

    @Test
    fun importIntervalsTAsRepeat() {
        val result = importZwo(
            """
            <workout_file>
              <name>Intervals</name>
              <workout>
                <IntervalsT Repeat="4" OnDuration="120" OffDuration="60"
                            OnPower="1.1" OffPower="0.5"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals(1, success.workout.segments.size)

        val repeat = assertIs<ParsedEwoSegment.Repeat>(success.workout.segments[0])
        assertEquals(4, repeat.count)
        assertEquals("Intervals", repeat.label)
        assertEquals(2, repeat.segments.size)

        val onSeg = assertIs<ParsedEwoSegment.Steady>(repeat.segments[0])
        assertEquals(120, onSeg.durationSec)
        assertEquals(1.1, (onSeg.target as ParsedEwoTarget.FtpPercent).fraction)
        assertEquals("Work", onSeg.label)

        val offSeg = assertIs<ParsedEwoSegment.Steady>(repeat.segments[1])
        assertEquals(60, offSeg.durationSec)
        assertEquals(0.5, (offSeg.target as ParsedEwoTarget.FtpPercent).fraction)
        assertEquals("Rest", offSeg.label)
    }

    @Test
    fun importIntervalsTWithPowerOnOffVariants() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <IntervalsT Repeat="3" OnDuration="60" OffDuration="60"
                            PowerOnLow="0.9" PowerOnHigh="1.1"
                            PowerOffLow="0.4" PowerOffHigh="0.6"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val repeat = assertIs<ParsedEwoSegment.Repeat>(success.workout.segments[0])
        assertEquals(3, repeat.count)

        val onSeg = assertIs<ParsedEwoSegment.Steady>(repeat.segments[0])
        assertEquals(1.0, (onSeg.target as ParsedEwoTarget.FtpPercent).fraction, 0.001)

        val offSeg = assertIs<ParsedEwoSegment.Steady>(repeat.segments[1])
        assertEquals(0.5, (offSeg.target as ParsedEwoTarget.FtpPercent).fraction, 0.001)
    }

    // --- FreeRide / MaxEffort ---

    @Test
    fun importFreeRide() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <FreeRide Duration="300"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.FreeRide>(success.workout.segments[0])
        assertEquals(300, seg.durationSec)
        assertTrue(success.warnings.isEmpty())
    }

    @Test
    fun importFreerideLowercase() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <Freeride Duration="600"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertIs<ParsedEwoSegment.FreeRide>(success.workout.segments[0])
    }

    @Test
    fun importMaxEffortAsFreeRideWithWarning() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <MaxEffort Duration="30"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.FreeRide>(success.workout.segments[0])
        assertEquals(30, seg.durationSec)
        assertEquals("Max Effort", seg.label)

        assertEquals(1, success.warnings.size)
        assertEquals(ZwoImportWarningCode.MAX_EFFORT_AS_FREE_RIDE, success.warnings[0].code)
    }

    // --- Cadence ---

    @Test
    fun cadenceIsMappedWhenPresent() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75" Cadence="90"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.Steady>(success.workout.segments[0])
        assertEquals(ParsedEwoCadenceRange(low = 90, high = 90), seg.cadence)
    }

    @Test
    fun cadenceWithLowHighRange() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75" Cadence="85" CadenceLow="80" CadenceHigh="95"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.Steady>(success.workout.segments[0])
        assertEquals(ParsedEwoCadenceRange(low = 80, high = 95), seg.cadence)
    }

    @Test
    fun noCadenceResultsInNull() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val seg = assertIs<ParsedEwoSegment.Steady>(success.workout.segments[0])
        assertNull(seg.cadence)
    }

    // --- Metadata ---

    @Test
    fun tagsAreImported() {
        val result = importZwo(
            """
            <workout_file>
              <tags>
                <tag name="endurance"/>
                <tag>recovery</tag>
              </tags>
              <workout>
                <SteadyState Duration="600" Power="0.5"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals(listOf("endurance", "recovery"), success.workout.tags)
    }

    @Test
    fun authorIsPreservedInDescription() {
        val result = importZwo(
            """
            <workout_file>
              <name>Test</name>
              <description>A workout.</description>
              <author>Coach Smith</author>
              <workout>
                <SteadyState Duration="300" Power="0.5"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertTrue(success.workout.description!!.contains("Coach Smith"))
    }

    @Test
    fun missingNameFallsBackToDefault() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals("Imported ZWO Workout", success.workout.title)
    }

    // --- Warnings ---

    @Test
    fun textEventsProduceWarning() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
                <textevent timeoffset="30" message="Keep it steady!"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertTrue(success.warnings.any { it.code == ZwoImportWarningCode.TEXT_EVENTS_NOT_IMPORTED })
    }

    @Test
    fun heartRateMetadataProducesWarning() {
        val result = importZwo(
            """
            <workout_file>
              <TargetHeartrate>150</TargetHeartrate>
              <MaxHeartrate>185</MaxHeartrate>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertTrue(success.warnings.any { it.code == ZwoImportWarningCode.HEART_RATE_TARGETS_NOT_IMPORTED })
    }

    @Test
    fun unsupportedStepTypeProducesWarning() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
                <CustomStep Duration="60" CustomAttr="foo"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals(1, success.workout.segments.size) // Only the SteadyState was kept
        assertTrue(success.warnings.any { it.code == ZwoImportWarningCode.UNSUPPORTED_STEP_TYPE })
    }

    @Test
    fun missingDurationProducesWarning() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
                <SteadyState Power="0.5"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals(1, success.workout.segments.size)
        assertTrue(success.warnings.any { it.code == ZwoImportWarningCode.MISSING_DURATION })
    }

    @Test
    fun missingPowerProducesWarning() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
                <SteadyState Duration="300"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals(1, success.workout.segments.size)
        assertTrue(success.warnings.any { it.code == ZwoImportWarningCode.MISSING_POWER_TARGET })
    }

    @Test
    fun incompleteIntervalsTProducesWarning() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
                <IntervalsT Repeat="4" OnDuration="60"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals(1, success.workout.segments.size)
        assertTrue(success.warnings.any { it.code == ZwoImportWarningCode.INCOMPLETE_INTERVALS })
    }

    // --- Failure cases ---

    @Test
    fun malformedXmlReturnsFailure() {
        val result = importZwo("<workout_file><broken")

        val failure = assertIs<ZwoImportResult.Failure>(result)
        assertTrue(failure.reason.contains("Malformed XML"))
    }

    @Test
    fun emptyWorkoutReturnsFailure() {
        val result = importZwo(
            """
            <workout_file>
              <name>Empty</name>
              <workout/>
            </workout_file>
            """.trimIndent(),
        )

        val failure = assertIs<ZwoImportResult.Failure>(result)
        assertTrue(failure.reason.contains("No valid segments"))
    }

    @Test
    fun noWorkoutElementReturnsFailure() {
        val result = importZwo(
            """
            <workout_file>
              <name>Just metadata</name>
            </workout_file>
            """.trimIndent(),
        )

        val failure = assertIs<ZwoImportResult.Failure>(result)
        assertTrue(failure.reason.contains("No valid segments"))
    }

    // --- Segment IDs ---

    @Test
    fun segmentIdsAreUniqueAndValid() {
        val result = importZwo(
            """
            <workout_file>
              <workout>
                <Warmup Duration="300" PowerLow="0.4" PowerHigh="0.7"/>
                <SteadyState Duration="600" Power="0.8"/>
                <IntervalsT Repeat="3" OnDuration="60" OffDuration="60" OnPower="1.0" OffPower="0.5"/>
                <Cooldown Duration="300" PowerLow="0.4" PowerHigh="0.6"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val allIds = collectAllIds(success.workout.segments)
        // All IDs are unique
        assertEquals(allIds.size, allIds.toSet().size)
        // All IDs match EWO ID pattern
        val idPattern = Regex("^[a-z][a-z0-9_-]{0,63}$")
        allIds.forEach { id ->
            assertTrue(idPattern.matches(id), "ID '$id' does not match EWO pattern")
        }
    }

    // --- Full composite workout ---

    @Test
    fun importTypicalZwoWorkout() {
        val result = importZwo(
            """
            <workout_file>
              <name>Sweet Spot 2x20</name>
              <description>Two 20-minute sweet spot intervals.</description>
              <tags>
                <tag name="sweet_spot"/>
                <tag name="threshold"/>
              </tags>
              <workout>
                <Warmup Duration="600" PowerLow="0.4" PowerHigh="0.7"/>
                <SteadyState Duration="1200" Power="0.88" Cadence="90"/>
                <SteadyState Duration="300" Power="0.5"/>
                <SteadyState Duration="1200" Power="0.9" Cadence="85"/>
                <Cooldown Duration="300" PowerLow="0.6" PowerHigh="0.4"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals("Sweet Spot 2x20", success.workout.title)
        assertEquals("Two 20-minute sweet spot intervals.", success.workout.description)
        assertEquals(listOf("sweet_spot", "threshold"), success.workout.tags)
        assertEquals(5, success.workout.segments.size)

        // Warmup is a ramp
        assertIs<ParsedEwoSegment.Ramp>(success.workout.segments[0])
        // Steady segments
        assertIs<ParsedEwoSegment.Steady>(success.workout.segments[1])
        assertIs<ParsedEwoSegment.Steady>(success.workout.segments[2])
        assertIs<ParsedEwoSegment.Steady>(success.workout.segments[3])
        // Cooldown is a ramp
        assertIs<ParsedEwoSegment.Ramp>(success.workout.segments[4])

        assertTrue(success.warnings.isEmpty())
    }

    // --- XML entity handling ---

    @Test
    fun xmlEntitiesInTextAreDecoded() {
        val result = importZwo(
            """
            <workout_file>
              <name>Threshold &amp; VO2</name>
              <description>Hard &lt;intervals&gt; workout.</description>
              <workout>
                <SteadyState Duration="600" Power="0.75"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals("Threshold & VO2", success.workout.title)
        assertEquals("Hard <intervals> workout.", success.workout.description)
    }

    @Test
    fun xmlCommentsAreIgnored() {
        val result = importZwo(
            """
            <workout_file>
              <!-- This is a comment -->
              <name>Test</name>
              <workout>
                <!-- Another comment -->
                <SteadyState Duration="600" Power="0.75"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        assertEquals("Test", success.workout.title)
        assertEquals(1, success.workout.segments.size)
    }

    // --- Round-trip validation ---

    @Test
    fun importedWorkoutCanBeValidatedByEwoEngine() {
        val result = importZwo(
            """
            <workout_file>
              <name>Simple Test</name>
              <workout>
                <Warmup Duration="300" PowerLow="0.4" PowerHigh="0.75"/>
                <SteadyState Duration="600" Power="0.8"/>
                <Cooldown Duration="300" PowerLow="0.6" PowerHigh="0.4"/>
              </workout>
            </workout_file>
            """.trimIndent(),
        )

        val success = assertIs<ZwoImportResult.Success>(result)
        val workout = success.workout

        // Verify the output has the structural fields EWO expects
        assertEquals("ewo", workout.format)
        assertEquals("1.5", workout.version)
        assertTrue(workout.title.isNotBlank())
        assertTrue(workout.segments.isNotEmpty())

        // Verify all segments have valid IDs
        val idPattern = Regex("^[a-z][a-z0-9_-]{0,63}$")
        workout.segments.forEach { seg ->
            assertTrue(idPattern.matches(seg.id), "Segment ID '${seg.id}' is invalid")
        }
    }

    // --- Helper ---

    private fun collectAllIds(segments: List<ParsedEwoSegment>): List<String> {
        val ids = mutableListOf<String>()
        for (seg in segments) {
            ids.add(seg.id)
            if (seg is ParsedEwoSegment.Repeat) {
                ids.addAll(collectAllIds(seg.segments))
            }
        }
        return ids
    }
}
