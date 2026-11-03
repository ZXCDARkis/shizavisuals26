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
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_GAP = 2;
    private static final float WHEEL_STEP = 18.0F;

    private final DropdownComponent dropdown;
    private final Map<String, MarqueeText> optionTexts = new HashMap<>();
    private final Animation popupAnimation = new Animation(150L, Animation.Easing.EASE_OUT_QUAD);
    private final Animation optionFlashAnimation = new Animation(0L, Animation.Easing.EASE_OUT_QUAD);
    private String flashedOption;
    private boolean open;
    private boolean closing;
    private float scrollOffset;

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
        this.scrollOffset = 0.0F;
        this.popupAnimation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    @Override
    public boolean hasOpenPopup() {
        return this.open;
    }

    @Override
    public boolean scroll(int mouseX, int mouseY, double vertical) {
        if (!this.open) {
            return false;
        }
        if (!hit(mouseX, mouseY, popupX(), popupY(), popupWidth(), visibleHeight())) {
            return false;
        }
        this.scrollOffset = clampScroll(this.scrollOffset - (float) vertical * WHEEL_STEP);
        return true;
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
        if (hit(mouseX, mouseY, popupX(), popupY(), popupWidth(), visibleHeight())) {
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
        renderPopup(guiGraphicsExtractor);
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
        this.scrollOffset = clampScroll(0.0F);
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
        if (options.isEmpty()) {
            return false;
        }
        int itemX = itemX();
        float itemY = firstItemY();
        int itemWidth = itemWidth();
        int step = Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_GAP;
        int first = firstVisibleIndex();
        int last = lastVisibleIndex();
        for (int index = first; index <= last && index < options.size(); index++) {
            if (!hit(mouseX, mouseY, itemX, itemY + (index - first) * step, itemWidth,
                    Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT)) {
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
            return controlY() - Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y - visibleHeight();
        }
        return controlY() + Theme.Sizes.DROPDOWN_VALUE_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y;
    }

    /** Popup width shrinks by the scrollbar only when the list actually overflows. */
    private int popupWidth() {
        return Theme.Sizes.DROPDOWN_POPUP_WIDTH;
    }

    private boolean opensUp() {
        int popupHeight = visibleHeight();
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

    private int contentHeight() {
        int count = options().size();
        return Theme.Sizes.DROPDOWN_POPUP_PADDING * 2
                + count * Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT
                + Math.max(0, count - 1) * Theme.Sizes.DROPDOWN_POPUP_GAP;
    }

    /**
     * Popup height is capped by the space between the header and the bottom of the
     * card viewport, so a 119-entry dropdown (ItemReplacer) can never grow past the
     * screen and swallow clicks meant for the rest of the menu.
     */
    private int maxVisibleHeight() {
        int anchor = controlY() + Theme.Sizes.DROPDOWN_VALUE_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y;
        int below = this.host.popupViewportMaxY() - anchor;
        int above = controlY() - Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y - Theme.Sizes.HEADER_HEIGHT;
        int room = Math.max(below, above);
        return Math.max(
                Theme.Sizes.DROPDOWN_POPUP_PADDING * 2 + Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT,
                room
        );
    }

    private int visibleHeight() {
        return Math.min(contentHeight(), maxVisibleHeight());
    }

    private boolean scrollable() {
        return contentHeight() > visibleHeight();
    }

    private int itemWidth() {
        return popupWidth() - Theme.Sizes.DROPDOWN_POPUP_PADDING * 2
                - (scrollable() ? SCROLLBAR_WIDTH + SCROLLBAR_GAP : 0);
    }

    private int itemX() {
        return popupX() + Theme.Sizes.DROPDOWN_POPUP_PADDING;
    }

    private float firstItemY() {
        return popupY() + Theme.Sizes.DROPDOWN_POPUP_PADDING - this.scrollOffset;
    }

    private float maxScroll() {
        return Math.max(0.0F, contentHeight() - visibleHeight());
    }

    private float clampScroll(float value) {
        return Math.max(0.0F, Math.min(value, maxScroll()));
    }

    private int step() {
        return Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_GAP;
    }

    private int firstVisibleIndex() {
        int step = step();
        return Math.max(0, (int) Math.floor((this.scrollOffset - Theme.Sizes.DROPDOWN_POPUP_PADDING) / step));
    }

    private int lastVisibleIndex() {
        int step = step();
        float bottom = this.scrollOffset + visibleHeight();
        return (int) Math.ceil((bottom - Theme.Sizes.DROPDOWN_POPUP_PADDING) / step);
    }

    private void renderPopup(GuiGraphicsExtractor guiGraphicsExtractor) {
        List<String> options = options();
        float progress = this.popupAnimation.getValue();
        if (progress <= 0.001F || options.isEmpty()) {
            return;
        }

        float slide = (opensUp() ? 6.0F : -6.0F) * (1.0F - progress);
        int popupX = popupX();
        float popupY = popupY() + slide;
        int popupWidth = popupWidth();
        int popupHeight = visibleHeight();
        float itemY = firstItemY();
        int itemWidth = itemWidth();
        int itemX = itemX();
        int step = step();

        glassPanel(sx(popupX), sy(popupY), px(popupWidth), px(popupHeight),
                px(Theme.Sizes.DROPDOWN_POPUP_RADIUS), px(20.0F), this.rowAlpha * progress);

        this.scrollOffset = clampScroll(this.scrollOffset);
        int first = firstVisibleIndex();
        int last = lastVisibleIndex();
        if (last >= options.size()) {
            last = options.size() - 1;
        }

        guiGraphicsExtractor.enableScissor(Math.round(sx(popupX)), Math.round(sy(popupY)),
                Math.round(sx(popupX + popupWidth)), Math.round(sy(popupY + popupHeight)));
        Render2DUtil.pushScissor(sx(popupX), sy(popupY), px(popupWidth), px(popupHeight));
        for (int index = first; index <= last && index >= 0; index++) {
            float optionY = itemY + (index - first) * step;
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
        Render2DUtil.popScissor();
        guiGraphicsExtractor.disableScissor();

        if (scrollable()) {
            int trackX = popupX + popupWidth - Theme.Sizes.DROPDOWN_POPUP_PADDING - SCROLLBAR_WIDTH;
            float trackY = popupY + Theme.Sizes.DROPDOWN_POPUP_PADDING;
            float trackHeight = Math.max(1.0F,
                    popupHeight - Theme.Sizes.DROPDOWN_POPUP_PADDING * 2.0F);
            int trackHeightPx = Math.max(1, Math.round(sy(trackY + trackHeight) - sy(trackY)));
            Render2DUtil.rect(sx(trackX), sy(trackY), px(SCROLLBAR_WIDTH), trackHeightPx)
                    .color(alpha(Theme.Colors.OUTLINES_SMALL, this.rowAlpha * progress * 0.6F))
                    .radius(px(SCROLLBAR_WIDTH * 0.5F))
                    .draw();
            float ratio = trackHeight / Math.max(1.0F, contentHeight());
            float thumbHeight = Math.max(18.0F, trackHeight * ratio);
            float maxScroll = maxScroll();
            float thumbY = trackY + (maxScroll <= 0.0F ? 0.0F
                    : (this.scrollOffset / maxScroll) * (trackHeight - thumbHeight));
            int thumbHeightPx = Math.max(1, Math.round(sy(thumbY + thumbHeight) - sy(thumbY)));
            Render2DUtil.rect(sx(trackX), sy(thumbY), px(SCROLLBAR_WIDTH), thumbHeightPx)
                    .color(alpha(Theme.Colors.ICON, this.rowAlpha * progress * 0.75F))
                    .radius(px(SCROLLBAR_WIDTH * 0.5F))
                    .draw();
        }
    }

    private int dropdownColor() {
        Integer warning = warningColor(this.setting);
        return warning == null ? Theme.Colors.ICON : warning;
    }
}
