package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;

public final class ItemPhysicsFeature extends Feature {
    public ItemPhysicsFeature() {
        super("ItemPhysics", "Выброшенные предметы лежат на земле", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }
}
