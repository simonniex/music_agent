import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localValue(name: String, fallback: String = ""): String {
    return localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: fallback
}

android {
    namespace = "com.example.mediaagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mediaagent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        resValue("string", "deepseek_api_key", localValue("DEEPSEEK_API_KEY"))
        resValue("string", "deepseek_base_url", localValue("DEEPSEEK_BASE_URL", "https://tokenhub.tencentmaas.com/v1"))
        resValue("string", "deepseek_model", localValue("DEEPSEEK_MODEL", "deepseek-v4-flash"))
        resValue("string", "vita_model", localValue("VITA_MODEL", "youtu-vita"))
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Ensure dex merge does not overlap processDebugResources (R.jar delete race on Windows).
afterEvaluate {
    tasks.matching { it.name == "mergeLibDexDebug" }.configureEach {
        mustRunAfter("processDebugResources")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
