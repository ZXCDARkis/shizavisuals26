package org.shizavisuals.menu.pages.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.menu.core.MenuOverlay;
import org.shizavisuals.menu.core.MenuPage;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.CardGrid;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.IconButton;
import org.shizavisuals.menu.ui.controls.InputComponent;
import org.shizavisuals.menu.ui.popups.ModalDialog;
import org.shizavisuals.menu.ui.PageComponent;
import org.shizavisuals.utils.AccountSwitcher;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.shizavisuals.utils.render.Textures;

public final class AccountPage extends PageComponent {

    private static final int ROW_STEP = AccountCard.HEIGHT + 16;

    private static final int DROPDOWN_WIDTH = 85;
    private static final int DROPDOWN_HEIGHT = 36;
    private static final int DROPDOWN_PADDING_LEFT = 16;
    private static final int DROPDOWN_PADDING_RIGHT = 8;
    private static final int PLUS_BOX = 24;
    private static final int PLUS_ICON = 16;

    private static final int CONTROLS_RIGHT = 1024 - 32;
    private static final int CONTROLS_GAP = 8;
    private static final int CONTROLS_ROW_Y = 104;
    private static final int PLUS_X = CONTROLS_RIGHT - PLUS_BOX;
    private static final float PLUS_Y = CONTROLS_ROW_Y + (DROPDOWN_HEIGHT - PLUS_BOX) / 2.0F;
    private static final int DROPDOWN_X = PLUS_X - CONTROLS_GAP - DROPDOWN_WIDTH;

    private static final String[] NICK_PREFIXES = {
            "Shadow", "Frost", "Void", "Pixel", "Aqua", "Night", "Storm", "Ember", "Ghost", "Nova",
            "Cyber", "Lunar", "Rapid", "Toxic", "Magma", "Blaze", "Astro", "Neon", "Grim", "Hyper",
            "Iron", "Zero", "Drako", "Mystic", "Silent", "Wicked", "Prime", "Retro", "Sour", "Vex"
    };
    private static final String[] NICK_SUFFIXES = {
            "Byte", "Craft", "Rush", "Fox", "ShizaVisuals", "Wing", "Strike", "Core", "Drift", "Zap",
            "Wolf", "Hawk", "Reign", "Spark", "Flux", "Dash", "Rage", "Snipe", "King", "Lord",
            "Punch", "Shot", "Fang", "Peak", "Vibe", "Loop", "Crypt", "Gaze", "Husk", "Riot"
    };

    private final List<Component> children = new ArrayList<>();
    private final List<AccountCard> cards = new ArrayList<>();
    private MenuPage displayedPage = MenuPage.NONE;
    private AccountCard selectedCard;
    private boolean oldestFirst;
    private final CardGrid grid = new CardGrid(188, AccountCard.WIDTH, ROW_STEP, 624);
    private int nextAvatarIndex;

    private String pendingNickname = "";
    private final InputComponent nicknameInput = new InputComponent(() -> this.pendingNickname, value -> this.pendingNickname = value)
            .placeholder("Type Nickname")
            .filter(val -> val.length() <= 16 && val.matches("^[a-zA-Z0-9_]*$"));
    private final IconButton addButton = new IconButton(Textures.Icons.CIRCLE_PLUS, PLUS_ICON, this::openModal);
    private final IconButton dicesButton = new IconButton(Textures.Icons.DICES, 16, () -> this.pendingNickname = randomNickname());
    private final ModalDialog createModal = new ModalDialog(
            Textures.Icons.PLUS,
            "Create Account",
            new String[]{"Enter a new account nickname", "below."},
            this.nicknameInput,
            this::saveModal,
            new ModalDialog.Extras() {
                @Override
                public void render(ModalDialog modal, float alpha) {
                    AccountPage.this.dicesButton.render(net.minecraft.client.Minecraft.getInstance(), null);
                }

                @Override
                public boolean click(ModalDialog modal, int mouseX, int mouseY) {
                    return AccountPage.this.dicesButton.handleClick(mouseX, mouseY);
                }
            });

    public AccountPage() {
        loadAccounts();
        rebuildChildren();
        this.oldestFirst = MenuConfigStore.getBoolean("accountsOldestFirst", false);

        String selectedName = MenuConfigStore.getString("selectedAccount", "");
        this.selectedCard = this.cards.stream()
                .filter(card -> card.name().equals(selectedName))
                .findFirst()
                .orElse(this.cards.get(0));
    }

