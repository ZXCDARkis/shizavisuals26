package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.UiFontStyle;

public final class PillButton extends Component {
    private final Runnable action;
    private String label;
    private float textSize = 11.0F;
    private int idleBackground = Theme.Colors.CONTROL;
    private int hoverBackground = Theme.Colors.SURFACE_HOVER;
    private int textColor = Theme.Colors.SECONDARY;
    private float alpha = 1.0F;
    private int mouseX;
    private int mouseY;

    public PillButton(String label, Runnable action) {
        this.label = label;
        this.action = action == null ? () -> {
        } : action;
    }

    public PillButton place(Component owner, float x, float y, float width, float height, int mouseX, int mouseY) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }

    public PillButton label(String label) {
        this.label = label;
        return this;
    }

    public PillButton textSize(float textSize) {
        this.textSize = textSize;
        return this;
    }

    public PillButton colors(int idleBackground, int hoverBackground, int textColor) {
        this.idleBackground = idleBackground;
        this.hoverBackground = hoverBackground;
        this.textColor = textColor;
        return this;
    }

    public PillButton alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        this.action.run();
        return true;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        boolean hovered = contains(this.mouseX, this.mouseY);
        float designX = designX(x());
        float designY = designY(y());
        float designWidth = designX(x() + width()) - designX;
        float designHeight = designY(y() + height()) - designY;
        rect(designX, designY, designWidth, designHeight,
                hovered ? this.hoverBackground : this.idleBackground, 999, this.alpha);
        textCentered(designX + designWidth / 2.0F,
                centeredTextY(designY + designHeight / 2.0F, this.textSize), this.textSize,
                MenuText.ui(this.label), this.textColor, this.alpha, UiFontStyle.MEDIUM);
    }
}
