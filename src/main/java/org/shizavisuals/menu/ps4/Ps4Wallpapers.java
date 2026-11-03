package org.shizavisuals.menu.ps4;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.shizavisuals.context.MinecraftContext;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wallpapers of the PS4 main menu: the seven built-in PS4 backgrounds plus
 * any custom images the user drops into config/shizavisuals/backgrounds or
 * selects via an absolute path. Custom images are loaded once and cached as
 * dynamic textures.
 */
public final class Ps4Wallpapers {
    private record Entry(String label, Identifier texture, String path) {
    }

    private static final Identifier CUSTOM_DIR_NOTE = Identifier.parse("shizavisuals:textures/ps4/background/ps4_background.png");

    private static final String[] BUILTIN_LABELS = {
            "PS4 Синие волны",
            "PS4 Монохром",
            "PS4 Красные волны",
            "PS4 Оранжевые волны",
            "PS4 Зелёные волны",
            "PS4 Бирюзовые волны",
            "PS4 Фиолетовые волны"
    };

    private static final String[] BUILTIN_PATHS = {
            "textures/ps4/background/ps4_background.png",
            "textures/ps4/background/ps4_background_mono.png",
            "textures/ps4/background/ps4_theme_red.png",
            "textures/ps4/background/ps4_theme_orange.png",
            "textures/ps4/background/ps4_theme_green.png",
            "textures/ps4/background/ps4_theme_cyan.png",
            "textures/ps4/background/ps4_theme_purple.png"
    };

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, Identifier> CUSTOM_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CUSTOM_MTIME = new ConcurrentHashMap<>();
    private static volatile int activeIndex;
    private static volatile String customSelection = "";
    private static volatile boolean initialized;

    private Ps4Wallpapers() {
    }

