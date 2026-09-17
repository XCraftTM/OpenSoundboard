## OpenSoundboard 0.5.0 - Redesign & Plasmo Voice Update

This release rebuilds the whole interface, adds Plasmo Voice support, makes voice chat optional, turns the sound wheel into a pie menu, and lets you search YouTube right from the game.

### Highlights

- Added support for **Minecraft 26.3**.
- Added **Plasmo Voice** support. Simple Voice Chat and Plasmo Voice are now both **optional**: without a voice chat, sounds simply play locally on your computer.
- **Redesigned interface** in both styles:
  - **Modern** has a cleaner look with pixel icons and a customizable **accent color** (8 presets or any hex color), background tone, panel opacity, and rounded corners.
  - **Vanilla** now uses Minecraft's real buttons, sliders, tabs, and menu backgrounds, and long labels scroll just like in vanilla menus.
- **New settings screen** with four pages: General, Audio, Sound Wheel, and Appearance. Every option has a short description. The Accessibility options (UI width, height, and text size) now live on the Appearance page.
- **Folders inside folders**: organize sounds as deep as you like, with clickable breadcrumbs to jump back up.
- The **Sound Wheel** is now a pie menu. Point in a direction to pick a slice; names wrap over several lines, and the center shows the full name of the hovered sound.
- The YouTube Downloader is now the **Downloader**, with a new **Search** mode: results show thumbnail, title, channel, length, and views, and download with one click.

### Soundboard

- Folders show how many sounds they contain, and search looks through all subfolders.
- The sound list shows each sound's length.
- Double-click a sound to play it.
- Press Enter to play the selected sound, or the first search result.
- Just start typing to search.
- The header shows where sounds are currently playing through (Simple Voice Chat, Plasmo Voice, or Local only).
- Fixed per-sound keybinds not working for sounds inside subfolders.

### Downloader

- Switch between pasting a link and searching YouTube.
- The yt-dlp log is hidden by default and can be shown with one button.
- New "Save to last folder" toggle downloads into the folder you last opened instead of the main sounds folder.

### Sound Wheel

- Slices use the space they have: labels wrap over multiple lines.
- Navigate nested folders directly in the wheel.
- The Wheel Layout editor uses the same pie design.

### Audio

- New "Play locally without voice chat" option (on by default), and the current output is shown in Settings → Audio.
- MP3s play even without Simple Voice Chat installed. When it is installed, its faster native decoder is still used.

### Other Changes

- Mod Menu now shows a full description, links to Modrinth and GitHub, credits, and update checks.
- Updated to the latest Fabric API, Simple Voice Chat 2.6.23, and Mod Menu for every supported version.
- Refreshed English and German translations.

### Good to Know

- Fabric API has to be up to date, since the minimum required version was raised.
- With Plasmo Voice, sounds are sent while your microphone activation is active (voice activation, or while holding push-to-talk).
- On Minecraft 26.3, per-sound keybinds created on older Minecraft versions need to be set again, because 26.3 changed how keys are stored.

### Supported Minecraft Versions

- 1.21 and 1.21.1
- 1.21.11
- 26.1, 26.1.1, and 26.1.2
- 26.2
- 26.3

Requires Fabric API. Simple Voice Chat or Plasmo Voice is needed to play sounds to other players. Mod Menu is optional.

---

## OpenSoundboard 0.4.0 - UI & Downloading Update

This release makes OpenSoundboard feel more at home in Minecraft, adds extensive UI sizing controls, and lets YouTube downloads keep running outside the downloader screen.

### Highlights

- Added full support for **Minecraft 26.2**.
- Added an optional **Vanilla Components** mode for Minecraft-style buttons, sliders, text fields, toggles, tooltips, and scrollbars.
- Added a new **Accessibility** screen with adjustable UI width, UI height, and font size, plus a one-click reset. The refreshed defaults are 80% width, 90% height, and 100% font size.
- YouTube downloads now continue after closing the downloader screen. Reopen it at any time to see the current log and progress or cancel the job.
- Added native top-right Minecraft notifications for download preparation, progress, completion, failure, and cancellation.

### Improvements & Fixes

- Restored Mod Menu integration so OpenSoundboard settings can be opened from Mod Menu again.
- Made scrollbars clickable and draggable.
- Improved screen proportions, control sizing, text scaling, vertical alignment, and long-label truncation across the interface.
- Made downloader actions more compact and reorganized appearance and accessibility settings into their own category.
- Fixed release packaging so shared configuration and platform classes are included in installed jars, preventing startup crashes caused by missing classes.
- Improved the multi-version Stonecutter build and release pipeline for more reliable GitHub and Modrinth artifacts.

### Supported Minecraft Versions

- 1.21 and 1.21.1
- 1.21.11
- 26.1, 26.1.1, and 26.1.2
- 26.2

Requires Fabric API and Simple Voice Chat. Mod Menu is optional.

---

## OpenSoundboard 0.2.1

### Changes

- Improved Song List Performance
- General Fixes and Optimizations
- Fixed a Memory Leak that happened cause of the Preloading of all Tracks
- Improved Pre-Loading of Track Durations
- Song List Performance is now much better, even with a large amount of Tracks

---

## OpenSoundboard 0.2.0

### New Features

- **Sound Wheel Overlay** – Hold a configurable keybind to open a radial sound wheel. Release over a sound to play it. Supports paging via scroll wheel.
- **Wheel Layout Editor** – Custom wheel mode lets you manually assign specific sounds to each slot. Accessible from the Config screen.
- **Song Picker Screen** – Full-screen sound browser with search, preview (plays first 1/3, up to 15s), and favourite indicators for selecting wheel slots.
- **Subfolder Support** – Optionally show subfolders in the sound list (depth 1). Navigate into folders and back out with a dedicated back button.
- **Sort Options** – Sort the sound list by Name, Creation Date, or Length, with ascending/descending toggle.
- **Search Bar** – Filter sounds in real time in both the main screen and the song picker.
- **Favourites-Only Wheel Mode** – Option to only show favourited sounds in the wheel overlay.
- **Custom Wheel Layout Config** – Wheel slot assignments are saved to a separate `opensoundboard-wheel.json` config file.

### Improvements

- Song list now remembers the last opened folder across sessions.
- Wheel overlay remembers the last opened folder and current page while the key is held.
- All features backported to **1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10** (previously only 1.21.11 had the wheel/editor/picker screens).
- German translation (`de_de`) fully updated to match all new keys.
- `src/` legacy folder removed (was unused).
- `GuiTools` moved into the common package, shared across all versions.

### Bug Fixes

- Fixed wheel overlay flickering when holding the keybind.
- Fixed BOM characters in Java source files causing compile errors on older versions.
- Fixed `KeyBinding.Category` not existing in 1.21.1 (uses plain String category instead).
- Fixed `HoldableKeyBinding` using reflection that failed silently in 1.21.1; now uses `setBoundKey` override for reliable GLFW polling.
- Fixed button spacing in 4- and 6-button wheel layouts.
- Fixed folder navigation scroll position resetting on refresh.
