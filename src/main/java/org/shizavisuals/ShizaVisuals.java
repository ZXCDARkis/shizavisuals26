package org.shizavisuals;

import net.fabricmc.api.ModInitializer;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.command.CommandManager;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.ClientStartEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.visual.HoldMyItemsFeature;
import org.shizavisuals.utils.FriendManager;
import org.shizavisuals.menu.MenuKeyHandler;
import org.shizavisuals.menu.MenuOverlayRenderHandler;
import org.shizavisuals.menu.i18n.UiLanguage;
import org.shizavisuals.utils.AccountSwitcher;
import org.shizavisuals.utils.ScreenNbtParser;
import org.shizavisuals.utils.inventory.DropAllInventoryController;
import org.shizavisuals.utils.inventory.InventorySwap;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.gui.GuiTexture;
import org.shizavisuals.utils.render.world.WorldEffects;

public class ShizaVisuals implements ModInitializer {
    private final MenuKeyHandler menuKeyHandler = new MenuKeyHandler();
    private final MenuOverlayRenderHandler menuOverlayRenderHandler = new MenuOverlayRenderHandler();

    private static volatile Thread mainGameThread;
    private static volatile boolean clientStarted = false;
    private static volatile boolean stopping = false;
    private static volatile long lastTickMillis = 0L;
    private static volatile long glfwWindow = 0L;

    public static void captureMainThread(Thread t) {
        mainGameThread = t;
    }

    public static void markStopping() {
        stopping = true;
    }

    public static void markClientStarted() {
        clientStarted = true;
    }

    public static void setWindowHandle(long h) {
        if (h != 0L) {
            glfwWindow = h;
        }
    }

    public static void noteTick() {
        lastTickMillis = System.currentTimeMillis();
    }

    private static void scheduleForceExit(long delayMs) {
        Thread w = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "ShizaVisuals-ForceExit");
        w.setDaemon(true);
        w.start();
    }

    @Override
    public void onInitialize() {
        UiLanguage.loadSaved();
        FriendManager.INSTANCE.initialize();
        try {
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> {
                    try { org.shizavisuals.webpanel.WebPanelAwtGuard.shutdown(); } catch (Throwable ignored) {}
                    try { org.shizavisuals.webpanel.WebPanelStream.shutdown(); } catch (Throwable ignored) {}
                    try { org.shizavisuals.hud.island.MusicStatus.shutdown(); } catch (Throwable ignored) {}
                    scheduleForceExit(1500L);
                });
        } catch (Throwable ignored) {}
        // System.exit can hang forever inside third-party shutdown hooks
        // (JCEF/Chromium, SMTC COM threads). A JVM shutdown hook hard-exits
        // regardless of what the other hooks are doing.
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> scheduleForceExit(1500L), "ShizaVisuals-ForceExit-Hook"));
        } catch (Throwable ignored) {}
        startMainThreadWatchdog();
        FeatureManager.INSTANCE.initialize();
        CommandManager.INSTANCE.initialize();
        WorldEffects.bootstrap();
        EventManager.subscribe(FeatureManager.INSTANCE);
        EventManager.subscribe(CommandManager.INSTANCE);
        EventManager.subscribe(FriendManager.INSTANCE);
        EventManager.subscribe(menuKeyHandler);
        EventManager.subscribe(menuOverlayRenderHandler);
        EventManager.subscribe(InventorySwap.INSTANCE);
        EventManager.subscribe(DropAllInventoryController.INSTANCE);
        EventManager.subscribe(ScreenNbtParser.INSTANCE);
        EventManager.subscribe(this);
    }

    @EventTarget
    public void onClientStart(ClientStartEvent event) {
        AccountSwitcher.applyStartupAccount();
        HoldMyItemsFeature.syncOnClientStart();

        mainGameThread = Thread.currentThread();
        clientStarted = true;
        installWindowCloseHook();
        GuiTexture.prewarm(Textures.all());
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        lastTickMillis = System.currentTimeMillis();
        // GLFW context is current on the render thread: capture the window handle here
        if (glfwWindow == 0L) {
            try {
                long h = GLFW.glfwGetCurrentContext();
                if (h != 0L) {
                    glfwWindow = h;
                }
            } catch (Throwable ignored) {
            }
        }
        installWindowCloseHook();
    }

    /**
     * Dumb, reliable kill: the instant the OS close button is pressed we
     * hard-exit the JVM with Runtime.halt(0). We deliberately do NOT call
     * Minecraft's own handler first, because that can synchronously call
     * System.exit() which hangs forever inside JCEF/SMTC shutdown hooks and
     * would block our kill from ever running.
     */
    public static void installWindowCloseHook() {
        try {
            long window = glfwWindow != 0L ? glfwWindow : getWindowHandle();
            if (window == 0L) {
                return;
            }
            org.lwjgl.glfw.GLFW.glfwSetWindowCloseCallback(window, handle -> Runtime.getRuntime().halt(0));
        } catch (Throwable ignored) {
        }
    }

    private static long getWindowHandle() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null) {
                return 0L;
            }
            Object win = mc.getWindow();
            if (win == null) {
                return 0L;
            }
            Class<?> c = win.getClass();
            for (String name : new String[]{"window", "handle", "ptr", "windowPointer"}) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    Object v = f.get(win);
                    if (v instanceof Long) {
                        return (long) v;
                    }
                } catch (Throwable ignored) {
                }
            }
            for (java.lang.reflect.Method m : c.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == long.class) {
                    try {
                        return (long) m.invoke(win);
                    } catch (Throwable ignored) {
                    }
                }
            }
            return org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /**
     * Last-resort guard against the JVM staying alive in the tray. Once the
     * client has started, the only thread that should keep the process alive
     * is the main game/render thread. If it dies -- whether by a clean
     * stop(), a crash, or an uncaught exception in runTick -- and the JVM is
     * still running (kept alive by the non-daemon AWT EventDispatchThread
     * from WebPanelAwtGuard), this daemon watchdog force-halts the process.
     * It also retries installing the window-close hook until the handle is
     * available.
     */
    private void startMainThreadWatchdog() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                    return;
                }
                installWindowCloseHook();
                if (!clientStarted || mainGameThread == null) {
                    continue;
                }
                boolean noTicks = lastTickMillis != 0L
                        && System.currentTimeMillis() - lastTickMillis > 8000L;
                if (stopping || !mainGameThread.isAlive() || noTicks) {
                    try { Thread.sleep(1000L); } catch (InterruptedException ignored) {}
                    Runtime.getRuntime().halt(0);
                    return;
                }
            }
        }, "ShizaVisuals-MainThreadWatchdog");
        t.setDaemon(true);
        t.start();
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
    }
}

