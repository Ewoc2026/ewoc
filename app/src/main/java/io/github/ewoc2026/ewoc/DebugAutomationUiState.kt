package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.session.MockTrainerDebugScenario

/**
 * Owns one-shot debug automation flags that should survive until their next consumer fires.
 *
 * Keeping the pending mock-trainer scenario and the forced Documents-folder write failure
 * together prevents those debug-only workflow toggles from drifting back into separate
 * `MainViewModel` vars as Phase 2 keeps shrinking the hotspot.
 */
internal class DebugAutomationUiState {
    var pendingMockTrainerScenario: MockTrainerDebugScenario? = null
    var forceDocumentsFolderWriteFailure: Boolean = false

    fun armMockTrainerScenario(scenario: MockTrainerDebugScenario) {
        pendingMockTrainerScenario = scenario
    }

    fun consumeMockTrainerScenario(): MockTrainerDebugScenario? {
        val scenario = pendingMockTrainerScenario
        pendingMockTrainerScenario = null
        return scenario
    }

    fun armDocumentsFolderWriteFailure() {
        forceDocumentsFolderWriteFailure = true
    }

    fun consumeDocumentsFolderWriteFailure(): Boolean {
        if (!forceDocumentsFolderWriteFailure) {
            return false
        }
        forceDocumentsFolderWriteFailure = false
        return true
    }
}
