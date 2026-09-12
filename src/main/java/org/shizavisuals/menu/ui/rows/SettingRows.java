package org.shizavisuals.menu.ui.rows;

import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ButtonSetting;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.InputBindSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.feature.setting.Setting;
import org.shizavisuals.feature.setting.TextSetting;

public final class SettingRows {
    private SettingRows() {
    }

    public static SettingRow create(String featureName, Setting<?> setting) {
        SettingRow row = switch (setting) {
            case BooleanSetting booleanSetting -> new ToggleRow(booleanSetting);
            case ButtonSetting buttonSetting -> new ButtonRow(buttonSetting);
            case NumberSetting numberSetting -> new SliderRow(numberSetting);
            case ModeSetting modeSetting -> new DropdownRow(modeSetting);
            case MultiSelectSetting multiSelectSetting -> new DropdownRow(multiSelectSetting);
            case ColorSetting colorSetting -> new ColorRow(colorSetting);
            case TextSetting textSetting -> new TextRow(textSetting);
            case InputBindSetting inputBindSetting -> new BindChipRow(inputBindSetting);
            default -> null;
        };
        return row == null ? null : row.context(featureName);
    }
}
