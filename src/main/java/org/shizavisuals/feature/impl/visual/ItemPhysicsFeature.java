package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;

public final class ItemPhysicsFeature extends Feature {
    public ItemPhysicsFeature() {
        super("ItemPhysics", "Dropped items lie on the ground", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }
}
