package org.shizavisuals.mixin.player;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.feature.impl.visual.HitParticlesFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Player player, Entity target, CallbackInfo ci) {
        if (player != MinecraftContext.mc.player) {
            return;
        }
        HitParticlesFeature hitParticles = HitParticlesFeature.getEnabled();
        if (hitParticles != null) {
            hitParticles.onAttack(target);
        }
    }
}
