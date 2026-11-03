package org.shizavisuals.menu.ps4;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.ui.AnimatedValue;
import org.shizavisuals.menu.ps5.Ps5SkinCache;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.io.ByteArrayInputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1:1 port of the PS4 Multiplayer screen (Сетевая игра): server list in a
 * PS4 dashboard carousel with favicons, ping information cards, add/edit
 * server modal, direct connect modal and the delete confirmation modal.
 */
public final class Ps4MultiplayerScreen extends Screen {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
    private static final Logger LOGGER = LoggerFactory.getLogger("ShizaVisuals/Multiplayer");

    private final Screen parent;
    private ServerList serverList;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final EventLoopGroupHolder pingerBackend = EventLoopGroupHolder.remote(false);
    /**
     * Dedicated worker for server pings. Vanilla resolves DNS + opens the
     * socket synchronously on the calling thread, so calling pingServer here
     * per server froze the render thread for seconds (or forever with dead
     * hosts) whenever the screen opened. Everything heavy now runs on this
     * daemon thread and only touches UI results through the main executor.
     */
    private final ExecutorService pingerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ShizaVisuals-ServerPinger");
        thread.setDaemon(true);
        return thread;
    });

    // Horizontal carousel.
    private int selectedIndex;
    private float scrollOffset;

    // Animation & interactions.
    private final Map<String, AnimatedValue> hoverAnims = new HashMap<>();
    private final Set<String> activeHovers = new HashSet<>();
    private final List<ClickZone> clickZones = new ArrayList<>();

    // Static favicon cache by ip.
    public static final Map<String, Identifier> SERVER_ICONS = new HashMap<>();
    /** Dynamic icon ids that are actually registered in the TextureManager. */
    public static final Set<String> READY_ICONS = new HashSet<>();
    private static final Set<String> PENDING_FAVICONS = java.util.Collections.synchronizedSet(new HashSet<>());
    private boolean hasPinged = false;

    // Add / Edit server modal state.
    private boolean isEditingModal = false;
    private boolean isAddingNew = true;
    private int editingServerIndex = -1;
    private String inputServerName = "";
    private String inputServerIp = "";
    private int activeInputField = 0; // 0 = Name, 1 = IP

    // Direct connect modal state.
    private boolean isDirectModal = false;
    private String inputDirectIp = "";

    // Delete confirmation modal state.
    private boolean isDeleteConfirm = false;

    private final boolean openAddOnStart;

    private record ClickZone(float x, float y, float w, float h, Runnable action) {
    }

    public Ps4MultiplayerScreen(Screen parent) {
        this(parent, false);
    }

    public Ps4MultiplayerScreen(Screen parent, boolean openAddOnStart) {
        super(Component.literal("ShizaVisuals"));
        this.parent = parent;
        this.openAddOnStart = openAddOnStart;
    }

    private Minecraft client() {
        return this.minecraft != null ? this.minecraft : Minecraft.getInstance();
    }

    /**
     * Stable dynamic favicon texture for a server address, refreshed from the
     * pinged status (via getIconBytes) asynchronously.
     */
    private static Identifier getOrCreateServerIcon(ServerData serverInfo) {
        if (serverInfo == null || serverInfo.ip == null) {
            return null;
        }
        String key = serverInfo.ip.toLowerCase(Locale.ROOT);
        Identifier icon = SERVER_ICONS.get(key);
        if (icon != null) {
            return icon;
        }
        icon = Identifier.parse(String.format("shizavisuals:dynamic/srv_%08x", key.hashCode()));
        SERVER_ICONS.put(key, icon);
        byte[] favicon = serverInfo.getIconBytes();
        if (favicon != null && favicon.length > 0) {
            loadFaviconIntoIcon(favicon, icon);
        }
        return icon;
    }

    private static void updateServerFavicon(ServerData serverInfo) {
        if (serverInfo == null || serverInfo.ip == null) {
            return;
        }
        Identifier icon = SERVER_ICONS.get(serverInfo.ip.toLowerCase(Locale.ROOT));
        if (icon != null) {
            byte[] favicon = serverInfo.getIconBytes();
            if (favicon != null && favicon.length > 0) {
                loadFaviconIntoIcon(favicon, icon);
            }
        }
    }

    private static void loadFaviconIntoIcon(byte[] favicon, Identifier icon) {
        if (!PENDING_FAVICONS.add(icon.toString())) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try (NativeImage img = NativeImage.read(new ByteArrayInputStream(favicon))) {
                NativeImage finalImg = new NativeImage(64, 64, true);
                if (img.getWidth() == 64 && img.getHeight() == 64) {
                    finalImg.copyFrom(img);
                } else {
                    img.resizeSubRectTo(0, 0, img.getWidth(), img.getHeight(), finalImg);
                }
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    try {
                        // Release the previous texture under the same id before
                        // replacing it, otherwise the old one leaks in the
                        // TextureManager on every refresh.
                        if (READY_ICONS.remove(icon.toString())) {
                            client.getTextureManager().release(icon);
                        }
                        client.getTextureManager().register(icon, new DynamicTexture(() -> icon.toString(), finalImg));
                        READY_ICONS.add(icon.toString());
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    protected void init() {
        super.init();
        Ps4Sound.startAmbientMusic();
        // WARM-UP (critical): ServerNameResolver's static init loads its
        // blocklist through the ServiceLoader, which only works on the render
        // (knot) thread. Ping workers on the common pool would poison the
        // class and later break ConnectScreen with NoClassDefFoundError, so
        // initialize it here first.
        try {
            LOGGER.info("Mp screen init: warm-up ServerNameResolver");
            Class.forName("net.minecraft.client.multiplayer.resolver.ServerNameResolver",
                    true, ServerStatusPinger.class.getClassLoader());
            LOGGER.info("Mp screen init: warm-up done");
        } catch (Throwable ignored) {
        }
        if (this.serverList == null) {
            this.serverList = new ServerList(this.client());
            LOGGER.info("Mp screen init: loading server list");
            this.serverList.load();
            LOGGER.info("Mp screen init: loaded {} servers", this.serverList.size());
        }
        if (!this.hasPinged) {
            this.hasPinged = true;
            this.pingAllServers();
        }

        if (this.selectedIndex >= this.getTotalItems()) {
            this.selectedIndex = Math.max(0, this.getTotalItems() - 1);
        }

        if (this.openAddOnStart) {
            this.openAddServerModal();
        }
    }

    private void pingAllServers() {
        if (this.serverList == null) {
            return;
        }
        // Mark the visible state immediately on the render thread, then hand
        // the actual (blocking DNS + socket) work to the dedicated worker.
        List<ServerData> targets = new ArrayList<>();
        for (int i = 0; i < this.serverList.size(); i++) {
            ServerData info = this.serverList.get(i);
            info.setState(ServerData.State.PINGING);
            info.motd = Component.literal("Опрос сервера...");
            targets.add(info);
        }
        LOGGER.info("Mp ping: queueing {} servers on worker thread", targets.size());
        this.pingerExecutor.execute(() -> {
            for (int i = 0; i < targets.size(); i++) {
                ServerData info = targets.get(i);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                LOGGER.info("Mp ping: {} ({}/{})", info.ip, i + 1, targets.size());
                try {
                    this.pinger.pingServer(info, () -> {
                    }, () -> {
                        if (this.minecraft != null) {
                            this.minecraft.execute(() -> updateServerFavicon(info));
                        }
                    }, this.pingerBackend);
                } catch (Exception e) {
                    info.setState(ServerData.State.UNREACHABLE);
                    info.motd = Component.literal("Не удалось подключиться");
                    LOGGER.info("Mp ping: {} failed: {}", info.ip, e.toString());
                }
            }
            LOGGER.info("Mp ping: finished queue");
        });
    }

    private int getTotalItems() {
        int count = this.serverList != null ? this.serverList.size() : 0;
        return count + 1; // +1 for the "Add Server" tile at the beginning.
    }

    private void connectToServer(ServerData serverInfo) {
        if (serverInfo == null || serverInfo.ip == null || serverInfo.ip.isBlank()) {
            return;
        }
        Ps4Sound.playUiSound("ps4_select");
        Ps4Sound.stopAmbientMusic();
        ServerAddress serverAddress = ServerAddress.parseString(serverInfo.ip);
        ConnectScreen.startConnecting(this, this.client(), serverAddress, serverInfo, false, null);
    }

    @Override
    public void tick() {
        super.tick();
        try {
            this.pinger.tick();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void removed() {
        super.removed();
        try {
            this.pinger.removeAll();
        } catch (Exception ignored) {
        }
        this.pingerExecutor.shutdownNow();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.isEditingModal && !this.isDirectModal && !this.isDeleteConfirm) {
            double delta = verticalAmount != 0 ? verticalAmount : -horizontalAmount;
            if (delta > 0 && this.selectedIndex > 0) {
                this.selectedIndex--;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            } else if (delta < 0 && this.selectedIndex < this.getTotalItems() - 1) {
                this.selectedIndex++;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (this.isDeleteConfirm) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.isDeleteConfirm = false;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                this.deleteCurrentServer();
                return true;
            }
            return true;
        }

        if (this.isDirectModal) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.isDirectModal = false;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (!this.inputDirectIp.isBlank()) {
                    ServerData directInfo = new ServerData("Direct Connect", this.inputDirectIp.trim(), ServerData.Type.OTHER);
                    this.connectToServer(directInfo);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !this.inputDirectIp.isEmpty()) {
                this.inputDirectIp = this.inputDirectIp.substring(0, this.inputDirectIp.length() - 1);
                return true;
            }
            return true;
        }

        if (this.isEditingModal) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.isEditingModal = false;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                this.activeInputField = (this.activeInputField + 1) % 2;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                this.saveModalServer();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (this.activeInputField == 0 && !this.inputServerName.isEmpty()) {
                    this.inputServerName = this.inputServerName.substring(0, this.inputServerName.length() - 1);
                } else if (this.activeInputField == 1 && !this.inputServerIp.isEmpty()) {
                    this.inputServerIp = this.inputServerIp.substring(0, this.inputServerIp.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            Ps4Sound.playUiSound("ps4_scroll");
            this.client().setScreenAndShow(this.parent);
            return true;
        }

        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            if (this.selectedIndex > 0) {
                this.selectedIndex--;
                Ps4Sound.playUiSound("ps4_scroll");
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            if (this.selectedIndex < this.getTotalItems() - 1) {
                this.selectedIndex++;
                Ps4Sound.playUiSound("ps4_scroll");
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.selectedIndex == 0) {
                this.openAddServerModal();
            } else {
                ServerData info = this.serverList.get(this.selectedIndex - 1);
                this.connectToServer(info);
            }
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codePoint = event.codepoint();
        if (this.isDirectModal) {
            if (codePoint >= 32 && codePoint != 127) {
                this.inputDirectIp += new String(Character.toChars(codePoint));
                return true;
            }
            return true;
        }
        if (this.isEditingModal) {
            if (codePoint >= 32 && codePoint != 127) {
                String character = new String(Character.toChars(codePoint));
                if (this.activeInputField == 0 && this.inputServerName.length() < 32) {
                    this.inputServerName += character;
                } else if (this.activeInputField == 1 && this.inputServerIp.length() < 64) {
                    this.inputServerIp += character;
                }
                return true;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            float mx = (float) event.x();
            float my = (float) event.y();

            for (int i = this.clickZones.size() - 1; i >= 0; i--) {
                ClickZone zone = this.clickZones.get(i);
                if (Ps4Helpers.inRect(mx, my, zone.x(), zone.y(), zone.w(), zone.h())) {
                    Ps4Sound.playUiSound("ps4_select");
                    zone.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    private void openAddServerModal() {
        this.isEditingModal = true;
        this.isAddingNew = true;
        this.inputServerName = "Minecraft Server";
        this.inputServerIp = "";
        this.activeInputField = 1;
    }

    private void openEditServerModal(int srvIdx) {
        ServerData info = this.serverList.get(srvIdx);
        this.isEditingModal = true;
        this.isAddingNew = false;
        this.editingServerIndex = srvIdx;
        this.inputServerName = info.name;
        this.inputServerIp = info.ip;
        this.activeInputField = 1;
    }

    private void saveModalServer() {
        String name = this.inputServerName.trim();
        String ip = this.inputServerIp.trim();
        if (name.isEmpty()) {
            name = "Minecraft Server";
        }
        if (ip.isEmpty()) {
            return;
        }
        if (this.isAddingNew) {
            ServerData newSrv = new ServerData(name, ip, ServerData.Type.OTHER);
            this.serverList.add(newSrv, false);
            this.serverList.save();
            this.selectedIndex = this.serverList.size(); // Focus the newly added server.
            this.pingAllServers();
        } else if (this.editingServerIndex >= 0 && this.editingServerIndex < this.serverList.size()) {
            ServerData srv = this.serverList.get(this.editingServerIndex);
            srv.name = name;
            srv.ip = ip;
            srv.setState(ServerData.State.INITIAL);
            this.serverList.save();
            this.pingAllServers();
        }
        this.isEditingModal = false;
    }

    private void deleteCurrentServer() {
        if (this.selectedIndex > 0 && this.serverList != null) {
            int srvIdx = this.selectedIndex - 1;
            if (srvIdx < this.serverList.size()) {
                ServerData srv = this.serverList.get(srvIdx);
                this.serverList.remove(srv);
                this.serverList.save();
                if (this.selectedIndex >= this.getTotalItems()) {
                    this.selectedIndex = Math.max(0, this.getTotalItems() - 1);
                }
                this.pingAllServers();
            }
        }
        this.isDeleteConfirm = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float screenW = this.width;
        float screenH = this.height;
        float scale = Math.min(screenW / 960.0F, screenH / 540.0F);
        float mx = (float) mouseX;
        float my = (float) mouseY;

        this.clickZones.clear();
        RenderContext.enter2D(this.minecraft.gui, graphics, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();

            // 1. Dynamic PS4 Theme Background.
            Ps4Helpers.coverWallpaper(Ps4Wallpapers.texture(), screenW, screenH, 1.0F);

            // Subtle top/bottom vignette.
            Ps4Helpers.rect(0.0F, 0.0F, screenW, 36.0F * scale, 0.0F, Ps4Helpers.tint(0xFF000000, 0.22F));
            Ps4Helpers.rect(0.0F, screenH - 42.0F * scale, screenW, 42.0F * scale, 0.0F, Ps4Helpers.tint(0xFF000000, 0.32F));

            // PS4 ambient floating light embers (halved: glow passes are costly).
            long now = System.currentTimeMillis();
            for (int p = 0; p < 12; p++) {
                float seed = (float) (p * 94.386);
                float speed = 0.00025F + (p % 5) * 0.0001F;
                float px = ((seed * 113.7F + now * speed * screenW) % screenW + screenW) % screenW;
                float py = ((seed * 271.3F + (float) Math.sin((now * 0.001) + seed) * 40.0F) % screenH + screenH) % screenH;
                float pSize = (1.5F + (p % 3) * 1.2F) * scale;
                float pAlpha = 0.22F + 0.25F * (float) Math.sin((now * 0.002) + seed * 2.0F);
                Ps4Helpers.glow(px, py, pSize, pSize, 0.0F, pSize * 2.5F,
                        Ps4Helpers.tint(Ps4Helpers.WHITE, pAlpha * 0.8F));
                Ps4Helpers.rect(px, py, pSize, pSize, 0.0F, Ps4Helpers.tint(Ps4Helpers.WHITE, Math.max(0.0F, pAlpha)));
            }

            // 2. Top bar (back button, direct connect, profile, clock).
            this.drawTopBar(screenW, scale, mx, my);

            // 3. Server tiles carousel.
            this.drawCarousel(screenW, screenH, scale, mx, my);

            // 4. Active tile info & actions.
            this.drawSelectedInfo(screenW, screenH, scale, mx, my);

            // 5. Modals.
            if (this.isEditingModal) {
                this.drawEditModal(screenW, screenH, scale, mx, my);
            } else if (this.isDirectModal) {
                this.drawDirectModal(screenW, screenH, scale, mx, my);
            } else if (this.isDeleteConfirm) {
                this.drawDeleteConfirm(screenW, screenH, scale, mx, my);
            }

            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }

    /** Top PS4 status bar: back button, direct connect shortcut, profile, clock. */
    private void drawTopBar(float screenW, float scale, float mx, float my) {
        float topY = 24.0F * scale;

        // Back button.
        float backW = 86.0F * scale;
        float backH = 22.0F * scale;
        float backX = 36.0F * scale;
        boolean backHover = Ps4Helpers.inRect(mx, my, backX, topY, backW, backH);
        float backHov = this.hoverPC("top_back", backHover);

        Ps4Helpers.rect(backX, topY, backW, backH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.15F + 0.2F * backHov));
        if (backHov > 0.05F) {
            Ps4Helpers.outline(backX, topY, backW, backH, 0.0F, 1.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, backHov));
        }
        Ps4Helpers.icon(Ps4Textures.ICON_BACK, backX + 10.0F * scale, topY + backH * 0.5F, 9.0F * scale, 9.0F * scale, Ps4Helpers.WHITE);
        Ps4Helpers.text(Ps4Fonts.UI, "Назад в меню", backX + 22.0F * scale, topY + backH * 0.5F - 3.2F * scale, 6.2F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(backX, topY, backW, backH, () -> this.client().setScreenAndShow(this.parent)));

        // Direct connect shortcut button.
        float directW = 88.0F * scale;
        float directX = backX + backW + 12.0F * scale;
        boolean directHover = Ps4Helpers.inRect(mx, my, directX, topY, directW, backH);
        float directHov = this.hoverPC("top_direct", directHover);

        Ps4Helpers.rect(directX, topY, directW, backH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.15F + 0.2F * directHov));
        if (directHov > 0.05F) {
            Ps4Helpers.outline(directX, topY, directW, backH, 0.0F, 1.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, directHov));
        }
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Прямой ввод", directX + directW * 0.5F, topY + backH * 0.5F - 3.2F * scale,
                6.2F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(directX, topY, directW, backH, () -> {
            this.isDirectModal = true;
            this.inputDirectIp = "";
        }));

        // Clock (shared cached formatter).
        String timeStr = Ps4Helpers.clockString();
        float timeX = screenW - 44.0F * scale;
        float timeW = Ps4Helpers.measure(Ps4Fonts.UI, timeStr, 7.8F * scale);
        float clockLeft = timeX - timeW;
        Ps4Helpers.text(Ps4Fonts.UI, timeStr, clockLeft, topY + 7.5F * scale, 7.8F * scale, Ps4Helpers.WHITE);

        // Profile pill.
        String profileName = this.client().getUser() != null && !this.client().getUser().getName().isBlank()
                ? this.client().getUser().getName() : "Player";
        float profNameW = Ps4Helpers.measure(Ps4Fonts.UI, profileName, 7.0F * scale);
        float profW = 34.0F * scale + profNameW + 16.0F * scale;
        float profH = 24.0F * scale;
        float profX = clockLeft - profW - 24.0F * scale;

        boolean profHover = Ps4Helpers.inRect(mx, my, profX, topY - 1.0F * scale, profW, profH);
        float profHov = this.hoverPC("top_profile", profHover);

        Ps4Helpers.rect(profX, topY - 1.0F * scale, profW, profH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.22F + 0.35F * profHov));
        if (profHov > 0.05F) {
            Ps4Helpers.outline(profX, topY - 1.0F * scale, profW, profH, 0.0F, 1.2F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, profHov));
        }

        float faceSize = 18.0F * scale;
        float faceX = profX + 4.0F * scale;
        float faceY = topY + 2.0F * scale;
        Ps4Helpers.rect(faceX - 1.0F * scale, faceY - 1.0F * scale, faceSize + 2.0F * scale, faceSize + 2.0F * scale, 0.0F, 0xFF181818);
        Identifier skin = Ps5SkinCache.texture(this.client(), profileName);
        Render2DUtil.texture(faceX, faceY, faceSize, faceSize, skin).color(Ps4Helpers.WHITE)
                .uv(0.125F, 0.125F, 0.25F, 0.25F).draw();

        Ps4Helpers.text(Ps4Fonts.UI, profileName, profX + 26.0F * scale, topY + 7.5F * scale, 7.0F * scale, Ps4Helpers.WHITE);

        this.clickZones.add(new ClickZone(profX, topY - 1.0F * scale, profW, profH, this::backFromProfile));
    }

    private void backFromProfile() {
        Ps4Sound.playUiSound("ps4_scroll");
        this.client().setScreenAndShow(this.parent);
    }

    /** Horizontal carousel of sharp square server tiles. */
    private void drawCarousel(float screenW, float screenH, float scale, float mx, float my) {
        long now = System.currentTimeMillis();
        float carouselY = screenH * 0.22F;
        float tileNormalSize = 102.0F * scale;
        float tileSelectedSize = 118.0F * scale;
        float tileGap = 20.0F * scale;
        float baseStartX = 56.0F * scale;

        int total = this.getTotalItems();
        float targetScroll = this.selectedIndex * (tileNormalSize + tileGap);
        this.scrollOffset += (targetScroll - this.scrollOffset) * 0.22F;

        float curX = baseStartX - this.scrollOffset;

        for (int i = 0; i < total; i++) {
            boolean isSelected = i == this.selectedIndex;
            float curTileSize = isSelected ? tileSelectedSize : tileNormalSize;
            float tileX = curX;
            float tileY = isSelected ? carouselY - (tileSelectedSize - tileNormalSize) * 0.5F : carouselY;
            curX += curTileSize + tileGap;

            int idx = i;
            this.clickZones.add(new ClickZone(tileX, tileY, curTileSize, curTileSize, () -> {
                if (this.selectedIndex != idx) {
                    this.selectedIndex = idx;
                    Ps4Sound.playUiSound("ps4_scroll");
                } else if (idx == 0) {
                    this.openAddServerModal();
                } else if (this.serverList != null && idx - 1 < this.serverList.size()) {
                    ServerData info = this.serverList.get(idx - 1);
                    this.connectToServer(info);
                }
            }));

            // Smooth fade when scrolling off screen (smoothstep).
            float leftFadeStart = baseStartX;
            float leftFadeEnd = -curTileSize * 0.45F;
            float leftFactor = 1.0F;
            if (tileX < leftFadeStart) {
                float t = Math.max(0.0F, Math.min(1.0F, (tileX - leftFadeEnd) / (leftFadeStart - leftFadeEnd)));
                leftFactor = t * t * (3.0F - 2.0F * t);
            }

            float rightFadeStart = screenW - 85.0F * scale;
            float rightFadeEnd = screenW + curTileSize * 0.45F;
            float rightFactor = 1.0F;
            if (tileX + curTileSize > rightFadeStart) {
                float t = Math.max(0.0F, Math.min(1.0F, (rightFadeEnd - (tileX + curTileSize)) / (rightFadeEnd - rightFadeStart)));
                rightFactor = t * t * (3.0F - 2.0F * t);
            }

            float tileAlpha = Math.min(leftFactor, rightFactor);
            if (tileAlpha < 0.01F) {
                continue;
            }

            boolean hovered = Ps4Helpers.inRect(mx, my, tileX, tileY, curTileSize, curTileSize);
            float hov = this.hoverPC("srv_tile_" + i, hovered);

            // Ambient soft drop shadow beneath every server card.
            Ps4Helpers.shadow(tileX + 1.5F * scale, tileY + 3.0F * scale, curTileSize, curTileSize, 0.0F,
                    10.0F * scale, Ps4Helpers.tint(0xFF000000, 0.55F * tileAlpha));

            float footerH = 22.0F * scale;
            float iconAreaH = curTileSize - footerH;

            if (i == 0) {
                // First tile: "+ Добавить сервер".
                Ps4Helpers.rect(tileX, tileY, curTileSize, curTileSize, 0.0F,
                        Ps4Helpers.tint(Ps4Helpers.ACCENT, (isSelected ? 0.45F : (0.2F + 0.15F * hov)) * tileAlpha));
                Ps4Helpers.textCentered(Ps4Fonts.UI, "+", tileX + curTileSize * 0.5F, tileY + iconAreaH * 0.5F - 14.0F * scale,
                        30.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));
            } else {
                // Server tile 1..N.
                ServerData info = this.serverList.get(i - 1);
                Ps4Helpers.rect(tileX, tileY, curTileSize, curTileSize, 0.0F,
                        Ps4Helpers.tint(Ps4Helpers.ACCENT, (isSelected ? 0.40F : (0.2F + 0.12F * hov)) * tileAlpha));

                Identifier srvIcon = getOrCreateServerIcon(info);
                if (srvIcon != null && info.getIconBytes() != null && READY_ICONS.contains(srvIcon.toString())) {
                    float iconPad = 8.0F * scale;
                    float iconS = iconAreaH - iconPad * 2.0F;
                    float iconY = tileY + (iconAreaH - iconS) * 0.5F;
                    Render2DUtil.dynamicTexture(tileX + (curTileSize - iconS) * 0.5F, iconY, iconS, iconS, srvIcon)
                            .color(Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha)).draw();
                } else {
                    // Default game art cover.
                    float iconS = iconAreaH * 0.48F;
                    Ps4Helpers.icon(Ps4Textures.ICON_SINGLEPLAYER, tileX + curTileSize * 0.5F, tileY + iconAreaH * 0.5F,
                            iconS, iconS, Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));
                }
            }

            // Unified footer bar across all tiles.
            Ps4Helpers.rect(tileX, tileY + curTileSize - footerH, curTileSize, footerH, 0.0F, Ps4Helpers.tint(0xB0101018, tileAlpha));
            String footerText = i == 0 ? "Добавить сервер" : this.serverList.get(i - 1).name;
            String fitted = Ps4Helpers.fit(Ps4Fonts.UI, footerText, 6.8F * scale, curTileSize + 28.0F * scale);
            Ps4Helpers.textCentered(Ps4Fonts.UI, fitted, tileX + curTileSize * 0.5F,
                    tileY + curTileSize - footerH * 0.5F - 3.2F * scale, 6.8F * scale,
                    Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));

            // Visual effects for the selected or hovered tile.
            if (isSelected) {
                float breath = 0.30F + 0.14F * (float) Math.sin(now * 0.0035);

                // 1. Breathing aura bloom.
                Ps4Helpers.glow(tileX, tileY, curTileSize, curTileSize, 0.0F, 16.0F * scale,
                        Ps4Helpers.tint(Ps4Helpers.ACCENT, breath * tileAlpha));
                // 2. White outer focus glow.
                Ps4Helpers.glow(tileX, tileY, curTileSize, curTileSize, 0.0F, 8.0F * scale,
                        Ps4Helpers.tint(Ps4Helpers.WHITE, 0.45F * tileAlpha));
                // 3. Crisp white PS4 frame.
                Ps4Helpers.outline(tileX, tileY, curTileSize, curTileSize, 0.0F, 2.6F * scale,
                        Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));

                // 4. Subtle shimmer sweep.
                float sweepProg = (now % 3200L) / 3200.0F;
                if (sweepProg < 0.32F) {
                    float sx = tileX + (sweepProg / 0.32F) * (curTileSize + 24.0F * scale) - 12.0F * scale;
                    Ps4Helpers.glow(sx, tileY + 3.0F * scale, 10.0F * scale, curTileSize - 6.0F * scale, 0.0F, 6.0F * scale,
                            Ps4Helpers.tint(Ps4Helpers.WHITE, 0.22F * tileAlpha));
                }
            } else if (hov > 0.05F) {
                Ps4Helpers.glow(tileX, tileY, curTileSize, curTileSize, 0.0F, 8.0F * scale,
                        Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.35F * hov * tileAlpha));
                Ps4Helpers.outline(tileX, tileY, curTileSize, curTileSize, 0.0F, 1.4F * scale,
                        Ps4Helpers.tint(Ps4Helpers.WHITE, hov * 0.6F * tileAlpha));
            }
        }
    }

    /** Detail view below the carousel: title, connect button and info cards. */
    private void drawSelectedInfo(float screenW, float screenH, float scale, float mx, float my) {
        float infoY = screenH * 0.50F;
        float leftX = 56.0F * scale;

        if (this.selectedIndex == 0) {
            // "+ Добавить сервер" selected.
            Ps4Helpers.text(Ps4Fonts.UI, "ДОБАВИТЬ НОВЫЙ СЕРВЕР", leftX, infoY, 15.0F * scale, Ps4Helpers.WHITE);
            Ps4Helpers.text(Ps4Fonts.UI, "Добавьте любимый сервер в список быстрого доступа",
                    leftX, infoY + 22.0F * scale, 7.5F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, 0xB3 / 255.0F));

            float btnW = 120.0F * scale;
            float btnH = 26.0F * scale;
            float btnY = infoY + 44.0F * scale;
            boolean btnHov = Ps4Helpers.inRect(mx, my, leftX, btnY, btnW, btnH);
            float hovVal = this.hoverPC("btn_create_srv", btnHov);

            Ps4Helpers.rect(leftX, btnY, btnW, btnH, 0.0F,
                    Ps4Helpers.lerp(Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.4F), Ps4Helpers.WHITE, hovVal));
            int txtCol = hovVal > 0.5F ? 0xFF000000 : Ps4Helpers.WHITE;
            Ps4Helpers.textCentered(Ps4Fonts.UI, "Добавить", leftX + btnW * 0.5F, btnY + btnH * 0.5F - 4.0F * scale,
                    7.8F * scale, txtCol);
            this.clickZones.add(new ClickZone(leftX, btnY, btnW, btnH, this::openAddServerModal));
            return;
        }

        int srvIdx = this.selectedIndex - 1;
        if (this.serverList == null || srvIdx >= this.serverList.size()) {
            return;
        }
        ServerData info = this.serverList.get(srvIdx);

        // Large PS4 game title.
        Ps4Helpers.text(Ps4Fonts.UI, info.name != null ? info.name.toUpperCase(Locale.ROOT) : "", leftX, infoY,
                16.0F * scale, Ps4Helpers.WHITE);

        // Connect button.
        float btnRowY = infoY + 28.0F * scale;
        float startW = 126.0F * scale;
        float startH = 28.0F * scale;
        boolean startHover = Ps4Helpers.inRect(mx, my, leftX, btnRowY, startW, startH);
        float startHov = this.hoverPC("btn_start_game", startHover);

        Ps4Helpers.rect(leftX, btnRowY, startW, startH, 0.0F,
                Ps4Helpers.lerp(Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.55F), Ps4Helpers.WHITE, startHov));
        if (startHov > 0.05F) {
            Ps4Helpers.outline(leftX, btnRowY, startW, startH, 0.0F, 1.5F * scale, Ps4Helpers.WHITE);
        }
        int startTxtCol = startHov > 0.5F ? 0xFF101010 : Ps4Helpers.WHITE;
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Подключиться", leftX + startW * 0.5F, btnRowY + startH * 0.5F - 4.2F * scale,
                8.2F * scale, startTxtCol);
        this.clickZones.add(new ClickZone(leftX, btnRowY, startW, startH, () -> this.connectToServer(info)));

        // Small action pills (Обновить, Редактировать, Удалить).
        float pillH = 22.0F * scale;
        float pillY = btnRowY + 3.0F * scale;

        float refW = 68.0F * scale;
        float refX = leftX + startW + 14.0F * scale;
        boolean refHover = Ps4Helpers.inRect(mx, my, refX, pillY, refW, pillH);
        float refHov = this.hoverPC("pill_ref", refHover);
        Ps4Helpers.rect(refX, pillY, refW, pillH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.2F + 0.25F * refHov));
        if (refHov > 0.05F) {
            Ps4Helpers.outline(refX, pillY, refW, pillH, 0.0F, 1.0F * scale, Ps4Helpers.WHITE);
        }
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Обновить", refX + refW * 0.5F, pillY + pillH * 0.5F - 3.2F * scale,
                6.2F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(refX, pillY, refW, pillH, this::pingAllServers));

        float editW = 86.0F * scale;
        float editX = refX + refW + 10.0F * scale;
        boolean editHover = Ps4Helpers.inRect(mx, my, editX, pillY, editW, pillH);
        float editHov = this.hoverPC("pill_edit", editHover);
        Ps4Helpers.rect(editX, pillY, editW, pillH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.2F + 0.25F * editHov));
        if (editHov > 0.05F) {
            Ps4Helpers.outline(editX, pillY, editW, pillH, 0.0F, 1.0F * scale, Ps4Helpers.WHITE);
        }
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Настройки", editX + editW * 0.5F, pillY + pillH * 0.5F - 3.2F * scale,
                6.2F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(editX, pillY, editW, pillH, () -> this.openEditServerModal(srvIdx)));

        float delW = 64.0F * scale;
        float delX = editX + editW + 10.0F * scale;
        boolean delHover = Ps4Helpers.inRect(mx, my, delX, pillY, delW, pillH);
        float delHov = this.hoverPC("pill_del", delHover);
        Ps4Helpers.rect(delX, pillY, delW, pillH, 0.0F, Ps4Helpers.tint(0xFFB03030, 0.2F + 0.3F * delHov));
        if (delHov > 0.05F) {
            Ps4Helpers.outline(delX, pillY, delW, pillH, 0.0F, 1.0F * scale, Ps4Helpers.WHITE);
        }
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Удалить", delX + delW * 0.5F, pillY + pillH * 0.5F - 3.2F * scale,
                6.2F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(delX, pillY, delW, pillH, () -> this.isDeleteConfirm = true));

        // Info cards grid.
        float gridY = btnRowY + startH + 18.0F * scale;
        float cardW = 145.0F * scale;
        float cardH = 68.0F * scale;
        float cardGap = 12.0F * scale;

        // Card 1: IP address.
        this.drawWidgetCard(leftX, gridY, cardW, cardH, scale, "АДРЕС СЕРВЕРА", info.ip, Ps4Helpers.tint(Ps4Helpers.WHITE, 0xB3 / 255.0F));

        // Card 2: online players.
        String playersStr = info.players != null && info.state() == ServerData.State.SUCCESSFUL
                ? info.players.online() + " / " + info.players.max() : "Опрос...";
        this.drawWidgetCard(leftX + cardW + cardGap, gridY, cardW, cardH, scale, "ИГРОКИ ОНЛАЙН", playersStr, 0xFF4AE07C);

        // Card 3: ping / status.
        String pingStr = info.ping > 0 ? info.ping + " ms"
                : (info.state() == ServerData.State.PINGING ? "Измерение..." : "Недоступен");
        int pingCol = info.ping > 0 ? (info.ping < 100 ? 0xFF4AE07C : 0xFFE5A93C) : 0xFFFF5050;
        this.drawWidgetCard(leftX + (cardW + cardGap) * 2.0F, gridY, cardW, cardH, scale, "СЕТЬ / ПИНГ", pingStr, pingCol);

        // Card 4: MOTD / description.
        float motdW = screenW - (leftX + (cardW + cardGap) * 3.0F) - 40.0F * scale;
        if (motdW > 160.0F * scale) {
            String motdStr = info.motd != null ? info.motd.getString() : "";
            String statusStr = info.status != null && !info.status.getString().isBlank() ? info.status.getString() : motdStr;
            this.drawWidgetCard(leftX + (cardW + cardGap) * 3.0F, gridY, motdW, cardH, scale, "ОПИСАНИЕ СЕРВЕРА", statusStr, Ps4Helpers.WHITE);
        }
    }

    private void drawWidgetCard(float x, float y, float w, float h, float scale, String header, String content, int contentCol) {
        // Sharp PS4 widget card.
        Ps4Helpers.rect(x, y, w, h, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.18F));
        Ps4Helpers.outline(x, y, w, h, 0.0F, 0.8F * scale, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.35F));

        // Header label.
        Ps4Helpers.text(Ps4Fonts.UI, header, x + 10.0F * scale, y + 9.0F * scale, 5.8F * scale,
                Ps4Helpers.tint(Ps4Helpers.WHITE, 0xB3 / 255.0F));

        // Main content (clipped so it cannot escape the card).
        Ps4Helpers.text(Ps4Fonts.UI, Ps4Helpers.fit(Ps4Fonts.UI, content, 7.2F * scale, w - 20.0F * scale),
                x + 10.0F * scale, y + 25.0F * scale, 7.2F * scale, contentCol);
    }

    /** Modal dialog for adding / editing a server. */
    private void drawEditModal(float screenW, float screenH, float scale, float mx, float my) {
        Ps4Helpers.rect(0.0F, 0.0F, screenW, screenH, 0.0F, 0x99000000);

        float modalW = 280.0F * scale;
        float modalH = 175.0F * scale;
        float modalX = (screenW - modalW) * 0.5F;
        float modalY = (screenH - modalH) * 0.5F;

        Ps4Helpers.rect(modalX, modalY, modalW, modalH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.45F));
        Ps4Helpers.outline(modalX, modalY, modalW, modalH, 0.0F, 1.5F * scale, Ps4Helpers.WHITE);

        String title = this.isAddingNew ? "Добавить сервер" : "Редактировать сервер";
        Ps4Helpers.textCentered(Ps4Fonts.UI, title, modalX + modalW * 0.5F, modalY + 14.0F * scale, 9.5F * scale, Ps4Helpers.WHITE);

        // Field 1: name.
        float fieldW = modalW - 36.0F * scale;
        float fieldH = 22.0F * scale;
        float field1Y = modalY + 38.0F * scale;
        Ps4Helpers.text(Ps4Fonts.UI, "Название сервера:", modalX + 18.0F * scale, field1Y, 6.2F * scale,
                Ps4Helpers.tint(Ps4Helpers.WHITE, 0xB3 / 255.0F));
        float input1Y = field1Y + 11.0F * scale;
        Ps4Helpers.rect(modalX + 18.0F * scale, input1Y, fieldW, fieldH, 0.0F, 0x80101010);
        if (this.activeInputField == 0) {
            Ps4Helpers.outline(modalX + 18.0F * scale, input1Y, fieldW, fieldH, 0.0F, 1.2F * scale, Ps4Helpers.WHITE);
        }
        String nameDisplay = Ps4Helpers.fit(Ps4Fonts.UI, this.inputServerName
                        + (this.activeInputField == 0 && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""),
                6.8F * scale, fieldW - 12.0F * scale);
        Ps4Helpers.text(Ps4Fonts.UI, nameDisplay, modalX + 24.0F * scale, input1Y + fieldH * 0.5F - 3.2F * scale, 6.8F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(modalX + 18.0F * scale, input1Y, fieldW, fieldH, () -> this.activeInputField = 0));

        // Field 2: IP address.
        float field2Y = input1Y + fieldH + 10.0F * scale;
        Ps4Helpers.text(Ps4Fonts.UI, "Адрес сервера (IP):", modalX + 18.0F * scale, field2Y, 6.2F * scale,
                Ps4Helpers.tint(Ps4Helpers.WHITE, 0xB3 / 255.0F));
        float input2Y = field2Y + 11.0F * scale;
        Ps4Helpers.rect(modalX + 18.0F * scale, input2Y, fieldW, fieldH, 0.0F, 0x80101010);
        if (this.activeInputField == 1) {
            Ps4Helpers.outline(modalX + 18.0F * scale, input2Y, fieldW, fieldH, 0.0F, 1.2F * scale, Ps4Helpers.WHITE);
        }
        String ipDisplay = Ps4Helpers.fit(Ps4Fonts.UI, this.inputServerIp
                        + (this.activeInputField == 1 && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""),
                6.8F * scale, fieldW - 12.0F * scale);
        Ps4Helpers.text(Ps4Fonts.UI, ipDisplay, modalX + 24.0F * scale, input2Y + fieldH * 0.5F - 3.2F * scale, 6.8F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(modalX + 18.0F * scale, input2Y, fieldW, fieldH, () -> this.activeInputField = 1));

        // Buttons (Save / Cancel).
        float btnW = 100.0F * scale;
        float btnH = 22.0F * scale;
        float btnY = modalY + modalH - 32.0F * scale;

        float saveX = modalX + 30.0F * scale;
        boolean saveHov = Ps4Helpers.inRect(mx, my, saveX, btnY, btnW, btnH);
        Ps4Helpers.rect(saveX, btnY, btnW, btnH, 0.0F, saveHov ? Ps4Helpers.WHITE : Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.6F));
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Сохранить", saveX + btnW * 0.5F, btnY + btnH * 0.5F - 3.2F * scale,
                6.8F * scale, saveHov ? 0xFF000000 : Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(saveX, btnY, btnW, btnH, this::saveModalServer));

        float cancelX = modalX + modalW - btnW - 30.0F * scale;
        boolean cancelHov = Ps4Helpers.inRect(mx, my, cancelX, btnY, btnW, btnH);
        Ps4Helpers.rect(cancelX, btnY, btnW, btnH, 0.0F, cancelHov ? Ps4Helpers.WHITE : 0x66333333);
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Отмена", cancelX + btnW * 0.5F, btnY + btnH * 0.5F - 3.2F * scale,
                6.8F * scale, cancelHov ? 0xFF000000 : Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(cancelX, btnY, btnW, btnH, () -> this.isEditingModal = false));
    }

    /** Modal dialog for direct connect. */
    private void drawDirectModal(float screenW, float screenH, float scale, float mx, float my) {
        Ps4Helpers.rect(0.0F, 0.0F, screenW, screenH, 0.0F, 0x99000000);

        float modalW = 260.0F * scale;
        float modalH = 135.0F * scale;
        float modalX = (screenW - modalW) * 0.5F;
        float modalY = (screenH - modalH) * 0.5F;

        Ps4Helpers.rect(modalX, modalY, modalW, modalH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.45F));
        Ps4Helpers.outline(modalX, modalY, modalW, modalH, 0.0F, 1.5F * scale, Ps4Helpers.WHITE);

        Ps4Helpers.textCentered(Ps4Fonts.UI, "Прямое подключение", modalX + modalW * 0.5F, modalY + 14.0F * scale,
                9.5F * scale, Ps4Helpers.WHITE);

        float fieldW = modalW - 36.0F * scale;
        float fieldH = 22.0F * scale;
        float inputY = modalY + 45.0F * scale;
        Ps4Helpers.rect(modalX + 18.0F * scale, inputY, fieldW, fieldH, 0.0F, 0x80101010);
        Ps4Helpers.outline(modalX + 18.0F * scale, inputY, fieldW, fieldH, 0.0F, 1.2F * scale, Ps4Helpers.WHITE);

        String ipDisplay = Ps4Helpers.fit(Ps4Fonts.UI, this.inputDirectIp
                        + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""),
                6.8F * scale, fieldW - 12.0F * scale);
        Ps4Helpers.text(Ps4Fonts.UI, ipDisplay, modalX + 24.0F * scale, inputY + fieldH * 0.5F - 3.2F * scale, 6.8F * scale, Ps4Helpers.WHITE);

        float btnW = 95.0F * scale;
        float btnH = 22.0F * scale;
        float btnY = modalY + modalH - 32.0F * scale;

        float joinX = modalX + 24.0F * scale;
        boolean joinHov = Ps4Helpers.inRect(mx, my, joinX, btnY, btnW, btnH);
        Ps4Helpers.rect(joinX, btnY, btnW, btnH, 0.0F, joinHov ? Ps4Helpers.WHITE : Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.6F));
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Войти", joinX + btnW * 0.5F, btnY + btnH * 0.5F - 3.2F * scale,
                6.8F * scale, joinHov ? 0xFF000000 : Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(joinX, btnY, btnW, btnH, () -> {
            if (!this.inputDirectIp.isBlank()) {
                ServerData info = new ServerData("Direct Connect", this.inputDirectIp.trim(), ServerData.Type.OTHER);
                this.connectToServer(info);
            }
        }));

        float cancelX = modalX + modalW - btnW - 24.0F * scale;
        boolean cancelHov = Ps4Helpers.inRect(mx, my, cancelX, btnY, btnW, btnH);
        Ps4Helpers.rect(cancelX, btnY, btnW, btnH, 0.0F, cancelHov ? Ps4Helpers.WHITE : 0x66333333);
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Отмена", cancelX + btnW * 0.5F, btnY + btnH * 0.5F - 3.2F * scale,
                6.8F * scale, cancelHov ? 0xFF000000 : Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(cancelX, btnY, btnW, btnH, () -> this.isDirectModal = false));
    }

    /** Modal dialog for delete confirmation. */
    private void drawDeleteConfirm(float screenW, float screenH, float scale, float mx, float my) {
        Ps4Helpers.rect(0.0F, 0.0F, screenW, screenH, 0.0F, 0x99000000);

        float modalW = 240.0F * scale;
        float modalH = 110.0F * scale;
        float modalX = (screenW - modalW) * 0.5F;
        float modalY = (screenH - modalH) * 0.5F;

        Ps4Helpers.rect(modalX, modalY, modalW, modalH, 0.0F, 0xE6201515);
        Ps4Helpers.outline(modalX, modalY, modalW, modalH, 0.0F, 1.5F * scale, 0xFFFF4040);

        Ps4Helpers.textCentered(Ps4Fonts.UI, "Удалить сервер из списка?", modalX + modalW * 0.5F, modalY + 16.0F * scale,
                8.5F * scale, Ps4Helpers.WHITE);

        float btnW = 85.0F * scale;
        float btnH = 22.0F * scale;
        float btnY = modalY + modalH - 34.0F * scale;

        float delX = modalX + 24.0F * scale;
        boolean delHov = Ps4Helpers.inRect(mx, my, delX, btnY, btnW, btnH);
        Ps4Helpers.rect(delX, btnY, btnW, btnH, 0.0F, delHov ? Ps4Helpers.WHITE : 0xFFCC3333);
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Удалить", delX + btnW * 0.5F, btnY + btnH * 0.5F - 3.2F * scale,
                6.8F * scale, delHov ? 0xFF000000 : Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(delX, btnY, btnW, btnH, this::deleteCurrentServer));

        float cancelX = modalX + modalW - btnW - 24.0F * scale;
        boolean cancelHov = Ps4Helpers.inRect(mx, my, cancelX, btnY, btnW, btnH);
        Ps4Helpers.rect(cancelX, btnY, btnW, btnH, 0.0F, cancelHov ? Ps4Helpers.WHITE : 0x66333333);
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Отмена", cancelX + btnW * 0.5F, btnY + btnH * 0.5F - 3.2F * scale,
                6.8F * scale, cancelHov ? 0xFF000000 : Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(cancelX, btnY, btnW, btnH, () -> this.isDeleteConfirm = false));
    }

    private float hoverPC(String key, boolean hovered) {
        if (hovered) {
            if (this.activeHovers.add(key)) {
                Ps4Sound.playUiSound("ps4_scroll");
            }
        } else {
            this.activeHovers.remove(key);
        }
        AnimatedValue anim = this.hoverAnims.computeIfAbsent(key, k -> new AnimatedValue(140L, Animation.Easing.EASE_OUT_QUAD));
        anim.toward(hovered ? 1.0F : 0.0F);
        return anim.value();
    }
}
