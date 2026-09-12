package org.shizavisuals.utils.render.world;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;

public record WorldEffectContext(
        LevelRenderState levelRenderState,
        CameraRenderState cameraRenderState,
        Matrix4fc projectionMatrix,
        float tickDelta
) {
}
