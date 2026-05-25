package com.example.ergometerapp

import com.example.ergometerapp.compat.CompatibilityFailureCode
import org.junit.Assert.assertEquals
import org.junit.Test

class CompatibilityFailureReasonMessageResolverTest {
    @Test
    fun resolveLookupKey_prefersNormalizedReasonKeyWhenPresent() {
        val key = resolveCompatibilityFailureReasonLookupKey(
            failureReasonKey = "  REQUEST_CONTROL_TIMEOUT  ",
            failureCode = CompatibilityFailureCode.UNKNOWN_FAILURE,
        )

        assertEquals("request_control_timeout", key)
    }

    @Test
    fun resolveLookupKey_fallsBackToFailureCodeWhenReasonKeyMissing() {
        val key = resolveCompatibilityFailureReasonLookupKey(
            failureReasonKey = null,
            failureCode = CompatibilityFailureCode.CONNECT_DISCONNECTED,
        )

        assertEquals("connect_disconnected", key)
    }

    @Test
    fun resolveMessageResId_mapsKnownReasonKey() {
        val resId = resolveCompatibilityFailureReasonMessageResId(
            failureReasonKey = "cleanup_fallback_timeout",
            failureCode = null,
        )

        assertEquals(R.string.compatibility_failure_reason_cleanup_fallback_timeout, resId)
    }

    @Test
    fun resolveMessageResId_returnsUnknownForUnsupportedReasonKey() {
        val resId = resolveCompatibilityFailureReasonMessageResId(
            failureReasonKey = "totally_new_reason_key",
            failureCode = null,
        )

        assertEquals(R.string.compatibility_failure_reason_unknown_failure, resId)
    }
}
