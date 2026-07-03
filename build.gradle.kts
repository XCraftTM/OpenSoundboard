import java.util.Properties

// This is the *versioned* buildscript: Stonecutter runs it once per registered Minecraft
// version (the active one is in versions/active.txt). Loader dimension (NeoForge/Quilt) will
// be added later as sibling modules; for now this is the Fabric mod.
plugins {
    id("net.fabricmc.fabric-loom") apply false
    id("net.fabricmc.fabric-loom-remap") apply false
    id("maven-publish")
    id("com.modrinth.minotaur") version "2.8.7"
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
        // Simple Voice Chat mod (runtime; remapped by loom on obfuscated targets). Skipped if unset.
        vpropOrNull("voicechat.version")?.let { "modRuntimeOnly"("maven.modrinth:simple-voice-chat:fabric-$it") }
    } else {
        "implementation"("net.fabricmc:fabric-loader:${vprop("fabric.loader.version")}")
        "implementation"("net.fabricmc.fabric-api:fabric-api:${vprop("fabric.api.version")}")
        vpropOrNull("voicechat.version")?.let { "runtimeOnly"("maven.modrinth:simple-voice-chat:fabric-$it") }
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

// Bundle the pure :common classes (config models, platform interfaces) into the mod jar.
// They are on the classpath in dev via project(":common"), but Loom does not package a plain
// `implementation` subproject, so an installed jar hit NoClassDefFoundError for
// de.xcrafttm.opensoundboard.config.SoundboardConfig. remapJar (legacy) remaps the jar output;
// these classes have no Minecraft references so they pass through unchanged.
evaluationDependsOn(":common")
val commonMainOutput = project(":common")
    .extensions.getByType(SourceSetContainer::class.java)
    .getByName("main").output
tasks.named<Jar>("jar") {
    from(commonMainOutput)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ---------------------------------------------------------------------------
// Modrinth publishing. Fabric only — Simple Voice Chat (a required dependency) does
// not support Quilt, so we don't list it. Provide the token via the MODRINTH_TOKEN env
// var or a `modrinth_token` property in ~/.gradle/gradle.properties (never committed).
// One Modrinth version is published per Minecraft version, so switch active + publish each:
//   ./gradlew stonecutterSwitchTo<ver> && ./gradlew :<ver>:modrinth
// The changelog comes from the MODRINTH_CHANGELOG env var, else links to the GitHub release.
// ---------------------------------------------------------------------------
modrinth {
    // Validate without uploading: ./gradlew :<ver>:modrinth -PmodrinthDebug
    debugMode.set(project.hasProperty("modrinthDebug"))
    token.set(System.getenv("MODRINTH_TOKEN") ?: rootProject.findProperty("modrinth_token")?.toString() ?: "")
    projectId.set(rootProject.property("modrinth_project_id").toString())
    versionNumber.set(version.toString())
    versionName.set("OpenSoundboard $version")
    versionType.set("release")
    changelog.set(
        System.getenv("MODRINTH_CHANGELOG")
            ?: "Full changelog: https://github.com/XCraftTM/OpenSoundboard/releases/tag/v${rootProject.property("mod_version")}"
    )
    uploadFile.set(tasks.named(if (isLegacyObfuscated) "remapJar" else "jar"))
    gameVersions.set((vpropOrNull("modrinth.game.versions") ?: mcVersion).split(",").map { it.trim() })
    loaders.set(listOf("fabric"))
    dependencies {
        required.project("fabric-api")
        required.project("simple-voice-chat")
    }
}
