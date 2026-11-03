package org.shizavisuals.feature.impl.visual;

import net.minecraft.world.entity.HumanoidArm;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.ButtonSetting;
import org.shizavisuals.feature.setting.NumberSetting;

public final class ViewModelFeature extends Feature {
    public final NumberSetting rightX = register(new NumberSetting("Right X", 0.0, -1.0, 1.0, 0.05, ""));
    public final NumberSetting rightY = register(new NumberSetting("Right Y", 0.0, -1.0, 1.0, 0.05, ""));
    public final NumberSetting rightZ = register(new NumberSetting("Right Z", 0.0, -1.0, 1.0, 0.05, ""));
    public final NumberSetting leftX = register(new NumberSetting("Left X", 0.0, -1.0, 1.0, 0.05, ""));
    public final NumberSetting leftY = register(new NumberSetting("Left Y", 0.0, -1.0, 1.0, 0.05, ""));
    public final NumberSetting leftZ = register(new NumberSetting("Left Z", 0.0, -1.0, 1.0, 0.05, ""));
    public final ButtonSetting resetPosition = register(new ButtonSetting(
            "Reset Position",
            "Reset",
            this::resetPosition
    ));

    public ViewModelFeature() {
        super("ViewModel", "Настраивает позиции рук от первого лица", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public float offsetX(HumanoidArm arm) {
        return value(arm == HumanoidArm.RIGHT ? rightX : leftX);
    }

    public float offsetY(HumanoidArm arm) {
        return value(arm == HumanoidArm.RIGHT ? rightY : leftY);
    }

    public float offsetZ(HumanoidArm arm) {
        return value(arm == HumanoidArm.RIGHT ? rightZ : leftZ);
    }

    private static float value(NumberSetting setting) {
        return setting.getValue().floatValue();
    }

    private void resetPosition() {
        this.rightX.reset();
        this.rightY.reset();
        this.rightZ.reset();
        this.leftX.reset();
        this.leftY.reset();
        this.leftZ.reset();
    }
}
