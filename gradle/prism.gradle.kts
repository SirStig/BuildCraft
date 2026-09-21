/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */

/*
 * Installs a built mod jar into a Prism Launcher instance, for testing in a real game rather than in a dev run.
 *
 * Applied by each platform project, which supplies the instance to target:
 *
 *     apply(from = rootProject.file("gradle/prism.gradle.kts"))
 *     extra["bc.prism.instance"] = "Retrograde 26.3 NeoForge"
 *
 * Both of these can be overridden from the command line, which is what you want for a differently named
 * instance or a non-Flatpak install:
 *
 *     ./gradlew :neoforge-26x:installToPrism
 *     ./gradlew :neoforge-26x:installToPrism -Pbc.prism.instance="My Other Instance"
 *     ./gradlew installToPrism -Pbc.prism.dir=/home/me/.local/share/PrismLauncher
 */

val defaultPrismDirs = listOf(
    // Flatpak, which is how Prism is installed on this machine.
    "${System.getProperty("user.home")}/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher",
    // Native package / AppImage.
    "${System.getProperty("user.home")}/.local/share/PrismLauncher",
)

fun resolvePrismDir(): File {
    val override = project.findProperty("bc.prism.dir") as String?
    if (override != null) {
        return File(override)
    }
    return defaultPrismDirs.map(::File).firstOrNull { it.isDirectory }
        ?: File(defaultPrismDirs.first())
}

tasks.register<Copy>("installToPrism") {
    group = "buildcraft"
    description = "Builds the mod jar and copies it into its Prism Launcher instance."

    dependsOn(tasks.named("build"))

    // Resolved at execution time so configuring the build never fails on a machine without Prism.
    val instanceName = providers.provider {
        (project.findProperty("bc.prism.instance") as String?)
            ?: (project.extra.takeIf { it.has("bc.prism.instance") }?.get("bc.prism.instance") as String?)
            ?: throw GradleException("No Prism instance configured for ${project.path}.")
    }

    val jarTask = tasks.named<Jar>("jar")
    from(jarTask)

    // The jar is the only thing copied; the destination is computed in doFirst because it depends on a
    // directory that may not exist until then.
    into(providers.provider {
        val prismDir = resolvePrismDir()
        val instances = File(prismDir, "instances")
        if (!instances.isDirectory) {
            throw GradleException(
                "Prism instances directory not found at $instances.\n" +
                    "Pass -Pbc.prism.dir=<PrismLauncher data dir> if Prism is installed somewhere else."
            )
        }
        val instance = File(instances, instanceName.get())
        if (!instance.isDirectory) {
            val available = instances.listFiles { f: File -> f.isDirectory }
                ?.joinToString("\n  ") { it.name } ?: "(none)"
            throw GradleException(
                "Prism instance '${instanceName.get()}' not found in $instances.\n" +
                    "Available instances:\n  $available\n" +
                    "Pass -Pbc.prism.instance=\"<name>\" to pick a different one."
            )
        }
        // Prism writes the game directory as "minecraft" for new instances and ".minecraft" for ones
        // imported from older launchers, so take whichever exists.
        val gameDir = listOf("minecraft", ".minecraft")
            .map { File(instance, it) }
            .firstOrNull { it.isDirectory }
            ?: throw GradleException("No minecraft/ directory inside $instance -- has the instance ever been launched?")
        File(gameDir, "mods")
    })

    doFirst {
        // Drop any jar from a previous install, so two versions of BuildCraft never load at once.
        val modsDir = destinationDir
        modsDir.mkdirs()
        val archiveBase = jarTask.get().archiveBaseName.get()
        modsDir.listFiles { f: File -> f.isFile && f.name.startsWith(archiveBase) && f.name.endsWith(".jar") }
            ?.forEach {
                logger.lifecycle("Removing previously installed ${it.name}")
                it.delete()
            }
    }

    doLast {
        logger.lifecycle("Installed ${jarTask.get().archiveFileName.get()} into $destinationDir")
    }
}
