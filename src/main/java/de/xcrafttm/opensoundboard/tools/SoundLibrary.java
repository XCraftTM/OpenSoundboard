package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.config.WheelLayoutConfig;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-system view of the sounds folder: nested folders of any depth, recursive listings, and
 * lookups by key. A sound's key is its path inside the sounds folder ("Games/intro.mp3"), so two
 * files with the same name in different folders keep separate settings. Sounds in the root folder
 * have the same key as before folders existed (just the file name).
 */
public final class SoundLibrary {

    /** Guards against symlink loops and absurdly deep trees. */
    private static final int MAX_DEPTH = 16;
    /** A legacy name lookup that missed does not rescan the library again within this time. */
    private static final long RESCAN_COOLDOWN_MS = 5_000;

    private static final Map<String, File> BY_NAME = new ConcurrentHashMap<>();
    private static volatile long lastNameScan = 0;

    private SoundLibrary() {
    }

    public static File root() {
        return OpenSoundboardClient.soundDir;
    }

    private static Path rootPath() {
        return root().toPath().toAbsolutePath().normalize();
    }

    public static boolean isSound(File file) {
        return file.isFile() && file.getName().toLowerCase().endsWith(".mp3");
    }

    /** The key settings, keybinds, wheel slots, and playback use for {@code file}. */
    public static String key(File file) {
        return relativePath(file);
    }

    /** Sound files directly inside {@code dir}. */
    public static List<File> sounds(File dir) {
        File[] files = dir.listFiles(SoundLibrary::isSound);
        return files == null ? List.of() : Arrays.asList(files);
    }

    /** Direct subfolders of {@code dir} that contain at least one sound somewhere below them. */
    public static List<File> folders(File dir) {
        File[] dirs = dir.listFiles(File::isDirectory);
        if (dirs == null) return List.of();
        List<File> out = new ArrayList<>();
        for (File sub : dirs) {
            if (!sub.getName().startsWith(".") && countSounds(sub) > 0) out.add(sub);
        }
        out.sort(Comparator.comparing(f -> f.getName().toLowerCase()));
        return out;
    }

    /** Every sound in {@code dir} and all of its subfolders. */
    public static List<File> allSounds(File dir) {
        List<File> out = new ArrayList<>();
        collect(dir, out, 0);
        return out;
    }

    private static void collect(File dir, List<File> out, int depth) {
        if (depth > MAX_DEPTH) return;
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                if (!entry.getName().startsWith(".")) collect(entry, out, depth + 1);
            } else if (isSound(entry)) {
                out.add(entry);
                BY_NAME.putIfAbsent(entry.getName(), entry);
            }
        }
    }

    public static int countSounds(File dir) {
        return countSounds(dir, 0);
    }

    private static int countSounds(File dir, int depth) {
        if (depth > MAX_DEPTH) return 0;
        File[] entries = dir.listFiles();
        if (entries == null) return 0;
        int count = 0;
        for (File entry : entries) {
            if (entry.isDirectory()) count += countSounds(entry, depth + 1);
            else if (isSound(entry)) count++;
        }
        return count;
    }

    /**
     * The sound with this key, or null. A bare file name that is not in the root folder (a key
     * from before sounds were keyed by path) falls back to any sound with that name.
     */
    public static File find(String key) {
        if (key == null || key.isBlank()) return null;
        File file = resolve(key);
        if (file != null && isSound(file)) return file;
        if (key.contains("/")) return null;

        File cached = BY_NAME.get(key);
        if (cached != null && isSound(cached)) return cached;
        long now = System.currentTimeMillis();
        if (now - lastNameScan < RESCAN_COOLDOWN_MS) return null;
        lastNameScan = now;
        BY_NAME.clear();
        allSounds(root());
        return BY_NAME.get(key);
    }

    /** The file a key points to (whether or not it exists), or null if it would leave the sounds folder. */
    private static File resolve(String key) {
        try {
            Path root = rootPath();
            Path path = root.resolve(key.replace('/', File.separatorChar)).normalize();
            if (!path.startsWith(root) || path.equals(root)) return null;
            return path.toFile();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /**
     * Re-points settings and wheel slots whose file is gone to the file's new location, when exactly
     * one sound in the library has that file name. This migrates keys from before sounds were keyed
     * by path, and keeps favorites, volumes, and keybinds when a file is moved to another folder.
     */
    public static void relinkMissing() {
        Map<String, List<File>> byName = new HashMap<>();
        for (File file : allSounds(root())) byName.computeIfAbsent(file.getName(), k -> new ArrayList<>()).add(file);

        boolean settingsChanged = false;
        Map<String, SoundboardConfig.SoundData> sounds = SoundboardConfig.sounds();
        for (String key : List.copyOf(sounds.keySet())) {
            String target = relinkTarget(key, byName);
            if (target == null) continue;
            SoundboardConfig.SoundData data = sounds.remove(key);
            sounds.putIfAbsent(target, data);
            settingsChanged = true;
        }

        boolean wheelChanged = false;
        List<String> slots = WheelLayoutConfig.instance.slots;
        for (int i = 0; i < slots.size(); i++) {
            String target = relinkTarget(slots.get(i), byName);
            if (target == null) continue;
            slots.set(i, target);
            wheelChanged = true;
        }

        if (settingsChanged) SoundboardConfig.save();
        if (wheelChanged) WheelLayoutConfig.save();
    }

    /** New key for {@code key} if its file is missing and its name is unique in the library. */
    private static String relinkTarget(String key, Map<String, List<File>> byName) {
        if (key == null || key.isBlank()) return null;
        File current = resolve(key);
        if (current != null && isSound(current)) return null;
        String name = key.substring(key.lastIndexOf('/') + 1);
        List<File> matches = byName.get(name);
        if (matches == null || matches.size() != 1) return null;
        String target = key(matches.get(0));
        return target.equals(key) ? null : target;
    }

    /** Path of {@code file} relative to the sounds folder with '/' separators ("" for the root). */
    public static String relativePath(File file) {
        Path root = rootPath();
        Path path = file.toPath().toAbsolutePath().normalize();
        if (!path.startsWith(root)) return file.getName();
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    /** Folder path of a sound relative to the root, e.g. "Games/Mario" (empty for root-level sounds). */
    public static String folderOf(File sound) {
        File parent = sound.getParentFile();
        return parent == null ? "" : relativePath(parent);
    }

    /** The chain of folders from the root (exclusive) down to {@code folder} (inclusive). */
    public static List<File> trail(File folder) {
        List<File> out = new ArrayList<>();
        File rootFile = root().getAbsoluteFile();
        File current = folder == null ? null : folder.getAbsoluteFile();
        while (current != null && !current.equals(rootFile)) {
            out.add(0, current);
            current = current.getParentFile();
        }
        return current == null ? List.of() : out;
    }

    /** Whether {@code folder} is an existing directory inside the sounds folder. */
    public static boolean isInside(File folder) {
        return folder != null && folder.isDirectory() && !trail(folder).isEmpty();
    }
}
