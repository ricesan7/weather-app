plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-model"))
}

kotlin {
    jvmToolchain(17)
}
