<p align="center">
  <img src="common/src/main/resources/assets/opensoundboard/icon.png" width="128" alt="OpenSoundboard icon">
</p>

<h1 align="center">OpenSoundboard</h1>

<p align="center">
  A feature-rich soundboard mod for <a href="https://modrinth.com/plugin/simple-voice-chat">Simple Voice Chat</a>.<br>
  Play MP3s directly through voice chat — with per-sound controls, keybinds, and a built-in YouTube downloader.
</p>

<p align="center">
  <a href="https://github.com/XCraftTM/OpenSoundboard/blob/main/LICENSE.txt"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-green"></a>
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
- Audio-only or full video download modes
- Automatically downloads yt-dlp and ffmpeg on first use

**Configuration**

- Play while muted
- Play sounds locally (hear your own sounds)
- Single song at a time mode
- Sync per-sound and global volume controls
- All settings accessible via Mod Menu or the in-game config button

## Supported Versions

| Minecraft  | Status      |
|------------|-------------|
| 1.21.11    | Supported   |
| 1.21.10    | Supported   |
| 1.21.8     | Supported   |
| 1.21.5     | Supported   |
| 1.21.4     | Supported   |
| 1.21.1     | Supported   |

## Dependencies

| Mod | Required |
|-----|----------|
| [Fabric API](https://modrinth.com/mod/fabric-api) | Yes |
| [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) | Yes |
| [owo-lib](https://modrinth.com/mod/owo-lib) | Yes |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Optional |

## Getting Started

1. Install the mod and all required dependencies
2. Join a server (or singleplayer world) with Simple Voice Chat
3. Drop `.mp3` files into `.minecraft/opensoundboard/` — or use the built-in YouTube downloader
4. Press **U** to open the soundboard and start playing

## Building from Source

```bash
# Build all versions and collect JARs into build/jars/
./gradlew collectJars
```

The release-ready JARs for every supported Minecraft version will be in `build/jars/`.

## License

[MIT](LICENSE.txt)
