package org.shizavisuals.context;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public interface ScreenContext extends MinecraftContext {
    default Screen currentScreen() {
        return screen();
    }

    default boolean hasScreen() {
        return currentScreen() != null;
    }

    default boolean hasContainerScreen() {
        return currentScreen() instanceof AbstractContainerScreen<?>;
    }

    default AbstractContainerScreen<?> containerScreen() {
        return currentScreen() instanceof AbstractContainerScreen<?> containerScreen ? containerScreen : null;
    }
}
