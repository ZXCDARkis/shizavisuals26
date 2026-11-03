package org.shizavisuals.menu.core;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.event.events.screen.ScreenMouseButtonEvent;
import org.lwjgl.glfw.GLFW;

public final class MenuOverlay {
    private static final MenuOverlayState STATE = new MenuOverlayState();
    private static final MenuDragController DRAG = new MenuDragController();
    private static final MenuOverlayRenderer RENDERER = new MenuOverlayRenderer();

    private MenuOverlay() {
    }

    public static boolean toggle(Minecraft minecraft) {
        if (STATE.isInteractive()) {
            close(minecraft);
            return true;
        }
        open(minecraft);
        return true;
    }

    public static void close(Minecraft minecraft) {
        if (!STATE.isOpen() || STATE.isClosing()) {
            return;
        }

        boolean restoreMouse = shouldReturnMouseToGame(minecraft);
        STATE.beginClose();
        DRAG.reset();
        RENDERER.releasePointer();
        if (restoreMouse) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    public static boolean suspendForReload(Minecraft minecraft) {
        if (!STATE.isInteractive()) {
            return false;
        }
        boolean restoreMouse = STATE.grabbedMouseBeforeOpen() && shouldReturnMouseToGame(minecraft);
        STATE.suspend();
        DRAG.reset();
        RENDERER.releasePointer();
        if (restoreMouse) {
            minecraft.mouseHandler.grabMouse();
        }
        return true;
    }

    public static void resumeAfterReload(Minecraft minecraft) {
        if (STATE.isOpen()) {
            return;
        }
        STATE.resume();
        DRAG.reset();
        KeyMapping.releaseAll();
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    public static boolean isOpen() {
        return STATE.isInteractive();
    }

    public static boolean isVisible() {
        return STATE.isOpen();
    }

    public static boolean blocksInput() {
        return STATE.isInteractive();
    }

    public static void focusNextHeaderAction(int direction) {
        STATE.focusNextHeaderAction(direction);
    }

    public static void activateFocusedHeaderAction() {
        STATE.activateFocusedHeaderAction();
    }

    public static boolean isResizing() {
        return DRAG.resizing();
    }

    public static void closePageOrOverlay(Minecraft minecraft) {
        if (STATE.page() != MenuPage.NONE) {
            STATE.openPage(MenuPage.NONE);
            return;
        }
        close(minecraft);
    }

    public static void openSearch() {
        if (STATE.isInteractive()) {
            if (STATE.page() != MenuPage.SEARCH) {
                STATE.openPage(MenuPage.SEARCH);
            }
            RENDERER.focusSearch();
        }
    }

    public static boolean isSearchOpen() {
        return STATE.isInteractive() && RENDERER.isSearchOpen();
    }

    public static boolean isSearchFocused() {
        return STATE.isInteractive() && RENDERER.isSearchFocused();
    }

    public static boolean isCapturingBind() {
        return STATE.isInteractive() && RENDERER.isCapturingBind();
    }

    public static void backspaceSearch() {
        RENDERER.backspaceSearch();
    }

    public static boolean handleKey(int key) {
        return STATE.isInteractive() && RENDERER.handleKey(key);
    }

    public static boolean handleCharacter(int codePoint) {
        if (!STATE.isInteractive()) {
            return false;
        }
        if (RENDERER.handleCharacter(codePoint)) {
            return true;
        }
        if (!isSearchFocused()) {
            return false;
        }
        RENDERER.appendSearchCodePoint(codePoint);
        return true;
    }

    public static void handleScroll(double vertical) {
        if (!STATE.isInteractive()) return;
        RENDERER.handleScroll(mouseX(Minecraft.getInstance()), mouseY(Minecraft.getInstance()), vertical);
    }

    public static boolean handleMouseButton(Minecraft minecraft, int button, int action) {
        if (action == GLFW.GLFW_RELEASE) {
            // The menu can stop being interactive while a drag or grip hold is in
            // progress (close animation, chat open, ...). The release must still
            // reach the controller, otherwise the resize latch stays stuck: the
            // percentage pill freezes on the previewed scale and the panel keeps
            // living on the preview value.
            boolean interactive = STATE.isInteractive();
            if (interactive) {
                RENDERER.releasePointer();
            }
            DRAG.onRelease(interactive ? STATE : null);
            if (!interactive) {
                STATE.cancelPreviewUiScale();
            }
        }
        if (!STATE.isInteractive()) {
            return false;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int mouseX = mouseX(minecraft);
        int mouseY = mouseY(minecraft);
        RENDERER.layout(minecraft, STATE, screenWidth, screenHeight, mouseX, mouseY);

        if (action == GLFW.GLFW_PRESS) {
            if (RENDERER.handleMouseButton(mouseX, mouseY, button, STATE)) {
                DRAG.onRelease(STATE);
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                DRAG.onPress(mouseX, mouseY, STATE, RENDERER);
            }
        }
        return true;
    }

    public static boolean handleScreenMouseButton(Minecraft minecraft, int button, ScreenMouseButtonEvent.Action action) {
        if (action == ScreenMouseButtonEvent.Action.DRAG) {
            return STATE.isInteractive();
        }
        return handleMouseButton(minecraft, button, action == ScreenMouseButtonEvent.Action.RELEASE ? GLFW.GLFW_RELEASE : GLFW.GLFW_PRESS);
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor, int screenWidth, int screenHeight) {
        if (!STATE.isOpen()) {
            return;
        }
        if (STATE.isClosing() && STATE.openProgress() <= 0.001F) {
            STATE.close();
            return;
        }

        MenuDimensions dimensions = MenuDimensions.resolve(minecraft, STATE);
        int mouseX = mouseX(minecraft);
        int mouseY = mouseY(minecraft);
        DRAG.update(mouseX, mouseY, STATE, dimensions, screenWidth, screenHeight);
        RENDERER.layout(minecraft, STATE, screenWidth, screenHeight, mouseX, mouseY);
        RENDERER.drag(mouseX, mouseY);
        RENDERER.render(minecraft, guiGraphicsExtractor);
    }

    private static void open(Minecraft minecraft) {
        boolean grabbedMouseBeforeOpen = minecraft.mouseHandler.isMouseGrabbed();
        STATE.open(grabbedMouseBeforeOpen);
        DRAG.reset();
        KeyMapping.releaseAll();
        if (grabbedMouseBeforeOpen) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    private static int mouseX(Minecraft minecraft) {
        return (int) Math.round(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()));
    }

    private static int mouseY(Minecraft minecraft) {
        return (int) Math.round(minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
    }

    private static boolean shouldReturnMouseToGame(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null && minecraft.gui.screen() == null;
    }
}
