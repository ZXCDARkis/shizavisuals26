package org.shizavisuals.menu.ui.popups;

import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.MarqueeText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class DropdownPopup extends Component {
    private final Supplier<String[]> options;
    private final IntSupplier selectedIndex;
    private final IntConsumer onPick;
    private final Map<String, MarqueeText> optionTexts = new HashMap<>();
    private final Animation animation = new Animation(150L, Animation.Easing.EASE_OUT_QUAD);
    private boolean open;
    private boolean closing;
    private int designX;
    private int designY;
    private float pageAlpha = 1.0F;
    private int hoverMouseX;
    private int hoverMouseY;

    public DropdownPopup(Supplier<String[]> options, IntSupplier selectedIndex, IntConsumer onPick) {
        this.options = options;
        this.selectedIndex = selectedIndex;
        this.onPick = onPick;
    }

    public DropdownPopup place(Component owner, int x, int y, int mouseX, int mouseY, float pageAlpha) {
        this.designX = x;
        this.designY = y;
        this.hoverMouseX = mouseX;
        this.hoverMouseY = mouseY;
        this.pageAlpha = pageAlpha;
        attach(owner, owner.sx(x), owner.sy(y), owner.px(popupWidth()), owner.px(popupHeight()));
        return this;
    }

    public void toggle() {
        if (this.open) {
            close();
            return;
        }
        this.open = true;
        this.closing = false;
        this.animation.animate(0.0F, 1.0F, 150L, Animation.Easing.EASE_OUT_QUAD);
    }

    public void close() {
        if (this.open) {

            this.closing = true;
            this.animation.animate(this.animation.getValue(), 0.0F, 120L, Animation.Easing.EASE_OUT_QUAD);
        }
        this.open = false;
    }

    public void closeImmediately() {
        this.open = false;
        this.closing = false;
        this.animation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    public boolean isOpen() {
        return this.open;
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY) {
        if (!this.open) {
            return false;
        }
        String[] values = this.options.get();
        int itemX = this.designX + Theme.Sizes.DROPDOWN_POPUP_PADDING;
        int itemWidth = popupWidth() - Theme.Sizes.DROPDOWN_POPUP_PADDING * 2;
        int step = Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_GAP;
        for (int index = 0; index < values.length; index++) {
            int itemY = this.designY + Theme.Sizes.DROPDOWN_POPUP_PADDING + index * step;
            if (hit(mouseX, mouseY, itemX, itemY, itemWidth, Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT)) {
                this.onPick.accept(index);
                close();
                return true;
            }
        }
        close();
        return false;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (!this.open && !this.closing) {
            return;
        }
        float popupProgress = this.animation.getValue();
        if (popupProgress <= 0.001F) {
            if (!this.open && this.animation.isFinished()) {
                this.closing = false;
            }
            return;
        }
        float alpha = this.pageAlpha * popupProgress;
        float lift = -6.0F * (1.0F - popupProgress);
        String[] values = this.options.get();
        float popupY = this.designY + lift;
        glassPanel(sx(this.designX), sy(popupY), px(popupWidth()), px(popupHeight()),
                px(Theme.Sizes.DROPDOWN_POPUP_RADIUS), px(20.0F), alpha);

        int selected = this.selectedIndex.getAsInt();
        int itemX = this.designX + Theme.Sizes.DROPDOWN_POPUP_PADDING;
        int itemWidth = popupWidth() - Theme.Sizes.DROPDOWN_POPUP_PADDING * 2;
        int step = Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_GAP;
        for (int index = 0; index < values.length; index++) {
            float itemY = popupY + Theme.Sizes.DROPDOWN_POPUP_PADDING + index * step;
            boolean isSelected = index == selected;
            rect(itemX, itemY, itemWidth, Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT,
                    isSelected ? Theme.Colors.SURFACE_ACTIVE : ColorUtil.TRANSPARENT,
                    Theme.Sizes.DROPDOWN_POPUP_ITEM_RADIUS, alpha);
            String canonicalValue = values[index];
            MarqueeText optionText = this.optionTexts.computeIfAbsent(
                    canonicalValue,
                    ignored -> new MarqueeText(() -> MenuText.option(canonicalValue))
            );
            optionText
                    .placeAt(
                            this,
                            sx(itemX + 10),
                            sy(itemY),
                            px(itemWidth - 20),
                            px(Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT),
                            this.hoverMouseX,
                            this.hoverMouseY
                    )
                    .style(
                            12.0F,
                            UiFontStyle.MEDIUM,
                            isSelected ? Theme.Colors.TEXT_TITLE : Theme.Colors.SECONDARY,
                            alpha
                    )
                    .render(minecraft, guiGraphicsExtractor);
        }
    }

    private int popupWidth() {
        return Theme.Sizes.DROPDOWN_POPUP_WIDTH;
    }

    private int popupHeight() {
        int count = this.options.get().length;
        return Theme.Sizes.DROPDOWN_POPUP_PADDING * 2
                + count * Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT
                + Math.max(0, count - 1) * Theme.Sizes.DROPDOWN_POPUP_GAP;
    }
}
