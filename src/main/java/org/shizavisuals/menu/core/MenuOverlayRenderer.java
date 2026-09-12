package org.shizavisuals.menu.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.pages.accounts.AccountPage;
import org.shizavisuals.menu.pages.ConfigPage;
import org.shizavisuals.menu.pages.friends.FriendPage;
import org.shizavisuals.menu.pages.modules.ModulePage;
import org.shizavisuals.menu.pages.SettingsPage;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;

public final class MenuOverlayRenderer extends Component {
    private final MenuHeader header = new MenuHeader();
    private final ModulePage modules = new ModulePage();
    private final SettingsPage settings = new SettingsPage();
    private final FriendPage friends = new FriendPage();
    private final AccountPage accounts = new AccountPage();
    private final ConfigPage configs = new ConfigPage();
    private MenuDimensions dimensions;
    private MenuPage displayPage = MenuPage.NONE;

    public void layout(Minecraft minecraft, MenuOverlayState state, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        this.dimensions = MenuDimensions.resolve(minecraft, state);
        ensurePosition(state, screenWidth, screenHeight);

        float slideY = (1.0F - state.openProgress()) * Math.max(0.0F, screenHeight - state.panelY());
        configureFrame(state.panelX(), state.panelY() + slideY, this.dimensions.panelWidth(), this.dimensions.panelHeight());
        this.displayPage = state.displayPage();
        this.header.place(this, state, minecraft, mouseX, mouseY);
        this.modules.layout(this, state, mouseX, mouseY);
        this.settings.layout(this, state, mouseX, mouseY);
        this.friends.layout(this, state, mouseX, mouseY);
        this.accounts.layout(this, state, mouseX, mouseY);
        this.configs.layout(this, state, mouseX, mouseY);
    }

    public boolean handleMouseButton(int mouseX, int mouseY, int button, MenuOverlayState state) {
        if (this.header.handleMouseButton(mouseX, mouseY, button)) {
            return true;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.header.isChevronLeftAt(mouseX, mouseY)) {
                state.goBack();
                this.displayPage = state.displayPage();
                return true;
            }
            if (this.header.isChevronRightAt(mouseX, mouseY)) {
                state.goForward();
                this.displayPage = state.displayPage();
                return true;
            }
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT && this.header.isSearchAt(mouseX, mouseY)) {
            state.openPage(state.page() == MenuPage.SEARCH ? MenuPage.NONE : MenuPage.SEARCH);
            this.displayPage = state.displayPage();
            return true;
        }

        MenuPage page = button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT ? this.header.pageAt(mouseX, mouseY) : MenuPage.NONE;
        if (page != MenuPage.NONE) {
            state.openPage(page);
            this.displayPage = state.displayPage();
            return true;
        }

