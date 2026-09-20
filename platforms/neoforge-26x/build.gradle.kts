/*
 * BuildCraft on Minecraft 26.x (26.1 / 26.2 / 26.3) -- the primary port target.
 *
 * The Minecraft release is selected by the `bc.mc26` Gradle property so the same source
 * tree can be built against any of the three 26.x releases:
 *     ./gradlew :neoforge-26x:build -Pbc.mc26=26.2
 */

plugins {
    java
    alias(libs.plugins.moddev)
}

val mc26: String = (project.findProperty("bc.mc26") as String? ?: "26.3").trim()

val neoVersion: String = when (mc26) {
    "26.3" -> libs.versions.neoforge.mc263.get()
    "26.2" -> libs.versions.neoforge.mc262.get()
    "26.1" -> libs.versions.neoforge.mc261.get()
    else -> throw GradleException(
        "Unsupported bc.mc26='$mc26'. Supported Minecraft 26.x releases: 26.1, 26.2, 26.3."
    )
}

base.archivesName = "buildcraft-neoforge-$mc26"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.mc26x.get().toInt())
}

neoForge {
    version = neoVersion

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
            clientData()
            gameDirectory = file("run/data")
        }
    }

    mods {
        create("buildcraft") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":expression"))
    implementation(project(":shared"))
}

// The version-independent modules are plain jars, so they have to be bundled into the
// mod jar rather than resolved from Maven at runtime.
tasks.named<Jar>("jar") {
    from(project(":expression").sourceSets["main"].output)
    from(project(":shared").sourceSets["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<ProcessResources>().configureEach {
    val replacements = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to mc26,
        "neoforge_version" to neoVersion,
    )
    inputs.properties(replacements)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replacements)
    }
}
