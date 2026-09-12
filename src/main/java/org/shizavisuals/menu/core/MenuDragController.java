package org.shizavisuals.menu.core;

import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.utils.math.MathUtil;

public final class MenuDragController {
    private boolean dragging;
    private boolean leftMouseHeld;
    private float dragOffsetX;
    private float dragOffsetY;

    public void reset() {
        this.dragging = false;
        this.leftMouseHeld = false;
        this.dragOffsetX = 0.0F;
        this.dragOffsetY = 0.0F;
    }

    public void onPress(float mouseX, float mouseY, MenuOverlayState state, Component component) {
        this.leftMouseHeld = true;
        if (component == null || !component.isDragHandle(mouseX, mouseY)) {
            return;
        }

        this.dragging = true;
        this.dragOffsetX = mouseX - state.panelX();
        this.dragOffsetY = mouseY - state.panelY();
    }

    public void onRelease() {
        this.leftMouseHeld = false;
        this.dragging = false;
    }

    public void update(float mouseX, float mouseY, MenuOverlayState state, MenuDimensions dimensions, float screenWidth, float screenHeight) {
        if (!this.leftMouseHeld) {
            this.dragging = false;
            return;
        }
        if (!this.dragging) {
            return;
        }

        float maxX = Math.max(0.0F, screenWidth - dimensions.panelWidth());
        float maxY = Math.max(0.0F, screenHeight - dimensions.panelHeight());
        state.setPanelPosition(
                MathUtil.clamp(mouseX - this.dragOffsetX, 0.0F, maxX),
                MathUtil.clamp(mouseY - this.dragOffsetY, 0.0F, maxY)
        );
    }
}
