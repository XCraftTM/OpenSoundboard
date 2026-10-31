package de.xcrafttm.opensoundboard.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import de.xcrafttm.opensoundboard.platform.PlatformBootstrap;

import java.io.File;
import java.io.IOException;

@Data
public class SoundboardConfig {

    public static final float DEFAULT_UI_WIDTH_SCALE = 0.8f;
    public static final float DEFAULT_UI_HEIGHT_SCALE = 0.9f;
    public static final float DEFAULT_FONT_SCALE = 1.0f;
    public static final String DEFAULT_ACCENT_COLOR = "#5FAE6E";
    public static final String DEFAULT_SURFACE_TONE = "graphite";
    public static final float DEFAULT_PANEL_OPACITY = 0.96f;
    /** Pitch modes: a random pitch inside a range per play, a fixed pitch per sound, or one for all. */
    public static final String PITCH_RANDOM = "random";
    public static final String PITCH_PER_SOUND = "per_sound";
    public static final String PITCH_GLOBAL = "global";
    public static final float MIN_PITCH = 0.5f;
    public static final float MAX_PITCH = 2.0f;
    /** -3 and +3 semitones (the main screen's pitch range slider works in semitones). */
    public static final float DEFAULT_RANDOM_PITCH_MIN = 0.8408964f;
    public static final float DEFAULT_RANDOM_PITCH_MAX = 1.1892071f;

    private static final File BASE_DIR = new File(PlatformBootstrap.client().configDirectory(), "opensoundboard");

    /**
     * Global options
     */
    private static final File CONFIG_FILE = new File(BASE_DIR, "config.json");

    /**
     * Per-sound options
     */
    private static final File SOUNDS_FILE = new File(BASE_DIR, "sounds.json");

    public static SoundboardConfig data;

    // Stores per-sound settings separately
    private static SoundStore soundStore = new SoundStore();

    boolean playWhileMuted = true;
    boolean playLocally = true;
    boolean localPlayback = true; // play on this computer when no voice chat is connected
    String preferredVoiceChat = "auto"; // backend id to use when several voice chats are connected
    float globalLocalVolume = 0.2f;
    float globalPlayerVolume = 0.2f;
    boolean syncGlobalVolume = false;
    boolean masterVolumeOnMainScreen = false; // main screen shows master sliders instead of per-sound ones
    boolean singleSongAtATime = true;
    boolean loopAll = false;
    boolean syncAudio = false;
    // Pitch controls: the main screen's pitch row only shows (and pitch only changes) when
    // randomPitchControls is on. Pitch never changes the speed.
    boolean randomPitchControls = false;
    /** What the pitch row sets: {@link #PITCH_RANDOM}, {@link #PITCH_PER_SOUND} or {@link #PITCH_GLOBAL}. */
    String pitchMode = PITCH_RANDOM;
    /** Pitch in semitones (-12..+12) for every sound in {@link #PITCH_GLOBAL} mode. */
    float fixedPitch = 0f;
    /** The main screen's pitch checkbox: whether pitch changes apply at all. */
    boolean randomPitch = false;
    float randomPitchMin = DEFAULT_RANDOM_PITCH_MIN;
    float randomPitchMax = DEFAULT_RANDOM_PITCH_MAX;
    int skipAmountSeconds = 5;
    String keybindMode = "play_stop";
    int wheelSoundsPerPage = 8;
    boolean wheelFavoritesOnly = false;
    boolean wheelCustomLayout = false;
    boolean vanillaComponents = false;
    float uiWidthScale = DEFAULT_UI_WIDTH_SCALE;
    float uiHeightScale = DEFAULT_UI_HEIGHT_SCALE;
    float fontScale = DEFAULT_FONT_SCALE;
    String accentColor = DEFAULT_ACCENT_COLOR;
    String surfaceTone = DEFAULT_SURFACE_TONE;
    float panelOpacity = DEFAULT_PANEL_OPACITY;
    boolean roundedCorners = true;
    boolean showSubfolders = true;
    String sortMode = "name";
    boolean sortAscending = true;
    String lastOpenedFolder = null; // folder path relative to the sounds folder, null = root
    boolean downloadToLastFolder = false; // downloader saves into lastOpenedFolder instead of the root

    private static void ensureConfigDir() {
        if (!BASE_DIR.exists()) {
            //noinspection ResultOfMethodCallIgnored
            BASE_DIR.mkdirs();
        }
    }

