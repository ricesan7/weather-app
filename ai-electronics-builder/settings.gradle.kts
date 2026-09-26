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
    ":core-model",
    ":parts-db",
    ":project-compiler",
    ":runtime-protocol",
    ":examples",
)
