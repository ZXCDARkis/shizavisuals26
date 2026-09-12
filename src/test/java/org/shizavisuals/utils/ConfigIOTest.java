package org.shizavisuals.utils;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigIOTest {
    @TempDir
    Path dir;

    @Test
    void writeThenReadRoundTrips() {
        Path file = dir.resolve("nested").resolve("config.json");
        JsonObject root = new JsonObject();
        root.addProperty("value", 42);

        assertTrue(ConfigIO.write(file, root));

        JsonObject loaded = ConfigIO.read(file);
        assertEquals(42, loaded.get("value").getAsInt());
    }

    @Test
    void writeLeavesNoTempFile() throws IOException {
        Path file = dir.resolve("config.json");
        assertTrue(ConfigIO.write(file, new JsonObject()));

        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.allMatch(entry -> entry.getFileName().toString().equals("config.json")));
        }
    }

    @Test
    void missingFileReadsAsNull() {
        assertNull(ConfigIO.read(dir.resolve("absent.json")));
    }

    @Test
    void corruptFileIsBackedUpAndReadsAsNull() throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{ not json");

        assertNull(ConfigIO.read(file));
        assertFalse(Files.exists(file), "corrupt file should be moved aside");

        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.anyMatch(entry -> entry.getFileName().toString().startsWith("config.json.corrupt-")));
        }
    }

    @Test
    void overwriteReplacesPreviousContent() {
        Path file = dir.resolve("config.json");
        JsonObject first = new JsonObject();
        first.addProperty("value", 1);
        ConfigIO.write(file, first);

        JsonObject second = new JsonObject();
        second.addProperty("value", 2);
        ConfigIO.write(file, second);

        assertEquals(2, ConfigIO.read(file).get("value").getAsInt());
    }
}