    public static Path customDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("shizavisuals").resolve("backgrounds");
    }

    /** Restores the config selection: built-in index and the custom override. */
    public static void restore(int index, String customPath) {
        ensureEntries();
        activeIndex = Math.max(0, Math.min(index, ENTRIES.size() - 1));
        if (customPath != null && !customPath.isBlank()) {
            setCustom(customPath);
        } else {
            customSelection = "";
        }
    }

    private static synchronized void ensureEntries() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (int i = 0; i < BUILTIN_PATHS.length; i++) {
            ENTRIES.add(new Entry(BUILTIN_LABELS[i], Identifier.parse("shizavisuals:" + BUILTIN_PATHS[i]), null));
        }
        // One entry per custom image, plus an explicit "pick a folder" entry solved by settings.
        try {
            Path dir = customDir();
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    for (Path file : stream.filter(Ps4Wallpapers::isImage).sorted().toList()) {
                        addCustomEntry(file);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isImage(Path path) {
        String lower = path.getFileName().toString().toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static void addCustomEntry(Path file) {
        if (loadFromFile(file)) {
            ENTRIES.add(new Entry(file.getFileName().toString(), CUSTOM_CACHE.get(pathKey(file)), file.toString()));
        }
    }

    /** Stable dynamic texture id for a custom image path. */
    private static Identifier idFor(Path file) {
        return Identifier.parse(String.format("shizavisuals:dynamic/ps4wall_%08x", file.toString().toLowerCase(Locale.ROOT).hashCode()));
    }

    private static String pathKey(Path file) {
        return file.toAbsolutePath().toString().toLowerCase(Locale.ROOT);
    }

    private static boolean loadFromFile(Path file) {
        String key = pathKey(file);
        try {
            long modified = Files.getLastModifiedTime(file).toMillis();
            Long cached = CUSTOM_MTIME.get(key);
            if (cached != null && cached == modified) {
                return true;
            }
            try (InputStream stream = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(stream);
                Identifier id = idFor(file);
                DynamicTexture texture = new DynamicTexture(() -> id.toString(), image);
                MinecraftContext.mc.getTextureManager().register(id, texture);
                CUSTOM_CACHE.put(key, id);
                CUSTOM_MTIME.put(key, modified);
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Replaces the active wallpaper with an absolute image path (or clears
     * the override with an empty string). Returns whether the image loaded.
     */
    public static boolean setCustom(String absolutePath) {
        ensureEntries();
        if (absolutePath == null || absolutePath.isBlank()) {
            customSelection = "";
            saveIndex();
            return true;
        }
        Path path = Path.of(absolutePath);
        if (!Files.isRegularFile(path) || !isImage(path)) {
            return false;
        }
        try {
            Files.createDirectories(customDir());
        } catch (Exception ignored) {
        }
        if (!loadFromFile(path)) {
            return false;
        }
        Identifier id = CUSTOM_CACHE.get(pathKey(path));
        Entry entry = new Entry(path.getFileName().toString(), id, path.toString());
        // Replace or reuse the trailing custom entry slot (but never replace a
        // different custom image already listed from the folder).
        boolean replaced = false;
        for (int i = BUILTIN_PATHS.length; i < ENTRIES.size(); i++) {
            if (pathKey(Path.of(ENTRIES.get(i).path())).equals(pathKey(path))) {
                ENTRIES.set(i, entry);
                activeIndex = i;
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            ENTRIES.add(entry);
            activeIndex = ENTRIES.size() - 1;
        }
        customSelection = path.toString();
        saveIndex();
        return true;
    }

    /** Adds the folder's images without replacing the manual override. */
    public static void refreshCustomDir() {
        try {
            Path dir = customDir();
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    for (Path file : stream.filter(Ps4Wallpapers::isImage).sorted().toList()) {
                        boolean already = ENTRIES.stream().anyMatch(e -> file.toString().equals(e.path()));
                        if (!already) {
                            addCustomEntry(file);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        saveIndex();
    }

    private static void saveIndex() {
        // Persist through MenuSettings without recursing into its own load logic.
        try {
            MenuSettings.save();
        } catch (Exception ignored) {
        }
    }

    /** Cycles to the next wallpaper and returns the new label. */
    public static String next() {
        ensureEntries();
        if (ENTRIES.isEmpty()) {
            return "Обои недоступны";
        }
        activeIndex = (activeIndex + 1) % ENTRIES.size();
        customSelection = "";
        saveIndex();
        return label();
    }

    /**
     * Cycles through the user's wallpapers from the custom folder. When the
     * folder is empty tells the caller (returning false) so the UI can open it.
     */
    public static boolean nextCustom() {
        ensureEntries();
        refreshCustomDir();
        List<Integer> customIndexes = new ArrayList<>();
        for (int i = BUILTIN_PATHS.length; i < ENTRIES.size(); i++) {
            customIndexes.add(i);
        }
        if (customIndexes.isEmpty()) {
            return false;
        }
        int position = customIndexes.indexOf(activeIndex);
        int next = customIndexes.get((position + 1) % customIndexes.size());
        activeIndex = next;
        customSelection = "";
        saveIndex();
        return true;
    }

    /** How many user wallpapers are currently in the folder. */
    public static int customCount() {
        ensureEntries();
        refreshCustomDir();
        int count = 0;
        for (int i = BUILTIN_PATHS.length; i < ENTRIES.size(); i++) {
            count++;
        }
        return count;
    }

    /**
     * Opens the custom wallpaper folder (refreshes the list first). Returns
     * whether a file manager was launched.
     */
    public static boolean openCustomFolder() {
        try {
            Path dir = customDir();
            Files.createDirectories(dir);
            refreshCustomDir();
            net.minecraft.util.Util.getPlatform().openPath(dir);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String label() {
        ensureEntries();
        int index = Math.max(0, Math.min(activeIndex, ENTRIES.size() - 1));
        return ENTRIES.get(index).label();
    }

    public static int count() {
        ensureEntries();
        return ENTRIES.size();
    }

    public static int activeIndex() {
        ensureEntries();
        return activeIndex;
    }

    public static String customPath() {
        return customSelection;
    }

    /** The current wallpaper texture, fall-back to the default blue wave. */
    public static Identifier texture() {
        ensureEntries();
        int index = Math.max(0, Math.min(activeIndex, ENTRIES.size() - 1));
        Identifier texture = ENTRIES.get(index).texture();
        return texture != null ? texture : CUSTOM_DIR_NOTE;
    }
}
