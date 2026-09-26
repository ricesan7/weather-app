pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "ai-electronics-builder-core"

include(
    ":app",
    ":application-core",
    ":core-model",
    ":parts-db",
    ":project-compiler",
    ":diagram-engine",
    ":diagnostics",
    ":feature-assembly",
    ":feature-bench",
    ":feature-control",
    ":feature-editor",
    ":runtime-protocol",
    ":transport-ble",
    ":transport-ble-android",
    ":project-storage-android",
    ":test-harness",
    ":examples",
)
