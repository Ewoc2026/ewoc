plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":modules:ewo-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
