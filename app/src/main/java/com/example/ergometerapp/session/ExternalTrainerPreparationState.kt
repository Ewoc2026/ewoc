package com.example.ergometerapp.session

/**
 * Latest trainer-preparation state for non-workout flows that borrow the FTMS stack.
 *
 * This lets callers distinguish a trainer that is still connecting from one that actually failed,
 * which is important for flows like the baseline test that should wait inside their own screen.
 */
internal enum class ExternalTrainerPreparationState {
    IDLE,
    PENDING,
    READY,
    FAILED,
}
