package org.shizavisuals.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.utils.render.EntityEspDispatcherBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements EntityEspDispatcherBridge {
    @Shadow
    public abstract <S extends EntityRenderState> EntityRenderer<?, ? super S> getRenderer(S state);

    @Override
    @Unique
    public <S extends EntityRenderState> void submitForGlow(S state,
                                                                  CameraRenderState cameraState,
                                                                  double x,
                                                                  double y,
                                                                  double z,
                                                                  PoseStack poseStack,
                                                                  SubmitNodeCollector submitNodeCollector) {
        EntityRenderer<?, ? super S> renderer = this.getRenderer(state);
        Vec3 renderOffset = renderer.getRenderOffset(state);

        poseStack.pushPose();
        poseStack.translate(x + renderOffset.x(), y + renderOffset.y(), z + renderOffset.z());

        var nameTag = state.nameTag;
        state.nameTag = null;
        try {
            renderer.submit(state, poseStack, submitNodeCollector, cameraState);
        } finally {
            state.nameTag = nameTag;
            poseStack.popPose();
        }
    }
}
