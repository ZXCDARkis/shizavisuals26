package org.shizavisuals.menu.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.shizavisuals.utils.ConfigIO;
import org.shizavisuals.utils.math.MathUtil;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class MenuConfigStore {
    private static final Path PATH = ConfigIO.resolve("menu.json");

    private static JsonObject root;

    private MenuConfigStore() {
    }

    public static float getFloat(String key, float defaultValue) {
        JsonElement element = data().get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        JsonElement element = data().get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : defaultValue;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        JsonElement element = data().get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : defaultValue;
    }

    public static String getString(String key, String defaultValue) {
        JsonElement element = data().get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : defaultValue;
    }

    public static JsonArray getArray(String key) {
        JsonElement element = data().get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    public static void save(Consumer<JsonObject> mutator) {
        JsonObject data = data();
        mutator.accept(data);
        ConfigIO.write(PATH, data);
    }

    static float loadUiScaleValue() {
        float scale = getFloat("uiScale", Float.NaN);
        if (Float.isNaN(scale)) {
            return MenuOverlayState.DEFAULT_UI_SCALE_VALUE;
        }
        return MathUtil.clamp01(
                (scale - MenuOverlayState.MIN_UI_SCALE) / (MenuOverlayState.MAX_UI_SCALE - MenuOverlayState.MIN_UI_SCALE)
        );
    }

    static void saveUiScaleValue(float uiScaleValue) {
        float scale = MathUtil.lerp(MenuOverlayState.MIN_UI_SCALE, MenuOverlayState.MAX_UI_SCALE, uiScaleValue);
        save(data -> data.addProperty("uiScale", scale));
    }

    public static void saveAccentIndex(int accentIndex) {
        save(data -> data.addProperty("accentIndex", accentIndex));
    }

    public static void resetHudPositions() {
        save(data -> data.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            return key.startsWith("hud.") && (key.endsWith(".x") || key.endsWith(".y"));
        }));
    }

    private static JsonObject data() {
        if (root != null) {
            return root;
        }
        JsonObject loaded = ConfigIO.read(PATH);
        root = loaded != null ? loaded : new JsonObject();
        return root;
    }
}
