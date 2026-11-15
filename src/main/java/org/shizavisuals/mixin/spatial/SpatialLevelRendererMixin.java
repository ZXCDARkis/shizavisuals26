package org.shizavisuals.mixin.spatial;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.shizavisuals.utils.render.spatial.SpatialInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Захват проекции и ориентации камеры в конце рендера мира. */
@Mixin(LevelRenderer.class)
public class SpatialLevelRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void shizavisuals$capturePerspective(GraphicsResourceAllocator allocator,
                                                 DeltaTracker deltaTracker,
                                                 boolean renderBlockOutline,
                                                 CameraRenderState cameraRenderState,
                                                 Matrix4fc projectionMatrix,
                                                 GpuBufferSlice fogParameters,
                                                 Vector4f skyColor,
                                                 boolean hasCapturedFrustum,
                                                 CallbackInfo ci) {
        if (SpatialInventory.shouldCapture() || SpatialInventory.isClosing()) {
            PoseStack poseStack = new PoseStack();
            Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            poseStack.mulPose(new Quaternionf()
                    .rotateX((float) Math.toRadians(camera.xRot()))
                    .rotateY((float) Math.toRadians(camera.yRot() + 180.0F))
                    .get(new Matrix4f()));
            SpatialInventory.capturePerspectiveState(
                    RenderSystem.getProjectionMatrixBuffer(),
                    RenderSystem.getProjectionType(),
                    poseStack);
        }
    }
}
