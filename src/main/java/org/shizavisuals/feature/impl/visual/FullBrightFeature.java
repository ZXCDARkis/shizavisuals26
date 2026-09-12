package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;

public final class FullBrightFeature extends Feature {
    private static final double FULL_BRIGHT_GAMMA = 16.0D;

    private double savedGamma = 1.0D;
    private boolean hasSavedGamma;

    public FullBrightFeature() {
        super("FullBright", "Максимальная яркость, убирает темноту", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static FullBrightFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(FullBrightFeature.class);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }
        try {
            this.savedGamma = mc.options.gamma().get();
            this.hasSavedGamma = true;
            mc.options.gamma().set(FULL_BRIGHT_GAMMA);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDisable() {
        if (!this.hasSavedGamma) {
            return;
        }
        this.hasSavedGamma = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) {
            return;
        }
        try {
            mc.options.gamma().set(this.savedGamma);
        } catch (Throwable ignored) {
        }
    }
}
