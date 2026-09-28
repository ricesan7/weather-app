plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.aielectronics.storage.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":application-core"))
    implementation(project(":core-model"))
    implementation(project(":parts-db"))
}
