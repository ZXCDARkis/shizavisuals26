package org.shizavisuals.mixin.render;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.shizavisuals.feature.impl.visual.WorldTweaksFeature;
import org.shizavisuals.utils.ColorUtil;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void worldLightTint(LightmapRenderState state,
                                      float partialTick,
                                      CallbackInfo ci) {
        WorldTweaksFeature worldTweaks = WorldTweaksFeature.getEnabled();
        if (worldTweaks == null || !worldTweaks.usesWorldColor()) {
            return;
        }
        int color = worldTweaks.resolvedWorldColor();
        Vector3f tint = new Vector3f(
                ColorUtil.red(color) / 255.0F,
                ColorUtil.green(color) / 255.0F,
                ColorUtil.blue(color) / 255.0F
        );
        state.blockLightTint = new Vector3f(state.blockLightTint).mul(tint);
        state.skyLightColor = new Vector3f(state.skyLightColor).mul(tint);
        state.ambientColor = new Vector3f(state.ambientColor).mul(tint);
    }
}
