package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.utils.text.StringUtil;

import java.util.List;

public final class MultiSelectComponent extends DropdownComponent {
    public MultiSelectComponent(MultiSelectSetting setting) {
        super(() -> selectedOptions(setting));
    }

    @Override
    protected String displayValue() {
        return value();
    }

    private static String selectedOptions(MultiSelectSetting setting) {
        List<String> selected = setting.getOptions().stream()
                .filter(setting::isSelected)
                .map(MenuText::option)
                .toList();
        if (selected.isEmpty()) {
            return MenuText.option("Nothing selected");
        }
        return StringUtil.joinLimited(selected, 2);
    }
}
