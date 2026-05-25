package com.example.ergometerapp.ui

import com.ewo.editor.model.EditorDocumentFactory
import com.ewo.editor.model.EditorPreview
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.SessionSetupMode
import com.example.ergometerapp.SessionLifecycleState
import com.example.ergometerapp.ewoeditor.EwoEditorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityUiModelFactoryTest {
    @Test
    fun menuScreensUseMenuAssistantStateAndTemplateKey() {
        val model = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.MENU,
                aiMenuAssistantMessage = "Menu guidance",
                aiMenuAssistantIsError = true,
                aiMenuAssistantTemplateKey = "menu-template",
                aiSessionAssistantMessage = "Session guidance",
                aiSummaryAssistantMessage = "Summary guidance",
            ),
        )

        assertEquals("Menu guidance", model.menuState.aiAssistantMessage)
        assertTrue(model.menuState.aiAssistantIsError)
        assertEquals("menu-template", model.menuState.aiAssistantTemplateKey)
    }

    @Test
    fun sessionScreensUseSessionAssistantStateWithoutTemplateKey() {
        val model = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.SESSION,
                aiMenuAssistantMessage = "Menu guidance",
                aiMenuAssistantTemplateKey = "menu-template",
                aiSessionAssistantMessage = "Session guidance",
                aiSessionAssistantIsError = true,
                sessionLifecycleState = SessionLifecycleState.RUNNING,
            ),
        )

        assertEquals("Session guidance", model.menuState.aiAssistantMessage)
        assertTrue(model.menuState.aiAssistantIsError)
        assertNull(model.menuState.aiAssistantTemplateKey)
    }

    @Test
    fun connectionFlagsTreatReachabilityAndReadySignalsAsKnownConnections() {
        val reachableModel = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.MENU,
                ftmsSelected = true,
                ftmsDeviceReachable = true,
                hrSelected = true,
                hrDeviceReachable = true,
            ),
        )
        val readyModel = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.MENU,
                ftmsReady = true,
                hrSelected = true,
            ),
        )
        val unknownModel = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.MENU,
                ftmsSelected = true,
                hrSelected = true,
            ),
        )

        assertTrue(reachableModel.menuState.ftmsConnected)
        assertTrue(reachableModel.menuState.ftmsConnectionKnown)
        assertTrue(reachableModel.menuState.hrConnected)
        assertTrue(reachableModel.menuState.hrConnectionKnown)

        assertTrue(readyModel.menuState.ftmsConnected)
        assertTrue(readyModel.menuState.ftmsConnectionKnown)
        assertFalse(readyModel.menuState.hrConnected)
        assertFalse(readyModel.menuState.hrConnectionKnown)

        assertFalse(unknownModel.menuState.ftmsConnected)
        assertFalse(unknownModel.menuState.ftmsConnectionKnown)
        assertFalse(unknownModel.menuState.hrConnected)
        assertFalse(unknownModel.menuState.hrConnectionKnown)
    }

    @Test
    fun sessionDurationSecondsPassesThroughToModel() {
        val model = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.SESSION,
                sessionDurationSeconds = 142,
                sessionLifecycleState = SessionLifecycleState.RUNNING,
            ),
        )

        assertEquals(142, model.sessionDurationSeconds)
    }

    @Test
    fun sessionDurationSecondsDefaultsToZero() {
        val model = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.SESSION,
                sessionLifecycleState = SessionLifecycleState.RUNNING,
            ),
        )

        assertEquals(0, model.sessionDurationSeconds)
    }

    @Test
    fun selectedSessionSetupModePassesThroughToMenuState() {
        val model = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.SESSION,
                selectedSessionSetupMode = SessionSetupMode.TELEMETRY_ONLY,
                sessionLifecycleState = SessionLifecycleState.RUNNING,
            ),
        )

        assertEquals(SessionSetupMode.TELEMETRY_ONLY, model.menuState.selectedSessionSetupMode)
    }

    @Test
    fun postWorkoutContinuationHandoffKeepsSessionScreenVisibleAcrossMenu() {
        val model = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.MENU,
                postWorkoutContinuationHandoffVisible = true,
                sessionLifecycleState = SessionLifecycleState.CONNECTING,
            ),
        )

        assertEquals(AppScreen.SESSION, model.screen)
        assertTrue(model.postWorkoutContinuationHandoffVisible)
    }

    @Test
    fun postWorkoutContinuationHandoffDoesNotOverrideSummaryScreen() {
        val model = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.SUMMARY,
                postWorkoutContinuationHandoffVisible = true,
                sessionLifecycleState = SessionLifecycleState.COMPLETED,
            ),
        )

        assertEquals(AppScreen.SUMMARY, model.screen)
    }

    @Test
    fun sessionDebugProbeStatePassesThroughToModel() {
        val model = MainActivityUiModelFactory.create(
            baseSnapshot(
                currentScreen = AppScreen.SESSION,
                sessionLifecycleState = SessionLifecycleState.RUNNING,
                sessionDebugProbeVisible = true,
                sessionDebugProbeTitle = "Safety probe",
                sessionDebugProbeMessage = "Hold steady",
                sessionDebugProbeDiagnostics = "Intent: Continue ride",
                sessionDebugProbeLastSignalLabel = "Smooth",
                sessionDebugProbeLastSignalCount = 2,
            ),
        )

        assertTrue(model.sessionDebugProbeVisible)
        assertEquals("Safety probe", model.sessionDebugProbeTitle)
        assertEquals("Hold steady", model.sessionDebugProbeMessage)
        assertEquals("Intent: Continue ride", model.sessionDebugProbeDiagnostics)
        assertEquals("Received: Smooth #2", model.sessionDebugProbeReceipt)
    }

    private fun baseSnapshot(
        currentScreen: AppScreen,
        aiMenuAssistantMessage: String? = null,
        aiMenuAssistantIsError: Boolean = false,
        aiMenuAssistantTemplateKey: String? = null,
        aiSessionAssistantMessage: String? = null,
        aiSessionAssistantIsError: Boolean = false,
        aiSummaryAssistantMessage: String? = null,
        ftmsReady: Boolean = false,
        ftmsSelected: Boolean = false,
        ftmsDeviceReachable: Boolean? = null,
        hrSelected: Boolean = false,
        hrDeviceReachable: Boolean? = null,
        sessionLifecycleState: SessionLifecycleState = SessionLifecycleState.IDLE,
        sessionDurationSeconds: Int = 0,
        selectedSessionSetupMode: SessionSetupMode = SessionSetupMode.FILE,
        postWorkoutContinuationHandoffVisible: Boolean = false,
        sessionDebugProbeVisible: Boolean = false,
        sessionDebugProbeTitle: String? = null,
        sessionDebugProbeMessage: String? = null,
        sessionDebugProbeDiagnostics: String? = null,
        sessionDebugProbeLastSignalLabel: String? = null,
        sessionDebugProbeLastSignalCount: Int = 0,
    ): MainActivityUiModelSnapshot {
        return MainActivityUiModelSnapshot(
            currentScreen = currentScreen,
            sessionDurationSeconds = sessionDurationSeconds,
            selectedSessionSetupMode = selectedSessionSetupMode,
            postWorkoutContinuationHandoffVisible = postWorkoutContinuationHandoffVisible,
            sessionDebugProbeVisible = sessionDebugProbeVisible,
            sessionDebugProbeTitle = sessionDebugProbeTitle,
            sessionDebugProbeMessage = sessionDebugProbeMessage,
            sessionDebugProbeDiagnostics = sessionDebugProbeDiagnostics,
            sessionDebugProbeLastSignalLabel = sessionDebugProbeLastSignalLabel,
            sessionDebugProbeLastSignalCount = sessionDebugProbeLastSignalCount,
            aiMenuAssistantMessage = aiMenuAssistantMessage,
            aiMenuAssistantIsError = aiMenuAssistantIsError,
            aiMenuAssistantTemplateKey = aiMenuAssistantTemplateKey,
            aiSessionAssistantMessage = aiSessionAssistantMessage,
            aiSessionAssistantIsError = aiSessionAssistantIsError,
            aiSummaryAssistantMessage = aiSummaryAssistantMessage,
            ftmsReady = ftmsReady,
            ftmsSelected = ftmsSelected,
            ftmsDeviceReachable = ftmsDeviceReachable,
            hrSelected = hrSelected,
            hrDeviceReachable = hrDeviceReachable,
            sessionLifecycleState = sessionLifecycleState,
            ewoEditorSnapshot = EwoEditorSnapshot(
                document = EditorDocumentFactory.empty(),
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
                openedFromBundledLibrary = false,
                ftpWatts = null,
                hrMaxBpm = null,
                restingHrBpm = null,
                lthrBpm = null,
                hasClipboard = false,
            ),
        )
    }
}
