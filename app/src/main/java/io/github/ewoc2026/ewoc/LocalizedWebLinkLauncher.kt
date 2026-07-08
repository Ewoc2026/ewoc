package io.github.ewoc2026.ewoc

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.os.ConfigurationCompat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Opens public website links while forwarding the current app locale through one stable query
 * parameter.
 *
 * The website owns locale matching and English fallback. The app only forwards the primary app
 * locale so every outbound website link stays on the same contract.
 */
internal object LocalizedWebLinkLauncher {

    private const val localeQueryParameter = "locale"

    fun open(
        context: Context,
        url: String,
        launchRequest: (WebLinkLaunchRequest) -> Unit = { request ->
            context.startActivity(request.toIntent())
        },
    ): Boolean {
        val request = buildLaunchRequest(
            url = url,
            localeTag = currentAppLocaleTag(context),
        )
        return runCatching {
            launchRequest(request)
            true
        }.getOrDefault(false)
    }

    internal fun buildLaunchRequest(
        url: String,
        localeTag: String? = null,
    ): WebLinkLaunchRequest {
        return WebLinkLaunchRequest(
            url = appendLocaleQueryParameter(url = url, localeTag = localeTag),
        )
    }

    internal fun appendLocaleQueryParameter(
        url: String,
        localeTag: String?,
    ): String {
        val normalizedLocaleTag = localeTag?.trim()?.takeIf { it.isNotEmpty() } ?: return url
        return runCatching {
            val fragmentSplit = url.split("#", limit = 2)
            val baseUrl = fragmentSplit[0]
            val fragmentSuffix = fragmentSplit.getOrNull(1)?.let { "#$it" }.orEmpty()
            val separator = if ('?' in baseUrl) '&' else '?'
            val encodedLocale =
                URLEncoder.encode(normalizedLocaleTag, StandardCharsets.UTF_8.toString())
            baseUrl + separator + localeQueryParameter + "=" + encodedLocale + fragmentSuffix
        }.getOrDefault(url)
    }

    private fun currentAppLocaleTag(context: Context): String? {
        return runCatching {
            ConfigurationCompat.getLocales(context.resources.configuration)[0]
                ?.toLanguageTag()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

internal data class WebLinkLaunchRequest(
    val url: String,
    val action: String = Intent.ACTION_VIEW,
    val category: String = Intent.CATEGORY_BROWSABLE,
) {
    fun toIntent(): Intent {
        return Intent(action, Uri.parse(url))
            .addCategory(category)
    }
}
