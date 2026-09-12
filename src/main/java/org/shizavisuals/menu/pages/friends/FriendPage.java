package org.shizavisuals.menu.pages.friends;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.core.MenuPage;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.CardGrid;
import org.shizavisuals.menu.ui.controls.IconButton;
import org.shizavisuals.menu.ui.controls.InputComponent;
import org.shizavisuals.menu.ui.popups.ModalDialog;
import org.shizavisuals.menu.ui.PageComponent;
import org.shizavisuals.utils.FriendManager;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.ArrayList;
import java.util.List;

public final class FriendPage extends PageComponent {

    private static final int CONTROLS_RIGHT = 1024 - 32;
    private static final int CONTROLS_Y = 104;
    private static final int COUNT_WIDTH = 100;
    private static final int COUNT_HEIGHT = 36;
    private static final int PLUS_BOX = 24;
    private static final int PLUS_X = CONTROLS_RIGHT - PLUS_BOX;
    private static final float PLUS_Y = CONTROLS_Y + (COUNT_HEIGHT - PLUS_BOX) / 2.0F;
    private static final int COUNT_X = PLUS_X - 8 - COUNT_WIDTH;
    private static final int LIST_TOP = 188;
    private static final int ROW_STEP = FriendCard.HEIGHT + 16;

    private final List<FriendCard> cards = new ArrayList<>();
    private final CardGrid grid = new CardGrid(LIST_TOP, FriendCard.WIDTH, ROW_STEP, 624);
    private final InputComponent nameInput = new InputComponent(() -> this.pendingName, value -> this.pendingName = value)
            .placeholder("Player name")
            .filter(value -> value.length() <= 16 && value.matches("^[A-Za-z0-9_]*$"));
    private final ModalDialog addModal = new ModalDialog(
            Textures.Icons.USER_ROUND_PLUS,
            "Add Friend",
            new String[]{"Enter a Minecraft player name."},
            this.nameInput,
            this::saveModal,
            new ModalDialog.Extras() {
                @Override
                public void render(ModalDialog modal, float alpha) {
                    if (!FriendPage.this.modalError.isEmpty()) {
                        FriendPage.this.text(modal.contentX(), modal.headerY() + 35, 9,
                                MenuText.ui(FriendPage.this.modalError),
                                Theme.Colors.SYSTEM_RED, alpha, UiFontStyle.REGULAR);
                    }
                }
            });
    private final IconButton addButton = new IconButton(Textures.Icons.USER_ROUND_PLUS, 16, this::openModal);

    private MenuPage displayedPage = MenuPage.NONE;
    private List<FriendManager.FriendEntry> snapshot = List.of();
    private String pendingName = "";
    private String modalError = "";

    @Override
    protected void onLayout() {
        this.displayedPage = this.state.displayPage();
        if (this.displayedPage != MenuPage.FRIENDS && this.addModal.isOpen()) {
            closeModal();
        }

        syncCards();
        this.grid.update(this.cards.size());
        for (int index = 0; index < this.cards.size(); index++) {
            this.cards.get(index).place(
                    this,
                    this.grid.x(index),
                    this.grid.y(index),
                    this.mouseX,
                    this.mouseY,
                    this.progress
            );
        }

        this.addButton.place(this, PLUS_X, PLUS_Y, PLUS_BOX, this.mouseX, this.mouseY).alpha(this.progress);
        this.addModal.place(this, this.mouseX, this.mouseY, this.progress);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY) {
        if (!contentContains(mouseX, mouseY)) {
            return false;
        }
        if (this.addModal.isOpen()) {
            return this.addModal.handleClick(mouseX, mouseY);
        }
        if (this.addButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        for (FriendCard card : List.copyOf(this.cards)) {
            if (card.isPinAt(mouseX, mouseY)) {
                FriendManager.INSTANCE.togglePinned(card.name());
                syncCards();
                return true;
            }
            if (card.isDeleteAt(mouseX, mouseY)) {
                FriendManager.INSTANCE.remove(card.name());
                syncCards();
                return true;
            }
        }
        return true;
    }

    public boolean handleKey(int key) {
        if (this.displayedPage != MenuPage.FRIENDS) {
            return false;
        }
        return this.addModal.handleKey(key);
    }

    public boolean handleCharacter(int codePoint) {
        return this.displayedPage == MenuPage.FRIENDS
                && this.addModal.handleCharacter(codePoint);
    }

    public void handleScroll(int mouseX, int mouseY, double vertical) {
        if (this.addModal.isOpen() || !contains(mouseX, mouseY) || mouseY < sy(168)) {
            return;
        }
        this.grid.scroll(vertical, this.cards.size());
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (this.displayedPage != MenuPage.FRIENDS || this.progress <= 0.001F) {
            return;
        }

        pageHeader("Friends", "Manage trusted players ignored by combat features.");

        rect(COUNT_X, CONTROLS_Y, COUNT_WIDTH, COUNT_HEIGHT,
                Theme.Colors.OUTLINES_MEDIUM, 999, this.progress);
        text(COUNT_X + 16, centeredTextY(CONTROLS_Y + COUNT_HEIGHT / 2.0F, 14), 14,
                MenuText.friendCount(this.cards.size()),
                Theme.Colors.TEXT_TITLE, this.progress, UiFontStyle.MEDIUM);
        this.addButton.render(minecraft, guiGraphicsExtractor);

        Render2DUtil.pushScissor(x(), sy(168), width(), height() - px(168));
        if (this.cards.isEmpty()) {
            renderEmptyState();
        } else {
            for (FriendCard card : this.cards) {
                card.render(minecraft, guiGraphicsExtractor);
            }
        }
        Render2DUtil.popScissor();

        this.addModal.render(minecraft, guiGraphicsExtractor);
    }

    private void renderEmptyState() {
        float centerY = (188 + 768) / 2.0F;
        texture(500, centerY - 58, 24, Textures.Header.FRIENDS, Theme.Colors.ICON_MUTED, this.progress);
        float titleHeight = UiFonts.sfProDisplay().textHeight(16);
        textCentered(512, centerY - 20, 16, MenuText.ui("No friends yet"),
                Theme.Colors.PRIMARY, this.progress, UiFontStyle.MEDIUM);
        textCentered(512, centerY - 20 + titleHeight + 8, 12,
                MenuText.ui("Add a player here or use .friend add <name>"),
                Theme.Colors.SECONDARY, this.progress, UiFontStyle.REGULAR);
    }

    private void openModal() {
        this.pendingName = "";
        this.modalError = "";
        this.addModal.open();
    }

    private void closeModal() {
        this.pendingName = "";
        this.modalError = "";
        this.addModal.close();
    }

    private void saveModal() {
        String name = this.pendingName.trim();
        if (!FriendManager.isValidName(name)) {
            this.modalError = "Enter a valid player name";
            return;
        }
        if (!FriendManager.INSTANCE.add(name)) {
            this.modalError = "This player is already a friend";
            return;
        }
        syncCards();
        closeModal();
    }

    private void syncCards() {
        List<FriendManager.FriendEntry> current = FriendManager.INSTANCE.getEntries();
        if (current.equals(this.snapshot)) {
            return;
        }
        this.snapshot = current;
        this.cards.clear();
        for (FriendManager.FriendEntry friend : current) {
            this.cards.add(new FriendCard(friend));
        }
    }

}
