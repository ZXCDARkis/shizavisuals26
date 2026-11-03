package org.shizavisuals.feature.impl.misc;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.TextSetting;

public final class NameProtectFeature extends Feature {
    public final TextSetting name = register(new TextSetting("Name", "ShizaVisualsUser", 32));

    public NameProtectFeature() {
        super("NameProtect", "Защищает ваш ник в интерфейсе клиента", FeatureCategory.MISC, BindSetting.UNBOUND);
    }
}
