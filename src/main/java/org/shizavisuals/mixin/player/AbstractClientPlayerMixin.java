package org.shizavisuals.mixin.player;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.shizavisuals.feature.impl.visual.RemovalsFeature;
import org.shizavisuals.utils.render.ClientCape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void applyCape(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (!ClientCape.shouldForceCape(player.getUUID())) {
            return;
        }

        cir.setReturnValue(ClientCape.apply(cir.getReturnValue()));
    }

    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void removeFovDynamics(CallbackInfoReturnable<Float> cir) {
        if (RemovalsFeature.shouldRemoveFovDynamics()) {
            cir.setReturnValue(1.0F);
        }
    }
}
