package org.shizavisuals.menu.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.shizavisuals.utils.render.gui.UiFontStyle;

public abstract class Component {
    private static final int DESIGN_WIDTH = 1024;

    private Component frame;
    private double designScale = 1.0D;
    private float x;
    private float y;
    private float width;
    private float height;

    public final void configureFrame(float x, float y, float width, float height) {
        this.frame = this;
        this.designScale = width / (double) DESIGN_WIDTH;
        setBounds(x, y, width, height);
    }

    public final void attach(Component frame, float x, float y, float width, float height) {
        this.frame = frame;
        this.designScale = frame.designScale;
        setBounds(x, y, width, height);
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

    public final float px(float designPixels) {
        return (float) (designPixels * this.designScale);
    }

    protected final Component frame() {
        return this.frame;
    }

    public final float sx(float designX) {
        return this.frame.x + px(designX);
    }

    public final float sy(float designY) {
        return this.frame.y + px(designY);
    }

    public final float designX(float screenX) {
        return (float) ((screenX - this.frame.x) / this.designScale);
    }

    public final float designY(float screenY) {
        return (float) ((screenY - this.frame.y) / this.designScale);
    }

    public final boolean hit(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= sx(x) && mouseX <= sx(x + width) && mouseY >= sy(y) && mouseY <= sy(y + height);
    }

    public boolean contains(float mouseX, float mouseY) {
        if (Render2DUtil.isPointScissored(mouseX, mouseY)) {
            return false;
        }
        return mouseX >= this.x
                && mouseX <= this.x + this.width
                && mouseY >= this.y
                && mouseY <= this.y + this.height;
    }

    public boolean isDragHandle(float mouseX, float mouseY) {
        return false;
    }

    public boolean handleClick(int mouseX, int mouseY) {
        return false;
    }

    protected static Identifier menuTexturePng(String path) {
        return Identifier.parse("shizavisuals:textures/menu/" + path + ".png");
    }

    protected final int alpha(int color, float alpha) {
        return alpha >= 0.999F ? color : ColorUtil.multiplyAlpha(color, alpha);
    }

    protected final void rect(float x, float y, float width, float height, int color, float radius) {
        Render2DUtil.rect(sx(x), sy(y), px(width), px(height)).color(color).radius(px(radius)).draw();
    }

    protected final void rect(float x, float y, float width, float height, int color, float radius, float alpha) {
        rect(x, y, width, height, alpha(color, alpha), radius);
    }

    protected final void outline(float x, float y, float width, float height, int color, float radius, float thickness) {
        Render2DUtil.rect(sx(x), sy(y), px(width), px(height))
                .color(ColorUtil.TRANSPARENT)
                .radius(px(radius))
                .border(Math.max(0.5F, px(thickness)), color)
                .draw();
    }

    protected final void outline(float x, float y, float width, float height, int color, float radius, float thickness, float alpha) {
        outline(x, y, width, height, alpha(color, alpha), radius, thickness);
    }

    protected final void glassPanel(float x, float y, float width, float height, float radius, float shadowBlur, float alpha) {
        Render2DUtil.rect(x, y, width, height)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.BACKGROUND_PRIMARY_50, alpha))
                .blur(px(8.0F), alpha)
                .radius(radius)
                .shadow(ColorUtil.multiplyAlpha(Theme.Colors.POPUP_SHADOW, alpha), shadowBlur)
                .draw();
    }

    protected final float centeredTextY(float centerY, float size) {
        return UiFonts.sfProDisplay().centeredTextY(centerY, size);
    }

    protected final void text(float x, float y, float size, String value, int color) {
        Render2DUtil.text(sx(x), sy(y), px(size), value).color(color).draw();
    }

    protected final void text(float x, float y, float size, String value, int color, float alpha) {
        text(x, y, size, value, alpha(color, alpha));
    }

    protected final void text(float x, float y, float size, String value, int color, float alpha, UiFontStyle style) {
        Render2DUtil.text(sx(x), sy(y), px(size), value).style(style).color(alpha(color, alpha)).draw();
    }

    protected final void text(float x, float y, float size, String value, int color, UiFontStyle style) {
        text(x, y, size, value, color, 1.0F, style);
    }

    protected final void textCentered(float x, float y, float size, String value, int color) {
        Render2DUtil.text(sx(x), sy(y), px(size), value).color(color).align(TextAlign.CENTER).draw();
    }

    protected final void textCentered(float x, float y, float size, String value, int color, float alpha) {
        textCentered(x, y, size, value, alpha(color, alpha));
    }

    protected final void textCenteredScaled(float x, float y, float size, String value, int color, float alpha, float scale) {
        Render2DUtil.text(sx(x), sy(y), px(size), value).color(alpha(color, alpha)).scale(scale).align(TextAlign.CENTER).draw();
    }

    protected final void textScaled(float x, float y, float size, String value, int color, float alpha, float scale) {
        Render2DUtil.text(sx(x), sy(y), px(size), value).color(alpha(color, alpha)).scale(scale).draw();
    }

    protected final void textCentered(float x, float y, float size, String value, int color, float alpha, UiFontStyle style) {
        Render2DUtil.text(sx(x), sy(y), px(size), value).style(style).color(alpha(color, alpha)).align(TextAlign.CENTER).draw();
    }

    protected final void textRight(float x, float y, float size, String value, int color) {
        Render2DUtil.text(sx(x), sy(y), px(size), value).color(color).align(TextAlign.RIGHT).draw();
    }

    protected final void textRight(float x, float y, float size, String value, int color, float alpha) {
        textRight(x, y, size, value, alpha(color, alpha));
    }

    protected final void textRight(float x, float y, float size, String value, int color, float alpha, UiFontStyle style) {
        Render2DUtil.text(sx(x), sy(y), px(size), value).style(style).color(alpha(color, alpha)).align(TextAlign.RIGHT).draw();
    }

    protected final void texture(float x, float y, float width, float height, Identifier texture, int color) {
        Render2DUtil.texture(sx(x), sy(y), px(width), px(height), texture).color(color).draw();
    }

    protected final void texture(float x, float y, float width, float height, Identifier texture, int color, float alpha) {
        texture(x, y, width, height, texture, alpha(color, alpha));
    }

    protected final void texture(float x, float y, float size, Identifier texture, int color) {
        texture(x, y, size, size, texture, color);
    }

    protected final void texture(float x, float y, float size, Identifier texture, int color, float alpha) {
        texture(x, y, size, size, texture, color, alpha);
    }

    private void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0.0F, width);
        this.height = Math.max(0.0F, height);
    }

    public abstract void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor);
}
