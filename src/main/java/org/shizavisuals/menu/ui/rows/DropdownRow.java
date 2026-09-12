package org.shizavisuals.menu.ui.rows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.feature.setting.Setting;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.DropdownComponent;
import org.shizavisuals.menu.ui.controls.MarqueeText;
import org.shizavisuals.menu.ui.controls.ModeComponent;
import org.shizavisuals.menu.ui.controls.MultiSelectComponent;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DropdownRow extends SettingRow {
    private final DropdownComponent dropdown;
    private final Map<String, MarqueeText> optionTexts = new HashMap<>();
    private final Animation popupAnimation = new Animation(150L, Animation.Easing.EASE_OUT_QUAD);
    private final Animation optionFlashAnimation = new Animation(0L, Animation.Easing.EASE_OUT_QUAD);
    private String flashedOption;
    private boolean open;
    private boolean closing;

    public DropdownRow(ModeSetting setting) {
        super(setting);
        this.dropdown = new ModeComponent(() -> MenuText.option(setting.getValue()));
    }

    public DropdownRow(MultiSelectSetting setting) {
        super(setting);
        this.dropdown = new MultiSelectComponent(setting);
    }

    @Override
    protected void placeControl(Component owner) {
        this.dropdown.place(owner, controlX(), controlY(), controlWidth(), controlHeight())
                .color(dropdownColor())
                .warning(warningColor(this.setting))
                .alpha(this.rowAlpha);
    }

    @Override
    public boolean click(int mouseX, int mouseY) {
        this.host.closeOtherRows(this);
        if (this.dropdown.handleClick(mouseX, mouseY)) {
            toggle();
        }
        return true;
    }

    @Override
    public void closeTransient() {
        close();
    }

    @Override
    public void closeTransientImmediately() {
        this.open = false;
        this.closing = false;
        this.popupAnimation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    @Override
    public boolean hasOpenPopup() {
        return this.open;
    }

    @Override
    public boolean popupClick(int mouseX, int mouseY) {
        if (optionClick(mouseX, mouseY)) {
            return true;
        }
        if (this.dropdown.contains(mouseX, mouseY)) {
            close();
            return true;
        }
        if (hit(mouseX, mouseY, popupX(), popupY(), Theme.Sizes.DROPDOWN_POPUP_WIDTH, popupHeight())) {
            return true;
        }
        close();
        return false;
    }

    @Override
    public void renderPopupOverlay(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (!this.open && !this.closing) {
            return;
        }
        renderPopup();
        if (!this.open && this.popupAnimation.isFinished()) {
            this.closing = false;
        }
    }

    @Override
    protected void renderControl(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        this.dropdown.render(minecraft, guiGraphicsExtractor);
    }

    @Override
    protected boolean controlRendersWarning() {
        return true;
    }

    @Override
    protected int labelColor() {
        return Theme.Colors.TEXT_TEXT;
    }

    @Override
    protected int controlWidth() {
        return Theme.Sizes.DROPDOWN_VALUE_WIDTH;
    }

    @Override
    protected int controlHeight() {
        return Theme.Sizes.DROPDOWN_VALUE_HEIGHT;
    }

    private void toggle() {
        if (this.open) {
            close();
            return;
        }
        this.open = true;
        this.closing = false;
        this.popupAnimation.animate(0.0F, 1.0F, 150L, Animation.Easing.EASE_OUT_QUAD);
    }

    private void close() {
        if (this.open) {

            this.closing = true;
            this.popupAnimation.animate(this.popupAnimation.getValue(), 0.0F, 120L, Animation.Easing.EASE_OUT_QUAD);
        }
        this.open = false;
    }

    private List<String> options() {
        if (this.setting instanceof ModeSetting mode) {
            return mode.getModes();
        }
        if (this.setting instanceof MultiSelectSetting multi) {
            return multi.getOptions();
        }
        return List.of();
    }

    private boolean optionClick(int mouseX, int mouseY) {
        List<String> options = options();
        int itemX = popupX() + Theme.Sizes.DROPDOWN_POPUP_PADDING;
        int itemY = popupY() + Theme.Sizes.DROPDOWN_POPUP_PADDING;
        int itemWidth = Theme.Sizes.DROPDOWN_POPUP_WIDTH - Theme.Sizes.DROPDOWN_POPUP_PADDING * 2;
        int step = Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_GAP;
        for (int index = 0; index < options.size(); index++) {
            if (!hit(mouseX, mouseY, itemX, itemY + index * step, itemWidth, Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT)) {
                continue;
            }
            String option = options.get(index);
            if (this.setting instanceof ModeSetting mode) {
                mode.setValue(option);
                close();
            } else if (this.setting instanceof MultiSelectSetting multi) {
                multi.toggle(option);

                this.flashedOption = option;
                this.optionFlashAnimation.animate(1.0F, 0.0F, 250L, Animation.Easing.EASE_OUT_QUAD);
            }
            return true;
        }
        return false;
    }

    private int popupX() {
        return this.rowX + this.rowWidth - PADDING - Theme.Sizes.DROPDOWN_POPUP_WIDTH;
    }

    private int popupY() {
        if (opensUp()) {
            return controlY() - Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y - popupHeight();
        }
        return controlY() + Theme.Sizes.DROPDOWN_VALUE_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y;
    }

    private boolean opensUp() {
        int popupHeight = popupHeight();
        int belowY = controlY() + Theme.Sizes.DROPDOWN_VALUE_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y;
        if (belowY + popupHeight <= this.host.popupViewportMaxY()) {
            return false;
        }

        int aboveY = controlY() - Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y - popupHeight;
        if (aboveY >= Theme.Sizes.HEADER_HEIGHT) {
            return true;
        }

        int spaceAbove = controlY() - Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y - Theme.Sizes.HEADER_HEIGHT;
        int spaceBelow = this.host.popupViewportMaxY() - belowY;
        return spaceAbove > spaceBelow;
    }

    private int popupHeight() {
        int count = options().size();
        return Theme.Sizes.DROPDOWN_POPUP_PADDING * 2
                + count * Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT
                + Math.max(0, count - 1) * Theme.Sizes.DROPDOWN_POPUP_GAP;
    }

    private void renderPopup() {
        List<String> options = options();
        float progress = this.popupAnimation.getValue();
        if (progress <= 0.001F || options.isEmpty()) {
            return;
        }

        float slide = (opensUp() ? 6.0F : -6.0F) * (1.0F - progress);
        int popupX = popupX();
        float popupY = popupY() + slide;
        int popupHeight = popupHeight();
        int itemX = popupX + Theme.Sizes.DROPDOWN_POPUP_PADDING;
        float itemY = popupY + Theme.Sizes.DROPDOWN_POPUP_PADDING;
        int itemWidth = Theme.Sizes.DROPDOWN_POPUP_WIDTH - Theme.Sizes.DROPDOWN_POPUP_PADDING * 2;
        int step = Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_GAP;

        glassPanel(sx(popupX), sy(popupY), px(Theme.Sizes.DROPDOWN_POPUP_WIDTH), px(popupHeight),
                px(Theme.Sizes.DROPDOWN_POPUP_RADIUS), px(20.0F), this.rowAlpha * progress);

        for (int index = 0; index < options.size(); index++) {
            float optionY = itemY + index * step;
            String option = options.get(index);
            boolean selected = this.setting instanceof ModeSetting mode && mode.is(option)
                    || this.setting instanceof MultiSelectSetting multi && multi.isSelected(option);
            boolean hovered = hit(this.mouseX, this.mouseY, itemX, optionY, itemWidth, Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT);
            if (selected || hovered) {
                int background = selected
                        ? hovered ? Theme.Colors.OUTLINES_MEDIUM : Theme.Colors.OUTLINES_LARGE
                        : Theme.Colors.OUTLINES_SMALL;
                Render2DUtil.rect(sx(itemX), sy(optionY), px(itemWidth), px(Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT))
                        .color(alpha(background, this.rowAlpha * progress))
                        .radius(px(Theme.Sizes.DROPDOWN_POPUP_ITEM_RADIUS))
                        .draw();
            }
            float flash = option.equals(this.flashedOption) ? this.optionFlashAnimation.getValue() : 0.0F;
            if (flash > 0.001F) {
                Render2DUtil.rect(sx(itemX), sy(optionY), px(itemWidth), px(Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT))
                        .color(alpha(ColorUtil.withAlpha(0xFFFFFF, Math.round(45.0F * flash)), this.rowAlpha * progress))
                        .radius(px(Theme.Sizes.DROPDOWN_POPUP_ITEM_RADIUS))
                        .draw();
            }

            Integer optionWarning = warningColor(this.setting.warningLevel(option));
            int textX = itemX + 12;
            int textWidth = Theme.Sizes.DROPDOWN_POPUP_TEXT_WIDTH;
            if (optionWarning != null) {
                int iconX = itemX + 10;
                float iconY = optionY + (Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT - WARNING_ICON_SIZE) / 2.0F;
                texture(iconX, iconY, WARNING_ICON_SIZE, Textures.Icons.TRIANGLE_ALERT, optionWarning, this.rowAlpha * progress);
                int shiftedTextX = iconX + WARNING_ICON_SIZE + WARNING_ICON_GAP;
                textWidth = Math.max(20, textWidth - (shiftedTextX - textX));
                textX = shiftedTextX;
            }

            int selectedColor = optionWarning == null ? Theme.Colors.TEXT_TEXT : optionWarning;
            MarqueeText optionText = this.optionTexts.computeIfAbsent(
                    option,
                    ignored -> new MarqueeText(() -> MenuText.option(option))
            );
            optionText
                    .placeAt(
                            this,
                            sx(textX),
                            sy(optionY),
                            px(textWidth),
                            px(Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT),
                            this.mouseX,
                            this.mouseY
                    )
                    .style(
                            12.0F,
                            UiFontStyle.MEDIUM,
                            selected ? selectedColor : Theme.Colors.TEXT_TEXT,
                            this.rowAlpha * progress
                    )
                    .render(Minecraft.getInstance(), null);
        }
    }

    private int dropdownColor() {
        Integer warning = warningColor(this.setting);
        return warning == null ? Theme.Colors.ICON : warning;
    }
}
