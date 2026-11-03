package org.shizavisuals.menu.title;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.utils.ConfigIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public final class WallpaperStore {
    public static final String FALLBACK_ID = "builtin";
    public static final String CONFIG_KEY = "wallpaper";

    private static final Logger LOGGER = LoggerFactory.getLogger("ShizaVisuals/Wallpapers");
    private static final Identifier FALLBACK_TEXTURE = Identifier.parse("shizavisuals:textures/menu/macos_wallpaper.png");
    private static final Path ROOT = ConfigIO.configDir().resolve("wallpapers").toAbsolutePath().normalize();
    private static final int MAX_FILES = 128;
    private static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_DIMENSION = 16384L;
    private static final long MAX_PIXELS = 16_777_216L;
    private static final Object LOCK = new Object();
    private static final Map<String, TextureState> TEXTURES = new HashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ShizaVisuals-WallpaperLoader");
        thread.setDaemon(true);
        return thread;
    });
    private static final Wallpaper FALLBACK = new Wallpaper(
            FALLBACK_ID,
            "Built-in",
            null,
            FALLBACK_TEXTURE,
            resourceAspect(),
            true,
            null
    );

    private static volatile List<Wallpaper> wallpapers = List.of(FALLBACK);
    private static volatile String selectedId = FALLBACK_ID;
    private static volatile long lastScan;

    private WallpaperStore() {
    }

    public static void reload() {
        List<Wallpaper> next = new ArrayList<>();
        next.add(FALLBACK);
        try {
            Files.createDirectories(ROOT);
            try (Stream<Path> paths = Files.list(ROOT)) {
                paths.filter(WallpaperStore::validFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .limit(MAX_FILES)
                        .forEach(path -> {
                            float aspect = imageAspect(path);
                            if (aspect > 0.0F) {
                                String name = path.getFileName().toString();
                                next.add(new Wallpaper(
                                        name,
                                        displayName(name, next.size()),
                                        path,
                                        textureId(name),
                                        aspect,
                                        false,
                                        stamp(path)
                                ));
                            } else {
                                LOGGER.warn("Skipping unsupported wallpaper {} (convert it to PNG/JPEG)", path.getFileName());
                            }
                        });
            }
        } catch (Exception ignored) {
        }

        synchronized (LOCK) {
            List<String> previousIds = new ArrayList<>();
            for (Wallpaper wallpaper : wallpapers) {
                previousIds.add(wallpaper.storageId());
            }
            wallpapers = List.copyOf(next);
            String stored = MenuConfigStore.getString(CONFIG_KEY, FALLBACK_ID);
            selectedId = find(next, stored) != null ? stored : FALLBACK_ID;
            Set<String> currentIds = new HashSet<>();
            for (Wallpaper wallpaper : next) {
                currentIds.add(wallpaper.storageId());
            }
            TEXTURES.entrySet().removeIf(entry -> {
                if (currentIds.contains(entry.getKey())) {
                    return false;
                }
                release(entry.getValue().textureId());
                return true;
            });
            List<String> nextIds = new ArrayList<>();
            for (Wallpaper wallpaper : next) {
                nextIds.add(wallpaper.storageId());
            }
            if (!nextIds.equals(previousIds)) {
                LOGGER.info("Wallpapers found ({}): {}", next.size() - 1, String.join(", ", nextIds.subList(1, nextIds.size())));
            }
        }
        lastScan = System.currentTimeMillis();
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastScan >= 2000L) {
            reload();
        }
    }

    public static List<Wallpaper> all() {
        return wallpapers;
    }

    public static Wallpaper fallback() {
        return FALLBACK;
    }

    public static Wallpaper selected() {
        Wallpaper selected = find(wallpapers, selectedId);
        return selected == null ? FALLBACK : selected;
    }

    public static Wallpaper find(String id) {
        return find(wallpapers, id);
    }

    public static void select(Wallpaper requested) {
        if (requested == null) {
            return;
        }
        Wallpaper selected = find(requested.storageId());
        if (selected == null) {
            return;
        }
        selectedId = selected.storageId();
        MenuConfigStore.save(data -> data.addProperty(CONFIG_KEY, selected.storageId()));
        requestLoad(selected);
    }

    public static void requestLoad(Wallpaper wallpaper) {
        if (wallpaper == null || wallpaper.builtin() || wallpaper.ready()) {
            return;
        }
        Stamp stamp = wallpaper.stamp();
        if (stamp == null || !safePath(wallpaper.path())) {
            return;
        }
        synchronized (LOCK) {
            TextureState state = TEXTURES.get(wallpaper.storageId());
            if (state != null && state.stamp().equals(stamp) && state.textureId() != null) {
                return;
            }
            if (!LOADING.add(wallpaper.storageId())) {
                return;
            }
        }
        LOADER.execute(() -> load(wallpaper, stamp));
    }

    public static void drawable(Wallpaper wallpaper) {
        if (wallpaper != null && !wallpaper.builtin()) {
            requestLoad(wallpaper);
        }
    }

    private static void load(Wallpaper wallpaper, Stamp expectedStamp) {
        NativeImage image = null;
        boolean submitted = false;
        try {
            if (!safePath(wallpaper.path())
                    || !Files.isRegularFile(wallpaper.path(), LinkOption.NOFOLLOW_LINKS)
                    || Files.size(wallpaper.path()) > MAX_FILE_BYTES
                    || !expectedStamp.equals(stamp(wallpaper.path()))) {
                return;
            }
            image = decode(wallpaper.path());
            if (image == null
                    || image.getWidth() <= 0
                    || image.getHeight() <= 0
                    || image.getWidth() > MAX_DIMENSION
                    || image.getHeight() > MAX_DIMENSION
                    || (long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
                close(image);
                return;
            }
            NativeImage upload = image;
            image = null;
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> register(wallpaper, expectedStamp, upload));
            submitted = true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to load wallpaper {}", wallpaper.displayName(), throwable);
            close(image);
        } finally {
            if (!submitted) {
                LOADING.remove(wallpaper.storageId());
            }
        }
    }

    private static NativeImage decode(Path path) throws Exception {
        try (InputStream inputStream = Files.newInputStream(path)) {
            try {
                return NativeImage.read(inputStream);
            } catch (Throwable nativeFailure) {
                inputStream.close();
            }
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            BufferedImage buffered = ImageIO.read(inputStream);
            if (buffered == null) {
                throw new IOException("Unsupported image format: " + path.getFileName());
            }
            return toNativeImage(buffered);
        }
    }

    private static NativeImage toNativeImage(BufferedImage buffered) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream(Math.max(4096, buffered.getWidth() * buffered.getHeight()));
        if (!ImageIO.write(buffered, "png", png)) {
            throw new IOException("No PNG writer available");
        }
        try (InputStream inputStream = new ByteArrayInputStream(png.toByteArray())) {
            return NativeImage.read(inputStream);
        }
    }

    private static void register(Wallpaper wallpaper, Stamp expectedStamp, NativeImage image) {
        DynamicTexture texture = null;
        boolean registered = false;
        try {
            if (!isCurrent(wallpaper)
                    || !safePath(wallpaper.path())
                    || !expectedStamp.equals(stamp(wallpaper.path()))) {
                close(image);
                return;
            }
            Identifier textureId = wallpaper.textureId();
            texture = new DynamicTexture(() -> textureId.toString(), image);
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
            registered = true;
            synchronized (LOCK) {
                TEXTURES.put(wallpaper.storageId(), new TextureState(expectedStamp, textureId));
            }
        } catch (Throwable throwable) {
            if (registered) {
                return;
            }
            if (texture != null) {
                texture.close();
            } else {
                close(image);
            }
        } finally {
            LOADING.remove(wallpaper.storageId());
        }
    }

    private static boolean isCurrent(Wallpaper wallpaper) {
        Wallpaper current = find(wallpapers, wallpaper.storageId());
        return current != null
                && current.stamp() != null
                && wallpaper.stamp() != null
                && current.stamp().equals(wallpaper.stamp());
    }

    private static boolean validFile(Path path) {
        if (path == null || !hasImageExtension(path.getFileName().toString())) {
            return false;
        }
        try {
            String name = path.getFileName().toString();
            return safeName(name) != null
                    && path.toAbsolutePath().normalize().getParent().equals(ROOT)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasImageExtension(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank() || name.length() > 240
                || name.indexOf('\0') >= 0
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf(':') >= 0
                || !hasImageExtension(name)) {
            return null;
        }
        try {
            Path resolved = ROOT.resolve(name).normalize();
            if (!ROOT.equals(resolved.getParent()) || !name.equals(resolved.getFileName().toString())) {
                return null;
            }
            return name;
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static boolean safePath(Path path) {
        if (path == null) {
            return false;
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            return ROOT.equals(normalized.getParent())
                    && path.getFileName().toString().equals(normalized.getFileName().toString())
                    && !Files.isSymbolicLink(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static float imageAspect(Path path) {
        if (!safePath(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return 0.0F;
        }
        try {
            if (Files.size(path) <= 0L || Files.size(path) > MAX_FILE_BYTES) {
                return 0.0F;
            }
            byte[] header;
            try (InputStream inputStream = Files.newInputStream(path)) {
                header = inputStream.readNBytes(512);
            }
            long[] size = readSize(header);
            if (size == null) {
                return 0.0F;
            }
            long width = size[0];
            long height = size[1];
            if (width <= 0L || height <= 0L || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || width * height > MAX_PIXELS) {
                return 0.0F;
            }
            return width / (float) height;
        } catch (Exception ignored) {
            return 0.0F;
        }
    }

    private static long[] readSize(byte[] header) {
        if (header.length < 16) {
            return null;
        }
        if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return header.length >= 24
                    ? new long[]{readUnsignedInt(header, 16), readUnsignedInt(header, 20)}
                    : null;
        }
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8) {
            return readJpegSize(header);
        }
        if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
            return new long[]{readUnsignedShort(header, 6), readUnsignedShort(header, 8)};
        }
        if (header[0] == 'B' && header[1] == 'M' && header.length >= 26) {
            long width = readUnsignedIntLE(header, 18);
            long height = Math.abs(readUnsignedIntLE(header, 22));
            return new long[]{width, height};
        }
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return readWebpSize(header);
        }
        return null;
    }

    private static long[] readJpegSize(byte[] data) {
        int at = 2;
        while (at + 9 < data.length) {
            if ((data[at] & 0xFF) != 0xFF) {
                return null;
            }
            int marker = data[at + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                at += 2;
                continue;
            }
            int length = ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
            boolean frame = (marker >= 0xC0 && marker <= 0xCF)
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (frame) {
                long height = ((data[at + 5] & 0xFF) << 8) | (data[at + 6] & 0xFF);
                long width = ((data[at + 7] & 0xFF) << 8) | (data[at + 8] & 0xFF);
                return new long[]{width, height};
            }
            at += 2 + Math.max(2, length);
        }
        return null;
    }

    private static long[] readWebpSize(byte[] data) {
        if (data.length < 30) {
            return null;
        }
        String chunk = new String(data, 12, 4, StandardCharsets.US_ASCII);
        if ("VP8X".equals(chunk)) {
            long width = 1L + (((data[24] & 0xFFL) | ((data[25] & 0xFFL) << 8) | ((data[26] & 0xFFL) << 16)) & 0xFFFFFFL);
            long height = 1L + (((data[27] & 0xFFL) | ((data[28] & 0xFFL) << 8) | ((data[29] & 0xFFL) << 16)) & 0xFFFFFFL);
            return new long[]{width, height};
        }
        if ("VP8L".equals(chunk)) {
            int bits = (data[21] & 0xFF) | ((data[22] & 0xFF) << 8) | ((data[23] & 0xFF) << 16) | ((data[24] & 0xFF) << 24);
            long width = (bits & 0x3FFF) + 1L;
            long height = ((bits >> 14) & 0x3FFF) + 1L;
            return new long[]{width, height};
        }
        if ("VP8 ".equals(chunk)) {
            long width = (((data[26] & 0xFFL) | ((data[27] & 0xFFL) << 8)) & 0x3FFF);
            long height = (((data[28] & 0xFFL) | ((data[29] & 0xFFL) << 8)) & 0x3FFF);
            return new long[]{width, height};
        }
        return null;
    }

    private static long readUnsignedInt(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static long readUnsignedIntLE(byte[] bytes, int offset) {
        return ((long) (bytes[offset + 3] & 0xFF) << 24)
                | ((long) (bytes[offset + 2] & 0xFF) << 16)
                | ((long) (bytes[offset + 1] & 0xFF) << 8)
                | (bytes[offset] & 0xFF);
    }

    private static long readUnsignedShort(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static float resourceAspect() {
        try (InputStream inputStream = WallpaperStore.class.getResourceAsStream("/assets/shizavisuals/textures/menu/macos_wallpaper.png")) {
            if (inputStream == null) {
                return 16.0F / 9.0F;
            }
            byte[] header = inputStream.readNBytes(24);
            if (header.length >= 24) {
                long width = readUnsignedInt(header, 16);
                long height = readUnsignedInt(header, 20);
                if (width > 0L && height > 0L) {
                    return width / (float) height;
                }
            }
        } catch (Exception ignored) {
        }
        return 16.0F / 9.0F;
    }

    private static Stamp stamp(Path path) {
        try {
            if (!safePath(path)) {
                return null;
            }
            return new Stamp(Files.size(path), Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String displayName(String name, int index) {
        String value = name;
        int dot = value.lastIndexOf('.');
        if (dot > 0) {
            value = value.substring(0, dot);
        }
        value = value.replace('_', ' ').replace('-', ' ').trim();
        if (value.isBlank() || looksLikeHash(value)) {
            return "Wallpaper " + index;
        }
        return value;
    }

    private static boolean looksLikeHash(String value) {
        if (value.length() < 12) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static Identifier textureId(String name) {
        return Identifier.fromNamespaceAndPath("shizavisuals", "title/wallpaper/" + hash(name));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (NoSuchAlgorithmException ignored) {
            return Integer.toUnsignedString(value.hashCode(), 16);
        }
    }

    private static Wallpaper find(List<Wallpaper> entries, String id) {
        if (id == null) {
            return null;
        }
        for (Wallpaper wallpaper : entries) {
            if (wallpaper.storageId().equals(id)) {
                return wallpaper;
            }
        }
        return null;
    }

    private static void release(Identifier textureId) {
        if (textureId == null) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(textureId);
        } catch (Throwable ignored) {
        }
    }

    private static void close(NativeImage image) {
        if (image != null) {
            try {
                image.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static final class Wallpaper {
        private final String storageId;
        private final String displayName;
        private final Path path;
        private final Identifier textureId;
        private final float aspectRatio;
        private final boolean builtin;
        private final Stamp stamp;

        private Wallpaper(String storageId, String displayName, Path path, Identifier textureId,
                          float aspectRatio, boolean builtin, Stamp stamp) {
            this.storageId = storageId;
            this.displayName = displayName;
            this.path = path;
            this.textureId = textureId;
            this.aspectRatio = aspectRatio > 0.0F ? aspectRatio : 16.0F / 9.0F;
            this.builtin = builtin;
            this.stamp = stamp;
        }

        public String storageId() {
            return this.storageId;
        }

        public String displayName() {
            return this.displayName;
        }

        public Identifier textureId() {
            return this.textureId;
        }

        public float aspectRatio() {
            return this.aspectRatio;
        }

        public boolean builtin() {
            return this.builtin;
        }

        public boolean managed() {
            return !this.builtin;
        }

        public boolean ready() {
            if (this.builtin) {
                return true;
            }
            synchronized (LOCK) {
                TextureState state = TEXTURES.get(this.storageId);
                return state != null && state.textureId() != null && this.stamp != null && this.stamp.equals(state.stamp());
            }
        }

        public Path path() {
            return this.path;
        }

        public Stamp stamp() {
            return this.stamp;
        }
    }

    public record Stamp(long size, long modified) {
    }

    private record TextureState(Stamp stamp, Identifier textureId) {
    }
}
