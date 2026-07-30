<p align="center">
  <img src="https://github.com/XCraftTM/OpenSoundboard/blob/master/logo.png?raw=true" width="128" alt="OpenSoundboard icon">
</p>

<h1 align="center">OpenSoundboard</h1>

<p align="center">
  A feature-rich soundboard mod for <a href="https://modrinth.com/plugin/simple-voice-chat">Simple Voice Chat</a>.<br>
  Play MP3s directly through voice chat — with per-sound controls, keybinds, and a built-in YouTube downloader.
</p>

<p align="center">
  <a href="https://raw.githubusercontent.com/XCraftTM/OpenSoundboard/refs/heads/master/LICENSE.txt"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-green"></a>
  <img alt="Client-side" src="https://img.shields.io/badge/environment-client-blue">
  <img alt="Fabric" src="https://img.shields.io/badge/mod loader-Fabric-dbd0b4">
</p>

---

## Features

**Soundboard UI** — Press **U** (configurable) to open a full-featured soundboard screen.

- Browse, search, and favorite your sounds
- Play / pause / stop with playback controls
- Timeline seeking and skip forward/back
- Set custom starting points for any sound
- Loop individual sounds or all sounds
- Double-click a sound to play instantly

**Per-Sound Controls**

- Independent **local volume** (what you hear) and **player volume** (what others hear)
- Optional synced volume mode for simpler control
- Global volume multiplier for both channels
- **Custom keybinds** — bind any key combo to instantly play a sound

**Built-in YouTube Downloader**

- Paste a YouTube URL and download audio directly into your sounds folder
- Progress bar and live log output
- Extracts audio automatically as MP3
- Automatically downloads yt-dlp and ffmpeg on first use

**Configuration**

- Play while muted
- Play sounds locally (hear your own sounds)
- Single song at a time mode
- Sync per-sound and global volume controls
- All settings accessible via Mod Menu or the in-game config button

## Supported Versions

| Minecraft              | Status                |
|------------------------|-----------------------|
| 26.2                   | ✅ Supported (latest)  |
| 26.1, 26.1.1, 26.1.2  | ✅ Supported           |
| 1.21.11                | ✅ Supported           |
| 1.21, 1.21.1           | ✅ Supported           |
| 1.21.10, 1.21.8, 1.21.5, 1.21.4 | ⚠️ Deprecated |

> **Deprecated** versions were supported under the previous per-version build and no longer receive updates. New releases target the supported versions above.

## Dependencies

| Mod | Required |
|-----|----------|
| [Fabric API](https://modrinth.com/mod/fabric-api) | Yes |
| [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) | Yes |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Optional |

## Getting Started

1. Install the mod and all required dependencies
2. Join a server (or singleplayer world) with Simple Voice Chat
3. Drop `.mp3` files into `.minecraft/opensoundboard/` — or use the built-in YouTube downloader
4. Press **U** to open the soundboard and start playing

## Building from Source

The project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build every supported Minecraft version from a single source tree.

See the [Stonecutter Development Guide](STONECUTTER.md) for version switching, single-version builds, full release builds, client launch tasks, and troubleshooting.

```bash
# `build` delegates to the Stonecutter multi-version build and collects build/jars/
JAVA_HOME=<jdk-25> ./gradlew build

# Equivalent explicit task
JAVA_HOME=<jdk-25> ./gradlew buildAllJars
```

The release jars are collected into `build/jars/` — one per version, e.g. `opensoundboard-0.3.0+mc26.2.jar`. (Each is also left in `versions/<version>/build/libs/`.)

To work on a single version, switch the active version first, then build or run just that node:

```bash
./gradlew stonecutterSwitchTo26.2   # pick the active version
./gradlew :26.2:build               # build just that node -> versions/26.2/build/libs/
./gradlew client_26_2               # switch + launch its client
```

> Minecraft 26.x builds require the Gradle daemon to run on Java 25 (set `JAVA_HOME` accordingly); 1.21.x versions target Java 21 but build fine on a Java 25 daemon too. The root `build` task delegates to the isolated Stonecutter builds; use a fully qualified task such as `:1.21.11:build` when working on only the active version.

## Credits

Thanks KamiKMB for creating the amazing Logo for the Mod.

## License

[MIT](LICENSE.txt)
