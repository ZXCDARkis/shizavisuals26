package org.shizavisuals.utils.math;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MathUtil {
    public int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    public double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    public int clampByte(int value) {
        return clamp(value, 0, 255);
    }

    public float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }

    public double lerp(double from, double to, double delta) {
        return from + (to - from) * delta;
    }

    public float easeOutCubic(float value) {
        float clamped = clamp01(value);
        return 1.0F - (float) Math.pow(1.0F - clamped, 3.0F);
    }

    public double easeOutCubic(double value) {
        double clamped = clamp01(value);
        return 1.0D - Math.pow(1.0D - clamped, 3.0D);
    }
}
