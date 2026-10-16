package de.xcrafttm.opensoundboard.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFilesTest {

    static class Sample {
        int number = 7;
        String text = "default";
    }

    @Test
    void missingFileGivesDefaults(@TempDir Path dir) {
        Sample sample = JsonFiles.read(dir.resolve("missing.json").toFile(), Sample.class, Sample::new);
        assertEquals(7, sample.number);
    }

    @Test
    void roundTrip(@TempDir Path dir) {
        File file = dir.resolve("sample.json").toFile();
        Sample sample = new Sample();
        sample.number = 42;
        sample.text = "Grüße";
        JsonFiles.write(file, sample);

        Sample loaded = JsonFiles.read(file, Sample.class, Sample::new);
        assertEquals(42, loaded.number);
        assertEquals("Grüße", loaded.text);
        try (var files = Files.list(dir)) {
            assertEquals(List.of(file.toPath()), files.toList(), "no temporary files are left behind");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void brokenFileIsBackedUpInsteadOfCrashing(@TempDir Path dir) throws IOException {
        File file = dir.resolve("config.json").toFile();
        Files.writeString(file.toPath(), "{ \"number\": 5, \"text\": ", StandardCharsets.UTF_8);

        Sample loaded = JsonFiles.read(file, Sample.class, Sample::new);
        assertEquals(7, loaded.number);
        assertFalse(file.exists(), "the broken file is moved aside");
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("config.json.broken-")));
        }
    }

    @Test
    void wrongTypeIsBackedUpInsteadOfCrashing(@TempDir Path dir) throws IOException {
        File file = dir.resolve("config.json").toFile();
        Files.writeString(file.toPath(), "{ \"number\": \"not a number\" }", StandardCharsets.UTF_8);
        assertEquals(7, JsonFiles.read(file, Sample.class, Sample::new).number);
    }
}
