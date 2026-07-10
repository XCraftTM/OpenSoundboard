pluginManagement {
    // Read the active Stonecutter version so we can pick the right Loom variant/version.
    // NB: the pluginManagement block is compiled before top-level imports apply, so
    // java.util.Properties must be fully qualified here.
    val activeVersion = file("versions/active.txt").readText().trim()
    val supportedVersions = file("versions/supported.txt").readLines()
        .map(String::trim)
        .filter(String::isNotEmpty)
    require(activeVersion in supportedVersions) {
        "Unsupported active Stonecutter version '$activeVersion'. Expected one of: ${supportedVersions.joinToString()}"
    }
    val versionProps = java.util.Properties().apply {
        file("versions/$activeVersion/gradle.properties").inputStream().use { load(it) }
    }
    fun scVersion(name: String): String = versionProps.getProperty(name)
        ?: error("Missing Stonecutter version property '$name' for $activeVersion.")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
    }

    resolutionStrategy {
        eachPlugin {
            val loomVersion = scVersion("loom.version")
            // Legacy releases (1.21.x) are obfuscated; year-versioned (26.x) ship deobfuscated.
            val isLegacy = scVersion("minecraft.obfuscated").toBooleanStrict()
            when (requested.id.id) {
                "net.fabricmc.fabric-loom" -> useVersion(loomVersion)
                "net.fabricmc.fabric-loom-remap" -> {
                    if (isLegacy) useVersion(loomVersion)
                    // For deobfuscated (26.x) Minecraft, fabric-loom-remap does not exist;
                    // redirect to fabric-loom's artifact (it is declared apply false and never applied).
                    else useModule("net.fabricmc:fabric-loom:$loomVersion")
                }
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.5"
}

// Gradle normally expands an unqualified `build` to every subproject. Stonecutter nodes cannot
// share one active dependency/mapping configuration, so route only the root shorthand to the
// isolated multi-version task. Fully-qualified tasks such as :1.21.11:build remain untouched.
gradle.startParameter.setTaskNames(
    gradle.startParameter.taskNames.map { requested ->
        if (requested == "build" || requested == ":build") "buildAllJars" else requested
    }
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.parchmentmc.org")
        maven("https://maven.terraformersmc.com/")   // Mod Menu
        maven("https://maven.maxhenkel.de/repository/public")  // Simple Voice Chat
        maven("https://api.modrinth.com/maven") {
            content { includeGroup("maven.modrinth") }
        }
    }
}

rootProject.name = "OpenSoundboard"

// Mapping-neutral shared module (config models, platform interfaces). Not Minecraft-mapped.
include(":common")

val supportedVersions = file("versions/supported.txt").readLines()
    .map(String::trim)
    .filter(String::isNotEmpty)

stonecutter {
    create(rootProject) {
        versions(*supportedVersions.toTypedArray())
        vcsVersion = "1.21.11"
    }
}
