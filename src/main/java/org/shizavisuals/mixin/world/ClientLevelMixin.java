package org.shizavisuals.mixin.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.shizavisuals.feature.impl.visual.RemovalsFeature;
import org.shizavisuals.utils.render.world.DynamicLightManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Shadow
    @Final
    private Deque<Runnable> lightUpdateQueue;

    private boolean hadQueuedLightUpdates;

    @Inject(method = "update", at = @At("HEAD"))
    private void captureQueuedLightUpdates(CallbackInfo ci) {
        this.hadQueuedLightUpdates = !this.lightUpdateQueue.isEmpty();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void restoreDynamicLightsAfterPackets(CallbackInfo ci) {
        if (this.hadQueuedLightUpdates) {
            DynamicLightManager.INSTANCE.revalidateAfterVanillaUpdates((ClientLevel) (Object) this);
        }
    }

    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlaySeededEntitySound(
            Entity except,
            Entity sourceEntity,
            Holder<SoundEvent> sound,
            SoundSource source,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci
    ) {
        if (RemovalsFeature.shouldRemoveSound(sound.value())) {
            ci.cancel();
        }
    }

    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlaySeededPositionedSound(
            Entity except,
            double x,
            double y,
            double z,
            Holder<SoundEvent> sound,
            SoundSource source,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci
    ) {
        if (RemovalsFeature.shouldRemoveSound(sound.value())) {
            ci.cancel();
        }
    }

    @Inject(
            method = "playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlayLocalEntitySound(Entity sourceEntity, SoundEvent sound, SoundSource source, float volume, float pitch, CallbackInfo ci) {
        if (RemovalsFeature.shouldRemoveSound(sound)) {
            ci.cancel();
        }
    }

    @Inject(method = "playPlayerSound", at = @At("HEAD"), cancellable = true)
    private void onPlayPlayerSound(SoundEvent sound, SoundSource source, float volume, float pitch, CallbackInfo ci) {
        if (RemovalsFeature.shouldRemoveSound(sound)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlayLocalPositionedSound(
            double x,
            double y,
            double z,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch,
            boolean distanceDelay,
            CallbackInfo ci
    ) {
        if (RemovalsFeature.shouldRemoveSound(sound)) {
            ci.cancel();
        }
    }
}
