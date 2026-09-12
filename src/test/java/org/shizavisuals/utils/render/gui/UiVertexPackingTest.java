package org.shizavisuals.utils.render.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the Java/GLSL packing contract: the encoders in
 * {@link UiVertexPacking} must round-trip through the GLSL decoders in
 * {@code assets/shizavisuals/shaders/include/ui_common.glsl}. The decoders below are
 * line-for-line Java ports of {@code ui_unpackZ} / {@code ui_unpackDual12Raw}
 * using 32-bit float math, so any drift between UiVertexPacking and the
 * shader library fails here instead of as corrupted rendering.
 */
class UiVertexPackingTest {

    /** Java port of ui_unpackZ: returns {mode, value px, alpha 0..1}. */
    private static float[] glslUnpackZ(float packed) {
        float units = (float) Math.floor(packed / 256.0f);
        float alpha = packed - units * 256.0f;
        float mode = units < 4096.0f ? 0.0f : 1.0f; // step(4096.0, units)
        units -= mode * 4096.0f;
        return new float[]{mode, units / UiVertexPacking.RADIUS_SCALE, alpha / 255.0f};
    }

    /** Java port of ui_unpackDual12Raw: returns {hi, lo} raw 12-bit values. */
    private static float[] glslUnpackDual12Raw(float packed) {
        float hi = (float) Math.floor(packed / 4096.0f);
        float lo = packed - hi * 4096.0f;
        return new float[]{hi, lo};
    }

    @Test
    void packZRoundTripsThroughGlslDecodeForAllUnitsAndModes() {
        int[] alphas = {0, 1, 127, 128, 254, 255};
        for (int units = 0; units <= 4095; units++) {
            float px = units / UiVertexPacking.RADIUS_SCALE;
            for (int alpha : alphas) {
                for (int modeBit = 0; modeBit <= 1; modeBit++) {
                    boolean mode = modeBit == 1;
                    float packed = UiVertexPacking.packZ(px, alpha, mode);
                    float[] decoded = glslUnpackZ(packed);

                    if (decoded[0] != modeBit) {
                        fail("mode flag lost for units=" + units + " alpha=" + alpha + " mode=" + mode);
                    }
                    if (decoded[1] != px) {
                        fail("value px mismatch for units=" + units + ": expected " + px + " got " + decoded[1]);
                    }
                    if (Math.round(decoded[2] * 255.0f) != alpha) {
                        fail("alpha byte mismatch for units=" + units + " alpha=" + alpha
                                + ": decoded " + decoded[2]);
                    }
                }
            }
        }
    }

    @Test
    void packZClampsValueAndAlpha() {
        // Value units clamp to 0..4095 (12 bits); the mode flag lives at bit 12.
        assertEquals(4095.0f / UiVertexPacking.RADIUS_SCALE, glslUnpackZ(UiVertexPacking.packZ(1.0e9f, 0, false))[1]);
        assertEquals(0.0f, glslUnpackZ(UiVertexPacking.packZ(-5.0f, 0, false))[1]);

        // An overflowing value must never bleed into the mode flag.
        assertEquals(0.0f, glslUnpackZ(UiVertexPacking.packZ(1.0e9f, 0, false))[0]);
        assertEquals(1.0f, glslUnpackZ(UiVertexPacking.packZ(1.0e9f, 0, true))[0]);

        // Alpha clamps to a byte.
        assertEquals(255, Math.round(glslUnpackZ(UiVertexPacking.packZ(1.0f, 999, false))[2] * 255.0f));
        assertEquals(0, Math.round(glslUnpackZ(UiVertexPacking.packZ(1.0f, -7, true))[2] * 255.0f));
    }

    @Test
    void packDual12IsExactForAllTwelveBitPairs() {
        for (int hi = 0; hi <= 4095; hi++) {
            float hiPx = hi / UiVertexPacking.RADIUS_SCALE;
            for (int lo = 0; lo <= 4095; lo++) {
                float packed = UiVertexPacking.packDual12(hiPx, lo / UiVertexPacking.RADIUS_SCALE);
                float decodedHi = (float) Math.floor(packed / 4096.0f);
                float decodedLo = packed - decodedHi * 4096.0f;
                if ((int) decodedHi != hi || (int) decodedLo != lo) {
                    fail("dual12 mismatch: hi=" + hi + " lo=" + lo
                            + " decoded hi=" + decodedHi + " lo=" + decodedLo);
                }
            }
        }
    }

    @Test
    void packDual12ClampsToTwelveBits() {
        float packed = UiVertexPacking.packDual12(1.0e9f, -3.0f);
        float[] decoded = glslUnpackDual12Raw(packed);
        assertEquals(4095.0f, decoded[0]);
        assertEquals(0.0f, decoded[1]);

        float packedRaw = UiVertexPacking.packDual12Raw(9999, -1);
        float[] decodedRaw = glslUnpackDual12Raw(packedRaw);
        assertEquals(4095.0f, decodedRaw[0]);
        assertEquals(0.0f, decodedRaw[1]);
    }

    @Test
    void packSizeClampsAndScales() {
        assertEquals(0, UiVertexPacking.packSize(-1.0f));
        assertEquals(32767, UiVertexPacking.packSize(1.0e9f));
        assertEquals(16, UiVertexPacking.packSize(2.0f), "sizes use 1/8 px fixed-point");

        assertEquals(-32768, UiVertexPacking.packSignedSize(-1.0e9f));
        assertEquals(32767, UiVertexPacking.packSignedSize(1.0e9f));
        assertEquals(-16, UiVertexPacking.packSignedSize(-2.0f));
    }

    @Test
    void packRadiusClampsAndScales() {
        assertEquals(0, UiVertexPacking.packRadius(-1.0f));
        assertEquals(32767, UiVertexPacking.packRadius(1.0e9f));
        assertEquals(32, UiVertexPacking.packRadius(2.0f), "radii use 1/16 px fixed-point");
    }

    @Test
    void packU8PairClampsAndPacksLowHigh() {
        assertEquals(0x3412, UiVertexPacking.packU8Pair(0x12, 0x34));
        assertEquals(255, UiVertexPacking.packU8Pair(300, -5), "both bytes clamp to 0..255");
    }
}
