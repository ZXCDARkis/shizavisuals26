package org.shizavisuals.context;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

public interface InventoryContext extends ScreenContext {

    default AbstractContainerMenu menu() {
        AbstractContainerScreen<?> containerScreen = containerScreen();
        return containerScreen != null ? containerScreen.getMenu() : null;
    }

    default boolean hasMenu() {
        return menu() != null;
    }
}
