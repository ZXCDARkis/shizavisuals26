package org.shizavisuals.menu.core;

import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.gui.Inertia;
import org.shizavisuals.utils.render.gui.JellyPanel;

/**
 * Panel movement with momentum, plus the corner grip that resizes it.
 *
 * <p>The old controller glued the panel to the cursor and stopped dead on
 * release, which is why the menu could not be thrown and felt welded to the
 * mouse. Now the panel keeps the velocity the drag measured, coasts, and is
 * caught by a spring — the same {@link Inertia} the HUD panels use, so both
 * surfaces decelerate the same way.
 *
 * <p>The resize grip scales {@link MenuOverlayState#uiScaleValue} rather than
 * resizing a rectangle, so every layout constant in the panel scales with it and
 * nothing has to be re-flowed by hand.
 */
public final class MenuDragController {
    /** Diagonal drag, so both axes resize at once at the expected rate. */
    private static final float RESIZE_SENSITIVITY = 1.9F;
    private static final float GRIP_SIZE = 9.0F;
    private static final float GRIP_GRAB = 20.0F;
    // uiScaleValue is a NORMALIZED 0..1 range (0 -> 50%, 0.5 -> 125%, 1 -> 200%:
    // lerped between MIN/MAX_UI_SCALE internally). Clamping it to
    // MIN_UI_SCALE_PERCENT/100 (0.5) locked the grip to 125% as its floor and
    // made a single grip click commit 125% forever.
    private static final float MIN_SCALE = 0.0F;
    private static final float MAX_SCALE = 1.0F;

    private final Inertia position = new Inertia(0.0F, 0.0F);
    private final JellyPanel jelly = new JellyPanel();
    private boolean dragging;
    private boolean resizing;
    private float dragOffsetX;
    private float dragOffsetY;
    private float resizeStartMouseX;
    private float resizeStartMouseY;
    private float resizeStartScale;
    private boolean initialized;

    private float gripX;
    private float gripY;
    private boolean gripVisible;

    public void reset() {
        this.dragging = false;
        this.resizing = false;
        this.initialized = false;
        this.position.snapTo(0.0F, 0.0F);
        this.jelly.reset();
        this.gripVisible = false;
    }

    public boolean resizing() {
        return this.resizing;
    }

    public void onPress(float mouseX, float mouseY, MenuOverlayState state, Component component) {
        if (component == null) {
            return;
        }
        // The grip sits on the panel corner and overlaps the drag handle, so it has
        // to be tested first or every resize would move the window instead.
        if (this.gripVisible && insideGrip(mouseX, mouseY)) {
            this.resizing = true;
            this.dragging = false;
            this.resizeStartMouseX = mouseX;
            this.resizeStartMouseY = mouseY;
            this.resizeStartScale = state.uiScaleValue();
            return;
        }
        if (!component.isDragHandle(mouseX, mouseY)) {
            return;
        }
        this.dragging = true;
        this.initialized = true;
        this.dragOffsetX = mouseX - state.panelX();
        this.dragOffsetY = mouseY - state.panelY();
        this.position.grab(mouseX, mouseY, state.panelX(), state.panelY());
    }

    public void onRelease(MenuOverlayState state) {
        if (this.resizing && state != null) {
            state.commitPreviewUiScale();
        } else if (state != null) {
            state.cancelPreviewUiScale();
        }
        this.dragging = false;
        this.resizing = false;
        this.position.release();
    }

    public void update(float mouseX, float mouseY, MenuOverlayState state,
                       MenuDimensions dimensions, float screenWidth, float screenHeight) {
        float panelWidth = dimensions.panelWidth();
        float panelHeight = dimensions.panelHeight();
        float maxX = Math.max(0.0F, screenWidth - panelWidth);
        float maxY = Math.max(0.0F, screenHeight - panelHeight);

        this.gripX = state.panelX() + panelWidth;
        this.gripY = state.panelY() + panelHeight;
        this.gripVisible = panelWidth > 0.0F && panelHeight > 0.0F;

        if (this.resizing) {
            // Signed projection along the grip diagonal: pulling in shrinks,
            // pushing out grows. Magnitude would make both directions grow.
            float dx = mouseX - this.resizeStartMouseX;
            float dy = mouseY - this.resizeStartMouseY;
            float travel = (dx + dy) * 0.5F;
            float scale = this.resizeStartScale + travel * RESIZE_SENSITIVITY / 400.0F;
            // Magnet at 100% so the readout can land exactly on it.
            float step = 1.0F / (MenuOverlayState.MAX_UI_SCALE_PERCENT - MenuOverlayState.MIN_UI_SCALE_PERCENT);
            float at100 = (100.0F - MenuOverlayState.MIN_UI_SCALE_PERCENT) * step;
            if (Math.abs(scale - at100) < 2.0F * step) {
                scale = at100;
            }
            // Preview only. setUiScaleValue() writes the config on every call, which
            // would mean a disk write per frame for the whole drag.
            state.previewUiScaleValue(Math.clamp(scale, MIN_SCALE, MAX_SCALE));
            return;
        }

        if (!this.dragging && !this.initialized) {
            // Never launched: the panel follows the stored position directly.
            this.position.snapTo(state.panelX(), state.panelY());
            this.jelly.reset();
            return;
        }

        float targetX = MathUtil.clamp(state.panelX(), 0.0F, maxX);
        float targetY = MathUtil.clamp(state.panelY(), 0.0F, maxY);
        this.position.update(mouseX, mouseY, targetX, targetY);
        // The inertia owns the position while it flies. Assigning the clamped
        // value first would cancel the throw on the very frame it starts.
        state.setPanelPosition(
                MathUtil.clamp(this.position.x(), 0.0F, maxX),
                MathUtil.clamp(this.position.y(), 0.0F, maxY));
        this.jelly.update(state.panelX() + panelWidth * 0.5F,
                state.panelY() + panelHeight * 0.5F, 0.0F);
    }

    private boolean insideGrip(float mouseX, float mouseY) {
        float half = GRIP_GRAB * 0.5F;
        return mouseX >= this.gripX - half && mouseX <= this.gripX + half
                && mouseY >= this.gripY - half && mouseY <= this.gripY + half;
    }

    public float gripX() {
        return this.gripX;
    }

    public float gripY() {
        return this.gripY;
    }

    public boolean gripVisible() {
        return this.gripVisible;
    }

    public static float gripSize() {
        return GRIP_SIZE;
    }

    /** True once the panel has stopped flying and deforming. */
    public boolean settled() {
        return this.position.settled() && this.jelly.settled();
    }
}