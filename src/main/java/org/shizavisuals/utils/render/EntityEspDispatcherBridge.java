package org.shizavisuals.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public interface EntityEspDispatcherBridge {
    <S extends EntityRenderState> void submitForGlow(
            S state,
            CameraRenderState cameraState,
            double x,
            double y,
            double z,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector
    );
}
