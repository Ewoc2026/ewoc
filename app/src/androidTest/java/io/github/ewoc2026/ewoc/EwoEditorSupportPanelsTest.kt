package io.github.ewoc2026.ewoc

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.android.ComposeNotIdleException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

/**
 * Guards that Android support panels stay collapsed by default when the real app
 * opens the editor, so the main editing flow keeps visual priority on compact layouts.
 */
class EwoEditorSupportPanelsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun athleteProfileSupportPanelExpandsOnlyAfterToggle() {
        try {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.ewoEditorCoordinator.newDocument()
                viewModel.ewoEditorSnapshotState.value = viewModel.ewoEditorCoordinator.snapshot()
                viewModel.onOpenEwoEditor()
            }
            waitUntilScreen(screen = AppScreen.EWO_EDITOR)

            val athleteProfileTitle =
                composeRule.activity.getString(R.string.ewo_editor_athlete_profile_title)
            val showAthleteProfile = composeRule.activity.getString(
                R.string.ewo_editor_show_section,
                athleteProfileTitle,
            )

            composeRule.onNodeWithText(athleteProfileTitle).performScrollTo().assertIsDisplayed()
            composeRule.onAllNodesWithTag("ewo_editor_support_panel_athlete_content").assertCountEquals(0)

            composeRule.onNodeWithContentDescription(showAthleteProfile).performClick()
            composeRule.onAllNodesWithTag("ewo_editor_support_panel_athlete_content").assertCountEquals(1)
        } finally {
            composeRule.runOnUiThread {
                currentViewModel().onBackToMenu()
            }
        }
    }

    private fun waitUntilScreen(
        screen: AppScreen,
        timeoutMillis: Long = 20_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            currentViewModel().uiState.screen.value == screen
        }
    }

    private fun currentViewModel(): MainViewModel {
        return ViewModelProvider(composeRule.activity)[MainViewModel::class.java]
    }
}
