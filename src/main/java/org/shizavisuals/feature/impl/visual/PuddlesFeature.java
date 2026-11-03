package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.NumberSetting;

public final class PuddlesFeature extends Feature {
    public final BooleanSetting enabled = register(new BooleanSetting(
            "Puddles", true
    ).configKey("render.puddles.enabled"));
    public final NumberSetting coverage = register(new NumberSetting(
            "Puddle Coverage", 0.65D, 0.1D, 1.0D, 0.05D, ""
    ).configKey("render.puddles.coverage").visibleWhen(() -> this.enabled.getValue()));
    public final NumberSetting waveSpeed = register(new NumberSetting(
            "Wave Speed", 1.2D, 0.1D, 3.0D, 0.1D, "x"
    ).configKey("render.puddles.wavespeed").visibleWhen(() -> this.enabled.getValue()));
    public final NumberSetting waveStrength = register(new NumberSetting(
            "Wave Ripples", 0.6D, 0.0D, 2.0D, 0.05D, "x"
    ).configKey("render.puddles.wavestrength").visibleWhen(() -> this.enabled.getValue()));
    public final NumberSetting reflectivity = register(new NumberSetting(
            "Reflectivity", 0.9D, 0.1D, 1.0D, 0.05D, ""
    ).configKey("render.puddles.reflectivity").visibleWhen(() -> this.enabled.getValue()));
    public final NumberSetting scale = register(new NumberSetting(
            "Puddle Scale", 0.25D, 0.05D, 0.8D, 0.05D, ""
    ).configKey("render.puddles.scale").visibleWhen(() -> this.enabled.getValue()));

    public PuddlesFeature() {
        super(
                "Puddles",
                "Wet floor with screen-space reflections",
                FeatureCategory.VISUAL,
                0
        );
    }

    public static PuddlesFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(PuddlesFeature.class);
    }

    public boolean usesPuddles() {
        return this.enabled.getValue();
    }
}