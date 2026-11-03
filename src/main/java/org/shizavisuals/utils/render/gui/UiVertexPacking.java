package org.shizavisuals.utils.render.gui;

import org.shizavisuals.utils.math.MathUtil;

final class UiVertexPacking {

    static final float SIZE_SCALE = 8.0F;

    static final float RADIUS_SCALE = 16.0F;

    static final float MAX_DUAL_RADIUS = 4095.0F / RADIUS_SCALE;
    private static final int MODE_FLAG = 1 << 12;

    private UiVertexPacking() {
    }

    static int packSize(float px) {
        return MathUtil.clamp(Math.round(px * SIZE_SCALE), 0, 32767);
    }

    static int packSignedSize(float px) {
        return MathUtil.clamp(Math.round(px * SIZE_SCALE), -32768, 32767);
    }

    static int packRadius(float px) {
        return MathUtil.clamp(Math.round(px * RADIUS_SCALE), 0, 32767);
    }

    static int packU8Pair(int lo, int hi) {
        return MathUtil.clampByte(lo) | (MathUtil.clampByte(hi) << 8);
    }

    static float packZ(float valuePx, int alpha8, boolean mode) {
        int units = MathUtil.clamp(Math.round(valuePx * RADIUS_SCALE), 0, MODE_FLAG - 1);
        if (mode) {
            units |= MODE_FLAG;
        }
        return units * 256.0F + MathUtil.clampByte(alpha8);
    }

    static float packDual12(float hiPx, float loPx) {
        int hi = MathUtil.clamp(Math.round(hiPx * RADIUS_SCALE), 0, 4095);
        int lo = MathUtil.clamp(Math.round(loPx * RADIUS_SCALE), 0, 4095);
        return hi * 4096.0F + lo;
    }

    static float packDual12Raw(int hi, int lo) {
        return MathUtil.clamp(hi, 0, 4095) * 4096.0F + MathUtil.clamp(lo, 0, 4095);
    }

    static int packDualHi(float px) {
        return MathUtil.clamp(Math.round(px * RADIUS_SCALE), 0, 4095);
    }

    static int packDualLo(float px) {
        return MathUtil.clamp(Math.round(px * RADIUS_SCALE), 0, 4095);
    }

    static float snormChannel(int channel) {
        return channel / 127.5F - 1.0F;
    }
}
