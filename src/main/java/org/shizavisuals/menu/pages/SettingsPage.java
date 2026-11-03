package org.shizavisuals.menu.pages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.core.MenuAppearance;
import org.shizavisuals.menu.core.MenuBackground;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.menu.core.MenuOverlayState;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.i18n.UiLanguage;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.DropdownComponent;
import org.shizavisuals.menu.ui.popups.DropdownPopup;
import org.shizavisuals.menu.ui.controls.IconButton;
import org.shizavisuals.menu.ui.controls.MarqueeText;
import org.shizavisuals.menu.ui.SmoothScroll;
import org.shizavisuals.menu.ui.controls.ModeComponent;
import org.shizavisuals.menu.ui.PageComponent;
import org.shizavisuals.menu.ui.controls.SliderComponent;
import org.shizavisuals.menu.ui.controls.ToggleComponent;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.shizavisuals.utils.render.gui.UiFontStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

public final class SettingsPage extends PageComponent {
    private static final int ACCENT_SWATCH_COUNT = 10;
    private static final int ACCENT_SWATCH_Y = 256;
    private static final int ACCENT_SWATCH_SIZE = 18;
    private static final int ACCENT_SWATCH_GAP = 4;
    private static final int ACCENT_SWATCH_STEP = ACCENT_SWATCH_SIZE + ACCENT_SWATCH_GAP;
    private static final int ACCENT_SWATCH_WIDTH = ACCENT_SWATCH_SIZE * ACCENT_SWATCH_COUNT + ACCENT_SWATCH_GAP * (ACCENT_SWATCH_COUNT - 1);
    private static final int ACCENT_SWATCH_RIGHT = 488;
    private static final int ACCENT_SWATCH_X = ACCENT_SWATCH_RIGHT - ACCENT_SWATCH_WIDTH;

    private static final int TOP_DROPDOWN_Y = 104;
    private static final int RESET_BUTTON_SIZE = 24;
    private static final int RESET_ICON_SIZE = 16;
    private static final int RESET_PADDING = 4;
    private static final int RESET_BUTTON_X = 1024 - 32 - RESET_BUTTON_SIZE;
    private static final int RESET_BUTTON_Y = TOP_DROPDOWN_Y + (36 - RESET_BUTTON_SIZE) / 2;
    private static final int TOP_DROPDOWN_RIGHT = RESET_BUTTON_X - 8;
    private static final String[] FPS_MODES = {"FPS Boost", "Balanced", "Quality"};
    private static final String[] LANGUAGE_MODES = UiLanguage.canonicalNames();
    private static final String[] BLUR_MODES = {"No blur", "Soft blur", "Strong blur"};
    private static final String[] CORNER_MODES = {"Small", "Medium", "Large"};
    private static final String[] BACKGROUND_MODES = MenuBackground.labels();

    private static final int CONTENT_TOP = 200;

    private static final int BACKGROUND_ROW_Y = 405;
    private static final int CORNER_ROW_Y = 459;
    private static final int SOUND_LABEL_Y = 535;
    private static final int SOUND_CARD_Y = 553;
    private static final int SOUND_ROW_Y = 565;
    private static final int CONTENT_BOTTOM = 635;

