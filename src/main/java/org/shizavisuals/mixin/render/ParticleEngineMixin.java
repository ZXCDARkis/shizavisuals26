package org.shizavisuals.mixin.render;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.shizavisuals.feature.impl.visual.RemovalsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void onCreateParticle(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xa,
            double ya,
            double za,
            CallbackInfoReturnable<Particle> cir
    ) {
        if (RemovalsFeature.shouldRemoveParticle(options)) {
            cir.setReturnValue(null);
        }
    }
}
