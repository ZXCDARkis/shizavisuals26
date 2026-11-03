package org.shizavisuals.hud.island;

import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;

public final class IslandKaraoke {

    private static ShapedLine cached;

    private IslandKaraoke() {
    }

    /**
     * Shaped representation of one lyric line, reused across frames so karaoke
     * drawing and measuring stay allocation-free while the line is unchanged.
     */
    private static final class ShapedLine {
        private final MsdfFont font;
        private final String text;
        private final float size;
        private final float letterSpacing;
        private final MsdfFont.Paragraph paragraph;
        private final MsdfFont.Line line;
        private final String[] glyphTexts;
        private final float[] penX;
        private final float[] glyphWidths;
        private final float totalWidth;

        private ShapedLine(MsdfFont font, String text, float size, float letterSpacing) {
            this.font = font;
            this.text = text;
            this.size = size;
            this.letterSpacing = letterSpacing;
            this.paragraph = font.shape(text);
            this.line = this.paragraph.lines().get(0);
            int count = this.line.glyphCount();
            this.glyphTexts = new String[count];
            this.penX = new float[count + 1];
            this.glyphWidths = new float[count];
            int at = 0;
            for (int i = 0; i < count; i++) {
                int codePoint = this.line.codePointAt(i);
                int chars = Character.charCount(codePoint);
                this.glyphTexts[i] = text.substring(at, Math.min(text.length(), at + chars));
                this.glyphWidths[i] = this.line.glyphAt(i).advanceEm() * size;
                at += chars;
            }
            for (int i = 0; i < count; i++) {
                this.penX[i] = this.line.penX(i, size, letterSpacing);
            }
            this.penX[count] = this.line.width(size, letterSpacing);
            this.totalWidth = this.penX[count];
        }

        private float width(int index) {
            return this.glyphWidths[index];
        }
    }

    private static ShapedLine shape(MsdfFont font, String line, float size, float ls) {
        ShapedLine current = cached;
        if (current != null
                && current.font == font
                && current.size == size
                && current.letterSpacing == ls
                && current.text.equals(line)) {
            return current;
        }
        current = new ShapedLine(font, line, size, ls);
        cached = current;
        return current;
    }

    public static float[][] wordBounds(MsdfFont font, String line, float size, float ls) {
        ShapedLine shaped = shape(font, line, size, ls);
        int count = shaped.glyphTexts.length;
        int words = 1;
        for (int i = 0; i < count; i++) {
            if (shaped.glyphTexts[i].charAt(0) == ' ') {
                words++;
            }
        }
        float[][] bounds = new float[words][2];
        int word = 0;
        float start = 0.0F;
        for (int i = 0; i < count; i++) {
            if (shaped.glyphTexts[i].charAt(0) == ' ') {
                bounds[word][0] = start;
                bounds[word][1] = shaped.penX[i];
                word++;
                start = shaped.penX[i + 1];
            }
        }
        bounds[word][0] = start;
        bounds[word][1] = shaped.totalWidth;
        return bounds;
    }

    public static int charCountForWidth(MsdfFont font, String line, float size, float ls, float maxWidth) {
        if (maxWidth <= 0.0F) {
            return 0;
        }
        ShapedLine shaped = shape(font, line, size, ls);
        int count = shaped.glyphTexts.length;
        if (count == 0) {
            return 0;
        }
        if (shaped.totalWidth <= maxWidth) {
            return line.length();
        }
        int low = 0;
        int high = count;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (shaped.penX[mid] > maxWidth) {
                high = mid - 1;
            } else {
                low = mid;
            }
        }
        int chars = 0;
        for (int i = 0; i < low; i++) {
            chars += shaped.glyphTexts[i].length();
        }
        return chars;
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
        ShapedLine shaped = shape(font, line, size, ls);
        int count = shaped.glyphTexts.length;
        if (count == 0) {
            return;
        }
        float slide = (swapIn ? 1.0F - swap : swap) * 10.0F;
        float drawX = x - scroll + (swapIn ? slide : -slide);
        float colorAlpha = ((color >>> 24) & 0xFF) / 255.0F;
        int rgb = color & 0x00FFFFFF;

        for (int index = 0; index < count; index++) {
            String glyphText = shaped.glyphTexts[index];
            float charX = shaped.penX[index];
            float width = shaped.width(index);

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
        }
    }
}
