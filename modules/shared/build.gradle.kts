/*
 * buildcraft-shared: version-independent BuildCraft logic.
 *
 * Anything in here must compile without Minecraft or a mod loader on the classpath.
 * Code is moved into this module from the 1.12.2 tree as it is proven to be free of
 * Minecraft types, so that it is written once and consumed by every platform target.
 */

plugins {
    java
    `java-library`
}

base.archivesName = "buildcraft-shared"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.shared.get().toInt())
}

dependencies {
    api(project(":expression"))
    api(libs.jetbrains.annotations)

    // Provided by Minecraft at runtime.
    compileOnly(libs.guava)
    compileOnly(libs.gson)
    compileOnly(libs.commons.lang3)
    compileOnly(libs.fastutil)
    compileOnly(libs.log4j.api)

    testImplementation(libs.fastutil)
    testImplementation(libs.log4j.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
