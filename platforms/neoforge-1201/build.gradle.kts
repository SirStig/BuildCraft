/*
 * BuildCraft on Minecraft 1.20.1 -- the compatibility target.
 *
 * 1.20.1 NeoForge is a direct fork of MinecraftForge published as `net.neoforged:forge`,
 * so it still uses the `net.minecraftforge.*` packages and needs SRG reobfuscation.
 * ModDevGradle's `legacyforge` variant handles that.
 */

plugins {
    java
    alias(libs.plugins.moddev.legacy)
}

// The mod model below reads the shared modules' source sets, so they must be configured first.
evaluationDependsOn(":expression")
evaluationDependsOn(":shared")

base.archivesName = "buildcraft-neoforge-1.20.1"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.mc1201.get().toInt())
}

legacyForge {
    // `version` on this extension means *MinecraftForge*; NeoForge's 1.20.1 fork is
    // published as net.neoforged:forge and is selected via neoForgeVersion instead.
    enable {
        neoForgeVersion = libs.versions.neoforge.mc1201.get()
    }

    runs {
        create("client") {
            client()
            gameDirectory = file("run/client")
        }
        create("server") {
            server()
            gameDirectory = file("run/server")
        }
        create("data") {
            data()
            gameDirectory = file("run/data")
        }
    }

    mods {
        create("buildcraft") {
            sourceSet(sourceSets.main.get())
            // A dev run loads build/classes rather than the jar, so the shared modules have to be declared as
            // part of the mod or the game dies with NoClassDefFoundError on the first shared class touched.
            sourceSet(project(":expression").sourceSets.main.get())
            sourceSet(project(":shared").sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":expression"))
    implementation(project(":shared"))
}

tasks.named<Jar>("jar") {
    from(project(":expression").sourceSets["main"].output)
    from(project(":shared").sourceSets["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<ProcessResources>().configureEach {
    val replacements = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to libs.versions.minecraft.mc1201.get(),
        "forge_version" to libs.versions.neoforge.mc1201.get().substringAfter('-'),
    )
    inputs.properties(replacements)
    filesMatching("META-INF/mods.toml") {
        expand(replacements)
    }
}

// Installing into a Prism Launcher instance, for testing in a real game. The 1.20.1 instance runs
// MinecraftForge rather than NeoForge's fork; both are 47.x and load the same jar.
extra["bc.prism.instance"] = "Retrograde 1.20.1 Forge"
apply(from = rootProject.file("gradle/prism.gradle.kts"))
