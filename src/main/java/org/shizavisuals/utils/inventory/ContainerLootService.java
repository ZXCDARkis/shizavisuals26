package org.shizavisuals.utils.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;

public final class ContainerLootService {
    private static final int PLAYER_INVENTORY_SLOTS = 36;

    private ContainerLootService() {
    }

    public static int containerSlotCount(AbstractContainerMenu menu) {
        return Math.max(0, menu.slots.size() - PLAYER_INVENTORY_SLOTS);
    }

    public static int findFirst(AbstractContainerMenu menu,
                                Predicate<ItemStack> predicate) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(predicate, "predicate");
        int slots = containerSlotCount(menu);
        for (int index = 0; index < slots; index++) {
            Slot slot = menu.getSlot(index);
            if (slot.hasItem() && predicate.test(slot.getItem())) {
                return index;
            }
        }
        return -1;
    }

    public static boolean quickMoveFirst(AbstractContainerMenu menu,
                                         Predicate<ItemStack> predicate) {
        int slot = findFirst(menu, predicate);
        if (slot < 0) {
            return false;
        }
        InventoryUtil.quickMoveSlot(slot);
        return true;
    }
}