    private void loadAccounts() {
        JsonArray saved = MenuConfigStore.getArray("accounts");
        if (saved != null) {
            for (JsonElement element : saved) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject account = element.getAsJsonObject();
                String name = account.has("name") ? account.get("name").getAsString() : "";
                if (name.isEmpty() || !name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                    continue;
                }
                String activity = account.has("activity") ? account.get("activity").getAsString() : "Never played";
                int avatar = account.has("avatar") ? account.get("avatar").getAsInt() : this.cards.size();
                boolean pinned = account.has("pinned") && account.get("pinned").getAsBoolean();
                this.cards.add(new AccountCard(name, activity, avatar, pinned));
                this.nextAvatarIndex = Math.max(this.nextAvatarIndex, avatar + 1);
            }
        }
        if (this.cards.isEmpty()) {

            this.cards.add(new AccountCard(AccountSwitcher.DEFAULT_NAME, "Playing now", 0));
            this.nextAvatarIndex = 1;
        }
    }

    private void persistAccounts() {
        JsonArray array = new JsonArray();
        for (AccountCard card : this.cards) {
            JsonObject account = new JsonObject();
            account.addProperty("name", card.name());
            account.addProperty("activity", card.activity());
            account.addProperty("avatar", card.avatarIndex());
            account.addProperty("pinned", card.pinned());
            array.add(account);
        }
        String selectedName = this.selectedCard != null ? this.selectedCard.name() : "";
        boolean oldest = this.oldestFirst;
        MenuConfigStore.save(data -> {
            data.add("accounts", array);
            data.addProperty("selectedAccount", selectedName);
            data.addProperty("accountsOldestFirst", oldest);
        });
    }

    @Override
    protected void onLayout() {
        this.displayedPage = this.state.displayPage();
        if (this.displayedPage != MenuPage.ACCOUNT_SWITCHER && this.createModal.isOpen()) {

            closeModal();
        }
        this.grid.update(this.cards.size());

        String activeName = activeProfileName();
        List<AccountCard> display = displayOrder();
        for (int index = 0; index < display.size(); index++) {
            AccountCard card = display.get(index);
            boolean selected = card == this.selectedCard;
            card.setSelected(selected);

            card.setShowGamepad(!selected || !card.name().equals(activeName));
            card.place(this, this.grid.x(index), this.grid.y(index), this.mouseX, this.mouseY, this.progress);
        }

        this.addButton.place(this, PLUS_X, PLUS_Y, PLUS_BOX, this.mouseX, this.mouseY).alpha(this.progress);
        this.createModal.place(this, this.mouseX, this.mouseY, this.progress);
        this.dicesButton.place(this,
                        this.createModal.contentX() + this.createModal.contentWidth() - 26,
                        this.createModal.inputY() + (this.createModal.inputHeight() - 24) / 2.0F,
                        24, this.mouseX, this.mouseY)
                .alpha(this.progress);
    }

    public void handleScroll(int mouseX, int mouseY, double vertical) {
        if (this.createModal.isOpen()) {
            return;
        }
        if (contains(mouseX, mouseY) && mouseY >= sy(168)) {
            this.grid.scroll(vertical, this.cards.size());
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY) {
        if (!contentContains(mouseX, mouseY)) {
            return false;
        }
        if (this.createModal.isOpen()) {
            return this.createModal.handleClick(mouseX, mouseY);
        }
        if (hit(mouseX, mouseY, DROPDOWN_X, CONTROLS_ROW_Y, DROPDOWN_WIDTH, DROPDOWN_HEIGHT)) {

            this.oldestFirst = !this.oldestFirst;
            java.util.Collections.reverse(this.cards);
            persistAccounts();
            return true;
        }
        if (this.addButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (mouseY < sy(168)) return true;
        for (AccountCard card : this.cards) {
            if (card.isPinAt(mouseX, mouseY)) {
                card.togglePinned();
                persistAccounts();
                return true;
            }
            if (card.isDeleteAt(mouseX, mouseY) && this.cards.size() > 1) {
                this.cards.remove(card);
                rebuildChildren();
                if (this.selectedCard == card) {
                    this.selectedCard = this.cards.get(0);
                }
                persistAccounts();
                return true;
            }

            if (card.isGamepadAt(mouseX, mouseY)) {
                this.selectedCard = card;
                persistAccounts();
                MenuOverlay.close(Minecraft.getInstance());
                AccountSwitcher.relogin(card.name());
                return true;
            }

            if (card.handleClick(mouseX, mouseY)) {
                this.selectedCard = card;
                AccountSwitcher.switchTo(card.name());
                persistAccounts();
                return true;
            }
        }
        return true;
    }

    private static String activeProfileName() {
        Minecraft mc = Minecraft.getInstance();
        return org.shizavisuals.utils.text.NameProtectUtil.protect(mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName());
    }

    private List<AccountCard> displayOrder() {
        List<AccountCard> display = new ArrayList<>(this.cards);
        display.sort(java.util.Comparator
                .comparing(AccountCard::pinned).reversed()
                .thenComparing(card -> card != this.selectedCard));
        return display;
    }

    public boolean handleKey(int key) {
        return this.createModal.handleKey(key);
    }

    public boolean handleCharacter(int codePoint) {
        return this.createModal.handleCharacter(codePoint);
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (this.displayedPage != MenuPage.ACCOUNT_SWITCHER || this.progress <= 0.001F) {
            return;
        }

        pageHeader("Accounts", "Switch between your saved Minecraft accounts.");

        rect(DROPDOWN_X, CONTROLS_ROW_Y, DROPDOWN_WIDTH, DROPDOWN_HEIGHT, Theme.Colors.OUTLINES_MEDIUM, 999, this.progress);
        text(DROPDOWN_X + DROPDOWN_PADDING_LEFT,
                centeredTextY(CONTROLS_ROW_Y + DROPDOWN_HEIGHT / 2.0F, 14), 14,
                MenuText.ui(this.oldestFirst ? "Oldest" : "Recent"),
                Theme.Colors.TEXT_TITLE, this.progress, UiFontStyle.MEDIUM);
        texture(DROPDOWN_X + DROPDOWN_WIDTH - DROPDOWN_PADDING_RIGHT - 12,
                CONTROLS_ROW_Y + (DROPDOWN_HEIGHT - 12) / 2.0F, 12, Textures.Icons.CHEVRONS_LEFT_RIGHT, Theme.Colors.ICON, this.progress);

        this.addButton.render(minecraft, guiGraphicsExtractor);

        Render2DUtil.pushScissor(x(), sy(168), width(), height() - px(168));
        for (AccountCard card : this.cards) {
            card.render(minecraft, guiGraphicsExtractor);
        }
        Render2DUtil.popScissor();

        this.createModal.render(minecraft, guiGraphicsExtractor);
    }

    private void openModal() {
        this.pendingNickname = "";
        this.createModal.open();
    }

    private void closeModal() {
        this.pendingNickname = "";
        this.createModal.close();
    }

    private void saveModal() {
        String name = this.pendingNickname.trim();
        if (name.length() < 3 || name.length() > 16 || !name.matches("^[a-zA-Z0-9_]+$")) {
            return;
        }
        this.cards.add(0, new AccountCard(name, "Never played", this.nextAvatarIndex++, false));
        rebuildChildren();
        persistAccounts();
        closeModal();
    }

    private String randomNickname() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 20; attempt++) {
            String prefix = NICK_PREFIXES[random.nextInt(NICK_PREFIXES.length)];
            String suffix = NICK_SUFFIXES[random.nextInt(NICK_SUFFIXES.length)];
            String name = switch (random.nextInt(6)) {
                case 0 -> prefix + suffix + random.nextInt(10, 100);
                case 1 -> prefix + "_" + suffix;
                case 2 -> prefix.toLowerCase(Locale.ROOT) + suffix + random.nextInt(100, 1000);
                case 3 -> prefix.toLowerCase(Locale.ROOT) + "_" + suffix.toLowerCase(Locale.ROOT);
                case 4 -> prefix + suffix;
                default -> suffix + prefix + random.nextInt(10, 100);
            };
            if (name.length() <= 16 && this.cards.stream().noneMatch(card -> card.name().equals(name))) {
                return name;
            }
        }
        return NICK_PREFIXES[random.nextInt(NICK_PREFIXES.length)] + random.nextInt(1000, 10000);
    }

    private void rebuildChildren() {
        this.children.clear();
        this.children.addAll(this.cards);
    }

}
