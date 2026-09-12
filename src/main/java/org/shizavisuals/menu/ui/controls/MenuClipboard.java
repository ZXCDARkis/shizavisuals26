package org.shizavisuals.menu.ui.controls;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public final class MenuClipboard {
    private MenuClipboard() {
    }

    public static boolean shortcutDown() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return false;
        }
        long window = client.getWindow().handle();
        if (Util.getPlatform() == Util.OS.OSX) {
            return keyDown(window, GLFW.GLFW_KEY_LEFT_SUPER)
                    || keyDown(window, GLFW.GLFW_KEY_RIGHT_SUPER);
        }
        return keyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || keyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static String get() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? "" : client.keyboardHandler.getClipboard();
    }

    public static void set(String value) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.keyboardHandler.setClipboard(value == null ? "" : value);
        }
    }

    private static boolean keyDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }
}
