package org.shizavisuals.menu.ps5;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.ui.AnimatedValue;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.gui.Render2DUtil;

/**
 * 1:1 port of the PS5 (deluxe) welcome screen: loading banner, welcome
 * panel typography, transition chip and active session chip.
 */
public final class Ps5WelcomeScreen extends Screen {
    private final long startedAt = System.currentTimeMillis();
    private long clickedAt;
    private long advancedAt;
    private final AnimatedValue buttonHover = new AnimatedValue(160L, Animation.Easing.EASE_OUT_QUAD);
    private float chipX;
    private float chipY;
    private float chipWidth;
    private float chipHeight;

    public Ps5WelcomeScreen() {
        super(Component.literal("Deluxe"));
    }

    @Override
    public void tick() {
        if (this.advancedAt != 0L && System.currentTimeMillis() - this.advancedAt >= 340L) {
            Minecraft minecraft = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
            minecraft.setScreenAndShow(new Ps5MainMenuScreen(true));
        }
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            long now = System.currentTimeMillis();
            if (this.clickedAt == 0L) {
                if (now - this.startedAt > 400L) {
                    this.clickedAt = now;
                }
                return true;
            }

            if (this.advancedAt == 0L
                    && Ps5Theme.inRect((float) event.x(), (float) event.y(), this.chipX, this.chipY, this.chipWidth, this.chipHeight)) {
                this.advancedAt = now;
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float viewWidth = this.width;
        float viewHeight = this.height;
        float scale = Math.min(viewWidth / 960.0F, viewHeight / 540.0F);
        float verticalOffset = (viewHeight - 540.0F * scale) * 0.5F;
        long now = System.currentTimeMillis();
        RenderContext.enter2D(this.minecraft.gui, graphics, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();
            Render2DUtil.setBackdropBlurScale(1.0F);
            if (this.clickedAt == 0L && now - this.startedAt >= 2700L) {
                this.clickedAt = now;
            }

            float fade = this.clickedAt == 0L ? 0.0F : Ps5Theme.easing(Ps5Theme.progress(this.clickedAt, 0L, 450L));
            Ps5Theme.cover(Ps5Textures.BG_LOADING, viewWidth, viewHeight, 1.0F);
            if (fade > 0.001F) {
                Ps5Theme.cover(Ps5Textures.BG_WELCOME, viewWidth, viewHeight, fade);
            }

            if (fade < 0.999F) {
                Ps5Theme.pushAlpha(1.0F - fade);
                Ps5Theme.loadingBanner(this.startedAt, viewWidth, viewHeight);
                Ps5Theme.popAlpha();
            }

            if (fade > 0.001F) {
                float advance = this.advancedAt == 0L ? 0.0F : Ps5Theme.easing(Ps5Theme.progress(this.advancedAt, 0L, 320L));
                Ps5Theme.pushAlpha(fade * (1.0F - advance));
                this.drawWelcomePhase(viewHeight, scale, verticalOffset,
                        (float) mouseX, (float) mouseY, advance * 26.0F * scale);
                Ps5Theme.popAlpha();
            }
            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }

    private void drawWelcomePhase(float viewHeight, float scale, float verticalOffset, float mouseX, float mouseY, float shift) {
        long phaseStart = this.clickedAt;
        float anchorX = 51.5F * scale - shift;
        float logoAlpha = Ps5Theme.easing(Ps5Theme.progress(phaseStart, 0L, 420L));
        Ps5Theme.pushAlpha(logoAlpha);
        Ps5Theme.icon(Ps5Textures.ICON_LOGO,
                anchorX - (1.0F - logoAlpha) * 14.0F * scale, 40.0F * scale,
                13.5F * scale, 10.5F * scale,
                Ps5Theme.paint(Ps5Theme.BLUE));
        Ps5Theme.popAlpha();

        float introAlpha = Ps5Theme.easing(Ps5Theme.progress(phaseStart, 90L, 460L));
        Ps5Theme.pushAlpha(introAlpha);
        float introX = anchorX - (1.0F - introAlpha) * 18.0F * scale;
        Ps5Theme.text(Ps5Fonts.SEMI, "\u0414\u043e\u0431\u0440\u043e \u043f\u043e\u0436\u0430\u043b\u043e\u0432\u0430\u0442\u044c \u0432",
                introX, verticalOffset + 209.0F * scale, 20.0F * scale, Ps5Theme.paint(Ps5Theme.WHITE));
        Ps5Theme.icon(Ps5Textures.ICON_SERIF,
                introX, verticalOffset + 236.0F * scale,
                68.04957F * scale, 15.4F * scale,
                Ps5Theme.paint(Ps5Theme.BLUE));
        Ps5Theme.popAlpha();

        float bodyAlpha = Ps5Theme.easing(Ps5Theme.progress(phaseStart, 170L, 460L));
        Ps5Theme.pushAlpha(bodyAlpha);
        float bodyY = verticalOffset + 268.5F * scale;
        for (String line : Ps5Theme.wrap(Ps5Fonts.SEMI,
                "\u0412\u0441\u0451 \u0433\u043e\u0442\u043e\u0432\u043e \u0434\u043b\u044f \u043a\u043e\u043c\u0444\u043e\u0440\u0442\u043d\u043e\u0439 \u0438\u0433\u0440\u044b. \u041d\u0430\u0441\u0442\u0440\u043e\u0439\u0442\u0435 \u043a\u043b\u0438\u0435\u043d\u0442 \u043f\u043e\u0434 \u0441\u0435\u0431\u044f, \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439\u0442\u0435 \u043b\u044e\u0431\u0438\u043c\u044b\u0435 \u0444\u0443\u043d\u043a\u0446\u0438\u0438 \u0438 \u043e\u0442\u043a\u0440\u043e\u0439\u0442\u0435 \u0431\u043e\u043b\u044c\u0448\u0435 \u0432\u043e\u0437\u043c\u043e\u0436\u043d\u043e\u0441\u0442\u0435\u0439 \u0432\u043c\u0435\u0441\u0442\u0435 \u0441 Deluxe.",
                7.5F * scale, 246.0F * scale)) {
            Ps5Theme.text(Ps5Fonts.SEMI, line,
                    anchorX - (1.0F - bodyAlpha) * 18.0F * scale, bodyY,
                    7.5F * scale, Ps5Theme.paint(Ps5Theme.TEXT_MUTED));
            bodyY += 9.0F * scale;
        }
        Ps5Theme.popAlpha();

        float chipAlpha = Ps5Theme.easing(Ps5Theme.progress(phaseStart, 250L, 460L));
        Ps5Theme.pushAlpha(chipAlpha);
        this.chipX = anchorX - (1.0F - chipAlpha) * 18.0F * scale;
        this.chipY = verticalOffset + 311.0F * scale;
        this.chipWidth = 231.0F * scale;
        this.chipHeight = 19.5F * scale;
        this.buttonHover.toward(
                this.advancedAt == 0L
                        && Ps5Theme.inRect(mouseX, mouseY, this.chipX, this.chipY, this.chipWidth, this.chipHeight)
                        ? 1.0F : 0.0F
        );
        float hover = this.buttonHover.value();
        Render2DUtil.rect(this.chipX, this.chipY, this.chipWidth, this.chipHeight)
                .color(Ps5Theme.paint(Ps5Theme.lerpColor(Ps5Theme.BLUE, Ps5Theme.BLUE_LIGHT, hover * 0.65F)))
                .radius(6.0F * scale)
                .draw();
        Render2DUtil.rect(this.chipX, this.chipY, this.chipWidth, this.chipHeight)
                .color(ColorUtil.TRANSPARENT)
                .radius(6.0F * scale)
                .border(0.5F, Ps5Theme.paint(Ps5Theme.ACCENT_MID))
                .draw();
        Ps5Theme.icon(Ps5Textures.ICON_ARROW,
                this.chipX + 9.5F * scale + hover * 1.5F * scale,
                this.chipY + this.chipHeight * 0.5F,
                6.0F * scale, 6.0F * scale,
                Ps5Theme.paint(Ps5Theme.WHITE));
        Ps5Theme.text(Ps5Fonts.SEMI, "\u041f\u0435\u0440\u0435\u0439\u0442\u0438 \u0432 \u0433\u043b\u0430\u0432\u043d\u043e\u0435 \u043c\u0435\u043d\u044e",
                this.chipX + 19.5F * scale,
                this.chipY + this.chipHeight * 0.5F - 3.5F * scale,
                7.0F * scale,
                Ps5Theme.paint(Ps5Theme.WHITE));
        Ps5Theme.popAlpha();

        float sessionAlpha = Ps5Theme.easing(Ps5Theme.progress(phaseStart, 330L, 460L));
        Ps5Theme.pushAlpha(sessionAlpha);
        this.drawSessionChip(anchorX, viewHeight - 51.0F * scale + (1.0F - sessionAlpha) * 10.0F * scale, scale);
        Ps5Theme.popAlpha();
    }

    private void drawSessionChip(float x, float y, float scale) {
        Minecraft minecraft = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        String name = minecraft.getUser() != null ? minecraft.getUser().getName() : "Player";
        Identifier skin = Ps5SkinCache.texture(minecraft, name);
        // Face crop at 0.125..0.25 plus the hat layer at 0.625..0.75 (deluxe 1:1).
        Render2DUtil.texture(x, y, 15.0F * scale, 15.0F * scale, skin)
                .color(Ps5Theme.paint(Ps5Theme.WHITE))
                .radius(5.0F * scale)
                .uv(0.125F, 0.125F, 0.25F, 0.25F)
                .draw();
        Render2DUtil.texture(x, y, 15.0F * scale, 15.0F * scale, skin)
                .color(Ps5Theme.paint(Ps5Theme.WHITE))
                .radius(5.0F * scale)
                .uv(0.625F, 0.125F, 0.75F, 0.25F)
                .draw();
        Ps5Theme.icon(Ps5Textures.ICON_PERSON,
                x + 22.2F * scale, y + 3.2F * scale,
                3.4F * scale, 4.5F * scale,
                Ps5Theme.paint(Ps5Theme.TEXT_MUTED));
        Ps5Theme.text(Ps5Fonts.SEMI, "\u0410\u043a\u0442\u0438\u0432\u043d\u0430\u044f \u0441\u0435\u0441\u0441\u0438\u044f",
                x + 26.5F * scale, y + 0.5F * scale,
                5.0F * scale, Ps5Theme.paint(Ps5Theme.TEXT_MUTED));
        Ps5Theme.text(Ps5Fonts.SEMI_BOLD, name,
                x + 20.0F * scale, y + 7.5F * scale,
                6.5F * scale, Ps5Theme.paint(Ps5Theme.WHITE));
    }
}
