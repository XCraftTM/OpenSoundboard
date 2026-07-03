import java.util.Properties

// This is the *versioned* buildscript: Stonecutter runs it once per registered Minecraft
// version (the active one is in versions/active.txt). Loader dimension (NeoForge/Quilt) will
// be added later as sibling modules; for now this is the Fabric mod.
plugins {
    id("net.fabricmc.fabric-loom") apply false
    id("net.fabricmc.fabric-loom-remap") apply false
    id("maven-publish")
}

// --- Per-version properties (from versions/<active>/gradle.properties) -------------------
val active = rootProject.file("versions/active.txt").readText().trim()
val vprops = Properties().apply {
    rootProject.file("versions/$active/gradle.properties").inputStream().use { load(it) }
}
fun vprop(name: String): String = vprops.getProperty(name)
    ?: error("Missing version property '$name' for $active.")
fun vpropOrNull(name: String): String? = vprops.getProperty(name)?.takeIf { it.isNotBlank() }

val mcVersion = vprop("minecraft.version")
// Legacy releases (1.21.x) ship obfuscated -> loom-remap + Mojang mappings.
// Year-versioned releases (26.x) ship deobfuscated -> plain loom, no mappings.
val isLegacyObfuscated = mcVersion.startsWith("1.")

if (isLegacyObfuscated) apply(plugin = "net.fabricmc.fabric-loom-remap")
else apply(plugin = "net.fabricmc.fabric-loom")

version = "${rootProject.property("mod_version")}+mc$mcVersion"
group = rootProject.property("maven_group").toString()

base {
    archivesName.set(rootProject.property("archives_base_name").toString())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(vprop("java.version").toInt()))
    }
    withSourcesJar()
}

// Loom injects project-level repositories, and dependencyResolutionManagement uses
// PREFER_PROJECT, so the mod-dependency mavens must be declared here too.
repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.parchmentmc.org")
    maven("https://maven.terraformersmc.com/")            // Mod Menu
    maven("https://maven.maxhenkel.de/repository/public") // Simple Voice Chat
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

val loom = the<net.fabricmc.loom.api.LoomGradleExtensionAPI>()

dependencies {
    "minecraft"("com.mojang:minecraft:$mcVersion")

    if (isLegacyObfuscated) {
        "mappings"(loom.layered {
            officialMojangMappings()
            vpropOrNull("parchment.dependency")?.let { parchment(it) }
        })
        "modImplementation"("net.fabricmc:fabric-loader:${vprop("fabric.loader.version")}")
        "modImplementation"("net.fabricmc.fabric-api:fabric-api:${vprop("fabric.api.version")}")
    } else {
        "implementation"("net.fabricmc:fabric-loader:${vprop("fabric.loader.version")}")
        "implementation"("net.fabricmc.fabric-api:fabric-api:${vprop("fabric.api.version")}")
    }

    // Pure, mapping-neutral shared code (config models, platform interfaces).
    "implementation"(project(":common"))

    // Simple Voice Chat API — a plain library (no Minecraft classes), so it needs no remap
    // on either mapping path. Provided at runtime by the Simple Voice Chat mod.
    "compileOnly"("de.maxhenkel.voicechat:voicechat-api:${vprop("voicechat.api.version")}")
}

tasks.processResources {
    val replacements = mapOf(
        "version" to version.toString(),
        "minecraftVersion" to mcVersion,
    )
    inputs.properties(replacements)
    filesMatching("fabric.mod.json") { expand(replacements) }
}
