package org.shizavisuals.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.shizavisuals.utils.ConfigIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class CommandStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandStore.class);

    private final Path path = ConfigIO.resolve("commands.json");

    void load(CommandManager manager) {
        JsonObject root = ConfigIO.read(path);
        if (root == null) {
            return;
        }

        try {
            String prefix = root.has("prefix") ? root.get("prefix").getAsString() : CommandManager.DEFAULT_PREFIX;

            List<Macro> macros = new ArrayList<>();
            JsonElement macrosElement = root.get("macros");
            if (macrosElement != null && macrosElement.isJsonArray()) {
                for (JsonElement element : macrosElement.getAsJsonArray()) {
                    JsonObject macro = element.getAsJsonObject();
                    macros.add(new Macro(
                            macro.get("name").getAsString(),
                            macro.get("bind").getAsInt(),
                            macro.get("text").getAsString()
                    ));
                }
            }

            manager.loadState(prefix, macros);
        } catch (Exception exception) {
            LOGGER.error("Failed to load command config from {}", path, exception);
        }
    }

    void save(CommandManager manager) {
        JsonObject root = new JsonObject();
        root.addProperty("prefix", manager.getPrefix());

        JsonArray macros = new JsonArray();
        for (Macro macro : manager.getMacros()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", macro.name());
            entry.addProperty("bind", macro.bindCode());
            entry.addProperty("text", macro.text());
            macros.add(entry);
        }
        root.add("macros", macros);

        ConfigIO.write(path, root);
    }
}
