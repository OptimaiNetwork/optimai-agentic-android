import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Machine-specific config: local.properties, then the environment, then a
// public default. See local.properties.example.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun localConfig(key: String, env: String, default: String): String =
    localProperties.getProperty(key) ?: System.getenv(env) ?: default

// Release builds refuse cleartext (see the network security config), so an
// http:// override such as the emulator address in local.properties is ignored
// for release rather than shipping a build that cannot reach its server.
// The hosted OptimAI Agentic server, used when local.properties sets nothing.
val DEFAULT_API_BASE_URL = "https://agentic-api.optimai.network"
val RELEASE_DEFAULT_API_BASE_URL = DEFAULT_API_BASE_URL

fun releaseApiBaseUrl(): String {
    val url = localConfig("optimai.apiBaseUrl", "OPTIMAI_API_BASE_URL", RELEASE_DEFAULT_API_BASE_URL)
    if (url.startsWith("https://")) return url
    logger.warn("optimai.apiBaseUrl is not https, so the release build uses $RELEASE_DEFAULT_API_BASE_URL instead.")
    return RELEASE_DEFAULT_API_BASE_URL
}

android {
    namespace = "com.test.agenttrade"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.test.agenttrade"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"

        // Reown project id (https://cloud.reown.com), needed for wallet connections.
        buildConfigField("String", "REOWN_PROJECT_ID", "\"${localConfig("reown.projectId", "REOWN_PROJECT_ID", "")}\"")
    }

    buildTypes {
        // The OptimAI Agentic server address. local.properties or the environment
        // overrides it; it can also be changed at runtime in Settings > Developer.
        // Debug defaults to the emulator's route to the host (plain HTTP, allowed
        // for development hosts only by src/debug/res/xml/network_security_config.xml).
        // Both variants default to the hosted server; release is HTTPS only.
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${localConfig("optimai.apiBaseUrl", "OPTIMAI_API_BASE_URL", DEFAULT_API_BASE_URL)}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl()}\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // The usual duplicate META-INF files that several libraries ship
            // (license texts and build metadata). Excluding them only avoids
            // "more than one file" merge errors; the licenses of what we ship
            // are listed in THIRD_PARTY_NOTICES.md.
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.savedstate:savedstate-ktx:1.3.3")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    implementation(platform("com.reown:android-bom:1.6.10"))
    implementation("com.reown:android-core")
    implementation("com.reown:sign")
}
