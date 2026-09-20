/*
 * buildcraft-expression: BuildCraft's expression/scripting engine.
 *
 * This module is completely version-independent -- it contains no Minecraft or loader
 * references at all -- so a single artifact is shared by every platform target.
 */

plugins {
    java
    `java-library`
}

base.archivesName = "buildcraft-expression"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.shared.get().toInt())
}

// The engine's numeric//function node classes are combinatorial (every primitive type
// crossed with every arity), so they are machine-generated rather than hand-written.
// `src/generator` holds the generator; it runs as part of compileJava.
val generator: SourceSet by sourceSets.creating

val autogenDir = layout.buildDirectory.dir("generated/sources/autogen/java")

val generateExpressionSources by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Regenerates the combinatorial expression node classes."
    classpath = generator.runtimeClasspath
    mainClass = "buildcraft.meta.generate.AutoGenerator"
    outputs.dir(autogenDir)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("-out", autogenDir.get().asFile.absolutePath, "-run", "-quiet")
    })
    doFirst {
        // AutoGenerator refuses to write into a non-empty directory.
        val out = autogenDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
    }
}

sourceSets {
    main {
        java.srcDir(generateExpressionSources.map { autogenDir })
    }
}

dependencies {
    api(libs.jetbrains.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
