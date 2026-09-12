package org.shizavisuals.utils.math;

public final class NoiseUtil {
    private static final int[] P = new int[512];
    private static final int[] PERMUTATION = {
        151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,
        8,99,37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,
        35,11,32,57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,74,165,71,
        134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,
        55,46,245,40,244,102,143,54,65,25,63,161,1,216,80,73,209,76,132,187,208,89,
        18,169,200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,52,217,226,
        250,124,123,5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,
        189,28,42,223,183,170,213,119,248,152,2,44,145,31,179,228,167,215,221,201,157,
        49,192,239,97,30,181,141,180,193,39,12,84,115,153,163,85,214,129,24,254,43,150,
        138,19,106,78,13,67,112,61,66,114,249,14,236,239,79,178,241,34,45,218,63,93,
        110,243,101,154,128,155,144,145,152,246,127,156,22,251,108,176,121,235,162,195,
        242,107,172,113,253,224,199,4,116,164,222,185,184,179
    };

    static {
        for (int i = 0; i < 256; i++) {
            P[256 + i] = P[i] = PERMUTATION[i % PERMUTATION.length];
        }
    }

    private NoiseUtil() {}

    public static float perlin1D(double x) {
        return perlin2D(x, 0.5D);
    }

    public static float perlin2D(double x, double y) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;

        x -= Math.floor(x);
        y -= Math.floor(y);

        double u = fade(x);
        double v = fade(y);

        int A = P[X] + Y;
        int B = P[X + 1] + Y;

        return (float) lerp(v, lerp(u, grad(P[A], x, y), grad(P[B], x - 1, y)),
                lerp(u, grad(P[A + 1], x, y - 1), grad(P[B + 1], x - 1, y - 1)));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        int h = hash & 7;
        double u = h < 4 ? x : y;
        double v = h < 4 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
