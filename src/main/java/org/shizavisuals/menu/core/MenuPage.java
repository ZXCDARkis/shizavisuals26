package org.shizavisuals.menu.core;

public enum MenuPage {
    NONE,
    SEARCH,
    SETTINGS,
    COSMETICS,
    FRIENDS,
    ACCOUNT_SWITCHER,
    CONFIGURATIONS;

    private static final MenuPage[] ACTION_PAGES = {SETTINGS, FRIENDS, ACCOUNT_SWITCHER, CONFIGURATIONS};

    public static MenuPage[] actionPages() {
        return ACTION_PAGES.clone();
    }

    public static int actionCount() {
        return ACTION_PAGES.length;
    }

    public static MenuPage actionAt(int index) {
        return ACTION_PAGES[Math.floorMod(index, ACTION_PAGES.length)];
    }

    public int actionIndex() {
        for (int index = 0; index < ACTION_PAGES.length; index++) {
            if (ACTION_PAGES[index] == this) {
                return index;
            }
        }
        return -1;
    }
}
