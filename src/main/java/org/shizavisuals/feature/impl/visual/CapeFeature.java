package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import java.util.Locale;

public final class CapeFeature extends Feature {

    public enum CapeStyle {
        APPICON("appicon.png"),
        ASCII("ascii.png"),
        BARCODE("barcode.png"),
        GLASS("glass.png"),
        MINIMALPATTERN("minimalpatern.png"),
        MOTTO("motto.png"),
        VANDAL("vandal.png");

        private final String fileName;

        CapeStyle(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return this.fileName;
        }
    }

    public final ModeSetting mode = register(new ModeSetting(
            "Style",
            "Glass",
            "AppIcon", "ASCII", "Barcode", "Glass", "MinimalPattern", "Motto", "Vandal"
    ));

    public CapeFeature() {
        super("Capes", "Свои косметические плащи, видимые на вас", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public CapeStyle currentStyle() {
        try {
            return CapeStyle.valueOf(mode.getValue().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CapeStyle.GLASS;
        }
    }
}
