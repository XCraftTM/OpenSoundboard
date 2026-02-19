package de.xcrafttm.opensoundboard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the custom radial-wheel slot assignments.
 * Each slot holds a sound file name (relative, e.g. "MySong.mp3") or null for empty.
 * The list length always equals {@link SoundboardConfig#getWheelSoundsPerPage()}.
 */
public class WheelLayoutConfig {

    private static final File BASE_DIR  = new File(FabricLoader.getInstance().getConfigDir().toFile(), "opensoundboard");
    private static final File FILE      = new File(BASE_DIR, "wheel_layout.json");
    private static final Gson GSON      = new GsonBuilder().setPrettyPrinting().create();

    /** Singleton – loaded once, mutated in place, saved on demand. */
    public static WheelLayoutConfig instance = new WheelLayoutConfig();

    /** Null entries represent empty/unassigned slots. */
    public List<String> slots = new ArrayList<>();

    // ----------------------------------------------------------------

    public static void load() {
        if (!BASE_DIR.exists()) BASE_DIR.mkdirs();
        if (FILE.exists()) {
            try (FileReader r = new FileReader(FILE)) {
                WheelLayoutConfig loaded = GSON.fromJson(r, WheelLayoutConfig.class);
                instance = (loaded != null) ? loaded : new WheelLayoutConfig();
                if (instance.slots == null) instance.slots = new ArrayList<>();
            } catch (IOException e) {
                e.printStackTrace();
                instance = new WheelLayoutConfig();
            }
        } else {
            instance = new WheelLayoutConfig();
            save();
        }
    }

    public static void save() {
        if (!BASE_DIR.exists()) BASE_DIR.mkdirs();
        try (FileWriter w = new FileWriter(FILE)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Ensure the slots list has exactly {@code count} entries (padding with null, trimming excess). */
    public static void resize(int count) {
        List<String> s = instance.slots;
        while (s.size() < count) s.add(null);
        while (s.size() > count) s.remove(s.size() - 1);
    }

    /** @return file name at slot {@code i}, or null if empty / out of range. */
    public static String get(int i) {
        if (i < 0 || i >= instance.slots.size()) return null;
        return instance.slots.get(i);
    }

    /** Assign a file name (or null to clear) to slot {@code i}. */
    public static void set(int i, String fileName) {
        resize(i + 1);
        instance.slots.set(i, fileName);
    }
}

