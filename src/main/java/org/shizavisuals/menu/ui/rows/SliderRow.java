package org.shizavisuals.menu.ui.rows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.SliderComponent;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

public final class SliderRow extends SettingRow {
    private final NumberSetting number;
    private final SliderComponent slider;

    public SliderRow(NumberSetting setting) {
        super(setting);
        this.number = setting;
        this.slider = new SliderComponent(
                () -> (float) setting.getProgress(),
                progress -> setting.setValue(setting.getMin() + progress * (setting.getMax() - setting.getMin())),
                Theme.Colors.OUTLINES_SMALL,
                Theme.getAccent(),
                ColorUtil.WHITE
        ).step((float) (setting.getStep() / (setting.getMax() - setting.getMin())));
    }

    @Override
    protected void placeControl(Component owner) {
        this.slider.place(owner, controlX(), controlY(), controlWidth(), controlHeight())
                .style(Theme.Colors.OUTLINES_SMALL, controlAccent(this.setting), ColorUtil.WHITE)
                .alpha(this.rowAlpha);
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        this.host.closeOtherRows(this);
        return this.slider.handleClick(mouseX, mouseY);
    }

    @Override
    public void drag(int mouseX) {
        this.slider.drag(mouseX);
    }

    @Override
    public boolean scroll(int mouseX, int mouseY, double vertical) {
        return this.slider.handleScroll(mouseX, mouseY, vertical);
    }

    @Override
    public void releasePointer() {
        this.slider.releasePointer();
    }

    @Override
    protected void renderExtras(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        float fontSize = 12.0F;
        float textY = centeredTextY(this.rowY + ROW_HEIGHT / 2.0F, fontSize);
        Integer warning = warningColor(this.setting);
        int valueRight = warning == null ? controlX() - 5 : warningIconX() - WARNING_ICON_GAP;
        int color = warning == null ? Theme.Colors.SECONDARY_DARK : warning;
        textRight(valueRight, textY, fontSize, MenuText.numberValue(this.number),
                color, this.rowAlpha, UiFontStyle.REGULAR_TRACKED);
    }

    @Override
    protected void renderControl(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        this.slider.render(minecraft, guiGraphicsExtractor);
    }

    @Override
    protected int controlWidth() {
        return Theme.Sizes.MODULE_CARD_SLIDER_WIDTH;
    }

    @Override
    protected int controlHeight() {
        return Theme.Sizes.MODULE_CARD_SLIDER_HEIGHT;
    }

    @Override
    protected float labelRight() {
        float fontSize = 12.0F;
        String value = MenuText.numberValue(this.number);
        float valueWidth = UiFonts.sfProDisplay().measureWidth(
                value,
                fontSize,
                fontSize * UiFontStyle.REGULAR_TRACKED.letterSpacingEm()
        );
        Integer warning = warningColor(this.setting);
        int valueRight = warning == null
                ? controlX() - 5
                : warningIconX() - WARNING_ICON_GAP;
        return valueRight - valueWidth - 8.0F;
    }
}
