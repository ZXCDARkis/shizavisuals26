package org.shizavisuals.menu.ps5;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.ui.AnimatedValue;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1 port of the PS5 (deluxe) main menu: blurred side panel, logo chip,
 * social buttons, slide-in column with the online/singleplayer/actions rows.
 * Accounts & cosmetics side panels are wired to the animators and get their
 * content in the following port batches.
 */
public final class Ps5MainMenuScreen extends Screen {
    private static final String TELL = "https://t.me/delnmt";
    private static final String DISCORD = "https://discord.gg/wWsxQkF2tP";

    private boolean accountsOpen;
    private boolean cosmeticsOpen;
    private final AnimatedValue accountsAnim = new AnimatedValue(260L, Animation.Easing.EASE_OUT_QUAD);
    private final AnimatedValue cosmeticsAnim = new AnimatedValue(260L, Animation.Easing.EASE_OUT_QUAD);
    private final Map<String, AnimatedValue> hovers = new HashMap<>();
    private final List<ClickTarget> clickTargets = new ArrayList<>();
    private boolean warm;
    private long startedAt = System.currentTimeMillis();

    private record ClickTarget(float x, float y, float width, float height, Runnable action) {
    }

    public Ps5MainMenuScreen() {
        this(false);
    }

    public Ps5MainMenuScreen(boolean warm) {
        super(Component.literal("Deluxe"));
        this.warm = warm;
    }

