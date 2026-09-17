package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-system view of the sounds folder: nested folders of any depth, recursive listings, and
 * lookups by file name (sound settings are keyed by file name).
 */
public final class SoundLibrary {

    /** Guards against symlink loops and absurdly deep trees. */
    private static final int MAX_DEPTH = 16;

    private static final Map<String, File> BY_NAME = new ConcurrentHashMap<>();

    private SoundLibrary() {
    }

    public static File root() {
        return OpenSoundboardClient.soundDir;
    }

    public static boolean isSound(File file) {
        return file.isFile() && file.getName().toLowerCase().endsWith(".mp3");
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
                BY_NAME.put(entry.getName(), entry);
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

    /** Find a sound anywhere in the library by its file name, or null. */
    public static File find(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        File cached = BY_NAME.get(fileName);
        if (cached != null && cached.isFile()) return cached;
        BY_NAME.clear();
        allSounds(root());
        return BY_NAME.get(fileName);
    }

    /** Path of {@code file} relative to the sounds folder with '/' separators ("" for the root). */
    public static String relativePath(File file) {
        String rootPath = root().getAbsoluteFile().toPath().normalize().toString();
        String path = file.getAbsoluteFile().toPath().normalize().toString();
        if (!path.startsWith(rootPath)) return file.getName();
        String rel = path.substring(rootPath.length()).replace(File.separatorChar, '/');
        return rel.startsWith("/") ? rel.substring(1) : rel;
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
