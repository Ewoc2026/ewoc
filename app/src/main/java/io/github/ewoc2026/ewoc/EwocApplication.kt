package io.github.ewoc2026.ewoc

import android.app.Application
import android.util.Log
import androidx.work.Configuration

/**
 * Provides WorkManager configuration so release builds do not depend solely on AndroidX Startup
 * manifest initialization before app services request a WorkManager instance.
 */
class EwocApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        Log.i(
            "APP_BUILD",
            "versionName=${BuildConfig.VERSION_NAME} " +
                "versionCode=${BuildConfig.VERSION_CODE} " +
                "buildType=${BuildConfig.BUILD_TYPE} " +
                "gitSha=${BuildConfig.BUILD_COMMIT_SHA} " +
                "branch=${BuildConfig.BUILD_BRANCH_NAME} " +
                "worktreeDirty=${BuildConfig.BUILD_WORKTREE_DIRTY}",
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) {
                    Log.DEBUG
                } else {
                    Log.INFO
                },
            )
            .build()
}
