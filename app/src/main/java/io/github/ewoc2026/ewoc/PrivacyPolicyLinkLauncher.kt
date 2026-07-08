package io.github.ewoc2026.ewoc

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the published privacy policy through one canonical browser intent source.
 *
 * Keeping the URL, locale passthrough, and launch policy centralized prevents
 * public-policy entry points from drifting to different locations.
 */
internal object PrivacyPolicyLinkLauncher {

    fun open(
        context: Context,
        launchRequest: (PrivacyPolicyLaunchRequest) -> Unit = { request ->
            context.startActivity(request.toIntent())
        },
    ): Boolean {
        return LocalizedWebLinkLauncher.open(
            context = context,
            url = context.getString(R.string.privacy_policy_url),
        ) { request ->
            launchRequest(request.toPrivacyPolicyLaunchRequest())
        }
    }

    internal fun buildLaunchRequest(
        url: String,
        localeTag: String? = null,
    ): PrivacyPolicyLaunchRequest {
        return LocalizedWebLinkLauncher.buildLaunchRequest(
            url = url,
            localeTag = localeTag,
        ).toPrivacyPolicyLaunchRequest()
    }

    private fun WebLinkLaunchRequest.toPrivacyPolicyLaunchRequest(): PrivacyPolicyLaunchRequest {
        return PrivacyPolicyLaunchRequest(
            url = url,
            action = action,
            category = category,
        )
    }
}

internal data class PrivacyPolicyLaunchRequest(
    val url: String,
    val action: String = Intent.ACTION_VIEW,
    val category: String = Intent.CATEGORY_BROWSABLE,
) {
    fun toIntent(): Intent {
        return Intent(action, Uri.parse(url))
            .addCategory(category)
    }
}