    private final List<Component> children = new ArrayList<>();
    private final Map<String, MarqueeText> descriptionLabels = new HashMap<>();
    private final SliderComponent themeSlider = new SliderComponent(() -> this.themeValue, value -> this.themeValue = value, Theme.Colors.OUTLINES_SMALL, Theme.getAccent(), ColorUtil.WHITE);
    private final SliderComponent uiScaleSlider = new SliderComponent(() -> this.pendingUiScaleValue, value -> this.pendingUiScaleValue = value, Theme.Colors.OUTLINES_SMALL, Theme.getAccent(), ColorUtil.WHITE)
            .step(1.0F / (MenuOverlayState.MAX_UI_SCALE_PERCENT - MenuOverlayState.MIN_UI_SCALE_PERCENT))
            .onRelease(this::applyUiScale);
    private final SliderComponent backgroundDimSlider = new SliderComponent(() -> this.backgroundDimValue, value -> {
        this.backgroundDimValue = value;
        MenuAppearance.setBackgroundDim(value);
    }, Theme.Colors.OUTLINES_SMALL, Theme.getAccent(), ColorUtil.WHITE);
    private final ToggleComponent lowPerformanceToggle = new ToggleComponent(() -> this.lowPerformance, () -> this.lowPerformance = !this.lowPerformance, ToggleComponent.Style.CIRCLE, Theme.Colors.CONTROL, Theme.getAccent());
    private final ToggleComponent reduceShadowsToggle = new ToggleComponent(() -> this.reduceShadows, () -> this.reduceShadows = !this.reduceShadows, ToggleComponent.Style.CIRCLE, Theme.Colors.CONTROL, Theme.getAccent());
    private final ToggleComponent cacheUiToggle = new ToggleComponent(() -> this.cacheUi, () -> this.cacheUi = !this.cacheUi, ToggleComponent.Style.CIRCLE, Theme.Colors.CONTROL, Theme.getAccent());
    private final ToggleComponent soundToggle = new ToggleComponent(() -> this.sound, () -> this.sound = !this.sound, ToggleComponent.Style.CIRCLE, Theme.Colors.CONTROL, Theme.getAccent());
    private final ModeComponent fpsModeDropdown = new ModeComponent(() -> MenuText.option(FPS_MODES[this.fpsMode]));
    private final ModeComponent languageDropdown = new ModeComponent(() -> MenuText.option(LANGUAGE_MODES[this.languageMode]));
    private final ModeComponent blurModeDropdown = new ModeComponent(() -> MenuText.option(BLUR_MODES[this.blurMode]));
    private final ModeComponent roundedModeDropdown = new ModeComponent(() -> MenuText.option(CORNER_MODES[this.roundedMode]));

    private final DropdownPopup fpsPopup = new DropdownPopup(() -> FPS_MODES, () -> this.fpsMode, index -> this.fpsMode = index);
    private final DropdownPopup languagePopup = new DropdownPopup(() -> LANGUAGE_MODES, () -> this.languageMode, index -> {
        this.languageMode = index;
        UiLanguage.set(UiLanguage.byIndex(index));
    });
    private final DropdownPopup blurPopup = new DropdownPopup(() -> BLUR_MODES, () -> this.blurMode, index -> {
        this.blurMode = index;
        MenuAppearance.setBlurMode(index);
    });
    private final DropdownPopup cornersPopup = new DropdownPopup(() -> CORNER_MODES, () -> this.roundedMode, index -> {
        this.roundedMode = index;
        applyPanelRadius();
    });
    private final ModeComponent backgroundDropdown = new ModeComponent(() -> MenuText.option(BACKGROUND_MODES[this.backgroundMode]));
    private final DropdownPopup backgroundPopup = new DropdownPopup(() -> BACKGROUND_MODES, () -> this.backgroundMode, index -> {
        this.backgroundMode = index;
        MenuAppearance.setBackground(MenuBackground.byIndex(index));
    });
    private final List<DropdownPopup> popups = List.of(this.fpsPopup, this.languagePopup, this.blurPopup, this.cornersPopup, this.backgroundPopup);
    private final IconButton resetButton = new IconButton(Textures.Icons.REFRESH_CCW, RESET_ICON_SIZE, this::resetSettings)
            .tint(Theme.Colors.SECONDARY, Theme.Colors.TEXT_TITLE);

    private float themeValue = MenuConfigStore.getFloat("themeValue", 0.38F);
    private float pendingUiScaleValue = MenuOverlayState.DEFAULT_UI_SCALE_VALUE;

    private float backgroundDimValue = MathUtil.clamp01(MenuConfigStore.getFloat(
            "backgroundDim",
            MenuConfigStore.getFloat("blurStrength", 0.35F)
    ));
    private boolean lowPerformance = MenuConfigStore.getBoolean("lowPerformance", true);
    private boolean reduceShadows = MenuConfigStore.getBoolean("reduceShadows", false);
    private boolean cacheUi = MenuConfigStore.getBoolean("cacheUi", true);
    private boolean sound = MenuConfigStore.getBoolean("sound", true);
    private int blurMode = (int) MathUtil.clamp(MenuConfigStore.getInt("blurMode", 1), 0, BLUR_MODES.length - 1);
    private int fpsMode = (int) MathUtil.clamp(MenuConfigStore.getInt("fpsMode", 0), 0, FPS_MODES.length - 1);
    private int languageMode = UiLanguage.current().ordinal();
    private int roundedMode = (int) MathUtil.clamp(
            MenuConfigStore.getInt("roundedMode", Theme.Sizes.DEFAULT_PANEL_RADIUS_INDEX), 0, Theme.Sizes.PANEL_RADII.length - 1);
    private int backgroundMode = (int) MathUtil.clamp(
            MenuConfigStore.getInt("backgroundMode", 0), 0, BACKGROUND_MODES.length - 1);
    private final Animation accentAnimation = new Animation(180L, Animation.Easing.EASE_OUT_QUAD);
    private final SmoothScroll scroll = new SmoothScroll();
    private float scrollY;
    private int animatedAccentTarget;

