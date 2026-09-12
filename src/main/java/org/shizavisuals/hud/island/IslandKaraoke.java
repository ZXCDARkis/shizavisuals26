package org.shizavisuals.hud.island;

import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;

public final class IslandKaraoke {

    private IslandKaraoke() {
    }

    public static float[][] wordBounds(MsdfFont font, String line, float size, float ls) {
        String[] words = line.split(" ", -1);
        float[][] bounds = new float[words.length][2];
        float cursor = 0.0F;
        float spaceWidth = font.measureWidth(" ", size, ls);
        for (int i = 0; i < words.length; i++) {
            float w = font.measureWidth(words[i], size, ls);
            bounds[i][0] = cursor;
            bounds[i][1] = cursor + w;
            cursor += w + spaceWidth;
        }
        return bounds;
    }

    public static int charCountForWidth(MsdfFont font, String line, float size, float ls, float maxWidth) {
        if (maxWidth <= 0.0F) {
            return 0;
        }
        for (int i = 1; i <= line.length(); i++) {
            if (font.measureWidth(line.substring(0, i), size, ls) > maxWidth) {
                return i - 1;
            }
        }
        return line.length();
    }

    /**
     * Draws a karaoke line character by character. Already-sung characters are
     * fully opaque, upcoming ones are dimmed, and characters near the window
     * edges fade out over {@code fadeZone} pixels instead of being hard-clipped.
     * {@code swap} drives the slide-and-fade transition between lyric lines;
     * {@code cursorPx < 0} disables the karaoke fill (plain dim text).
     */
    public static void draw(MsdfFont font, String line, float size, float ls,
                            float x, float textY, float scroll,
                            float windowLeft, float windowRight, float fadeZone,
                            float cursorPx, float swap, boolean swapIn,
                            int color, float dimFactor) {
        float swapAlpha = swapIn ? swap : 1.0F - swap;
        if (swapAlpha <= 0.01F || line.isEmpty()) {
            return;
        }
        float slide = (swapIn ? 1.0F - swap : swap) * 10.0F;
        float drawX = x - scroll + (swapIn ? slide : -slide);
        float colorAlpha = ((color >>> 24) & 0xFF) / 255.0F;
        int rgb = color & 0x00FFFFFF;

        float charX = 0.0F;
        int index = 0;
        int length = line.length();
        while (index < length) {
            int codePoint = line.codePointAt(index);
            int chars = Character.charCount(codePoint);
            String glyphText = line.substring(index, index + chars);
            float width = font.measureWidth(glyphText, size, ls);

            float sung;
            if (cursorPx < 0.0F) {
                sung = 1.0F;
            } else if (cursorPx <= charX) {
                sung = 0.0F;
            } else if (cursorPx >= charX + width) {
                sung = 1.0F;
            } else {
                sung = (cursorPx - charX) / Math.max(0.01F, width);
            }

            float center = drawX + charX + width * 0.5F;
            float edge = 1.0F;
            if (fadeZone > 0.0F) {
                edge = Math.clamp((center - windowLeft) / fadeZone, 0.0F, 1.0F)
                        * Math.clamp((windowRight - center) / fadeZone, 0.0F, 1.0F);
            }

            float alpha = swapAlpha * edge * colorAlpha
                    * (dimFactor + (1.0F - dimFactor) * sung);
            if (alpha > 0.01F) {
                Render2DUtil.text(drawX + charX, textY, size, glyphText)
                        .font(font)
                        .style(UiFontStyle.MEDIUM)
                        .color(rgb | (Math.round(alpha * 255.0F) << 24))
                        .draw();
            }
            charX += width;
            index += chars;
        }
    }
}
