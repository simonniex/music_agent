plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        val desktopMain by getting
    }
}

android {
    namespace = "com.example.mediaagent.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 8.7+ on Windows: bundleLibCompileToJar* and bundleLibRuntimeToJar* can run in parallel
// and contend on the same classes.jar (NonIncrementalTask delete-then-write race).
afterEvaluate {
    tasks.matching { it.name.startsWith("bundleLibRuntimeToJar") }.configureEach {
        val compileJarTask = "bundleLibCompileToJar${name.removePrefix("bundleLibRuntimeToJar")}"
        if (tasks.names.contains(compileJarTask)) {
            mustRunAfter(compileJarTask)
        }
    }
    tasks.matching { it.name.startsWith("bundleLibRuntimeToDir") }.configureEach {
        val compileJarTask = "bundleLibCompileToJar${name.removePrefix("bundleLibRuntimeToDir")}"
        if (tasks.names.contains(compileJarTask)) {
            mustRunAfter(compileJarTask)
        }
    }
}
