package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;

public final class MotionBlurFeature extends Feature {
    public final NumberSetting strength = register(new NumberSetting(
            "Strength",
            0.55D,
            0.0D,
            2.0D,
            0.05D,
            "x"
    ).configKey("render.motionblur.strength"));
    public final ModeSetting quality = register(new ModeSetting(
            "Quality",
            "High",
            "Low", "Medium", "High", "Ultra"
    ).configKey("render.motionblur.quality"));
    public final BooleanSetting refreshScaling = register(new BooleanSetting(
            "Refresh Scaling",
            true
    ).configKey("render.motionblur.refreshScaling"));
    public final NumberSetting handThreshold = register(new NumberSetting(
            "Hand Threshold",
            0.56D,
            0.0D,
            1.0D,
            0.01D,
            ""
    ).configKey("render.motionblur.handThreshold"));

    public MotionBlurFeature() {
        super("MotionBlur", "Смазывание картинки в движении (репроджекция по глубине)", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static MotionBlurFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(MotionBlurFeature.class);
    }
}
