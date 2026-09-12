package org.shizavisuals.utils;

import java.awt.Color;
import java.util.Locale;
import java.util.Objects;

public final class ColorUtil {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public static final int TRANSPARENT = 0x00000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;

    private ColorUtil() {
    }

    public static int pack(int red, int green, int blue, int alpha) {
        return (clamp255(alpha) << 24)
                | (clamp255(red) << 16)
                | (clamp255(green) << 8)
                | clamp255(blue);
    }

    public static int pack(Color color) {
        Objects.requireNonNull(color, "color");
        return pack(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    public static int rgb(int red, int green, int blue) {
        return pack(red, green, blue, 255);
    }

    public static int rgba(int red, int green, int blue, int alpha) {
        return pack(red, green, blue, alpha);
    }

    public static int rgba(float red, float green, float blue, float alpha) {
        return pack(toChannel(red), toChannel(green), toChannel(blue), toChannel(alpha));
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int withAlpha(int color, int alpha) {
        return (clamp255(alpha) << 24) | (color & 0x00FFFFFF);
    }

    public static int withAlpha(int color, float alpha) {
        return withAlpha(color, toChannel(alpha));
    }

    public static int multiplyAlpha(int color, float factor) {
        return withAlpha(color, Math.round(alpha(color) * clamp01(factor)));
    }

    public static int applyAlpha(int color, float alpha) {
        return withAlpha(color, alpha);
    }

    public static int fade(int speed, int index, int first, int second) {
        int angle = (int) ((System.currentTimeMillis() / Math.max(1, speed) + index) % 360);
        angle = angle >= 180 ? 360 - angle : angle;
        return lerp(first, second, angle / 180.0F);
    }

    public static int multiplyRgb(int color, float factor) {
        float clamped = factor < 0f ? 0f : factor;
        return pack(
                Math.round(red(color) * clamped),
                Math.round(green(color) * clamped),
                Math.round(blue(color) * clamped),
                alpha(color)
        );
    }

    public static int lerp(int from, int to, float delta) {
        float t = clamp01(delta);
        return pack(
                Math.round(red(from) + (red(to) - red(from)) * t),
                Math.round(green(from) + (green(to) - green(from)) * t),
                Math.round(blue(from) + (blue(to) - blue(from)) * t),
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * t)
        );
    }

    public static float[] hsv(int argb) {
        float r = red(argb) / 255f;
        float g = green(argb) / 255f;
        float b = blue(argb) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float hue;
        if (delta == 0f) {
            hue = 0f;
        } else if (max == r) {
            hue = ((g - b) / delta) % 6f;
        } else if (max == g) {
            hue = ((b - r) / delta) + 2f;
        } else {
            hue = ((r - g) / delta) + 4f;
        }

        hue /= 6f;
        if (hue < 0f) {
            hue += 1f;
        }

        float saturation = max == 0f ? 0f : delta / max;
        return new float[]{hue, saturation, max};
    }

    public static int fromHsv(float hue, float saturation, float brightness, int alpha) {
        float h = hue - (float) Math.floor(hue);
        float s = clamp01(saturation);
        float v = clamp01(brightness);

        if (s == 0f) {
            int gray = toChannel(v);
            return pack(gray, gray, gray, alpha);
        }

        float scaled = h * 6f;
        int sector = (int) Math.floor(scaled);
        float fraction = scaled - sector;
        float p = v * (1f - s);
        float q = v * (1f - s * fraction);
        float t = v * (1f - s * (1f - fraction));

        return switch (sector % 6) {
            case 0 -> pack(toChannel(v), toChannel(t), toChannel(p), alpha);
            case 1 -> pack(toChannel(q), toChannel(v), toChannel(p), alpha);
            case 2 -> pack(toChannel(p), toChannel(v), toChannel(t), alpha);
            case 3 -> pack(toChannel(p), toChannel(q), toChannel(v), alpha);
            case 4 -> pack(toChannel(t), toChannel(p), toChannel(v), alpha);
            default -> pack(toChannel(v), toChannel(p), toChannel(q), alpha);
        };
    }

    public static int hue(float hue) {
        return fromHsv(hue, 1f, 1f, 255);
    }

    public static int fromHex(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Hex string cannot be null");
        }

        String value = normalize(hex);
        int length = value.length();
        if (length != 6 && length != 8) {
            throw new IllegalArgumentException("Expected 6 or 8 hex digits, got " + length);
        }

        long parsed = parseHexDigits(value, 0, length);

        if (length == 6) {
            return pack((int) (parsed >> 16) & 0xFF, (int) (parsed >> 8) & 0xFF, (int) parsed & 0xFF, 255);
        }
        return pack(
                (int) (parsed >> 24) & 0xFF,
                (int) (parsed >> 16) & 0xFF,
                (int) (parsed >> 8) & 0xFF,
                (int) parsed & 0xFF
        );
    }

    public static Integer parse(String input) {
        if (input == null) {
            return null;
        }

        String value = normalize(input);
        if (value.isEmpty()) {
            return null;
        }

        Integer named = named(value);
        if (named != null) {
            return named;
        }

        try {
            return switch (value.length()) {
                case 3 -> pack(
                        hexDigit(value.charAt(0)) * 17,
                        hexDigit(value.charAt(1)) * 17,
                        hexDigit(value.charAt(2)) * 17,
                        255
                );
                case 4 -> pack(
                        hexDigit(value.charAt(0)) * 17,
                        hexDigit(value.charAt(1)) * 17,
                        hexDigit(value.charAt(2)) * 17,
                        hexDigit(value.charAt(3)) * 17
                );
                case 6, 8 -> fromHex(value);
                default -> null;
            };
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String toHex(int argb) {
        char[] out = new char[9];
        out[0] = '#';
        writeHexByte(out, 1, red(argb));
        writeHexByte(out, 3, green(argb));
        writeHexByte(out, 5, blue(argb));
        writeHexByte(out, 7, alpha(argb));
        return new String(out);
    }

    public static boolean isColorChar(char c) {
        return c == '#' || Character.digit(c, 16) >= 0;
    }

    private static String normalize(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static void writeHexByte(char[] out, int offset, int value) {
        out[offset] = HEX[(value >>> 4) & 0xF];
        out[offset + 1] = HEX[value & 0xF];
    }

    private static long parseHexDigits(String value, int start, int end) {
        long result = 0L;
        for (int i = start; i < end; i++) {
            result = (result << 4) | hexDigit(value.charAt(i));
        }
        return result;
    }

    private static int hexDigit(char c) {
        int digit = Character.digit(c, 16);
        if (digit < 0) {
            throw new IllegalArgumentException("Invalid hex digit: " + c);
        }
        return digit;
    }

    private static Integer named(String value) {
        return switch (value) {
            case "RED" -> RED;
            case "GREEN", "LIME" -> GREEN;
            case "BLUE" -> BLUE;
            case "WHITE" -> WHITE;
            case "BLACK" -> BLACK;
            case "YELLOW" -> rgb(255, 255, 0);
            case "CYAN", "AQUA" -> rgb(0, 255, 255);
            case "MAGENTA", "FUCHSIA", "PINK" -> rgb(255, 0, 255);
            case "PURPLE" -> rgb(128, 0, 128);
            case "ORANGE" -> rgb(255, 153, 101);
            case "GRAY", "GREY" -> rgb(128, 128, 128);
            case "TRANSPARENT" -> TRANSPARENT;
            default -> null;
        };
    }

    private static int toChannel(float value) {
        return clamp255(Math.round(clamp01(value) * 255f));
    }

    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        return Math.min(value, 1f);
    }

    private static int clamp255(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 255);
    }
}
