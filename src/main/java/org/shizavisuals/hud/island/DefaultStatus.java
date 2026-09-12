package org.shizavisuals.hud.island;

import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;

public class DefaultStatus extends IslandStatus {

    public static final String CLIENT_NAME = "ShizaVisuals";

    public DefaultStatus(DynamicIsland island) {
        super(island, "default");
    }

    @Override
    public void draw(float alpha) {
        float s = this.island.scale();
        float x = this.island.x();
        float y = this.island.y();
        float size7 = 7.0F * s;
        float letterSpacing = size7 * UiFontStyle.MEDIUM.letterSpacingEm();
        org.shizavisuals.utils.render.gui.MsdfFont font =
                org.shizavisuals.utils.render.gui.UiFonts.getFontForText(CLIENT_NAME, UiFontStyle.MEDIUM.weight());
        float textWidth = font.measureWidth(CLIENT_NAME, size7, letterSpacing) / s;
        this.size.width = 20.0F + textWidth;
        this.size.height = 15.0F;
        float anim = this.animation.getValue();

        int accent = Theme.getAccent();
        int light = ColorUtil.lerp(accent, ColorUtil.WHITE, 0.5F);
        int mid = ColorUtil.lerp(accent, ColorUtil.WHITE, 0.25F);

        Render2DUtil.rect(x + (-6.0F + 10.0F * anim) * s, y + 4.0F * s, 7.0F * s, 7.0F * s)
                .radius(3.0F * s)
                .color(
                        ColorUtil.multiplyAlpha(accent, alpha),
                        ColorUtil.multiplyAlpha(light, alpha),
                        ColorUtil.multiplyAlpha(light, alpha),
                        ColorUtil.multiplyAlpha(mid, alpha)
                )
                .draw();
        Render2DUtil.text(x + (25.0F - 10.0F * anim) * s, font.centeredTextY(y + 7.5F * s, size7), size7, CLIENT_NAME)
                .font(font)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, alpha))
                .draw();
    }

    @Override
    public boolean canShow() {
        return true;
    }
}
