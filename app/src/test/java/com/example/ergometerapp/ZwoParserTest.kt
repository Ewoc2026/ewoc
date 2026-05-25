package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.parseZwo

class ZwoParserTest {
    @Test
    fun parsesTextEventAttributesFromWorkout() {
        val xml = """
            <workout_file>
              <workout>
                <textevent timeoffset="15" message="Settle cadence" duration="6"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(1, result.textEvents.size)
        val textEvent = result.textEvents.first()
        assertEquals(15, textEvent.timeOffsetSec)
        assertEquals("Settle cadence", textEvent.message)
        assertEquals(6, textEvent.durationSec)
    }

    @Test
    fun parsesTextEventMessageFromTagText() {
        val xml = """
            <workout_file>
              <workout>
                <TextEvent TimeOffset="30" Duration="5">Relax shoulders</TextEvent>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(1, result.textEvents.size)
        val textEvent = result.textEvents.first()
        assertEquals(30, textEvent.timeOffsetSec)
        assertEquals("Relax shoulders", textEvent.message)
        assertEquals(5, textEvent.durationSec)
    }

    @Test
    fun tagAttributeOnlyIsParsed() {
        val xml = """
            <workout_file>
              <tags>
                <tag name="test"/>
              </tags>
              <workout/>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(listOf("test"), result.tags)
    }

    @Test
    fun tagTextOnlyIsParsed() {
        val xml = """
            <workout_file>
              <tags>
                <tag>short</tag>
              </tags>
              <workout/>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(listOf("short"), result.tags)
    }

    @Test
    fun tagAttributeOverridesTextAndNoDuplicateIsAdded() {
        val xml = """
            <workout_file>
              <tags>
                <tag name="name">text</tag>
              </tags>
              <workout/>
            </workout_file>
        """.trimIndent()
        val result = parseZwo(xml)

        assertEquals(listOf("name"), result.tags)
    }

    @Test
    fun freeRideAliasFreerideIsRecognized() {
        val xml = """
            <workout_file>
              <workout>
                <Freeride Duration="120"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(1, result.steps.size)
        assertTrue(result.steps.first() is Step.FreeRide)
        assertEquals(120, (result.steps.first() as Step.FreeRide).durationSec)
    }

    @Test
    fun steadyStateCanFallbackToPowerRangeAverage() {
        val xml = """
            <workout_file>
              <workout>
                <SteadyState Duration="300" PowerLow="0.70" PowerHigh="0.90"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(1, result.steps.size)
        val steady = result.steps.first() as Step.SteadyState
        assertEquals(0.80, steady.power ?: 0.0, 0.0001)
    }

    @Test
    fun intervalsTCanFallbackToPowerOnOffRanges() {
        val xml = """
            <workout_file>
              <workout>
                <IntervalsT Repeat="4" OnDuration="30" OffDuration="30" PowerOnLow="0.95" PowerOnHigh="1.05" PowerOffLow="0.55" PowerOffHigh="0.65"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(1, result.steps.size)
        val intervals = result.steps.first() as Step.IntervalsT
        assertEquals(1.00, intervals.onPower ?: 0.0, 0.0001)
        assertEquals(0.60, intervals.offPower ?: 0.0, 0.0001)
    }

    @Test
    fun parsesOptionalWorkoutLevelHeartRateTargets() {
        val xml = """
            <workout_file>
              <TargetHeartrate>155</TargetHeartrate>
              <MaxHeartrate>182</MaxHeartrate>
              <workout>
                <SteadyState Duration="300" Power="0.80"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        val heartRateTargets = result.heartRateTargets
        assertEquals(155, heartRateTargets?.targetHeartRateBpm)
        assertEquals(182, heartRateTargets?.maxHeartRateBpm)
    }

    @Test
    fun ignoresInvalidOptionalWorkoutLevelHeartRateTargets() {
        val xml = """
            <workout_file>
              <TargetHeartrate>hard</TargetHeartrate>
              <MaxHeartrate> </MaxHeartrate>
              <workout>
                <SteadyState Duration="300" Power="0.80"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(null, result.heartRateTargets)
    }

    @Test
    fun parsesOptionalWorkoutLevelHeartRateTargetsAcrossTagVariants() {
        val xml = """
            <workout_file>
              <targetHeartRate>154</targetHeartRate>
              <max-heart-rate>188</max-heart-rate>
              <workout>
                <SteadyState Duration="300" Power="0.80"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = parseZwo(xml)

        assertEquals(154, result.heartRateTargets?.targetHeartRateBpm)
        assertEquals(188, result.heartRateTargets?.maxHeartRateBpm)
    }
}
