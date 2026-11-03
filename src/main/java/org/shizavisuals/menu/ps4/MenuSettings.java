package org.shizavisuals.menu.ps4;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent settings of the PS4 main menu. Adobe-port of deluxe MenuConfig
 * extended with the wallpaper selection: any of the seven PS4 backgrounds or
 * a custom user image.
 */
public final class MenuSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile float menuVolume = 0.8F;
    private static volatile boolean ambientMuted = false;
    private static volatile boolean useVanillaMenu = false;
    private static volatile int wallpaperIndex = 0; // index into Ps4Wallpapers
    private static volatile String customWallpaperPath = "";
    private static volatile boolean loaded = false;

    private MenuSettings() {
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("shizavisuals").resolve("menu_settings.json");
    }

    /** Double-checked: the hot per-frame path costs one volatile read only. */
    public static void load() {
        if (loaded) {
            return;
        }
        synchronized (MenuSettings.class) {
            if (loaded) {
                return;
            }
            loadOnce();
        }
    }

    private static void loadOnce() {
        loaded = true;
        try {
            Path path = configPath();
            if (Files.exists(path)) {
                String json = Files.readString(path);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("menuVolume")) {
                    menuVolume = Math.max(0.0F, Math.min(1.0F, obj.get("menuVolume").getAsFloat()));
                }
                if (obj.has("ambientMuted")) {
                    ambientMuted = obj.get("ambientMuted").getAsBoolean();
                }
                if (obj.has("useVanillaMenu")) {
                    useVanillaMenu = obj.get("useVanillaMenu").getAsBoolean();
                }
                if (obj.has("wallpaperIndex")) {
                    wallpaperIndex = Math.max(0, obj.get("wallpaperIndex").getAsInt());
                }
                if (obj.has("customWallpaper")) {
                    customWallpaperPath = obj.get("customWallpaper").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        Ps4Sound.initFromConfig(menuVolume, ambientMuted);
        Ps4Wallpapers.restore(wallpaperIndex, customWallpaperPath);
    }

    public static synchronized void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("menuVolume", menuVolume);
            obj.addProperty("ambientMuted", ambientMuted);
            obj.addProperty("useVanillaMenu", useVanillaMenu);
            obj.addProperty("wallpaperIndex", Ps4Wallpapers.activeIndex());
            obj.addProperty("customWallpaper", Ps4Wallpapers.customPath());
            Files.writeString(path, GSON.toJson(obj));
        } catch (Exception ignored) {
        }
    }

    public static float getMenuVolume() {
        load();
        return menuVolume;
    }

    public static void setMenuVolume(float volume) {
        load();
        menuVolume = Math.max(0.0F, Math.min(1.0F, volume));
        Ps4Sound.updateMenuVolume(menuVolume);
        save();
    }

    /** Copies the live Ps4Sound volume into the persisted field without saving. */
    public static void captureMenuVolume(float volume) {
        menuVolume = Math.max(0.0F, Math.min(1.0F, volume));
    }

    public static boolean isAmbientMuted() {
        load();
        return ambientMuted;
    }

    public static void setAmbientMuted(boolean muted) {
        load();
        ambientMuted = muted;
        save();
    }

    public static boolean isUseVanillaMenu() {
        load();
        return useVanillaMenu;
    }

    public static void setUseVanillaMenu(boolean vanilla) {
        load();
        useVanillaMenu = vanilla;
        save();
    }
}
