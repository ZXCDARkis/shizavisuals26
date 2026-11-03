package org.shizavisuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.utils.math.MathUtil;

public abstract class HudElement {
    private static final long APPEAR_MS = 200L;

    private final String id;
    private final String displayName;

    private final String configKeyX;
    private final String configKeyY;
    private final String configKeyScale;
    protected float x;
    protected float y;
    protected float width;
    protected float height;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    /** Momentum after release: HUD panels can be thrown and glide to a snap point. */
    private final org.shizavisuals.utils.render.gui.Inertia inertia =
            new org.shizavisuals.utils.render.gui.Inertia(0.0F, 0.0F);
    /** Deforms while thrown so the HUD shares the menu's material. */
    private final org.shizavisuals.utils.render.gui.JellyPanel jelly =
            new org.shizavisuals.utils.render.gui.JellyPanel();
    private boolean wasHidden = true;
    private long appearStart;
    private float restX;
    private float restY;
    private boolean hasRest;
    /** Throw speed latched on release, consumed by HudFeature. */
    private float pendingThrowX;
    private float pendingThrowY;
    private boolean pendingThrow;
    private boolean positionInitialized;
    /** Set once the user drags the element; centred elements free themselves then. */
    private boolean userMoved;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;

    protected HudElement(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.configKeyX = "hud." + id + ".x";
        this.configKeyY = "hud." + id + ".y";
        this.configKeyScale = "hud." + id + ".scale";
        // Per-element scale multiplier, 0.5..1.8. Elements used to share one
        // global HUD size, so scaling the watermark also blew up the hotbar.
        this.scalePercent = MenuConfigStore.getFloat(this.configKeyScale, 100.0F);
    }

    public static final float MIN_SCALE_PERCENT = 50.0F;
    public static final float MAX_SCALE_PERCENT = 180.0F;

    private float scalePercent;
    /** Live scale during a resize drag, before it is committed. */
    private float pendingScalePercent = -1.0F;

    /** This element's size as a fraction of the global HUD scale. */
    public final float scaleMultiplier() {
        return percent() / 100.0F;
    }

    private float percent() {
        return this.pendingScalePercent >= 0.0F ? this.pendingScalePercent : this.scalePercent;
    }

    /** Scale in percent, clamped to the supported range. */
    public final float scalePercent() {
        return percent();
    }

    public final void setScalePercent(float percent) {
        this.pendingScalePercent = -1.0F;
        this.scalePercent = Math.clamp(percent, MIN_SCALE_PERCENT, MAX_SCALE_PERCENT);
        MenuConfigStore.save(data -> data.addProperty(this.configKeyScale, (double) this.scalePercent));
    }

    /** Commits an in-progress resize gesture. */
    public final void commitScale() {
        if (this.pendingScalePercent < 0.0F) {
            return;
        }
        setScalePercent(this.pendingScalePercent);
    }

    /** Live scale during a drag, without committing it to the config. */
    public final void previewScalePercent(float percent) {
        this.pendingScalePercent = Math.clamp(percent, MIN_SCALE_PERCENT, MAX_SCALE_PERCENT);
    }

    public final String id() {
        return this.id;
    }

    public final String displayName() {
        return this.displayName;
    }

