package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.function.Supplier;
import org.shizavisuals.utils.render.Textures;

public final class SearchInputComponent extends Component {
    private static final String PLACEHOLDER = "Start typing to search...";
    private static final int PADDING_X = 16;
    private static final int ICON_SIZE = 16;
    private static final int GAP = 10;
    private static final int TEXT_SIZE = 12;

    private final Supplier<String> querySupplier;
    private final Supplier<String> suggestionSupplier;
    private int mouseX;
    private int mouseY;
    private boolean focused;
    private boolean selected;
    private float alpha = 1.0F;

    private float smoothCaretX = -1.0F;
    private float smoothDrawX = -1.0F;
    private float placeholderAlpha = 1.0F;
    private long lastFrameNanos = System.nanoTime();

    public SearchInputComponent(Supplier<String> querySupplier, Supplier<String> suggestionSupplier) {
        this.querySupplier = querySupplier;
        this.suggestionSupplier = suggestionSupplier;
    }

    public SearchInputComponent place(Component owner, int x, int y, int width, int height, int mouseX, int mouseY) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }

    public SearchInputComponent alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public SearchInputComponent focused(boolean focused) {
        this.focused = focused;
        return this;
    }

    public SearchInputComponent selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        long nowNanos = System.nanoTime();
        float seconds = Math.min(0.05F, (nowNanos - this.lastFrameNanos) / 1_000_000_000.0F);
        this.lastFrameNanos = nowNanos;
        float dtFactor = 1.0F - (float) Math.exp(-seconds * 24.0F);

        String query = this.querySupplier.get();
        if (query == null) {
            query = "";
        }
        String suggestion = this.suggestionSupplier.get();
        if (suggestion == null || query.isEmpty()) {
            suggestion = "";
        }

        boolean hasInput = !query.isEmpty();
        boolean active = this.focused || (!hasInput && contains(this.mouseX, this.mouseY));
        int background = active ? Theme.Colors.OUTLINES_LARGE : Theme.Colors.OUTLINES_MEDIUM;
        int border = active ? Theme.Colors.OUTLINES_LARGE : Theme.Colors.OUTLINES_SMALL;
        UiFontStyle textStyle = hasInput ? UiFontStyle.MEDIUM : UiFontStyle.REGULAR;
        float textSize = px((float) TEXT_SIZE);
        float letterSpacing = textStyle.letterSpacingEm() * textSize;
        float textX = x() + px(PADDING_X + ICON_SIZE + GAP);
        float maxTextWidth = Math.max(1, width() - px(PADDING_X * 2 + ICON_SIZE + GAP));

        float targetPlaceholderAlpha = (hasInput || this.focused) ? 0.0F : 1.0F;
        this.placeholderAlpha += (targetPlaceholderAlpha - this.placeholderAlpha) * dtFactor;

        float queryWidth = hasInput ? UiFonts.sfProDisplay().measureWidth(query, textSize, letterSpacing) : 0.0F;
        float suggestionWidth = suggestion.isEmpty() ? 0.0F : UiFonts.sfProDisplay().measureWidth(suggestion, textSize, letterSpacing);
        float joinSpacing = hasInput && !suggestion.isEmpty() ? letterSpacing : 0.0F;
        float visualTextWidth = queryWidth + joinSpacing + suggestionWidth;
        float targetDrawX = visualTextWidth > maxTextWidth ? textX - (visualTextWidth - maxTextWidth) : textX;

        if (this.smoothDrawX < 0.0F) {
            this.smoothDrawX = targetDrawX;
        } else {
            this.smoothDrawX += (targetDrawX - this.smoothDrawX) * dtFactor;
        }

        float drawX = this.smoothDrawX;
        float iconY = y() + (height() - px(ICON_SIZE)) / 2;
        float textY = UiFonts.sfProDisplay().centeredTextY(y() + height() / 2.0F, textSize);

        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.multiplyAlpha(background, this.alpha))
                .radius(Math.max(1, height() / 2))
                .border(Math.max(0.5F, px(0.5F)), ColorUtil.multiplyAlpha(border, this.alpha))
                .draw();

        Render2DUtil.texture(x() + px(PADDING_X), iconY, px(ICON_SIZE), px(ICON_SIZE), Textures.Header.SEARCH)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.ICON, this.alpha))
                .draw();

        Render2DUtil.pushScissor(textX, y() + px(12), maxTextWidth, Math.max(1, height() - px(24)));

        if (this.placeholderAlpha > 0.01F) {
            float placeholderOffset = (1.0F - this.placeholderAlpha) * px(-6.0F);
            Render2DUtil.text(textX + placeholderOffset, textY, textSize, PLACEHOLDER)
                    .style(UiFontStyle.REGULAR)
                    .color(ColorUtil.multiplyAlpha(Theme.Colors.ICON, this.alpha * this.placeholderAlpha))
                    .draw();
        }

        if (this.focused && this.selected && hasInput) {
            Render2DUtil.rect(
                            drawX,
                            y() + px(12),
                            queryWidth,
                            Math.max(1, height() - px(24))
                    )
                    .color(ColorUtil.multiplyAlpha(Theme.getAccent(), this.alpha * 0.55F))
                    .radius(px(2.0F))
                    .draw();
        }

        if (hasInput) {
            Render2DUtil.text(drawX, textY, textSize, query)
                    .style(textStyle)
                    .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, this.alpha))
                    .draw();
        }

        if (!suggestion.isEmpty()) {
            Render2DUtil.text(drawX + queryWidth + joinSpacing, textY, textSize, suggestion)
                    .style(textStyle)
                    .color(ColorUtil.multiplyAlpha(Theme.Colors.ICON, this.alpha * 0.7F))
                    .draw();
        }

        if (this.focused && !this.selected) {
            float targetCaretX = drawX + queryWidth + px(2);
            if (this.smoothCaretX < 0.0F) {
                this.smoothCaretX = targetCaretX;
            } else {
                this.smoothCaretX += (targetCaretX - this.smoothCaretX) * dtFactor;
            }

            float blinkPhase = (System.currentTimeMillis() % 1000L) / 1000.0F;
            float caretAlphaMult = 0.5F + 0.5F * (float) Math.cos(blinkPhase * Math.PI * 2.0D);
            if (caretAlphaMult > 0.05F) {
                Render2DUtil.rect(this.smoothCaretX, y() + px(16), Math.max(1, px(1.5F)), px(16))
                        .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, this.alpha * caretAlphaMult))
                        .draw();
            }
        } else {
            this.smoothCaretX = -1.0F;
        }

        Render2DUtil.popScissor();
    }
}
