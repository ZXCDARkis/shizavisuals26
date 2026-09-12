package org.shizavisuals.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.Events;
import org.shizavisuals.event.events.game.PlayerTickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickPre(CallbackInfo ci) {
        if (!EventManager.hasListeners(PlayerTickEvent.class)) {
            return;
        }
        EventManager.call(Events.PLAYER_TICK.set((LocalPlayer) (Object) this, PlayerTickEvent.Phase.PRE));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickPost(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!EventManager.hasListeners(PlayerTickEvent.class)) {
            return;
        }
        EventManager.call(Events.PLAYER_TICK.set(player, PlayerTickEvent.Phase.POST));
    }

    @Redirect(
            method = "applyInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F")
    )
    private float useCameraPitchForHandBob(LocalPlayer player) {
        if (this.minecraft.options.getCameraType().isFirstPerson()) {
            return this.minecraft.gameRenderer.mainCamera().xRot();
        }

        return player.getXRot();
    }

    @Redirect(
            method = "applyInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F")
    )
    private float useCameraYawForHandBob(LocalPlayer player) {
        if (this.minecraft.options.getCameraType().isFirstPerson()) {
            return this.minecraft.gameRenderer.mainCamera().yRot();
        }

        return player.getYRot();
    }

    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void onGetViewYRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (org.shizavisuals.context.RotationContext.isActive()) {
            cir.setReturnValue(org.shizavisuals.context.RotationContext.getFreeYaw());
        }
    }

    @Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
    private void onGetViewXRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (org.shizavisuals.context.RotationContext.isActive()) {
            cir.setReturnValue(org.shizavisuals.context.RotationContext.getFreePitch());
        }
    }
}
