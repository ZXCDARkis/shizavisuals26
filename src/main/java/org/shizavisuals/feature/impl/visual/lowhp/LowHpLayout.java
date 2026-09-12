package org.shizavisuals.feature.impl.visual.lowhp;

/** Pure placement and value policy for the LowHP warning. Ported from the vesper reference. */
public final class LowHpLayout {
    public static final String ABOVE_HOTBAR = "Над хотбаром";
    public static final String BELOW_CROSSHAIR = "Под прицелом";
    public static final String ABOVE_CROSSHAIR = "Над прицелом";
    public static final String LEFT_OF_CROSSHAIR = "Слева от прицела";
    public static final String RIGHT_OF_CROSSHAIR = "Справа от прицела";

    private static final String[] POSITION_NAMES = {
            ABOVE_HOTBAR,
            BELOW_CROSSHAIR,
            ABOVE_CROSSHAIR,
            LEFT_OF_CROSSHAIR,
            RIGHT_OF_CROSSHAIR
    };
    private static final float CROSSHAIR_GAP = 14.0F;
    private static final float HOTBAR_CLEARANCE = 52.0F;
    private static final float VIEWPORT_MARGIN = 2.0F;

    private LowHpLayout() {
    }

    public static String[] positionNames() {
        return POSITION_NAMES.clone();
    }

    public static Point resolve(String position,
                                float screenWidth,
                                float screenHeight,
                                float visualWidth,
                                float visualHeight) {
        float safeScreenWidth = Math.max(0.0F, screenWidth);
        float safeScreenHeight = Math.max(0.0F, screenHeight);
        float safeWidth = Math.max(0.0F, visualWidth);
        float safeHeight = Math.max(0.0F, visualHeight);
        float centerX = safeScreenWidth * 0.5F;
        float centerY = safeScreenHeight * 0.5F;
        float x;
        float y;

        if (BELOW_CROSSHAIR.equalsIgnoreCase(position)) {
            x = centerX - safeWidth * 0.5F;
            y = centerY + CROSSHAIR_GAP;
        } else if (ABOVE_CROSSHAIR.equalsIgnoreCase(position)) {
            x = centerX - safeWidth * 0.5F;
            y = centerY - CROSSHAIR_GAP - safeHeight;
        } else if (LEFT_OF_CROSSHAIR.equalsIgnoreCase(position)) {
            x = centerX - CROSSHAIR_GAP - safeWidth;
            y = centerY - safeHeight * 0.5F;
        } else if (RIGHT_OF_CROSSHAIR.equalsIgnoreCase(position)) {
            x = centerX + CROSSHAIR_GAP;
            y = centerY - safeHeight * 0.5F;
        } else {
            // Unknown/old config values deliberately fall back to the safest preset.
            x = centerX - safeWidth * 0.5F;
            y = safeScreenHeight - HOTBAR_CLEARANCE - safeHeight;
        }

        return new Point(
                clamp(x, VIEWPORT_MARGIN, Math.max(VIEWPORT_MARGIN, safeScreenWidth - safeWidth - VIEWPORT_MARGIN)),
                clamp(y, VIEWPORT_MARGIN, Math.max(VIEWPORT_MARGIN, safeScreenHeight - safeHeight - VIEWPORT_MARGIN))
        );
    }

    /**
     * Exact Minecraft health-point threshold: 1 HP is half a vanilla heart,
     * 2 HP is one full heart, and 20 HP is ten full hearts.
     */
    public static boolean isLowHealth(float health, float thresholdHealth) {
        if (!Float.isFinite(health) || health <= 0.0F) {
            return false;
        }
        float threshold = clamp(thresholdHealth, 1.0F, 20.0F);
        return health <= threshold + 0.0001F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Point(float x, float y) {
    }
}
