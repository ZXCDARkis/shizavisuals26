package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;

public final class TntEffectFeature extends Feature {
    public final NumberSetting lifetime = register(new NumberSetting(
            "Lifetime", 1800.0D, 400.0D, 6000.0D, 100.0D, "ms"
    ).configKey("render.tnt.lifetime"));
    public final NumberSetting radiusScale = register(new NumberSetting(
            "Radius Scale", 1.0D, 0.3D, 4.0D, 0.1D, "x"
    ).configKey("render.tnt.radiusScale"));
    public final BooleanSetting smoke = register(new BooleanSetting(
            "Smoke", true
    ).configKey("render.tnt.smoke"));
    public final NumberSetting smokeAmount = register(new NumberSetting(
            "Smoke Amount", 60.0D, 10.0D, 200.0D, 5.0D, ""
    ).configKey("render.tnt.smokeAmount"));
    public final BooleanSetting flash = register(new BooleanSetting(
            "Flash", true
    ).configKey("render.tnt.flash"));
    public final BooleanSetting wave = register(new BooleanSetting(
            "World Wave", true
    ).configKey("render.tnt.wave"));
    public final BooleanSetting screenWave = register(new BooleanSetting(
            "Screen Wave", true
    ).configKey("render.tnt.screenWave"));
    public final NumberSetting wavePower = register(new NumberSetting(
            "Wave Power", 26.0D, 0.0D, 60.0D, 1.0D, ""
    ).configKey("render.tnt.wavePower"));
    public final BooleanSetting shake = register(new BooleanSetting(
            "Shake", true
    ).configKey("render.tnt.shake"));
    public final NumberSetting shakePower = register(new NumberSetting(
            "Shake Power", 0.55D, 0.0D, 2.0D, 0.05D, ""
    ).configKey("render.tnt.shakePower"));
    public final ModeSetting colorMode = register(new ModeSetting(
            "Color",
            "Theme",
            "Theme", "Orange", "Red", "Blue", "Green", "White", "Purple"
    ).configKey("render.tnt.colorMode"));
    public final ModeSetting explosionFilter = register(new ModeSetting(
            "Explosions",
            "All",
            "All", "TNT Only"
    ).configKey("render.tnt.explosionFilter"));

    public TntEffectFeature() {
        super("TNT Effect", "Взрыв: дым, вспышка и ударная волна", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static TntEffectFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(TntEffectFeature.class);
    }

    public boolean acceptsExplosion(boolean windCharge) {
        return !"TNT Only".equals(this.explosionFilter.getValue()) || !windCharge;
    }
}