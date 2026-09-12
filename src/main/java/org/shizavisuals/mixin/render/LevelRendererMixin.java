package org.shizavisuals.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.shizavisuals.feature.impl.visual.BlockOutlineFeature;
import org.shizavisuals.utils.render.world.WorldEffectContext;
import org.shizavisuals.utils.render.world.WorldEffects;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void replaceVanillaBlockOutline(PoseStack poseStack,
                                                  SubmitNodeCollector collector,
                                                  LevelRenderState levelRenderState,
                                                  CallbackInfo ci) {
        BlockOutlineFeature feature = BlockOutlineFeature.getEnabled();
        if (feature != null && feature.usesShader()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderWorldEffects(GraphicsResourceAllocator graphicsResourceAllocator,
                                     DeltaTracker deltaTracker,
                                     boolean renderBlockOutline,
                                     CameraRenderState cameraRenderState,
                                     Matrix4fc projectionMatrix,
                                     GpuBufferSlice fogParameters,
                                     Vector4f skyColor,
                                     boolean hasCapturedFrustum,
                                     CallbackInfo ci) {
        WorldEffects.render(new WorldEffectContext(
                this.levelRenderState,
                cameraRenderState,
                projectionMatrix,
                deltaTracker.getGameTimeDeltaPartialTick(false)
        ));
    }
}
