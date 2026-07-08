package io.github.ewoc2026.ewoc

import androidx.compose.runtime.mutableStateOf
import io.github.ewoc2026.ewoc.ai.AiPhase

/**
 * Owns the phase-scoped AI assistant presentation state shared across coordinator writes and
 * activity-level screen routing.
 *
 * Keeping the MENU, SESSION, and SUMMARY message flags together prevents `AppUiState` from
 * reintroducing another inline multi-consumer cluster while preserving the existing observable
 * state shape that the rest of the app already consumes.
 */
internal class AiAssistantUiState {
    val menuMessageState = mutableStateOf<String?>(null)
    val menuIsErrorState = mutableStateOf(false)
    val menuTemplateKeyState = mutableStateOf<String?>(null)
    val sessionMessageState = mutableStateOf<String?>(null)
    val sessionIsErrorState = mutableStateOf(false)
    val summaryMessageState = mutableStateOf<String?>(null)
    val summaryIsErrorState = mutableStateOf(false)

    fun clearPhaseMessage(phase: AiPhase) {
        when (phase) {
            AiPhase.MENU -> {
                menuMessageState.value = null
                menuIsErrorState.value = false
                menuTemplateKeyState.value = null
            }
            AiPhase.SESSION -> {
                sessionMessageState.value = null
                sessionIsErrorState.value = false
            }
            AiPhase.SUMMARY -> {
                summaryMessageState.value = null
                summaryIsErrorState.value = false
            }
        }
    }

    fun setPhaseMessage(
        phase: AiPhase,
        message: String,
        isError: Boolean,
        menuTemplateKey: String? = null,
    ) {
        when (phase) {
            AiPhase.MENU -> {
                menuMessageState.value = message
                menuIsErrorState.value = isError
                menuTemplateKeyState.value = menuTemplateKey
            }
            AiPhase.SESSION -> {
                sessionMessageState.value = message
                sessionIsErrorState.value = isError
            }
            AiPhase.SUMMARY -> {
                summaryMessageState.value = message
                summaryIsErrorState.value = isError
            }
        }
    }
}
