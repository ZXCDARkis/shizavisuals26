package org.shizavisuals.menu.ps4;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.menu.ui.AnimatedValue;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 1:1 port of the PS4 (deluxe PS4) welcome screen: themed wallpaper, top-left
 * logo, centered "Welcome Back" text and the enter button with keycap badge.
 */
public final class Ps4WelcomeScreen extends Screen {
    private final long startTime = System.currentTimeMillis();
    private long exitStartTime;
    private final AnimatedValue buttonHover = new AnimatedValue(180L, Animation.Easing.EASE_OUT_QUAD);
    private boolean wasHovered;
    private float btnX;
    private float btnY;
    private float btnW;
    private float btnH;
    private float muteX;
    private float muteY;
    private float muteW;
    private float muteH;
    private boolean muteWasHovered;
    private final AnimatedValue muteHover = new AnimatedValue(150L, Animation.Easing.EASE_OUT_QUAD);
    /** Global draw alpha multiplied into every painted color. */
    private float drawAlpha = 1.0F;

    public Ps4WelcomeScreen() {
        super(Component.literal("ShizaVisuals"));
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
    protected void init() {
        super.init();
        Ps4Sound.startAmbientMusic();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            float mx = (float) event.x();
            float my = (float) event.y();
            if (Ps4Helpers.inRect(mx, my, this.muteX, this.muteY, this.muteW, this.muteH)) {
                Ps4Sound.playUiSound("ps4_select");
                Ps4Sound.toggleAmbientMuted();
                return true;
            }
            if (this.exitStartTime == 0L) {
                this.exitStartTime = System.currentTimeMillis();
                Ps4Sound.playUiSound("ps4_select");
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.exitStartTime == 0L) {
            this.exitStartTime = System.currentTimeMillis();
            Ps4Sound.playUiSound("ps4_select");
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.exitStartTime != 0L && System.currentTimeMillis() - this.exitStartTime >= 340L) {
            Minecraft minecraft = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
            minecraft.setScreenAndShow(new Ps4MainMenuScreen(true));
        }
    }

    /** Multiplies an opaque color further down by the intro alpha. */
    private int paint(int color) {
        return ColorUtil.multiplyAlpha(color, this.drawAlpha);
    }

    private int paint(int color, float extra) {
        return ColorUtil.multiplyAlpha(color, this.drawAlpha * Math.max(0.0F, Math.min(1.0F, extra)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float screenW = this.width;
        float screenH = this.height;
        float intro = Ps4Helpers.easeOut(Ps4Helpers.progress(this.startTime, 0L, 450L));
        float outro = this.exitStartTime == 0L ? 0.0F : Ps4Helpers.easeOut(Ps4Helpers.progress(this.exitStartTime, 0L, 320L));
        float alpha = intro * (1.0F - outro);
        float scale = Math.min(screenW / 960.0F, screenH / 540.0F);
        float centerX = screenW * 0.5F;
        float centerY = screenH * 0.5F;
        float mx = (float) mouseX;
        float my = (float) mouseY;
        this.drawAlpha = alpha;

        RenderContext.enter2D(this.minecraft.gui, graphics, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();

            // 1. Wallpaper dynamically matching the active theme.
            Ps4Helpers.coverWallpaper(Ps4Wallpapers.texture(), screenW, screenH, alpha);

            // 2. Top-left Deluxe logo (0.889 ratio, no horizontal stretch).
            float logoH = 24.0F * scale;
            float logoW = logoH * (1207.0F / 1357.0F);
            float logoX = 42.0F * scale;
            float logoY = 24.0F * scale;
            Render2DUtil.texture(logoX, logoY, logoW, logoH, Ps4Textures.ICON_DELUXE)
                    .color(paint(Ps4Helpers.WHITE)).draw();
            Ps4Helpers.text(Ps4Fonts.UI, "SHIZAVISUALS", logoX + logoW + 8.0F * scale,
                    logoY + logoH * 0.5F - 4.2F * scale, 8.5F * scale, paint(Ps4Helpers.WHITE));

            // 3. Center title.
            float titleY = centerY - 32.0F * scale;
            Ps4Helpers.textCentered(Ps4Fonts.UI, "Welcome Back to SHIZAVISUALS",
                    centerX, titleY, 16.0F * scale, paint(Ps4Helpers.WHITE));

            // 4. Center prompt.
            float promptY = centerY - 10.0F * scale;
            Ps4Helpers.textCentered(Ps4Fonts.UI, "Press the Enter button to use the keyboard.",
                    centerX, promptY, 7.8F * scale, paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0.78F)));

            // 5. Button "Перейти в главное меню" with the [ Enter ] keycap.
            float labelWidth = Ps4Helpers.measure(Ps4Fonts.UI, "Перейти в главное меню", 6.8F * scale);
            float keyW = 26.0F * scale;
            float keyH = 13.0F * scale;
            float btnWidth = keyW + labelWidth + 24.0F * scale;
            float btnHeight = 23.0F * scale;
            float buttonX = centerX - btnWidth * 0.5F;
            float buttonY = centerY + 18.0F * scale;
            this.btnX = buttonX;
            this.btnY = buttonY;
            this.btnW = btnWidth;
            this.btnH = btnHeight;

            boolean isHovered = Ps4Helpers.inRect(mx, my, buttonX, buttonY, btnWidth, btnHeight);
            if (isHovered && !this.wasHovered && this.exitStartTime == 0L) {
                Ps4Sound.playUiSound("ps4_scroll");
            }
            this.wasHovered = isHovered;
            this.buttonHover.toward(isHovered && this.exitStartTime == 0L ? 1.0F : 0.0F);
            float hover = this.buttonHover.value();

            // Button glow on hover with theme accent.
            if (hover > 0.01F && alpha > 0.01F) {
                Ps4Helpers.glow(buttonX, buttonY, btnWidth, btnHeight, 7.0F * scale, 12.0F * scale,
                        paint(Ps4Helpers.tint(Ps4Helpers.ACCENT_LIGHT, hover * 0.45F)));
            }

            // Background pill shaded with the theme.
            int darkTheme = Ps4Helpers.lerp(Ps4Helpers.ACCENT, 0xFF000000, 0.70F);
            int midTheme = Ps4Helpers.lerp(Ps4Helpers.ACCENT, 0xFF000000, 0.45F);
            int bgCol = Ps4Helpers.lerp(paint(darkTheme, 0.50F), paint(midTheme, 0.75F), hover);
            Ps4Helpers.rect(buttonX, buttonY, btnWidth, btnHeight, 7.0F * scale, bgCol);

            // Border outline matching the theme.
            int borderCol = Ps4Helpers.lerp(paint(Ps4Helpers.ACCENT, 0.45F), paint(Ps4Helpers.ACCENT_LIGHT, 0.95F), hover);
            Ps4Helpers.outline(buttonX, buttonY, btnWidth, btnHeight, 7.0F * scale, 0.65F * scale, borderCol);

            // Sleek keyboard Enter keycap badge.
            float keyX = buttonX + 8.0F * scale;
            float keyY = buttonY + (btnHeight - keyH) * 0.5F;
            int badgeDark = Ps4Helpers.lerp(Ps4Helpers.ACCENT, 0xFF000000, 0.20F);
            int keyBg = Ps4Helpers.lerp(paint(badgeDark, 0.75F), paint(Ps4Helpers.ACCENT, 0.95F), hover);
            Ps4Helpers.rect(keyX, keyY, keyW, keyH, 3.5F * scale, keyBg);
            Ps4Helpers.outline(keyX, keyY, keyW, keyH, 3.5F * scale, 0.5F * scale, borderCol);
            Ps4Helpers.textCentered(Ps4Fonts.UI, "Enter", keyX + keyW * 0.5F, keyY + keyH * 0.5F - 2.6F * scale,
                    4.5F * scale, paint(Ps4Helpers.WHITE));

            // Label.
            float textX = keyX + keyW + 7.0F * scale;
            Ps4Helpers.text(Ps4Fonts.UI, "Перейти в главное меню", textX,
                    buttonY + btnHeight * 0.5F - 3.4F * scale, 6.8F * scale, paint(Ps4Helpers.WHITE));

            // 6. Top-right mute toggle button.
            float muteW = 24.0F * scale;
            float muteH = muteW;
            float muteX = screenW - muteW - 32.0F * scale;
            float muteY = 24.0F * scale;
            this.muteX = muteX;
            this.muteY = muteY;
            this.muteW = muteW;
            this.muteH = muteH;

            boolean isMuteHovered = Ps4Helpers.inRect(mx, my, muteX, muteY, muteW, muteH);
            if (isMuteHovered && !this.muteWasHovered && this.exitStartTime == 0L) {
                Ps4Sound.playUiSound("ps4_scroll");
            }
            this.muteWasHovered = isMuteHovered;
            this.muteHover.toward(isMuteHovered ? 1.0F : 0.0F);
            float mHover = this.muteHover.value();

            if (mHover > 0.01F && alpha > 0.01F) {
                Ps4Helpers.glow(muteX, muteY, muteW, muteH, 6.0F * scale, 10.0F * scale,
                        paint(Ps4Helpers.tint(Ps4Helpers.ACCENT, mHover * 0.45F)));
            }

            int mBg = Ps4Helpers.lerp(paint(Ps4Helpers.SOFT_WHITE, 0.05F), paint(Ps4Helpers.ACCENT, 0.22F), mHover);
            Ps4Helpers.rect(muteX, muteY, muteW, muteH, 6.0F * scale, mBg);

            int mBorder = Ps4Helpers.lerp(paint(Ps4Helpers.SOFT_WHITE, 0.08F), paint(Ps4Helpers.ACCENT, 0.75F), mHover);
            Ps4Helpers.outline(muteX, muteY, muteW, muteH, 6.0F * scale, 0.6F * scale, mBorder);

            boolean isMuted = Ps4Sound.isAmbientMuted();
            Ps4Helpers.icon(isMuted ? Ps4Textures.ICON_MUSIC_OFF : Ps4Textures.ICON_MUSIC_ON,
                    muteX + muteW * 0.5F, muteY + muteH * 0.5F, 13.0F * scale, 13.0F * scale,
                    isMuted ? paint(Ps4Helpers.tint(Ps4Helpers.SOFT_WHITE, 0.55F))
                            : Ps4Helpers.lerp(paint(Ps4Helpers.SOFT_WHITE), paint(Ps4Helpers.WHITE), mHover));

            if (mHover > 0.05F && alpha > 0.05F) {
                String tip = isMuted ? "Музыка: Выкл" : "Музыка: Вкл";
                float tipW = Ps4Helpers.measure(Ps4Fonts.UI, tip, 5.8F * scale);
                Ps4Helpers.text(Ps4Fonts.UI, tip, muteX - tipW - 6.0F * scale,
                        muteY + muteH * 0.5F - 2.8F * scale, 5.8F * scale,
                        paint(Ps4Helpers.tint(Ps4Helpers.WHITE, mHover * 0.9F)));
            }

            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }
}
