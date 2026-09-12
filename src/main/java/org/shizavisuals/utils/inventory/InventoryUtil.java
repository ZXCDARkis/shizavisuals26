package org.shizavisuals.utils.inventory;

import org.shizavisuals.context.InventoryContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public final class InventoryUtil implements InventoryContext {
    private static final InventoryUtil CONTEXT = new InventoryUtil();
    private static final int INVENTORY_SLOTS_START = 9;

    private InventoryUtil() {
    }

    public static int findPlayerMenuSlot(LocalPlayer player, Predicate<ItemStack> predicate) {
        for (int slot = InventoryMenu.USE_ROW_SLOT_START; slot < InventoryMenu.USE_ROW_SLOT_END; slot++) {
            if (predicate.test(player.inventoryMenu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        for (int slot = INVENTORY_SLOTS_START; slot < InventoryMenu.USE_ROW_SLOT_START; slot++) {
            if (predicate.test(player.inventoryMenu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }

    public static int findInventorySlot(LocalPlayer player, Predicate<ItemStack> predicate) {
        for (int slot = InventoryMenu.INV_SLOT_START; slot < InventoryMenu.USE_ROW_SLOT_START; slot++) {
            if (predicate.test(player.inventoryMenu.getSlot(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }

    public static Screen getCurrentScreen() {
        return CONTEXT.screen();
    }

    public static boolean isContainerScreenOpen() {
        return CONTEXT.hasContainerScreen();
    }

    public static AbstractContainerScreen<?> getContainerScreen() {
        return CONTEXT.containerScreen();
    }

    public static AbstractContainerMenu getOpenMenu() {
        return CONTEXT.menu();
    }

    public static boolean hasOpenMenu() {
        return CONTEXT.hasMenu();
    }

    public static boolean clickSlot(int slotId, ClickAction clickAction, ContainerInput input) {
        return clickSlot(slotId, clickButton(clickAction), input);
    }

    public static boolean clickSlot(int slotId, int button, ContainerInput input) {
        AbstractContainerMenu menu = getOpenMenu();
        return menu != null && clickMenu(menu, slotId, button, input);
    }

    public static boolean leftClickSlot(int slotId) {
        return clickSlot(slotId, ClickAction.PRIMARY, ContainerInput.PICKUP);
    }

    public static boolean rightClickSlot(int slotId) {
        return clickSlot(slotId, ClickAction.SECONDARY, ContainerInput.PICKUP);
    }

    public static boolean quickMoveSlot(int slotId) {
        return clickSlot(slotId, 0, ContainerInput.QUICK_MOVE);
    }

    public static boolean swapWithHotbar(int slotId, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return false;
        }
        return clickSlot(slotId, hotbarSlot, ContainerInput.SWAP);
    }

    public static boolean dropOne(int slotId) {
        return clickSlot(slotId, 0, ContainerInput.THROW);
    }

    public static boolean dropStack(int slotId) {
        return clickSlot(slotId, 1, ContainerInput.THROW);
    }

    public static boolean dropPlayerStack(LocalPlayer player, int slotId) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null
                || player == null
                || player.containerMenu != player.inventoryMenu
                || !player.inventoryMenu.isValidSlotIndex(slotId)
                || player.inventoryMenu.getSlot(slotId).getItem().isEmpty()) {
            return false;
        }
        client.gameMode.handleContainerInput(
                player.inventoryMenu.containerId,
                slotId,
                1,
                ContainerInput.THROW,
                player
        );
        return true;
    }

    public static boolean pickupAll(int slotId) {
        return clickSlot(slotId, 0, ContainerInput.PICKUP_ALL);
    }

    public static boolean pickupOutside() {
        AbstractContainerMenu menu = getOpenMenu();
        return menu != null && clickMenu(menu, AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, 0, ContainerInput.PICKUP);
    }

    public static boolean moveStack(int fromSlotId, int toSlotId) {
        AbstractContainerMenu menu = getOpenMenu();
        if (menu == null || fromSlotId == toSlotId) {
            return false;
        }
        if (!menu.isValidSlotIndex(fromSlotId) || !menu.isValidSlotIndex(toSlotId)) {
            return false;
        }
        if (!menu.getCarried().isEmpty()) {
            return false;
        }

        Slot fromSlot = menu.getSlot(fromSlotId);
        if (fromSlot == null || !fromSlot.hasItem()) {
            return false;
        }

        if (!clickMenu(menu, fromSlotId, 0, ContainerInput.PICKUP)) {
            return false;
        }
        if (menu.getCarried().isEmpty()) {
            return false;
        }

        if (!clickMenu(menu, toSlotId, 0, ContainerInput.PICKUP)) {
            clickMenu(menu, fromSlotId, 0, ContainerInput.PICKUP);
            return false;
        }

        if (!menu.getCarried().isEmpty()) {
            clickMenu(menu, fromSlotId, 0, ContainerInput.PICKUP);
        }
        return true;
    }

    public static boolean hasCarriedItem() {
        AbstractContainerMenu menu = getOpenMenu();
        return menu != null && !menu.getCarried().isEmpty();
    }

    public static ItemStack getCarriedItem() {
        AbstractContainerMenu menu = getOpenMenu();
        return menu != null ? menu.getCarried() : ItemStack.EMPTY;
    }

    public static ItemStack getSlotItem(int slotId) {
        AbstractContainerMenu menu = getOpenMenu();
        if (menu == null || !menu.isValidSlotIndex(slotId)) {
            return ItemStack.EMPTY;
        }
        return menu.getSlot(slotId).getItem();
    }

    private static boolean clickMenu(AbstractContainerMenu menu, int slotId, int button, ContainerInput input) {
        MultiPlayerGameMode gameMode = CONTEXT.gameMode();
        LocalPlayer player = CONTEXT.player();
        if (gameMode == null || player == null) {
            return false;
        }
        gameMode.handleContainerInput(menu.containerId, slotId, button, input, player);
        return true;
    }

    private static int clickButton(ClickAction clickAction) {
        return clickAction == ClickAction.SECONDARY ? 1 : 0;
    }
}
