plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":application-core"))
    implementation(project(":core-model"))
    implementation(project(":project-compiler"))

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
