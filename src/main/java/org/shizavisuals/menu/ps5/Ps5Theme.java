package org.shizavisuals.menu.ps5;

import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 1:1 port of the PS5 (deluxe) menu theme: palette, time based progress
 * helpers, the global draw alpha stack and the loading banner/spinner.
 */
public final class Ps5Theme {
    // Palette (exact deluxe UiTheme values).
    public static final int WHITE = rgba(255, 255, 255, 255);
    public static final int WHITE_72 = rgba(255, 255, 255, 184);
    public static final int WHITE_48 = rgba(255, 255, 255, 122);
    public static final int WHITE_25 = rgba(255, 255, 255, 62);
    public static final int BLUE = rgba(71, 123, 255, 255);
    public static final int BLUE_LIGHT = rgba(123, 163, 255, 255);
    public static final int BLUE_MID = rgba(97, 142, 255, 255);
    public static final int ACCENT_MID = rgba(102, 144, 255, 255);
    public static final int RED = rgba(255, 81, 81, 255);
    public static final int AMBER = rgba(248, 197, 111, 255);
    public static final int ROSE = rgba(231, 130, 137, 255);
    public static final int PANEL_DARK = rgba(15, 14, 17, 255);
    public static final int PANEL_CARD = rgba(19, 18, 22, 255);
    public static final int TEXT_SECONDARY = rgba(255, 255, 255, 178);
    public static final int TEXT_MUTED = rgba(255, 255, 255, 128);
    public static final int TEXT_FAINT = rgba(255, 255, 255, 102);

    private static final Deque<Float> ALPHA_STACK = new ArrayDeque<>();
    private static float currentAlpha = 1.0F;

    private Ps5Theme() {
    }

    // ---- palette helpers -------------------------------------------------

    public static int rgba(int red, int green, int blue, int alpha) {
        return ColorUtil.rgba(red, green, blue, alpha);
    }

    /** Applies only the alpha fraction to the opaque part of a color. */
    public static int withAlpha(int color, float fraction) {
        return ColorUtil.multiplyAlpha(color, Math.max(0.0F, Math.min(1.0F, fraction)));
    }

    /** Color lerped component-wise, matching deluxe lerp. */
    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---- alpha stack -----------------------------------------------------

    public static void pushAlpha(float alpha) {
        ALPHA_STACK.push(currentAlpha);
        currentAlpha *= Math.max(0.0F, Math.min(1.0F, alpha));
    }

    public static void popAlpha() {
        if (ALPHA_STACK.isEmpty()) {
            throw new IllegalStateException("Ps5Theme alpha stack underflow");
        }
        currentAlpha = ALPHA_STACK.pop();
    }

    /** Color premultiplied by the active global draw alpha. */
    public static int paint(int color) {
        return ColorUtil.multiplyAlpha(color, currentAlpha);
    }

    public static int paint(int color, float extraAlpha) {
        return ColorUtil.multiplyAlpha(color, currentAlpha * Math.max(0.0F, Math.min(1.0F, extraAlpha)));
    }

    // ---- time / easing ---------------------------------------------------