    public final void render(Minecraft mc, float globalUnit) {
        // The per-element scale multiplies the global HUD unit, so an element's own
        // layout constants stay in "design units" and every child of it (text
        // size, padding, radius) scales with it for free.
        float unit = globalUnit * scaleMultiplier();
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

        // Snap targets. While the panel is flying we must clamp only the free
        // flight, otherwise the clamp would fight the momentum every frame and
        // the throw would stick to the wall instead of stopping cleanly.
        float targetX = MathUtil.clamp(this.x, 0.0F, freeWidth);
        float targetY = MathUtil.clamp(this.y, 0.0F, freeHeight);
        if (this.hasRest) {
            // The owner (HudFeature) resolved a snap guide for this flight.
            targetX = MathUtil.clamp(this.restX, 0.0F, freeWidth);
            targetY = MathUtil.clamp(this.restY, 0.0F, freeHeight);
        }
        if (isHorizontallyLocked()) {
            targetX = freeWidth / 2.0F;
        }

        if (this.dragging) {
            // The position is written directly so dragTo() sees it, but the
            // inertia must NOT be snapped here: snapTo() zeroes the velocity, and
            // it runs after dragTo() has already measured it, so every throw
            // released at zero speed.
            this.x = mouseX(mc) - this.dragOffsetX;
            this.y = mouseY(mc) - this.dragOffsetY;
        } else if (this.inertia.initialized()) {
            this.inertia.update(mouseX(mc), mouseY(mc), targetX, targetY);
            // The inertia owns the position until it settles; hard-clamping the
            // throw to the screen would cancel it mid-flight.
            this.x = this.inertia.x();
            this.y = this.inertia.y();
            if (this.inertia.settled()) {
                this.inertia.snapTo(targetX, targetY);
                this.x = targetX;
                this.y = targetY;
                // Persist the place it ACTUALLY landed, not the predicted one at
                // release time. Saving early left the config holding a spot the
                // panel never reached, which is what it snapped back to later.
                boolean wasThrow = endThrow();
                if (wasThrow) {
                    savePosition(mc);
                }
            }
        } else {
            // Not dragging and never launched: the config is the single source of
            // truth. Reading it back every frame is what used to make a released
            // panel drift back to where it was saved from.
            this.x = targetX;
            this.y = targetY;
            if (isHorizontallyLocked()) {
                this.x = freeWidth / 2.0F;
            }
            this.inertia.snapTo(this.x, this.y);
        }
        // No post-settle override here. It used to reset x/y to the values read
        // from the config BEFORE the flight, which is exactly why a thrown panel
        // snapped back to its release spot one frame after landing. Each branch
        // above already leaves x/y at the right place.

        // Jelly reacts to how far the panel travelled this frame, so a throw
        // smears and a snap does not.
        this.jelly.update(this.x + this.width / 2.0F, this.y + this.height / 2.0F, 0.0F);

        // The jelly deformation is applied around the element's own centre, so the
        // panel visibly smears while it flies and springs back when it lands. It
        // has to wrap the draw: without the matrix the squash was computed but
        // never seen.
        float scaleX = this.jelly.scaleX();
        float scaleY = this.jelly.scaleY();
        if (Math.abs(scaleX - 1.0F) > 0.002F || Math.abs(scaleY - 1.0F) > 0.002F) {
            var pose = RenderContext.currentGuiGraphicsExtractor().pose();
            float centerX = this.x + this.width / 2.0F;
            float centerY = this.y + this.height / 2.0F;
            pose.pushMatrix();
            pose.translate(centerX, centerY);
            pose.scale(scaleX, scaleY);
            pose.translate(-centerX, -centerY);
            draw(mc, unit);
            RenderContext.currentGuiGraphicsExtractor().pose().popMatrix();
            return;
        }
        draw(mc, unit);
    }

