package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.context.RenderContext;
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
    /** Element whose resize grip is held; null when no resize is in progress. */
    private HudElement resizedElement;
    private float resizeStartX;
    private float resizeStartY;
    private float resizeStartPercent = 100.0F;
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
        super("HUD", "Настройки кастомизации HUD", FeatureCategory.VISUAL, BindSetting.UNBOUND);
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

        if (this.resizedElement != null) {
            // SIGNED distance along the grip's own diagonal. Using the magnitude
            // (sqrt) made both directions increase the size, so dragging inwards
            // grew the element instead of shrinking it.
            float dx = mouseX(mc) - this.resizeStartX;
            float dy = mouseY(mc) - this.resizeStartY;
            // The grip sits at the bottom-right corner, so "outward" (grow) is
            // +x/+y and "inward" (shrink) is -x/-y.
            float signed = (dx + dy) * 0.5F;
            // Reference is the element's own diagonal half, so the sensitivity
            // matches the size of the thing being resized at any zoom level.
            float reference = Math.max(16.0F, (this.resizedElement.width() + this.resizedElement.height()) * 0.25F);
            float percent = this.resizeStartPercent * (1.0F + signed / reference);
            // Magnet at 100% (the default) plus integer steps: without this the
            // drag almost never produced exactly 100% again.
            if (Math.abs(percent - 100.0F) < 2.0F) {
                percent = 100.0F;
            } else {
                percent = Math.max(50.0F, Math.min(200.0F, (float) Math.round(percent)));
            }
            this.resizedElement.previewScalePercent(percent);
        } else if (this.draggedElement != null) {
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

        // A thrown panel must land ON a snap guide, not next to it: apply the
        // same candidate search to the predicted resting position. Without this
        // the inertia carries the element past the guides and it settles at an
        // arbitrary offset, which looked like the snapping was broken.
        // Landing spot is resolved ONCE per flight. Doing it every frame from the live
        // position made the destination move with the panel, and the panel visibly
        // twitched back towards its release point on long throws.
        for (HudElement element : visible) {
            if (element.needsRestTarget()) {
                applySnapToRest(mc, visible, element);
            }
        }

        if (snapMode) {
            drawSnapGrid(mc);
        }

        // A released HUD panel keeps gliding, so the edit overlay has to stay up
        // for as long as anything is still in motion — otherwise the grid would
        // vanish under the panel that is flying across it and the snap guides
        // would flash off right when they matter.
        boolean editing = chatOpen && (snapMode || this.draggedElement != null || gliding(mc, visible));

        for (HudElement element : visible) {
            element.render(mc, unit);
        }
        if (editing) {
            drawResizeHandle(mc, unit, visible);
        }
        if (snapMode) {
            drawResetButton(mc, unit, visible);
        } else {
            this.resetButtonWidth = 0.0F;
            this.resetButtonHeight = 0.0F;
        }
    }

    /** True while any element is still flying or settling from a throw. */
    private static boolean gliding(Minecraft mc, List<HudElement> visible) {
        return glidingElement(visible) != null;
    }

    private static HudElement glidingElement(List<HudElement> visible) {
        for (HudElement element : visible) {
            if (!element.isSettled()) {
                return element;
            }
        }
        return null;
    }

    /**
     * Snaps the resting position of a flying panel. Uses the same candidate set
     * as the drag snap so a throw behaves identically to a careful drag.
     */
    private void applySnapToRest(Minecraft mc, List<HudElement> visible, HudElement flying) {
        float[] rest = new float[2];
        flying.restPosition(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), rest);
        float maxX = Math.max(0.0F, mc.getWindow().getGuiScaledWidth() - flying.width());
        float maxY = Math.max(0.0F, mc.getWindow().getGuiScaledHeight() - flying.height());
        // Guides are only meaningful while the user is looking at them; a
        // backgrounded HUD panel should just land in bounds.
        boolean showGuides = mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
        SnapCandidate snappedX = showGuides ? nearestSnap(rest[0], xCandidates(maxX, flying, visible)) : null;
        SnapCandidate snappedY = showGuides ? nearestSnap(rest[1], yCandidates(maxY, flying, visible)) : null;
        if (snappedX != null) {
            rest[0] = snappedX.position();
            this.activeGuideX = snappedX.guide();
        }
        if (snappedY != null) {
            rest[1] = snappedY.position();
            this.activeGuideY = snappedY.guide();
        }
        flying.targetRest(rest[0], rest[1]);
    }

    private static SnapCandidate nearestSnap(float position, List<SnapCandidate> candidates) {
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

    private List<SnapCandidate> xCandidates(float maxX, HudElement dragged, List<HudElement> visible) {
        float screenWidth = maxX + dragged.width();
        List<SnapCandidate> candidates = new ArrayList<>(24);
        addCandidate(candidates, Math.min(SNAP_MARGIN, maxX), SNAP_MARGIN, maxX);
        addCandidate(candidates, maxX / 2.0F, screenWidth / 2.0F, maxX);
        addCandidate(candidates, Math.max(0.0F, maxX - SNAP_MARGIN), screenWidth - SNAP_MARGIN, maxX);
        addEdgeCandidates(candidates, dragged, visible, maxX, true);
        return candidates;
    }

    private List<SnapCandidate> yCandidates(float maxY, HudElement dragged, List<HudElement> visible) {
        float screenHeight = maxY + dragged.height();
        List<SnapCandidate> candidates = new ArrayList<>(24);
        addCandidate(candidates, Math.min(SNAP_MARGIN, maxY), SNAP_MARGIN, maxY);
        addCandidate(candidates, maxY / 2.0F, screenHeight / 2.0F, maxY);
        addCandidate(candidates, Math.max(0.0F, maxY - SNAP_MARGIN), screenHeight - SNAP_MARGIN, maxY);
        addEdgeCandidates(candidates, dragged, visible, maxY, false);
        return candidates;
    }

    private void addEdgeCandidates(List<SnapCandidate> candidates, HudElement dragged,
                                   List<HudElement> visible, float max, boolean horizontal) {
        for (HudElement other : visible) {
            if (other == dragged || other.width() <= 0.5F || other.height() <= 0.5F) {
                continue;
            }
            float self = horizontal ? dragged.width() : dragged.height();
            float otherStart = horizontal ? other.x() : other.y();
            float otherSize = horizontal ? other.width() : other.height();
            float otherEnd = otherStart + otherSize;
            float otherCenter = otherStart + otherSize / 2.0F;

            addCandidate(candidates, otherStart, otherStart, max);
            addCandidate(candidates, otherEnd - self, otherEnd, max);
            addCandidate(candidates, otherCenter - self / 2.0F, otherCenter, max);
            addCandidate(candidates, otherStart - self - SNAP_GAP, otherStart - SNAP_GAP / 2.0F, max);
            addCandidate(candidates, otherEnd + SNAP_GAP, otherEnd + SNAP_GAP / 2.0F, max);
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        Minecraft mc = MinecraftContext.mc;
        if (mc == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (this.resizedElement != null) {
                // Only persist if the gesture actually changed anything: committing
                // unconditionally rewrote the config on every grip click.
                this.resizedElement.commitScale();
                this.resizedElement.settleNow();
                this.resizedElement = null;
                event.cancel();
                return;
            }
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

        // Resize grip wins over dragging: it sits on top of the element corner,
        // so without this the first click would grab and move the panel instead
        // of scaling it.
        if (this.handleSize > 0.0F && inside(mouseX, mouseY,
                this.handleX - HANDLE_GRAB / 2.0F,
                this.handleY - HANDLE_GRAB / 2.0F,
                this.handleSize + HANDLE_GRAB,
                this.handleSize + HANDLE_GRAB)) {
            this.resizedElement = handleTarget(mc, visibleElements());
            if (this.resizedElement != null) {
                this.resizeStartX = mouseX;
                this.resizeStartY = mouseY;
                this.resizeStartPercent = this.resizedElement.scalePercent();
                this.draggedElement = null;
                event.cancel();
                return;
            }
        }

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

        // Snap on both axes for every element: the island and the hotbar are only
        // centred until the user moves them, so they must take part in the same
        // snapping as everything else instead of being silently skipped.
        SnapCandidate snappedX = nearestCandidate(x, xCandidates);
        if (snappedX != null) {
            x = snappedX.position();
            this.activeGuideX = snappedX.guide();
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

    // --- resize handle -------------------------------------------------------
    // Lives in the bottom-right corner of the dragged element and scales the HUD
    // 50..180%. A handle with no numeric readout forces the user to guess, so the
    // live percentage is drawn next to the grip.

    private static final float HANDLE_SIZE = 9.0F;
    private static final float HANDLE_GRAB = 18.0F;

    private float handleX;
    private float handleY;
    private float handleSize;

    private float handleCenterX(Minecraft mc, float unit, HudElement element) {
        return element.x() + element.width();
    }

    private float handleCenterY(Minecraft mc, float unit, HudElement element) {
        return element.y() + element.height();
    }

    private HudElement handleTarget(Minecraft mc, List<HudElement> visible) {
        if (this.draggedElement != null) {
            return this.draggedElement;
        }
        float mouseX = mouseX(mc);
        float mouseY = mouseY(mc);
        HudElement best = null;
        float bestDistance = Float.MAX_VALUE;
        // Topmost first, so a handle sitting over two panels picks the one the
        // user can actually see.
        for (int i = visible.size() - 1; i >= 0; i--) {
            HudElement element = visible.get(i);
            if (element.width() <= 0.5F || element.height() <= 0.5F) {
                continue;
            }
            float dx = mouseX - handleCenterX(mc, 1.0F, element);
            float dy = mouseY - handleCenterY(mc, 1.0F, element);
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance <= HANDLE_GRAB && distance < bestDistance) {
                best = element;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void drawResizeHandle(Minecraft mc, float unit, List<HudElement> visible) {
        HudElement target = handleTarget(mc, visible);
        if (target == null) {
            this.handleSize = 0.0F;
            return;
        }
        float size = HANDLE_SIZE * Math.max(1.0F, unit * 0.8F);
        float cx = handleCenterX(mc, unit, target);
        float cy = handleCenterY(mc, unit, target);
        float x = cx - size / 2.0F;
        float y = cy - size / 2.0F;
        this.handleX = x;
        this.handleY = y;
        this.handleSize = size;

        boolean active = this.resizedElement == target;
        boolean hovered = inside(mouseX(mc), mouseY(mc), x - HANDLE_GRAB / 2.0F,
                y - HANDLE_GRAB / 2.0F, size + HANDLE_GRAB, size + HANDLE_GRAB);
        int accent = Theme.getAccent();
        int gripColor = ColorUtil.multiplyAlpha(active || hovered ? ColorUtil.WHITE : accent,
                active ? 0.95F : hovered ? 0.85F : 0.65F);

        Render2DUtil.rect(x, y, size, size)
                .radius(size * 0.32F)
                .color(gripColor)
                .draw();
        // Diagonal grip lines, so the handle reads as "resize" and not "dot".
        float arm = size * 0.34F;
        float line = Math.max(1.0F, size * 0.13F);
        float diag = (float) Math.toDegrees(Math.atan2(size, size));
        RenderContext.currentGuiGraphicsExtractor().pose().pushMatrix();
        RenderContext.currentGuiGraphicsExtractor().pose().translate(x + size * 0.5F, y + size * 0.5F);
        RenderContext.currentGuiGraphicsExtractor().pose().rotate(diag);
        Render2DUtil.rect(arm - line / 2.0F, -size * 0.30F, arm, line).radius(line / 2.0F)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, 0.55F)).draw();
        RenderContext.currentGuiGraphicsExtractor().pose().rotate(-diag);
        Render2DUtil.rect(0.0F, size * 0.30F - line / 2.0F, arm, line).radius(line / 2.0F)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, 0.55F)).draw();
        RenderContext.currentGuiGraphicsExtractor().pose().popMatrix();

        // The readout only appears while the user interacts with the grip:
        // a permanent pill permanently covers HUD content.
        if (active || hovered) {
            drawResizeReadout(mc, unit, x + size / 2.0F, y, target.scalePercent());
        }
    }

    /** Live "128%" pill under the grip. */
    private void drawResizeReadout(Minecraft mc, float unit, float centerX, float topY, float percent) {
        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = 9.0F * Math.max(1.0F, unit * 0.85F);
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();
        String label = Math.round(percent) + "%";
        float textWidth = font.measureWidth(label, textSize, letterSpacing);
        float padX = 7.0F * Math.max(1.0F, unit * 0.8F);
        float padY = 4.0F * Math.max(1.0F, unit * 0.8F);
        float pillW = textWidth + padX * 2.0F;
        float pillH = font.textHeight(textSize) + padY * 2.0F;
        float pillX = centerX - pillW / 2.0F;
        float pillY = topY + 6.0F * Math.max(1.0F, unit * 0.8F);

        Render2DUtil.rect(pillX, pillY, pillW, pillH)
                .radius(pillH * 0.5F)
                .color(Theme.Colors.BACKGROUND_PRIMARY_50)
                .border(Math.max(0.5F, 0.5F * unit), Theme.getAccent())
                .blur(8.0F * unit)
                .draw();
        Render2DUtil.text(pillX + padX, pillY + padY, textSize, label)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, 0.95F))
                .draw();
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
        HudElement element = this.draggedElement;
        this.draggedElement = null;
        clearSnapGuides();
        if (element == null) {
            return;
        }
        // stopDrag latches the measured throw speed; the velocity has to be read
        // AFTER it, not before.
        element.stopDrag(mc);
        float throwX = element.releaseVelocityX();
        float throwY = element.releaseVelocityY();
        element.clearThrow();
        // Below this the drag was a slow reposition, not a throw. Re-launching it
        // just adds a stutter at the end of every deliberate placement.
        if (Math.abs(throwX) > 60.0F || Math.abs(throwY) > 60.0F) {
            element.fling(throwX, throwY);
        } else {
            element.settleNow();
        }
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