    /** easeOutQuad, identical to deluxe method2875. */
    public static float easing(double progress) {
        float t = clamp01((float) progress);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    /** easeOutBack, identical to deluxe method2874. */
    public static float backOut(double progress) {
        float t = clamp01((float) progress);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float shifted = t - 1.0F;
        return 1.0F + c3 * shifted * shifted * shifted + c1 * shifted * shifted;
    }

    public static float progress(long startedAtMillis, long delayMillis, long durationMillis) {
        return clamp01((System.currentTimeMillis() - startedAtMillis - delayMillis) / (float) durationMillis);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    // ---- geometry ---------------------------------------------------------

    public static boolean inRect(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    // ---- draw helpers -----------------------------------------------------

    /** Fullscreen background drawn with the CSS "cover" behavior. */
    public static void cover(Identifier texture, float viewWidth, float viewHeight, float alpha) {
        float coverScale = Math.max(viewWidth / 960.0F, viewHeight / 540.0F);
        float width = 960.0F * coverScale;
        float height = 540.0F * coverScale;
        Render2DUtil.texture((viewWidth - width) * 0.5F, (viewHeight - height) * 0.5F, width, height, texture)
                .color(Ps5Theme.withAlpha(WHITE, alpha))
                .draw();
    }

    /** Centered tinted icon, exactly deluxe centeredIcon. */
    public static void icon(Identifier texture, float centerX, float centerY, float width, float height, int tint) {
        Render2DUtil.texture(centerX - width * 0.5F, centerY - height * 0.5F, width, height, texture)
                .color(tint)
                .draw();
    }

    // ---- text -------------------------------------------------------------

    public static void text(MsdfFont font, String value, float x, float y, float size, int color) {
        if (value == null || value.isEmpty() || size <= 0.0F) {
            return;
        }
        Render2DUtil.text(x, y, size, value).font(font).color(color).draw();
    }

    public static void textCentered(MsdfFont font, String value, float x, float y, float size, int color) {
        if (value == null || value.isEmpty() || size <= 0.0F) {
            return;
        }
        Render2DUtil.text(x, y, size, value).font(font).color(color).align(TextAlign.CENTER).draw();
    }

    public static float measure(MsdfFont font, String value, float size) {
        return font.measureWidth(value, size);
    }

    /** Word wrapping at a pixel budget, matching deluxe wrapping. */
    public static List<String> wrap(MsdfFont font, String value, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() != 0 && !(font.measureWidth(candidate, size) <= maxWidth)) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    // ---- spinner & loading banner -----------------------------------------

    /**
     * Rotating progress arc: spinning blue sweep over a faint full ring.
     * Exact deluxe arc behavior (speed, ease, blend in degrees).
     */
    public static void spinner(float cx, float cy, float radius, float thickness, int color) {
        long now = System.currentTimeMillis();
        float startAngle = -(now % 1100L) / 1100.0F * 360.0F;
        float phase = (now % 1800L) / 1800.0F;
        float eased = phase < 0.5F ? easing(phase * 2.0) : 1.0F - easing((phase - 0.5F) * 2.0);
        float sweep = 55.0F + 230.0F * eased;
        Render2DUtil.ring(cx, cy, radius, thickness, startAngle, sweep, Math.min(48.0F, sweep * 0.42F),
                paint(withAlpha(WHITE, 0.051F)), paint(color));
    }

    /**
     * The deluxe loading banner used behind the loading screen: breathing
     * logo inside a growing ring, themed caption and a wrapped paragraph.
     */
    public static void loadingBanner(long startedAtMillis, float viewWidth, float viewHeight) {
        float scale = Math.min(viewWidth / 960.0F, viewHeight / 540.0F);
        float verticalOffset = (viewHeight - 540.0F * scale) * 0.5F;
        long now = System.currentTimeMillis();
        float ringProgress = backOut(progress(startedAtMillis, 0L, 520L));
        float centerX = viewWidth * 0.5F;
        float centerY = verticalOffset + 229.0F * scale;
        pushAlpha(Math.min(1.0F, ringProgress * 2.0F));
        spinner(centerX, centerY, 28.5F * scale * ringProgress,
                2.7F * scale * Math.max(0.001F, ringProgress), BLUE);
        float breathe = 1.0F + 0.045F * (float) Math.sin((now % 1600L) / 1600.0 * Math.PI * 2.0);
        icon(Ps5Textures.ICON_LOGO, centerX, centerY, 15.5F * scale * ringProgress * breathe, 12.0F * scale * ringProgress * breathe, paint(BLUE));
        popAlpha();

        float captionAlpha = easing(progress(startedAtMillis, 140L, 480L));
        float captionWidth = Ps5Fonts.SEMI.measureWidth("\u0417\u0430\u043f\u0443\u0441\u043a\u0430\u0435\u043c Deluxe...", 15.0F * scale);
        pushAlpha(captionAlpha);
        text(Ps5Fonts.SEMI, "\u0417\u0430\u043f\u0443\u0441\u043a\u0430\u0435\u043c Deluxe" + ".".repeat(1 + (int) (now / 450L % 3L)),
                centerX - captionWidth * 0.5F, verticalOffset + 283.0F * scale + (1.0F - captionAlpha) * 10.0F * scale,
                15.0F * scale, paint(WHITE));
        popAlpha();

        float bodyAlpha = easing(progress(startedAtMillis, 240L, 480L));
        pushAlpha(bodyAlpha);
        float bodyY = verticalOffset + 313.0F * scale + (1.0F - bodyAlpha) * 10.0F * scale;
        for (String line : wrap(Ps5Fonts.SEMI,
                "\u0417\u0430\u0433\u0440\u0443\u0436\u0430\u0435\u043c \u043d\u0435\u043e\u0431\u0445\u043e\u0434\u0438\u043c\u044b\u0435 \u043a\u043e\u043c\u043f\u043e\u043d\u0435\u043d\u0442\u044b \u0438 \u043f\u0440\u0438\u043c\u0435\u043d\u044f\u0435\u043c \u0432\u0430\u0448\u0438 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438. \u0415\u0449\u0451 \u043d\u0435\u043c\u043d\u043e\u0433\u043e - \u0438 Deluxe \u0431\u0443\u0434\u0435\u0442 \u0433\u043e\u0442\u043e\u0432 \u043a \u0440\u0430\u0431\u043e\u0442\u0435.",
                7.5F * scale, 190.0F * scale)) {
            float lineWidth = Ps5Fonts.SEMI.measureWidth(line, 7.5F * scale);
            text(Ps5Fonts.SEMI, line, centerX - lineWidth * 0.5F, bodyY, 7.5F * scale, paint(TEXT_MUTED));
            bodyY += 9.0F * scale;
        }
        popAlpha();
    }
}
