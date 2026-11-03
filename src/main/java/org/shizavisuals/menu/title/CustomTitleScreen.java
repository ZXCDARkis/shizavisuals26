package org.shizavisuals.menu.title;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.i18n.UiLanguage;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.List;

public final class CustomTitleScreen extends Screen {
    private static final String LOGO = "shizavisuals.";
    private static final int ACTION_SINGLEPLAYER = 0;
    private static final int ACTION_MULTIPLAYER = 1;
    private static final int ACTION_OPTIONS = 2;
    private static final int ACTION_WALLPAPER = 3;
    private static final int ACTION_QUIT = 4;
    private static final int ACTION_COUNT = 5;
    private static final float MIN_SCALE = 0.62F;
    private static final float MAX_SCALE = 1.20F;
    private static final long START_NANOS = System.nanoTime();

    private final float[][] actionRects = new float[ACTION_COUNT][4];
    private final float[] hoverAmount = new float[ACTION_COUNT];
    /** Windows 11 style wallpaper carousel; replaces the old scrolling list. */
    private final Win11WallpaperStrip strip = new Win11WallpaperStrip();

    private long startedAt;
    private int mouseX;
    private int mouseY;

    public CustomTitleScreen() {
        super(Component.literal("ShizaVisuals"));
    }

    @Override
    protected void init() {
        this.startedAt = System.currentTimeMillis();
        WallpaperStore.reload();
        this.strip.close();
        super.init();
    }

    @Override
    public void tick() {
        WallpaperStore.tick();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.strip.visible();
    }

