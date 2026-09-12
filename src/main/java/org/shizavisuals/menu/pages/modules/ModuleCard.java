package org.shizavisuals.menu.pages.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.setting.Setting;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.MarqueeText;
import org.shizavisuals.menu.ui.controls.ToggleComponent;
import org.shizavisuals.menu.ui.rows.RowHost;
import org.shizavisuals.menu.ui.rows.SettingRow;
import org.shizavisuals.menu.ui.rows.SettingRows;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.ArrayList;
import java.util.List;

public final class ModuleCard extends Component implements RowHost {
    static final int HEADER_HEIGHT = Theme.Sizes.MODULE_CARD_HEADER_HEIGHT;
    static final int ROW_HEIGHT = Theme.Sizes.MODULE_CARD_ROW_HEIGHT;

    private final Feature feature;
    private final ToggleComponent featureToggle;
    private final MarqueeText descriptionText;
    private final ModuleBindPopup bindPopup = new ModuleBindPopup();
    private final ModuleBindDetailsPopup bindDetailsPopup = new ModuleBindDetailsPopup();
    private final List<SettingRow> rows = new ArrayList<>();
    private final Animation cardHighlightAnimation = new Animation(0L, Animation.Easing.LINEAR);
    private int designX;
    private int designY;
    private int designWidth;
    private int mouseX;
    private int mouseY;
    private float alpha = 1.0F;
    private float viewportMinY;
    private float viewportMaxY;
    private int popupViewportMaxY;

    public ModuleCard(Feature feature) {
        this.feature = feature;
        this.featureToggle = new ToggleComponent(
                feature::isEnabled,
                feature::toggle,
                ToggleComponent.Style.SWITCH,
                Theme.Colors.CONTROL_STRONG,
                Theme.getAccent()
        );
        this.descriptionText = new MarqueeText(this::displayDescription);
        for (Setting<?> setting : feature.getSettings()) {
            SettingRow row = SettingRows.create(feature.getName(), setting);
            if (row != null) {
                this.rows.add(row);
            }
        }
    }

    public Feature feature() {
        return this.feature;
    }

    public int designHeight() {
        int visibleRows = visibleRowCount();
        return visibleRows == 0 ? HEADER_HEIGHT : HEADER_HEIGHT + visibleRows * ROW_HEIGHT;
    }

    public void place(Component owner, int x, int y, int width, float alpha, int mouseX, int mouseY, int offsetX,
                      int popupViewportMaxY) {
        this.viewportMinY = owner.y() + owner.px(Theme.Sizes.HEADER_HEIGHT);
        this.viewportMaxY = owner.y() + owner.height();
        this.popupViewportMaxY = popupViewportMaxY;
        this.designX = x + offsetX;
        this.designY = y;
        this.designWidth = width;
        this.alpha = alpha;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        attach(owner, owner.sx(this.designX), owner.sy(y), owner.px(width), owner.px(designHeight()));
        this.bindPopup.place(owner, mouseX, mouseY, alpha);
        this.bindDetailsPopup.place(owner, mouseX, mouseY, alpha);
        if (this.feature.isToggleable()) {
            this.featureToggle.place(owner,
                            this.designX + width - Theme.Sizes.MODULE_CARD_PADDING - Theme.Sizes.MODULE_CARD_SWITCH_WIDTH,
                            y + 12,
                            Theme.Sizes.MODULE_CARD_SWITCH_WIDTH,
                            Theme.Sizes.MODULE_CARD_SWITCH_HEIGHT)
                    .style(ToggleComponent.Style.SWITCH, Theme.Colors.CONTROL_STRONG, Theme.getAccent())
                    .alpha(alpha);
        }

        int rowY = this.designY + HEADER_HEIGHT;
        for (SettingRow row : this.rows) {
            if (!row.visible()) {
                continue;
            }
            row.place(owner, this, this.designX, rowY, width, alpha, mouseX, mouseY);
            rowY += ROW_HEIGHT;
        }
    }

    @Override
    public void closeOtherRows(SettingRow except) {
        for (SettingRow row : this.rows) {
            if (row != except) {
                row.closeTransient();
            }
        }
    }

    @Override
    public int popupViewportMaxY() {
        return this.popupViewportMaxY;
    }

    public boolean handleRightClick(int mouseX, int mouseY) {
        return hit(mouseX, mouseY, this.designX, this.designY, this.designWidth, HEADER_HEIGHT);
    }

    public boolean handleMiddleClick(int mouseX, int mouseY) {
        if (!hit(mouseX, mouseY, this.designX, this.designY, this.designWidth, HEADER_HEIGHT)) {
            return false;
        }
        if (!this.feature.supportsBinds()) {
            return true;
        }
        closeOtherRows(null);
        this.bindDetailsPopup.close();
        this.bindPopup.openAt(
                this.feature,
                designX(mouseX),
                designY(mouseY),
                0,
                Theme.Sizes.HEADER_HEIGHT,
                1024,
                640
        );
        return true;
    }

    public void drag(int mouseX) {
        for (SettingRow row : this.rows) {
            row.drag(mouseX);
        }
    }