    private static float mouseY(Minecraft mc) {
        return (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
    }

    private static float mouseX(Minecraft mc) {
        return (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
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
        this.inertia.grab(mouseX, mouseY, this.x, this.y);
        this.pendingThrow = false;
        this.hasRest = false;
        return true;
    }

    protected boolean draggable() {
        return true;
    }

    public final void dragTo(float mouseX, float mouseY) {
        // Centred elements (island, hotbar) only stay locked to the middle until
        // the user actually moves them, otherwise they can never leave the centre
        // and they never take part in horizontal snapping.
        if (!isHorizontallyLocked()) {
            this.x = mouseX - this.dragOffsetX;
            this.userMoved = true;
        }
        this.y = mouseY - this.dragOffsetY;
        // Track the pointer so the release velocity is real instead of zero.
        this.inertia.update(mouseX, mouseY, this.x, this.y);
    }

    /** Stops an in-flight throw and hands the position back to the caller. */
    public final void settleNow() {
        this.inertia.snapTo(this.x, this.y);
        this.jelly.reset();
    }

    /**
     * Throw speed in GUI px/s, latched at the moment of release and cleared by the
     * read. Reading it before {@link #stopDrag} returns 0, because the drag is
     * still live at that point and the inertia has not measured anything yet.
     */
    public final float releaseVelocityX() {
        return this.pendingThrowX;
    }

    public final float releaseVelocityY() {
        return this.pendingThrowY;
    }

    public final void clearThrow() {
        this.pendingThrowX = 0.0F;
        this.pendingThrowY = 0.0F;
    }

    /** Called once the flight has finished; the panel is back under control. */
    private boolean endThrow() {
        boolean wasThrow = this.pendingThrow;
        this.pendingThrow = false;
        this.pendingThrowX = 0.0F;
        this.pendingThrowY = 0.0F;
        this.hasRest = false;
        return wasThrow;
    }

    /** Writes the current spot to the config as a fraction of the free space. */
    private void savePosition(Minecraft mc) {
        float freeWidth = Math.max(1.0F, mc.getWindow().getGuiScaledWidth() - this.width);
        float freeHeight = Math.max(1.0F, mc.getWindow().getGuiScaledHeight() - this.height);
        float fractionX = MathUtil.clamp01(this.x / freeWidth);
        float fractionY = MathUtil.clamp01(this.y / freeHeight);
        MenuConfigStore.save(data -> {
            data.addProperty(this.configKeyX, fractionX);
            data.addProperty(this.configKeyY, fractionY);
        });
    }

    /** Re-applies the latched throw speed so the panel glides. Sets, not adds. */
    public final void fling(float impulseX, float impulseY) {
        this.inertia.fling(impulseX, impulseY);
    }

    private boolean isHorizontallyLocked() {
        return centerHorizontally() && !this.userMoved;
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

    /** False while the panel is flying or springing back — the editor waits for it. */
    public final boolean isSettled() {
        return this.inertia.settled() && this.jelly.settled();
    }

    /** True while this element is the one being dragged. */
    public final boolean dragging() {
        return this.dragging;
    }

    public final boolean isHorizontallyCentered() {
        return isHorizontallyLocked();
    }

    public final void snapTo(float x, float y) {
        if (!this.dragging) {
            return;
        }
        if (!isHorizontallyLocked()) {
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
        this.userMoved = false;
        this.lastScreenWidth = -1;
        this.lastScreenHeight = -1;
        this.inertia.snapTo(0.0F, 0.0F);
        this.jelly.reset();
    }

    /** Applies a configuration-driven position without motion. */
    public final void placeAt(float x, float y) {
        this.x = x;
        this.y = y;
        this.inertia.snapTo(x, y);
    }

    public final void stopDrag(Minecraft mc) {
        if (!this.dragging) {
            return;
        }
        this.dragging = false;
        // release() latches the measured throw speed (and zeroes the live
        // velocity), so the latch has to be read AFTER it — reading before gave
        // the stale value from the previous release, which made throws launch at
        // the wrong speed or not at all.
        this.inertia.release();
        this.pendingThrowX = this.inertia.latchedVelocityX();
        this.pendingThrowY = this.inertia.latchedVelocityY();

        // HudFeature resolves the landing spot ONCE, on the next frame. Clearing
        // the latch here is what makes needsRestTarget() fire a single time
        // instead of every frame for the whole flight.
        this.pendingThrow = true;
        this.hasRest = false;

        // Persist the DESTINATION, not the current position: after a throw the
        // panel is still gliding, and saving the midpoint would make it drift
        // back on every reload. The glide is the exact decay integral: velocity
        // divided by the friction constant.
        float freeWidth = Math.max(1.0F, mc.getWindow().getGuiScaledWidth() - this.width);
        float freeHeight = Math.max(1.0F, mc.getWindow().getGuiScaledHeight() - this.height);
        float savedX = MathUtil.clamp(
                this.x + org.shizavisuals.utils.render.gui.Inertia.remainingGlide(this.pendingThrowX), 0.0F, freeWidth);
        float savedY = MathUtil.clamp(
                this.y + org.shizavisuals.utils.render.gui.Inertia.remainingGlide(this.pendingThrowY), 0.0F, freeHeight);
        float fractionX = MathUtil.clamp01(savedX / freeWidth);
        float fractionY = MathUtil.clamp01(savedY / freeHeight);
        MenuConfigStore.save(data -> {
            data.addProperty(this.configKeyX, fractionX);
            data.addProperty(this.configKeyY, fractionY);
        });
    }

    /** Where the panel will come to rest: current position plus its remaining glide. */
    public final void restPosition(float screenWidth, float screenHeight, float[] out) {
        float freeWidth = Math.max(0.0F, screenWidth - this.width);
        float freeHeight = Math.max(0.0F, screenHeight - this.height);
        // Free flight decays as exp(-FRICTION * t): the remaining distance is
        // velocity / FRICTION, not velocity scaled by an arbitrary factor — an
        // overestimated glide put the snap guide beyond the actual landing spot,
        // and the spring then yanked the panel back or forward to reach it.
        out[0] = MathUtil.clamp(
                this.x + org.shizavisuals.utils.render.gui.Inertia.remainingGlide(this.inertia.velocityX()), 0.0F, freeWidth);
        out[1] = MathUtil.clamp(
                this.y + org.shizavisuals.utils.render.gui.Inertia.remainingGlide(this.inertia.velocityY()), 0.0F, freeHeight);
    }

    /**
     * Overrides where a flying panel should settle. The owner owns this because
     * only it knows about snap guides; a thrown HUD element has to land on a
     * guide, not wherever the momentum happens to die.
     *
     * <p>Latched. Recomputing the destination every frame from the CURRENT
     * position made the target travel with the panel, so the spring chased a
     * moving target and the panel twitched back towards where it was released.
     */
    public final void targetRest(float x, float y) {
        this.restX = x;
        this.restY = y;
        this.hasRest = true;
    }

    /**
     * True once, on the first frame of a flight, when the owner still has to
     * resolve the landing spot. False while dragging and after the target has
     * been handed over.
     */
    public final boolean needsRestTarget() {
        return !this.dragging && this.pendingThrow && !this.hasRest;
    }
}
