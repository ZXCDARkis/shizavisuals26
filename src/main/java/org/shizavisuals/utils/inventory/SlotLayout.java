package org.shizavisuals.utils.inventory;

/** Player inventory slot layout (armor 5-8, main 9-35, hotbar 36-44, offhand 45). */
public final class SlotLayout {
    private SlotLayout() {
    }

    public static final int ARMOR_START = 5;
    public static final int MAIN_START = 9;
    public static final int HOTBAR_START = 36;
    public static final int OFFHAND = 45;

    public static final int FIRST_MANAGED = 5;
    public static final int LAST_MANAGED = 45;

    /** Priority order: hotbar first, then main, then armor, then offhand. */
    public static int[] managedOrder() {
        int[] ids = new int[41];
        int k = 0;
        for (int i = HOTBAR_START; i <= 44; i++) ids[k++] = i;
        for (int i = MAIN_START; i <= 35; i++) ids[k++] = i;
        for (int i = ARMOR_START; i <= 8; i++) ids[k++] = i;
        ids[k++] = OFFHAND;
        return ids;
    }

    public static boolean isManaged(int slot) {
        return slot >= FIRST_MANAGED && slot <= LAST_MANAGED;
    }

    public static boolean isArmor(int slot) {
        return slot >= ARMOR_START && slot <= 8;
    }

    public static boolean isHotbar(int slot) {
        return slot >= HOTBAR_START && slot <= 44;
    }
}
