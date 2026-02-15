package de.xcrafttm.opensoundboard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@Data
public class SoundboardConfig {

    private static final File BASE_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "opensoundboard");

    /**
     * Global options
     */
    private static final File CONFIG_FILE = new File(BASE_DIR, "config.json");

    /**
     * Per-sound options
     */
    private static final File SOUNDS_FILE = new File(BASE_DIR, "sounds.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static SoundboardConfig data;

    // Stores per-sound settings separately
    private static SoundStore soundStore = new SoundStore();

    boolean playWhileMuted = false;
    boolean playLocally = true;
    float globalLocalVolume = 1.0f;
    float globalPlayerVolume = 1.0f;
    boolean syncGlobalVolume = false;
    boolean singleSongAtATime = false;
    boolean loopAll = false;
    boolean syncAudio = false;
    int skipAmountSeconds = 5;

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
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                data = GSON.fromJson(reader, SoundboardConfig.class);
                if (data == null) data = new SoundboardConfig();
            } catch (IOException e) {
                e.printStackTrace();
                data = new SoundboardConfig();
            }
        } else {
            data = new SoundboardConfig();
        }

        // per-sound
        if (SOUNDS_FILE.exists()) {
            try (FileReader reader = new FileReader(SOUNDS_FILE)) {
                SoundStore loaded = GSON.fromJson(reader, SoundStore.class);
                soundStore = loaded != null ? loaded : new SoundStore();
            } catch (IOException e) {
                e.printStackTrace();
                soundStore = new SoundStore();
            }
        } else {
            soundStore = new SoundStore();
        }

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
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // sounds
        try (FileWriter writer = new FileWriter(SOUNDS_FILE)) {
            GSON.toJson(soundStore, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                && !d.favorite;
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
            putStored(key, stored);
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyBind {
        int keyCode;
        int scanCode;
        int modifiers;
    }
}
