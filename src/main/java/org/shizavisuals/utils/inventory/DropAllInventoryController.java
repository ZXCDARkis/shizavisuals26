package org.shizavisuals.utils.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.screen.ScreenCloseEvent;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DropAllInventoryController implements MinecraftContext {
    public static final DropAllInventoryController INSTANCE = new DropAllInventoryController();

    private static final Component DROP_ALL = Component.literal("Выбросить всё");
    private static final Component STOP = Component.literal("Остановить");

    private final Deque<Integer> slots = new ArrayDeque<>();
    private InventoryScreen screen;
    private InventoryMenu menu;
    private Button button;
    private boolean running;

    private DropAllInventoryController() {
    }

    public static void bindButton(Button button) {
        INSTANCE.button = button;
        INSTANCE.updateButton();
    }

    public static void toggle(InventoryScreen screen) {
        if (INSTANCE.running) {
            INSTANCE.cancel();
        } else {
            INSTANCE.start(screen);
        }
    }

    public static boolean blocksInventoryOperations() {
        return INSTANCE.running;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!this.running) {
            return;
        }
        Minecraft client = event.getClient();
        if (client == null || client.player == null || client.gui.screen() != this.screen
                || client.player.containerMenu != this.menu || InventoryUtil.hasCarriedItem()) {
            cancel();
            return;
        }
        if (InventorySwap.isBusy()) {
            return;
        }

        while (!this.slots.isEmpty()) {
            int slotId = this.slots.removeFirst();
            if (!this.menu.isValidSlotIndex(slotId) || !this.menu.getSlot(slotId).hasItem()) {
                continue;
            }
            InventoryUtil.dropStack(slotId);
            return;
        }
        finish();
    }

    @EventTarget
    public void onScreenClose(ScreenCloseEvent event) {
        if (event.getScreen() == this.screen) {
            cancel();
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        cancel();
    }

    private void start(InventoryScreen screen) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || screen == null
                || client.player.containerMenu != screen.getMenu()
                || InventoryUtil.hasCarriedItem() || InventorySwap.isBusy()) {
            return;
        }

        this.screen = screen;
        this.menu = screen.getMenu();
        this.slots.clear();
        for (int slotId = 0; slotId < this.menu.slots.size(); slotId++) {
            if (this.menu.getSlot(slotId).hasItem()) {
                this.slots.addLast(slotId);
            }
        }
        this.running = !this.slots.isEmpty();
        updateButton();
    }

    private void finish() {
        this.running = false;
        this.slots.clear();
        updateButton();
    }

    private void cancel() {
        finish();
        this.screen = null;
        this.menu = null;
        this.button = null;
    }

    private void updateButton() {
        if (this.button != null) {
            this.button.setMessage(this.running ? STOP : DROP_ALL);
        }
    }
}
