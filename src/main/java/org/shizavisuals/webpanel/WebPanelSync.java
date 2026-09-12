package org.shizavisuals.webpanel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.shizavisuals.utils.ConfigIO;

/**
 * Локальная замена GlobalsNet/GlobalsData из rocksnow: раскладки и кадры
 * обмениваются через файлы в config/shizavisuals/webpanel/share/ вместо
 * сервера Globals. Каркас сохраняет структуру пакетов PanelInfo/PanelShare,
 * транспорт между разными машинами не реализован.
 */
public final class WebPanelSync {

    private WebPanelSync() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("ShizaVisuals-WebPanel");

    private static final float SEND_INTERVAL = 4.0f;

    private static final long STALE_MS = 20_000L;

    public static final int MAX_REMOTE = 3;

    private static final double RANGE = 64.0;

    public record PanelInfo(int index, Vec position, float yaw, float pitch, double width,
                            double height) {
    }

    public record PanelShare(String author, List<PanelInfo> panels) {
    }

    private record Remote(PanelShare share, long received) {
    }

    private static final Map<String, Remote> INCOMING = new HashMap<>();
    private static final Map<String, Long> FRAME_STAMPS = new HashMap<>();
    private static float sendTimer;
    private static float frameTimer;

    private static boolean showing;

    public record Vec(double x, double y, double z) {
        public Vec3 toVec3d() {
            return new Vec3(x, y, z);
        }
    }

    public static void receive(PanelShare packet) {
        if (packet.panels().isEmpty()) {
            INCOMING.remove(packet.author());
            note(packet.author(), "прислал пустую раскладку");
            return;
        }
        INCOMING.put(packet.author(), new Remote(packet, System.currentTimeMillis()));
        note(packet.author(), "принято окон: " + packet.panels().size());
    }

    public static void receiveFrame(String author, int index, byte[] jpeg) {
        if (!showing || jpeg == null || jpeg.length == 0) {
            return;
        }
        WebPanelStream.receive(author, index, jpeg);
    }

    private static final Map<String, String> DIAGNOSTIC = new HashMap<>();

    public static void note(String author, String state) {
        if (!state.equals(DIAGNOSTIC.put(author, state))) {
            LOGGER.info("WebPanel(друзья) {}: {}", author, state);
        }
    }

    public static void clear() {
        INCOMING.clear();
        DIAGNOSTIC.clear();
        FRAME_STAMPS.clear();
        WebPanelStream.clear();
        sendTimer = 0.0f;
        frameTimer = 0.0f;
    }

    public static void update(float deltaSeconds, List<WebPanelInstance> panels, boolean show,
                               @Nullable WebPanelInstance watched) {
        showing = show;
        String self = selfKey();
        send(deltaSeconds, panels, watched, self);
        pullFrames();
        WebPanelStream.tick();
        if (show) {
            sync(panels);
        } else if (removeRemote(panels)) {
            INCOMING.clear();
            WebPanelStream.clear();
        }
    }

    private static List<WebPanelInstance> own(List<WebPanelInstance> panels) {
        List<WebPanelInstance> own = new ArrayList<>();
        for (WebPanelInstance panel : panels) {
            if (!panel.isRemote() && !panel.isClosing()) {
                own.add(panel);
            }
        }
        return own;
    }

