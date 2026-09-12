package org.shizavisuals.hud.island;

import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

public class TimerStatus extends IslandStatus {
    private String prefix = "";
    private String suffix = "";
    private int time = -1;
    private String text = "text";
    private int color = ColorUtil.WHITE;
    protected AnimatedNumber timeAnim;

    public TimerStatus(DynamicIsland island, String name) {
        super(island, name);
    }

    @Override
    public void draw(float alpha) {
        float s = this.island.scale();
        float x = this.island.x();
        float y = this.island.y();
        MsdfFont timeFont = UiFonts.googleSansFlex(UiFontStyle.MEDIUM.weight());
        if (this.timeAnim == null) {
            this.timeAnim = new AnimatedNumber(5.0F, 6.0F, 500L, IslandEasing.BAKEK);
        }
        float size6 = 6.0F * s;
        float size7 = 7.0F * s;
        float ls6 = spacing(size6);
        float ls7 = spacing(size7);
        float anim = this.animation.getValue();
        String prefixSuffix = this.prefix + this.suffix;
        float timeWidth = this.timeAnim.getWidth(size6, ls6) / s;
        if (!prefixSuffix.isEmpty()) {
            timeWidth += timeFont.measureWidth(prefixSuffix, size6, ls6) / s;
        }
        float pxTimeWidth = timeWidth * s;
        float textWidth = font(this.text, UiFontStyle.MEDIUM).measureWidth(this.text, size7, ls7) / s;
        this.size.width = 17.0F + textWidth + timeWidth;
        this.size.height = 15.0F;
        int colorAlpha = ColorUtil.multiplyAlpha(this.color, alpha * anim);
        Render2DUtil.rect(x + (-16.0F + 20.0F * anim) * s, y + 3.5F * s,
                        (5.5F + timeWidth) * s, 8.0F * s)
                .radius(3.0F * s)
                .color(colorAlpha)
                .draw();
        float digitsX = x + (-13.0F + 20.0F * anim) * s;
        float textY = y + 5.5F * s;
        int prefixColor = ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha * anim);
        if (!this.prefix.isEmpty()) {
            Render2DUtil.text(digitsX, textY, size6, this.prefix)
                    .font(timeFont)
                    .color(prefixColor)
                    .draw();
        }
        float prefixWidth = this.prefix.isEmpty() ? 0.0F : timeFont.measureWidth(this.prefix, size6, ls6);
        this.timeAnim.update(this.time);
        this.timeAnim.settings(true, ColorUtil.WHITE);
        this.timeAnim.render(digitsX + prefixWidth, textY, size6, ls6, alpha * anim);
        if (!this.suffix.isEmpty()) {
            Render2DUtil.text(
                            digitsX + prefixWidth + this.timeAnim.getWidth(size6, ls6),
                            textY, size6, this.suffix)
                    .font(timeFont)
                    .color(prefixColor)
                    .draw();
        }
        Render2DUtil.text(x + (23.0F - 10.0F * anim) * s + pxTimeWidth, textY, size7, this.text)
                .font(font(this.text, UiFontStyle.MEDIUM))
                .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, alpha * anim))
                .draw();
    }

    public void update(String suffix, int time, String text, int color) {
        this.update("", suffix, time, text, color);
    }

    public void update(String prefix, String suffix, int time, String text, int color) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.time = time;
        this.text = text;
        this.color = color;
    }

    @Override
    public boolean canShow() {
        return false;
    }
}
