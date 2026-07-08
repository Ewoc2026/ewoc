package io.github.ewoc2026.ewoc

import androidx.compose.runtime.mutableStateOf
import io.github.ewoc2026.ewoc.compat.CompatibilityRunArtifacts

/**
 * Owns Compatibility Mode UI-facing state.
 */
internal class CompatibilityModeUiState {
    val checkInProgressState = mutableStateOf(false)
    val statusMessageState = mutableStateOf<String?>(null)

    var latestRunArtifacts: CompatibilityRunArtifacts? = null

    val checkStatePort = object : CompatibilityCheckStatePort {
        override var latestRunArtifacts: CompatibilityRunArtifacts?
            get() = this@CompatibilityModeUiState.latestRunArtifacts
            set(value) {
                this@CompatibilityModeUiState.latestRunArtifacts = value
            }

        override var checkInProgress: Boolean
            get() = checkInProgressState.value
            set(value) {
                checkInProgressState.value = value
            }

        override var statusMessage: String?
            get() = statusMessageState.value
            set(value) {
                statusMessageState.value = value
            }
    }

    fun restoreLatestRunArtifacts(artifacts: CompatibilityRunArtifacts?) {
        latestRunArtifacts = artifacts
    }

}
