package com.example.ergometerapp.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErgoWorkoutParserTest {
    @Test
    fun parsesMinimalValidPowerSteadyWorkout() {
        val result = ErgoWorkoutParser.parse(
            """
            {
              "format": "ergo_workout",
              "version": "0.1",
              "title": "  Minimal power steady  ",
              "segments": [
                {
                  "type": "steady",
                  "duration_sec": 900,
                  "target": {
                    "metric": "power",
                    "value": 150
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val success = requireSuccess(result)
        assertEquals("  Minimal power steady  ", success.parsed.title)
        assertEquals("Minimal power steady", success.normalized.title)
        assertEquals(900, success.compiled.totalDurationSec)
        val compiledStep = success.compiled.steps.single() as CompiledErgoWorkoutStep.PowerSteady
        assertEquals(0, compiledStep.stepIndex)
        assertEquals(0, compiledStep.startOffsetSec)
        assertEquals(900, compiledStep.durationSec)
        assertEquals(150, compiledStep.watts)
    }

    @Test
    fun rejectsHeartRateWorkoutWithoutControl() {
        val failure = requireFailure(
            ErgoWorkoutParser.parse(
                """
                {
                  "format": "ergo_workout",
                  "version": "0.1",
                  "title": "HR without control",
                  "segments": [
                    {
                      "type": "steady",
                      "duration_sec": 600,
                      "target": {
                        "metric": "heart_rate",
                        "range": {
                          "low": 125,
                          "high": 135
                        }
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(
            ErgoWorkoutValidationErrorCode.CONTROL_REQUIRED_FOR_HEART_RATE,
            failure.error.code,
        )
        assertEquals("$.control", failure.error.fieldPath)
    }

    @Test
    fun rejectsInvalidHeartRateRange() {
        val failure = requireFailure(
            ErgoWorkoutParser.parse(
                """
                {
                  "format": "ergo_workout",
                  "version": "0.1",
                  "title": "Bad HR range",
                  "control": {
                    "initial_power_watts": 110,
                    "min_power_watts": 90,
                    "max_power_watts": 220,
                    "signal_loss_power_watts": 100
                  },
                  "segments": [
                    {
                      "type": "steady",
                      "duration_sec": 600,
                      "target": {
                        "metric": "heart_rate",
                        "range": {
                          "low": 135,
                          "high": 135
                        }
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(ErgoWorkoutValidationErrorCode.INVALID_HEART_RATE_RANGE, failure.error.code)
        assertEquals("$.segments[0].target.range", failure.error.fieldPath)
    }

    @Test
    fun rejectsRampWithHeartRateTarget() {
        val failure = requireFailure(
            ErgoWorkoutParser.parse(
                """
                {
                  "format": "ergo_workout",
                  "version": "0.1",
                  "title": "Bad ramp",
                  "segments": [
                    {
                      "type": "ramp",
                      "duration_sec": 300,
                      "from_target": {
                        "metric": "heart_rate",
                        "range": {
                          "low": 120,
                          "high": 130
                        }
                      },
                      "to_target": {
                        "metric": "power",
                        "value": 180
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(ErgoWorkoutValidationErrorCode.RAMP_TARGET_MUST_BE_POWER, failure.error.code)
        assertEquals("$.segments[0].from_target.metric", failure.error.fieldPath)
    }

    @Test
    fun rejectsRepeatWithInvalidChild() {
        val failure = requireFailure(
            ErgoWorkoutParser.parse(
                """
                {
                  "format": "ergo_workout",
                  "version": "0.1",
                  "title": "Bad repeat child",
                  "segments": [
                    {
                      "type": "repeat",
                      "count": 2,
                      "steps": [
                        {
                          "type": "ramp",
                          "duration_sec": 60,
                          "from_target": {
                            "metric": "power",
                            "value": 140
                          },
                          "to_target": {
                            "metric": "power",
                            "value": 180
                          }
                        },
                        {
                          "type": "steady",
                          "duration_sec": 30,
                          "target": {
                            "metric": "power",
                            "value": 120
                          }
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(
            ErgoWorkoutValidationErrorCode.REPEAT_CHILD_TYPE_NOT_ALLOWED,
            failure.error.code,
        )
        assertEquals("$.segments[0].steps[0].type", failure.error.fieldPath)
    }

    @Test
    fun rejectsUnknownTopLevelField() {
        val failure = requireFailure(
            ErgoWorkoutParser.parse(
                """
                {
                  "format": "ergo_workout",
                  "version": "0.1",
                  "title": "Unknown field",
                  "author": "manual",
                  "segments": [
                    {
                      "type": "steady",
                      "duration_sec": 60,
                      "target": {
                        "metric": "power",
                        "value": 100
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(ErgoWorkoutValidationErrorCode.UNKNOWN_FIELD, failure.error.code)
        assertEquals("$.author", failure.error.fieldPath)
    }

    @Test
    fun rejectsBlankTitleAfterTrim() {
        val failure = requireFailure(
            ErgoWorkoutParser.parse(
                """
                {
                  "format": "ergo_workout",
                  "version": "0.1",
                  "title": "   ",
                  "segments": [
                    {
                      "type": "steady",
                      "duration_sec": 60,
                      "target": {
                        "metric": "power",
                        "value": 100
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(ErgoWorkoutValidationErrorCode.EMPTY_TITLE, failure.error.code)
        assertEquals("$.title", failure.error.fieldPath)
    }

    @Test
    fun compilesRepeatBlocksIntoFlatTimelineWithCorrectOffsets() {
        val result = ErgoWorkoutParser.parse(
            """
            {
              "format": "ergo_workout",
              "version": "0.1",
              "title": "Compiled repeat workout",
              "control": {
                "initial_power_watts": 110,
                "min_power_watts": 90,
                "max_power_watts": 220,
                "signal_loss_power_watts": 100
              },
              "segments": [
                {
                  "type": "steady",
                  "duration_sec": 300,
                  "target": {
                    "metric": "power",
                    "value": 130
                  }
                },
                {
                  "type": "repeat",
                  "count": 2,
                  "steps": [
                    {
                      "type": "steady",
                      "duration_sec": 60,
                      "target": {
                        "metric": "heart_rate",
                        "range": {
                          "low": 125,
                          "high": 135
                        }
                      }
                    },
                    {
                      "type": "steady",
                      "duration_sec": 30,
                      "target": {
                        "metric": "power",
                        "value": 150
                      }
                    }
                  ]
                },
                {
                  "type": "ramp",
                  "duration_sec": 120,
                  "from_target": {
                    "metric": "power",
                    "value": 150
                  },
                  "to_target": {
                    "metric": "power",
                    "value": 180
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val success = requireSuccess(result)
        assertEquals(600, success.compiled.totalDurationSec)
        assertEquals(6, success.compiled.steps.size)
        assertEquals(
            listOf(0, 300, 360, 390, 450, 480),
            success.compiled.steps.map { it.startOffsetSec },
        )
        assertTrue(success.compiled.steps[1] is CompiledErgoWorkoutStep.HeartRateSteady)
        assertTrue(success.compiled.steps[5] is CompiledErgoWorkoutStep.PowerRamp)

        val hrStep = success.compiled.steps[1] as CompiledErgoWorkoutStep.HeartRateSteady
        assertEquals(125, hrStep.lowBpm)
        assertEquals(135, hrStep.highBpm)
        assertEquals(110, hrStep.initialPowerWatts)
        assertEquals(90, hrStep.minPowerWatts)
        assertEquals(220, hrStep.maxPowerWatts)
        assertEquals(100, hrStep.signalLossPowerWatts)

        val lastStep = success.compiled.steps[5] as CompiledErgoWorkoutStep.PowerRamp
        assertEquals(120, lastStep.durationSec)
        assertEquals(150, lastStep.fromWatts)
        assertEquals(180, lastStep.toWatts)
    }

    private fun requireSuccess(result: ErgoWorkoutParseResult): ErgoWorkoutParseResult.Success {
        return result as? ErgoWorkoutParseResult.Success
            ?: throw AssertionError("Expected success, got $result")
    }

    private fun requireFailure(result: ErgoWorkoutParseResult): ErgoWorkoutParseResult.Failure {
        return result as? ErgoWorkoutParseResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
    }
}
