package org.shizavisuals.menu.ui.rows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.menu.ui.controls.ColorComponent;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.utils.render.Theme;

public final class ColorRow extends SettingRow {
    private final ColorComponent color;

    public ColorRow(ColorSetting setting) {
        super(setting);
        this.color = new ColorComponent(setting::getValue, setting::setValue);
    }

    @Override
    protected void placeControl(Component owner) {
        this.color.place(owner, controlX(), controlY(), controlWidth(), controlHeight())
                .mouse(this.mouseX, this.mouseY)
                .alpha(this.rowAlpha);
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        this.host.closeOtherRows(this);
        return this.color.handleClick(mouseX, mouseY);
    }

    @Override
    public void closeTransient() {
        this.color.close();
    }

    @Override
    public void closeTransientImmediately() {
        this.color.closeImmediately();
    }

    @Override
    public boolean hasOpenPopup() {
        return this.color.isOpen();
    }

    @Override
    public boolean popupClick(int mouseX, int mouseY) {
        return this.color.handlePopupClick(mouseX, mouseY);
    }

    @Override
    public void renderPopupOverlay(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        this.color.renderOverlay();
    }

    @Override
    protected void renderControl(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        this.color.render(minecraft, guiGraphicsExtractor);
    }

    @Override
    protected int labelColor() {
        return Theme.Colors.TEXT_TEXT;
    }

    @Override
    protected int controlWidth() {
        return Theme.Sizes.COLOR_PREVIEW_WIDTH;
    }

    @Override
    protected int controlHeight() {
        return Theme.Sizes.COLOR_PREVIEW_HEIGHT;
    }
}
