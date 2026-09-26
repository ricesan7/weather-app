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
    ":project-compiler",
    ":runtime-protocol",
    ":examples",
)
