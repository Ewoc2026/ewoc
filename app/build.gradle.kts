import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

private fun String.asBuildConfigLiteral(): String {
    val escaped = replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

private fun gitOutputOr(defaultValue: String, vararg args: String): String {
    if (!rootProject.layout.projectDirectory.file(".git").asFile.exists()) {
        return defaultValue
    }
    return runCatching {
        val process = ProcessBuilder("git", *args)
            .directory(rootProject.layout.projectDirectory.asFile)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && output.isNotBlank()) output else defaultValue
    }.getOrDefault(defaultValue)
}

val defaultFtpWatts = providers.gradleProperty("ergometer.ftp.watts")
    .orElse("100")
val allowLegacyWorkoutFallback = providers.gradleProperty("ergometer.workout.allowLegacyFallback")
    .orElse("false")
val allowLegacyWorkoutFallbackDebug = providers.gradleProperty("ergometer.workout.allowLegacyFallback.debug")
    .orElse(allowLegacyWorkoutFallback)
val allowLegacyWorkoutFallbackRelease = providers.gradleProperty("ergometer.workout.allowLegacyFallback.release")
    .orElse("false")
val releaseMinifyEnabled = providers.gradleProperty("ergometer.release.minify")
    .orElse("true")
val releaseSigningStoreFile = providers.environmentVariable("ERGOMETER_RELEASE_STORE_FILE")
    .orElse(providers.gradleProperty("ergometer.signing.storeFile"))
    .orNull
val releaseSigningStorePassword = providers.environmentVariable("ERGOMETER_RELEASE_STORE_PASSWORD")
    .orElse(providers.gradleProperty("ergometer.signing.storePassword"))
    .orNull
val releaseSigningKeyAlias = providers.environmentVariable("ERGOMETER_RELEASE_KEY_ALIAS")
    .orElse(providers.gradleProperty("ergometer.signing.keyAlias"))
    .orNull
val releaseSigningKeyPassword = providers.environmentVariable("ERGOMETER_RELEASE_KEY_PASSWORD")
    .orElse(providers.gradleProperty("ergometer.signing.keyPassword"))
    .orNull

val releaseSigningConfigured =
    !releaseSigningStoreFile.isNullOrBlank() &&
        !releaseSigningStorePassword.isNullOrBlank() &&
        !releaseSigningKeyAlias.isNullOrBlank() &&
        !releaseSigningKeyPassword.isNullOrBlank()

val releaseSigningPartiallyConfigured = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword,
).any { !it.isNullOrBlank() } && !releaseSigningConfigured

if (releaseSigningPartiallyConfigured) {
    throw GradleException(
        "Release signing is partially configured. Provide all of: " +
            "ERGOMETER_RELEASE_STORE_FILE, ERGOMETER_RELEASE_STORE_PASSWORD, " +
            "ERGOMETER_RELEASE_KEY_ALIAS, ERGOMETER_RELEASE_KEY_PASSWORD.",
    )
}

val buildCommitSha = gitOutputOr("unknown", "rev-parse", "--short=12", "HEAD")
val buildBranchName = gitOutputOr("unknown", "rev-parse", "--abbrev-ref", "HEAD")
val buildWorktreeDirty = gitOutputOr("", "status", "--porcelain")
    .lineSequence()
    .any { it.isNotBlank() }
    .toString()

android {
    namespace = "io.github.ewoc2026.ewoc"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "io.github.ewoc2026.ewoc"
        minSdk = 33
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.4"
        buildConfigField("int", "DEFAULT_FTP_WATTS", defaultFtpWatts.get())
        buildConfigField("boolean", "ALLOW_LEGACY_WORKOUT_FALLBACK", allowLegacyWorkoutFallbackDebug.get())
        buildConfigField("boolean", "VERBOSE_TELEMETRY_LOGGING", "false")
        buildConfigField("String", "BUILD_COMMIT_SHA", buildCommitSha.asBuildConfigLiteral())
        buildConfigField("String", "BUILD_BRANCH_NAME", buildBranchName.asBuildConfigLiteral())
        buildConfigField("boolean", "BUILD_WORKTREE_DIRTY", buildWorktreeDirty)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigningStoreFile!!)
                storePassword = releaseSigningStorePassword!!
                keyAlias = releaseSigningKeyAlias!!
                keyPassword = releaseSigningKeyPassword!!
            }
        }
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "ALLOW_LEGACY_WORKOUT_FALLBACK", allowLegacyWorkoutFallbackDebug.get())
            buildConfigField("boolean", "VERBOSE_TELEMETRY_LOGGING", "true")
        }
        release {
            buildConfigField("boolean", "ALLOW_LEGACY_WORKOUT_FALLBACK", allowLegacyWorkoutFallbackRelease.get())
            buildConfigField("boolean", "VERBOSE_TELEMETRY_LOGGING", "false")
            isMinifyEnabled = releaseMinifyEnabled.get().toBoolean()
            isShrinkResources = isMinifyEnabled
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":modules:ewo-core"))
    implementation(project(":modules:ewo-editor-model"))
    implementation(project(":modules:ewo-editor-commands"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kxml2)
}

/**
 * Publishes unit-test coverage for CI visibility without enforcing thresholds yet.
 * We keep this task debug-only so coverage trends stay comparable with the existing CI test lane.
 */
tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val coverageExclusions = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/android/**/*.*",
        "**/databinding/**/*.*",
    )

    val javaDebugClasses = fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
        exclude(coverageExclusions)
    }
    val kotlinDebugClasses = fileTree("${layout.buildDirectory.get().asFile}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
        exclude(coverageExclusions)
    }

    classDirectories.setFrom(files(javaDebugClasses, kotlinDebugClasses))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest/testDebugUnitTest.exec",
            )
        },
    )

}
