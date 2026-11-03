package org.shizavisuals.menu.ps4;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.ui.AnimatedValue;
import org.shizavisuals.menu.ps5.Ps5SkinCache;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 1:1 port of the PS4 System Settings screen (НАСТРОЙКИ СИСТЕМЫ): themed
 * wallpaper, PS4 top bar and a vertical sharp settings list with glowing
 * focus frames, covering every settings category in 1:1.
 */
public final class Ps4SettingsScreen extends Screen {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final Screen parent;
    private final List<ClickZone> clickZones = new ArrayList<>();
    private final Map<String, AnimatedValue> hoverAnims = new HashMap<>();
    private final Set<String> activeHovers = new HashSet<>();

    private int selectedIndex;
    private float scrollOffset;
    private float targetScrollOffset;

    private record Category(String title, String subtitle, net.minecraft.resources.Identifier icon, Runnable action) {
    }

    private final List<Category> categories = new ArrayList<>();

    private record ClickZone(float x, float y, float w, float h, Runnable action) {
    }

    public Ps4SettingsScreen(Screen parent) {
        super(Component.literal("ShizaVisuals"));
        this.parent = parent;
        this.initCategories();
    }

    private Minecraft client() {
        return this.minecraft != null ? this.minecraft : Minecraft.getInstance();
    }

    private void initCategories() {
        this.categories.clear();

        this.categories.add(new Category(
                "Графика и видео",
                "Разрешение, дальность прорисовки, частота кадров, эффекты",
                Ps4Textures.ICON_SINGLEPLAYER,
                () -> this.client().setScreenAndShow(new VideoSettingsScreen(this, this.client(), this.client().options))
        ));

        this.categories.add(new Category(
                "Звук и громкость",
                "Общая громкость, музыка, звуки окружения, блоки, игроки",
                Ps4Textures.ICON_MUSIC_ON,
                () -> this.client().setScreenAndShow(new SoundOptionsScreen(this, this.client().options))
        ));

        int menuVol = (int) Math.round(Ps4Sound.getMenuVolume() * 100.0F);
        this.categories.add(new Category(
                "Громкость меню ShizaVisuals: " + menuVol + "%",
                "Нажмите для изменения громкости меню (100% -> 75% -> 50% -> 25% -> 0%)",
                Ps4Textures.ICON_MUSIC_ON,
                () -> {
                    Ps4Sound.playUiSound("ps4_select");
                    float cur = Ps4Sound.getMenuVolume();
                    float next = cur >= 0.95F ? 0.75F : (cur >= 0.70F ? 0.50F : (cur >= 0.45F ? 0.25F : (cur >= 0.20F ? 0.0F : 1.0F)));
                    Ps4Sound.setMenuVolume(next);
                    if (next > 0.0F && Ps4Sound.isAmbientMuted()) {
                        Ps4Sound.toggleAmbientMuted();
                    }
                    this.initCategories();
                }
        ));

        this.categories.add(new Category(
                "Стандартное меню Minecraft",
                "Переключиться на оригинальное классическое главное меню",
                Ps4Textures.ICON_GRASS,
                () -> {
                    Ps4Sound.playUiSound("ps4_select");
                    MenuSettings.setUseVanillaMenu(true);
                    Ps4Sound.stopAmbientMusic();
                    this.client().setScreenAndShow(new net.minecraft.client.gui.screens.TitleScreen());
                }
        ));

        this.categories.add(new Category(
                "Управление и клавиатура",
                "Назначение горячих клавиш, прыжок, спринт, раскладка",
                Ps4Textures.ICON_SETTINGS,
                () -> this.client().setScreenAndShow(new ControlsScreen(this, this.client().options))
        ));

        this.categories.add(new Category(
                "Настройки мыши",
                "Чувствительность обзора, инверсия, плавность камеры",
                Ps4Textures.ICON_CHEVRON,
                () -> this.client().setScreenAndShow(new MouseSettingsScreen(this, this.client().options))
        ));

        this.categories.add(new Category(
                "Внешний вид и скин",
                "Отображение плаща, куртки, рукавов, штанов и шапки",
                Ps4Textures.ICON_PERSON,
                () -> this.client().setScreenAndShow(new SkinCustomizationScreen(this, this.client().options))
        ));

        this.categories.add(new Category(
                "Пакеты ресурсов",
                "Управление ресурс-паками, текстурами и аддонами",
                Ps4Textures.ICON_GRASS,
                () -> {
                    Minecraft client = this.client();
                    client.setScreenAndShow(new PackSelectionScreen(
                            client.getResourcePackRepository(),
                            repository -> {
                                client.options.updateResourcePacks(repository);
                                client.setScreenAndShow(this);
                            },
                            client.getResourcePackDirectory(),
                            Component.translatable("resourcePack.title")
                    ));
                }
        ));

        this.categories.add(new Category(
                "Язык / Language",
                "Выбор языка локализации интерфейса и субтитров",
                Ps4Textures.ICON_RENAME,
                () -> this.client().setScreenAndShow(new LanguageSelectScreen(this, this.client().options, this.client().getLanguageManager()))
        ));

        this.categories.add(new Category(
                "Настройки чата",
                "Видимость чата, цвета, размер шрифта, задержка и фильтры",
                Ps4Textures.ICON_BELL,
                () -> this.client().setScreenAndShow(new ChatOptionsScreen(this, this.client().options))
        ));

        this.categories.add(new Category(
                "Специальные возможности",
                "Субтитры, вспышки, затемнение фона, авто-прыжок",
                Ps4Textures.ICON_PLUS,
                () -> this.client().setScreenAndShow(new AccessibilityOptionsScreen(this, this.client().options))
        ));

        this.categories.add(new Category(
                "Благодарности и авторы",
                "Сведения о разработчиках, открытый исходный код",
                Ps4Textures.ICON_LOGO,
                () -> this.client().setScreenAndShow(new CreditsAndAttributionScreen(this))
        ));
    }

