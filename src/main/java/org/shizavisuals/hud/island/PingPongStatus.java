package org.shizavisuals.hud.island;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.Random;

public class PingPongStatus extends Screen {

    private static final float ARENA_RADIUS = 24.0F;
    private static final float WALL_PAD = 18.0F;

    private static final float BALL_R = 6.0F;
    private static final float PADDLE_W = 90.0F;
    private static final float PADDLE_H = 14.0F;
    private static final float PADDLE_R = 7.0F;

    private static final String GAMES_KEY = "hud.dynamic_island.game.totalGames";
    private static final String BEST_KEY = "hud.dynamic_island.game.bestScore";

    private static final Random RNG = new Random();

    private final DynamicIsland island;

    private float paddleX;
    private float ballX;
    private float ballY;
    private float velX;
    private float velY;
    private int score;
    private int lives;
    private boolean gameOver;
    private long lastNano;

    private final float[] hitAnims = new float[3];

    private int totalGames;
    private int bestScore;

    private float mouseX;
    private float mouseY;

    public PingPongStatus(DynamicIsland island) {
        super(Component.literal("Dynamic Island Ping Pong"));
        this.island = island;
        this.totalGames = MenuConfigStore.getInt(GAMES_KEY, 0);
        this.bestScore = MenuConfigStore.getInt(BEST_KEY, 0);
        resetGame();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.island.onGameClosed();
        saveStats();
        super.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
    }

    public void renderHud(Minecraft mc) {
        long now = System.nanoTime();
        float dt = Math.min(0.05F, Math.max(0.0F, (now - this.lastNano) / 1.0E9F));
        this.lastNano = now;
        update(dt);
        setMouse(mc.mouseHandler.getScaledXPos(mc.getWindow()),
                mc.mouseHandler.getScaledYPos(mc.getWindow()));
        drawGame();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (this.gameOver && event.button() == 0) {
            resetGame();
        }
        return super.mouseClicked(event, doubled);
    }

    public void open() {
        resetGame();
        this.lastNano = System.nanoTime();
        MinecraftContext.mc.gui.setScreen(this);
    }

    public boolean canShow() {
        return MinecraftContext.mc.gui.screen() == this;
    }

    private void saveStats() {
        MenuConfigStore.save(data -> {
            data.addProperty(GAMES_KEY, this.totalGames);
            data.addProperty(BEST_KEY, this.bestScore);
        });
    }

    private void resetGame() {
        this.score = 0;
        this.lives = 3;
        this.gameOver = false;
        this.hitAnims[0] = this.hitAnims[1] = this.hitAnims[2] = 0.0F;
        resetRound();
    }

    private void resetRound() {
        this.paddleX = 0.0F;
        this.ballX = 0.0F;
        this.ballY = 160.0F - WALL_PAD - PADDLE_H - 10.0F;
        this.velX = (RNG.nextFloat() * 2.0F - 1.0F) * 70.0F;
        this.velY = -170.0F;
    }

    private void update(float dt) {
        for (int i = 0; i < this.hitAnims.length; i++) {
            this.hitAnims[i] = Math.max(0.0F, this.hitAnims[i] - dt * (1.2F + i * 0.4F));
        }

        if (this.gameOver) {
            return;
        }

        float left = -140.0F + WALL_PAD;
        float right = 140.0F - WALL_PAD;
        float top = -160.0F + WALL_PAD;
        float bottom = 160.0F - WALL_PAD;

        float rawMx = toDesignX(this.mouseX);
        float tgt = Math.clamp(rawMx, left + PADDLE_W / 2.0F, right - PADDLE_W / 2.0F);
        this.paddleX += (tgt - this.paddleX) * Math.min(1.0F, dt * 16.0F);

        float prevX = this.ballX;
        float prevY = this.ballY;
        this.ballX += this.velX * dt;
        this.ballY += this.velY * dt;

        if (this.ballX - BALL_R < left) {
            this.velX = Math.abs(this.velX);
            this.ballX = left + BALL_R;
        }
        if (this.ballX + BALL_R > right) {
            this.velX = -Math.abs(this.velX);
            this.ballX = right - BALL_R;
        }
        if (this.ballY - BALL_R < top) {
            this.velY = Math.abs(this.velY);
            this.ballY = top + BALL_R;
        }

        handleIslandCollision();
        handlePaddleCollision(prevX, prevY);

        if (this.ballY - BALL_R > bottom) {
            if (--this.lives <= 0) {
                this.gameOver = true;
                this.hitAnims[2] = 1.0F;
                this.totalGames++;
                if (this.score > this.bestScore) {
                    this.bestScore = this.score;
                }
                saveStats();
            } else {
                resetRound();
            }
        }
    }

