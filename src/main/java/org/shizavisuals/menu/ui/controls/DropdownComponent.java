package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.function.Supplier;
import org.shizavisuals.utils.render.Textures;

public abstract class DropdownComponent extends Component {
    private static final int WARNING_ICON_SIZE = 12;
    private static final int WARNING_ICON_GAP = 5;

    private final Supplier<String> valueSupplier;
    private final MarqueeText valueText;
    private Style style = Style.VALUE;
    private int valueColor = Theme.Colors.ICON;
    private Integer warningColor;
    private float alpha = 1.0F;

    protected DropdownComponent(Supplier<String> valueSupplier) {
        this.valueSupplier = valueSupplier;
        this.valueText = new MarqueeText(this::displayValue);
    }

    public DropdownComponent place(Component owner, int x, int y, int width, int height) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        return this;
    }

    public DropdownComponent alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public DropdownComponent style(Style style) {
        this.style = style == null ? Style.VALUE : style;
        return this;
    }

    public DropdownComponent color(int color) {
        this.valueColor = color;
        return this;
    }

    public DropdownComponent warning(Integer color) {
        this.warningColor = color;
        return this;
    }

    public boolean handleClick(int mouseX, int mouseY) {
        return contains(mouseX, mouseY);
    }

    protected String displayValue() {
        return this.valueSupplier.get();
    }

    protected String value() {
        return this.valueSupplier.get();
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (this.style == Style.PILL) {
            renderPill();
            return;
        }
        renderValue(minecraft);
    }

    private void renderValue(Minecraft minecraft) {
        int color = ColorUtil.multiplyAlpha(this.valueColor, this.alpha);
        float gap = px(Theme.Sizes.DROPDOWN_GAP);
        float iconSize = px(Theme.Sizes.DROPDOWN_ICON_SIZE);
        float iconPadding = px(Theme.Sizes.DROPDOWN_ICON_PADDING);
        float iconX = x() + width() - iconSize + iconPadding;
        float textRight = x() + width() - iconSize - gap;
        float warningReserve = this.warningColor == null
                ? 0.0F
                : px(WARNING_ICON_SIZE + WARNING_ICON_GAP);
        float textLeft = x() + warningReserve;
        float maxTextWidth = Math.max(1.0F, textRight - textLeft);

        if (this.warningColor != null) {
            float warningSize = px(WARNING_ICON_SIZE);
            float warningX = x();
            float warningY = y() + (height() - warningSize) / 2 + 1;
            Render2DUtil.texture(warningX, warningY, warningSize, warningSize, Textures.Icons.TRIANGLE_ALERT)
                    .color(ColorUtil.multiplyAlpha(this.warningColor, this.alpha))
                    .draw();
        }

        Minecraft client = minecraft != null ? minecraft : Minecraft.getInstance();
        int mouseX = (int) Math.round(client.mouseHandler.getScaledXPos(client.getWindow()));
        int mouseY = (int) Math.round(client.mouseHandler.getScaledYPos(client.getWindow()));
        this.valueText
                .placeAt(this, textLeft, y(), maxTextWidth, height(), mouseX, mouseY)
                .style(12.0F, UiFontStyle.MEDIUM, this.valueColor, this.alpha)
                .align(TextAlign.RIGHT)
                .render(client, null);

        Render2DUtil.texture(iconX, y() + iconPadding,
                        Math.max(0, iconSize - iconPadding * 2), Math.max(0, iconSize - iconPadding * 2), Textures.Icons.CHEVRON_DOWN)
                .color(color)
                .draw();
    }

    private void renderPill() {

        float iconSize = px(12);
        float iconX = x() + width() - px(8) - iconSize;
        float textX = x() + px(16);
        float textSize = px(14.0F);
        float textY = UiFonts.sfProDisplay().centeredTextY(y() + height() / 2.0F, textSize);

        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, this.alpha))
                .radius(999)
                .draw();

        Render2DUtil.text(textX, textY, textSize, value())
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, this.alpha))
                .draw();

        Render2DUtil.texture(iconX, y() + (height() - iconSize) / 2, iconSize, iconSize, Textures.Icons.CHEVRONS_LEFT_RIGHT)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.ICON, this.alpha))
                .draw();
    }

    public enum Style {
        VALUE,
        PILL
    }

    public static int pillWidth(String value) {
        float textWidth = UiFonts.sfProDisplay().measureWidth(value == null ? "" : value, 14.0F, UiFontStyle.MEDIUM.letterSpacingEm() * 14.0F);
        return Math.max(48, Math.round(textWidth) + 44);
    }
}
