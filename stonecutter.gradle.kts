plugins {
    id("dev.kikugie.stonecutter")
}

// Active version is tracked in versions/active.txt so settings.gradle.kts pluginManagement
// can read it before the controller is configured. Stonecutter registers per-version
// "build", "runClient", etc. tasks plus a "chiseledBuild" that runs across every version.
stonecutter active file("versions/active.txt")

// Convenience: launch a specific version's client in one command. Each task switches the
// active version, then spawns a fresh Gradle invocation to run just that node's client
// (a nested invocation is needed because the active version is read at configuration time).
//   ./gradlew client_1_21_11
//   ./gradlew client_26_1_2   (26.x needs the Gradle daemon on Java 25 -> set JAVA_HOME)
listOf("1.21.1", "1.21.11", "26.1.2").forEach { id ->
    tasks.register("client_" + id.replace('.', '_')) {
        group = "opensoundboard"
        description = "Switch active to $id and run its Minecraft client"
        dependsOn("Set active project to $id")
        doLast {
            val windows = System.getProperty("os.name").lowercase().contains("win")
            val gradlew = if (windows) listOf("cmd", "/c", "gradlew.bat") else listOf("./gradlew")
            ProcessBuilder(gradlew + ":$id:runClient")
                .directory(rootDir)
                .inheritIO()
                .start()
                .waitFor()
        }
    }
}
