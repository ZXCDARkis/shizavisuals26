package org.shizavisuals.utils.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Assembles the inventory into a preset using server-side slot swaps
 * (ContainerInput.SWAP with hotbar buttons) — the same packets a player sends
 * by pressing number keys over slots. Timing is randomized around a base
 * delay so the sequence looks hand-made.
 */
public final class AutoArranger {
    private static final int HOTBAR_FIRST = 36, HOTBAR_LAST = 44, OFFHAND = 45;
    private static final int OFFHAND_BUTTON = 40;
    private static final int MAX_SWAPS = 400;

    private final Minecraft mc = Minecraft.getInstance();

    private boolean running;
    private InventoryPreset preset;

    private final Deque<int[]> pending = new ArrayDeque<>();
    private final Set<Integer> skip = new HashSet<>();

    private long nextActionNano;
    private double baseDelayMs = 160.0;
    private int swaps;

    public void setBaseDelayMs(double ms) {
        this.baseDelayMs = Math.max(60.0, ms);
    }

    public boolean isRunning() {
        return running;
    }

    public void start(InventoryPreset preset) {
        if (preset == null || preset.items.isEmpty()) {
            return;
        }
        this.preset = preset;
        this.running = true;
        this.skip.clear();
        this.pending.clear();
        this.swaps = 0;
        this.nextActionNano = System.nanoTime();
    }

    public void stop() {
        this.running = false;
        this.pending.clear();
    }

    public void tick() {
        if (!running) {
            return;
        }
        LocalPlayer player = mc.player;
        if (!(mc.gui.screen() instanceof InventoryScreen) || player == null || mc.gameMode == null) {
            stop();
            return;
        }
        AbstractContainerMenu menu = player.inventoryMenu;
        long now = System.nanoTime();
        if (now < nextActionNano) {
            return;
        }

        if (!menu.getCarried().isEmpty()) {
            schedule();
            return;
        }

        if (pending.isEmpty()) {
            planNextSwap(menu);
            if (!running || pending.isEmpty()) {
                return;
            }
        }

        int[] act = pending.poll();
        if (act != null) {
            swap(menu, act[0], act[1]);
            if (++swaps >= MAX_SWAPS) {
                stop();
                return;
            }
        }
        schedule();
    }

    private void planNextSwap(AbstractContainerMenu menu) {
        while (true) {
            int target = findWrongTarget(menu);
            if (target < 0) {
                running = false;
                return;
            }
            InvItem want = preset.items.get(target);
            int source = findSource(menu, want, target);
            if (source < 0) {
                skip.add(target);
                continue;
            }

            int bS = swapButton(source);
            int bT = swapButton(target);

            // Direct hotbar-number swap when either side is hotbar/offhand.
            if (bS >= 0) {
                pending.add(new int[]{target, bS});
                return;
            }
            if (bT >= 0 && !SlotLayout.isArmor(source)) {
                pending.add(new int[]{source, bT});
                return;
            }

            // Otherwise shuttle through a free (or wrong) hotbar slot.
            int tmp = emptyHotbar(menu);
            if (tmp < 0 && !SlotLayout.isArmor(source)) {
                tmp = wrongHotbar(menu);
                if (tmp < 0) {
                    tmp = HOTBAR_FIRST;
                }
            }
            if (tmp < 0) {
                skip.add(target);
                continue;
            }
            int hb = tmp - HOTBAR_FIRST;
            pending.add(new int[]{source, hb});
            pending.add(new int[]{target, hb});
            return;
        }
    }

    private int findWrongTarget(AbstractContainerMenu menu) {
        for (int slot : SlotLayout.managedOrder()) {
            if (slot >= menu.slots.size() || skip.contains(slot)) {
                continue;
            }
            InvItem want = preset.items.get(slot);
            if (want != null && !want.matches(menu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }

    private int findSource(AbstractContainerMenu menu, InvItem want, int target) {
        if (want == null) {
            return -1;
        }
        for (int slot : SlotLayout.managedOrder()) {
            if (slot == target || slot >= menu.slots.size()) {
                continue;
            }
            ItemStack cur = menu.getSlot(slot).getItem();
            if (!want.matches(cur)) {
                continue;
            }
            InvItem deserved = preset.items.get(slot);
            if (deserved != null && deserved.matches(cur)) {
                continue; // that slot is already correct — don't rob it
            }
            return slot;
        }
        return -1;
    }

    private static int swapButton(int slot) {
        if (SlotLayout.isHotbar(slot)) {
            return slot - HOTBAR_FIRST;
        }
        if (slot == OFFHAND) {
            return OFFHAND_BUTTON;
        }
        return -1;
    }

    private int emptyHotbar(AbstractContainerMenu menu) {
        for (int s = HOTBAR_FIRST; s <= HOTBAR_LAST; s++) {
            if (s < menu.slots.size() && menu.getSlot(s).getItem().isEmpty()) {
                return s;
            }
        }
        return -1;
    }

    private int wrongHotbar(AbstractContainerMenu menu) {
        for (int s = HOTBAR_FIRST; s <= HOTBAR_LAST; s++) {
            if (s >= menu.slots.size()) {
                continue;
            }
            InvItem deserved = preset.items.get(s);
            if (deserved == null || !deserved.matches(menu.getSlot(s).getItem())) {
                return s;
            }
        }
        return -1;
    }

    private void swap(AbstractContainerMenu menu, int slot, int button) {
        mc.gameMode.handleContainerInput(menu.containerId, slot, button, ContainerInput.SWAP, mc.player);
    }

    private void schedule() {
        double ms = baseDelayMs * (0.6 + ThreadLocalRandom.current().nextDouble() * 0.8);
        if (ThreadLocalRandom.current().nextInt(10) == 0) {
            ms *= 2.5;
        }
        nextActionNano = System.nanoTime() + (long) (ms * 1_000_000.0);
    }
}
