package io.github.ewoc2026.ewoc

import androidx.compose.runtime.mutableStateOf
import io.github.ewoc2026.ewoc.session.release.TrainerControlAuthority

/**
 * Owns the narrow session-runtime cluster that still changes together across start, control,
 * and stop flow transitions.
 *
 * Keeping these FTMS readiness flags and cadence-start guards together prevents
 * `AppUiState` and its consumers from reintroducing another loose runtime flag cluster while
 * preserving the existing Compose-observable state shape used throughout the app.
 */
internal class SessionRuntimeUiState {
    val ftmsReady = mutableStateOf(false)
    val ftmsControlGranted = mutableStateOf(false)
    val lastTargetPower = mutableStateOf<Int?>(null)
    val trainerControlAuthority = mutableStateOf(TrainerControlAuthority.RIDER_CONTROLLED)
    val lastAppControlledTargetPower = mutableStateOf<Int?>(null)
    val workoutReady = mutableStateOf(false)
    val stopFlowState = mutableStateOf(StopFlowState.IDLE)
    val postWorkoutContinuationHandoffVisible = mutableStateOf(false)
    val sessionDebugProbeVisible = mutableStateOf(false)
    val sessionDebugProbeTitle = mutableStateOf<String?>(null)
    val sessionDebugProbeMessage = mutableStateOf<String?>(null)
    val sessionDebugProbeLastSignalLabel = mutableStateOf<String?>(null)
    val sessionDebugProbeLastSignalCount = mutableStateOf(0)
    val sessionDebugProbeLastSignalAtEpochMs = mutableStateOf<Long?>(null)

    var pendingSessionStartAfterPermission: Boolean = false
    var pendingCadenceStartAfterControlGranted: Boolean = false
    var autoPausedByZeroCadence: Boolean = false
    var postWorkoutFreerideModeActive: Boolean = false
    var bikeDataLastUpdatedAtEpochMs: Long? = null
    var heartRateLastUpdatedAtEpochMs: Long? = null
}
