plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":modules:ewo-editor-model"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
