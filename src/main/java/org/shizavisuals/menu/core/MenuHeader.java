package org.shizavisuals.menu.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.IconButton;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.shizavisuals.utils.text.StringUtil;
import org.shizavisuals.utils.render.Textures;

public final class MenuHeader extends Component {
    private final IconButton searchButton = new IconButton(Textures.Header.SEARCH, 0, null)
            .radius(8)
            .tint(Theme.Colors.ICON, Theme.Colors.ICON)
            .activeTint(ColorUtil.WHITE)
            .hoverBackground(Theme.Colors.SURFACE_HOVER)
            .activeBackground(Theme.Colors.SURFACE_ACTIVE, Theme.Colors.SURFACE_ACTIVE);
    private final IconButton chevronLeftButton = new IconButton(Textures.Header.CHEVRON_LEFT, 0, null)
            .tint(Theme.Colors.ICON, Theme.Colors.ICON);
    private final IconButton chevronRightButton = new IconButton(Textures.Header.CHEVRON_RIGHT, 0, null)
            .tint(Theme.Colors.ICON, Theme.Colors.ICON);
    private static final int CONTENT_RIGHT_EDGE = 1024 - 16;
    private static final int ACTION_BUTTON_GAP = 8;
    private static final int ACTION_DIVIDER_GAP = 8;
    private static final int PROFILE_DIVIDER_GAP = 20;
    private static final int PROFILE_HIT_HEIGHT = 24;
    private static final int PROFILE_HIT_PADDING_X = 6;
    private static final int PROFILE_POPUP_WIDTH = 185;
    private static final int PROFILE_POPUP_HEIGHT = 143;
    private static final int PROFILE_POPUP_PADDING = 12;
    private static final int PROFILE_POPUP_GAP = 8;
    private static final int PROFILE_POPUP_RADIUS = 16;
    private static final int PROFILE_POPUP_HEADER_HEIGHT = 32;
    private static final int PROFILE_POPUP_AVATAR_SIZE = 32;
    private static final int PROFILE_POPUP_AVATAR_RADIUS = 10;
    private static final int PROFILE_POPUP_BADGE_HEIGHT = 14;
    private static final int PROFILE_POPUP_BADGE_RADIUS = 65;
    private static final int PROFILE_POPUP_INFO_HEIGHT = 16;
    private static final int PROFILE_POPUP_SWATCH_SIZE = 12;
    private static final int PROFILE_POPUP_SWATCH_GAP = 4;
    private static final long POPUP_ANIMATION_MS = 150L;
    private static final long ACCENT_ANIMATION_MS = 180L;
    private static final Identifier[] ACTION_TEXTURES = {
            Textures.Header.SETTINGS, Textures.Header.FRIENDS,
            Textures.Header.PROFILE_ADD, Textures.Header.DOCUMENT
    };
    private static final MenuPage[] ACTION_PAGES = MenuPage.actionPages();

