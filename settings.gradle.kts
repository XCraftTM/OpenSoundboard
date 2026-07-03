pluginManagement {
    // Read the active Stonecutter version so we can pick the right Loom variant/version.
    // NB: the pluginManagement block is compiled before top-level imports apply, so
    // java.util.Properties must be fully qualified here.
    val activeVersion = file("versions/active.txt").readText().trim()
    val versionProps = java.util.Properties().apply {
        file("versions/$activeVersion/gradle.properties").inputStream().use { load(it) }
    }
    fun scVersion(name: String): String = versionProps.getProperty(name)
        ?: error("Missing Stonecutter version property '$name' for $activeVersion.")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.parchmentmc.org")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.quiltmc.org/repository/release/")
    }

    resolutionStrategy {
        eachPlugin {
            val loomVersion = scVersion("loom.version")
            // Legacy releases (1.21.x) are obfuscated; year-versioned (26.x) ship deobfuscated.
            val isLegacy = scVersion("minecraft.version").startsWith("1.")
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
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.quiltmc.org/repository/release/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "OpenSoundboard"

// Pure, mapping-neutral module (config models, platform interfaces). Not Minecraft-mapped.
include(":common")

stonecutter {
    create(rootProject) {
        versions("1.21.1", "1.21.11", "26.1.2")
        vcsVersion = "1.21.11"
    }
}
