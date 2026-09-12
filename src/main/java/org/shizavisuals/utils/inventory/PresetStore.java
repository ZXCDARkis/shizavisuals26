package org.shizavisuals.utils.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JSON persistence for inventory presets (game dir / shizavisuals / inventories.json). */
public final class PresetStore {
    private static final Logger LOG = LoggerFactory.getLogger("ShizaVisuals/Inventory");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final List<InventoryPreset> presets = new ArrayList<>();
    private String activeName = "";

    public PresetStore() {
        this.file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                .resolve("shizavisuals").resolve("inventories.json");
    }

    public List<InventoryPreset> presets() {
        return presets;
    }

    public String activeName() {
        return activeName;
    }

    public InventoryPreset active() {
        return activeName == null || activeName.isEmpty() ? null : byName(activeName);
    }

    public InventoryPreset byName(String name) {
        for (InventoryPreset p : presets) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    public boolean exists(String name) {
        return byName(name) != null;
    }

    public void put(InventoryPreset preset) {
        InventoryPreset old = byName(preset.name);
        if (old != null) {
            presets.set(presets.indexOf(old), preset);
        } else {
            presets.add(preset);
        }
        save();
    }

    public void delete(String name) {
        presets.removeIf(p -> p.name.equals(name));
        if (name.equals(activeName)) {
            activeName = "";
        }
        save();
    }

    public void setActive(String name) {
        this.activeName = name == null ? "" : name;
        save();
    }

    public void clearActive() {
        this.activeName = "";
        save();
    }

    public void load() {
        presets.clear();
        activeName = "";
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("active")) {
                activeName = root.get("active").getAsString();
            }
            if (root.has("presets") && root.get("presets").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("presets")) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject po = el.getAsJsonObject();
                    InventoryPreset preset = new InventoryPreset(
                            po.has("name") ? po.get("name").getAsString() : "preset");
                    if (po.has("items") && po.get("items").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> e : po.getAsJsonObject("items").entrySet()) {
                            try {
                                int slot = Integer.parseInt(e.getKey());
                                JsonObject io = e.getValue().getAsJsonObject();
                                preset.items.put(slot, new InvItem(
                                        io.get("id").getAsString(),
                                        io.has("count") ? io.get("count").getAsInt() : 1,
                                        io.has("name") ? io.get("name").getAsString() : "",
                                        io.has("stack") ? io.get("stack").toString() : "",
                                        io.has("key") ? io.get("key").getAsString() : ""));
                            } catch (RuntimeException ignored) {
                            }
                        }
                    }
                    presets.add(preset);
                }
            }
            if (!activeName.isEmpty() && byName(activeName) == null) {
                activeName = "";
            }
        } catch (Exception e) {
            LOG.warn("Не удалось прочитать {}", file, e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("active", activeName == null ? "" : activeName);
        JsonArray arr = new JsonArray();
        for (InventoryPreset p : presets) {
            JsonObject po = new JsonObject();
            po.addProperty("name", p.name);
            JsonObject items = new JsonObject();
            for (Map.Entry<Integer, InvItem> e : p.items.entrySet()) {
                JsonObject io = new JsonObject();
                io.addProperty("id", e.getValue().id);
                io.addProperty("count", e.getValue().count);
                if (!e.getValue().name.isEmpty()) {
                    io.addProperty("name", e.getValue().name);
                }
                if (!e.getValue().key.isEmpty()) {
                    io.addProperty("key", e.getValue().key);
                }
                if (!e.getValue().json.isEmpty()) {
                    try {
                        io.add("stack", JsonParser.parseString(e.getValue().json));
                    } catch (RuntimeException ignored) {
                    }
                }
                items.add(String.valueOf(e.getKey()), io);
            }
            po.add("items", items);
            arr.add(po);
        }
        root.add("presets", arr);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            LOG.error("Не удалось записать {}", file, e);
        }
    }
}