    @Override
    public boolean contains(float mouseX, float mouseY) {
        if (mouseY < this.viewportMinY || mouseY > this.viewportMaxY) {
            return false;
        }
        return super.contains(mouseX, mouseY);
    }

    public boolean handleClick(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            closeOtherRows(null);
            return false;
        }
        if (hit(mouseX, mouseY, this.designX, this.designY, this.designWidth, HEADER_HEIGHT)) {
            closeOtherRows(null);
            if (this.feature.isToggleable()) {
                this.feature.toggle();
            }
            return true;
        }

        int rowY = this.designY + HEADER_HEIGHT;
        for (SettingRow row : this.rows) {
            if (!row.visible()) {
                continue;
            }
            if (hit(mouseX, mouseY, this.designX + 12, rowY, this.designWidth - 24, ROW_HEIGHT)) {
                return row.click(mouseX, mouseY);
            }
            rowY += ROW_HEIGHT;
        }
        return true;
    }

    public boolean handleKey(int key) {
        if (this.bindDetailsPopup.captureKey(key)) {
            return true;
        }
        for (SettingRow row : this.rows) {
            if (row.key(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleCharacter(int codePoint) {
        for (SettingRow row : this.rows) {
            if (row.character(codePoint)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleScroll(int mouseX, int mouseY, double vertical) {
        for (SettingRow row : this.rows) {
            if (row.visible() && row.scroll(mouseX, mouseY, vertical)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleDropdownPopupClick(int mouseX, int mouseY) {
        boolean handled = false;
        for (SettingRow row : this.rows) {
            if (row.hasOpenPopup()) {
                handled |= row.popupClick(mouseX, mouseY);
            }
        }
        return handled;
    }

    public boolean handleBindPopupClick(int mouseX, int mouseY, int button) {
        if (!this.feature.supportsBinds()) {
            return false;
        }
        if (this.bindDetailsPopup.captureMouseButton(button)) {
            return true;
        }
        for (SettingRow row : this.rows) {
            if (row.captureMouse(button)) {
                return true;
            }
        }

        boolean detailsHovered = this.bindDetailsPopup.containsPopup(mouseX, mouseY);
        if (detailsHovered) {
            return this.bindDetailsPopup.handleMouseButton(mouseX, mouseY, button);
        }

        boolean popupHovered = this.bindPopup.containsPopup(mouseX, mouseY);
        ModuleBindPopup.Action action = this.bindPopup.handleMouseButton(mouseX, mouseY, button);
        if (popupHovered) {
            if (action.type() == ModuleBindPopup.ActionType.EDIT_BIND) {
                this.bindDetailsPopup.openForBind(
                        this.feature,
                        action.bindIndex(),
                        this.bindPopup.popupX(),
                        this.bindPopup.popupY(),
                        0,
                        Theme.Sizes.HEADER_HEIGHT,
                        1024,
                        640
                );
            } else if (action.type() == ModuleBindPopup.ActionType.CREATE_BIND) {
                this.bindDetailsPopup.openForNewBind(
                        this.feature,
                        this.bindPopup.popupX(),
                        this.bindPopup.popupY(),
                        0,
                        Theme.Sizes.HEADER_HEIGHT,
                        1024,
                        640
                );
            } else if (action.type() == ModuleBindPopup.ActionType.RELOAD) {
                this.feature.clearBind();
                this.bindDetailsPopup.close();
                this.bindPopup.close();
            }
            return true;
        }

        this.bindDetailsPopup.close();
        return false;
    }

    public boolean isCapturingBind() {
        if (!this.feature.supportsBinds()) {
            return false;
        }
        if (this.bindDetailsPopup.isListeningForBind()) {
            return true;
        }
        for (SettingRow row : this.rows) {
            if (row.isCapturingBind()) {
                return true;
            }
        }
        return false;
    }

    public void closeBindPopup() {
        this.bindPopup.close();
        this.bindDetailsPopup.close();
    }

    public void flashHighlight() {
        this.cardHighlightAnimation.animate(1.0F, 0.0F, 2000L, Animation.Easing.LINEAR);
    }

    public void releasePointer() {
        for (SettingRow row : this.rows) {
            row.releasePointer();
        }
    }

    public void closeOverlays() {
        for (SettingRow row : this.rows) {
            row.closeTransientImmediately();
        }
        this.bindPopup.closeImmediately();
        this.bindDetailsPopup.close();
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        boolean hovered = contains(this.mouseX, this.mouseY);
        Render2DUtil.rect(sx(this.designX), sy(this.designY), px(this.designWidth), px(designHeight()))
                .color(alpha(hovered ? Theme.Colors.MODULE_CARD_HOVER : Theme.Colors.MODULE_CARD, this.alpha))
                .radius(px(Theme.Sizes.MODULE_CARD_RADIUS))
                .border(Math.max(0.5F, px(0.5F)), alpha(Theme.Colors.DIVIDER_HEADER, this.alpha))
                .draw();
        renderHighlightFlash();
        renderHeader();
        if (this.feature.isToggleable()) {
            this.featureToggle.render(minecraft, guiGraphicsExtractor);
        }

        float contentHeight = Math.round(height() - px(HEADER_HEIGHT));
        if (contentHeight > 0 && visibleRowCount() > 0) {
            Render2DUtil.pushScissor(x(), y() + px(HEADER_HEIGHT), width(), contentHeight);
            int rowY = this.designY + HEADER_HEIGHT;
            for (SettingRow row : this.rows) {
                if (!row.visible()) {
                    continue;
                }
                rect(this.designX + Theme.Sizes.MODULE_CARD_PADDING, rowY,
                        this.designWidth - Theme.Sizes.MODULE_CARD_PADDING * 2, 1,
                        Theme.Colors.DIVIDER_HEADER, 0, this.alpha);
                row.render(minecraft, guiGraphicsExtractor);
                rowY += ROW_HEIGHT;
            }
            Render2DUtil.popScissor();
        }
    }

    public void renderOverlay(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        for (SettingRow row : this.rows) {
            if (row.visible()) {
                row.renderPopupOverlay(minecraft, guiGraphicsExtractor);
            }
        }
        this.bindPopup.render(minecraft, guiGraphicsExtractor);
        this.bindDetailsPopup.render(minecraft, guiGraphicsExtractor);
    }

    private void renderHighlightFlash() {
        float highlight = this.cardHighlightAnimation.getValue();
        if (highlight <= 0.001F) {
            return;
        }
        float rawProgress = 1.0F - highlight;
        float blink = (float) ((1.0F - Math.cos(rawProgress * Math.PI * 6)) / 2.0F);
        float envelope = Math.max(0, 1.0F - rawProgress * 0.7F);
        float flashAlpha = blink * envelope;
        Render2DUtil.rect(sx(this.designX), sy(this.designY), px(this.designWidth), px(designHeight()))
                .color(alpha(ColorUtil.withAlpha(ColorUtil.WHITE, Math.round(35.0F * flashAlpha)), this.alpha))
                .radius(px(Theme.Sizes.MODULE_CARD_RADIUS))
                .draw();
    }

    private void renderHeader() {
        float titleHeight = UiFonts.sfProDisplay().textHeight(14);
        float descriptionHeight = UiFonts.sfProDisplay().textHeight(12);
        float headerGap = 5.0F;
        float headerBlockTop = this.designY + (HEADER_HEIGHT - (titleHeight + headerGap + descriptionHeight)) / 2.0F;
        int switchX = this.designX + this.designWidth
                - Theme.Sizes.MODULE_CARD_PADDING
                - Theme.Sizes.MODULE_CARD_SWITCH_WIDTH;
        boolean showActionIcon = !this.rows.isEmpty();
        int actionIconX = this.feature.isToggleable()
                ? switchX - 22
                : this.designX + this.designWidth
                - Theme.Sizes.MODULE_CARD_PADDING
                - Theme.Sizes.MODULE_CARD_ACTION_ICON;
        float textRight;
        if (!this.feature.isToggleable()) {
            textRight = showActionIcon
                    ? actionIconX - 6.0F
                    : this.designX + this.designWidth
                    - Theme.Sizes.MODULE_CARD_PADDING;
        } else {
            textRight = !showActionIcon
                    ? switchX - 6.0F
                    : switchX - 22.0F - 6.0F;
        }
        float textX = this.designX + Theme.Sizes.MODULE_CARD_PADDING;

        float titleTextWidth = Math.max(1.0F, textRight - textX);
        float descriptionTextWidth = this.designWidth - Theme.Sizes.MODULE_CARD_PADDING * 2.0F;
        String title = UiFonts.sfProDisplay().ellipsize(
                this.feature.getName(), 14,
                14 * UiFontStyle.SEMIBOLD.letterSpacingEm(), titleTextWidth);
        text(textX, headerBlockTop, 14, title,
                Theme.Colors.PRIMARY, this.alpha, UiFontStyle.SEMIBOLD);
        this.descriptionText
                .place(
                        this,
                        textX,
                        headerBlockTop + titleHeight + headerGap,
                        descriptionTextWidth,
                        descriptionHeight,
                        this.mouseX,
                        this.mouseY
                )
                .style(12.0F, UiFontStyle.REGULAR, Theme.Colors.SECONDARY_DARK, this.alpha)
                .render(Minecraft.getInstance(), null);
        if (showActionIcon) {
            texture(actionIconX, this.designY + 14, Theme.Sizes.MODULE_CARD_ACTION_ICON,
                    !this.feature.isToggleable() && !this.feature.supportsBinds()
                            ? Textures.Icons.OPTION
                            : Textures.Icons.COMMAND,
                    Theme.Colors.SECONDARY_DARK,
                    this.alpha);
        }
    }

    private String displayDescription() {
        String description = MenuText.featureDescription(this.feature.getDescription());
        return description.endsWith(".") ? description : description + ".";
    }

    private int visibleRowCount() {
        int count = 0;
        for (SettingRow row : this.rows) {
            if (row.visible()) {
                count++;
            }
        }
        return count;
    }
}
