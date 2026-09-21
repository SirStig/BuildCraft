// The root project deliberately applies no `java` plugin: the 1.12.2 source trees
// (common/, src/, tests/) still sit at the repository root as the reference being ported
// from, and must not be fed to the compiler.

allprojects {
    group = rootProject.property("group") as String
    version = rootProject.property("version") as String
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    }

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // BuildCraft carries a lot of 1.12-era code; keep the deprecation firehose off by
        // default but leave the flag here so it can be switched on per-module while porting.
        options.compilerArgs.add("-Xlint:-deprecation,-unchecked")
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// Convenience aggregate: build everything that does not need a Minecraft artifact.
tasks.register("buildCommon") {
    group = "build"
    description = "Builds the version-independent modules (expression, shared)."
    dependsOn(":expression:build", ":shared:build")
}

// Convenience aggregate: install both platform jars into their Prism Launcher instances.
tasks.register("installToPrism") {
    group = "buildcraft"
    description = "Builds both targets and installs them into their Prism Launcher instances."
    dependsOn(":neoforge-26x:installToPrism", ":neoforge-1201:installToPrism")
}
