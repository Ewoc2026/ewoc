package io.github.ewoc2026.ewoc

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that critical top-level screen state survives orientation-driven activity recreation.
 */
class MainActivityRecreationRotationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun menuAndSessionAnchorsRemainVisibleAcrossRotationRecreation() {
        try {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.uiState.screen.value = AppScreen.MENU
                viewModel.uiState.ftmsReady.value = false
                viewModel.uiState.ftmsControlGranted.value = false
            }
            composeRule.waitForIdle()
            assertSessionState(
                expectedScreen = AppScreen.MENU,
                expectedFtmsReady = false,
                expectedControlGranted = false,
            )

            rotateAndWait(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            assertSessionState(
                expectedScreen = AppScreen.MENU,
                expectedFtmsReady = false,
                expectedControlGranted = false,
            )

            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.uiState.screen.value = AppScreen.SESSION
                viewModel.uiState.ftmsReady.value = true
                viewModel.uiState.ftmsControlGranted.value = true
            }
            composeRule.waitForIdle()
            assertSessionState(
                expectedScreen = AppScreen.SESSION,
                expectedFtmsReady = true,
                expectedControlGranted = true,
            )

            rotateAndWait(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            assertSessionState(
                expectedScreen = AppScreen.SESSION,
                expectedFtmsReady = true,
                expectedControlGranted = true,
            )
        } finally {
            composeRule.runOnUiThread {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun rotateAndWait(requestedOrientation: Int) {
        composeRule.runOnUiThread {
            composeRule.activity.requestedOrientation = requestedOrientation
        }
        composeRule.waitForIdle()
    }

    private fun currentViewModel(): MainViewModel {
        return ViewModelProvider(composeRule.activity)[MainViewModel::class.java]
    }

    private fun assertSessionState(
        expectedScreen: AppScreen,
        expectedFtmsReady: Boolean,
        expectedControlGranted: Boolean,
    ) {
        composeRule.runOnIdle {
            val viewModel = currentViewModel()
            assertEquals(expectedScreen, viewModel.uiState.screen.value)
            assertEquals(expectedFtmsReady, viewModel.uiState.ftmsReady.value)
            assertEquals(expectedControlGranted, viewModel.uiState.ftmsControlGranted.value)
        }
    }
}
