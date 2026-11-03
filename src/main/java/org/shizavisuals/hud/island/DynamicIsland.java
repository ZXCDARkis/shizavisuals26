package org.shizavisuals.hud.island;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.input.MouseInputEvent;
import org.shizavisuals.hud.HudElement;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.menu.core.MenuOverlay;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DynamicIsland extends HudElement {

    public static final float BASE_WIDTH = 48.0F;
    public static final float BASE_HEIGHT = 15.0F;

    private static final float SCALE_MULTIPLIER = 1.5F;
    private static final String BG_KEY = "hud.island.bg";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static String cycleBackground(String current) {
        return switch (current) {
            case "gradient" -> "minimal";
            default -> "gradient";
        };
    }
    private static final Identifier AIRPLANE = Identifier.parse("shizavisuals:icons/hud/island/airplane.png");

    private final IslandSize size = new IslandSize(BASE_WIDTH, BASE_HEIGHT);
    private boolean extended;
    private final IslandAnimation extendingAnim = new IslandAnimation(200L, 0.0F, IslandEasing.LINEAR);
    private final IslandAnimation widthAnim = new IslandAnimation(500L, BASE_WIDTH, IslandEasing.BAKEK_SIZE);
    private final IslandAnimation heightAnim = new IslandAnimation(500L, BASE_HEIGHT, IslandEasing.BAKEK_SIZE);
    private final IslandAnimation showPing = new IslandAnimation(500L, 0.0F, IslandEasing.BAKEK);
    private final IslandColorAnimation backgroundColor =
            new IslandColorAnimation(300L, ColorUtil.BLACK, IslandEasing.FIGMA_EASE_IN_OUT);
    private final IslandColorAnimation adaptColor =
            new IslandColorAnimation(300L, ColorUtil.WHITE, IslandEasing.LINEAR);
    private long cachedTimeSecond = -1L;
    private String cachedTime = "--:--";
    private boolean useDark;

    private final PingPongStatus pingPong;
    private int secretClicks;
    private long lastClickTime;
    private float scale = 1.0F;

    private boolean settingsOpen;
    private int menuPressIndex = -1;
    private boolean menuDragged;
    private float menuPressY;
    private final IslandPopup popup;

    private final List<IslandStatus> statuses = new ArrayList<>();

    public DynamicIsland() {
        super("island", "Dynamic Island");
        this.statuses.add(new NotificationStatus(this));
        this.statuses.add(new ObjectStatus(this));
        this.statuses.add(new MineStatus(this));
        this.statuses.add(new PVPStatus(this));
        this.statuses.add(new EventStatus(this));
        this.statuses.add(new MusicStatus(this));
        DefaultStatus defaultStatus = new DefaultStatus(this);
        this.statuses.add(defaultStatus);

        for (IslandStatus status : this.statuses) {
            status.setEnabled(MenuConfigStore.getBoolean(statusKey(status), true));
        }
        loadStatusOrder();

        this.pingPong = new PingPongStatus(this);
        this.popup = new IslandPopup(this);
        EventManager.subscribe(this);
    }

    private static String statusKey(IslandStatus status) {
        return "hud.island.status." + status.getName();
    }

    private void loadStatusOrder() {
        com.google.gson.JsonArray arr = MenuConfigStore.getArray("hud.island.order");
        if (arr == null || arr.isEmpty()) {
            return;
        }
        List<IslandStatus> ordered = new ArrayList<>();
        for (com.google.gson.JsonElement element : arr) {
            String name = element.getAsString();
            for (IslandStatus status : this.statuses) {
                if (status.getName().equals(name) && !ordered.contains(status)) {
                    ordered.add(status);
                    break;
                }
            }
        }
        for (IslandStatus status : this.statuses) {
            if (!ordered.contains(status)) {
                ordered.add(status);
            }
        }
        this.statuses.clear();
        this.statuses.addAll(ordered);
    }

    public void saveStatusOrder() {
        MenuConfigStore.save(data -> {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (IslandStatus status : this.statuses) {
                arr.add(status.getName());
            }
            data.add("hud.island.order", arr);
        });
    }

    public void reorderStatus(int from, int to) {
        if (from < 0 || to < 0 || from >= this.statuses.size() || to >= this.statuses.size() || from == to) {
            return;
        }
        IslandStatus status = this.statuses.remove(from);
        this.statuses.add(to, status);
        saveStatusOrder();
    }

    public List<IslandStatus> allStatuses() {
        return this.statuses;
    }

    public void saveStatuses() {
        MenuConfigStore.save(data -> {
            for (IslandStatus status : this.statuses) {
                data.addProperty(statusKey(status), status.isEnabled());
            }
        });
    }

    @Override
    protected boolean draggable() {
        return false;
    }

    @Override
    protected boolean centerHorizontally() {
        return true;
    }

    @Override
    protected boolean preservePositionOnContentResize() {
        return true;
    }

    @Override
    protected float defaultY(float unit) {
        return 7.0F * unit * SCALE_MULTIPLIER;
    }

    public float scale() {
        return this.scale;
    }

    public IslandSize size() {
        return this.size;
    }

    public Minecraft mc() {
        return MinecraftContext.mc;
    }

    public boolean isExtended() {
        return this.extended;
    }

    public float extendingValue() {
        return this.extendingAnim.getValue();
    }

    public void onGameClosed() {
        this.extended = false;
    }

    @Override
    protected void layout(Minecraft mc, float unit) {
        this.scale = unit * SCALE_MULTIPLIER;
        this.width = this.size.width * this.scale;
        this.height = this.size.height * this.scale;
    }

    @Override
    protected void draw(Minecraft mc, float unit) {
        float s = this.scale;
        boolean gameActive = pingPong.canShow();

        if (gameActive) {
            this.pingPong.renderHud(mc);
        }
        float x = this.x;
        float y = this.y;

        float targetRadius = this.extended ? 20.0F : (this.size.height / 2.0F);
        float widthRatio = Math.min(1.0F, (this.size.width - this.size.height) / 60.0F);
        float minRadius = this.size.height / 2.0F - 1.0F;
        float currentRadius = (this.size.height / 2.0F) * (1.0F - widthRatio) + minRadius * widthRatio;
        currentRadius += (targetRadius - currentRadius) * this.extendingAnim.getValue();

        long now = System.currentTimeMillis();
        String time = currentTime(now);
        // The old screen-pixel probe used glReadPixels and crashed the JVM on
        // the extract phase (no GL context there under the Vulkan backend).
        // The island now just keeps its static adapt switchover.
        this.adaptColor.update(this.useDark ? ColorUtil.BLACK : ColorUtil.WHITE);
        int elmtColorBase = this.adaptColor.getColor();
        int elmtColor = ColorUtil.multiplyAlpha(elmtColorBase, 1.0F - this.extendingAnim.getValue());

        if (mc.player != null && !gameActive) {
            MsdfFont measureFont = UiFonts.getFontForText(time, UiFontStyle.MEDIUM.weight());
            float size9 = 9.0F * s;
            Render2DUtil.text(x - measureFont.measureWidth(time, size9, spacing(size9)) - 4.0F * s,
                            measureFont.centeredTextY(y + 7.5F * s, 7.0F * s), 7.0F * s, time)
                    .font(measureFont)
                    .color(elmtColor)
                    .draw();
        }

        drawRightIndicator(mc, gameActive, x, y, s, elmtColor);

        IslandStatus status = active();
        this.backgroundColor.update(status.getColor());

        String backgroundMode = MenuConfigStore.getString(BG_KEY, "minimal");
        if (gameActive) {
            drawFrostedBar(x, y, s, currentRadius);
        } else if (backgroundMode.equals("gradient")) {
            int accentDark = ColorUtil.lerp(Theme.getAccent(), ColorUtil.BLACK, 0.25F);
            int fade = Math.round(216.75F / 255.0F * 255.0F);
            Render2DUtil.rect(x, y, this.width, this.height)
                    .radius(currentRadius * s)
                    .color(
                            ColorUtil.withAlpha(accentDark, fade),
                            ColorUtil.withAlpha(ColorUtil.BLACK, fade),
                            ColorUtil.withAlpha(ColorUtil.BLACK, fade),
                            ColorUtil.withAlpha(accentDark, fade))
                    .draw();
        } else {
            Render2DUtil.rect(x, y, this.width, this.height)
                    .radius(currentRadius * s)
                    .color(ColorUtil.multiplyAlpha(this.backgroundColor.getColor(), 216.75F / 255.0F))
                    .draw();
        }

        for (IslandStatus value : this.statuses) {
            value.getAnimation().update(status == value ? 1.0F : 0.0F);
        }
        if (!gameActive) {
            Render2DUtil.pushScissor(x, y, this.width, this.height);
            try {
                if (status.getAnimation().getValue() == 1.0F) {
                    status.draw(1.0F);
                } else {
                    for (IslandStatus value : this.statuses) {
                        if (value.getAnimation().getValue() > 0.0F) {
                            value.draw(value.getAnimation().getValue());
                        }
                    }
                }
            } finally {
                Render2DUtil.popScissor();
            }
        } else {
            drawGameBadge(x, y, s);
        }

        if (gameActive) {
            this.extended = false;
            this.widthAnim.update(BASE_WIDTH);
            this.heightAnim.update(BASE_HEIGHT);
        } else {
            this.widthAnim.update(status.getSize().width);
            this.heightAnim.update(status.getSize().height);
        }
        this.size.width = this.widthAnim.getValue();
        this.size.height = this.heightAnim.getValue();
        this.extendingAnim.update(this.extended ? 1.0F : 0.0F);
        this.width = this.size.width * s;
        this.height = this.size.height * s;

        if (!gameActive) {
            if (!(status instanceof ExtandableStatus)) {
                this.extended = false;
            }
            if (!(mc.gui.screen() instanceof ChatScreen)
                    && !MenuOverlay.isOpen()
                    && mc.player != null && !this.isDragging() && !this.settingsOpen) {
                this.extended = false;
            }

            if (this.settingsOpen || this.popup.isAnimating()) {
                double mx = mouseX(mc);
                double my = mouseY(mc);
                this.popup.render(mx, my, x + this.width / 2.0F,
                        y + this.height + 6.0F * s, s);
            }
        }
    }

    private void drawFrostedBar(float x, float y, float s, float currentRadius) {
        float extending = this.extendingAnim.getValue();
        Render2DUtil.rect(x - 1.0F * s, y - 1.0F * s, this.width + 2.0F * s, this.height + 2.0F * s)
                .radius(currentRadius * s)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.BACKGROUND_PRIMARY_50, 0.9F))
                .blur((2.0F + 5.0F * extending) * s)
                .draw();
    }

    private void drawRightIndicator(Minecraft mc, boolean gameActive, float x, float y, float s, int elmtColor) {
        if (mc.player == null || gameActive) {
            return;
        }
        PlayerInfo info = mc.getConnection() != null
                ? mc.getConnection().getPlayerInfo(mc.player.getUUID())
                : null;
        if (mc.hasSingleplayerServer() || info == null) {
            Render2DUtil.texture(x + this.width + 8.0F * s, y + 3.5F * s, 8.0F * s, 8.0F * s, AIRPLANE)
                    .color(elmtColor)
                    .draw();
            return;
        }
        double mouseX = mouseX(mc);
        double mouseY = mouseY(mc);
        float indicatorX = x + this.width + (4.0F + 4.0F * this.showPing.getValue()) * s;
        this.showPing.update(hovered(indicatorX, y + 5.0F * s, 12.8F * s, 7.0F * s, mouseX, mouseY) ? 1.0F : 0.0F);

        int ping = info.getLatency();
        int[] pings = {450, 300, 150, 75};
        MsdfFont font = UiFonts.getFontForText(ping + " ms", UiFontStyle.MEDIUM.weight());
        Render2DUtil.text(indicatorX, y + 5.0F * s, 7.0F * s, ping + " ms")
                .font(font)
                .color(ColorUtil.multiplyAlpha(elmtColor, this.showPing.getValue()))
                .draw();
        for (int i = 0; i < 4; i++) {
            Render2DUtil.rect(
                            x + this.width + (9.0F + i * 2.7F + 4.0F * this.showPing.getValue()) * s,
                            y + (8.0F - i) * s,
                            2.0F * s, (3.0F + i) * s)
                    .radius(0.1F * s)
                    .color(ColorUtil.multiplyAlpha(elmtColor, 0.2F * (1.0F - this.showPing.getValue())))
                    .draw();
        }
        for (int i = 0; i < 4; i++) {
            if (ping >= pings[i]) {
                continue;
            }
            Render2DUtil.rect(
                            x + this.width + (9.0F + i * 2.7F + 4.0F * this.showPing.getValue()) * s,
                            y + (8.0F - i) * s,
                            2.0F * s, (3.0F + i) * s)
                    .radius(0.1F * s)
                    .color(ColorUtil.multiplyAlpha(elmtColor, 1.0F - this.showPing.getValue()))
                    .draw();
        }
    }

    private void drawGameBadge(float x, float y, float s) {
        String name = DefaultStatus.CLIENT_NAME;
        MsdfFont font = UiFonts.getFontForText(name, UiFontStyle.MEDIUM.weight());
        float size7 = 7.0F * s;
        float textW = font.measureWidth(name, size7, spacing(size7));
        float totalW = 11.0F * s + textW;
        float startX = x + this.width / 2.0F - totalW / 2.0F;

        int accent = Theme.getAccent();
        int light = ColorUtil.lerp(accent, ColorUtil.WHITE, 0.5F);
        int mid = ColorUtil.lerp(accent, ColorUtil.WHITE, 0.25F);
        Render2DUtil.rect(startX, y + 4.0F * s, 7.0F * s, 7.0F * s)
                .radius(3.0F * s)
                .color(accent, light, light, mid)
                .draw();
        Render2DUtil.text(startX + 11.0F * s, y + 5.0F * s, size7, name)
                .font(font)
                .style(UiFontStyle.MEDIUM)
                .color(Theme.Colors.TEXT_TEXT)
                .draw();
    }

    public boolean handleClick(float mouseX, float mouseY, int button) {
        float x = this.x;
        float y = this.y;

        boolean hovered = hovered(x, y, this.width, this.height, mouseX, mouseY);

        if (hovered && button == 0 && !this.extended) {
            long now = System.currentTimeMillis();
            if (now - this.lastClickTime > 1000L) {
                this.secretClicks = 0;
            }
            this.lastClickTime = now;
            this.secretClicks++;

            if (this.secretClicks >= 5) {
                this.secretClicks = 0;
                if (!pingPong.canShow()) {
                    pingPong.open();
                }
                return true;
            }
        }

        if (pingPong.canShow()) {
            return true;
        }

        if (this.extended) {
            if (!hovered) {
                this.extended = false;
            } else {
                this.active().click(mouseX, mouseY, button);
            }
            return true;
        }
        if (hovered && this.active() instanceof ExtandableStatus) {
            this.extended = true;
            return true;
        }
        return false;
    }

    @EventTarget
    public void onKeyInput(org.shizavisuals.event.events.input.KeyboardInputEvent event) {
        Minecraft mc = MinecraftContext.mc;
        if (mc.gui.screen() != null || mc.player == null) {
            closeMenu();
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_Y && event.getAction() == GLFW.GLFW_PRESS
                && !pingPong.canShow()) {
            event.cancel();
            this.settingsOpen = !this.settingsOpen;
            this.popup.setOpen(this.settingsOpen);
            this.menuPressIndex = -1;
            this.popup.setPressedRow(-1, 0.0F);
            return;
        }
        if (this.settingsOpen && event.getKey() == GLFW.GLFW_KEY_ESCAPE
                && event.getAction() == GLFW.GLFW_PRESS) {
            event.cancel();
            closeMenu();
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        Minecraft mc = MinecraftContext.mc;
        boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
        if ((mc.gui.screen() != null && !chatOpen) || mc.player == null) {
            return;
        }

        if (this.settingsOpen) {
            float mx = (float) mouseX(mc);
            float my = (float) mouseY(mc);
            boolean inMenu = this.popup.isInside(mx, my);
            float pad = Math.max(14.0F, this.height * 1.25F);
            boolean overIsland = hovered(this.x - pad, this.y - pad,
                    this.width + pad * 2.0F, this.height + pad * 2.0F, mx, my);

            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && event.getAction() == GLFW.GLFW_PRESS) {
                if (inMenu) {
                    int row = this.popup.rowAt(my);
                    if (row >= 0) {
                        this.menuPressIndex = row;
                        this.menuPressY = my;
                        this.menuDragged = false;
                        this.popup.setPressedRow(row, my);
                    } else if (this.popup.backgroundRowAt(my)) {
                        String next = cycleBackground(MenuConfigStore.getString(BG_KEY, "minimal"));
                        MenuConfigStore.save(data -> data.addProperty(BG_KEY, next));
                    }
                    event.cancel();
                    return;
                }
                if (!chatOpen) {
                    closeMenu();
                    event.cancel();
                }
                return;
            }
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && event.getAction() == GLFW.GLFW_RELEASE) {
                if (this.menuPressIndex >= 0) {
                    List<IslandStatus> rows = toggleable();
                    int target = this.popup.rowAt(my);
                    if (Math.abs(my - this.menuPressY) > this.popup.rowHeightScreen() * 0.6F) {
                        this.menuDragged = true;
                        if (target >= 0 && target != this.menuPressIndex) {
                            reorderStatus(this.menuPressIndex, target);
                            // Update menuPressIndex to follow the dragged row after reorder.
                            if (target < this.menuPressIndex) {
                                this.menuPressIndex = target;
                            } else {
                                this.menuPressIndex = target - 1;
                            }
                            this.menuPressY = my;
                        }
                    } else if (this.menuPressIndex < rows.size()) {
                        IslandStatus status = rows.get(this.menuPressIndex);
                        status.setEnabled(!status.isEnabled());
                        saveStatuses();
                    }
                    saveStatusOrder();
                    this.menuPressIndex = -1;
                    this.menuDragged = false;
                    this.popup.setPressedRow(-1, 0.0F);
                    event.cancel();
                }
                return;
            }
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    && event.getAction() == GLFW.GLFW_PRESS) {
                closeMenu();
                if (inMenu || overIsland || !chatOpen) {
                    event.cancel();
                }
                return;
            }
            return;
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getAction() == GLFW.GLFW_PRESS) {
            if (handleClick((float) mouseX(mc), (float) mouseY(mc), event.getButton())) {
                event.cancel();
            }
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && event.getAction() == GLFW.GLFW_PRESS
                && !pingPong.canShow()) {
            float mx = (float) mouseX(mc);
            float my = (float) mouseY(mc);
            float pad = Math.max(14.0F, this.height * 1.25F);
            if (hovered(this.x - pad, this.y - pad, this.width + pad * 2.0F, this.height + pad * 2.0F, mx, my)) {
                event.cancel();
                this.settingsOpen = true;
                this.popup.setOpen(true);
                this.menuPressIndex = -1;
                this.popup.setPressedRow(-1, 0.0F);
            }
        }
    }

    private void closeMenu() {
        this.settingsOpen = false;
        this.popup.setOpen(false);
        this.menuPressIndex = -1;
        this.menuDragged = false;
        this.popup.setPressedRow(-1, 0.0F);
    }

    public List<IslandStatus> toggleable() {
        List<IslandStatus> list = new ArrayList<>();
        for (IslandStatus status : this.statuses) {
            if (status.isToggleable()) {
                list.add(status);
            }
        }
        return list;
    }

    public IslandStatus active() {
        for (IslandStatus status : this.statuses) {
            if (status.isEnabled() && status.canShow()) {
                return status;
            }
        }
        return this.statuses.getLast();
    }

    public List<IslandStatus> statuses() {
        List<IslandStatus> visible = new ArrayList<>();
        for (IslandStatus status : this.statuses) {
            if (status.isEnabled() && status.canShow()) {
                visible.add(status);
            }
        }
        return visible.reversed();
    }

    private static float spacing(float size) {
        return size * UiFontStyle.MEDIUM.letterSpacingEm();
    }

    private String currentTime(long now) {
        long second = Math.floorDiv(now, 1000L);
        if (second != this.cachedTimeSecond) {
            this.cachedTime = LocalTime.now().format(TIME_FORMAT);
            this.cachedTimeSecond = second;
        }
        return this.cachedTime;
    }

    private static double mouseX(Minecraft mc) {
        return mc.mouseHandler.getScaledXPos(mc.getWindow());
    }

    private static double mouseY(Minecraft mc) {
        return mc.mouseHandler.getScaledYPos(mc.getWindow());
    }

    private static boolean hovered(float x, float y, float w, float h, double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
