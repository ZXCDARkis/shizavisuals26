package org.shizavisuals.menu.ps4;

import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * PS4 menu drawing helpers: cover wallpaper, glow blobs, centered text and
 * the hover helpers. Mirrors the deluxe UiTheme/Render2D subset the PS4
 * screens rely on.
 */
public final class Ps4Helpers {
    /** Accent color of the theme (deluxe UiTheme.field09686). */
    public static final int ACCENT = rgba(71, 123, 255, 255);
    /** Light accent (deluxe UiTheme.field09687). */
    public static final int ACCENT_LIGHT = rgba(123, 163, 255, 255);
    /** Full white (field09680). */
    public static final int WHITE = rgba(255, 255, 255, 255);
    /** Soft white (field09680-muted, 0xB3FFFFFF). */
    public static final int SOFT_WHITE = 0xB3FFFFFF;

    private Ps4Helpers() {
    }

    private static final java.time.format.DateTimeFormatter CLOCK_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US);
    private static String cachedClock = "";
    private static int cachedClockSecond = -1;

    /** Formats the status-bar clock once per second instead of on every frame. */
    public static String clockString() {
        java.time.LocalTime now = java.time.LocalTime.now();
        if (now.getSecond() != cachedClockSecond) {
            cachedClock = now.format(CLOCK_FORMATTER);
            cachedClockSecond = now.getSecond();
        }
        return cachedClock;
    }

    public static int rgba(int red, int green, int blue, int alpha) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** Stretch-covers a 960x540 design frame over the screen with alpha. */
    public static void coverWallpaper(Identifier texture, float viewWidth, float viewHeight, float alpha) {
        float cover = Math.max(viewWidth / 960.0F, viewHeight / 540.0F);
        // Runtime-registered wallpapers (custom user images) must go through
        // the managed path: they are not pack resources.
        if (texture.getPath().startsWith("dynamic/")) {
            Render2DUtil.dynamicTexture((viewWidth - 960.0F * cover) * 0.5F, (viewHeight - 540.0F * cover) * 0.5F,
                    960.0F * cover, 540.0F * cover, texture)
                    .color(rgba(255, 255, 255, Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, alpha)))))
                    .draw();
            return;
        }
        Render2DUtil.texture((viewWidth - 960.0F * cover) * 0.5F, (viewHeight - 540.0F * cover) * 0.5F,
                960.0F * cover, 540.0F * cover, texture)
                .color(rgba(255, 255, 255, Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, alpha)))))
                .draw();
    }

    /**
     * Soft glowing blob (deluxe Render2D.method4725): translucent rounded
     * shape blurred outwards, approximated through the shadow pass.
     */
    public static void glow(float x, float y, float width, float height, float radius, float blur, int color) {
        Render2DUtil.rect(x, y, width, height)
                .radius(radius)
                .color(0x00000000)
                .shadow(color, blur)
                .draw();
    }

    /** Soft dark drop shadow underneath a shape (deluxe Render2D.method4731). */
    public static void shadow(float x, float y, float width, float height, float radius, float blur, int color) {
        glow(x, y, width, height, radius, blur, color);
    }

    /** Filled rounded rect (deluxe Render2D.method4715). */
    public static void rect(float x, float y, float width, float height, float radius, int color) {
        Render2DUtil.rect(x, y, width, height).radius(radius).color(color).draw();
    }

    /** Outlined rounded rect (deluxe Render2D.method4769), fill kept empty. */
    public static void outline(float x, float y, float width, float height, float radius, float thickness, int color) {
        Render2DUtil.rect(x, y, width, height)
                .radius(radius)
                .color(0x00000000)
                .border(thickness, color)
                .draw();
    }

    /** Tinted icon centered at (cx, cy) (deluxe UiTheme.method2876). */
    public static void icon(Identifier texture, float centerX, float centerY, float width, float height, int tint) {
        Render2DUtil.texture(centerX - width * 0.5F, centerY - height * 0.5F, width, height, texture)
                .color(tint)
                .draw();
    }

    /** Component-lerp between two colors (deluxe UiTheme.method2881). */
    public static int lerp(int from, int to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Alpha-only tint over a fully opaque color (deluxe UiTheme.method2877). */
    public static int tint(int opaqueColor, float alphaFraction) {
        alphaFraction = Math.max(0.0F, Math.min(1.0F, alphaFraction));
        return (Math.round(((opaqueColor >>> 24) & 0xFF) * alphaFraction) << 24)
                | (opaqueColor & 0x00FFFFFF);
    }

    public static float progress(long startedAtMillis, long delayMillis, long durationMillis) {
        return Math.max(0.0F, Math.min(1.0F,
                (System.currentTimeMillis() - startedAtMillis - delayMillis) / (float) durationMillis));
    }

    /** easeOutQuad. */
    public static float easeOut(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    public static boolean inRect(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

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
        Render2DUtil.text(x, y, size, value).font(font).color(color)
                .align(org.shizavisuals.utils.render.gui.TextAlign.CENTER).draw();
    }

    public static float measure(MsdfFont font, String value, float size) {
        return font.measureWidth(value, size);
    }

    /** Cuts the text with "..." so it stays inside maxWidth (fixes overflow). */
    public static String fit(MsdfFont font, String value, float size, float maxWidth) {
        if (value == null || value.isEmpty() || size <= 0.0F || maxWidth <= 0.0F) {
            return "";
        }
        String trimmed = value.trim();
        return font.ellipsize(trimmed, size, 0.0F, maxWidth);
    }

    /** Word wrap to a pixel budget. */
    public static List<String> wrap(MsdfFont font, String value, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : value.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && !(font.measureWidth(candidate, size) <= maxWidth)) {
                lines.add(line);
                line = word;
            } else {
                line = candidate;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }
}
