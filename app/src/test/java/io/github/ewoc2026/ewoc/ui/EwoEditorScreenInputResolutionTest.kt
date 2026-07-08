package io.github.ewoc2026.ewoc.ui

import com.ewo.core.HrReference
import com.ewo.editor.commands.SetRepeatCount
import com.ewo.editor.commands.SetSegmentDuration
import com.ewo.editor.commands.SetSegmentLabel
import com.ewo.editor.commands.SetSegmentNote
import com.ewo.editor.commands.SetSteadyTarget
import com.ewo.editor.model.EditorCadenceRange
import com.ewo.editor.model.EditorTarget
import com.ewo.editor.model.EditorNodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EwoEditorScreenInputResolutionTest {
    private val nodeId = EditorNodeId("segment_1")

    @Test
    fun resolveCadenceInputClearsWhenBothFieldsAreBlank() {
        val resolution = resolveCadenceInput(lowText = "", highText = "")

        assertEquals(CadenceInputResolution.Clear, resolution)
    }

    @Test
    fun resolveCadenceInputKeepsExistingWhileOneBoundIsStillMissing() {
        val resolution = resolveCadenceInput(lowText = "85", highText = "")

        assertEquals(CadenceInputResolution.KeepExisting, resolution)
    }

    @Test
    fun resolveCadenceInputUpdatesWhenBothBoundsAreValid() {
        val resolution = resolveCadenceInput(lowText = "85", highText = "95")

        assertEquals(
            CadenceInputResolution.Update(EditorCadenceRange(low = 85, high = 95)),
            resolution,
        )
    }

    @Test
    fun resolveCadenceInputKeepsExistingForNonNumericInput() {
        val resolution = resolveCadenceInput(lowText = "tempo", highText = "95")

        assertEquals(CadenceInputResolution.KeepExisting, resolution)
    }

    @Test
    fun setSegmentLabelActionNormalizesBlankInputToNull() {
        val action = setSegmentLabelAction(nodeId, "   ")

        assertEquals(
            EwoEditorScreenAction.Dispatch(SetSegmentLabel(nodeId, null)),
            action,
        )
    }

    @Test
    fun setSegmentNoteActionPreservesNonBlankInput() {
        val action = setSegmentNoteAction(nodeId, "Recover before next block")

        assertEquals(
            EwoEditorScreenAction.Dispatch(SetSegmentNote(nodeId, "Recover before next block")),
            action,
        )
    }

    @Test
    fun resolvePositiveIntegerInputKeepsExistingForBlankZeroAndInvalidText() {
        assertEquals(PositiveIntegerInputResolution.KeepExisting, resolvePositiveIntegerInput(""))
        assertEquals(PositiveIntegerInputResolution.KeepExisting, resolvePositiveIntegerInput("0"))
        assertEquals(PositiveIntegerInputResolution.KeepExisting, resolvePositiveIntegerInput("tempo"))
    }

    @Test
    fun resolvePositiveIntegerInputUpdatesForPositiveInteger() {
        assertEquals(PositiveIntegerInputResolution.Update(4), resolvePositiveIntegerInput("4"))
    }

    @Test
    fun resolveRepeatCountActionIgnoresBlankZeroAndInvalidInput() {
        assertNull(resolveRepeatCountAction(nodeId, ""))
        assertNull(resolveRepeatCountAction(nodeId, "0"))
        assertNull(resolveRepeatCountAction(nodeId, "tempo"))
    }

    @Test
    fun resolveRepeatCountActionReturnsDispatchForPositiveInteger() {
        val action = resolveRepeatCountAction(nodeId, "4")

        assertEquals(
            EwoEditorScreenAction.Dispatch(SetRepeatCount(nodeId, 4)),
            action,
        )
    }

    @Test
    fun resolveIntegerInputKeepsExistingForBlankAndInvalidText() {
        assertEquals(IntegerInputResolution.KeepExisting, resolveIntegerInput(""))
        assertEquals(IntegerInputResolution.KeepExisting, resolveIntegerInput("tempo"))
    }

    @Test
    fun resolveIntegerInputUpdatesForValidInteger() {
        assertEquals(IntegerInputResolution.Update(180), resolveIntegerInput("180"))
    }

    @Test
    fun resolveOptionalIntegerInputClearsForBlankAndKeepsInvalidText() {
        assertEquals(OptionalIntegerInputResolution.Clear, resolveOptionalIntegerInput(" "))
        assertEquals(OptionalIntegerInputResolution.KeepExisting, resolveOptionalIntegerInput("tempo"))
    }

    @Test
    fun resolveOptionalIntegerInputUpdatesForValidInteger() {
        assertEquals(OptionalIntegerInputResolution.Update(250), resolveOptionalIntegerInput("250"))
    }

    @Test
    fun resolveOptionalIntegerActionBuildsClearActionForBlankInput() {
        val action = resolveOptionalIntegerAction(" ", EwoEditorScreenAction::SetFtp)

        assertEquals(EwoEditorScreenAction.SetFtp(null), action)
    }

    @Test
    fun resolveOptionalIntegerActionReturnsNullForInvalidInput() {
        val action = resolveOptionalIntegerAction("tempo", EwoEditorScreenAction::SetHrMax)

        assertNull(action)
    }

    @Test
    fun resolveSegmentDurationActionReturnsDispatchForValidInteger() {
        val action = resolveSegmentDurationAction(nodeId, "300")

        assertEquals(
            EwoEditorScreenAction.Dispatch(SetSegmentDuration(nodeId, 300)),
            action,
        )
    }

    @Test
    fun resolveSegmentDurationActionIgnoresBlankInput() {
        val action = resolveSegmentDurationAction(nodeId, "")

        assertNull(action)
    }

    @Test
    fun resolveSteadyTargetPowerActionReturnsDispatchForValidInteger() {
        val action = resolveSteadyTargetPowerAction(nodeId, "260")

        assertEquals(
            EwoEditorScreenAction.Dispatch(SetSteadyTarget(nodeId, EditorTarget.Power(260))),
            action,
        )
    }

    @Test
    fun resolveSteadyTargetPowerActionIgnoresInvalidInput() {
        val action = resolveSteadyTargetPowerAction(nodeId, "tempo")

        assertNull(action)
    }

    @Test
    fun resolveSteadyTargetFtpPercentActionReturnsDispatchForValidInteger() {
        val action = resolveSteadyTargetFtpPercentAction(nodeId, "95")

        assertEquals(
            EwoEditorScreenAction.Dispatch(SetSteadyTarget(nodeId, EditorTarget.FtpPercent(0.95))),
            action,
        )
    }

    @Test
    fun resolveSteadyTargetFtpPercentActionIgnoresBlankAndInvalidInput() {
        assertNull(resolveSteadyTargetFtpPercentAction(nodeId, ""))
        assertNull(resolveSteadyTargetFtpPercentAction(nodeId, "tempo"))
    }

    @Test
    fun resolveIntBandInputUpdatesForValidBand() {
        assertEquals(
            IntBandInputResolution.Update(low = 130, high = 150),
            resolveIntBandInput("130-150"),
        )
    }

    @Test
    fun resolveIntBandInputKeepsExistingForBlankInvalidAndDescendingBand() {
        assertEquals(IntBandInputResolution.KeepExisting, resolveIntBandInput(""))
        assertEquals(IntBandInputResolution.KeepExisting, resolveIntBandInput("tempo"))
        assertEquals(IntBandInputResolution.KeepExisting, resolveIntBandInput("150-130"))
    }

    @Test
    fun resolveSteadyTargetHeartRateActionReturnsDispatchForValidBand() {
        val action = resolveSteadyTargetHeartRateAction(nodeId, "130-150")

        assertEquals(
            EwoEditorScreenAction.Dispatch(SetSteadyTarget(nodeId, EditorTarget.HeartRate(130, 150))),
            action,
        )
    }

    @Test
    fun resolveSteadyTargetHeartRateRelativeActionReturnsDispatchForValidBand() {
        val action = resolveSteadyTargetHeartRateRelativeAction(
            nodeId = nodeId,
            text = "72-80",
            reference = HrReference.HR_MAX,
        )

        assertEquals(
            EwoEditorScreenAction.Dispatch(
                SetSteadyTarget(
                    nodeId,
                    EditorTarget.HeartRateRelative(
                        reference = HrReference.HR_MAX,
                        lowFraction = 0.72,
                        highFraction = 0.80,
                    ),
                ),
            ),
            action,
        )
    }
}