    private void handleIslandCollision() {
        float ix = toDesignX(this.island.x());
        float iy = toDesignY(this.island.y());
        float iw = this.island.width() / this.island.scale();
        float ih = this.island.height() / this.island.scale();
        float ib = iy + ih;

        if (this.ballY - BALL_R <= ib && this.ballY + BALL_R >= iy && this.velY < 0.0F) {
            float clampedX = Math.clamp(this.ballX, ix, ix + iw);
            if (Math.abs(this.ballX - clampedX) <= BALL_R + 1.5F) {
                this.ballY = ib + BALL_R;
                this.velY = Math.abs(this.velY) + Math.min(90.0F, this.score * 2.5F);
                this.velX += (this.ballX - (ix + iw / 2.0F)) / (iw / 2.0F) * 45.0F;
                this.score++;
                this.hitAnims[2] = 0.75F;
            }
        }
    }

    private void handlePaddleCollision(float prevX, float prevY) {
        float paddleY = 160.0F - WALL_PAD - PADDLE_H;
        float halfW = PADDLE_W / 2.0F;

        if (this.velY > 0.0F) {
            float prevBot = prevY + BALL_R;
            float currBot = this.ballY + BALL_R;
            if (prevBot <= paddleY && currBot >= paddleY && currBot != prevBot) {
                float t = Math.clamp((paddleY - prevBot) / (currBot - prevBot), 0.0F, 1.0F);
                float intX = t * (this.ballX - prevX) + prevX;
                if (intX >= this.paddleX - halfW && intX <= this.paddleX + halfW) {
                    this.ballX = intX;
                    this.ballY = paddleY - BALL_R;
                    bounceFromPaddle(intX, halfW);
                    return;
                }
            }
            if (this.ballY + BALL_R >= paddleY && this.ballY - BALL_R <= paddleY + PADDLE_H
                    && this.ballX >= this.paddleX - halfW && this.ballX <= this.paddleX + halfW) {
                this.ballY = paddleY - BALL_R;
                bounceFromPaddle(this.ballX, halfW);
            }
        }
    }

    private void bounceFromPaddle(float collX, float halfW) {
        this.velY = -Math.abs(this.velY) - Math.min(80.0F, this.score * 2.0F);
        this.velX = Math.clamp(this.velX + (collX - this.paddleX) / halfW * 60.0F, -180.0F, 180.0F);
        this.hitAnims[1] = 1.0F;
    }

    private void drawGame() {
        float s = this.island.scale();
        float sw = MinecraftContext.mc.getWindow().getGuiScaledWidth();
        float sh = MinecraftContext.mc.getWindow().getGuiScaledHeight();
        float ox = sw / 2.0F;
        float oy = sh / 2.0F;
        float arenaX = ox - 140.0F * s;
        float arenaY = oy - 160.0F * s;

        int accent = ColorUtil.withAlpha(Theme.getAccent(), 220);

        int bg = ColorUtil.rgba(20, 24, 36, Math.round(170.0F + this.hitAnims[2] * 30.0F));
        Render2DUtil.rect(arenaX, arenaY, 280.0F * s, 320.0F * s)
                .radius(ARENA_RADIUS * s)
                .color(bg)
                .draw();

        int paddleBase = ColorUtil.lerp(accent, ColorUtil.WHITE, 0.25F);
        int paddleColor = ColorUtil.lerp(paddleBase, ColorUtil.WHITE, this.hitAnims[1] * 0.7F);
        float paddleY = oy + (160.0F - WALL_PAD - PADDLE_H) * s;
        Render2DUtil.rect(ox + (this.paddleX - PADDLE_W / 2.0F) * s, paddleY, PADDLE_W * s, PADDLE_H * s)
                .radius(PADDLE_R * s)
                .color(paddleColor)
                .border(0.5F * s, ColorUtil.rgba(255, 255, 255, 40))
                .blur(8.0F * s, 0.35F)
                .draw();

        Render2DUtil.rect(ox + (this.ballX - BALL_R) * s, oy + (this.ballY - BALL_R) * s,
                        BALL_R * 2.0F * s, BALL_R * 2.0F * s)
                .radius(BALL_R * s)
                .color(ColorUtil.lerp(ColorUtil.WHITE, Theme.getAccent(), 0.3F))
                .draw();

        drawHud(accent, arenaY, s);
    }

