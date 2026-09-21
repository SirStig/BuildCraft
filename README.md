## Welcome to BuildCraft on GitHub

> **This branch is a work-in-progress NeoForge port.**
> BuildCraft 8.0.x targets Minecraft 1.12.2; this branch is porting it to NeoForge as
> **BuildCraft 10**, on Minecraft 26.x (primary) and 1.20.1 (compatibility). The 1.12.2 source tree under
> `common/` is kept as the reference being ported from and is not compiled.
> See **[PORTING.md](PORTING.md)** for the current status, the build commands, and the
> API migration reference.

### Reporting an issue

Please open an issue for a bug report only if:

* you are sure the bug is caused by BuildCraft and not by any other mod,
* you have at least one of the following:
  * a crash report, 
  * means of reproducing the bug in question,
  * screenshots/videos/etc. to demonstrate the bug.

**If you are not sure if a bug report is valid, please use the "Ask Help!" subforum.**

Please only use **official BuildCraft releases** for any kind of bug reports unless otherwise told to do by the BuildCraft team. Custom builds (for instance from Jenkins) are unsupported, often buggy and will **not** get any support from the developers.

Please check if the bug has been reported beforehand. Also, provide the version of BuildCraft used - if it's a version compiled from source, link to the commit/tree you complied from.

Please mention if you are using MCPC+, Cauldron, OptiFine, FastCraft or any other mods which optimize or otherwise severely modify the functioning of the Minecraft engine. That is very helpful when trying to reproduce a bug.

Please do not open issues for features unless you are a member of the BuildCraft team. For that, use the "Feature Requests" subforum.

BuildCraft, being an open-source project, gives you the right to submit a pull request if a particular fix or feature is important to you. However, if the change in question is major, please contact the team beforehand - we wish to prevent wasted effort.

### Contributing

If you wish to submit a pull request to fix bugs or broken behaviour feel free to do so. If you would like to add 
features or change existing behaviour or balance, please discuss it on discord before submitting a PR (https://discord.gg/v4geqgA).

Do not submit pull requests which solely "fix" formatting. As these kinds of changes are usually very intrusive in commit history and everyone has their own idea what "proper formatting" is, they should be done by one of the main contributors. 
Please only submit "code cleanup", if the changes actually have a substantial impact on readability.

PR implementing new features or changing large portions of code are helpful. But if you're doing such a change and if it gets accepted, please don't "fire and forget". Complex changes are introducing bugs, and as thorough as testing and peer review may be, there will be bugs. Please carry on playing your changes after initial commit and fix residual issues. It is extremely frustrating for others to spend days fixing regressions introduced by unmaintained submissions.

#### Frequently reported

* java.lang.AbstractMethodError, java.lang.NoSuchMethodException
  * A mod has not updated to the current BuildCraft API
  * You are not using the correct version of BuildCraft for your Forge/Minecraft versions
  * You are using the dev version on a normal game instance (or vice versa)
* Render issue (Quarry causes flickering) - Try without OptiFine first! This is a known issue with some versions of OptiFine.

### Compiling and packaging BuildCraft

These instructions are for the NeoForge port on this branch. For the 1.12.2 build, see the
`8.0.x-1.12.2` branch.

1. Install `Git` (found [here](https://git-scm.com/)) and a JDK. Minecraft 26.x needs
   **Java 25**; Gradle will download a matching toolchain if you do not have one. Do not use
   Gradle 8.x — it cannot drive a Java 25 toolchain, and the wrapper here is already 9.x.
2. Clone the BuildCraft repository.
3. Fetch the submodules: `git submodule update --init`.
4. From the repository root, run one of:
    * `./gradlew build` to build every target.
    * `./gradlew :neoforge-26x:build` for Minecraft 26.3 (the default), or add
      `-Pbc.mc26=26.1` / `-Pbc.mc26=26.2` to pick another 26.x release.
    * `./gradlew :neoforge-1201:build` for Minecraft 1.20.1.
    * `./gradlew :neoforge-26x:runClient` to launch the game with the mod loaded.
    * On Windows: use `gradlew.bat` instead of `./gradlew`.
5. The mod jars are in `platforms/<target>/build/libs/`.

Your directory structure should look like this before running gradle:
***

    baseDir
    \- BuildCraft
     |- buildcraft_resources
     |- common
     |- ...
     \- BuildCraftAPI
      |- api
      |- ...
     \- BuildCraft-Localization
      |- lang
      |- ...

***

And like this after running gradle:
***

    basedir
    \- BuildCraft
     |- .gradle
     |- build
     |- buildcraft_resources
     |- common
     |- ...
     \- BuildCraftAPI
      |- api
      |- ...
     \- BuildCraft-Localization
      |- lang
      |- ...

***

### Localizations

Localizations can be submitted [here](https://github.com/BuildCraft/BuildCraft-Localization). Localization PRs against
this repository will have to be rejected.

### Depending on BuildCraft

Instructions for depending on BC 7.1.x can be found [here](https://github.com/BuildCraft/BuildCraft/blob/7.1.x/README.md) (for 1.7.10).

8.0.x hasn't been finished yet, so there are no instructions for depending on it :(

The following instructions are for BC 7.99.12 (1.12.2):

Add the following to your build.gradle file:
```
repositories {
    maven {
        name "BuildCraft"
        url = "https://mod-buildcraft.com/maven"
    }
}
````

If you want to depend on JUST the API then do this:
````
dependencies {
    deobfCompile "com.mod-buildcraft:buildcraft-api:7.99.12"
}
````

If you want to depend on JUST the lib then do this:
````
dependencies {
    deobfCompile "com.mod-buildcraft:buildcraft-lib:7.99.12"
}
````

If you want to depend on the whole of buildcraft do this:
```
dependencies {
    deobfCompile "com.mod-buildcraft:buildcraft:7.99.12"
}
```
Where `7.99.12` is the desired version of BuildCraft.

## Licensing

BuildCraft is under two licenses, and the BuildCraft 10 NeoForge port keeps both -- neither
one permits relicensing code you did not write, so a ported file stays under whatever license
it already had, with the port's copyright added alongside the original notice.

| Code | License | File |
| --- | --- | --- |
| `buildcraft.api.*` | MIT | `LICENSE.API` |
| Everything else ported from BuildCraft 8.0.x | MPL 2.0 | `LICENSE-NEW` |
| Files written for this port, not derived from earlier BuildCraft code | MIT | `LICENSE.PORT` |

Every source file says which of the three applies to it. `LICENSE` is the original MMPL,
which BuildCraft moved off; `license_checker/` is the tooling the BuildCraft team used to
collect the per-contributor agreement that move required, and is kept for reference.
