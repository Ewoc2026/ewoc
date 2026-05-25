plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.jvm.toolchain.JavaLanguageVersion

dependencies {
    implementation(project(":modules:ewo-core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.ewo.validator.cli.EwoValidatorCliKt")
}