    private void drawHud(int accent, float arenaY, float s) {
        float sw = MinecraftContext.mc.getWindow().getGuiScaledWidth();
        float cx = sw / 2.0F;

        centeredText("Games: " + this.totalGames + "   |   Best: " + this.bestScore,
                cx, arenaY + 338.0F * s, 6.5F * s, UiFontStyle.MEDIUM,
                ColorUtil.multiplyAlpha(ColorUtil.WHITE, 153.0F / 255.0F));

        centeredText("Score", cx - 60.0F * s, arenaY + 6.0F * s, 7.0F * s, UiFontStyle.MEDIUM,
                ColorUtil.multiplyAlpha(ColorUtil.WHITE, 192.0F / 255.0F));
        centeredText(String.valueOf(this.score), cx - 60.0F * s, arenaY + 22.0F * s, 12.0F * s,
                UiFontStyle.SEMIBOLD, accent);

        centeredText("Lives", cx + 60.0F * s, arenaY + 6.0F * s, 7.0F * s, UiFontStyle.MEDIUM,
                ColorUtil.multiplyAlpha(ColorUtil.WHITE, 192.0F / 255.0F));
        centeredText(String.valueOf(Math.max(this.lives, 0)), cx + 60.0F * s, arenaY + 22.0F * s, 12.0F * s,
                UiFontStyle.SEMIBOLD, accent);

        if (this.gameOver) {
            float gy = arenaY + 160.0F * s;
            Render2DUtil.rect(cx - 90.0F * s, gy - 40.0F * s, 180.0F * s, 80.0F * s)
                    .radius(18.0F * s)
                    .color(ColorUtil.rgba(12, 14, 22, 200))
                    .blur(35.0F * s)
                    .draw();
            centeredText("Game Over", cx, gy - 14.0F * s, 10.0F * s, UiFontStyle.SEMIBOLD, ColorUtil.WHITE);
            centeredText("Score: " + this.score, cx, gy, 7.5F * s, UiFontStyle.MEDIUM, ColorUtil.WHITE);
            centeredText("Click to play again", cx, gy + 10.0F * s, 6.5F * s, UiFontStyle.MEDIUM,
                    ColorUtil.multiplyAlpha(ColorUtil.WHITE, 217.0F / 255.0F));
        }
    }

    private void setMouse(double mx, double my) {
        this.mouseX = (float) mx;
        this.mouseY = (float) my;
    }

    private float toDesignX(float px) {
        float s = this.island.scale();
        return (px - MinecraftContext.mc.getWindow().getGuiScaledWidth() / 2.0F) / s;
    }

    private float toDesignY(float py) {
        float s = this.island.scale();
        return (py - MinecraftContext.mc.getWindow().getGuiScaledHeight() / 2.0F) / s;
    }

    private static void centeredText(String text, float x, float y, float size,
                                     UiFontStyle style, int color) {
        MsdfFont font = UiFonts.getFontForText(text, style.weight());
        Render2DUtil.text(x, font.centeredTextY(y + size / 2.0F, size), size, text)
                .font(font)
                .style(style)
                .color(color)
                .align(TextAlign.CENTER)
                .draw();
    }
}
