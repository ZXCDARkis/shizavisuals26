package org.shizavisuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.shizavisuals.command.CommandManager;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.ClientStartEvent;
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
import org.shizavisuals.utils.render.cosmetic.PulseCosmeticLayers;
import org.shizavisuals.utils.render.gui.GuiTexture;
import org.shizavisuals.utils.render.world.WorldEffects;

public class ShizaVisuals implements ModInitializer {
    private static final long FORCE_EXIT_DELAY_MS = 5000L;

    private MenuKeyHandler menuKeyHandler;
    private MenuOverlayRenderHandler menuOverlayRenderHandler;

    private static volatile Thread mainGameThread;
    private static volatile boolean clientStarted;
    private static volatile boolean stopping;
    private static volatile boolean forceExitScheduled;

    public static void captureMainThread(Thread thread) {
        mainGameThread = thread;
    }

    public static void markClientStarted() {
        stopping = false;
        clientStarted = true;
    }

    public static void markStopping() {
        stopping = true;
        scheduleForceExit();
    }

    private static synchronized void scheduleForceExit() {
        if (forceExitScheduled) {
            return;
        }
        forceExitScheduled = true;
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(FORCE_EXIT_DELAY_MS);
            } catch (InterruptedException ignored) {
                return;
            }
            Runtime.getRuntime().halt(0);
        }, "ShizaVisuals-ForceExit");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        menuKeyHandler = new MenuKeyHandler();
        menuOverlayRenderHandler = new MenuOverlayRenderHandler();
        UiLanguage.loadSaved();
        FriendManager.INSTANCE.initialize();
        try {
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> markStopping());
        } catch (Throwable ignored) {
        }
        startMainThreadWatchdog();
        PulseCosmeticLayers.register();
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
        GuiTexture.prewarm(Textures.all());
    }

    private void startMainThreadWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                    return;
                }
                Thread gameThread = mainGameThread;
                if (clientStarted && !stopping && gameThread != null && !gameThread.isAlive()) {
                    Runtime.getRuntime().halt(0);
                    return;
                }
            }
        }, "ShizaVisuals-MainThreadWatchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
}

