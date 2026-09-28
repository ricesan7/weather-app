plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val aiGatewayUrl = providers.gradleProperty("AI_GATEWAY_URL")
    .orElse(providers.environmentVariable("AI_GATEWAY_URL"))
    .orElse("")
    .get()
val aiGatewayToken = providers.gradleProperty("AI_GATEWAY_TOKEN")
    .orElse(providers.environmentVariable("AI_GATEWAY_TOKEN"))
    .orElse("")
    .get()
val base44BridgeUrl = providers.gradleProperty("BASE44_BRIDGE_URL")
    .orElse(providers.environmentVariable("BASE44_BRIDGE_URL"))
    .orElse("https://base44.app/api/apps/6ab8775b4a181f8a3257001f/functions/hardwareBridge")
    .get()

android {
    namespace = "com.aielectronics.builder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aielectronics.builder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "AI_GATEWAY_URL", buildConfigString(aiGatewayUrl))
        buildConfigField("String", "AI_GATEWAY_TOKEN", buildConfigString(aiGatewayToken))
        buildConfigField("String", "BASE44_BRIDGE_URL", buildConfigString(base44BridgeUrl))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":application-core"))
    implementation(project(":core-model"))
    implementation(project(":parts-db"))
    implementation(project(":project-compiler"))
    implementation(project(":diagram-engine"))
    implementation(project(":feature-assembly"))
    implementation(project(":feature-bench"))
    implementation(project(":feature-control"))
    implementation(project(":feature-editor"))
    implementation(project(":runtime-protocol"))
    implementation(project(":transport-ble"))
    implementation(project(":transport-ble-android"))
    implementation(project(":project-storage-android"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.json:json:20240303")
}
