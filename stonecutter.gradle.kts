plugins {
    id("dev.kikugie.stonecutter")
}

// Active version is tracked in versions/active.txt so settings.gradle.kts pluginManagement
// can read it before the controller is configured. Stonecutter registers per-version
// "build", "runClient", etc. tasks on each version node (:1.21.1, :1.21.11, :26.1.2).
stonecutter active file("versions/active.txt")

// Single source of truth for the Stonecutter anchor nodes, shared with settings.gradle.kts.
val supportedVersions = file("versions/supported.txt").readLines()
    .map(String::trim)
    .filter(String::isNotEmpty)
val projectRoot = rootDir
val activeVersionFile = projectRoot.resolve("versions/active.txt")
val nestedLogFile = projectRoot.resolve("build/nested-gradle.log")

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
    nestedLogFile.parentFile.mkdirs()
    nestedLogFile.appendText(
        "${System.lineSeparator()}>>> gradlew ${args.joinToString(" ")}${System.lineSeparator()}"
    )
    logger.lifecycle("Nested Gradle: ${args.joinToString(" ")}")
    val pb = ProcessBuilder(launcher + args.toList())
        .directory(projectRoot)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(nestedLogFile))
    // The spawned launcher must find a JDK; the daemon's ambient env may not carry JAVA_HOME,
    // so pin it to the JVM this build is already running on (Java 25 when invoked for 26.x).
    pb.environment()["JAVA_HOME"] = System.getProperty("java.home")
    val exit = pb.start().waitFor()
    if (exit != 0) throw GradleException(
        "`gradlew ${args.joinToString(" ")}` failed (exit $exit); see ${nestedLogFile.relativeTo(projectRoot)}"
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
        doLast {
            nestedLogFile.writeText("")
            gradlew(":$id:runClient")
        }
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
        val stagingDir = projectRoot.resolve("build/jars-staging")
        val originalVersion = activeVersionFile.readText().trim()
        require(originalVersion in supportedVersions) {
            "Unsupported active Stonecutter version '$originalVersion'"
        }

        delete(stagingDir)
        check(stagingDir.mkdirs()) { "Could not create $stagingDir" }
        nestedLogFile.writeText("")

        var buildFailure: Throwable? = null
        try {
            supportedVersions.forEach { v ->
                // Use the space-free switch alias: a spaced task name ("Set active project to X")
                // gets split into separate task tokens when passed through cmd /c on Windows.
                gradlew("stonecutterSwitchTo$v")
                // Clean the node first: Stonecutter's per-node compileJava can go falsely
                // UP-TO-DATE across switches and package stale classes.
                gradlew(":$v:clean", ":$v:build")

                val releaseJars = projectRoot.resolve("versions/$v/build/libs").listFiles()
                    ?.filter { it.extension == "jar" && !it.name.endsWith("-sources.jar") }
                    .orEmpty()
                if (releaseJars.size != 1) {
                    throw GradleException(
                        "Expected exactly one release jar for $v, found ${releaseJars.size}: " +
                            releaseJars.joinToString { it.name }
                    )
                }
                releaseJars.single().copyTo(
                    stagingDir.resolve(releaseJars.single().name),
                    overwrite = true
                )
            }
        } catch (failure: Throwable) {
            buildFailure = failure
            delete(stagingDir)
            throw failure
        } finally {
            if (activeVersionFile.readText().trim() != originalVersion) {
                try {
                    gradlew("stonecutterSwitchTo$originalVersion")
                } catch (resetFailure: Throwable) {
                    delete(stagingDir)
                    buildFailure?.addSuppressed(resetFailure) ?: throw resetFailure
                }
            }
        }

        // Keep the last known-good output untouched until every version has built and validated.
        sync {
            from(stagingDir)
            into(outDir)
        }
        delete(stagingDir)
        logger.lifecycle("Release jars collected into build/jars/")
    }
}

// Expose the conventional root task in task listings. settings.gradle.kts routes a bare
// `gradlew build` directly to buildAllJars before Gradle expands it to all version nodes.
tasks.register("build") {
    group = "build"
    description = "Build all supported Minecraft versions via Stonecutter"
    dependsOn("buildAllJars")
}
