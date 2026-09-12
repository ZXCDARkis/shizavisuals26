package org.shizavisuals.hud.island;

import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFonts;

public final class AnimatedNumber {
    private boolean drawZero;
    private int color = ColorUtil.WHITE;
    private final float offsetUnits;
    private final float baseFontSize;
    private final MsdfFont font = UiFonts.googleSansFlex(500);
    private final String[] numbers = new String[]{"", ""};
    private final String[] prevs = new String[]{"", ""};
    private final IslandAnimation[] animations;
    private float lastOffset;
    private float renderSize;
    private float renderSpacing;

    public AnimatedNumber(float offsetUnits, float baseFontSize, long speed, IslandEasing easing) {
        this.offsetUnits = offsetUnits;
        this.baseFontSize = baseFontSize;
        this.animations = new IslandAnimation[2];
        for (int i = 0; i < this.animations.length; i++) {
            this.animations[i] = new IslandAnimation(speed, easing);
        }
    }

    public void render(float x, float y, float size, float letterSpacing, float alpha) {
        this.renderSize = size;
        this.renderSpacing = letterSpacing;
        float offset = this.offsetUnits / this.baseFontSize * size;
        for (IslandAnimation animation : this.animations) {
            animation.update(1.0F);
        }
        Render2DUtil.text(x, y + offset * this.animations[0].getValue(), size, this.prevs[0])
                .font(this.font)
                .color(ColorUtil.multiplyAlpha(this.color, alpha * (1.0F - this.animations[0].getValue())))
                .draw();
        Render2DUtil.text(x, y - offset + offset * this.animations[0].getValue(), size, this.numbers[0])
                .font(this.font)
                .color(ColorUtil.multiplyAlpha(this.color, alpha * this.animations[0].getValue()))
                .draw();
        Render2DUtil.text(x + this.lastOffset, y + offset * this.animations[1].getValue(), size, this.prevs[1])
                .font(this.font)
                .color(ColorUtil.multiplyAlpha(this.color, alpha * (1.0F - this.animations[1].getValue())))
                .draw();
        Render2DUtil.text(x + this.font.measureWidth(this.numbers[0], size, letterSpacing),
                y - offset + offset * this.animations[1].getValue(), size, this.numbers[1])
                .font(this.font)
                .color(ColorUtil.multiplyAlpha(this.color, alpha * this.animations[1].getValue()))
                .draw();
    }

    public float getWidth(float size, float letterSpacing) {
        return this.font.measureWidth(this.numbers[0] + this.numbers[1], size, letterSpacing);
    }

    public void update(int updated) {
        String first = String.valueOf(updated / 10);
        String second = String.valueOf(updated % 10);
        if (!second.equals(this.numbers[1])) {
            this.lastOffset = this.font.measureWidth(this.numbers[0], this.renderSize, this.renderSpacing);
            this.prevs[1] = this.numbers[1];
            this.numbers[1] = second;
            this.animations[1].setValue(0.0F);
        }
        if (!first.equals(this.numbers[0])) {
            this.prevs[0] = this.numbers[0];
            this.numbers[0] = this.drawZero ? first : (first.equals("0") ? "" : first);
            this.animations[0].setValue(0.0F);
        }
    }

    public void settings(boolean drawZero, int color) {
        this.drawZero = drawZero;
        this.color = color;
    }
}