        return switch (state.displayPage()) {
            case SETTINGS -> this.settings.handleMouseButton(mouseX, mouseY, button);
            case FRIENDS -> this.friends.handleClick(mouseX, mouseY);
            case ACCOUNT_SWITCHER -> this.accounts.handleClick(mouseX, mouseY);
            case CONFIGURATIONS -> this.configs.handleClick(mouseX, mouseY);
            default -> this.modules.handleMouseButton(mouseX, mouseY, button);
        };
    }

    public void drag(int mouseX, int mouseY) {
        switch (this.displayPage) {
            case SETTINGS -> this.settings.drag(mouseX, mouseY);
            case NONE, SEARCH -> this.modules.drag(mouseX);
            default -> {
            }
        }
    }

    public void releasePointer() {
        this.modules.releasePointer();
        this.settings.releasePointer();
    }

    public boolean isSearchOpen() {
        return this.modules.isSearchOpen();
    }

    public boolean isSearchFocused() {
        return this.modules.isSearchFocused();
    }

    public void focusSearch() {
        this.modules.focusSearch();
    }

    public boolean isCapturingBind() {
        return this.displayPage == MenuPage.NONE && this.modules.isCapturingBind();
    }

    public void appendSearchCodePoint(int codePoint) {
        this.modules.appendCodePoint(codePoint);
    }

    public void backspaceSearch() {
        this.modules.backspace();
    }

    public boolean handleKey(int key) {
        if (this.displayPage == MenuPage.FRIENDS) {
            return this.friends.handleKey(key);
        }
        if (this.displayPage == MenuPage.ACCOUNT_SWITCHER) {
            return this.accounts.handleKey(key);
        }
        return (this.displayPage == MenuPage.NONE || this.displayPage == MenuPage.SEARCH)
                && this.modules.handleKey(key);
    }

    public boolean handleCharacter(int codePoint) {
        if (this.displayPage == MenuPage.FRIENDS) {
            return this.friends.handleCharacter(codePoint);
        }
        if (this.displayPage == MenuPage.ACCOUNT_SWITCHER) {
            return this.accounts.handleCharacter(codePoint);
        }
        return this.displayPage == MenuPage.NONE && this.modules.handleCharacter(codePoint);
    }

    public void handleScroll(int mouseX, int mouseY, double vertical) {
        if (this.header.handleScroll(mouseX, mouseY, vertical)) {
            return;
        }
        switch (this.displayPage) {
            case SETTINGS -> this.settings.handleScroll(mouseX, mouseY, vertical);
            case FRIENDS -> this.friends.handleScroll(mouseX, mouseY, vertical);
            case ACCOUNT_SWITCHER -> this.accounts.handleScroll(mouseX, mouseY, vertical);
            case CONFIGURATIONS -> this.configs.handleScroll(mouseX, mouseY, vertical);
            case NONE, SEARCH -> this.modules.handleScroll(mouseX, mouseY, vertical);
        }
    }

    @Override
    public boolean isDragHandle(float mouseX, float mouseY) {
        return contains(mouseX, mouseY)
                && mouseY <= y() + px(Theme.Sizes.HEADER_HEIGHT)
                && this.header.pageAt(mouseX, mouseY) == MenuPage.NONE
                && !this.header.isSearchAt(mouseX, mouseY)
                && !this.header.isChevronLeftAt(mouseX, mouseY)
                && !this.header.isChevronRightAt(mouseX, mouseY)
                && !this.header.isProfileAt(mouseX, mouseY)
                && !this.header.isProfilePopupAt(mouseX, mouseY);
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        drawBackground();
        switch (this.displayPage) {
            case SETTINGS -> this.settings.render(minecraft, guiGraphicsExtractor);
            case FRIENDS -> this.friends.render(minecraft, guiGraphicsExtractor);
            case ACCOUNT_SWITCHER -> this.accounts.render(minecraft, guiGraphicsExtractor);
            case CONFIGURATIONS -> this.configs.render(minecraft, guiGraphicsExtractor);
            default -> this.modules.render(minecraft, guiGraphicsExtractor);
        }
        this.header.render(minecraft, guiGraphicsExtractor);
    }

    private void ensurePosition(MenuOverlayState state, int screenWidth, int screenHeight) {
        if (!state.hasPosition()) {
            state.setPanelPosition(
                    (screenWidth - this.dimensions.panelWidth()) / 2,
                    (screenHeight - this.dimensions.panelHeight()) / 2
            );
            state.markPositionInitialized();
        }
        clampPosition(state, screenWidth, screenHeight);
    }

    private void clampPosition(MenuOverlayState state, int screenWidth, int screenHeight) {
        float maxX = Math.max(0.0F, screenWidth - this.dimensions.panelWidth());
        float maxY = Math.max(0.0F, screenHeight - this.dimensions.panelHeight());
        state.setPanelPosition(
                MathUtil.clamp(state.panelX(), 0.0F, maxX),
                MathUtil.clamp(state.panelY(), 0.0F, maxY)
        );
    }

    private void drawBackground() {
        float radius = this.dimensions.panelRadius();
        Render2DUtil.rect(x(), y() + px(25), width(), height())
                .color(ColorUtil.TRANSPARENT)
                .radius(radius)
                .shadow(Theme.Colors.PANEL_SHADOW_STRONG, px(30))
                .draw();
        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.TRANSPARENT)
                .radius(radius)
                .shadow(Theme.Colors.PANEL_SHADOW, px(20))
                .draw();

        Render2DUtil.rect(x(), y(), width(), height())
                .color(Theme.Colors.PANEL)
                .radius(radius)
                .draw();
        drawBackgroundShader(radius);
        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.TRANSPARENT)
                .radius(radius)
                .border(this.dimensions.panelBorder(), Theme.Colors.PANEL_BORDER)
                .draw();
    }

    private void drawBackgroundShader(float radius) {
        MenuBackground background = MenuAppearance.background();
        if (!background.isAnimated()) {
            return;
        }

        int alpha = Math.round(216.0F);
        int primary = (Theme.getAccent() & 0x00FFFFFF) | (alpha << 24);
        int secondary = (accentComplement() & 0x00FFFFFF) | (alpha << 24);
        float headerHeight = px(Theme.Sizes.HEADER_HEIGHT);

        Render2DUtil.pushScissor(x(), y() + headerHeight, width(), height() - headerHeight);
        Render2DUtil.menuBackground(x(), y(), width(), height(), radius,
                backgroundSeconds(), background.shaderMode(), primary, secondary);
        Render2DUtil.popScissor();
        float dim = MenuAppearance.backgroundDim();
        if (dim > 0.001F) {
            Render2DUtil.rect(x(), y() + headerHeight, width(), height() - headerHeight)
                    .color(ColorUtil.withAlpha(ColorUtil.BLACK, Math.round(255.0F * dim)))
                    .radius(0.0F, 0.0F, radius, radius)
                    .draw();
        }

        Render2DUtil.rect(x(), y(), width(), px(Theme.Sizes.HEADER_HEIGHT))
                .color(Theme.Colors.PANEL)
                .radius(radius, radius, 0.0F, 0.0F)
                .draw();
    }

    private static int accentComplement() {
        float[] hsv = ColorUtil.hsv(Theme.getAccent());
        float hue = (hsv[0] + 0.42F) % 1.0F;
        return ColorUtil.fromHsv(hue, Math.max(0.55F, hsv[1]), Math.max(0.75F, hsv[2]), 255);
    }

    private static float backgroundSeconds() {
        return (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
    }
}
