package com.example.ergometerapp.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.ewo.editor.commands.SetRepeatCount
import com.ewo.editor.model.EditorDocumentFactory
import com.ewo.editor.model.EditorNodeId
import com.ewo.editor.model.EditorPreview
import com.ewo.editor.model.EditorSegment
import com.ewo.editor.model.EditorTarget
import com.example.ergometerapp.R
import com.example.ergometerapp.ewoeditor.EwoEditorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-level regression tests for EwoEditorScreen numeric input fields.
 *
 * Specifically guards that the repeat-count field retains partial/invalid intermediate
 * text without snap-resetting to the last committed count, which is the hardening
 * introduced in the ewo-v1-5-editor-surfaces feature slice.
 *
 * Scroll strategy: SelectedSegmentProperties lives in a LazyColumn item that may not be
 * composed yet. We scroll to the "Properties" section heading (a plain Text composable,
 * so reliably visible to hasText()) before interacting with the numeric fields.
 * Material3 OutlinedTextField labels are NOT accessible via hasText(), so we identify
 * the repeat-count field by its EditableText value "3" after the section is composed.
 */
class EwoEditorScreenNumericInputTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // --- Repeat count field ---

    @Test
    fun repeatCountFieldRetainsZeroWithoutDispatch() {
        val actions = mutableListOf<EwoEditorScreenAction>()
        val (snapshot, _) = snapshotWithSelectedRepeat(count = 3)

        composeRule.setContent {
            EwoEditorScreen(snapshot = snapshot, onAction = { actions += it })
        }

        scrollToPropertiesSection()

        // Type "0" — an invalid repeat count.
        composeRule.onNode(hasSetTextAction() and hasText("3"), useUnmergedTree = true)
            .assertIsDisplayed()
            .performTextReplacement("0")

        // Zero is not a positive integer — no SetRepeatCount must have been dispatched.
        composeRule.runOnIdle {
            assertTrue(
                "Expected no SetRepeatCount action for input '0', got: $actions",
                actions.none { it is EwoEditorScreenAction.Dispatch && it.command is SetRepeatCount },
            )
        }
    }

    @Test
    fun repeatCountFieldRetainsBlankWithoutDispatch() {
        val actions = mutableListOf<EwoEditorScreenAction>()
        val (snapshot, _) = snapshotWithSelectedRepeat(count = 3)

        composeRule.setContent {
            EwoEditorScreen(snapshot = snapshot, onAction = { actions += it })
        }

        scrollToPropertiesSection()

        // Clear the field — blank is not a positive integer.
        composeRule.onNode(hasSetTextAction() and hasText("3"), useUnmergedTree = true)
            .assertIsDisplayed()
            .performTextReplacement("")

        // No SetRepeatCount must have been dispatched.
        composeRule.runOnIdle {
            assertTrue(
                "Expected no SetRepeatCount action for blank input, got: $actions",
                actions.none { it is EwoEditorScreenAction.Dispatch && it.command is SetRepeatCount },
            )
        }
    }

    @Test
    fun repeatCountFieldDispatchesSetRepeatCountForValidPositiveInteger() {
        val actions = mutableListOf<EwoEditorScreenAction>()
        val (snapshot, repeatNodeId) = snapshotWithSelectedRepeat(count = 3)

        composeRule.setContent {
            EwoEditorScreen(snapshot = snapshot, onAction = { actions += it })
        }

        scrollToPropertiesSection()

        // Type a valid positive integer.
        composeRule.onNode(hasSetTextAction() and hasText("3"), useUnmergedTree = true)
            .assertIsDisplayed()
            .performTextReplacement("5")

        composeRule.runOnIdle {
            assertEquals(
                listOf(EwoEditorScreenAction.Dispatch(SetRepeatCount(repeatNodeId, 5))),
                actions.filter { it is EwoEditorScreenAction.Dispatch && it.command is SetRepeatCount },
            )
        }
    }

    // --- Helpers ---

    /**
     * Scrolls the LazyColumn until the SelectedSegmentProperties section heading is
     * visible, which forces composition of the entire properties item (including the
     * numeric fields). Must be called before interacting with the repeat-count field.
     *
     * We anchor on the "Properties" plain-Text heading rather than an OutlinedTextField
     * label because Material3 OutlinedTextField labels are not exposed via hasText().
     */
    private fun scrollToPropertiesSection() {
        val propertiesHeading = composeRule.activity.getString(R.string.ewo_editor_properties_title)
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(propertiesHeading))
    }

    /**
     * Returns a snapshot whose document contains a single Repeat segment with the
     * given [count] and that segment selected, together with the repeat's [EditorNodeId].
     */
    private fun snapshotWithSelectedRepeat(count: Int): Pair<EwoEditorSnapshot, EditorNodeId> {
        val repeatNodeId = EditorNodeId("node_2")
        val childNodeId = EditorNodeId("node_3")

        val baseDoc = EditorDocumentFactory.empty()
        val repeatSegment = EditorSegment.Repeat(
            nodeId = repeatNodeId,
            segmentId = "main_set",
            label = "Main set",
            note = null,
            messages = emptyList(),
            count = count,
            segments = listOf(
                EditorSegment.Steady(
                    nodeId = childNodeId,
                    segmentId = "interval",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = 60,
                    target = EditorTarget.Power(watts = 250),
                    cadence = null,
                ),
            ),
        )

        val doc = baseDoc.copy(
            segments = listOf(repeatSegment),
            selectedNodeId = repeatNodeId,
        )

        val snapshot = EwoEditorSnapshot(
            document = doc,
            preview = EditorPreview(
                steps = emptyList(),
                totalDurationSec = 0,
                intensityFactor = null,
                tss = null,
                sanityWarnings = emptyList(),
                compileErrors = emptyList(),
            ),
            canUndo = false,
            canRedo = false,
            statusMessage = "Ready",
            currentFileName = null,
            ftpWatts = null,
            hrMaxBpm = null,
            restingHrBpm = null,
            lthrBpm = null,
            hasClipboard = false,
        )

        return snapshot to repeatNodeId
    }
}
