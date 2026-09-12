package org.shizavisuals.feature.setting;

import org.shizavisuals.utils.render.Theme;

public final class ColorMode {
    public static final String SYNC = "Sync";
    public static final String CUSTOM = "Custom";

    private ColorMode() {
    }

    public static ModeSetting setting() {
        return new ModeSetting(
                "Color Mode",
                SYNC,
                SYNC,
                CUSTOM
        );
    }

    public static boolean isCustom(ModeSetting mode) {
        return mode != null && mode.is(CUSTOM);
    }

    public static int resolve(ModeSetting mode, ColorSetting customColor) {
        return isCustom(mode) ? customColor.getValue() : Theme.getAccent();
    }
}
