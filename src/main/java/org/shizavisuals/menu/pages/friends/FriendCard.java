package org.shizavisuals.menu.pages.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.FriendManager;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.MenuCard;
import org.shizavisuals.menu.ui.controls.IconButton;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.PlayerHead;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.shizavisuals.utils.render.Textures;

public final class FriendCard extends MenuCard {

    private static final int TEXT_X = PADDING_X + AVATAR_SIZE + 10;
    private static final int DELETE_BOX_X = WIDTH - PADDING_X - ACTION_BOX;
    private static final int PIN_BOX_X = DELETE_BOX_X - ACTION_GAP - ACTION_BOX;

    private final FriendManager.FriendEntry entry;
    private final IconButton pinButton = new IconButton(Textures.Icons.PIN, ACTION_ICON, null);
    private final IconButton deleteButton = new IconButton(Textures.Icons.DELETE_LEFT, ACTION_ICON, null)
            .hoverBackground(ColorUtil.withAlpha(Theme.Colors.SYSTEM_RED, 28))
            .tint(Theme.Colors.ICON, Theme.Colors.SYSTEM_RED);

    public FriendCard(FriendManager.FriendEntry entry) {
        this.entry = entry;
    }

    public String name() {
        return this.entry.name();
    }

    public void place(Component owner, int x, int y, int mouseX, int mouseY, float alpha) {
        placeBounds(owner, x, y, mouseX, mouseY, alpha);

        float actionY = actionRowY();
        this.pinButton.place(owner, x + actionSlotX(1), actionY, ACTION_BOX, mouseX, mouseY)
                .active(this.entry.pinned())
                .activeTint(Theme.getAccent())
                .activeBackground(
                        ColorUtil.withAlpha(Theme.getAccent(), 30),
                        ColorUtil.withAlpha(Theme.getAccent(), 48))
                .hoverBackground(Theme.Colors.SURFACE_HOVER)
                .alpha(alpha);
        this.deleteButton.place(owner, x + actionSlotX(0), actionY, ACTION_BOX, mouseX, mouseY)
                .alpha(alpha);
    }

    public boolean isPinAt(int mouseX, int mouseY) {
        return this.pinButton.contains(mouseX, mouseY);
    }

    public boolean isDeleteAt(int mouseX, int mouseY) {
        return this.deleteButton.contains(mouseX, mouseY);
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        rect(this.designX, this.designY, WIDTH, HEIGHT,
                cardHovered() ? Theme.Colors.CARD_HOVER : Theme.Colors.BACKGROUND_SURFACE_S, RADIUS, this.alpha);
        outline(this.designX, this.designY, WIDTH, HEIGHT, Theme.Colors.OUTLINES_SMALL, RADIUS, 0.5F, this.alpha);

        boolean online = FriendSkinCache.isOnline(minecraft, this.entry.name());
        if (online) {
            FriendManager.INSTANCE.markSeen(this.entry.name());
        }
        drawHead(FriendSkinCache.texture(minecraft, this.entry.name()));

        float nameHeight = UiFonts.sfProDisplay().textHeight(14);
        float statusHeight = UiFonts.sfProDisplay().textHeight(11);
        float textTop = this.designY + (HEIGHT - nameHeight - 4 - statusHeight) / 2.0F;
        float maxTextWidth = PIN_BOX_X - TEXT_X - 8;
        String shownName = UiFonts.sfProDisplay().ellipsize(
                this.entry.name(), 14, 14 * UiFontStyle.MEDIUM.letterSpacingEm(), maxTextWidth);
        text(this.designX + TEXT_X, textTop, 14, shownName,
                Theme.Colors.TEXT_TITLE, this.alpha, UiFontStyle.MEDIUM);
        text(this.designX + TEXT_X, textTop + nameHeight + 4, 11,
                MenuText.friendStatus(online, this.entry.lastSeen()),
                online ? Theme.Colors.TRAFFIC_MAXIMIZE : Theme.Colors.SECONDARY,
                this.alpha, UiFontStyle.REGULAR);

        this.pinButton.render(minecraft, guiGraphicsExtractor);
        this.deleteButton.render(minecraft, guiGraphicsExtractor);
    }

    private void drawHead(Identifier skin) {
        float x = sx(this.designX + PADDING_X);
        float y = sy(this.designY + AVATAR_Y);
        PlayerHead.draw(x, y, px(AVATAR_SIZE), skin, px(6), alpha(ColorUtil.WHITE, this.alpha));
    }

}
