package org.shizavisuals.feature.impl.misc;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.input.CharacterInputEvent;
import org.shizavisuals.event.events.input.KeyboardInputEvent;
import org.shizavisuals.event.events.input.MouseInputEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.render.Render2DEvent;
import org.shizavisuals.event.events.render.Render3DEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.InputBindSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.webpanel.WebPanelAwtGuard;
import org.shizavisuals.webpanel.WebPanelBlur;
import org.shizavisuals.webpanel.WebPanelStream;
import org.shizavisuals.webpanel.WebPanelBrowserHooks;
import org.shizavisuals.webpanel.WebPanelCrosshair;
import org.shizavisuals.webpanel.WebPanelGrab;
import org.shizavisuals.webpanel.WebPanelInstance;
import org.shizavisuals.webpanel.WebPanelKeyShim;
import org.shizavisuals.webpanel.WebPanelPose;
import org.shizavisuals.webpanel.WebPanelRenderer;
import org.shizavisuals.webpanel.WebPanelStore;
import org.shizavisuals.webpanel.WebPanelSync;
import org.shizavisuals.webpanel.WebPanelUrls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebPanelFeature extends Feature {
    public static final String QUALITY_LOW = "Low";
    public static final String QUALITY_MEDIUM = "Medium";
    public static final String QUALITY_HIGH = "High";
    public static final String QUALITY_ULTRA = "Ultra";

    private static final Logger LOGGER = LoggerFactory.getLogger("ShizaVisuals-WebPanel");

    private static final double SPAWN_DISTANCE = 2.0;
    private static final double PANEL_WIDTH = 1.8;
    private static final double PANEL_HEIGHT = 1.18;
    private static final int MAX_PANELS = 4;
    private static final double RENDER_DISTANCE = 64.0;
    private static final int LEVEL_KEY = GLFW.GLFW_KEY_R;
    private static final float SAVE_INTERVAL = 8.0f;

    public final InputBindSetting placeKey = register(new InputBindSetting("Place Key", BindSetting.UNBOUND));
    public final BooleanSetting snapToWalls = register(new BooleanSetting("Snap To Walls", true));
    public final BooleanSetting friendPanels = register(new BooleanSetting("Friend Panels", false));
    public final BooleanSetting fullControl = register(new BooleanSetting("Full Control", false));
    public final NumberSetting bend = register(new NumberSetting("Bend", 30.0D, 0.0D, 100.0D, 5.0D, "%"));
    public final ModeSetting quality = register(new ModeSetting(
            "Quality", QUALITY_HIGH, QUALITY_LOW, QUALITY_MEDIUM, QUALITY_HIGH, QUALITY_ULTRA));

    private final List<WebPanelInstance> panels = new ArrayList<>();
    private final WebPanelGrab grab = new WebPanelGrab(RENDER_DISTANCE);

    @Nullable
    private WebPanelInstance hovered;
    @Nullable
    private WebPanelInstance.Hit hoveredHit;
    private int hoverX;
    private int hoverY;

    @Nullable
    private WebPanelInstance movePanel;
    private int moveX = Integer.MIN_VALUE;
    private int moveY = Integer.MIN_VALUE;

    private long lastRenderNanos = System.nanoTime();
    private float crossProgress;

    @Nullable
    private WebPanelInstance typingPanel;
    @Nullable
    private WebPanelInstance focusedPanel;

    @Nullable
    private String worldId;
    private float saveTimer;

    private static final int SUPPORTED_JAVA = 21;
    private static boolean jvmWarned;

    private static void warnAboutJvm() {
        if (jvmWarned) {
            return;
        }
        jvmWarned = true;
        int feature = Runtime.version().feature();
        if (feature >= SUPPORTED_JAVA) {
            LOGGER.info("WebPanel: JVM Java {}, MCEF {} — совместимо", feature, SUPPORTED_JAVA);
        } else {
            LOGGER.warn("WebPanel: Java {} старше минимальной ({}) для нативного CEF", feature, SUPPORTED_JAVA);
        }
    }

    public WebPanelFeature() {
        super("WebPanel", "Браузерные панели в мире: сайты как окна на блоках",
                FeatureCategory.MISC, BindSetting.UNBOUND);
    }

    public static WebPanelFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(WebPanelFeature.class);
    }

    @Override
    protected void onEnable() {
        warnAboutJvm();
        WebPanelAwtGuard.install();
        WebPanelBrowserHooks.installAppHandler();
        try {
            MCEFApi.initialize();
        } catch (Throwable t) {
            LOGGER.error("MCEF init failed", t);
        }
        this.lastRenderNanos = System.nanoTime();
    }

    @Override
    protected void onDisable() {
        closeAll();
        try { WebPanelStream.clear(); } catch (Throwable ignored) {}
    }

    @EventTarget
    public void onShutdown(org.shizavisuals.event.events.lifecycle.ShutdownEvent event) {
        closeAll();
        try { WebPanelStream.shutdown(); } catch (Throwable ignored) {}
        try { WebPanelAwtGuard.shutdown(); } catch (Throwable ignored) {}
        try { org.shizavisuals.webpanel.WebPanelSync.clear(); } catch (Throwable ignored) {}
        try { org.shizavisuals.webpanel.WebPanelBlur.release(); } catch (Throwable ignored) {}
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.crossProgress = 0.0f;
            return;
        }
        long now = System.nanoTime();
        float delta = Math.clamp((now - this.lastRenderNanos) / 1.0E9f, 0.0f, 0.25f);
        this.lastRenderNanos = now;

        applySettings();
        syncWorldStorage(delta);
        WebPanelSync.update(delta, this.panels, this.friendPanels.getValue(), watchedPanel());

        if (this.panels.isEmpty()) {
            this.crossProgress = 0.0f;
            return;
        }

        WebPanelAwtGuard.rearm();

        updateHover();
        this.grab.setSnapEnabled(this.snapToWalls.getValue());
        this.grab.update(delta);

        float target = this.hovered != null ? 1.0f : 0.0f;
        float step = delta / WebPanelCrosshair.MORPH_TIME;
        this.crossProgress = target > this.crossProgress
                ? Math.min(target, this.crossProgress + step)
                : Math.max(target, this.crossProgress - step);

        for (WebPanelInstance panel : this.panels) {
            panel.animate(delta, panel == this.hovered && this.hoveredHit != null
                    ? this.hoveredHit.part() : null);
        }
        dropDeadPanels();
    }

    public void renderWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || this.panels.isEmpty()) {
            return;
        }
        Vec3 cameraPos = mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null
                ? mc.gameRenderer.mainCamera().position()
                : mc.player.getEyePosition();
        WebPanelRenderer.render(cameraPos, this.panels,
                RENDER_DISTANCE, delta(), this.hovered);
    }

    private float delta() {
        long now = System.nanoTime();
        return Math.clamp((now - this.lastRenderNanos) / 1.0E9f, 0.0f, 0.25f);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (shouldDrawCrosshair()) {
            WebPanelCrosshair.draw(this.crossProgress);
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (consumeMouse(event.getAction(), event.getButton(), event.getModifiers())) {
            event.cancel();
        }
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (event.getAction() != GLFW.GLFW_RELEASE
                && consumeKey(event.getAction(), event.getKey(), event.getScanCode(), event.getModifiers())) {
            event.cancel();
        }
    }

    @EventTarget
    public void onCharacterInput(CharacterInputEvent event) {
        if (consumeChar(event.getCodePoint())) {
            event.cancel();
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        closeAll();
    }

    private void applySettings() {
        WebPanelInstance.setBend(this.bend.getValue().floatValue() / 100.0f);
        WebPanelInstance.setQuality(
                this.quality.is(QUALITY_LOW) ? WebPanelInstance.Quality.LOW
                        : this.quality.is(QUALITY_MEDIUM) ? WebPanelInstance.Quality.MEDIUM
                        : this.quality.is(QUALITY_ULTRA) ? WebPanelInstance.Quality.ULTRA
                        : WebPanelInstance.Quality.HIGH);
    }

    @Nullable
    private WebPanelInstance watchedPanel() {
        Minecraft mc = Minecraft.getInstance();
        if (this.hovered != null && !this.hovered.isRemote() && !this.hovered.isClosing()) {
            return this.hovered;
        }
        WebPanelInstance best = null;
        double distance = Double.MAX_VALUE;
        Vec3 eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
        for (WebPanelInstance panel : this.panels) {
            if (panel.isRemote() || panel.isClosing()) {
                continue;
            }
            double d = panel.getCenter().distanceToSqr(eye);
            if (d < distance) {
                distance = d;
                best = panel;
            }
        }
        return best;
    }

    private void dropDeadPanels() {
        for (Iterator<WebPanelInstance> it = this.panels.iterator(); it.hasNext(); ) {
            WebPanelInstance panel = it.next();
            if (!panel.isDead()) {
                continue;
            }
            if (this.hovered == panel) {
                this.hovered = null;
                this.hoveredHit = null;
            }
            if (this.movePanel == panel) {
                forgetMouseMove();
            }
            if (this.typingPanel == panel) {
                this.typingPanel = null;
            }
            if (this.focusedPanel == panel) {
                this.focusedPanel = null;
            }
            panel.close();
            it.remove();
        }
    }

    private void syncWorldStorage(float delta) {
        String world = WebPanelStore.worldId();
        if (world.isEmpty()) {
            return;
        }
        if (!world.equals(this.worldId)) {
            this.worldId = world;
            this.saveTimer = 0.0f;
            for (WebPanelStore.Saved saved : WebPanelStore.load(world)) {
                if (this.panels.size() >= MAX_PANELS) {
                    break;
                }
                this.panels.add(new WebPanelInstance(saved.center(), saved.yaw(), saved.pitch(),
                        saved.width(), saved.height(), saved.url()));
            }
            return;
        }
        this.saveTimer += delta;
        if (this.saveTimer >= SAVE_INTERVAL) {
            this.saveTimer = 0.0f;
            WebPanelStore.save(world, this.panels);
        }
    }

    private void updateHover() {
        WebPanelInstance dragging = this.grab.getDragging();
        if (dragging != null) {
            this.hovered = dragging;
            this.hoveredHit = new WebPanelInstance.Hit(WebPanelInstance.Part.BAR, 0.0, 0.0, 0.0);
            return;
        }
        WebPanelInstance resizing = this.grab.getResizing();
        if (resizing != null) {
            this.hovered = resizing;
            this.hoveredHit = new WebPanelInstance.Hit(
                    WebPanelInstance.Part.values()[WebPanelInstance.Part.CORNER_BL.ordinal()
                            + this.grab.getCorner()], 0.0, 0.0, 0.0);
            return;
        }

        this.hovered = null;
        this.hoveredHit = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            syncMouseMove();
            syncFocus();
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);

        double bestT = Double.MAX_VALUE;
        for (WebPanelInstance panel : this.panels) {
            if (panel.isClosing()) {
                continue;
            }
            WebPanelInstance.Hit hit = panel.hitTest(eye, look, RENDER_DISTANCE);
            if (hit == null || hit.t() >= bestT) {
                continue;
            }
            int[] pixels = panel.toPixels(hit.u(), hit.v());
            bestT = hit.t();
            this.hovered = panel;
            this.hoveredHit = hit;
            this.hoverX = pixels[0];
            this.hoverY = pixels[1];
        }
        syncMouseMove();
        syncFocus();
    }

    private void syncMouseMove() {
        MCEFBrowser browser = pageBrowser();
        if (this.movePanel != null && this.movePanel != this.hovered) {
            MCEFBrowser previous = this.movePanel.getBrowser();
            if (previous != null) {
                previous.onMouseMoved(-1, -1);
            }
            forgetMouseMove();
        }
        if (browser == null) {
            return;
        }
        if (this.hovered == this.movePanel && this.hoverX == this.moveX && this.hoverY == this.moveY) {
            return;
        }
        browser.onMouseMoved(this.hoverX, this.hoverY);
        this.movePanel = this.hovered;
        this.moveX = this.hoverX;
        this.moveY = this.hoverY;
    }

    private void forgetMouseMove() {
        this.movePanel = null;
        this.moveX = Integer.MIN_VALUE;
        this.moveY = Integer.MIN_VALUE;
    }

    private void syncFocus() {
        WebPanelInstance wanted = this.hovered != null && !this.hovered.isRemote()
                ? this.hovered : null;
        if (this.focusedPanel == wanted) {
            return;
        }
        if (this.focusedPanel != null) {
            this.focusedPanel.setFocused(false);
        }
        if (wanted != null) {
            wanted.setFocused(true);
        }
        this.focusedPanel = wanted;
    }

    @Nullable
    private MCEFBrowser pageBrowser() {
        return this.hovered != null && this.hoveredHit != null
                && this.hoveredHit.part() == WebPanelInstance.Part.PAGE && !this.hovered.isRemote()
                ? this.hovered.getBrowser() : null;
    }

    @Nullable
    private MCEFBrowser keyBrowser() {
        if (this.grab.isActive()) {
            return null;
        }
        if (this.fullControl.getValue()) {
            return this.hovered != null && !this.hovered.isRemote()
                    ? this.hovered.getBrowser() : null;
        }
        return pageBrowser();
    }

    private void placePanel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        int alive = 0;
        for (WebPanelInstance panel : this.panels) {
            if (!panel.isClosing() && !panel.isRemote()) {
                alive++;
            }
        }
        for (WebPanelInstance panel : this.panels) {
            if (alive < MAX_PANELS) {
                break;
            }
            if (!panel.isClosing() && !panel.isRemote()) {
                panel.requestClose();
                alive--;
            }
        }

        double reach = SPAWN_DISTANCE;
        Vec3 eye = mc.player.getEyePosition();
        HitResult hit = mc.player.pick(reach, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            reach = Math.max(0.75, hit.getLocation().distanceTo(eye) - 0.15);
        }
        Vec3 center = eye.add(mc.player.getViewVector(1.0F).scale(reach));
        float yaw = mc.player.getYRot();
        float pitch = 0.0f;

        if (this.snapToWalls.getValue() && hit.getType() == HitResult.Type.BLOCK
                && hit instanceof BlockHitResult block) {
            Vec3 normal = block.getDirection().getUnitVec3();
            center = hit.getLocation().add(normal.scale(WebPanelGrab.SNAP_GAP));
            yaw = WebPanelPose.snapYaw(normal, mc.player.getYRot());
            pitch = WebPanelPose.snapPitch(normal);
        }

        this.panels.add(new WebPanelInstance(center, yaw, pitch, PANEL_WIDTH, PANEL_HEIGHT,
                WebPanelUrls.HOME));
    }

    private void closeAll() {
        if (this.worldId != null && !this.worldId.isEmpty()) {
            WebPanelStore.save(this.worldId, this.panels);
        }
        this.worldId = null;
        this.grab.end();
        for (WebPanelInstance panel : this.panels) {
            panel.close();
        }
        this.panels.clear();
        this.hovered = null;
        this.hoveredHit = null;
        this.typingPanel = null;
        this.focusedPanel = null;
        forgetMouseMove();
        WebPanelSync.clear();
        WebPanelBlur.release();
    }

    private boolean shouldDrawCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        return !this.panels.isEmpty() && mc.gui.screen() == null
                && mc.options.getCameraType().isFirstPerson();
    }

    @Nullable
    private static WebPanelFeature active() {
        return FeatureManager.INSTANCE.getEnabled(WebPanelFeature.class);
    }

    public static boolean consumeMouse(int action, int button, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        WebPanelFeature module = active();
        if (module == null || mc.player == null || mc.gui.screen() != null) {
            return false;
        }
        boolean press = action == GLFW.GLFW_PRESS;
        boolean release = action == GLFW.GLFW_RELEASE;

        if (press && module.placeKey.matchesMouse(button)) {
            module.placePanel();
            return false;
        }
        if (module.grab.isActive()) {
            if (release) {
                module.grab.end();
                return false;
            }
            return true;
        }

        WebPanelInstance panel = module.hovered;
        WebPanelInstance.Hit hit = module.hoveredHit;
        if (panel == null || hit == null) {
            return false;
        }

        if (hit.part() != WebPanelInstance.Part.PAGE) {
            if (press && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                switch (hit.part()) {
                    case CLOSE -> panel.requestClose();
                    case BAR -> module.grab.beginDrag(panel);
                    default -> module.grab.beginResize(panel,
                            hit.part().ordinal() - WebPanelInstance.Part.CORNER_BL.ordinal());
                }
            }
            return !release;
        }
        if (panel.isRemote()) {
            return !release;
        }

        MCEFBrowser browser = panel.getBrowser();
        if (browser != null) {
            MouseButtonEvent click = new MouseButtonEvent(module.hoverX, module.hoverY,
                    new MouseButtonInfo(button, modifiers));
            if (press) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    module.typingPanel = browser.getCursorType() == CursorTypes.IBEAM ? panel : null;
                }
                browser.setFocus(true);
                browser.onMouseClicked(click, false);
            } else if (release) {
                browser.onMouseReleased(click);
            }
        }
        return !release;
    }

    public static boolean consumeScroll(double verticalAmount) {
        Minecraft mc = Minecraft.getInstance();
        WebPanelFeature module = active();
        if (module == null || mc.gui.screen() != null) {
            return false;
        }
        if (module.grab.getDragging() != null) {
            module.grab.scroll(verticalAmount);
            return true;
        }
        MCEFBrowser browser = module.pageBrowser();
        if (browser == null) {
            return false;
        }
        browser.onMouseScrolled(module.hoverX, module.hoverY, verticalAmount);
        return true;
    }

    public static boolean blocksClientBind(int code) {
        Minecraft mc = Minecraft.getInstance();
        WebPanelFeature module = active();
        if (module == null || mc.gui.screen() != null) {
            return false;
        }
        if (module.hovered == null && !module.grab.isActive()) {
            return false;
        }
        return !module.placeKey.matches(code);
    }

    public static boolean consumeKey(int action, int key, int scanCode, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        WebPanelFeature module = active();
        if (module == null || mc.player == null || mc.level == null || mc.gui.screen() != null) {
            return false;
        }
        if (module.grab.getDragging() != null && key == LEVEL_KEY) {
            if (action == GLFW.GLFW_PRESS) {
                module.grab.toggleLevel();
            }
            return true;
        }
        if (action == GLFW.GLFW_PRESS && module.placeKey.matches(key)) {
            module.placePanel();
            return false;
        }

        MCEFBrowser browser = module.keyBrowser();
        if (browser == null) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            module.typingPanel = null;
            return false;
        }
        if (module.placeKey.matches(key)) {
            return false;
        }
        boolean typing = module.fullControl.getValue()
                || module.typingPanel == module.hovered;
        if (action == GLFW.GLFW_RELEASE) {
            if (!WebPanelKeyShim.handles(key)) {
                browser.onKeyReleased(new KeyEvent(key, scanCode, modifiers));
            }
            return typing;
        }
        if (WebPanelKeyShim.handle(browser, key, modifiers)) {
            return typing;
        }
        browser.onKeyPressed(new KeyEvent(key, scanCode, modifiers));
        if (key == GLFW.GLFW_KEY_TAB) {
            browser.onCharTyped(new CharacterEvent('\t'));
        }
        return typing;
    }

    public static boolean consumeChar(int codePoint) {
        Minecraft mc = Minecraft.getInstance();
        WebPanelFeature module = active();
        if (module == null || mc.gui.screen() != null) {
            return false;
        }
        MCEFBrowser browser = module.keyBrowser();
        if (browser == null) {
            return false;
        }
        browser.onCharTyped(new CharacterEvent(codePoint));
        return module.fullControl.getValue() || module.typingPanel == module.hovered;
    }

    public static boolean shouldSuppressCrosshair() {
        WebPanelFeature module = active();
        return module != null && module.shouldDrawCrosshair();
    }
}
