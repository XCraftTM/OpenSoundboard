package de.xcrafttm.opensoundboard.config;

import de.xcrafttm.opensoundboard.platform.PlatformBootstrap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the custom radial-wheel slot assignments.
 * Each slot holds a sound key (its path inside the sounds folder, e.g. "Games/MySong.mp3") or null
 * for empty. The list is at least {@link SoundboardConfig#getWheelSoundsPerPage()} long; slots past
 * that are kept, so lowering the slot count and raising it again does not lose assignments.
 */
public class WheelLayoutConfig {

    private static final File BASE_DIR  = new File(PlatformBootstrap.client().configDirectory(), "opensoundboard");
    private static final File FILE      = new File(BASE_DIR, "wheel_layout.json");

    /** Singleton – loaded once, mutated in place, saved on demand. */
    public static WheelLayoutConfig instance = new WheelLayoutConfig();

    /** Null entries represent empty/unassigned slots. */
    public List<String> slots = new ArrayList<>();

    // ----------------------------------------------------------------

    public static void load() {
        boolean existed = FILE.isFile();
        instance = JsonFiles.read(FILE, WheelLayoutConfig.class, WheelLayoutConfig::new);
        if (instance.slots == null) instance.slots = new ArrayList<>();
        if (!existed) save();
    }

    public static void save() {
        JsonFiles.write(FILE, instance);
    }

    /** Ensure the slots list has at least {@code count} entries (padding with null). */
    public static void resize(int count) {
        List<String> s = instance.slots;
        while (s.size() < count) s.add(null);
    }

    /** @return sound key at slot {@code i}, or null if empty / out of range. */
    public static String get(int i) {
        if (i < 0 || i >= instance.slots.size()) return null;
        return instance.slots.get(i);
    }

    /** Assign a sound key (or null to clear) to slot {@code i}. */
    public static void set(int i, String key) {
        resize(i + 1);
        instance.slots.set(i, key);
    }
}
