plugins {
    id("dev.kikugie.stonecutter")
}

// Active version is tracked in versions/active.txt so settings.gradle.kts pluginManagement
// can read it before the controller is configured. Stonecutter registers per-version
// "build", "runClient", etc. tasks on each version node (:1.21.1, :1.21.11, :26.1.2).
stonecutter active file("versions/active.txt")

// Every Minecraft version the mod is built for (the Stonecutter anchor nodes). Ordered so the
// last build leaves the tree on the 26.x node; buildAllJars resets to the dev default at the end.
val supportedVersions = listOf("1.21.1", "1.21.11", "26.1.2")
val devVersion = "1.21.11" // vcsVersion / dev default the tree is reset to after a full build
val projectRoot = rootDir

// Spawn a fresh Gradle invocation and fail the calling task on a non-zero exit. A subprocess is
// required whenever the active version must change between steps: the active version selects the
// Loom variant (remap for 1.21.x vs plain for 26.x) and the per-version dependencies, and it is
// resolved at configuration time -- so switching + building in one invocation uses the stale version.
fun gradlew(vararg args: String) {
    val windows = System.getProperty("os.name").lowercase().contains("win")
    // Invoke the wrapper by absolute path: `cmd /c gradlew.bat` searches PATH, not the working
    // directory, so a bare name is not found even with .directory() set.
    val launcher = if (windows)
        listOf("cmd", "/c", projectRoot.resolve("gradlew.bat").absolutePath)
    else
        listOf(projectRoot.resolve("gradlew").absolutePath)
    val logFile = projectRoot.resolve("build/nested-gradle.log")
    logFile.parentFile.mkdirs()
    val pb = ProcessBuilder(launcher + args.toList())
        .directory(projectRoot)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
    // The spawned launcher must find a JDK; the daemon's ambient env may not carry JAVA_HOME,
    // so pin it to the JVM this build is already running on (Java 25 when invoked for 26.x).
    pb.environment()["JAVA_HOME"] = System.getProperty("java.home")
    val exit = pb.start().waitFor()
    if (exit != 0) throw GradleException(
        "`gradlew ${args.joinToString(" ")}` failed (exit $exit); see build/nested-gradle.log"
    )
}

// Convenience: launch a specific version's client in one command. The active switch is a task
// dependency; the client itself runs in a fresh invocation that re-reads the updated active.txt.
//   ./gradlew client_1_21_11
//   ./gradlew client_26_1_2   (26.x needs the Gradle daemon on Java 25 -> set JAVA_HOME)
supportedVersions.forEach { id ->
    tasks.register("client_" + id.replace('.', '_')) {
        group = "opensoundboard"
        description = "Switch active to $id and run its Minecraft client"
        dependsOn("Set active project to $id")
        doLast { gradlew(":$id:runClient") }
    }
}

// Build every supported version and collect the release jars into build/jars/. Each version is
// built in its own invocation (see gradlew() above) so both sides of the obfuscation boundary
// get the correct Loom variant. Run under JAVA_HOME = Java 25 so the 26.x node's daemon is happy;
// the 1.21.x nodes build fine on a Java 25 daemon too (they target Java 21 via toolchains).
//   JAVA_HOME=<jdk25> ./gradlew buildAllJars
tasks.register("buildAllJars") {
    group = "opensoundboard"
    description = "Build every supported version and collect release jars into build/jars/"
    doLast {
        val outDir = projectRoot.resolve("build/jars")
        outDir.mkdirs()
        outDir.listFiles()?.filter { it.extension == "jar" }?.forEach { it.delete() }
        supportedVersions.forEach { v ->
            // Use the space-free switch alias: a spaced task name ("Set active project to X")
            // gets split into separate task tokens when passed through cmd /c on Windows.
            gradlew("stonecutterSwitchTo$v")
            // Clean the node first: Stonecutter's per-node compileJava can go falsely UP-TO-DATE
            // across version switches and package stale classes, so force a fresh compile.
            gradlew(":$v:clean", ":$v:build")
            projectRoot.resolve("versions/$v/build/libs").listFiles()
                ?.filter { it.name.endsWith(".jar") && !it.name.endsWith("-sources.jar") }
                ?.forEach { it.copyTo(outDir.resolve(it.name), overwrite = true) }
        }
        gradlew("stonecutterSwitchTo$devVersion") // reset to the dev default
        logger.lifecycle("Release jars collected into build/jars/")
    }
}
