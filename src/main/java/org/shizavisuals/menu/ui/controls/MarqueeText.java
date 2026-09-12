package org.shizavisuals.menu.ui.controls;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.function.Supplier;

public final class MarqueeText extends Component {
    private static final int FADE_STEPS = 6;
    private static final long HOVER_DELAY_NANOS = 350_000_000L;
    private static final float SPEED_DESIGN_PX = 28.0F;
    private static final float STATIC_FADE_DESIGN_PX = 24.0F;
    private static final float SCROLL_FADE_DESIGN_PX = 12.0F;
    private static final float LOOP_GAP_DESIGN_PX = 28.0F;

    private final Supplier<String> textSupplier;
    private float textSize = 12.0F;
    private UiFontStyle style = UiFontStyle.MEDIUM;
    private TextAlign align = TextAlign.LEFT;
    private int color = ColorUtil.WHITE;
    private float alpha = 1.0F;
    private int mouseX;
    private int mouseY;
    private boolean hoveredLastFrame;
    private long hoverStartNanos;

    public MarqueeText(Supplier<String> textSupplier) {
        this.textSupplier = textSupplier;
    }

    public MarqueeText place(Component owner, float x, float y, float width, float height,
                             int mouseX, int mouseY) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }

    public MarqueeText placeAt(Component owner, float x, float y, float width, float height,
                               int mouseX, int mouseY) {
        attach(owner, x, y, width, height);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }

    public MarqueeText style(float textSize, UiFontStyle style, int color, float alpha) {
        this.textSize = textSize;
        this.style = style;
        this.color = color;
        this.alpha = alpha;
        return this;
    }

    public MarqueeText align(TextAlign align) {
        this.align = align == null ? TextAlign.LEFT : align;
        return this;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        String text = this.textSupplier.get();
        if (text == null || text.isEmpty() || width() <= 0.5F) {
            resetHover();
            return;
        }

        MsdfFont font = UiFonts.sfProDisplay();
        float size = px(this.textSize);
        float spacing = size * this.style.letterSpacingEm();
        float textWidth = font.measureWidth(text, size, spacing);
        float textY = font.centeredTextY(y() + height() / 2.0F, size);
        if (textWidth <= width()) {
            resetHover();
            float textX = switch (this.align) {
                case CENTER -> x() + (width() - textWidth) / 2.0F;
                case RIGHT -> x() + width() - textWidth;
                default -> x();
            };
            drawText(text, textX, textY, size, this.alpha);
            return;
        }

        boolean hovered = this.mouseX >= x() && this.mouseX <= x() + width()
                && this.mouseY >= y() && this.mouseY <= y() + height();
        long now = System.nanoTime();
        if (hovered && !this.hoveredLastFrame) {
            this.hoverStartNanos = now;
        } else if (!hovered) {
            this.hoverStartNanos = 0L;
        }
        this.hoveredLastFrame = hovered;

        long scrollingNanos = hovered ? now - this.hoverStartNanos - HOVER_DELAY_NANOS : -1L;
        if (scrollingNanos <= 0L) {
            drawWithEdgeFades(text, x(), textY, size, textWidth, false);
            return;
        }

        float gap = px(LOOP_GAP_DESIGN_PX);
        float cycle = textWidth + gap;
        float offset = (float) ((scrollingNanos / 1_000_000_000.0D)
                * px(SPEED_DESIGN_PX) % cycle);
        drawWithEdgeFades(text, x() - offset, textY, size, textWidth, true);
    }

    private void drawWithEdgeFades(String text, float textX, float textY,
                                   float size, float textWidth, boolean scrolling) {
        float fade = Math.min(
                px(scrolling ? SCROLL_FADE_DESIGN_PX : STATIC_FADE_DESIGN_PX),
                width() / 3.0F
        );
        float leftFade = scrolling ? fade : 0.0F;
        float rightFade = fade;

        drawStrip(text, textX, textY, size,
                x() + leftFade, x() + width() - rightFade, this.alpha, textWidth, scrolling);

        if (scrolling) {
            for (int step = 0; step < FADE_STEPS; step++) {
                float x0 = x() + leftFade * step / FADE_STEPS;
                float x1 = x() + leftFade * (step + 1) / FADE_STEPS;
                float stripAlpha = this.alpha * (step + 0.5F) / FADE_STEPS;
                drawStrip(text, textX, textY, size, x0, x1, stripAlpha, textWidth, true);
            }
        }

        for (int step = 0; step < FADE_STEPS; step++) {
            float x0 = x() + width() - rightFade + rightFade * step / FADE_STEPS;
            float x1 = x() + width() - rightFade + rightFade * (step + 1) / FADE_STEPS;
            float stripAlpha = this.alpha * (1.0F - (step + 0.5F) / FADE_STEPS);
            drawStrip(text, textX, textY, size, x0, x1, stripAlpha, textWidth, scrolling);
        }
    }

    private void drawStrip(String text, float textX, float textY, float size,
                           float clipLeft, float clipRight, float stripAlpha,
                           float textWidth, boolean duplicate) {
        float clipWidth = clipRight - clipLeft;
        if (clipWidth <= 0.0F || stripAlpha <= 0.001F) {
            return;
        }
        Render2DUtil.pushScissor(clipLeft, y(), clipWidth, height());
        drawText(text, textX, textY, size, stripAlpha);
        if (duplicate) {
            drawText(text, textX + textWidth + px(LOOP_GAP_DESIGN_PX),
                    textY, size, stripAlpha);
        }
        Render2DUtil.popScissor();
    }

    private void drawText(String text, float textX, float textY, float size, float drawAlpha) {
        Render2DUtil.text(textX, textY, size, text)
                .style(this.style)
                .color(ColorUtil.multiplyAlpha(this.color, drawAlpha))
                .draw();
    }

    private void resetHover() {
        this.hoveredLastFrame = false;
        this.hoverStartNanos = 0L;
    }
}
