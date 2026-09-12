package org.shizavisuals.menu.ui.rows;

public interface RowHost {

    void closeOtherRows(SettingRow except);

    int popupViewportMaxY();
}
