package org.shizavisuals.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.shizavisuals.feature.impl.visual.TargetEsp;
import org.shizavisuals.utils.render.esp.TargetScanRenderer;
import org.shizavisuals.utils.render.esp.TargetScanState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class TargetScanLivingMixin<
        T extends LivingEntity,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>> {

    @Shadow
    public abstract Identifier getTextureLocation(S state);

    @Shadow
    protected M model;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void shiza$extractTargetScan(T entity, S state, float partialTick, CallbackInfo ci) {
        TargetEsp targetEsp = TargetEsp.getEnabled();
        ((TargetScanState) state).shiza$setTargetScanTarget(
                targetEsp != null && targetEsp.isScanTarget(entity));
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                    shift = At.Shift.BEFORE))
    private void shiza$submitTargetScan(
            S state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci) {
        if (!((TargetScanState) state).shiza$isTargetScanTarget()) {
            return;
        }

        TargetEsp targetEsp = TargetEsp.getEnabled();
        if (targetEsp == null || !targetEsp.isScanMode()) {
            return;
        }

        TargetScanRenderer.submit(
                this.model,
                state,
                poseStack,
                collector,
                this.getTextureLocation(state),
                targetEsp.scanColor(),
                targetEsp.scanSecondColor(),
                targetEsp.scanSaturation(),
                targetEsp.scanAnimation(),
                targetEsp.scanSpeed(),
                targetEsp.scanGlow());
    }
}
