package com.example.ergometerapp

import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class PrivacyPolicyLinkLauncherTest {

    @Test
    fun buildLaunchRequest_usesBrowsableViewContractForPolicyUrl() {
        val request = PrivacyPolicyLinkLauncher.buildLaunchRequest("https://github.com/Ewoc2026/ewoc/blob/main/docs/privacy.md")

        assertEquals(Intent.ACTION_VIEW, request.action)
        assertEquals("https://github.com/Ewoc2026/ewoc/blob/main/docs/privacy.md", request.url)
        assertTrue(request.category == Intent.CATEGORY_BROWSABLE)
    }

    @Test
    fun buildLaunchRequest_appendsLocaleQueryParameterWhenProvided() {
        val request = PrivacyPolicyLinkLauncher.buildLaunchRequest(
            url = "https://github.com/Ewoc2026/ewoc/blob/main/docs/privacy.md",
            localeTag = "fi-FI",
        )

        assertEquals("https://github.com/Ewoc2026/ewoc/blob/main/docs/privacy.md?locale=fi-FI", request.url)
    }

    @Test
    fun open_returnsTrueWhenStartActivitySucceedsWithoutPreResolvingBrowser() {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getString(R.string.privacy_policy_url))
            .thenReturn("https://github.com/Ewoc2026/ewoc/blob/main/docs/privacy.md")
        var launchedRequest: PrivacyPolicyLaunchRequest? = null

        val launched = PrivacyPolicyLinkLauncher.open(context) { request ->
            launchedRequest = request
        }

        assertTrue(launched)
        assertEquals("https://github.com/Ewoc2026/ewoc/blob/main/docs/privacy.md", launchedRequest?.url)
        assertEquals(Intent.ACTION_VIEW, launchedRequest?.action)
        assertEquals(Intent.CATEGORY_BROWSABLE, launchedRequest?.category)
    }

    @Test
    fun open_returnsFalseWhenStartActivityThrows() {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getString(R.string.privacy_policy_url))
            .thenReturn("https://github.com/Ewoc2026/ewoc/blob/main/docs/privacy.md")
        val expectedFailure = RuntimeException("blocked")

        val launched = PrivacyPolicyLinkLauncher.open(context) {
            throw expectedFailure
        }

        assertFalse(launched)
    }
}
