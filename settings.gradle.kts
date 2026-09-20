pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    }
}

plugins {
    // Lets Gradle auto-provision a JDK when the required toolchain is not installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "buildcraft"

// --- Version-independent modules (plain Java, no Minecraft on the classpath) ---
include(":expression")
project(":expression").projectDir = file("modules/expression")

include(":shared")
project(":shared").projectDir = file("modules/shared")

// --- Platform targets ----------------------------------------------------------
// Primary: Minecraft 26.x (26.1 / 26.2 / 26.3) on modern NeoForge.
include(":neoforge-26x")
project(":neoforge-26x").projectDir = file("platforms/neoforge-26x")

// Compatibility: Minecraft 1.20.1 on NeoForge's Forge fork.
include(":neoforge-1201")
project(":neoforge-1201").projectDir = file("platforms/neoforge-1201")
