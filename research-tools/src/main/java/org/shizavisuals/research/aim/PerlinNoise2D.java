package org.shizavisuals.research.aim;

import java.util.SplittableRandom;

/**
 * Детерминированный двумерный шум Перлина без внешних зависимостей.
 */
public final class PerlinNoise2D {
    private final int[] permutation = new int[512];

    public PerlinNoise2D(long seed) {
        int[] values = new int[256];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }

        SplittableRandom random = new SplittableRandom(seed);
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temporary = values[i];
            values[i] = values[j];
            values[j] = temporary;
        }

        for (int i = 0; i < permutation.length; i++) {
            permutation[i] = values[i & 255];
        }
    }

    /**
     * Возвращает плавное значение приблизительно в диапазоне [-1; 1].
     */
    public double sample(double x, double y) {
        int cellX = fastFloor(x) & 255;
        int cellY = fastFloor(y) & 255;
        double localX = x - Math.floor(x);
        double localY = y - Math.floor(y);

        double u = fade(localX);
        double v = fade(localY);

        int bottomLeft = permutation[permutation[cellX] + cellY];
        int topLeft = permutation[permutation[cellX] + cellY + 1];
        int bottomRight = permutation[permutation[cellX + 1] + cellY];
        int topRight = permutation[permutation[cellX + 1] + cellY + 1];

        double bottom = lerp(
                gradient(bottomLeft, localX, localY),
                gradient(bottomRight, localX - 1.0, localY),
                u
        );
        double top = lerp(
                gradient(topLeft, localX, localY - 1.0),
                gradient(topRight, localX - 1.0, localY - 1.0),
                u
        );
        return lerp(bottom, top, v);
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double from, double to, double amount) {
        return from + amount * (to - from);
    }

    private static double gradient(int hash, double x, double y) {
        return switch (hash & 7) {
            case 0 -> x + y;
            case 1 -> -x + y;
            case 2 -> x - y;
            case 3 -> -x - y;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> y;
            default -> -y;
        };
    }
}
