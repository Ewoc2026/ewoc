package com.example.ergometerapp.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityV1ConstantsTest {

    @Test
    fun constants_match_locked_v1_retry_policy() {
        assertEquals(1, CompatibilityV1Constants.CONNECT_RETRY_COUNT)
        assertEquals(1, CompatibilityV1Constants.REQUEST_CONTROL_RETRY_ON_TIMEOUT_OR_WRITE_FAILURE)
        assertEquals(0, CompatibilityV1Constants.REQUEST_CONTROL_RETRY_ON_REJECT)
        assertEquals(1, CompatibilityV1Constants.CP_ACK_RETRY_ON_TIMEOUT_OR_WRITE_FAILURE)
        assertEquals(0, CompatibilityV1Constants.CP_ACK_RETRY_ON_REJECT)
        assertEquals(1, CompatibilityV1Constants.STOP_RETRY_COUNT)
    }

    @Test
    fun constants_match_locked_v1_step_profile() {
        assertEquals(80, CompatibilityV1Constants.STEP_A_WATTS)
        assertEquals(140, CompatibilityV1Constants.STEP_B_WATTS)
        assertEquals(100, CompatibilityV1Constants.STEP_C_WATTS)
        assertTrue(CompatibilityV1Constants.STEP_A_WATTS in CompatibilityV1Constants.STEP_MIN_WATTS..CompatibilityV1Constants.STEP_MAX_WATTS)
        assertTrue(CompatibilityV1Constants.STEP_B_WATTS in CompatibilityV1Constants.STEP_MIN_WATTS..CompatibilityV1Constants.STEP_MAX_WATTS)
        assertTrue(CompatibilityV1Constants.STEP_C_WATTS in CompatibilityV1Constants.STEP_MIN_WATTS..CompatibilityV1Constants.STEP_MAX_WATTS)
    }
}

