package org.shizavisuals.mixin.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.Events;
import org.shizavisuals.event.events.game.PlayerJumpEvent;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.visual.RemovalsFeature;
import org.shizavisuals.feature.impl.visual.SwingAnimationFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void onPlayerJump(CallbackInfo ci) {
        LocalPlayer player = MinecraftContext.mc.player;
        if (player != null && (Object) this == player && EventManager.hasListeners(PlayerJumpEvent.class)) {
            EventManager.call(Events.PLAYER_JUMP.set(player, player.position()));
        }
    }

    @Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
    private void onGetEffectBlendFactor(Holder<MobEffect> effect, float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this == MinecraftContext.mc.player && RemovalsFeature.shouldRemoveBadEffectsVisuals()) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void customSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this != MinecraftContext.mc.player) {
            return;
        }
        SwingAnimationFeature swing = FeatureManager.INSTANCE.getEnabled(SwingAnimationFeature.class);
        if (swing != null) {
            cir.setReturnValue(swing.swingDurationTicks());
        }
    }
}