    private String welcomeName() {
        Minecraft minecraft = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        String name = minecraft.getUser() != null ? minecraft.getUser().getName() : "";
        return name.isBlank() ? "Player" : name;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    protected void init() {
        super.init();
        // Fresh entry (not the first frame of a warm transition) restarts the intro.
        if (System.currentTimeMillis() - this.startedAt > 600L) {
            this.startedAt = System.currentTimeMillis();
            this.warm = false;
            this.accountsOpen = false;
            this.cosmeticsOpen = false;
            this.accountsAnim.value();
            this.cosmeticsAnim.value();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            float mx = (float) event.x();
            float my = (float) event.y();
            if (this.accountsOpen && this.accountsAnim.value() > 0.6F) {
                return true;
            }

            if (this.cosmeticsOpen && this.cosmeticsAnim.value() > 0.6F) {
                return true;
            }

            for (int index = this.clickTargets.size() - 1; index >= 0; index--) {
                ClickTarget target = this.clickTargets.get(index);
                if (Ps5Theme.inRect(mx, my, target.x(), target.y(), target.width(), target.height())) {
                    target.action().run();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubled);
    }

    private void drawMenuColumn(float anchorX, float scale, float verticalOffset, float mouseX, float mouseY, float columnAnim) {
        Minecraft minecraft = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        float columnX = anchorX + 51.5F * scale;
        boolean interactive = columnAnim < 0.5F;
        float headSlide = this.slideInProgress(0);
        Ps5Theme.pushAlpha(headSlide);
        float headX = columnX - (1.0F - headSlide) * 16.0F * scale;
        Ps5Theme.text(Ps5Fonts.SEMI, "Deluxe client  /  \u0413\u043b\u0430\u0432\u043d\u0430\u044f",
                headX, verticalOffset + 127.5F * scale, 6.5F * scale, Ps5Theme.paint(Ps5Theme.TEXT_MUTED));
        Ps5Theme.text(Ps5Fonts.SEMI, "\u0420\u0430\u0434\u044b \u0432\u0438\u0434\u0435\u0442\u044c \u0442\u0435\u0431\u044f,",
                headX, verticalOffset + 142.0F * scale, 20.0F * scale, Ps5Theme.paint(Ps5Theme.WHITE));
        Ps5Theme.text(Ps5Fonts.SEMI, this.welcomeName() + ".",
                headX, verticalOffset + 166.2F * scale, 20.0F * scale, Ps5Theme.paint(Ps5Theme.WHITE));
        Ps5Theme.text(Ps5Fonts.SEMI, "\u0412\u0441\u0451 \u0433\u043e\u0442\u043e\u0432\u043e - \u0432\u044b\u0431\u0438\u0440\u0430\u0439 \u0440\u0435\u0436\u0438\u043c \u0438 \u043d\u0430\u0447\u0438\u043d\u0430\u0439 \u0438\u0433\u0440\u0430\u0442\u044c.",
                headX, verticalOffset + 201.0F * scale, 7.5F * scale, Ps5Theme.paint(Ps5Theme.TEXT_MUTED));
        Ps5Theme.popAlpha();
        this.drawOnlineButton(
                columnX,
                verticalOffset + 227.0F * scale,
                scale,
                mouseX,
                mouseY,
                interactive,
                () -> minecraft.setScreenAndShow(new JoinMultiplayerScreen(this))
        );
        this.drawRow(
                columnX,
                verticalOffset + 270.0F * scale,
                scale,
                2,
                Ps5Textures.ICON_SINGLEPLAYER,
                6.0F,
                "\u041e\u0434\u0438\u043d\u043e\u0447\u043d\u0430\u044f \u0438\u0433\u0440\u0430",
                null,
                0,
                mouseX,
                mouseY,
                interactive,
                () -> minecraft.setScreenAndShow(new SelectWorldScreen(this))
        );
        this.drawRow(
                columnX,
                verticalOffset + 295.5F * scale,
                scale,
                3,
                Ps5Textures.ICON_ACCOUNTS,
                6.0F,
                "\u0412\u0430\u0448\u0438 \u0430\u043a\u043a\u0430\u0443\u043d\u0442\u044b",
                Ps5Textures.ICON_BELL,
                Ps5Theme.RED,
                mouseX,
                mouseY,
                interactive,
                this::toggleAccounts
        );
        this.drawRow(
                columnX,
                verticalOffset + 321.0F * scale,
                scale,
                4,
                Ps5Textures.ICON_COSMENU,
                6.5F,
                "\u041a\u043e\u0441\u043c\u0435\u0442\u0438\u043a\u0430",
                Ps5Textures.ICON_CHEVRON,
                Ps5Theme.TEXT_MUTED,
                mouseX,
                mouseY,
                interactive,
                this::toggleCosmetics
        );
        this.drawRow(
                columnX,
                verticalOffset + 346.5F * scale,
                scale,
                5,
                Ps5Textures.ICON_SETTINGS,
                7.0F,
                "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0438\u0433\u0440\u044b",
                null,
                0,
                mouseX,
                mouseY,
                interactive,
                () -> minecraft.setScreenAndShow(new OptionsScreen(this, minecraft.options, false))
        );
        this.drawRow(
                columnX,
                verticalOffset + 379.5F * scale,
                scale,
                6,
                Ps5Textures.ICON_EXIT,
                7.0F,
                "\u0412\u044b\u0439\u0442\u0438 \u0438\u0437 \u0438\u0433\u0440\u044b",
                Ps5Textures.ICON_CHEVRON,
                Ps5Theme.TEXT_MUTED,
                mouseX,
                mouseY,
                interactive,
                minecraft::stop
        );
    }

    private void drawRow(
            float anchorX,
            float y,
            float scale,
            int order,
            Identifier iconTexture,
            float iconSize,
            String label,
            Identifier trailingIcon,
            int trailingColor,
            float mouseX,
            float mouseY,
            boolean interactive,
            Runnable action
    ) {
        float slide = this.slideInProgress(order);
        Ps5Theme.pushAlpha(slide);
        float x = anchorX - (1.0F - slide) * 16.0F * scale;
        float hover = this.hoverProgress(
                "row" + label,
                interactive && Ps5Theme.inRect(mouseX, mouseY, x, y, 226.5F * scale, 19.5F * scale)
        );
        x += hover * 2.0F * scale;
        Render2DUtil.rect(x, y, 226.5F * scale, 19.5F * scale)
                .color(Ps5Theme.paint(Ps5Theme.withAlpha(Ps5Theme.WHITE, 0.031F + 0.05F * hover)))
                .radius(6.0F * scale)
                .draw();
        Render2DUtil.rect(x, y, 226.5F * scale, 19.5F * scale)
                .color(ColorUtil.TRANSPARENT)
                .radius(6.0F * scale)
                .border(0.5F, Ps5Theme.paint(Ps5Theme.withAlpha(Ps5Theme.WHITE, 0.078F + 0.09F * hover)))
                .draw();
        Ps5Theme.icon(iconTexture,
                x + 11.0F * scale, y + 9.75F * scale,
                iconSize * scale, iconSize * scale,
                Ps5Theme.paint(Ps5Theme.lerpColor(Ps5Theme.TEXT_SECONDARY, Ps5Theme.WHITE, hover))
        );
        Ps5Theme.text(Ps5Fonts.SEMI, label,
                x + 18.0F * scale, y + 6.25F * scale, 7.0F * scale,
                Ps5Theme.paint(Ps5Theme.lerpColor(Ps5Theme.TEXT_SECONDARY, Ps5Theme.WHITE, hover))
        );
        if (trailingIcon != null) {
            Ps5Theme.icon(trailingIcon,
                    x + 214.5F * scale + hover * 1.5F * scale,
                    y + 9.75F * scale,
                    7.0F * scale, 7.0F * scale,
                    Ps5Theme.paint(trailingColor)
            );
        }

        Ps5Theme.popAlpha();
        if (interactive && slide > 0.7F) {
            this.clickTargets.add(new ClickTarget(x, y, 226.5F * scale, 19.5F * scale, action));
        }
    }

    private void drawOnlineButton(
            float anchorX, float y, float scale, float mouseX, float mouseY, boolean interactive, Runnable action
    ) {
        float slide = this.slideInProgress(1);
        Ps5Theme.pushAlpha(slide);
        float x = anchorX - (1.0F - slide) * 16.0F * scale;
        float hover = this.hoverProgress(
                "row\u041e\u043d\u043b\u0430\u0439\u043d",
                interactive && Ps5Theme.inRect(mouseX, mouseY, x, y, 226.5F * scale, 37.0F * scale)
        );
        Render2DUtil.rect(x, y, 226.5F * scale, 37.0F * scale)
                .color(Ps5Theme.paint(Ps5Theme.lerpColor(Ps5Theme.BLUE, Ps5Theme.BLUE_LIGHT, hover * 0.55F)))
                .radius(6.0F * scale)
                .draw();
        Render2DUtil.rect(x, y, 226.5F * scale, 37.0F * scale)
                .color(ColorUtil.TRANSPARENT)
                .radius(6.0F * scale)
                .border(0.5F, Ps5Theme.paint(Ps5Theme.ACCENT_MID))
                .draw();
        Render2DUtil.texture(
                        x + 173.0F * scale + hover * 2.0F * scale,
                        y + 5.0F * scale,
                        44.0F * scale,
                        32.0F * scale,
                        Ps5Textures.ICON_PATTERN
                )
                .color(Ps5Theme.paint(Ps5Theme.withAlpha(Ps5Theme.WHITE, 0.25F * 0.81F)))
                .uv(1.0F, 0.0F, 0.0F, 1.0F)
                .draw();
        Ps5Theme.text(Ps5Fonts.SEMI, "\u041e\u043d\u043b\u0430\u0439\u043d \u0438\u0433\u0440\u0430",
                x + 8.5F * scale, y + 6.5F * scale, 7.0F * scale, Ps5Theme.paint(Ps5Theme.WHITE));
        float lineWidth = y + 16.5F * scale;
        for (String line : Ps5Theme.wrap(Ps5Fonts.SEMI,
                "\u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0430\u0439\u0442\u0435\u0441\u044c \u043a \u0441\u0435\u0440\u0432\u0435\u0440\u0430\u043c \u0438 \u0438\u0433\u0440\u0430\u0439\u0442\u0435 \u0432\u043c\u0435\u0441\u0442\u0435 \u0441 \u0434\u0440\u0443\u0433\u0438\u043c\u0438.",
                5.5F * scale, 118.0F * scale)) {
            Ps5Theme.text(Ps5Fonts.SEMI, line, x + 8.5F * scale, lineWidth, 5.5F * scale, Ps5Theme.paint(Ps5Theme.withAlpha(Ps5Theme.WHITE, 0.5F)));
            lineWidth += 6.65F * scale;
        }

        Ps5Theme.popAlpha();
        if (interactive && slide > 0.7F) {
            this.clickTargets.add(new ClickTarget(x, y, 226.5F * scale, 37.0F * scale, action));
        }
    }

    private void drawChrome(float anchorX, float viewHeight, float scale, float mouseX, float mouseY) {
        float logoAlpha = Ps5Theme.easing(Ps5Theme.progress(this.startedAt, this.warm ? 260L : 60L, 420L));
        Ps5Theme.pushAlpha(logoAlpha);
        Ps5Theme.icon(
                Ps5Textures.ICON_LOGO,
                anchorX + 51.5F * scale,
                40.0F * scale - (1.0F - logoAlpha) * 8.0F * scale,
                13.5F * scale,
                10.5F * scale,
                Ps5Theme.paint(Ps5Theme.BLUE)
        );
        Ps5Theme.popAlpha();
        float socialAlpha = Ps5Theme.easing(Ps5Theme.progress(this.startedAt, this.warm ? 700L : 300L, 420L));
        Ps5Theme.pushAlpha(socialAlpha);
        float telegramHover = this.hoverProgress(
                "socialTelegram",
                Ps5Theme.inRect(mouseX, mouseY, anchorX + 47.5F * scale, viewHeight - 59.0F * scale, 16.5F * scale, 16.5F * scale)
        );
        Ps5Theme.icon(
                Ps5Textures.ICON_TELEGRAM,
                anchorX + 55.75F * scale,
                viewHeight - 50.75F * scale - telegramHover * 1.5F * scale,
                8.5F * scale,
                8.5F * scale,
                Ps5Theme.paint(Ps5Theme.lerpColor(Ps5Theme.TEXT_SECONDARY, Ps5Theme.WHITE, telegramHover))
        );
        float discordHover = this.hoverProgress(
                "socialDiscord",
                Ps5Theme.inRect(mouseX, mouseY, anchorX + 65.0F * scale, viewHeight - 59.0F * scale, 16.5F * scale, 16.5F * scale)
        );
        Ps5Theme.icon(
                Ps5Textures.ICON_DISCORD,
                anchorX + 73.25F * scale,
                viewHeight - 50.75F * scale - discordHover * 1.5F * scale,
                8.5F * scale,
                8.5F * scale,
                Ps5Theme.paint(Ps5Theme.lerpColor(Ps5Theme.TEXT_SECONDARY, Ps5Theme.WHITE, discordHover))
        );
        Ps5Theme.popAlpha();
        if (socialAlpha > 0.7F) {
            this.clickTargets.add(new ClickTarget(
                    anchorX + 47.5F * scale,
                    viewHeight - 59.0F * scale,
                    16.5F * scale,
                    16.5F * scale,
                    () -> Util.getPlatform().openUri(TELL)
            ));
            this.clickTargets.add(new ClickTarget(
                    anchorX + 65.0F * scale,
                    viewHeight - 59.0F * scale,
                    16.5F * scale,
                    16.5F * scale,
                    () -> Util.getPlatform().openUri(DISCORD)
            ));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float viewWidth = this.width;
        float viewHeight = this.height;
        float scale = Math.min(viewWidth / 960.0F, viewHeight / 540.0F);
        float verticalOffset = (viewHeight - 540.0F * scale) * 0.5F;
        mxStore = mouseX;
        myStore = mouseY;
        this.clickTargets.clear();
        this.accountsAnim.toward(this.accountsOpen ? 1.0F : 0.0F);
        this.cosmeticsAnim.toward(this.cosmeticsOpen ? 1.0F : 0.0F);
        float accountsAmount = this.accountsAnim.value();
        float cosmeticsAmount = this.cosmeticsAnim.value();
        float panelAmount = Math.max(accountsAmount, cosmeticsAmount);
        RenderContext.enter2D(this.minecraft.gui, graphics, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();
            Render2DUtil.setBackdropBlurScale(1.0F);
            Ps5Theme.cover(Ps5Textures.BG_MENU, viewWidth, viewHeight, 1.0F);
            if (this.warm) {
                float welcomeFade = Ps5Theme.easing(Ps5Theme.progress(this.startedAt, 100L, 600L));
                if (welcomeFade < 0.999F) {
                    Ps5Theme.cover(Ps5Textures.BG_WELCOME, viewWidth, viewHeight, 1.0F - welcomeFade);
                }
            }

            float slide = Ps5Theme.easing(Ps5Theme.progress(this.startedAt, this.warm ? 150L : 0L, this.warm ? 500L : 350L));
            float slideX = -330.0F * scale * (1.0F - slide);
            float panelWidth = 329.5F * scale + Math.min(0.0F, slideX);
            if (slideX > 1.0F) {
                Render2DUtil.pushScissor(Math.max(0.0F, slideX), 0.0F, panelWidth, viewHeight);
                Ps5Theme.cover(Ps5Textures.BG_MENU_BLUR, viewWidth, viewHeight, 1.0F);
                Render2DUtil.popScissor();
            }

            Render2DUtil.rect(slideX, 0.0F, 329.5F * scale, viewHeight)
                    .color(Ps5Theme.paint(Ps5Theme.withAlpha(Ps5Theme.WHITE, 0.012F)))
                    .radius(0.0F)
                    .draw();
            Render2DUtil.rect(slideX + 329.5F * scale - 0.5F * scale, 0.0F, 0.5F * scale, viewHeight)
                    .color(Ps5Theme.paint(Ps5Theme.withAlpha(Ps5Theme.WHITE, 0.03F)))
                    .radius(0.0F)
                    .draw();
            Ps5Theme.pushAlpha(slide);
            this.drawChrome(slideX, viewHeight, scale, (float) mxStore, (float) myStore);
            if (panelAmount < 0.999F) {
                Ps5Theme.pushAlpha(1.0F - panelAmount);
                this.drawMenuColumn(slideX - 36.0F * scale * panelAmount, scale, verticalOffset,
                        (float) mxStore, (float) myStore, panelAmount);
                Ps5Theme.popAlpha();
            }

            // Side panels (accounts / cosmetics) render in later port batches;
            // the slide-away animation already matches on their animator.
            Ps5Theme.popAlpha();
            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }

    private int mxStore;
    private int myStore;

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.accountsOpen) {
            if (key == 256) {
                this.toggleAccounts();
                return true;
            }
        }

        if (this.cosmeticsOpen) {
            if (key == 256) {
                this.toggleCosmetics();
                return true;
            }
        }

        // Swallow everything: the menu has its own chrome, no vanilla hotkeys.
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.accountsOpen || this.cosmeticsOpen) {
            return true;
        }
        return super.charTyped(event);
    }

    private float hoverProgress(String key, boolean hovered) {
        AnimatedValue animatedValue = this.hovers.computeIfAbsent(key, ignored -> new AnimatedValue(150L, Animation.Easing.EASE_OUT_QUAD));
        animatedValue.toward(hovered ? 1.0F : 0.0F);
        return animatedValue.value();
    }

    private float slideInProgress(int order) {
        return Ps5Theme.easing(Ps5Theme.progress(this.startedAt, (this.warm ? 300L : 80L) + Math.abs(order) * 70L, 450L));
    }

    private void toggleAccounts() {
        this.accountsOpen = !this.accountsOpen;
    }

    private void toggleCosmetics() {
        this.cosmeticsOpen = !this.cosmeticsOpen;
    }
}
