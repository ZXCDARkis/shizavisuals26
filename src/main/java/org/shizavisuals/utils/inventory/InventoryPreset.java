package org.shizavisuals.utils.inventory;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** A named snapshot of the managed player inventory slots. */
public final class InventoryPreset {
    public String name;
    public final Map<Integer, InvItem> items = new LinkedHashMap<>();

    public InventoryPreset(String name) {
        this.name = name;
    }

    public int size() {
        return items.size();
    }

    public static InventoryPreset capture(String name, LocalPlayer player) {
        InventoryPreset preset = new InventoryPreset(name);
        if (player == null) {
            return preset;
        }
        var menu = player.inventoryMenu;
        for (int slot : SlotLayout.managedOrder()) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack stack = menu.getSlot(slot).getItem();
            InvItem item = InvItem.of(stack);
            if (item != null) {
                preset.items.put(slot, item);
            }
        }
        return preset;
    }
}
