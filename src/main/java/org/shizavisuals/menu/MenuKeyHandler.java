package org.shizavisuals.menu;

import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.input.KeyboardInputEvent;
import org.shizavisuals.event.events.input.CharacterInputEvent;
import org.shizavisuals.event.events.input.MouseInputEvent;
import org.shizavisuals.event.events.screen.ScreenKeyEvent;
import org.shizavisuals.event.events.screen.ScreenMouseButtonEvent;
import org.shizavisuals.menu.core.MenuOverlay;
import org.shizavisuals.menu.ui.controls.MenuClipboard;
import org.lwjgl.glfw.GLFW;

public final class MenuKeyHandler implements MinecraftContext {
    @EventTarget(priority = 1_000)
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (event.getKey() == GLFW.GLFW_KEY_F11) {
            return;
        }

        if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (!MenuOverlay.isOpen() || !MenuOverlay.isCapturingBind()) {
                if (MenuOverlay.toggle(mc)) {
                    event.cancel();
                }
                return;
            }
        }

        if (!MenuOverlay.isOpen()) {
            return;
        }

        boolean pressOrRepeat = event.getAction() == GLFW.GLFW_PRESS || event.getAction() == GLFW.GLFW_REPEAT;
        if (pressOrRepeat && MenuOverlay.handleKey(event.getKey())) {
            event.cancel();
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            event.cancel();
            return;
        }
        if (pressOrRepeat && event.getKey() == GLFW.GLFW_KEY_ESCAPE) {
            MenuOverlay.closePageOrOverlay(mc);
            event.cancel();
            return;
        } else if (pressOrRepeat && event.getKey() == GLFW.GLFW_KEY_BACKSPACE && MenuOverlay.isSearchFocused()) {
            MenuOverlay.backspaceSearch();
            event.cancel();
            return;
        } else if (event.getAction() == GLFW.GLFW_PRESS
                && event.getKey() == GLFW.GLFW_KEY_F
                && MenuClipboard.shortcutDown()) {
            MenuOverlay.openSearch();
            event.cancel();
            return;
        } else if (event.getAction() == GLFW.GLFW_PRESS && (event.getKey() == GLFW.GLFW_KEY_TAB || event.getKey() == GLFW.GLFW_KEY_RIGHT)) {
            MenuOverlay.focusNextHeaderAction(1);
            event.cancel();
            return;
        } else if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_LEFT) {
            MenuOverlay.focusNextHeaderAction(-1);
            event.cancel();
            return;
        } else if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_ENTER) {
            MenuOverlay.activateFocusedHeaderAction();
            event.cancel();
            return;
        }

        if (MenuOverlay.isSearchFocused() || MenuOverlay.isCapturingBind()) {
            event.cancel();
        }
    }

    @EventTarget(priority = 1_000)
    public void onCharacterInput(CharacterInputEvent event) {
        if (MenuOverlay.handleCharacter(event.getCodePoint())) {
            event.cancel();
        }
    }

    @EventTarget(priority = 1_000)
    public void onMouseInput(MouseInputEvent event) {
        if (MenuOverlay.handleMouseButton(mc, event.getButton(), event.getAction())) {
            event.cancel();
        }
    }

    @EventTarget(priority = 1_000)
    public void onScreenKey(ScreenKeyEvent event) {
        if (event.getKeyEvent() != null && event.getKeyEvent().key() == GLFW.GLFW_KEY_F11) {
            return;
        }
        if (MenuOverlay.blocksInput()) {
            event.cancel();
        }
    }

    @EventTarget(priority = 1_000)
    public void onScreenMouseButton(ScreenMouseButtonEvent event) {
        if (MenuOverlay.handleScreenMouseButton(mc, event.getMouseButtonEvent().button(), event.getAction())) {
            event.cancel();
        }
    }
}
