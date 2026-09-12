package org.shizavisuals.menu.ui.rows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.feature.setting.ButtonSetting;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.ButtonComponent;
import org.shizavisuals.utils.render.Theme;

public final class ButtonRow extends SettingRow {
    private final ButtonComponent button;

    public ButtonRow(ButtonSetting setting) {
        super(setting);
        this.button = new ButtonComponent(setting.getButtonLabel(), setting::press);
    }

    @Override
    protected void placeControl(Component owner) {
        this.button.place(
                        owner,
                        controlX(),
                        controlY(),
                        controlWidth(),
                        controlHeight(),
                        this.mouseX,
                        this.mouseY
                )
                .alpha(this.rowAlpha);
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        this.host.closeOtherRows(this);
        return this.button.handleClick(mouseX, mouseY);
    }

    @Override
    protected void renderControl(
            Minecraft minecraft,
            GuiGraphicsExtractor guiGraphicsExtractor
    ) {
        this.button.render(minecraft, guiGraphicsExtractor);
    }

    @Override
    protected int labelColor() {
        return Theme.Colors.TEXT_TEXT;
    }

    @Override
    protected int controlWidth() {
        return Theme.Sizes.INPUT_WIDTH;
    }

    @Override
    protected int controlHeight() {
        return Theme.Sizes.INPUT_HEIGHT;
    }
}
