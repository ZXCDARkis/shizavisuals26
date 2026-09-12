package org.shizavisuals.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.feature.impl.visual.RemovalsFeature;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.visual.ShaderHandsFeature;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.Events;
import org.shizavisuals.event.events.render.Render3DEvent;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.world.ShaderHandsRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    private static ShaderHandsRenderer shaderHandsRenderer;
    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            )
    )
    private Matrix4f captureLevelProjection(Matrix4f projectionMatrix) {
        Render3DUtil.captureLevelProjection(projectionMatrix);
        return projectionMatrix;
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void onBobHurt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (RemovalsFeature.shouldRemoveShaking()) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"
            )
    )
    private void renderShaderHands(FeatureRenderDispatcher dispatcher,
                                         SubmitNodeStorage storage,
                                         Operation<Void> original) {
        ShaderHandsFeature feature = FeatureManager.INSTANCE.getEnabled(ShaderHandsFeature.class);
        if (feature == null) {
            if (shaderHandsRenderer != null) {

                shaderHandsRenderer.release();
                shaderHandsRenderer = null;
            }
            original.call(dispatcher, storage);
            return;
        }
        if (shaderHandsRenderer == null) {
            shaderHandsRenderer = new ShaderHandsRenderer();
        }
        shaderHandsRenderer.render(feature, () -> original.call(dispatcher, storage));
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRender3D(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!EventManager.hasListeners(Render3DEvent.class)) {
            return;
        }
        RenderContext.enter3D((GameRenderer) (Object) this, deltaTracker);
        try {
            EventManager.call(Events.RENDER_3D.set(this.minecraft, (GameRenderer) (Object) this, deltaTracker));
        } finally {
            RenderContext.exit3D();
        }
    }
}
