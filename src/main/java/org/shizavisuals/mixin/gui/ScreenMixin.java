package org.shizavisuals.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.Events;
import org.shizavisuals.event.events.screen.ScreenCloseEvent;
import org.shizavisuals.event.events.screen.ScreenKeyEvent;
import org.shizavisuals.event.events.screen.ScreenRenderEvent;
import org.shizavisuals.menu.core.MenuOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow
    @Final
    protected net.minecraft.client.Minecraft minecraft;

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void onClose(CallbackInfo ci) {
        if (!EventManager.hasListeners(ScreenCloseEvent.class)) {
            return;
        }

        if (EventManager.call(Events.SCREEN_CLOSE.set(this.minecraft, (Screen) (Object) this)).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!EventManager.hasListeners(ScreenRenderEvent.class)) {
            return;
        }

        if (EventManager.call(Events.SCREEN_RENDER.set((Screen) (Object) this, guiGraphicsExtractor, mouseX, mouseY, partialTick)).isCancelled()) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "extractRenderState", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int maskMouseXWhenOverlayOpen(int mouseX) {
        return MenuOverlay.isOpen() ? Integer.MIN_VALUE / 4 : mouseX;
    }

    @ModifyVariable(method = "extractRenderState", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int maskMouseYWhenOverlayOpen(int mouseY) {
        return MenuOverlay.isOpen() ? Integer.MIN_VALUE / 4 : mouseY;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (!EventManager.hasListeners(ScreenKeyEvent.class)) {
            return;
        }

        if (EventManager.call(Events.SCREEN_KEY.set((Screen) (Object) this, keyEvent, ScreenKeyEvent.Action.PRESS)).isCancelled()) {
            cir.setReturnValue(true);
        }
    }
}