    private int mouseX;
    private int mouseY;
    private MenuPage activePage = MenuPage.NONE;
    private int focusedAction = -1;
    private String username = "";
    private float profileX;
    private float profileY;
    private float profileWidth;
    private float profileHeight;
    private float popupX;
    private float popupY;
    private float popupWidth;
    private float popupHeight;
    private int screenHeight;
    private boolean profilePopupOpen;
    private final Animation profilePopupAnimation = new Animation(POPUP_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    private final Animation accentAnimation = new Animation(ACCENT_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    private int animatedAccentTarget = Theme.accentIndex();

    public MenuHeader() {
        float accent = Theme.accentIndex();
        this.profilePopupAnimation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
        this.accentAnimation.animate(accent, accent, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    private MenuOverlayState state;

    public void place(Component frame, MenuOverlayState state, Minecraft minecraft, int mouseX, int mouseY) {
        attach(frame, frame.x(), frame.y(), frame.width(), frame.px(Theme.Sizes.HEADER_HEIGHT));
        this.state = state;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.activePage = state.page();
        this.focusedAction = state.focusedHeaderAction();
        this.username = org.shizavisuals.utils.text.NameProtectUtil.protect(minecraft.getUser().getName());
        this.screenHeight = minecraft.getWindow().getGuiScaledHeight();
        updateProfileBounds();
        updatePopupBounds();
        syncAccentAnimation();
    }

    public MenuPage pageAt(float mouseX, float mouseY) {
        float hitSize = px(Theme.Sizes.HEADER_CONTROL_BUTTON_SIZE);
        float iconSize = px(Theme.Sizes.HEADER_ICON_SIZE);
        float hitY = controlButtonY();
        if (mouseY < hitY || mouseY > hitY + hitSize) {
            return MenuPage.NONE;
        }

        for (int index = 0; index < ACTION_PAGES.length; index++) {
            float buttonX = actionButtonX(index);
            float iconX = buttonX + (hitSize - iconSize) / 2;
            float hitX = iconX - (hitSize - iconSize) / 2;
            if (mouseX >= hitX && mouseX <= hitX + hitSize) {
                return ACTION_PAGES[index];
            }
        }
        return MenuPage.NONE;
    }

    public boolean isSearchAt(float mouseX, float mouseY) {
        return this.searchButton.contains(mouseX, mouseY);
    }

    public boolean isProfileAt(float mouseX, float mouseY) {
        return mouseX >= this.profileX && mouseX <= this.profileX + this.profileWidth
                && mouseY >= this.profileY && mouseY <= this.profileY + this.profileHeight;
    }

    public boolean isProfilePopupAt(float mouseX, float mouseY) {
        return this.profilePopupOpen
                && mouseX >= this.popupX
                && mouseX <= this.popupX + this.popupWidth
                && mouseY >= this.popupY
                && mouseY <= this.popupY + this.popupHeight;
    }

    public boolean handleMouseButton(float mouseX, float mouseY, int button) {
        boolean profileHit = isProfileAt(mouseX, mouseY);
        boolean popupHit = isProfilePopupAt(mouseX, mouseY);
        if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return profileHit || popupHit;
        }
        if (profileHit) {
            if (this.profilePopupOpen) {
                closeProfilePopup();
            } else {
                openProfilePopup();
            }
            return true;
        }
        if (!this.profilePopupOpen) {
            return false;
        }
        if (popupHit) {
            int accentIndex = accentAt(mouseX, mouseY);
            if (accentIndex >= 0) {
                setAccentIndex(accentIndex);
            }
            return true;
        }
        closeProfilePopup();
        return false;
    }

    public boolean handleScroll(float mouseX, float mouseY, double vertical) {
        if (!this.profilePopupOpen || accentAt(mouseX, mouseY) < 0) {
            return false;
        }
        int next = Math.floorMod(Theme.accentIndex() + (vertical > 0 ? -1 : 1), Theme.accentCount());
        setAccentIndex(next);
        return true;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        drawNavigation();
        drawBrand();
        drawProfile(minecraft);
        drawActions();
        Render2DUtil.rect(x(), sy(55), width(), 1).color(Theme.Colors.DIVIDER_HEADER).draw();
        float popupProgress = this.profilePopupAnimation.getValue();
        if (popupProgress > 0.001F) {
            drawProfilePopup(popupProgress);
        }
    }

    private void openProfilePopup() {
        this.profilePopupOpen = true;
        this.profilePopupAnimation.animate(0.0F, 1.0F, POPUP_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    }

    private void closeProfilePopup() {
        if (this.profilePopupOpen) {
            this.profilePopupAnimation.animate(this.profilePopupAnimation.getValue(), 0.0F, 120L, Animation.Easing.EASE_OUT_QUAD);
        }
        this.profilePopupOpen = false;
    }

    private void setAccentIndex(int index) {
        float from = this.accentAnimation.getValue();
        Theme.setAccentIndex(index);
        MenuConfigStore.saveAccentIndex(Theme.accentIndex());
        this.animatedAccentTarget = Theme.accentIndex();
        this.accentAnimation.animate(from, this.animatedAccentTarget, ACCENT_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    }

    private void syncAccentAnimation() {
        int target = Theme.accentIndex();
        if (target == this.animatedAccentTarget) {
            return;
        }
        float from = this.accentAnimation.getValue();
        this.animatedAccentTarget = target;
        this.accentAnimation.animate(from, target, ACCENT_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    }

    private void drawNavigation() {
        float currentX = sx(Theme.Sizes.HEADER_CONTROLS_X);
        float controlsY = sy(Theme.Sizes.HEADER_CONTROLS_Y);
        float controlsHeight = px(Theme.Sizes.HEADER_CONTROLS_HEIGHT);
        float trafficSize = px(Theme.Sizes.HEADER_TRAFFIC_SIZE);
        float trafficY = controlsY + (controlsHeight - trafficSize) / 2;
        float trafficRadius = trafficSize / 2;

        Render2DUtil.rect(currentX, trafficY, trafficSize, trafficSize).color(Theme.Colors.TRAFFIC_CLOSE).radius(trafficRadius).draw();
        currentX += trafficSize + px(Theme.Sizes.HEADER_TRAFFIC_GAP);
        Render2DUtil.rect(currentX, trafficY, trafficSize, trafficSize).color(Theme.Colors.TRAFFIC_MINIMIZE).radius(trafficRadius).draw();
        currentX += trafficSize + px(Theme.Sizes.HEADER_TRAFFIC_GAP);
        Render2DUtil.rect(currentX, trafficY, trafficSize, trafficSize).color(Theme.Colors.TRAFFIC_MAXIMIZE).radius(trafficRadius).draw();
        currentX += trafficSize + px(Theme.Sizes.HEADER_CONTROLS_GAP);

        float dividerY = controlsY + (controlsHeight - px(Theme.Sizes.HEADER_DIVIDER_HEIGHT)) / 2;
        Render2DUtil.rect(currentX, dividerY, 1, px(Theme.Sizes.HEADER_DIVIDER_HEIGHT)).color(Theme.Colors.OUTLINES_LARGE).draw();
        currentX += 1 + px(Theme.Sizes.HEADER_CONTROLS_GAP);

        float buttonSize = px(Theme.Sizes.HEADER_CONTROL_BUTTON_SIZE);
        float buttonY = controlsY + (controlsHeight - buttonSize) / 2;
        int headerIconSize = Theme.Sizes.HEADER_ICON_SIZE;
        Minecraft minecraft = Minecraft.getInstance();

        this.searchButton
                .placeAt(this, currentX, buttonY, buttonSize, this.mouseX, this.mouseY)
                .iconSize(headerIconSize)
                .active(this.activePage == MenuPage.SEARCH)
                .render(minecraft, null);
        currentX += buttonSize + px(Theme.Sizes.HEADER_CONTROLS_GAP);

        this.chevronLeftButton
                .placeAt(this, currentX, buttonY, buttonSize, this.mouseX, this.mouseY)
                .iconSize(headerIconSize)
                .enabled(this.state != null && this.state.canGoBack())
                .render(minecraft, null);
        currentX += px(Theme.Sizes.HEADER_CONTROL_BUTTON_SIZE);
        this.chevronRightButton
                .placeAt(this, currentX, buttonY, buttonSize, this.mouseX, this.mouseY)
                .iconSize(headerIconSize)
                .enabled(this.state != null && this.state.canGoForward())
                .render(minecraft, null);
    }

    private void drawBrand() {
        float pillWidth = Math.round(px(Theme.Sizes.HEADER_BRAND_WIDTH));
        float pillX = x() + (width() - pillWidth) / 2;
        Render2DUtil.rect(pillX, sy(12), pillWidth, px(Theme.Sizes.HEADER_BRAND_HEIGHT))
                .color(Theme.Colors.CARD)
                .radius(px(Theme.Sizes.HEADER_BRAND_RADIUS))
                .border(Math.max(0.5F, px(0.5F)), Theme.Colors.DIVIDER_HEADER)
                .draw();
        Render2DUtil.text(pillX + pillWidth / 2.0F,
                        UiFonts.sfProDisplay().centeredTextY(sy(12) + px(Theme.Sizes.HEADER_BRAND_HEIGHT) / 2.0F, px(12.0F)),
                        px(12.0F), "shizavisuals")
                .style(UiFontStyle.MEDIUM)
                .color(Theme.Colors.PRIMARY_BRIGHT)
                .align(org.shizavisuals.utils.render.gui.TextAlign.CENTER)
                .draw();
    }

    private void drawProfile(Minecraft minecraft) {
        String label = StringUtil.abbreviate(org.shizavisuals.utils.text.NameProtectUtil.protect(minecraft.getUser().getName()), 18);
        if (isProfileAt(this.mouseX, this.mouseY) || this.profilePopupOpen) {
            Render2DUtil.rect(this.profileX, this.profileY, this.profileWidth, this.profileHeight)
                    .color(this.profilePopupOpen ? Theme.Colors.SURFACE_ACTIVE : Theme.Colors.SURFACE_HOVER)
                    .radius(px(10))
                    .draw();
        }

        float avatarSize = px(Theme.Sizes.HEADER_PROFILE_ICON_SIZE);
        float avatarY = this.profileY + (this.profileHeight - avatarSize) / 2;
        float contentX = this.profileX + px(PROFILE_HIT_PADDING_X);
        float gap = px(8);
        Render2DUtil.texture(contentX, avatarY, avatarSize, avatarSize, Textures.Header.DEV_AVATAR).draw();
        Render2DUtil.text(contentX + avatarSize + gap,
                        UiFonts.sfProDisplay().centeredTextY(this.profileY + this.profileHeight / 2.0F, px(12.0F)),
                        px(12.0F), label)
                .style(UiFontStyle.REGULAR_TRACKED)
                .color(Theme.Colors.TEXT)
                .draw();
    }

    private void drawProfilePopup(float progress) {
        float alpha = progress;
        float lift = -6.0F * (1.0F - progress);
        float drawY = this.popupY + lift;

        Render2DUtil.rect(this.popupX, drawY, this.popupWidth, this.popupHeight)
                .color(ColorUtil.TRANSPARENT)
                .radius(px(PROFILE_POPUP_RADIUS))
                .shadow(ColorUtil.multiplyAlpha(Theme.Colors.POPUP_SHADOW, alpha), px(12.0F))
                .draw();
        Render2DUtil.rect(this.popupX, drawY, this.popupWidth, this.popupHeight)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.BACKGROUND_PRIMARY_50, alpha))
                .radius(px(PROFILE_POPUP_RADIUS))
                .border(Math.max(0.5F, px(0.5F)), ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha))
                .blur(px(8.0F), alpha)
                .draw(); 

        float contentX = this.popupX + px(PROFILE_POPUP_PADDING);
        float currentY = drawY + px(PROFILE_POPUP_PADDING);
        drawPopupHeader(contentX, currentY, alpha);
        currentY += px(PROFILE_POPUP_HEADER_HEIGHT) + px(PROFILE_POPUP_GAP);
        drawPopupDivider(contentX, currentY, alpha);
        currentY += Math.max(1, px(1)) + px(PROFILE_POPUP_GAP);
        drawPopupInfoRow(contentX, currentY, "Username", StringUtil.abbreviate(this.username, 12), alpha);
        currentY += px(PROFILE_POPUP_INFO_HEIGHT) + px(PROFILE_POPUP_GAP);
        drawPopupInfoRow(contentX, currentY, "Status", "Active", alpha);
        currentY += px(PROFILE_POPUP_INFO_HEIGHT) + px(PROFILE_POPUP_GAP);
        drawPopupDivider(contentX, currentY, alpha);
        currentY += Math.max(1, px(1)) + px(PROFILE_POPUP_GAP);
        drawPopupAccentRow(currentY, alpha);
    }

    private void drawPopupHeader(float contentX, float y, float alpha) {
        float avatarSize = px(PROFILE_POPUP_AVATAR_SIZE);
        Render2DUtil.rect(contentX, y, avatarSize, avatarSize)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.CONTROL_ALT, alpha))
                .radius(px(PROFILE_POPUP_AVATAR_RADIUS))
                .draw();
        Render2DUtil.texture(contentX, y, avatarSize, avatarSize, Textures.Header.DEV_AVATAR)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                .draw();

        float textX = contentX + avatarSize + px(6);
        float badgeWidth = px(40);
        float badgeHeight = px(PROFILE_POPUP_BADGE_HEIGHT);
        float badgeY = y + px(2);
        Render2DUtil.rect(textX, badgeY, badgeWidth, badgeHeight)
                .color(ColorUtil.multiplyAlpha(ColorUtil.withAlpha(Theme.Colors.SYSTEM_INFORMATION, 51), alpha))
                .radius(px(PROFILE_POPUP_BADGE_RADIUS))
                .draw();
        float badgeTextSize = px(8.0F);
        float badgeTextY = UiFonts.sfProDisplay().centeredTextY(badgeY + badgeHeight / 2.0F, badgeTextSize);
        Render2DUtil.text(textX + badgeWidth / 2.0F, badgeTextY, badgeTextSize, "ACTIVE")
                .style(UiFontStyle.MEDIUM)
                .align(org.shizavisuals.utils.render.gui.TextAlign.CENTER)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.SYSTEM_INFORMATION, alpha))
                .draw();
        float usernameCenterY = y + px(2 + PROFILE_POPUP_BADGE_HEIGHT + PROFILE_POPUP_AVATAR_SIZE) / 2.0F;
        Render2DUtil.text(textX, UiFonts.sfProDisplay().centeredTextY(usernameCenterY, px(12.0F)), px(12.0F), StringUtil.abbreviate(this.username, 16))
                .style(UiFontStyle.SEMIBOLD)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TITLE, alpha))
                .draw();
    }

    private void drawPopupInfoRow(float contentX, float y, String label, String value, float alpha) {
        float rowTextY = UiFonts.sfProDisplay().centeredTextY(y + px(PROFILE_POPUP_INFO_HEIGHT) / 2.0F, px(10.0F));
        Render2DUtil.text(contentX, rowTextY, px(10.0F), label)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.TEXT_TEXT, alpha))
                .draw();
        Render2DUtil.text(this.popupX + this.popupWidth - px(PROFILE_POPUP_PADDING), rowTextY, px(10.0F), value)
                .style(UiFontStyle.MEDIUM)
                .align(org.shizavisuals.utils.render.gui.TextAlign.RIGHT)
                .color(ColorUtil.multiplyAlpha(Theme.Colors.SECONDARY, alpha))
                .draw();
    }

    private void drawPopupDivider(float contentX, float y, float alpha) {
        Render2DUtil.rect(contentX, y, this.popupWidth - px(PROFILE_POPUP_PADDING * 2), Math.max(1, px(1)))
                .color(ColorUtil.multiplyAlpha(Theme.Colors.OUTLINES_MEDIUM, alpha))
                .draw();
    }

    private void drawPopupAccentRow(float y, float alpha) {
        float size = px(PROFILE_POPUP_SWATCH_SIZE);
        float gap = px(PROFILE_POPUP_SWATCH_GAP);
        float rowWidth = size * Theme.accentCount() + gap * (Theme.accentCount() - 1);
        float rowX = this.popupX + (this.popupWidth - rowWidth) / 2;
        for (int index = 0; index < Theme.accentCount(); index++) {
            float swatchX = rowX + index * (size + gap);
            Render2DUtil.rect(swatchX, y, size, size)
                    .color(ColorUtil.multiplyAlpha(Theme.accent(index), alpha))
                    .radius(px(4))
                    .draw();
        }
        float indicatorX = rowX + this.accentAnimation.getValue() * (size + gap);
        Render2DUtil.rect(indicatorX - Math.max(1, px(1)), y - Math.max(1, px(1)), size + Math.max(2, px(2)), size + Math.max(2, px(2)))
                .color(ColorUtil.TRANSPARENT)
                .radius(px(5))
                .border(Math.max(0.5F, px(1.0F)), ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                .draw();
    }

    private void drawActions() {
        Render2DUtil.rect(actionDividerX(), sy(22), 1, px(Theme.Sizes.HEADER_DIVIDER_HEIGHT)).color(Theme.Colors.SEPARATOR).draw();
        float hitSize = px(Theme.Sizes.HEADER_CONTROL_BUTTON_SIZE);
        float iconSize = px(Theme.Sizes.HEADER_ICON_SIZE);
        for (int index = 0; index < ACTION_PAGES.length; index++) {
            float hitX = actionButtonX(index);
            float iconX = hitX + (hitSize - iconSize) / 2;
            boolean active = this.activePage == ACTION_PAGES[index];
            Render2DUtil.texture(iconX, controlIconY(), iconSize, iconSize, ACTION_TEXTURES[index])
                    .color(active ? ColorUtil.WHITE : Theme.Colors.ICON)
                    .draw();
        }
    }

    private float searchButtonX() {
        float trafficSize = px(Theme.Sizes.HEADER_TRAFFIC_SIZE);
        return sx(Theme.Sizes.HEADER_CONTROLS_X)
                + trafficSize * 3
                + px(Theme.Sizes.HEADER_TRAFFIC_GAP) * 2
                + px(Theme.Sizes.HEADER_CONTROLS_GAP)
                + 1
                + px(Theme.Sizes.HEADER_CONTROLS_GAP);
    }

    private float controlButtonY() {
        return sy(Theme.Sizes.HEADER_CONTROLS_Y)
                + (px(Theme.Sizes.HEADER_CONTROLS_HEIGHT) - px(Theme.Sizes.HEADER_CONTROL_BUTTON_SIZE)) / 2;
    }

    public boolean isChevronLeftAt(float mouseX, float mouseY) {
        return this.chevronLeftButton.contains(mouseX, mouseY);
    }

    public boolean isChevronRightAt(float mouseX, float mouseY) {
        return this.chevronRightButton.contains(mouseX, mouseY);
    }

    private float leftChevronX() {
        return searchButtonX() + px(Theme.Sizes.HEADER_CONTROL_BUTTON_SIZE) + px(Theme.Sizes.HEADER_CONTROLS_GAP);
    }

    private float rightChevronX() {
        return leftChevronX() + px(Theme.Sizes.HEADER_CONTROL_BUTTON_SIZE);
    }

    private float controlIconY() {
        return controlButtonY() + px(Theme.Sizes.HEADER_CONTROL_BUTTON_PADDING);
    }

    private float actionButtonX(int index) {
        int buttonDesignX = CONTENT_RIGHT_EDGE
                - Theme.Sizes.HEADER_ICON_SIZE
                - Theme.Sizes.HEADER_CONTROL_BUTTON_PADDING
                - (ACTION_PAGES.length - 1 - index) * (Theme.Sizes.HEADER_CONTROL_BUTTON_SIZE + ACTION_BUTTON_GAP);
        return sx(buttonDesignX);
    }

    private float actionDividerX() {
        return actionButtonX(0) - px(ACTION_DIVIDER_GAP) - 1;
    }

    private void updateProfileBounds() {
        String label = StringUtil.abbreviate(this.username, 18);
        float fontSize = 12.0F;
        MsdfFont font = UiFonts.sfProDisplay();
        float letterSpacing = fontSize * UiFontStyle.REGULAR_TRACKED.letterSpacingEm();
        float textWidth = font.measureWidth(label, px(fontSize), px(letterSpacing));
        float avatarSize = px(Theme.Sizes.HEADER_PROFILE_ICON_SIZE);
        float gap = px(8);
        float totalWidth = avatarSize + gap + textWidth;
        float rightBoundary = actionDividerX() - px(PROFILE_DIVIDER_GAP);
        this.profileX = rightBoundary - totalWidth - px(PROFILE_HIT_PADDING_X);
        this.profileY = sy(16);
        this.profileWidth = totalWidth + px(PROFILE_HIT_PADDING_X * 2);
        this.profileHeight = px(PROFILE_HIT_HEIGHT);
    }

    private void updatePopupBounds() {
        float rightBoundary = actionDividerX() - px(PROFILE_DIVIDER_GAP);
        this.popupWidth = px(PROFILE_POPUP_WIDTH);
        this.popupHeight = px(PROFILE_POPUP_HEIGHT);
        this.popupX = rightBoundary - this.popupWidth;
        float gap = px(PROFILE_POPUP_GAP);
        float topY = y() - this.popupHeight - gap;
        float bottomY = y() + height() + gap;
        float maxY = Math.max(0, this.screenHeight - this.popupHeight);
        this.popupY = topY >= 0 ? topY : Math.min(bottomY, maxY);
    }

    private int accentAt(float mouseX, float mouseY) {
        if (!isProfilePopupAt(mouseX, mouseY)) {
            return -1;
        }
        float size = px(PROFILE_POPUP_SWATCH_SIZE);
        float gap = px(PROFILE_POPUP_SWATCH_GAP);
        float rowWidth = size * Theme.accentCount() + gap * (Theme.accentCount() - 1);
        float rowX = this.popupX + (this.popupWidth - rowWidth) / 2;
        float rowY = accentRowY();
        for (int index = 0; index < Theme.accentCount(); index++) {
            float swatchX = rowX + index * (size + gap);
            if (mouseX >= swatchX && mouseX <= swatchX + size && mouseY >= rowY && mouseY <= rowY + size) {
                return index;
            }
        }
        return -1;
    }

    private float accentRowY() {
        return this.popupY
                + px(PROFILE_POPUP_PADDING + PROFILE_POPUP_HEADER_HEIGHT + PROFILE_POPUP_GAP)
                + Math.max(1, px(1))
                + px(PROFILE_POPUP_GAP + PROFILE_POPUP_INFO_HEIGHT + PROFILE_POPUP_GAP + PROFILE_POPUP_INFO_HEIGHT + PROFILE_POPUP_GAP)
                + Math.max(1, px(1))
                + px(PROFILE_POPUP_GAP);
    }
}
