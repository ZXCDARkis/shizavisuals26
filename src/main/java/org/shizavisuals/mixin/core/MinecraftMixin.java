package org.shizavisuals.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.shizavisuals.context.RotationContext;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.Events;
import org.shizavisuals.event.events.game.TickContext;
import org.shizavisuals.event.events.game.AttackEvent;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.ClientStartEvent;
import org.shizavisuals.event.events.lifecycle.DisconnectEvent;
import org.shizavisuals.event.events.lifecycle.ResourceReloadEvent;
import org.shizavisuals.event.events.lifecycle.ShutdownEvent;
import org.shizavisuals.event.events.lifecycle.WorldJoinEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.screen.ScreenOpenEvent;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    private static final TickContext TICK_CONTEXT = new TickContext();
    @Unique
    private ClientLevel previousLevel;

    @Shadow
    public ClientLevel level;

    @Inject(method = "onGameLoadFinished", at = @At("TAIL"))
    private void onClientStart(GameLoadCookie gameLoadCookie, CallbackInfo ci) {
        if (!EventManager.hasListeners(ClientStartEvent.class)) {
            return;
        }
        EventManager.call(Events.CLIENT_START.set((Minecraft) (Object) this));
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if (!EventManager.hasListeners(GameTickEvent.class)) {
            return;
        }
        Minecraft client = (Minecraft) (Object) this;
        EventManager.call(Events.GAME_TICK.set(client, TICK_CONTEXT.begin(client)));
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!EventManager.hasListeners(AttackEvent.class)) {
            return;
        }
        if (EventManager.call(Events.ATTACK.set((Minecraft) (Object) this)).isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void capturePreviousLevel(ClientLevel level, CallbackInfo ci) {
        this.previousLevel = this.level;
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void onSetLevel(ClientLevel level, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        ClientLevel previous = this.previousLevel;

        if (level == null) {
            RotationContext.clear();
        }

        if (previous == null && level != null && EventManager.hasListeners(WorldJoinEvent.class)) {
            EventManager.call(Events.WORLD_JOIN.set(client, level));
        }

        if (previous != null && level == null && EventManager.hasListeners(WorldLeaveEvent.class)) {
            EventManager.call(Events.WORLD_LEAVE.set(client, previous));
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void onDisconnect(Screen screen, boolean transferring, boolean resetting, CallbackInfo ci) {
        if (!EventManager.hasListeners(DisconnectEvent.class)) {
            return;
        }
        EventManager.call(Events.DISCONNECT.set((Minecraft) (Object) this, screen, transferring, resetting));
    }

    @Inject(method = "setScreenAndShow", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (screen == null || !EventManager.hasListeners(ScreenOpenEvent.class)) {
            return;
        }

        if (EventManager.call(Events.SCREEN_OPEN.set(client, screen)).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void onShutdown(CallbackInfo ci) {
        org.shizavisuals.ShizaVisuals.markStopping();
        if (!EventManager.hasListeners(ShutdownEvent.class)) {
            return;
        }
        EventManager.call(Events.SHUTDOWN.set((Minecraft) (Object) this));
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void shizavisuals$captureMainThread(CallbackInfo ci) {
        org.shizavisuals.ShizaVisuals.captureMainThread(Thread.currentThread());
        org.shizavisuals.ShizaVisuals.markClientStarted();
    }

    @Inject(method = "onResourceLoadFinished", at = @At("TAIL"))
    private void onResourceReload(GameLoadCookie gameLoadCookie, CallbackInfo ci) {
        if (!EventManager.hasListeners(ResourceReloadEvent.class)) {
            return;
        }
        EventManager.call(Events.RESOURCE_RELOAD.set((Minecraft) (Object) this));
    }
}