    /**
     * Load global config and per-sound store.
     *
     * Migration:
     * - If config.json exists and contains an embedded "sounds" map from older versions,
     *   move it into sounds.json and remove it from memory.
     */
    public static void load() {
        ensureConfigDir();

        // global
        data = JsonFiles.read(CONFIG_FILE, SoundboardConfig.class, SoundboardConfig::new);
        data.normalizeAccessibility();

        // per-sound
        soundStore = JsonFiles.read(SOUNDS_FILE, SoundStore.class, SoundStore::new);
        if (soundStore.getSounds() == null) soundStore.setSounds(new java.util.HashMap<>());
        soundStore.getSounds().values().removeIf(java.util.Objects::isNull);

        // migration from old single-file storage: if we loaded 'sounds' directly into config
        if (data.sounds != null && !data.sounds.isEmpty()) {
            if (soundStore.getSounds() == null) soundStore.setSounds(new java.util.HashMap<>());
            soundStore.getSounds().putAll(data.sounds);
            data.sounds.clear();
            save();
        } else {
            // ensure files exist on disk
            save();
        }

        // never keep the embedded map around anymore
        data.sounds = null;

        // load wheel layout config
        WheelLayoutConfig.load();
    }

    /** Whether pitch changes apply: the feature is enabled in the settings and ticked on the main screen. */
    public boolean isPitchActive() {
        return randomPitchControls && randomPitch;
    }

    /** Whether sounds play at a fixed pitch (per sound or one for all) instead of a random one. */
    public boolean isFixedPitchActive() {
        return isPitchActive() && !PITCH_RANDOM.equals(pitchMode);
    }

    private void normalizeAccessibility() {
        uiWidthScale = normalizeScale(uiWidthScale, DEFAULT_UI_WIDTH_SCALE, 0.6f, 1.0f);
        uiHeightScale = normalizeScale(uiHeightScale, DEFAULT_UI_HEIGHT_SCALE, 0.7f, 1.0f);
        fontScale = normalizeScale(fontScale, DEFAULT_FONT_SCALE, 0.75f, 1.25f);
        panelOpacity = normalizeScale(panelOpacity, DEFAULT_PANEL_OPACITY, 0.6f, 1.0f);
        randomPitchMin = normalizeScale(randomPitchMin, DEFAULT_RANDOM_PITCH_MIN, MIN_PITCH, MAX_PITCH);
        randomPitchMax = normalizeScale(randomPitchMax, DEFAULT_RANDOM_PITCH_MAX, MIN_PITCH, MAX_PITCH);
        if (!PITCH_PER_SOUND.equals(pitchMode) && !PITCH_GLOBAL.equals(pitchMode)) pitchMode = PITCH_RANDOM;
        if (!Float.isFinite(fixedPitch)) fixedPitch = 0f;
        fixedPitch = Math.max(-12f, Math.min(12f, fixedPitch));
        if (parseHexColor(accentColor) < 0) accentColor = DEFAULT_ACCENT_COLOR;
        if (surfaceTone == null || surfaceTone.isBlank()) surfaceTone = DEFAULT_SURFACE_TONE;
    }