    @Override
    public void onClose() {
        this.strip.close();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        updateLayout();
        float intro = clamp((System.currentTimeMillis() - this.startedAt) / 550.0F, 0.0F, 1.0F);
        float delta = Math.min(1.0F, Math.max(0.0F, partialTick * 20.0F));
        boolean stripOpen = this.strip.visible();
        for (int index = 0; index < ACTION_COUNT; index++) {
            boolean hovered = !stripOpen && hit(actionRects[index], mouseX, mouseY);
            this.hoverAmount[index] = clamp(this.hoverAmount[index] + (hovered ? 0.12F : -0.12F) * delta, 0.0F, 1.0F);
        }
        this.strip.update(WallpaperStore.all(), mouseX, mouseY);
        RenderContext.enter2D(this.minecraft.gui, graphics, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();
            Render2DUtil.setBackdropBlurScale(1.0F);
            drawWallpaper(intro);
            drawShade(intro);
            drawLogo(intro);
            drawPills(intro);
            this.strip.render(WallpaperStore.all());
            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        updateLayout();
        if (event.button() != 0) {
            if (this.strip.visible()) {
                this.strip.close();
                return true;
            }
            return false;
        }
        if (this.strip.visible()) {
            List<WallpaperStore.Wallpaper> entries = WallpaperStore.all();
            int hitIndex = this.strip.hitCard(entries, event.x(), event.y());
            if (hitIndex >= 0) {
                applyWallpaper(entries, hitIndex);
            } else {
                this.strip.close();
            }
            return true;
        }
        for (int index = 0; index < ACTION_COUNT; index++) {
            if (hit(actionRects[index], event.x(), event.y())) {
                activate(index);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.strip.visible()) {
            return false;
        }
        // Any wheel over the carousel scrubs it; the strip springs to the nearest
        // card so a hard flick still lands cleanly.
        this.strip.scrollBy(WallpaperStore.all(), verticalAmount > 0.0D ? -1.0F : 1.0F);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.strip.visible()) {
            List<WallpaperStore.Wallpaper> entries = WallpaperStore.all();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.strip.close();
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                this.strip.scrollBy(entries, -1.0F);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                this.strip.scrollBy(entries, 1.0F);
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                this.strip.scrollBy(entries, -1.0F);
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                this.strip.scrollBy(entries, 1.0F);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                applyWallpaper(entries, this.strip.index());
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_S) {
            activate(ACTION_SINGLEPLAYER);
            return true;
        }
        if (key == GLFW.GLFW_KEY_M) {
            activate(ACTION_MULTIPLAYER);
            return true;
        }
        if (key == GLFW.GLFW_KEY_O) {
            activate(ACTION_OPTIONS);
            return true;
        }
        if (key == GLFW.GLFW_KEY_W) {
            activate(ACTION_WALLPAPER);
            return true;
        }
        if (key == GLFW.GLFW_KEY_Q) {
            activate(ACTION_QUIT);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.strip.visible();
    }

    /** Applies a wallpaper from the carousel and keeps the strip in sync with it. */
    private void applyWallpaper(List<WallpaperStore.Wallpaper> entries, int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        WallpaperStore.select(entries.get(index));
        this.strip.focus(index, entries.size());
    }

    private void activate(int action) {
        if (this.minecraft == null) {
            return;
        }
        switch (action) {
            case ACTION_SINGLEPLAYER -> this.minecraft.setScreenAndShow(new SelectWorldScreen(this));
            case ACTION_MULTIPLAYER -> this.minecraft.setScreenAndShow(new JoinMultiplayerScreen(this));
            case ACTION_OPTIONS -> this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
            case ACTION_QUIT -> this.minecraft.stop();
            case ACTION_WALLPAPER -> {
                if (this.strip.visible()) {
                    this.strip.close();
                } else {
                    this.strip.ensureOpen();
                }
            }
            default -> {
            }
        }
    }

    private void updateLayout() {
        if (this.width <= 0 || this.height <= 0) {
            return;
        }
        float scale = scale();
        float cx = this.width * 0.5F;

        float buttonWidth = Math.min(122.0F * scale, (this.width - 40.0F * scale) / 2.0F);
        float buttonHeight = 25.0F * scale;
        float gap = 6.0F * scale;
        float rowOneY = this.height * 0.5F - 14.0F * scale;
        float rowWidth = buttonWidth * 2.0F + gap;
        float rowOneX = cx - rowWidth * 0.5F;
        setRect(actionRects[ACTION_SINGLEPLAYER], rowOneX, rowOneY, buttonWidth, buttonHeight);
        setRect(actionRects[ACTION_MULTIPLAYER], rowOneX + buttonWidth + gap, rowOneY, buttonWidth, buttonHeight);

        float smallHeight = 21.0F * scale;
        float smallGap = 5.0F * scale;
        float smallWidth = (rowWidth - smallGap * 2.0F) / 3.0F;
        float rowTwoY = rowOneY + buttonHeight + smallGap;
        setRect(actionRects[ACTION_OPTIONS], rowOneX, rowTwoY, smallWidth, smallHeight);
        setRect(actionRects[ACTION_WALLPAPER], rowOneX + smallWidth + smallGap, rowTwoY, smallWidth, smallHeight);
        setRect(actionRects[ACTION_QUIT], rowOneX + (smallWidth + smallGap) * 2.0F, rowTwoY, smallWidth, smallHeight);

        // The carousel owns the bottom of the screen; keep the buttons clear of it.
        this.strip.layout(this.width, this.height, scale);
    }

    private void drawWallpaper(float intro) {
        WallpaperStore.Wallpaper selected = WallpaperStore.selected();
        WallpaperStore.drawable(selected);
        WallpaperStore.Wallpaper drawable = selected.ready() ? selected : WallpaperStore.fallback();
        float time = animationTime();
        float parallaxX = (this.mouseX / Math.max(1.0F, this.width) - 0.5F) * 0.010F
                + (float) Math.sin(time * 0.31F) * 0.0030F;
        float parallaxY = (this.mouseY / Math.max(1.0F, this.height) - 0.5F) * 0.007F
                + (float) Math.cos(time * 0.24F) * 0.0022F;
        CoverUv uv = shiftCover(coverUv(drawable.aspectRatio(), this.width, this.height), parallaxX, parallaxY);
        Render2DUtil.TextureBuilder texture = Render2DUtil.texture(0.0F, 0.0F, this.width, this.height, drawable.textureId())
                .uv(uv.u0(), uv.v0(), uv.u1(), uv.v1())
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, intro));
        if (drawable.managed()) {
            texture.managed();
        }
        texture.draw();
    }

