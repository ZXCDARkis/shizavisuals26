package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;

public final class SwingAnimationFeature extends Feature {
    public enum Style {
        SLICE,
        SPIRAL,
        THRUST,
        SPEAR
    }

    public final ModeSetting mode = register(new ModeSetting("Mode", "Slice", "Slice", "Spiral", "Thrust", "Spear"));
    public final NumberSetting speed = register(new NumberSetting("Speed", 6.0, 1.0, 20.0, 1.0, ""));

    public SwingAnimationFeature() {
        super("SwingAnimation", "Custom hand swing animation", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public Style style() {
        return Style.valueOf(mode.getValue().toUpperCase(java.util.Locale.ROOT));
    }

    public int swingDurationTicks() {
        return speed.getValue().intValue();
    }
}
