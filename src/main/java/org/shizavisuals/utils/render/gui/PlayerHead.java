package org.shizavisuals.utils.render.gui;

import net.minecraft.resources.Identifier;

public final class PlayerHead {
    private PlayerHead() {
    }

    public static void draw(float x, float y, float size, Identifier skin, float radius, int tint) {
        Render2DUtil.texture(x, y, size, size, skin)
                .managed()
                .uv(8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F)
                .radius(radius)
                .color(tint)
                .draw();
        Render2DUtil.texture(x, y, size, size, skin)
                .managed()
                .uv(40.0F / 64.0F, 8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F)
                .radius(radius)
                .color(tint)
                .draw();
    }
}
