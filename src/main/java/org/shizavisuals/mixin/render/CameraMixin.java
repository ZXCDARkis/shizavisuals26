package org.shizavisuals.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.context.RotationContext;
import org.shizavisuals.feature.impl.visual.RemovalsFeature;
import org.shizavisuals.utils.render.tnt.TntEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(CameraRenderState cameraState, float cameraEntityPartialTick, CallbackInfo ci) {
        if (RemovalsFeature.shouldRemoveBadEffectsVisuals()) {
            cameraState.entityRenderState.doesMobEffectBlockSky = false;
        }
        // Offsetting the render-state position (instead of the camera itself)
        // shakes everything world-space built from it together, which is what
        // an explosion should do; hands and GUI stay anchored.
        TntEffectRenderer tnt = TntEffectRenderer.active();
        if (tnt != null) {
            cameraState.pos = cameraState.pos.add(tnt.shakeX(), tnt.shakeY(), tnt.shakeZ());
        }
    }

    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void onAlignWithEntity(float partialTick, CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        Entity entity = camera.entity();
        if (entity == null) {
            return;
        }

        RotationContext.applyRenderInterpolation();
        RotationContext.syncFreeLook(entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
    }


}
