package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.input.MouseInputEvent;
import org.shizavisuals.event.events.render.Render2DEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.hud.ArmorHudElement;
import org.shizavisuals.hud.HotbarElement;
import org.shizavisuals.hud.HudElement;
import org.shizavisuals.hud.KeybindsElement;
import org.shizavisuals.hud.PotionsElement;
import org.shizavisuals.hud.TargetElement;
import org.shizavisuals.hud.WatermarkElement;
import org.shizavisuals.hud.island.DynamicIsland;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class HudFeature extends Feature {

    private static final float HUD_SCALE = 1.4F;
    private static final String SIZE_MIGRATION_KEY = "hudScale100Migrated";
    private static final double LEGACY_DEFAULT_SIZE = 70.0;
    private static final Identifier RESET_ICON = Identifier.parse("shizavisuals:textures/menu/icons/refresh_ccw.svg");
    private static final int RESET_DIVIDER_COLOR = ColorUtil.rgba(255, 255, 255, 25);
    private static final String RESET_LABEL = "Сбросить расположение";
    private static final float SNAP_MARGIN = 12.0F;
    private static final float SNAP_GAP = 6.0F;
    private static final float SNAP_THRESHOLD = 8.0F;

    private static final String WATERMARK = "Watermark";
    private static final String COORDINATES = "Coordinates";
    private static final String KEYBINDS = "Keybinds";
    private static final String POTIONS = "Potions";
    private static final String TARGET = "Target";
    private static final String HOTBAR = "Hotbar";
    private static final String ARMOR_HUD = "ArmorHud";
    private static final String DYNAMIC_ISLAND = "Dynamic Island";

    public final MultiSelectSetting elements = register(new MultiSelectSetting(
            "Elements",
            List.of(WATERMARK, COORDINATES, KEYBINDS, POTIONS, TARGET, HOTBAR, ARMOR_HUD, DYNAMIC_ISLAND),
            WATERMARK, COORDINATES, KEYBINDS, POTIONS, TARGET, HOTBAR, ARMOR_HUD, DYNAMIC_ISLAND
    ));
    public final NumberSetting size = register(new NumberSetting("Size", 100.0, 50.0, 200.0, 1.0, "%"));

    private static HudFeature instance;
    private final WatermarkElement watermarkElement = new WatermarkElement();
    private final KeybindsElement keybindsElement = new KeybindsElement();
    private final PotionsElement potionsElement = new PotionsElement();
    private final TargetElement targetElement = new TargetElement();
    private final HotbarElement hotbarElement = new HotbarElement();
    private final ArmorHudElement armorHudElement = new ArmorHudElement();
    private final DynamicIsland dynamicIsland = new DynamicIsland();
    private final List<HudElement> allElements = List.of(
            this.watermarkElement,
            this.keybindsElement,
            this.potionsElement,
            this.targetElement,
            this.hotbarElement,
            this.armorHudElement,
            this.dynamicIsland
    );
    private HudElement draggedElement;
    private boolean sizeMigrationChecked;
    private float resetButtonX;
    private float resetButtonY;
    private float resetButtonWidth;
    private float resetButtonHeight;
    private float activeGuideX = Float.NaN;
    private float activeGuideY = Float.NaN;

    private record SnapCandidate(float position, float guide) {
    }

    public HudFeature() {
        super("HUD", "HUD customization settings", FeatureCategory.VISUAL, BindSetting.UNBOUND);
        instance = this;
    }

    public static boolean customHotbarActive() {
        return instance != null && instance.isEnabled() && instance.elements.isSelected(HOTBAR);
    }

    public static float islandBossShift() {
        return 0.0F;
    }

    public static float hotbarDecorationOffset(Minecraft mc) {
        if (!customHotbarActive() || mc.player == null || mc.player.isSpectator()) {
            return 0.0F;
        }
        return instance.hotbarElement.decorationOffset(mc);
    }

    public static float hotbarDecorationScale(Minecraft mc) {
        if (!customHotbarActive() || mc.player == null || mc.player.isSpectator()) {
            return 1.0F;
        }
        return (float) (instance.size.getValue() / 100.0);
    }

    public static int hotbarStatsHalfWidth(Minecraft mc, int vanillaHalfWidth) {
        if (!customHotbarActive() || mc.player == null || mc.player.isSpectator()) {
            return vanillaHalfWidth;
        }
        float halfWidth = instance.hotbarElement.statsHalfWidth();
        float scale = hotbarDecorationScale(mc);
        return halfWidth > 0.5F && scale > 0.001F
                ? Math.round(halfWidth / scale)
                : vanillaHalfWidth;
    }

    @EventTarget(priority = -100)
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = event.getClient();
        if (mc == null || mc.player == null) {
            return;
        }

        migrateLegacySize();

        float unit = (float) (HUD_SCALE / mc.getWindow().getGuiScale() * this.size.getValue() / 100.0);
        List<HudElement> visible = visibleElements();
        boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
        boolean snapMode = chatOpen && controlDown(mc);

        if (this.draggedElement != null) {
            if (chatOpen) {
                this.draggedElement.dragTo(mouseX(mc), mouseY(mc));
                if (snapMode) {
                    applySnap(mc, visible);
                } else {
                    clearSnapGuides();
                }
            } else {
                stopDrag(mc);
            }
        } else {
            clearSnapGuides();
        }

        if (snapMode) {
            drawSnapGrid(mc);
        }
        for (HudElement element : visible) {
            element.render(mc, unit);
        }
        if (snapMode) {
            drawResetButton(mc, unit, visible);
        } else {
            this.resetButtonWidth = 0.0F;
            this.resetButtonHeight = 0.0F;
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        Minecraft mc = MinecraftContext.mc;
        if (mc == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (this.draggedElement != null) {
                stopDrag(mc);
                event.cancel();
            }
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS || !(mc.gui.screen() instanceof ChatScreen)) {
            return;
        }

        float mouseX = mouseX(mc);
        float mouseY = mouseY(mc);
        if (controlDown(mc) && inside(mouseX, mouseY,
                this.resetButtonX,
                this.resetButtonY,
                this.resetButtonWidth,
                this.resetButtonHeight)) {
            resetPositions();
            event.cancel();
            return;
        }
        if (inside(mouseX, mouseY,
                mc.getWindow().getGuiScaledWidth() - 150.0F,
                mc.getWindow().getGuiScaledHeight() - 40.0F,
                146.0F,
                20.0F)) {
            return;
        }
        for (HudElement element : visibleElements()) {
            if (element.startDrag(mouseX, mouseY)) {
                this.draggedElement = element;
                event.cancel();
                return;
            }
        }
    }

    @Override
    protected void onDisable() {
        if (this.draggedElement != null && MinecraftContext.mc != null) {
            stopDrag(MinecraftContext.mc);
        }
    }

    public void resetPositions() {
        this.draggedElement = null;
        clearSnapGuides();
        MenuConfigStore.resetHudPositions();
        this.allElements.forEach(HudElement::resetPosition);
    }

    private void applySnap(Minecraft mc, List<HudElement> visible) {
        HudElement dragged = this.draggedElement;
        if (dragged == null) {
            clearSnapGuides();
            return;
        }

        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();
        float maxX = Math.max(0.0F, screenWidth - dragged.width());
        float maxY = Math.max(0.0F, screenHeight - dragged.height());
        List<SnapCandidate> xCandidates = new ArrayList<>(20);
        List<SnapCandidate> yCandidates = new ArrayList<>(20);

        addCandidate(xCandidates, Math.min(SNAP_MARGIN, maxX), SNAP_MARGIN, maxX);
        addCandidate(xCandidates, maxX / 2.0F, screenWidth / 2.0F, maxX);
        addCandidate(xCandidates, Math.max(0.0F, maxX - SNAP_MARGIN), screenWidth - SNAP_MARGIN, maxX);
        addCandidate(yCandidates, Math.min(SNAP_MARGIN, maxY), SNAP_MARGIN, maxY);
        addCandidate(yCandidates, maxY / 2.0F, screenHeight / 2.0F, maxY);
        addCandidate(yCandidates, Math.max(0.0F, maxY - SNAP_MARGIN), screenHeight - SNAP_MARGIN, maxY);

        for (HudElement other : visible) {
            if (other == dragged || other.width() <= 0.5F || other.height() <= 0.5F) {
                continue;
            }

            float otherRight = other.x() + other.width();
            float otherBottom = other.y() + other.height();
            float otherCenterX = other.x() + other.width() / 2.0F;
            float otherCenterY = other.y() + other.height() / 2.0F;

            addCandidate(xCandidates, other.x(), other.x(), maxX);
            addCandidate(xCandidates, otherRight - dragged.width(), otherRight, maxX);
            addCandidate(xCandidates, otherCenterX - dragged.width() / 2.0F, otherCenterX, maxX);
            addCandidate(xCandidates, other.x() - dragged.width() - SNAP_GAP, other.x() - SNAP_GAP / 2.0F, maxX);
            addCandidate(xCandidates, otherRight + SNAP_GAP, otherRight + SNAP_GAP / 2.0F, maxX);

            addCandidate(yCandidates, other.y(), other.y(), maxY);
            addCandidate(yCandidates, otherBottom - dragged.height(), otherBottom, maxY);
            addCandidate(yCandidates, otherCenterY - dragged.height() / 2.0F, otherCenterY, maxY);
            addCandidate(yCandidates, other.y() - dragged.height() - SNAP_GAP, other.y() - SNAP_GAP / 2.0F, maxY);
            addCandidate(yCandidates, otherBottom + SNAP_GAP, otherBottom + SNAP_GAP / 2.0F, maxY);
        }

        float x = Math.clamp(dragged.x(), 0.0F, maxX);
        float y = Math.clamp(dragged.y(), 0.0F, maxY);
        this.activeGuideX = Float.NaN;
        this.activeGuideY = Float.NaN;

        if (!dragged.isHorizontallyCentered()) {
            SnapCandidate snappedX = nearestCandidate(x, xCandidates);
            if (snappedX != null) {
                x = snappedX.position();
                this.activeGuideX = snappedX.guide();
            }
        }
        SnapCandidate snappedY = nearestCandidate(y, yCandidates);
        if (snappedY != null) {
            y = snappedY.position();
            this.activeGuideY = snappedY.guide();
        }
        dragged.snapTo(x, y);
    }

    private static void addCandidate(List<SnapCandidate> candidates, float position, float guide, float maximum) {
        if (Float.isFinite(position) && position >= 0.0F && position <= maximum) {
            candidates.add(new SnapCandidate(position, guide));
        }
    }

    private static SnapCandidate nearestCandidate(float position, List<SnapCandidate> candidates) {
        SnapCandidate nearest = null;
        float nearestDistance = SNAP_THRESHOLD;
        for (SnapCandidate candidate : candidates) {
            float distance = Math.abs(position - candidate.position());
            if (distance <= nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void drawSnapGrid(Minecraft mc) {
        float width = mc.getWindow().getGuiScaledWidth();
        float height = mc.getWindow().getGuiScaledHeight();
        float line = (float) Math.max(0.5, 1.0 / Math.max(1.0, mc.getWindow().getGuiScale()));
        int thirdsColor = ColorUtil.rgba(190, 195, 205, 36);
        int centerColor = ColorUtil.rgba(205, 209, 216, 58);
        int cornerColor = ColorUtil.rgba(210, 214, 220, 78);
        float left = SNAP_MARGIN;
        float top = SNAP_MARGIN;
        float right = width - SNAP_MARGIN;
        float bottom = height - SNAP_MARGIN;
        float cornerLength = 10.0F;

        drawDashedVertical(width / 3.0F, top, bottom, 1.5F, 6.0F, line, thirdsColor);
        drawDashedVertical(width * 2.0F / 3.0F, top, bottom, 1.5F, 6.0F, line, thirdsColor);
        drawDashedHorizontal(height / 3.0F, left, right, 1.5F, 6.0F, line, thirdsColor);
        drawDashedHorizontal(height * 2.0F / 3.0F, left, right, 1.5F, 6.0F, line, thirdsColor);
        drawDashedVertical(width / 2.0F, top, bottom, 4.0F, 6.0F, line, centerColor);
        drawDashedHorizontal(height / 2.0F, left, right, 4.0F, 6.0F, line, centerColor);

        Render2DUtil.rect(left, top, cornerLength, line).color(cornerColor).draw();
        Render2DUtil.rect(left, top, line, cornerLength).color(cornerColor).draw();
        Render2DUtil.rect(right - cornerLength, top, cornerLength, line).color(cornerColor).draw();
        Render2DUtil.rect(right - line, top, line, cornerLength).color(cornerColor).draw();
        Render2DUtil.rect(left, bottom - line, cornerLength, line).color(cornerColor).draw();
        Render2DUtil.rect(left, bottom - cornerLength, line, cornerLength).color(cornerColor).draw();
        Render2DUtil.rect(right - cornerLength, bottom - line, cornerLength, line).color(cornerColor).draw();
        Render2DUtil.rect(right - line, bottom - cornerLength, line, cornerLength).color(cornerColor).draw();

        if (Float.isFinite(this.activeGuideX)) {
            Render2DUtil.rect(this.activeGuideX - line * 2.0F, 0.0F, line * 4.0F, height)
                    .color(ColorUtil.rgba(205, 210, 218, 30))
                    .draw();
            Render2DUtil.rect(this.activeGuideX - line / 2.0F, 0.0F, line, height)
                    .color(ColorUtil.rgba(220, 224, 230, 175))
                    .draw();
        }
        if (Float.isFinite(this.activeGuideY)) {
            Render2DUtil.rect(0.0F, this.activeGuideY - line * 2.0F, width, line * 4.0F)
                    .color(ColorUtil.rgba(205, 210, 218, 30))
                    .draw();
            Render2DUtil.rect(0.0F, this.activeGuideY - line / 2.0F, width, line)
                    .color(ColorUtil.rgba(220, 224, 230, 175))
                    .draw();
        }
    }

    private static void drawDashedVertical(float x, float top, float bottom, float dash, float gap, float width, int color) {
        for (float y = top; y < bottom; y += dash + gap) {
            Render2DUtil.rect(x - width / 2.0F, y, width, Math.min(dash, bottom - y)).color(color).draw();
        }
    }

    private static void drawDashedHorizontal(float y, float left, float right, float dash, float gap, float height, int color) {
        for (float x = left; x < right; x += dash + gap) {
            Render2DUtil.rect(x, y - height / 2.0F, Math.min(dash, right - x), height).color(color).draw();
        }
    }

    private void clearSnapGuides() {
        this.activeGuideX = Float.NaN;
        this.activeGuideY = Float.NaN;
    }

    private void drawResetButton(Minecraft mc, float unit, List<HudElement> visible) {
        float height = 28.0F * unit;
        float radius = 8.0F * unit;
        float padding = 8.0F * unit;
        float gap = 4.0F * unit;
        float iconSize = 12.0F * unit;
        float dividerWidth = Math.max(0.5F, 0.5F * unit);
        float textSize = 10.0F * unit;
        MsdfFont font = UiFonts.sfProDisplay();
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();
        float textWidth = font.measureWidth(RESET_LABEL, textSize, letterSpacing);

        this.resetButtonWidth = padding * 2.0F + iconSize + gap * 2.0F + dividerWidth + textWidth;
        this.resetButtonHeight = height;
        this.resetButtonX = (mc.getWindow().getGuiScaledWidth() - this.resetButtonWidth) / 2.0F;
        this.resetButtonY = resolveResetButtonY(mc, visible);

        float mouseX = mouseX(mc);
        float mouseY = mouseY(mc);
        boolean hovered = inside(mouseX, mouseY,
                this.resetButtonX,
                this.resetButtonY,
                this.resetButtonWidth,
                this.resetButtonHeight);
        int foreground = hovered ? Theme.Colors.PRIMARY_BRIGHT : Theme.Colors.TEXT_TEXT;
        int iconColor = Theme.getAccent();
        float centerY = this.resetButtonY + height / 2.0F;

        Render2DUtil.rect(this.resetButtonX, this.resetButtonY, this.resetButtonWidth, height)
                .color(Theme.Colors.BACKGROUND_PRIMARY_50)
                .radius(radius)
                .border(Math.max(0.5F, 0.5F * unit), Theme.Colors.OUTLINES_MEDIUM)
                .blur(8.0F * unit)
                .draw();

        float cursor = this.resetButtonX + padding;
        Render2DUtil.texture(cursor, centerY - iconSize / 2.0F, iconSize, iconSize, RESET_ICON)
                .color(iconColor)
                .draw();
        cursor += iconSize + gap;

        Render2DUtil.rect(cursor, centerY - iconSize / 2.0F, dividerWidth, iconSize)
                .color(RESET_DIVIDER_COLOR)
                .draw();
        cursor += dividerWidth + gap;

        Render2DUtil.text(cursor, font.centeredTextY(centerY, textSize), textSize, RESET_LABEL)
                .style(UiFontStyle.MEDIUM)
                .color(foreground)
                .draw();
    }

    private float resolveResetButtonY(Minecraft mc, List<HudElement> visible) {
        float preferredY = 18.0F;
        float minY = 4.0F;
        float maxY = Math.max(minY,
                mc.getWindow().getGuiScaledHeight() - this.resetButtonHeight - minY);
        for (float offset = 0.0F; offset <= maxY + preferredY; offset += 2.0F) {
            float above = preferredY - offset;
            if (above >= minY && resetButtonFits(visible, above)) {
                return above;
            }
            float below = preferredY + offset;
            if (offset > 0.0F && below <= maxY && resetButtonFits(visible, below)) {
                return below;
            }
        }
        return Math.min(preferredY, maxY);
    }

    private boolean resetButtonFits(List<HudElement> visible, float y) {
        float gap = 4.0F;
        for (HudElement element : visible) {
            if (element.overlaps(
                    this.resetButtonX - gap,
                    y - gap,
                    this.resetButtonWidth + gap * 2.0F,
                    this.resetButtonHeight + gap * 2.0F)) {
                return false;
            }
        }
        return true;
    }

    private List<HudElement> visibleElements() {
        List<HudElement> visible = new ArrayList<>(4);
        if (this.elements.isSelected(WATERMARK)) {
            visible.add(this.watermarkElement);
        }
        if (this.elements.isSelected(KEYBINDS)) {
            visible.add(this.keybindsElement);
        }
        if (this.elements.isSelected(POTIONS)) {
            visible.add(this.potionsElement);
        }
        if (this.elements.isSelected(TARGET)) {
            visible.add(this.targetElement);
        }
        if (this.elements.isSelected(HOTBAR)) {
            visible.add(this.hotbarElement);
        }
        if (this.elements.isSelected(ARMOR_HUD)) {
            visible.add(this.armorHudElement);
        }
        if (this.elements.isSelected(DYNAMIC_ISLAND)) {
            visible.add(this.dynamicIsland);
        }
        return visible;
    }

    private void stopDrag(Minecraft mc) {
        this.draggedElement.stopDrag(mc);
        this.draggedElement = null;
        clearSnapGuides();
    }

    private static boolean controlDown(Minecraft mc) {
        long window = mc.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static float mouseX(Minecraft mc) {
        return (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
    }

    private static float mouseY(Minecraft mc) {
        return (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void migrateLegacySize() {
        if (this.sizeMigrationChecked) {
            return;
        }
        this.sizeMigrationChecked = true;
        if (MenuConfigStore.getBoolean(SIZE_MIGRATION_KEY, false)) {
            return;
        }
        if (this.size.getValue() == LEGACY_DEFAULT_SIZE) {
            this.size.setValue(100.0);
        }
        MenuConfigStore.save(data -> data.addProperty(SIZE_MIGRATION_KEY, true));
    }
}
