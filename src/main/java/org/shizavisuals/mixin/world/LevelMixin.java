package org.shizavisuals.mixin.world;

import net.minecraft.world.level.Level;
import org.shizavisuals.feature.impl.visual.RemovalsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void onGetRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (RemovalsFeature.shouldRemoveWeather()) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void onGetThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (RemovalsFeature.shouldRemoveWeather()) {
            cir.setReturnValue(0.0F);
        }
    }
}