    private static void send(float deltaSeconds, List<WebPanelInstance> panels,
                              @Nullable WebPanelInstance watched, String self) {
        List<WebPanelInstance> own = own(panels);
        boolean live = !INCOMING.isEmpty();

        int index = watched == null ? -1 : own.indexOf(watched);
        WebPanelStream.setSource(live && index >= 0 ? watched : null, Math.max(index, 0));
        if (live) {
            WebPanelStream.dispatch(WebPanelStream.poll(),
                    frame -> writeFrameFile(self, frame.index(), frame.jpeg()));
        }

        sendTimer += deltaSeconds;
        if (sendTimer < SEND_INTERVAL) {
            return;
        }
        sendTimer = 0.0f;

        List<PanelInfo> layout = new ArrayList<>(own.size());
        for (int i = 0; i < own.size(); i++) {
            WebPanelInstance panel = own.get(i);
            Vec3 center = panel.getCenter();
            layout.add(new PanelInfo(i,
                    new Vec(center.x, center.y, center.z),
                    panel.getYaw(), panel.getPitch(), panel.getWidth(), panel.getHeight()));
        }
        try {
            writeLayoutFile(self, layout);
            note("(я)", live
                    ? "сохранено окон в локальный шаринг: " + layout.size()
                    : "раскладка сохранена, но рядом нет чужих окон — кадры не транслирую");
        } catch (Throwable t) {
            LOGGER.warn("WebPanel: раскладка окон не записалась в локальный шаринг: {}", t.toString());
        }

        long now = System.currentTimeMillis();
        INCOMING.keySet().removeIf(owner -> {
            Path file = layoutFile(owner);
            long stamp = stampOf(file);
            if (stamp <= 0L || now - stamp > STALE_MS) {
                note(owner, "замолчал — убираю его окна");
                return true;
            }
            return false;
        });
        scanIncoming(now);
    }