    private void drawShade(float intro) {
        float time = animationTime();
        float shift = 0.5F + 0.5F * (float) Math.sin(time * 0.6F);
        int top = ColorUtil.rgba(Math.round(8 + shift * 8), Math.round(8 + shift * 8), Math.round(12 + shift * 10), Math.round(96.0F * intro));
        int bottom = ColorUtil.rgba(Math.round(14 - shift * 6), Math.round(14 - shift * 6), Math.round(20 - shift * 8), Math.round(168.0F * intro));
        Render2DUtil.rect(0.0F, 0.0F, this.width, this.height)
                .color(top, top, bottom, bottom)
                .draw();
    }

    private void drawLogo(float intro) {
        float scale = scale();
        float cx = this.width * 0.5F;
        float time = animationTime();
        MsdfFont font = UiFonts.getFontForText(LOGO, UiFontStyle.BOLD.weight());
        float size = 140.0F * scale;
        float maxWidth = this.width * 0.48F;
        float measured = font.measureWidth(LOGO, size);
        if (measured > maxWidth && measured > 0.0F) {
            size = size * (maxWidth / measured);
        }
        float y = this.height * 0.5F - 92.0F * scale + (float) Math.sin(time * 1.1F) * 1.6F * scale;
        Render2DUtil.text(cx, font.centeredTextY(y, size), size, LOGO)
                .font(font)
                .style(UiFontStyle.BOLD)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, intro))
                .outline(ColorUtil.multiplyAlpha(ColorUtil.rgba(0, 0, 0, 120), intro), Math.max(1.0F, 1.6F * scale))
                .align(TextAlign.CENTER)
                .draw();
    }

    private void drawPills(float intro) {
        for (int index = ACTION_SINGLEPLAYER; index <= ACTION_QUIT; index++) {
            drawPill(index, intro);
        }
    }
    private void drawPill(int index, float intro) {
        float scale = scale();
        float[] rect = actionRects[index];
        // Pill: radius is half the height, the iOS signature. A 5px radius on a
        // 25px-tall button reads as a "web button", not a native control.
        float radius = rect[3] * 0.5F;
        float hover = this.hoverAmount[index];
        boolean active = index == ACTION_SINGLEPLAYER;
        String label = label(index);

        if (active) {
            int fill = ColorUtil.multiplyAlpha(ColorUtil.rgba(242, 242, 246, 255), intro);
            Render2DUtil.rect(rect[0], rect[1], rect[2], rect[3])
                    .color(fill)
                    .radius(radius)
                    .draw();
            float size = pillTextSize(rect, label, 12.0F * scale);
            text(rect[0] + rect[2] * 0.5F, pillTextY(rect, label, size), size, label,
                    ColorUtil.multiplyAlpha(ColorUtil.rgba(18, 18, 24, 255), intro), TextAlign.CENTER, UiFontStyle.SEMIBOLD);
            return;
        }

        int top = ColorUtil.multiplyAlpha(ColorUtil.rgba(40, 40, 48, 220), intro);
        int bottom = ColorUtil.multiplyAlpha(ColorUtil.rgba(24, 24, 31, 220), intro);
        Render2DUtil.rect(rect[0], rect[1], rect[2], rect[3])
                .color(top, top, bottom, bottom)
                .radius(radius)
                .draw();
        int border = ColorUtil.multiplyAlpha(ColorUtil.rgba(255, 255, 255, Math.round(45.0F + hover * 75.0F)), intro);
        Render2DUtil.rect(rect[0], rect[1], rect[2], rect[3])
                .color(ColorUtil.TRANSPARENT)
                .radius(radius)
                .border(Math.max(0.5F, 0.5F * scale), border)
                .draw();
        int color = hover > 0.5F
                ? ColorUtil.multiplyAlpha(ColorUtil.rgba(240, 240, 245, 255), intro)
                : ColorUtil.multiplyAlpha(ColorUtil.rgba(160, 160, 172, 235), intro);
        float size = pillTextSize(rect, label, 12.0F * scale);
        text(rect[0] + rect[2] * 0.5F, pillTextY(rect, label, size), size, label, color, TextAlign.CENTER, UiFontStyle.MEDIUM);
        if (index == ACTION_WALLPAPER) {
            String name = ellipsize(WallpaperStore.selected().displayName(), 6.0F * scale, rect[2] * 0.62F);
            text(rect[0] + rect[2] * 0.5F, rect[1] + rect[3] + 8.0F * scale, 6.5F * scale, name,
                    ColorUtil.multiplyAlpha(ColorUtil.rgba(214, 218, 230, 150), intro), TextAlign.CENTER, UiFontStyle.REGULAR);
}
    }

    private String label(int action) {
        boolean russian = UiLanguage.current() == UiLanguage.RUSSIAN;
        return switch (action) {
            case ACTION_SINGLEPLAYER -> russian ? "Одиночная игра" : "Singleplayer";
            case ACTION_MULTIPLAYER -> russian ? "Сетевая игра" : "Multiplayer";
            case ACTION_OPTIONS -> russian ? "Настройки" : "Options";
            case ACTION_WALLPAPER -> russian ? "Обои" : "Wallpaper";
            case ACTION_QUIT -> russian ? "Выход" : "Quit";
            default -> "";
        };
    }

    private float fitSize(String value, float preferred, float maxWidth) {
        MsdfFont font = fontFor(value, UiFontStyle.MEDIUM.weight());
        float measured = font.measureWidth(value, preferred);
        return measured <= maxWidth || measured <= 0.0F ? preferred : preferred * (maxWidth / measured);
    }

    private float pillTextSize(float[] rect, String value, float preferred) {
        return clamp(fitSize(value, preferred, rect[2] - 14.0F * scale()), 5.5F, preferred);
    }

    private float pillTextY(float[] rect, String value, float size) {
        return fontFor(value, UiFontStyle.MEDIUM.weight()).centeredTextY(rect[1] + rect[3] * 0.5F, size);
    }

    private String ellipsize(String value, float size, float maxWidth) {
        return fontFor(value, UiFontStyle.REGULAR.weight())
                .ellipsize(value, size, size * UiFontStyle.REGULAR.letterSpacingEm(), Math.max(1.0F, maxWidth));
    }

    private void text(float x, float y, float size, String value, int color, TextAlign align, UiFontStyle style) {
        if (value == null || value.isEmpty() || size <= 0.0F) {
            return;
        }
        Render2DUtil.text(x, y, size, value)
                .font(fontFor(value, style.weight()))
                .style(style)
                .color(color)
                .align(align)
                .draw();
    }

    private MsdfFont fontFor(String value, int weight) {
        return UiFonts.getFontForText(value, weight);
    }

    private float scale() {
        return clamp(Math.min(this.width / 900.0F, this.height / 600.0F), MIN_SCALE, MAX_SCALE);
    }

    private static float animationTime() {
        return (System.nanoTime() - START_NANOS) * 1.0E-9F;
    }

    private static CoverUv shiftCover(CoverUv uv, float offsetX, float offsetY) {
        float width = clamp(uv.u1() - uv.u0(), 0.0F, 1.0F);
        float height = clamp(uv.v1() - uv.v0(), 0.0F, 1.0F);
        float u0 = clamp(uv.u0() + offsetX, 0.0F, 1.0F - width);
        float v0 = clamp(uv.v0() + offsetY, 0.0F, 1.0F - height);
        return new CoverUv(u0, v0, u0 + width, v0 + height);
    }

    private static CoverUv coverUv(float sourceAspect, float targetWidth, float targetHeight) {
        float targetAspect = targetWidth / Math.max(1.0F, targetHeight);
        if (sourceAspect > targetAspect) {
            float visible = targetAspect / Math.max(0.0001F, sourceAspect);
            float offset = (1.0F - visible) * 0.5F;
            return new CoverUv(offset, 0.0F, offset + visible, 1.0F);
        }
        float visible = sourceAspect / Math.max(0.0001F, targetAspect);
        float offset = (1.0F - visible) * 0.5F;
        return new CoverUv(0.0F, offset, 1.0F, offset + visible);
    }

    private static boolean hit(float[] rect, double x, double y) {
        return x >= rect[0] && x <= rect[0] + rect[2] && y >= rect[1] && y <= rect[1] + rect[3];
    }

    private static boolean hit(float x, float y, float width, float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static void setRect(float[] rect, float x, float y, float width, float height) {
        rect[0] = x;
        rect[1] = y;
        rect[2] = width;
        rect[3] = height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CoverUv(float u0, float v0, float u1, float v1) {
    }
}