    public SettingsPage() {
        this.children.add(this.themeSlider);
        this.children.add(this.uiScaleSlider);
        this.children.add(this.backgroundDimSlider);
        this.children.add(this.lowPerformanceToggle);
        this.children.add(this.reduceShadowsToggle);
        this.children.add(this.cacheUiToggle);
        this.children.add(this.soundToggle);
        Theme.setAccentIndex(MenuConfigStore.getInt("accentIndex", 0));
        MenuAppearance.setBlurMode(this.blurMode);
        MenuAppearance.setBackground(MenuBackground.byIndex(this.backgroundMode));
        MenuAppearance.setBackgroundDim(this.backgroundDimValue);
        this.animatedAccentTarget = Theme.accentIndex();
        this.accentAnimation.animate(this.animatedAccentTarget, this.animatedAccentTarget, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    @Override
    protected void onLayout() {
        if (!this.uiScaleSlider.isDragging()) {
            this.pendingUiScaleValue = this.state.uiScaleValue();
        }
        this.scrollY = this.scroll.update(maxScroll());
        placeControls();
    }

    private float maxScroll() {
        float viewportBottom = designY(y() + height());
        return Math.max(0.0F, CONTENT_BOTTOM - viewportBottom);
    }

    private int oy(int designY) {
        return designY >= CONTENT_TOP ? designY - Math.round(this.scrollY) : designY;
    }

    public boolean handleMouseButton(int mouseX, int mouseY, int button) {
        boolean consumed = processMouseButton(mouseX, mouseY, button);
        if (consumed) {
            persistSettings();
        }
        return consumed;
    }

    private boolean processMouseButton(int mouseX, int mouseY, int button) {
        if (!contentContains(mouseX, mouseY)) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (DropdownPopup popup : this.popups) {
                if (popup.isOpen() && popup.handleClick(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        for (Component child : this.children) {
            if (child.handleClick(mouseX, mouseY)) {
                return true;
            }
        }
        if (this.fpsModeDropdown.handleClick(mouseX, mouseY)) {
            openPopup(this.fpsPopup);
            return true;
        }
        if (this.languageDropdown.handleClick(mouseX, mouseY)) {
            openPopup(this.languagePopup);
            return true;
        }
        if (this.blurModeDropdown.handleClick(mouseX, mouseY)) {
            openPopup(this.blurPopup);
            return true;
        }
        if (this.roundedModeDropdown.handleClick(mouseX, mouseY)) {
            openPopup(this.cornersPopup);
            return true;
        }
        if (this.backgroundDropdown.handleClick(mouseX, mouseY)) {
            openPopup(this.backgroundPopup);
            return true;
        }

        if (this.resetButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (hit(mouseX, mouseY, ACCENT_SWATCH_X, oy(ACCENT_SWATCH_Y) - 6, ACCENT_SWATCH_WIDTH, 32)) {
            setAccentIndex((int) MathUtil.clamp((designX(mouseX) - ACCENT_SWATCH_X) / ACCENT_SWATCH_STEP, 0, Theme.accentCount() - 1));
        }
        return true;
    }

    private void openPopup(DropdownPopup popup) {
        for (DropdownPopup other : this.popups) {
            if (other != popup) {
                other.closeImmediately();
            }
        }
        popup.toggle();
    }

    public void drag(int mouseX, int mouseY) {
        this.themeSlider.drag(mouseX);
        this.uiScaleSlider.drag(mouseX);
        this.backgroundDimSlider.drag(mouseX);
    }

    public void releasePointer() {
        boolean wasDragging = this.themeSlider.isDragging()
                || this.uiScaleSlider.isDragging()
                || this.backgroundDimSlider.isDragging();
        this.themeSlider.releasePointer();
        this.uiScaleSlider.releasePointer();
        this.backgroundDimSlider.releasePointer();
        if (wasDragging) {
            persistSettings();
        }
    }

    public void handleScroll(int mouseX, int mouseY, double vertical) {

        if (hit(mouseX, mouseY, ACCENT_SWATCH_X, oy(ACCENT_SWATCH_Y) - 6, ACCENT_SWATCH_WIDTH, 32)) {
            int next = Math.floorMod(Theme.accentIndex() + (vertical > 0 ? -1 : 1), Theme.accentCount());
            setAccentIndex(next);
            persistSettings();
            return;
        }
        if (contentContains(mouseX, mouseY)) {
            this.scroll.scroll(vertical, maxScroll());
        }
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (this.progress <= 0.001F) {
            return;
        }
        pageHeader(
                MenuText.ui("Settings"),
                MenuText.ui("Customize your cheat client to meet your needs with just one click.")
        );
        this.resetButton.render(minecraft, guiGraphicsExtractor);
        this.fpsModeDropdown.render(minecraft, guiGraphicsExtractor);

        Render2DUtil.pushScissor(x(), sy(CONTENT_TOP), width(), y() + height() - sy(CONTENT_TOP));
        renderBody(minecraft, guiGraphicsExtractor);
        Render2DUtil.popScissor();

        for (DropdownPopup popup : this.popups) {
            popup.render(minecraft, guiGraphicsExtractor);
        }
    }

    private void renderBody(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        sectionLabel(32, 214, "Theme Editor");
        sectionLabel(536, 214, "Interface");

        sectionCard(16, 234, 488, 171);
        sectionCard(520, 234, 488, 283);
        sectionLabel(32, 423, "Performance");
        sectionCard(16, 442, 488, 299);
        sectionLabel(536, SOUND_LABEL_Y, "Controls");
        sectionCard(520, SOUND_CARD_Y, 488, 66);

        settingText(32, 246, 39, "Client Color",
                "Adjust the main accent color of the interface.", ACCENT_SWATCH_X - 12);
        for (int index = 0; index < Theme.accentCount(); index++) {
            int swatchX = ACCENT_SWATCH_X + index * ACCENT_SWATCH_STEP;
            rect(swatchX, oy(ACCENT_SWATCH_Y), ACCENT_SWATCH_SIZE, ACCENT_SWATCH_SIZE, Theme.accent(index), 4, this.progress);
        }
        settingDivider(32, dividerY(246, 39, 297, 42));
        settingText(32, 297, 42, "Theme Variable",
                "Fine-tune specific theme settings or scaling.", 340);
        textRight(355, centeredTextY(oy(309) + Theme.Sizes.SLIDER_HEIGHT / 2.0F, 12), 12, String.format(Locale.ROOT, "%.1f", this.themeValue), Theme.Colors.SECONDARY, this.progress);
        settingDivider(32, dividerY(297, 42, 351, 42));
        settingText(32, 351, 42, "Language",
                "Choose the language that suits you.", 364);

        settingText(32, 454, 42, "Low Performance Mode",
                "Optimize UI rendering to increase FPS.", 458);
        settingDivider(32, dividerY(454, 42, 508, 42));
        settingText(32, 508, 42, "Reduce Shadows",
                "Disable interface shadow effects to save resources.", 458);
        settingDivider(32, dividerY(508, 42, 562, 42));
        settingText(32, 562, 42, "Cache UI",
                "Keep layout elements in memory to reduce stutters.", 458);

        settingText(536, 246, 39, "UI Scale",
                "Change the overall size of the client menus.", 810);
        textRight(859, centeredTextY(oy(256) + Theme.Sizes.SLIDER_HEIGHT / 2.0F, 12), 12, String.format(Locale.ROOT, "%.0f%%", MathUtil.lerp(MenuOverlayState.MIN_UI_SCALE_PERCENT, MenuOverlayState.MAX_UI_SCALE_PERCENT, this.pendingUiScaleValue)), Theme.Colors.SECONDARY, this.progress);
        settingDivider(536, dividerY(246, 39, 297, 42));
        settingText(536, 297, 42, "Glass Blur",
                "Controls blur inside frosted glass cards and popups.",
                blurDropdownLeft() - 12);
        settingDivider(536, dividerY(297, 42, 351, 42));
        boolean backgroundEnabled = backgroundEnabled();
        settingText(
                536,
                351,
                42,
                "Background Dimming",
                backgroundEnabled
                        ? "Darken the animated menu background."
                        : "Select a Menu Background to enable.",
                800,
                backgroundEnabled
        );
        textRight(
                859,
                centeredTextY(oy(363) + Theme.Sizes.SLIDER_HEIGHT / 2.0F, 12),
                12,
                backgroundEnabled
                        ? String.format(Locale.ROOT, "%.0f%%", this.backgroundDimValue * 100.0F)
                        : MenuText.option("Off"),
                backgroundEnabled ? Theme.Colors.SECONDARY : Theme.Colors.TEXT_GHOST,
                this.progress
        );
        settingDivider(536, dividerY(351, 42, 405, 42));
        settingText(536, BACKGROUND_ROW_Y, 42,
                "Menu Background", "Animated shader behind the menu content.", 868);
        settingDivider(536, dividerY(405, 42, CORNER_ROW_Y, 42));
        settingText(536, CORNER_ROW_Y, 42, "Rounded Corners",
                "Select the corner smoothness for menu elements.", 868);

        settingText(536, SOUND_ROW_Y, 42, "Toggle Sound",
                "Enable or disable audio feedback for clicks.", 962);

        for (Component child : this.children) {
            child.render(minecraft, guiGraphicsExtractor);
        }
        this.languageDropdown.render(minecraft, guiGraphicsExtractor);
        this.blurModeDropdown.render(minecraft, guiGraphicsExtractor);
        this.roundedModeDropdown.render(minecraft, guiGraphicsExtractor);
        this.backgroundDropdown.render(minecraft, guiGraphicsExtractor);
        float indicatorX = ACCENT_SWATCH_X + this.accentAnimation.getValue() * ACCENT_SWATCH_STEP;
        outline(indicatorX, oy(ACCENT_SWATCH_Y), ACCENT_SWATCH_SIZE, ACCENT_SWATCH_SIZE, ColorUtil.WHITE, 4, 2.0F, this.progress);
    }

    private void setAccentIndex(int index) {
        float from = this.accentAnimation.getValue();
        Theme.setAccentIndex(index);
        this.animatedAccentTarget = Theme.accentIndex();
        this.accentAnimation.animate(from, this.animatedAccentTarget, 180L, Animation.Easing.EASE_OUT_QUAD);
    }

    private void placeControls() {
        int accent = Theme.getAccent();
        this.themeSlider.place(this, 360, oy(309), Theme.Sizes.SLIDER_WIDTH, Theme.Sizes.SLIDER_HEIGHT).style(Theme.Colors.OUTLINES_SMALL, accent, ColorUtil.WHITE).alpha(this.progress);
        this.uiScaleSlider.place(this, 864, oy(256), Theme.Sizes.SLIDER_WIDTH, Theme.Sizes.SLIDER_HEIGHT).style(Theme.Colors.OUTLINES_SMALL, accent, ColorUtil.WHITE).alpha(this.progress);
        this.backgroundDimSlider
                .place(this, 864, oy(363), Theme.Sizes.SLIDER_WIDTH, Theme.Sizes.SLIDER_HEIGHT)
                .style(Theme.Colors.OUTLINES_SMALL, accent, ColorUtil.WHITE)
                .enabled(backgroundEnabled())
                .alpha(this.progress);

        this.lowPerformanceToggle.place(this, 470, oy(466), 18, 18).style(ToggleComponent.Style.CIRCLE, Theme.Colors.CONTROL, accent).alpha(this.progress).circle();
        this.reduceShadowsToggle.place(this, 470, oy(520), 18, 18).style(ToggleComponent.Style.CIRCLE, Theme.Colors.CONTROL, accent).alpha(this.progress).circle();
        this.cacheUiToggle.place(this, 470, oy(574), 18, 18).style(ToggleComponent.Style.CIRCLE, Theme.Colors.CONTROL, accent).alpha(this.progress).circle();

        this.soundToggle.place(this, 974, oy(SOUND_ROW_Y + 12), 18, 18).style(ToggleComponent.Style.CIRCLE, Theme.Colors.CONTROL, accent).alpha(this.progress).circle();

        this.resetButton.place(this, RESET_BUTTON_X, RESET_BUTTON_Y, RESET_BUTTON_SIZE, this.mouseX, this.mouseY)
                .alpha(this.progress);
        placePillRight(this.fpsModeDropdown, FPS_MODES[this.fpsMode], TOP_DROPDOWN_RIGHT, TOP_DROPDOWN_Y);
        placeValueRight(this.languageDropdown, 488, oy(centeredControlY(351, 42, Theme.Sizes.DROPDOWN_VALUE_HEIGHT)));
        placePillRight(this.blurModeDropdown, BLUR_MODES[this.blurMode], 992, oy(centeredControlY(297, 42, 36)));
        placeValueRight(this.roundedModeDropdown, 992, oy(centeredControlY(CORNER_ROW_Y, 42, Theme.Sizes.DROPDOWN_VALUE_HEIGHT)));
        placeValueRight(this.backgroundDropdown, 992, oy(centeredControlY(BACKGROUND_ROW_Y, 42, Theme.Sizes.DROPDOWN_VALUE_HEIGHT)));

        int popupWidth = Theme.Sizes.DROPDOWN_POPUP_WIDTH;
        this.fpsPopup.place(this, TOP_DROPDOWN_RIGHT - popupWidth, 144, this.mouseX, this.mouseY, this.progress);
        this.languagePopup.place(this, 488 - popupWidth,
                oy(centeredControlY(351, 42, Theme.Sizes.DROPDOWN_VALUE_HEIGHT) + Theme.Sizes.DROPDOWN_VALUE_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y),
                this.mouseX, this.mouseY, this.progress);
        this.blurPopup.place(this, 992 - popupWidth, oy(centeredControlY(297, 42, 36) + 43), this.mouseX, this.mouseY, this.progress);
        this.cornersPopup.place(this, 992 - popupWidth,
                oy(centeredControlY(CORNER_ROW_Y, 42, Theme.Sizes.DROPDOWN_VALUE_HEIGHT) + Theme.Sizes.DROPDOWN_VALUE_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y),
                this.mouseX, this.mouseY, this.progress);
        this.backgroundPopup.place(this, 992 - popupWidth,
                oy(centeredControlY(BACKGROUND_ROW_Y, 42, Theme.Sizes.DROPDOWN_VALUE_HEIGHT) + Theme.Sizes.DROPDOWN_VALUE_HEIGHT + Theme.Sizes.DROPDOWN_POPUP_OFFSET_Y),
                this.mouseX, this.mouseY, this.progress);
    }

    private void placePillRight(DropdownComponent dropdown, String value, int right, int y) {
        int width = DropdownComponent.pillWidth(MenuText.option(value));
        dropdown.place(this, right - width, y, width, 36)
                .style(DropdownComponent.Style.PILL)
                .alpha(this.progress);
    }

    private int blurDropdownLeft() {
        return 992 - DropdownComponent.pillWidth(
                MenuText.option(BLUR_MODES[this.blurMode])
        );
    }

    private void placeValueRight(DropdownComponent dropdown, int right, int y) {
        dropdown.place(this, right - Theme.Sizes.DROPDOWN_VALUE_WIDTH, y, Theme.Sizes.DROPDOWN_VALUE_WIDTH, Theme.Sizes.DROPDOWN_VALUE_HEIGHT)
                .style(DropdownComponent.Style.VALUE)
                .alpha(this.progress);
    }

    private static int centeredControlY(int rowY, int rowHeight, int controlHeight) {
        return rowY + Math.round((rowHeight - controlHeight) / 2.0F);
    }

    private void sectionLabel(int x, int y, String label) {
        text(x, oy(y), 12, MenuText.ui(label), Theme.Colors.TEXT_GHOST, this.progress, UiFontStyle.REGULAR);
    }

    private void sectionCard(int x, int y, int width, int height) {
        Render2DUtil.rect(sx(x), sy(oy(y)), px(width), px(height))
                .color(alpha(Theme.Colors.BACKGROUND_SURFACE_S, this.progress))
                .radius(px(8))
                .border(Math.max(0.5F, px(0.5F)), alpha(Theme.Colors.OUTLINES_SMALL, this.progress))
                .draw();
    }

    private void settingDivider(int x, int y) {
        rect(x, oy(y), 456, 1, Theme.Colors.OUTLINES_SMALL, 0, this.progress);
    }

    private static int dividerY(int previousY, int previousHeight, int nextY, int nextHeight) {
        return Math.round((previousY + previousHeight + nextY) / 2.0F + (nextHeight - previousHeight) / 4.0F);
    }

    private void settingText(int x, int rowY, int rowHeight, String title,
                             String description, int textRight) {
        settingText(x, rowY, rowHeight, title, description, textRight, true);
    }

    private void settingText(int x, int rowY, int rowHeight, String title,
                             String description, int textRight, boolean enabled) {
        float titleSize = 14.0F;
        float descriptionSize = 12.0F;
        float gap = 4.0F;
        float stateAlpha = this.progress * (enabled ? 1.0F : 0.45F);

        float titleHeight = UiFonts.sfProDisplay().textHeight(px(titleSize));
        float descriptionHeight = UiFonts.sfProDisplay().textHeight(px(descriptionSize));
        float blockHeight = titleHeight + px(gap) + descriptionHeight;
        float titleY = sy(oy(rowY)) + (px(rowHeight) - blockHeight) / 2.0F;

        Render2DUtil.text(sx(x), titleY, px(titleSize), MenuText.ui(title))
                .style(UiFontStyle.MEDIUM)
                .color(alpha(enabled ? Theme.Colors.TEXT_TEXT : Theme.Colors.TEXT_GHOST, stateAlpha))
                .draw();
        float descriptionY = titleY + titleHeight + px(gap);
        MarqueeText marquee = this.descriptionLabels.computeIfAbsent(
                title + '\u0000' + description,
                ignored -> new MarqueeText(() -> MenuText.ui(description))
        );
        marquee.placeAt(
                        this,
                        sx(x),
                        descriptionY,
                        px(Math.max(0, textRight - x)),
                        descriptionHeight,
                        this.mouseX,
                        this.mouseY
                )
                .style(
                        descriptionSize,
                        UiFontStyle.REGULAR,
                        enabled ? Theme.Colors.SECONDARY : Theme.Colors.TEXT_GHOST,
                        stateAlpha
                )
                .render(Minecraft.getInstance(), null);
    }

    private void resetSettings() {
        setAccentIndex(0);
        this.themeValue = 0.38F;
        this.pendingUiScaleValue = MenuOverlayState.DEFAULT_UI_SCALE_VALUE;
        applyUiScale();
        this.backgroundDimValue = 0.35F;
        MenuAppearance.setBackgroundDim(this.backgroundDimValue);
        this.lowPerformance = true;
        this.reduceShadows = false;
        this.cacheUi = true;
        this.sound = true;
        this.blurMode = 1;
        MenuAppearance.setBlurMode(this.blurMode);
        this.roundedMode = Theme.Sizes.DEFAULT_PANEL_RADIUS_INDEX;
        applyPanelRadius();
        this.backgroundMode = 0;
        MenuAppearance.setBackground(MenuBackground.NONE);
        this.languageMode = 0;
        UiLanguage.set(UiLanguage.ENGLISH);
        this.fpsMode = 0;
    }

    private void persistSettings() {
        MenuConfigStore.save(data -> {
            data.addProperty("accentIndex", Theme.accentIndex());
            data.addProperty("themeValue", this.themeValue);
            data.remove("blurStrength");
            data.addProperty("backgroundDim", this.backgroundDimValue);
            data.addProperty("blurMode", this.blurMode);
            data.addProperty("fpsMode", this.fpsMode);
            data.remove("languageMode");
            data.addProperty("language", UiLanguage.byIndex(this.languageMode).storageKey());
            data.addProperty("roundedMode", this.roundedMode);
            data.addProperty("backgroundMode", this.backgroundMode);
            data.addProperty("lowPerformance", this.lowPerformance);
            data.addProperty("reduceShadows", this.reduceShadows);
            data.addProperty("cacheUi", this.cacheUi);
            data.addProperty("sound", this.sound);
        });
    }

    private boolean backgroundEnabled() {
        return MenuBackground.byIndex(this.backgroundMode).isAnimated();
    }

    private void applyUiScale() {
        if (this.state != null) {
            this.state.setUiScaleValue(this.pendingUiScaleValue);
        }
    }

    private void applyPanelRadius() {
        if (this.state != null) {
            this.state.setPanelRadius(Theme.Sizes.PANEL_RADII[this.roundedMode]);
        }
    }
}
