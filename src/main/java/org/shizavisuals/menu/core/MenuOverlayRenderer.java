package org.shizavisuals.menu.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.pages.accounts.AccountPage;
import org.shizavisuals.menu.pages.ConfigPage;
import org.shizavisuals.menu.pages.CosmeticsPage;
import org.shizavisuals.menu.pages.friends.FriendPage;
import org.shizavisuals.menu.pages.modules.ModulePage;
import org.shizavisuals.menu.pages.SettingsPage;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

public final class MenuOverlayRenderer extends Component {
    private final MenuHeader header = new MenuHeader();
    private final ModulePage modules = new ModulePage();
    private final SettingsPage settings = new SettingsPage();
    private final FriendPage friends = new FriendPage();
    private final AccountPage accounts = new AccountPage();
    private final CosmeticsPage cosmetics = new CosmeticsPage();
    private final ConfigPage configs = new ConfigPage();
    private MenuDimensions dimensions;
    private MenuPage displayPage = MenuPage.NONE;
    private MenuOverlayState state;
    private Minecraft mc;

    public void layout(Minecraft minecraft, MenuOverlayState state, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        this.dimensions = MenuDimensions.resolve(minecraft, state);
        this.state = state;
        this.mc = minecraft;
        ensurePosition(state, screenWidth, screenHeight);

        float slideY = (1.0F - state.openProgress()) * Math.max(0.0F, screenHeight - state.panelY());
        configureFrame(state.panelX(), state.panelY() + slideY, this.dimensions.panelWidth(), this.dimensions.panelHeight());
        this.displayPage = state.displayPage();
        this.header.place(this, state, minecraft, mouseX, mouseY);
        this.modules.layout(this, state, mouseX, mouseY);
        this.settings.layout(this, state, mouseX, mouseY);
        this.friends.layout(this, state, mouseX, mouseY);
        this.accounts.layout(this, state, mouseX, mouseY);
        this.cosmetics.layout(this, state, mouseX, mouseY);
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
            case COSMETICS -> this.cosmetics.handleClick(mouseX, mouseY);
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
            case COSMETICS -> this.cosmetics.handleScroll(mouseX, mouseY, vertical);
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
            case COSMETICS -> this.cosmetics.render(minecraft, guiGraphicsExtractor);
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
        float reveal = state == null ? 1.0F : state.openProgress();

        // Contact shadow. Two offsets, because a single shadow read as a hard
        // outline around the panel rather than as light falling from above.
        Render2DUtil.rect(x(), y() + px(14), width(), height())
                .color(ColorUtil.TRANSPARENT)
                .radius(radius)
                .shadow(ColorUtil.multiplyAlpha(Theme.Colors.PANEL_SHADOW_STRONG, reveal), px(34))
                .draw();
        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.TRANSPARENT)
                .radius(radius)
                .shadow(ColorUtil.multiplyAlpha(Theme.Colors.PANEL_SHADOW, reveal), px(18))
                .draw();