    private static void scanIncoming(long now) {
        Path dir = shareDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".layout.json"))
                    .toList()) {
                String name = file.getFileName().toString();
                String owner = name.substring(0, name.length() - ".layout.json".length());
                if (owner.equals(selfKey())) {
                    continue;
                }
                long stamp = stampOf(file);
                if (stamp <= 0L || now - stamp > STALE_MS) {
                    continue;
                }
                JsonObject root = ConfigIO.read(file);
                if (root == null) {
                    continue;
                }
                List<PanelInfo> panels = new ArrayList<>();
                JsonArray array = root.getAsJsonArray("panels");
                if (array != null) {
                    for (JsonElement element : array) {
                        if (!element.isJsonObject()) {
                            continue;
                        }
                        JsonObject object = element.getAsJsonObject();
                        panels.add(new PanelInfo(
                                object.get("index").getAsInt(),
                                new Vec(object.get("x").getAsDouble(),
                                        object.get("y").getAsDouble(),
                                        object.get("z").getAsDouble()),
                                object.get("yaw").getAsFloat(), object.get("pitch").getAsFloat(),
                                object.get("w").getAsDouble(), object.get("h").getAsDouble()));
                    }
                }
                receive(new PanelShare(owner, panels));
            }
        } catch (Throwable t) {
            LOGGER.warn("WebPanel: не удалось прочитать локальный шаринг: {}", t.toString());
        }
    }

    private static void pullFrames() {
        if (!showing || INCOMING.isEmpty()) {
            return;
        }
        frameTimer += 1.0f / 60.0f;
        if (frameTimer < 0.25f) {
            return;
        }
        frameTimer = 0.0f;
        for (Map.Entry<String, Remote> entry : INCOMING.entrySet()) {
            String owner = entry.getKey();
            for (PanelInfo info : entry.getValue().share().panels()) {
                Path file = frameFile(owner, info.index());
                long stamp = stampOf(file);
                if (stamp <= 0L) {
                    continue;
                }
                Long previous = FRAME_STAMPS.get(owner + '#' + info.index());
                if (previous != null && previous == stamp) {
                    continue;
                }
                FRAME_STAMPS.put(owner + '#' + info.index(), stamp);
                try {
                    receiveFrame(owner, info.index(), Files.readAllBytes(file));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void sync(List<WebPanelInstance> panels) {
        long now = System.currentTimeMillis();
        INCOMING.entrySet().removeIf(entry -> {
            if (now - entry.getValue().received() > STALE_MS) {
                note(entry.getKey(), "замолчал — убираю его окна");
                return true;
            }
            return false;
        });

        List<PanelInfo> wanted = new ArrayList<>();
        List<String> owners = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        for (Map.Entry<String, Remote> entry : INCOMING.entrySet()) {
            int near = 0;
            for (PanelInfo info : entry.getValue().share().panels()) {
                if (info.position().toVec3d().distanceToSqr(eye) > RANGE * RANGE) {
                    continue;
                }
                near++;
                wanted.add(info);
                owners.add(entry.getKey());
            }
            note(entry.getKey(), near > 0
                    ? "показываю окон: " + near
                    : "все окна дальше " + (int) RANGE + " блоков");
        }
        while (wanted.size() > MAX_REMOTE) {
            int worst = 0;
            double far = -1.0;
            for (int i = 0; i < wanted.size(); i++) {
                double distance = wanted.get(i).position().toVec3d().distanceToSqr(eye);
                if (distance > far) {
                    far = distance;
                    worst = i;
                }
            }
            wanted.remove(worst);
            owners.remove(worst);
        }

        List<WebPanelInstance> existing = new ArrayList<>();
        for (WebPanelInstance panel : panels) {
            if (panel.isRemote()) {
                existing.add(panel);
            }
        }

        for (int i = 0; i < wanted.size(); i++) {
            PanelInfo info = wanted.get(i);
            String owner = owners.get(i);
            WebPanelInstance panel;
            if (i < existing.size()) {
                panel = existing.get(i);
                panel.setOrientation(info.yaw(), info.pitch());
                panel.setTargetSize(info.width(), info.height());
                panel.setOrigin(info.position().toVec3d()
                        .subtract(panel.getUp().scale(panel.getHeight() * 0.5)));
            } else {
                panel = new WebPanelInstance(info.position().toVec3d(), info.yaw(), info.pitch(),
                        info.width(), info.height(), WebPanelUrls.HOME);
                panels.add(panel);
            }
            panel.markRemote(owner, owner, info.index());
        }
        for (int i = wanted.size(); i < existing.size(); i++) {
            existing.get(i).requestClose();
        }
    }

    private static boolean removeRemote(List<WebPanelInstance> panels) {
        boolean any = false;
        for (WebPanelInstance panel : panels) {
            if (panel.isRemote() && !panel.isClosing()) {
                panel.requestClose();
                any = true;
            }
        }
        return any;
    }

    private static String selfKey() {
        try {
            String name = Minecraft.getInstance().getUser().getName();
            if (name != null && !name.isBlank()) {
                return sanitizeKey(name);
            }
        } catch (Throwable ignored) {
        }
        return "local";
    }

    private static String sanitizeKey(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (char c : key.toLowerCase().toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        }
        return sb.toString();
    }

    private static Path shareDir() {
        return ConfigIO.resolve("webpanel/share");
    }

    private static Path layoutFile(String owner) {
        return shareDir().resolve(sanitizeKey(owner) + ".layout.json");
    }

    private static Path frameFile(String owner, int index) {
        return shareDir().resolve(sanitizeKey(owner) + "." + index + ".frame.jpg");
    }

    private static long stampOf(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static void writeLayoutFile(String owner, List<PanelInfo> layout) {
        JsonObject root = new JsonObject();
        root.addProperty("author", owner);
        root.addProperty("time", System.currentTimeMillis());
        JsonArray array = new JsonArray();
        for (PanelInfo info : layout) {
            JsonObject object = new JsonObject();
            object.addProperty("index", info.index());
            object.addProperty("x", info.position().x);
            object.addProperty("y", info.position().y);
            object.addProperty("z", info.position().z);
            object.addProperty("yaw", info.yaw());
            object.addProperty("pitch", info.pitch());
            object.addProperty("w", info.width());
            object.addProperty("h", info.height());
            array.add(object);
        }
        root.add("panels", array);
        Path target = layoutFile(owner);
        try {
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, ConfigGson.GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            LOGGER.warn("WebPanel: раскладка не записалась: {}", t.toString());
        }
    }

    private static void writeFrameFile(String owner, int index, byte[] jpeg) {
        Path target = frameFile(owner, index);
        try {
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, jpeg);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            LOGGER.warn("WebPanel: кадр не записался: {}", t.toString());
        }
    }

    private static final class ConfigGson {
        private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .create();
    }
}