    /** Parses "#RRGGBB" or "RRGGBB" into 0xRRGGBB, or returns -1 if the value is not a valid color. */
    public static int parseHexColor(String value) {
        if (value == null) return -1;
        String hex = value.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}")) return -1;
        return Integer.parseInt(hex, 16);
    }

    public static String formatHexColor(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** Restores every appearance option (style, colors, and sizes) to its default. */
    public void resetAppearance() {
        accentColor = DEFAULT_ACCENT_COLOR;
        surfaceTone = DEFAULT_SURFACE_TONE;
        panelOpacity = DEFAULT_PANEL_OPACITY;
        roundedCorners = true;
        uiWidthScale = DEFAULT_UI_WIDTH_SCALE;
        uiHeightScale = DEFAULT_UI_HEIGHT_SCALE;
        fontScale = DEFAULT_FONT_SCALE;
    }

    private static float normalizeScale(float value, float fallback, float minimum, float maximum) {
        if (!Float.isFinite(value) || value <= 0f) value = fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Save global config and per-sound store.
     */
    public static void save() {
        ensureConfigDir();
        if (data == null) data = new SoundboardConfig();
        if (soundStore == null) soundStore = new SoundStore();

        // global: do not serialize any embedded sounds map
        data.sounds = null;
        JsonFiles.write(CONFIG_FILE, data);
        JsonFiles.write(SOUNDS_FILE, soundStore);
    }

    /**
     * Resolves the stored lastOpenedFolder (a path relative to the sounds folder, any depth) to a File.
     * Returns null if unset, outside the sounds folder, or no longer existing (and clears the stored value).
     */
    public static java.io.File resolveLastOpenedFolder(java.io.File soundDir) {
        String path = data.lastOpenedFolder;
        if (path == null || path.isBlank()) return null;
        java.io.File folder = new java.io.File(soundDir, path);
        boolean inside;
        try {
            inside = folder.getCanonicalPath().startsWith(soundDir.getCanonicalPath() + java.io.File.separator);
        } catch (IOException e) {
            inside = false;
        }
        if (!inside || !folder.isDirectory()) {
            data.lastOpenedFolder = null;
            save();
            return null;
        }
        return folder;
    }

    /** Remembers the open folder as a '/'-separated path relative to the sounds folder (null = root). */
    public static void saveLastOpenedFolder(String relativePath) {
        data.lastOpenedFolder = (relativePath == null || relativePath.isBlank()) ? null : relativePath;
        save();
    }

    /**
     * Get the stored sound data IF it exists; otherwise null.
     */
    private static SoundData getStored(String name) {
        if (soundStore == null || soundStore.getSounds() == null) return null;
        return soundStore.getSounds().get(name);
    }

    private static void putStored(String name, SoundData data) {
        if (soundStore == null) soundStore = new SoundStore();
        if (soundStore.getSounds() == null) soundStore.setSounds(new java.util.HashMap<>());
        soundStore.getSounds().put(name, data);
    }

    private static void removeStored(String name) {
        if (soundStore == null || soundStore.getSounds() == null) return;
        soundStore.getSounds().remove(name);
    }

    private static boolean isDefault(SoundData d) {
        if (d == null) return true;
        return d.startingPoint == 0f
                && d.localVolume == 1.0f
                && d.playerVolume == 1.0f
                && d.keybind == null
                && !d.favorite
                && d.pitch == 0f;
    }

    /**
     * Lazy getter:
     * - does NOT create a stored entry until the user changes something
     */
    public static SoundData get(String name) {
        if (data == null) return new LazySoundData(name);
        SoundData stored = getStored(name);
        if (stored != null) return stored;
        return new LazySoundData(name);
    }

    /**
     * Helper for callers which need the map (e.g. rename/migration).
     */
    public static java.util.Map<String, SoundData> sounds() {
        if (soundStore == null) soundStore = new SoundStore();
        if (soundStore.getSounds() == null) soundStore.setSounds(new java.util.HashMap<>());
        return soundStore.getSounds();
    }

    // Kept only for migration compatibility (old config.json included this field)
    java.util.Map<String, SoundData> sounds = null;

    @Data
    @NoArgsConstructor
    public static class SoundData {
        float startingPoint = 0f;
        float localVolume = 1.0f;
        float playerVolume = 1.0f;
        KeyBind keybind = null;
        boolean favorite = false;
        /** Fixed pitch in semitones (-12..+12, 0 = normal), used in "fixed pitch per sound" mode. */
        float pitch = 0f;
    }

    private static final class LazySoundData extends SoundData {

        private final String key;

        private LazySoundData(String key) {
            super();
            this.key = key;
        }

        private void syncToStore() {
            if (isDefault(this)) {
                removeStored(key);
                return;
            }

            SoundData stored = getStored(key);
            if (stored == null) stored = new SoundData();
            stored.startingPoint = this.startingPoint;
            stored.localVolume = this.localVolume;
            stored.playerVolume = this.playerVolume;
            stored.keybind = this.keybind;
            stored.favorite = this.favorite;
            stored.pitch = this.pitch;
            putStored(key, stored);
        }

        @Override
        public void setPitch(float pitch) {
            this.pitch = pitch;
            syncToStore();
        }

        @Override
        public void setStartingPoint(float startingPoint) {
            this.startingPoint = startingPoint;
            syncToStore();
        }

        @Override
        public void setLocalVolume(float localVolume) {
            this.localVolume = localVolume;
            syncToStore();
        }

        @Override
        public void setPlayerVolume(float playerVolume) {
            this.playerVolume = playerVolume;
            syncToStore();
        }

        @Override
        public void setKeybind(KeyBind keybind) {
            this.keybind = keybind;
            syncToStore();
        }

        @Override
        public void setFavorite(boolean favorite) {
            this.favorite = favorite;
            syncToStore();
        }
    }

    /**
     * A per-sound keybind. Key codes differ between Minecraft versions (26.3 switched from GLFW to
     * SDL), so binds are matched by {@link #key}, Minecraft's version-independent key name (the same
     * one options.txt uses), and the {@link #ctrl}/{@link #shift}/{@link #alt} flags.
     * {@code keyCode}/{@code scanCode}/{@code modifiers} hold the raw values of the version that
     * saved the bind; binds from 0.5.1 and older only have those and are upgraded on startup.
     */
    @Data
    @NoArgsConstructor
    public static class KeyBind {
        int keyCode;
        int scanCode;
        int modifiers;
        String key;
        boolean ctrl;
        boolean shift;
        boolean alt;

        public KeyBind(int keyCode, int scanCode, int modifiers, String key, boolean ctrl, boolean shift, boolean alt) {
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
            this.key = key;
            this.ctrl = ctrl;
            this.shift = shift;
            this.alt = alt;
        }

        /** Whether this bind predates version-independent key names. */
        public boolean isLegacy() {
            return key == null || key.isBlank();
        }
    }
}

