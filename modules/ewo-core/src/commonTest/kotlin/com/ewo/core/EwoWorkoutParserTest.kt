package com.ewo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EwoWorkoutParserTest {
    @Test
    fun parsesMinimalValidPowerSteadyWorkout() {
        val result = EwoEngine.parse(
            """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "  Minimal power steady  ",
              "messages": [
                {
                  "kind": "intro",
                  "when": "start",
                  "text": {
                    "default": "  Get ready.  "
                  }
                }
              ],
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 900,
                  "target": {
                    "metric": "power",
                    "value": 150
                  },
                  "messages": [
                    {
                      "kind": "instruction",
                      "when": "start",
                      "text": {
                        "default": "  Settle into the effort.  "
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val success = requireSuccess(result)
        assertEquals("  Minimal power steady  ", success.parsed.title)
        assertEquals("Minimal power steady", success.normalized.title)
        assertEquals("Get ready.", success.normalized.messages.single().text.defaultText)
        assertEquals("Settle into the effort.", success.compiled.steps.single().messages.single().text.defaultText)
        assertEquals(900, success.compiled.totalDurationSec)

        val compiledStep = success.compiled.steps.single() as CompiledEwoWorkoutStep.PowerSteady
        assertEquals(0, compiledStep.stepIndex)
        assertEquals(0, compiledStep.startOffsetSec)
        assertEquals(900, compiledStep.durationSec)
        assertEquals(150, compiledStep.watts)
        assertEquals("warmup", compiledStep.origin.sourceSegmentId)
        assertEquals(null, compiledStep.origin.enclosingRepeatSegmentId)
        assertEquals(null, compiledStep.origin.repeatIterationIndex)
    }

    @Test
    fun rejectsHeartRateWorkoutWithoutControl() {
        val failure = requireFailure(
            EwoEngine.parse(
                """
                {
                  "format": "ewo",
                  "version": "1.0",
                  "title": "HR without control",
                  "segments": [
                    {
                      "id": "threshold",
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
            EwoWorkoutValidationErrorCode.CONTROL_REQUIRED_FOR_HEART_RATE,
            failure.error.code,
        )
        assertEquals("$.control", failure.error.fieldPath)
    }

    @Test
    fun rejectsInvalidHeartRateRange() {
        val failure = requireFailure(
            EwoEngine.parse(
                """
                {
                  "format": "ewo",
                  "version": "1.0",
                  "title": "Bad HR range",
                  "control": {
                    "initial_power_watts": 110,
                    "min_power_watts": 90,
                    "max_power_watts": 220,
                    "signal_loss_power_watts": 100,
                    "hr_upper_cap_bpm": 160
                  },
                  "segments": [
                    {
                      "id": "hr_hold",
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

        assertEquals(EwoWorkoutValidationErrorCode.INVALID_HEART_RATE_RANGE, failure.error.code)
        assertEquals("$.segments[0].target.range", failure.error.fieldPath)
    }

    @Test
    fun rejectsRepeatWithCountOne() {
        val failure = requireFailure(
            EwoEngine.parse(
                """
                {
                  "format": "ewo",
                  "version": "1.0",
                  "title": "Count one not allowed",
                  "segments": [
                    {
                      "id": "block",
                      "type": "repeat",
                      "count": 1,
                      "segments": [
                        {
                          "id": "effort",
                          "type": "steady",
                          "duration_sec": 120,
                          "target": { "metric": "power", "value": 250 }
                        },
                        {
                          "id": "recover",
                          "type": "steady",
                          "duration_sec": 60,
                          "target": { "metric": "power", "value": 100 }
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(EwoWorkoutValidationErrorCode.INVALID_REPEAT_COUNT, failure.error.code)
        assertEquals("$.segments[0].count", failure.error.fieldPath)
    }

    @Test
    fun rejectsDuplicateSegmentIdsAcrossWorkoutTree() {
        val failure = requireFailure(
            EwoEngine.parse(
                """
                {
                  "format": "ewo",
                  "version": "1.0",
                  "title": "Duplicate ids",
                  "segments": [
                    {
                      "id": "warmup",
                      "type": "steady",
                      "duration_sec": 60,
                      "target": {
                        "metric": "power",
                        "value": 100
                      }
                    },
                    {
                      "id": "repeat_block",
                      "type": "repeat",
                      "count": 2,
                      "segments": [
                        {
                          "id": "warmup",
                          "type": "steady",
                          "duration_sec": 30,
                          "target": {
                            "metric": "power",
                            "value": 120
                          }
                        },
                        {
                          "id": "recover",
                          "type": "steady",
                          "duration_sec": 30,
                          "target": {
                            "metric": "power",
                            "value": 100
                          }
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(EwoWorkoutValidationErrorCode.DUPLICATE_SEGMENT_ID, failure.error.code)
        assertEquals("$.segments[1].segments[0].id", failure.error.fieldPath)
    }

    @Test
    fun parsesV15MetadataFreeRideAndStructuredMessageTiming() {
        val result = EwoEngine.parse(
            """
            {
              "format": "ewo",
              "version": "1.5",
              "uid": "workout-001",
              "revision": 3,
              "title": "Free Ride Builder",
              "messages": [
                {
                  "kind": "intro",
                  "when": { "anchor": "end", "offset_sec": -15 },
                  "text": { "default": "Cooldown soon." }
                }
              ],
              "segments": [
                {
                  "id": "legs_open",
                  "type": "free_ride",
                  "label": "Legs Open",
                  "note": "Let the rider choose resistance.",
                  "duration_sec": 120,
                  "messages": [
                    {
                      "kind": "instruction",
                      "when": { "anchor": "end", "offset_sec": -5 },
                      "text": { "default": "Prepare to transition." }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val success = requireSuccess(result)
        assertEquals("workout-001", success.parsed.uid)
        assertEquals(3, success.parsed.revision)
        assertEquals(EwoMessageAnchor.END, success.normalized.messages.single().timing.anchor)
        assertEquals(-15, success.normalized.messages.single().timing.offsetSec)

        val normalizedSegment = success.normalized.segments.single() as NormalizedEwoWorkoutSegment.FreeRide
        assertEquals("Legs Open", normalizedSegment.label)
        assertEquals("Let the rider choose resistance.", normalizedSegment.note)

        val compiledStep = success.compiled.steps.single() as CompiledEwoWorkoutStep.FreeRide
        assertEquals("legs_open", compiledStep.origin.sourceSegmentId)
        assertEquals("Legs Open", compiledStep.origin.sourceSegmentLabel)
        assertEquals("Let the rider choose resistance.", compiledStep.origin.sourceSegmentNote)
        assertEquals(EwoMessageAnchor.END, compiledStep.messages.single().timing.anchor)
        assertEquals(-5, compiledStep.messages.single().timing.offsetSec)
    }

    @Test
    fun parsesV16RootLocalizedMetadata() {
        val result = EwoEngine.parse(
            """
            {
              "format": "ewo",
              "version": "1.6",
              "title": "Threshold Builder",
              "description": "3 x 8 min near threshold with controlled recoveries.",
              "title_localized": {
                "default": "Threshold Builder",
                "translations": {
                  "fi": "Kynnystehon rakentaja"
                }
              },
              "description_localized": {
                "default": "3 x 8 min near threshold with controlled recoveries.",
                "translations": {
                  "fi": "3 x 8 min lahella kynnysta hallituilla palautuksilla."
                }
              },
              "segments": [
                {
                  "id": "warmup",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": {
                    "metric": "power",
                    "value": 150
                  }
                }
              ]
            }
            """.trimIndent(),
            EwoCompileContext(ftpWatts = 250),
        )

        val success = requireSuccess(result)
        assertEquals("Threshold Builder", success.normalized.titleLocalized?.defaultText)
        assertEquals("Kynnystehon rakentaja", success.normalized.titleLocalized?.translations?.get("fi"))
        assertEquals(
            "3 x 8 min near threshold with controlled recoveries.",
            success.compiled.descriptionLocalized?.defaultText,
        )
        assertEquals(
            "3 x 8 min lahella kynnysta hallituilla palautuksilla.",
            success.compiled.descriptionLocalized?.translations?.get("fi"),
        )
    }

    @Test
    fun rejectsRootLocalizedMetadataBeforeV16() {
        val failure = requireFailure(
            EwoEngine.parse(
                """
                {
                  "format": "ewo",
                  "version": "1.5",
                  "title": "Threshold Builder",
                  "title_localized": {
                    "default": "Threshold Builder"
                  },
                  "segments": [
                    {
                      "id": "warmup",
                      "type": "steady",
                      "duration_sec": 300,
                      "target": {
                        "metric": "power",
                        "value": 150
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(EwoWorkoutValidationErrorCode.UNKNOWN_FIELD, failure.error.code)
        assertEquals("$.title_localized", failure.error.fieldPath)
    }

    @Test
    fun rejectsDescriptionLocalizedWithoutRootDescription() {
        val failure = requireFailure(
            EwoEngine.parse(
                """
                {
                  "format": "ewo",
                  "version": "1.6",
                  "title": "Threshold Builder",
                  "description_localized": {
                    "default": "Localized description without fallback."
                  },
                  "segments": [
                    {
                      "id": "warmup",
                      "type": "steady",
                      "duration_sec": 300,
                      "target": {
                        "metric": "power",
                        "value": 150
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(EwoWorkoutValidationErrorCode.INVALID_LOCALIZED_TEXT, failure.error.code)
        assertEquals("$.description_localized", failure.error.fieldPath)
    }

    @Test
    fun rejectsTitleLocalizedDefaultMismatch() {
        val failure = requireFailure(
            EwoEngine.parse(
                """
                {
                  "format": "ewo",
                  "version": "1.6",
                  "title": "Threshold Builder",
                  "title_localized": {
                    "default": "Tempo Builder"
                  },
                  "segments": [
                    {
                      "id": "warmup",
                      "type": "steady",
                      "duration_sec": 300,
                      "target": {
                        "metric": "power",
                        "value": 150
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(EwoWorkoutValidationErrorCode.INVALID_LOCALIZED_TEXT, failure.error.code)
        assertEquals("$.title_localized.default", failure.error.fieldPath)
    }

    @Test
    fun compilesRepeatBlocksIntoFlatTimelineWithCorrectOffsetsAndOrigins() {
        val result = EwoEngine.parse(
            """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Compiled repeat workout",
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
                  "target": {
                    "metric": "power",
                    "value": 130
                  }
                },
                {
                  "id": "main_set",
                  "type": "repeat",
                  "count": 2,
                  "segments": [
                    {
                      "id": "hr_hold",
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
                      "id": "recover",
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
                  "id": "finish",
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
        assertTrue(success.compiled.steps[1] is CompiledEwoWorkoutStep.HeartRateSteady)
        assertTrue(success.compiled.steps[5] is CompiledEwoWorkoutStep.PowerRamp)

        val hrStep = success.compiled.steps[1] as CompiledEwoWorkoutStep.HeartRateSteady
        assertEquals(125, hrStep.lowBpm)
        assertEquals(135, hrStep.highBpm)
        assertEquals(110, hrStep.initialPowerWatts)
        assertEquals(90, hrStep.minPowerWatts)
        assertEquals(220, hrStep.maxPowerWatts)
        assertEquals(100, hrStep.signalLossPowerWatts)
        assertEquals(160, hrStep.hrUpperCapBpm)
        assertEquals("hr_hold", hrStep.origin.sourceSegmentId)
        assertEquals("main_set", hrStep.origin.enclosingRepeatSegmentId)
        assertEquals(0, hrStep.origin.repeatIterationIndex)

        val recoverStep = success.compiled.steps[4] as CompiledEwoWorkoutStep.PowerSteady
        assertEquals("recover", recoverStep.origin.sourceSegmentId)
        assertEquals("main_set", recoverStep.origin.enclosingRepeatSegmentId)
        assertEquals(1, recoverStep.origin.repeatIterationIndex)

        val lastStep = success.compiled.steps[5] as CompiledEwoWorkoutStep.PowerRamp
        assertEquals(120, lastStep.durationSec)
        assertEquals(150, lastStep.fromWatts)
        assertEquals(180, lastStep.toWatts)
        assertEquals("finish", lastStep.origin.sourceSegmentId)
    }

    @Test
    fun ewoVersionParsesAndCompares() {
        val v10 = EwoVersion.parse("1.0")
        val v11 = EwoVersion.parse("1.1")
        val v13 = EwoVersion.parse("1.3")
        assertTrue(v10 < v11)
        assertTrue(v11 < v13)
        assertEquals("1.0", v10.toString())
        assertEquals(EwoVersion(1, 3), v13)
    }

    // --- HR Relative Tests ---

    @Test
    fun hrRelativeWithHrMaxResolvesToCorrectBpm() {
        val result = EwoEngine.parse(
            HR_RELATIVE_HR_MAX_WORKOUT,
            EwoCompileContext(hrMaxBpm = 190),
        )
        val success = requireCompiled(result)
        val step = success.compiled.steps[1] as CompiledEwoWorkoutStep.HeartRateSteady
        // 190 * 0.72 = 136.8 → 137, 190 * 0.80 = 152
        assertEquals(137, step.lowBpm)
        assertEquals(152, step.highBpm)
    }

    @Test
    fun hrRelativeWithHeartRateReserveResolvesViaKarvonen() {
        val result = EwoEngine.parse(
            hrRelativeWorkout("heart_rate_reserve"),
            EwoCompileContext(hrMaxBpm = 190, restingHrBpm = 60),
        )
        val success = requireCompiled(result)
        val step = success.compiled.steps[1] as CompiledEwoWorkoutStep.HeartRateSteady
        // reserve = 130; low = 60 + 130*0.72 = 60 + 93.6 = 153.6 → 154
        // high = 60 + 130*0.80 = 60 + 104 = 164
        assertEquals(154, step.lowBpm)
        assertEquals(164, step.highBpm)
    }

    @Test
    fun hrRelativeWithLthrResolvesToCorrectBpm() {
        val result = EwoEngine.parse(
            hrRelativeWorkout("lthr"),
            EwoCompileContext(lthrBpm = 170),
        )
        val success = requireCompiled(result)
        val step = success.compiled.steps[1] as CompiledEwoWorkoutStep.HeartRateSteady
        // 170 * 0.72 = 122.4 → 122, 170 * 0.80 = 136
        assertEquals(122, step.lowBpm)
        assertEquals(136, step.highBpm)
    }

    @Test
    fun hrRelativeInRepeatBlock() {
        val result = EwoEngine.parse(
            HR_RELATIVE_REPEAT_WORKOUT,
            EwoCompileContext(hrMaxBpm = 200),
        )
        val success = requireCompiled(result)
        // warmup + 2*(hr_rel + recover) = 5 steps
        assertEquals(5, success.compiled.steps.size)
        val step = success.compiled.steps[1] as CompiledEwoWorkoutStep.HeartRateSteady
        assertEquals(144, step.lowBpm) // 200 * 0.72
        assertEquals(160, step.highBpm) // 200 * 0.80
        assertEquals("hr_effort", step.origin.sourceSegmentId)
        assertEquals("main_set", step.origin.enclosingRepeatSegmentId)
    }

    @Test
    fun hrRelativeRejectedInV13() {
        val failure = requireFailure(
            EwoEngine.parse(HR_RELATIVE_V13_WORKOUT),
        )
        assertEquals(EwoWorkoutValidationErrorCode.INVALID_TARGET_METRIC, failure.error.code)
    }

    @Test
    fun hrRelativeUnknownReference() {
        val failure = requireFailure(
            EwoEngine.parse(hrRelativeWorkout("bogus")),
        )
        assertEquals(EwoWorkoutValidationErrorCode.INVALID_HR_RELATIVE_REFERENCE, failure.error.code)
    }

    @Test
    fun hrRelativeInvalidRangeLowGteHigh() {
        val failure = requireFailure(
            EwoEngine.parse(HR_RELATIVE_BAD_RANGE_WORKOUT),
        )
        assertEquals(EwoWorkoutValidationErrorCode.INVALID_HR_RELATIVE_RANGE, failure.error.code)
    }

    @Test
    fun hrRelativeMissingProfileReturnsNeedsCompileContext() {
        val result = EwoEngine.parse(
            HR_RELATIVE_HR_MAX_WORKOUT,
            EwoCompileContext(), // no HR profile
        )
        assertIs<EwoWorkoutParseResult.Success.NeedsCompileContext>(result)
        assertEquals(1, result.compileErrors.size)
        assertEquals(EwoCompileErrorCode.MISSING_HR_MAX, result.compileErrors[0].code)
    }

    @Test
    fun ftpPercentMissingProfileReturnsNeedsCompileContext() {
        val result = EwoEngine.parse(
            FTP_PERCENT_WORKOUT,
            EwoCompileContext(),
        )
        assertIs<EwoWorkoutParseResult.Success.NeedsCompileContext>(result)
        assertEquals(1, result.compileErrors.size)
        assertEquals(EwoCompileErrorCode.MISSING_FTP, result.compileErrors[0].code)
    }

    private fun requireCompiled(result: EwoWorkoutParseResult): EwoWorkoutParseResult.Success.Compiled {
        return result as? EwoWorkoutParseResult.Success.Compiled
            ?: throw AssertionError("Expected Success.Compiled, got $result")
    }

    private fun requireSuccess(result: EwoWorkoutParseResult): EwoWorkoutParseResult.Success.Compiled {
        return requireCompiled(result)
    }

    private fun requireFailure(result: EwoWorkoutParseResult): EwoWorkoutParseResult.Failure {
        return result as? EwoWorkoutParseResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
    }

    companion object {
        private val CONTROL_BLOCK = """
            "control": {
                "initial_power_watts": 110,
                "min_power_watts": 90,
                "max_power_watts": 220,
                "signal_loss_power_watts": 100,
                "hr_upper_cap_bpm": 160
            }
        """.trimIndent()

        private val HR_RELATIVE_HR_MAX_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.4",
              "title": "HR Relative HRmax",
              $CONTROL_BLOCK,
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

        private val HR_RELATIVE_REPEAT_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.4",
              "title": "HR Relative Repeat",
              $CONTROL_BLOCK,
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
                      "id": "hr_effort",
                      "type": "steady",
                      "duration_sec": 300,
                      "target": {
                        "metric": "heart_rate_relative",
                        "reference": "hr_max",
                        "range": { "low": 0.72, "high": 0.80 }
                      }
                    },
                    {
                      "id": "recover",
                      "type": "steady",
                      "duration_sec": 120,
                      "target": { "metric": "power", "value": 100 }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        private val FTP_PERCENT_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.5",
              "title": "FTP Percent",
              "segments": [
                {
                  "id": "ftp_step",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": { "metric": "ftp_percent", "value": 0.75 }
                }
              ]
            }
        """.trimIndent()

        private val HR_RELATIVE_V13_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.3",
              "title": "HR Relative in v1.3",
              $CONTROL_BLOCK,
              "segments": [
                {
                  "id": "zone",
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

        private val HR_RELATIVE_BAD_RANGE_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.4",
              "title": "Bad HR relative range",
              $CONTROL_BLOCK,
              "segments": [
                {
                  "id": "zone",
                  "type": "steady",
                  "duration_sec": 600,
                  "target": {
                    "metric": "heart_rate_relative",
                    "reference": "hr_max",
                    "range": { "low": 0.80, "high": 0.72 }
                  }
                }
              ]
            }
        """.trimIndent()

        private fun hrRelativeWorkout(reference: String) = """
            {
              "format": "ewo",
              "version": "1.4",
              "title": "HR Relative $reference",
              $CONTROL_BLOCK,
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
                    "reference": "$reference",
                    "range": { "low": 0.72, "high": 0.80 }
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