    private void closeToMenu() {
        Ps4Sound.playUiSound("ps4_scroll");
        if (this.parent != null) {
            this.client().setScreenAndShow(this.parent);
        } else {
            this.client().setScreenAndShow(new Ps4MainMenuScreen());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.targetScrollOffset -= (float) verticalAmount * 44.0F * this.listScale();
        if (this.targetScrollOffset < 0.0F) {
            this.targetScrollOffset = 0.0F;
        }
        return true;
    }

    private float listScale() {
        float screenW = this.width;
        float screenH = this.height;
        return Math.min(screenW / 960.0F, screenH / 540.0F);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.closeToMenu();
            return true;
        }

        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
            if (this.selectedIndex > 0) {
                this.selectedIndex--;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
        } else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
            if (this.selectedIndex < this.categories.size() - 1) {
                this.selectedIndex++;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.selectedIndex >= 0 && this.selectedIndex < this.categories.size()) {
                Ps4Sound.playUiSound("ps4_select");
                this.categories.get(this.selectedIndex).action().run();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            float mx = (float) event.x();
            float my = (float) event.y();

            for (int i = this.clickZones.size() - 1; i >= 0; i--) {
                ClickZone zone = this.clickZones.get(i);
                if (Ps4Helpers.inRect(mx, my, zone.x(), zone.y(), zone.w(), zone.h())) {
                    Ps4Sound.playUiSound("ps4_select");
                    zone.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float screenW = this.width;
        float screenH = this.height;
        float scale = Math.min(screenW / 960.0F, screenH / 540.0F);
        float mx = (float) mouseX;
        float my = (float) mouseY;

        this.clickZones.clear();
        RenderContext.enter2D(this.minecraft.gui, graphics, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();

            // 1. Dynamic PS4 Theme Background.
            Ps4Helpers.coverWallpaper(Ps4Wallpapers.texture(), screenW, screenH, 1.0F);

            // Dark gradient overlay.
            Ps4Helpers.rect(0.0F, 0.0F, screenW, screenH, 0.0F, Ps4Helpers.tint(0xFF000000, 0.20F));

            // 2. Top bar (back button, header title, profile, clock).
            this.drawTopBar(screenW, scale, mx, my);

            // 3. Settings category list.
            this.drawSettingsList(screenW, screenH, scale, mx, my);

            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }

    /** Top status bar: back button, list header, profile pill and the clock. */
    private void drawTopBar(float screenW, float scale, float mx, float my) {
        float topY = 24.0F * scale;

        // Back button.
        float backW = 86.0F * scale;
        float backH = 22.0F * scale;
        float backX = 36.0F * scale;
        boolean backHover = Ps4Helpers.inRect(mx, my, backX, topY, backW, backH);
        float backHov = this.hoverPC("top_back", backHover);

        Ps4Helpers.rect(backX, topY, backW, backH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.15F + 0.2F * backHov));
        if (backHov > 0.05F) {
            Ps4Helpers.outline(backX, topY, backW, backH, 0.0F, 1.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, backHov));
        }
        Ps4Helpers.icon(Ps4Textures.ICON_BACK, backX + 10.0F * scale, topY + backH * 0.5F, 9.0F * scale, 9.0F * scale, Ps4Helpers.WHITE);
        Ps4Helpers.text(Ps4Fonts.UI, "Назад в меню", backX + 22.0F * scale, topY + backH * 0.5F - 3.2F * scale, 6.2F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(backX, topY, backW, backH, this::closeToMenu));

        // Header title.
        float titleX = backX + backW + 16.0F * scale;
        Ps4Helpers.text(Ps4Fonts.UI, "НАСТРОЙКИ СИСТЕМЫ", titleX, topY + backH * 0.5F - 4.2F * scale, 8.5F * scale, Ps4Helpers.WHITE);

        // Clock (shared cached formatter).
        String timeStr = Ps4Helpers.clockString();
        float timeX = screenW - 44.0F * scale;
        float timeW = Ps4Helpers.measure(Ps4Fonts.UI, timeStr, 7.8F * scale);
        float clockLeft = timeX - timeW;
        Ps4Helpers.text(Ps4Fonts.UI, timeStr, clockLeft, topY + 7.5F * scale, 7.8F * scale, Ps4Helpers.WHITE);

        // Profile pill.
        String profileName = this.client().getUser() != null && !this.client().getUser().getName().isBlank()
                ? this.client().getUser().getName() : "Player";
        float profNameW = Ps4Helpers.measure(Ps4Fonts.UI, profileName, 7.0F * scale);
        float profW = 34.0F * scale + profNameW + 16.0F * scale;
        float profH = 24.0F * scale;
        float profX = clockLeft - profW - 24.0F * scale;

        boolean profHover = Ps4Helpers.inRect(mx, my, profX, topY - 1.0F * scale, profW, profH);
        float profHov = this.hoverPC("top_profile", profHover);

        Ps4Helpers.rect(profX, topY - 1.0F * scale, profW, profH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.22F + 0.35F * profHov));
        if (profHov > 0.05F) {
            Ps4Helpers.outline(profX, topY - 1.0F * scale, profW, profH, 0.0F, 1.2F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, profHov));
        }

        float avSize = 16.0F * scale;
        float avX = profX + 4.0F * scale;
        float avY = topY - 1.0F * scale + (profH - avSize) * 0.5F;
        Ps4Helpers.rect(avX - 1.0F * scale, avY - 1.0F * scale, avSize + 2.0F * scale, avSize + 2.0F * scale, 0.0F, 0xFF181818);
        Identifier skin = Ps5SkinCache.texture(this.client(), profileName);
        Render2DUtil.texture(avX, avY, avSize, avSize, skin).color(Ps4Helpers.WHITE)
                .uv(0.125F, 0.125F, 0.25F, 0.25F).draw();

        Ps4Helpers.text(Ps4Fonts.UI, profileName, profX + 24.0F * scale, topY - 1.0F * scale + profH * 0.5F - 3.5F * scale, 7.0F * scale, Ps4Helpers.WHITE);

        this.clickZones.add(new ClickZone(profX, topY - 1.0F * scale, profW, profH, this::closeToMenu));
    }

