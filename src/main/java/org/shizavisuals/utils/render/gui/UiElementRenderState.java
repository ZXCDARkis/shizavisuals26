package org.shizavisuals.utils.render.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

abstract class UiElementRenderState implements GuiElementRenderState {
    private final float m00;
    private final float m01;
    private final float m10;
    private final float m11;
    private final float m20;
    private final float m21;
    private final ScreenRectangle bounds;
    private final ScreenRectangle scissor;

    protected UiElementRenderState(Matrix3x2fc pose, ScreenRectangle scissor, float x, float y, float width, float height) {
        this.m00 = pose.m00();
        this.m01 = pose.m01();
        this.m10 = pose.m10();
        this.m11 = pose.m11();
        this.m20 = pose.m20();
        this.m21 = pose.m21();
        this.scissor = scissor;
        this.bounds = new ScreenRectangle(
                Math.round(x),
                Math.round(y),
                Math.max(1, Math.round(width)),
                Math.max(1, Math.round(height))
        ).transformMaxBounds(pose);
    }

    protected final float transformX(float x, float y) {
        return this.m00 * x + this.m10 * y + this.m20;
    }

    protected final float transformY(float x, float y) {
        return this.m01 * x + this.m11 * y + this.m21;
    }

    @Override
    public final ScreenRectangle scissorArea() {
        return this.scissor;
    }

    @Override
    public final ScreenRectangle bounds() {
        return this.bounds;
    }
}
