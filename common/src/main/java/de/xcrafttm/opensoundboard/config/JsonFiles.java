package de.xcrafttm.opensoundboard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Crash-safe JSON storage for the config files. Reads never throw: a missing file gives the
 * defaults, and an unreadable or malformed one is moved aside (so the user can recover it) before
 * the defaults are used. Writes go to a temporary file that then replaces the real one, so a crash
 * mid-save never leaves a half-written file behind.
 */
public final class JsonFiles {

    static final Logger LOGGER = LoggerFactory.getLogger("OpenSoundboard");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private JsonFiles() {
    }

    /** Read {@code file} as {@code type}; returns {@code defaults} when it is missing, empty, or broken. */
    public static <T> T read(File file, Class<T> type, Supplier<T> defaults) {
        if (!file.isFile()) return defaults.get();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            T value = GSON.fromJson(reader, type);
            return value != null ? value : defaults.get();
        } catch (IOException | RuntimeException e) {
            File backup = backup(file);
            LOGGER.warn("[OpenSoundboard] Could not read {} ({}); using defaults.{}", file.getName(), e.getMessage(),
                    backup != null ? " The old file was saved as " + backup.getName() + "." : "");
            return defaults.get();
        }
    }

    /** Write {@code value} to {@code file}, replacing it only once the new content is fully on disk. */
    public static void write(File file, Object value) {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            LOGGER.warn("[OpenSoundboard] Could not create {}", dir);
            return;
        }
        Path target = file.toPath();
        Path temp = null;
        try {
            temp = Files.createTempFile(dir.toPath(), file.getName(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[OpenSoundboard] Could not save {}: {}", file.getName(), e.getMessage());
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Moves a broken file to "name.broken-<timestamp>"; returns the backup, or null if that failed. */
    private static File backup(File file) {
        File backup = new File(file.getParentFile(), file.getName() + ".broken-" + LocalDateTime.now().format(BACKUP_STAMP));
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            return null;
        }
    }
}
