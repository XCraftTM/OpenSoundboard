plugins {
    id("dev.kikugie.stonecutter")
}

// Active version is tracked in versions/active.txt so settings.gradle.kts pluginManagement
// can read it before the controller is configured. Stonecutter registers per-version
// "build", "runClient", etc. tasks plus a "chiseledBuild" that runs across every version.
stonecutter active file("versions/active.txt")
