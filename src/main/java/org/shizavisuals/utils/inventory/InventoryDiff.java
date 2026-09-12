package org.shizavisuals.utils.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares the live inventory against a preset:
 * wrongSlots — slots whose contents don't match the preset;
 * arrangeableSlots — wrong slots whose item exists elsewhere (swappable);
 * missing — items the preset wants that are nowhere in the inventory.
 */
public final class InventoryDiff {
    public final List<Integer> wrongSlots = new ArrayList<>();
    public final Set<Integer> arrangeableSlots = new HashSet<>();
    public final List<InvItem> missing = new ArrayList<>();
    public boolean assembled;

    public static InventoryDiff compute(AbstractContainerMenu menu, InventoryPreset preset) {
        InventoryDiff d = new InventoryDiff();
        if (menu == null || preset == null) {
            return d;
        }

        for (Map.Entry<Integer, InvItem> e : preset.items.entrySet()) {
            int slot = e.getKey();
            if (slot >= menu.slots.size()) {
                continue;
            }
            if (!e.getValue().matches(menu.getSlot(slot).getItem())) {
                d.wrongSlots.add(slot);
            }
        }

        // Which misplaced items exist and can fill a wrong slot?
        Map<String, Integer> movable = new LinkedHashMap<>();
        for (int slot : SlotLayout.managedOrder()) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack cur = menu.getSlot(slot).getItem();
            if (cur.isEmpty()) {
                continue;
            }
            InvItem deserved = preset.items.get(slot);
            if (deserved == null || !deserved.matches(cur)) {
                movable.merge(InventoryDiff.identityKey(cur), 1, Integer::sum);
            }
        }
        for (int slot : d.wrongSlots) {
            InvItem want = preset.items.get(slot);
            if (want == null) {
                continue;
            }
            String k = want.identityKey();
            if (movable.getOrDefault(k, 0) > 0) {
                d.arrangeableSlots.add(slot);
                movable.merge(k, -1, Integer::sum);
            }
        }

        // Missing = preset totals minus inventory totals per identity.
        Map<String, Integer> wantTotals = new LinkedHashMap<>();
        Map<String, InvItem> sample = new LinkedHashMap<>();
        for (InvItem it : preset.items.values()) {
            String k = it.identityKey();
            wantTotals.merge(k, it.count, Integer::sum);
            sample.putIfAbsent(k, it);
        }
        Map<String, Integer> haveTotals = new LinkedHashMap<>();
        for (int slot : SlotLayout.managedOrder()) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            ItemStack st = menu.getSlot(slot).getItem();
            if (st.isEmpty()) {
                continue;
            }
            haveTotals.merge(identityKey(st), st.getCount(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : wantTotals.entrySet()) {
            int shortfall = e.getValue() - haveTotals.getOrDefault(e.getKey(), 0);
            if (shortfall > 0) {
                d.missing.add(sample.get(e.getKey()).withCount(shortfall));
            }
        }

        d.assembled = d.wrongSlots.isEmpty();
        return d;
    }

    static String identityKey(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        var customName = s.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            sb.append('\u0001').append(customName.getString());
        }
        var cmd = s.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) {
            sb.append('\u0002').append(cmd);
        }
        return sb.toString();
    }
}
