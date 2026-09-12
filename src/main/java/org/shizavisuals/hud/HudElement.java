package org.shizavisuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.utils.math.MathUtil;

public abstract class HudElement {
    private static final long APPEAR_MS = 200L;

    private final String id;
    private final String displayName;

    private final String configKeyX;
    private final String configKeyY;
    protected float x;
    protected float y;
    protected float width;
    protected float height;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean wasHidden = true;
    private long appearStart;
    private boolean positionInitialized;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;

    protected HudElement(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.configKeyX = "hud." + id + ".x";
        this.configKeyY = "hud." + id + ".y";
    }

    public final String id() {
        return this.id;
    }

    public final String displayName() {
        return this.displayName;
    }

    public final void render(Minecraft mc, float unit) {
        layout(mc, unit);
        if (this.width <= 0.5F || this.height <= 0.5F) {
            this.wasHidden = true;
            return;
        }
        if (this.wasHidden) {
            this.wasHidden = false;
            this.appearStart = System.currentTimeMillis();
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        float freeWidth = Math.max(0.0F, screenWidth - this.width);
        float freeHeight = Math.max(0.0F, screenHeight - this.height);
        boolean contentResizeOnly = preservePositionOnContentResize()
                && this.positionInitialized
                && screenWidth == this.lastScreenWidth
                && screenHeight == this.lastScreenHeight;
        if (!this.dragging) {
            if (!contentResizeOnly) {
                this.x = MenuConfigStore.getFloat(this.configKeyX, defaultX(unit) / Math.max(1.0F, freeWidth)) * freeWidth;
                this.y = MenuConfigStore.getFloat(this.configKeyY, defaultY(unit) / Math.max(1.0F, freeHeight)) * freeHeight;
            }
        }
        this.positionInitialized = true;
        this.lastScreenWidth = screenWidth;
        this.lastScreenHeight = screenHeight;
        this.x = contentResizeOnly && !this.dragging ? Math.max(0.0F, this.x) : MathUtil.clamp(this.x, 0.0F, freeWidth);
        this.y = MathUtil.clamp(this.y, 0.0F, freeHeight);
        if (centerHorizontally()) {
            this.x = freeWidth / 2.0F;
        }

        draw(mc, unit);
    }

    protected boolean centerHorizontally() {
        return false;
    }

    protected boolean preservePositionOnContentResize() {
        return false;
    }

    protected final float appearAlpha() {
        float progress = MathUtil.clamp01((System.currentTimeMillis() - this.appearStart) / (float) APPEAR_MS);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    protected static final int DIVIDER_COLOR = org.shizavisuals.utils.ColorUtil.rgba(255, 255, 255, 25);

    protected final float divider(float cursor, float centerY, float heightUnits, float gapUnits, float unit, float alpha) {
        cursor += gapUnits * unit;
        org.shizavisuals.utils.render.gui.Render2DUtil.rect(
                        cursor, centerY - heightUnits * unit / 2.0F, 1.0F * unit, heightUnits * unit)
                .color(org.shizavisuals.utils.ColorUtil.multiplyAlpha(DIVIDER_COLOR, alpha))
                .draw();
        return cursor + (1.0F + gapUnits) * unit;
    }

    protected abstract void layout(Minecraft mc, float unit);

    protected float defaultX(float unit) {
        return 20.0F * unit;
    }

    protected float defaultY(float unit) {
        return 20.0F * unit;
    }

    protected abstract void draw(Minecraft mc, float unit);

    protected static boolean showcase(Minecraft mc) {
        return mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof InventoryScreen;
    }

    public final boolean startDrag(float mouseX, float mouseY) {
        if (!draggable()) {
            return false;
        }
        if (mouseX < this.x || mouseX > this.x + this.width || mouseY < this.y || mouseY > this.y + this.height) {
            return false;
        }
        this.dragging = true;
        this.dragOffsetX = mouseX - this.x;
        this.dragOffsetY = mouseY - this.y;
        return true;
    }

    protected boolean draggable() {
        return true;
    }

    public final void dragTo(float mouseX, float mouseY) {
        if (!centerHorizontally()) {
            this.x = mouseX - this.dragOffsetX;
        }
        this.y = mouseY - this.dragOffsetY;
    }

    public final boolean isDragging() {
        return this.dragging;
    }

    public final float x() {
        return this.x;
    }

    public final float y() {
        return this.y;
    }

    public final float width() {
        return this.width;
    }

    public final float height() {
        return this.height;
    }

    public final boolean isHorizontallyCentered() {
        return centerHorizontally();
    }

    public final void snapTo(float x, float y) {
        if (!this.dragging) {
            return;
        }
        if (!centerHorizontally()) {
            this.x = x;
        }
        this.y = y;
    }

    public final boolean overlaps(float x, float y, float width, float height) {
        return this.width > 0.5F
                && this.height > 0.5F
                && x < this.x + this.width
                && x + width > this.x
                && y < this.y + this.height
                && y + height > this.y;
    }

    public final void resetPosition() {
        this.dragging = false;
        this.positionInitialized = false;
        this.lastScreenWidth = -1;
        this.lastScreenHeight = -1;
    }

    public final void stopDrag(Minecraft mc) {
        if (!this.dragging) {
            return;
        }
        this.dragging = false;
        float freeWidth = Math.max(1.0F, mc.getWindow().getGuiScaledWidth() - this.width);
        float freeHeight = Math.max(1.0F, mc.getWindow().getGuiScaledHeight() - this.height);
        float fractionX = MathUtil.clamp01(this.x / freeWidth);
        float fractionY = MathUtil.clamp01(this.y / freeHeight);
        MenuConfigStore.save(data -> {
            data.addProperty(this.configKeyX, fractionX);
            data.addProperty(this.configKeyY, fractionY);
        });
    }
}
