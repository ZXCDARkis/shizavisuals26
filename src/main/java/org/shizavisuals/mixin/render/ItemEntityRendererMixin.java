package org.shizavisuals.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.visual.ItemPhysicsFeature;
import org.shizavisuals.utils.render.ItemEntityRenderStateAccess;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final float TUMBLE_SPIN_DEGREES = 300.0F;
    private static final float FLAT_ROTATION_DEGREES = 90.0F;

    private static final float GROUND_EPSILON = 0.002F;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void captureOnGround(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ((ItemEntityRenderStateAccess) state).setOnGround(entity.onGround());
    }

    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V")
    )
    private void skipHoverTranslate(
            PoseStack poseStack,
            float x,
            float y,
            float z,
            ItemEntityRenderState state,
            PoseStack poseStackArg,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        if (!physicsApplies(state)) {
            poseStack.translate(x, y, z);
        }
    }

    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V")
    )
    private void applyPhysicsTransform(
            PoseStack poseStack,
            Quaternionfc spinRotation,
            ItemEntityRenderState state,
            PoseStack poseStackArg,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        if (!physicsApplies(state)) {
            poseStack.mulPose(spinRotation);
            return;
        }

        AABB box = state.item.getModelBoundingBox();
        float yaw = (state.seed % 360 + 360) % 360;
        if (((ItemEntityRenderStateAccess) state).isOnGround()) {

            poseStack.translate(0.0F, (float) box.maxZ + GROUND_EPSILON, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(FLAT_ROTATION_DEGREES));
        } else {

            float centerY = (float) ((box.minY + box.maxY) * 0.5D);
            float halfHeight = (float) ((box.maxY - box.minY) * 0.5D);
            float spin = ItemEntity.getSpin(state.ageInTicks, state.bobOffset);
            poseStack.translate(0.0F, halfHeight, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(spin * TUMBLE_SPIN_DEGREES));
            poseStack.translate(0.0F, -centerY, 0.0F);
        }
    }

    @Unique
    private static boolean physicsApplies(ItemEntityRenderState state) {
        return FeatureManager.INSTANCE.getEnabled(ItemPhysicsFeature.class) != null && !state.item.isEmpty();
    }
}
