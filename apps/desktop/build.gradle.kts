import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val desktopPackageVersion = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(project(":modules:ewo-core"))
    implementation(project(":modules:ewo-editor-model"))
    implementation(project(":modules:ewo-editor-commands"))
    testImplementation(kotlin("test-junit"))
}

compose.desktop {
    application {
        mainClass = "com.ewo.editor.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "ewoc-editor"
            packageVersion = desktopPackageVersion
            vendor = "Ewoc"
            description = "Desktop editor for creating and exporting Ewoc structured workouts."

            linux {
                debMaintainer = "Ewoc2026 <ewoc2026@users.noreply.github.com>"
                menuGroup = "Ewoc"
            }

            windows {
                menuGroup = "Ewoc"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
