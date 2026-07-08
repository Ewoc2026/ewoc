package io.github.ewoc2026.ewoc

/**
 * Centralizes public guide URLs so app surfaces can link to website help without embedding path
 * details directly into UI code.
 *
 * The website structure is still evolving, so keep both the base URL and page slugs here for
 * low-friction follow-up changes.
 */
internal object WebsiteGuideLinks {

    private const val guideBaseUrl = "https://github.com/Ewoc2026/ewoc/tree/main/docs/user-guide"
    private const val androidEditorPath = "android-editor"
    private const val gettingStartedPath = "getting-started"
    private const val aiWorkoutsPath = "ai-workouts"
    private const val desktopEditorPath = "desktop-editor"
    private const val troubleshootingPath = "troubleshooting"

    fun resolve(destination: WebsiteGuideDestination): String {
        return when (destination) {
            WebsiteGuideDestination.ANDROID_EDITOR -> buildGuideUrl(androidEditorPath)
            WebsiteGuideDestination.GETTING_STARTED -> buildGuideUrl(gettingStartedPath)
            WebsiteGuideDestination.AI_WORKOUTS -> buildGuideUrl(aiWorkoutsPath)
            WebsiteGuideDestination.DESKTOP_EDITOR -> buildGuideUrl(desktopEditorPath)
            WebsiteGuideDestination.TROUBLESHOOTING -> buildGuideUrl(troubleshootingPath)
        }
    }

    private fun buildGuideUrl(path: String): String {
        return guideBaseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}

internal enum class WebsiteGuideDestination {
    ANDROID_EDITOR,
    GETTING_STARTED,
    AI_WORKOUTS,
    DESKTOP_EDITOR,
    TROUBLESHOOTING,
}
