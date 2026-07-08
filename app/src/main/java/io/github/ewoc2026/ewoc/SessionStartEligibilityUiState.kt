package io.github.ewoc2026.ewoc

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Owns the narrow state cluster that determines whether a session may start.
 *
 * The selected trainer/HR identities are shared with the picker-facing device state so
 * session-start guards, restore logic, and device-selection flows mutate one backing owner
 * instead of drifting into separate `MainViewModel` vars.
 */
internal class SessionStartEligibilityUiState {
    val mockTrainerModeEnabledState = mutableStateOf(false)
    val ftmsSelection = SessionStartEligibilityDeviceUiState()
    val hrSelection = SessionStartEligibilityDeviceUiState()

    fun restoreMockTrainerMode(enabled: Boolean, isDebugBuild: Boolean) {
        mockTrainerModeEnabledState.value = isDebugBuild && enabled
    }

    fun statePort(
        workoutReadyProvider: () -> Boolean,
        selectedSessionSetupModeProvider: () -> SessionSetupMode,
    ): SessionStartEligibilityStatePort {
        return object : SessionStartEligibilityStatePort {
            override val workoutReady: Boolean
                get() = workoutReadyProvider()

            override val selectedSessionSetupMode: SessionSetupMode
                get() = selectedSessionSetupModeProvider()

            override var mockTrainerModeEnabled: Boolean
                get() = mockTrainerModeEnabledState.value
                set(value) {
                    mockTrainerModeEnabledState.value = value
                }

            override var selectedFtmsDeviceMac: String?
                get() = ftmsSelection.selectedMacState.value
                set(value) {
                    ftmsSelection.selectedMacState.value = value
                }

            override var selectedHrDeviceMac: String?
                get() = hrSelection.selectedMacState.value
                set(value) {
                    hrSelection.selectedMacState.value = value
                }
        }
    }
}

/**
 * Holds the selected device identity used by session-start gating.
 */
internal class SessionStartEligibilityDeviceUiState(
    val selectedMacState: MutableState<String?> = mutableStateOf(null),
)
