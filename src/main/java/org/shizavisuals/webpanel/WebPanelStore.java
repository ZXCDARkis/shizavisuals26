package org.shizavisuals.webpanel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.utils.ConfigIO;

public final class WebPanelStore {

    private WebPanelStore() {
    }

    private static final String FILE_NAME = "webpanel/webpanels.json";

    public record Saved(Vec3 center, float yaw, float pitch, double width, double height,
                        String url) {
    }

    public static String worldId() {
        Minecraft mc = Minecraft.getInstance();
        try {
            ServerData entry = mc.getCurrentServer();
            if (entry != null && entry.ip != null) {
                return "mp:" + entry.ip.toLowerCase();
            }
            if (mc.getSingleplayerServer() != null) {
                return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
            }
        } catch (Throwable ignored) {
        }
        return mc.level != null ? "world" : "";
    }

    private static Path file() {
        return ConfigIO.resolve(FILE_NAME);
    }

    private static JsonObject readRoot() {
        JsonObject parsed = ConfigIO.read(file());
        return parsed != null ? parsed : new JsonObject();
    }

    private static void writeRoot(JsonObject root) {
        ConfigIO.write(file(), root);
    }

    public static List<Saved> load(String world) {
        List<Saved> result = new ArrayList<>();
        if (world.isEmpty()) {
            return result;
        }
        JsonObject worlds = readRoot().getAsJsonObject("worlds");
        if (worlds == null || !worlds.has(world)) {
            return result;
        }
        for (JsonElement element : worlds.getAsJsonArray(world)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String url = null;
            if (object.has("url")) {
                url = object.get("url").getAsString();
            } else if (object.has("tabs") && object.get("tabs").isJsonArray()
                    && !object.getAsJsonArray("tabs").isEmpty()) {
                url = object.getAsJsonArray("tabs").get(0).getAsString();
            }
            result.add(new Saved(
                    new Vec3(object.get("x").getAsDouble(), object.get("y").getAsDouble(),
                            object.get("z").getAsDouble()),
                    object.get("yaw").getAsFloat(), object.get("pitch").getAsFloat(),
                    object.get("w").getAsDouble(), object.get("h").getAsDouble(),
                    sanitize(url)));
        }
        return result;
    }

    private static String sanitize(String url) {
        return url == null || url.isBlank() || url.startsWith("data:") || "about:blank".equals(url)
                ? WebPanelUrls.HOME : url;
    }

    public static void save(String world, List<WebPanelInstance> panels) {
        if (world.isEmpty()) {
            return;
        }
        JsonObject root = readRoot();
        JsonObject worlds = root.getAsJsonObject("worlds");
        if (worlds == null) {
            worlds = new JsonObject();
            root.add("worlds", worlds);
        }

        JsonArray array = new JsonArray();
        for (WebPanelInstance panel : panels) {
            if (panel.isClosing() || panel.isRemote()) {
                continue;
            }
            JsonObject object = new JsonObject();
            Vec3 center = panel.getCenter();
            object.addProperty("x", center.x);
            object.addProperty("y", center.y);
            object.addProperty("z", center.z);
            object.addProperty("yaw", panel.getYaw());
            object.addProperty("pitch", panel.getPitch());
            object.addProperty("w", panel.getWidth());
            object.addProperty("h", panel.getHeight());
            object.addProperty("url", sanitize(panel.getUrl()));
            array.add(object);
        }
        if (array.isEmpty()) {
            worlds.remove(world);
        } else {
            worlds.add(world, array);
        }
        writeRoot(root);
    }
}
