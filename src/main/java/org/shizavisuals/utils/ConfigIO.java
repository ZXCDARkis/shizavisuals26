package org.shizavisuals.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ConfigIO {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigIO.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigIO() {
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("shizavisuals");
    }

    public static Path resolve(String relativePath) {
        return configDir().resolve(relativePath);
    }

    public static boolean write(Path path, JsonElement root) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {

                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.error("Failed to save config {}", path, exception);
            return false;
        }
    }

    public static JsonObject read(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            LOGGER.error("Config {} is corrupt; moving it aside and using defaults", path, exception);
            backupCorrupt(path);
            return null;
        }
    }

    private static void backupCorrupt(Path path) {
        Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + LocalDateTime.now().format(BACKUP_TIME));
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Corrupt config preserved as {}", backup);
        } catch (IOException exception) {
            LOGGER.error("Failed to back up corrupt config {}", path, exception);
        }
    }
}