    /** Vertical PS4 settings list with a smooth scroll and edge fades. */
    private void drawSettingsList(float screenW, float screenH, float scale, float mx, float my) {
        float startY = 72.0F * scale;
        float rowH = 36.0F * scale;
        float rowGap = 8.0F * scale;
        float listW = Math.min(screenW - 72.0F * scale, 680.0F * scale);
        float listX = (screenW - listW) * 0.5F;

        float totalContentH = this.categories.size() * (rowH + rowGap);
        float maxScroll = Math.max(0.0F, totalContentH - (screenH - startY - 24.0F * scale));

        this.targetScrollOffset = Math.max(0.0F, Math.min(maxScroll, this.targetScrollOffset));
        this.scrollOffset += (this.targetScrollOffset - this.scrollOffset) * 0.22F;

        for (int i = 0; i < this.categories.size(); i++) {
            Category cat = this.categories.get(i);
            float rowY = startY + i * (rowH + rowGap) - this.scrollOffset;

            if (rowY + rowH < startY - 10.0F || rowY > screenH - 10.0F) {
                continue;
            }

            boolean isSelected = i == this.selectedIndex;
            boolean hovered = Ps4Helpers.inRect(mx, my, listX, rowY, listW, rowH);
            float hov = this.hoverPC("setting_" + i, hovered);

            if (hovered) {
                // Hovering a row gently moves the keyboard focus onto it.
                this.selectedIndex = i;
            }

            // Smooth fade near the top and bottom boundaries (smoothstep).
            float topFadeStart = startY + 16.0F * scale;
            float topFadeEnd = startY - rowH * 0.4F;
            float topFactor = 1.0F;
            if (rowY < topFadeStart) {
                float t = Math.max(0.0F, Math.min(1.0F, (rowY - topFadeEnd) / (topFadeStart - topFadeEnd)));
                topFactor = t * t * (3.0F - 2.0F * t);
            }

            float bottomFadeStart = screenH - 42.0F * scale - rowH;
            float bottomFadeEnd = screenH - 5.0F;
            float bottomFactor = 1.0F;
            if (rowY > bottomFadeStart) {
                float t = Math.max(0.0F, Math.min(1.0F, (bottomFadeEnd - rowY) / (bottomFadeEnd - bottomFadeStart)));
                bottomFactor = t * t * (3.0F - 2.0F * t);
            }

            float rowAlpha = Math.min(topFactor, bottomFactor);

            // Sharp rectangular item background.
            int bgCol = isSelected ? 0xD01E1E2A : (hovered ? 0x90181822 : 0x65101018);
            Ps4Helpers.rect(listX, rowY, listW, rowH, 0.0F, Ps4Helpers.tint(bgCol, rowAlpha));

            // PS4 glowing focus frame on selection.
            if (isSelected) {
                Ps4Helpers.glow(listX, rowY, listW, rowH, 0.0F, 10.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, 0.35F * rowAlpha));
                Ps4Helpers.outline(listX, rowY, listW, rowH, 0.0F, 2.2F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, rowAlpha));
            } else if (hov > 0.05F) {
                Ps4Helpers.outline(listX, rowY, listW, rowH, 0.0F, 1.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, hov * 0.6F * rowAlpha));
            }

            // Left icon.
            float iconSize = 16.0F * scale;
            float iconX = listX + 16.0F * scale;
            float iconY = rowY + (rowH - iconSize) * 0.5F;
            Ps4Helpers.icon(cat.icon(), iconX + iconSize * 0.5F, iconY + iconSize * 0.5F, iconSize, iconSize,
                    Ps4Helpers.tint(isSelected ? Ps4Helpers.WHITE : 0xCCFFFFFF, rowAlpha));

            // Title & subtitle (clipped).
            float textX = iconX + iconSize + 16.0F * scale;
            String title = Ps4Helpers.fit(Ps4Fonts.UI, cat.title(), 7.8F * scale, listW - (textX - listX) - 34.0F * scale);
            Ps4Helpers.text(Ps4Fonts.UI, title, textX, rowY + 9.0F * scale, 7.8F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, rowAlpha));

            String subtitle = Ps4Helpers.fit(Ps4Fonts.UI, cat.subtitle(), 6.0F * scale, listW - (textX - listX) - 34.0F * scale);
            Ps4Helpers.text(Ps4Fonts.UI, subtitle, textX, rowY + 22.0F * scale, 6.0F * scale,
                    Ps4Helpers.tint(0xAAFFFFFF, rowAlpha));

            // Right arrow indicator.
            Ps4Helpers.textCentered(Ps4Fonts.UI, "›", listX + listW - 24.0F * scale, rowY + rowH * 0.5F - 4.5F * scale,
                    10.0F * scale, Ps4Helpers.tint(isSelected ? Ps4Helpers.WHITE : 0x88FFFFFF, rowAlpha));

            // Click action.
            final int idx = i;
            this.clickZones.add(new ClickZone(listX, rowY, listW, rowH, () -> {
                this.selectedIndex = idx;
                cat.action().run();
            }));
        }
    }

    private float hoverPC(String key, boolean hovered) {
        if (hovered) {
            if (this.activeHovers.add(key)) {
                Ps4Sound.playUiSound("ps4_scroll");
            }
        } else {
            this.activeHovers.remove(key);
        }
        AnimatedValue anim = this.hoverAnims.computeIfAbsent(key, k -> new AnimatedValue(140L, Animation.Easing.EASE_OUT_QUAD));
        anim.toward(hovered ? 1.0F : 0.0F);
        return anim.value();
    }
}