        // Acrylic body: a cool, low-alpha tint that the glass pass can blur rather
        // than the flat near-black panel this used to fill with. Reading a solid
        // fill first kept the glass pass invisible on the menu.
        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.multiplyAlpha(Theme.Colors.PANEL, reveal))
                .radius(radius)
                .blur(px(24.0F), reveal)
                .draw();
        if (Theme.getAccent() != 0) {
            // Accent wash on top of the blurred body.
            Render2DUtil.rect(x(), y(), width(), height())
                    .radius(radius)
                    .color(ColorUtil.rgba(
                            (Theme.getAccent() >>> 16) & 0xFF,
                            (Theme.getAccent() >>> 8) & 0xFF,
                            Theme.getAccent() & 0xFF,
                            Math.round(0.10F * reveal * 255.0F)))
                    .draw();
        }

        drawBackgroundShader(radius);

        // Light from above: a bright top edge and a dark bottom edge are what make
        // a flat rect read as a physical surface.
        drawLighting(radius, reveal);
        drawGrip(reveal);
    }

    private void drawLighting(float radius, float reveal) {
        float thickness = px(1.0F);
        Render2DUtil.rect(x(), y(), width(), thickness)
                .radius(radius, radius, 0.0F, 0.0F)
                .color(ColorUtil.multiplyAlpha(ColorUtil.rgba(255, 255, 255, 34), reveal))
                .draw();
        Render2DUtil.rect(x(), y() + px(Theme.Sizes.HEADER_HEIGHT), width(), height() - px(Theme.Sizes.HEADER_HEIGHT))
                .color(ColorUtil.TRANSPARENT)
                .radius(0.0F, 0.0F, radius, radius)
                .border(this.dimensions.panelBorder(), ColorUtil.multiplyAlpha(Theme.Colors.PANEL_BORDER, reveal))
                .draw();
    }

    /** Corner grip that scales the whole panel, with a live percentage readout. */
    private void drawGrip(float reveal) {
        float size = MenuDragController.gripSize() * Math.max(1.0F, px(1.0F));
        float gx = x() + width();
        float gy = y() + height();
        boolean active = MenuDragControllerHolder.resizing();
        boolean hovered = MenuDragControllerHolder.hovered(mouseX(mc), mouseY(mc), gx, gy, size);
        int gripColor = ColorUtil.multiplyAlpha(active || hovered ? ColorUtil.WHITE : Theme.getAccent(),
                (active ? 0.95F : hovered ? 0.80F : 0.55F) * reveal);
        Render2DUtil.rect(gx - size / 2.0F, gy - size / 2.0F, size, size)
                .radius(size * 0.3F)
                .color(gripColor)
                .draw();
        if (reveal <= 0.02F) {
            return;
        }
        // The percentage pill only shows while the grip is held or hovered;
        // a permanent pill permanently covers the panel content.
        if (!active && !hovered) {
            return;
        }
        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = 9.0F * Math.max(1.0F, px(1.0F));
        float spacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();
        int percent = Math.round(state == null ? 100.0F
                : org.shizavisuals.utils.math.MathUtil.lerp(
                        MenuOverlayState.MIN_UI_SCALE_PERCENT, MenuOverlayState.MAX_UI_SCALE_PERCENT,
                        state.liveUiScaleValue()));
        String label = percent + "%";
        float textWidth = font.measureWidth(label, textSize, spacing);
        float padX = 7.0F * Math.max(1.0F, px(1.0F));
        float padY = 4.0F * Math.max(1.0F, px(1.0F));
        float pillW = textWidth + padX * 2.0F;
        float pillH = font.textHeight(textSize) + padY * 2.0F;
        Render2DUtil.rect(gx - pillW / 2.0F, gy - size - 6.0F - pillH, pillW, pillH)
                .radius(pillH * 0.5F)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.BACKGROUND_PRIMARY_50, reveal))
                .border(Math.max(0.5F, this.dimensions.panelBorder()), ColorUtil.multiplyAlpha(Theme.getAccent(), reveal))
                .draw();
        Render2DUtil.text(gx - pillW / 2.0F + padX, gy - size - 6.0F - pillH + padY, textSize, label)
                .font(font)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, 0.95F * reveal))
                .draw();
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static float mouseX(Minecraft mc) {
        return mc == null ? -1.0F : (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
    }

    private static float mouseY(Minecraft mc) {
        return mc == null ? -1.0F : (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
    }

    /** Bridges the render pass to the live drag state held by MenuOverlay. */
    static final class MenuDragControllerHolder {
        private MenuDragControllerHolder() {
        }

        static boolean resizing() {
            return MenuOverlay.isResizing();
        }

        static boolean hovered(float mouseX, float mouseY, float gx, float gy, float size) {
            float grab = MenuDragController.gripSize() * 10.0F;
            return mouseX >= gx - grab && mouseX <= gx + grab
                    && mouseY >= gy - grab && mouseY <= gy + grab;
        }
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
