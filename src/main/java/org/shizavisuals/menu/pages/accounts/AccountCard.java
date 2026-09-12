package org.shizavisuals.menu.pages.accounts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.MenuCard;
import org.shizavisuals.menu.ui.controls.IconButton;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.shizavisuals.utils.render.Textures;

public final class AccountCard extends MenuCard {
    static final int HEAD_COUNT = 7;

    private static final int PADDING_X = MenuCard.PADDING_X;
    private static final int CONTENT_GAP = 8;
    private static final int AVATAR_RADIUS = 6;
    private static final int INACTIVE_MASK = ColorUtil.rgba(0, 0, 0, 110);
    private static final int TEXT_X = PADDING_X + AVATAR_SIZE + CONTENT_GAP;
    private static final float TEXT_GAP = 4.0F;

    private final String name;
    private final String activity;
    private final int avatarIndex;
    private final Identifier avatar;
    private final IconButton pinButton = new IconButton(Textures.Icons.PIN, ACTION_ICON, null);
    private final IconButton gamepadButton = new IconButton(Textures.Icons.GAMEPAD, ACTION_ICON, null);
    private final IconButton deleteButton = new IconButton(Textures.Icons.DELETE_LEFT, ACTION_ICON, null);
    private boolean pinned;
    private boolean selected;
    private boolean showGamepad = true;

    public AccountCard(String name, String activity, int avatarIndex) {
        this(name, activity, avatarIndex, false);
    }

    public AccountCard(String name, String activity, int avatarIndex, boolean pinned) {
        this.name = name;
        this.activity = activity;
        this.avatarIndex = avatarIndex;
        this.avatar = menuTexturePng("accounts/head_" + Math.floorMod(avatarIndex, HEAD_COUNT));
        this.pinned = pinned;
    }

    public String name() {
        return this.name;
    }

    public String activity() {
        return this.activity;
    }

    public int avatarIndex() {
        return this.avatarIndex;
    }

    public boolean pinned() {
        return this.pinned;
    }

    public void place(Component owner, int x, int y, int mouseX, int mouseY, float alpha) {
        placeBounds(owner, x, y, mouseX, mouseY, alpha);

        int deleteSlot = 0;
        int gamepadSlot = 1;
        int pinSlot = this.showGamepad ? 2 : 1;

        float actionY = actionRowY();
        int white38 = ColorUtil.withAlpha(ColorUtil.WHITE, 38);
        this.pinButton.place(owner, x + actionSlotX(pinSlot), actionY, ACTION_BOX, mouseX, mouseY)
                .active(this.pinned)
                .activeTint(this.selected ? Theme.Colors.TEXT_TITLE : Theme.getAccent())
                .activeBackground(
                        this.selected ? ColorUtil.withAlpha(ColorUtil.WHITE, 22) : ColorUtil.withAlpha(Theme.getAccent(), 30),
                        this.selected ? white38 : ColorUtil.withAlpha(Theme.getAccent(), 48))
                .hoverBackground(this.selected ? white38 : Theme.Colors.SURFACE_HOVER)
                .tint(this.selected ? Theme.Colors.TEXT_TITLE : Theme.Colors.ICON, Theme.Colors.TEXT_TITLE)
                .alpha(alpha);
        this.gamepadButton.place(owner, x + actionSlotX(gamepadSlot), actionY, ACTION_BOX, mouseX, mouseY)
                .hoverBackground(this.selected ? white38 : Theme.Colors.SURFACE_HOVER)
                .tint(this.selected ? Theme.Colors.TEXT_TITLE : Theme.Colors.ICON, Theme.Colors.TEXT_TITLE)
                .alpha(alpha);
        this.deleteButton.place(owner, x + actionSlotX(deleteSlot), actionY, ACTION_BOX, mouseX, mouseY)
                .hoverBackground(ColorUtil.withAlpha(Theme.Colors.SYSTEM_RED, 28))
                .tint(this.selected ? Theme.Colors.TEXT_TITLE : Theme.Colors.ICON, Theme.Colors.SYSTEM_RED)
                .alpha(alpha);
    }

    public boolean handleClick(int mouseX, int mouseY) {
        return contains(mouseX, mouseY);
    }

    public boolean isDeleteAt(int mouseX, int mouseY) {
        return this.deleteButton.contains(mouseX, mouseY);
    }

    public boolean isPinAt(int mouseX, int mouseY) {
        return this.pinButton.contains(mouseX, mouseY);
    }

    public boolean isGamepadAt(int mouseX, int mouseY) {
        return this.showGamepad && this.gamepadButton.contains(mouseX, mouseY);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setShowGamepad(boolean showGamepad) {
        this.showGamepad = showGamepad;
    }

    public void togglePinned() {
        this.pinned = !this.pinned;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (this.selected) {
            rect(this.designX, this.designY, WIDTH, HEIGHT, Theme.getAccent(), RADIUS, this.alpha);
        } else {
            rect(this.designX, this.designY, WIDTH, HEIGHT, Theme.Colors.BACKGROUND_SURFACE_S, RADIUS, this.alpha);
            outline(this.designX, this.designY, WIDTH, HEIGHT, Theme.Colors.OUTLINES_SMALL, RADIUS, 0.5F, this.alpha);
        }

        texture(this.designX + PADDING_X, this.designY + AVATAR_Y, AVATAR_SIZE, this.avatar, ColorUtil.WHITE, this.alpha);
        if (!this.selected) {

            rect(this.designX + PADDING_X, this.designY + AVATAR_Y, AVATAR_SIZE, AVATAR_SIZE, INACTIVE_MASK, AVATAR_RADIUS, this.alpha);
        }

        int textColor = this.selected ? Theme.Colors.TEXT_TITLE : Theme.Colors.ICON;

        float textMaxWidth = actionSlotX(this.showGamepad ? 2 : 1) - TEXT_X - CONTENT_GAP;
        String shownName = UiFonts.sfProDisplay().ellipsize(
                this.name, 14, 14 * UiFontStyle.MEDIUM.letterSpacingEm(), textMaxWidth);
        String shownActivity = UiFonts.sfProDisplay().ellipsize(
                MenuText.ui(this.activity), 12,
                12 * UiFontStyle.REGULAR.letterSpacingEm(), textMaxWidth);

        float nameHeight = UiFonts.sfProDisplay().textHeight(14);
        float activityHeight = UiFonts.sfProDisplay().textHeight(12);
        float blockTop = this.designY + (HEIGHT - (nameHeight + TEXT_GAP + activityHeight)) / 2.0F;
        text(this.designX + TEXT_X, blockTop, 14, shownName, textColor, this.alpha, UiFontStyle.MEDIUM);
        text(this.designX + TEXT_X, blockTop + nameHeight + TEXT_GAP, 12, shownActivity, textColor, this.alpha, UiFontStyle.REGULAR);

        this.pinButton.render(minecraft, guiGraphicsExtractor);
        if (this.showGamepad) {
            this.gamepadButton.render(minecraft, guiGraphicsExtractor);
        }
        this.deleteButton.render(minecraft, guiGraphicsExtractor);
    }
}
