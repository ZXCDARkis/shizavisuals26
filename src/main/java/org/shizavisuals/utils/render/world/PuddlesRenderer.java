package org.shizavisuals.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.shizavisuals.feature.impl.visual.PuddlesFeature;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.post.FullscreenQuad;
import org.shizavisuals.utils.render.post.PostFx;
import org.shizavisuals.utils.render.post.PostPipelines;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Wet floor with screen-space reflections, ported from GodWeer's puddles pass.
 *
 * <p>Depth is reconstructed into world space, only up-facing surfaces get the
 * treatment, an fbm mask decides where the puddles are, the wave normal adds
 * ripples and the view ray is ray-marched against the depth buffer so the floor
 * actually reflects what is on screen.
 */
public final class PuddlesRenderer {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private TextureTarget sceneCopy;
    private GpuBuffer uniforms;

    public void render(PuddlesFeature feature, CameraRenderState cameraState) {
        if (feature == null || cameraState == null || !cameraState.initialized) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        if (!valid(target)) {
            return;
        }
        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null || cameraState.viewRotationMatrix == null) {
            return;
        }

        ensureResources(target.width, target.height);
        if (this.sceneCopy == null || this.sceneCopy.getColorTexture() == null) {
            return;
        }

        Matrix4f invProjection = new Matrix4f(projection).invert();
        Matrix4f view = new Matrix4f(cameraState.viewRotationMatrix);
        Matrix4f invView = new Matrix4f(view).invert();

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                target.getColorTexture(),
                this.sceneCopy.getColorTexture(),
                0, 0, 0, 0, 0,
                target.width,
                target.height
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putMat4f(projection)
                    .putMat4f(invProjection)
                    .putMat4f(view)
                    .putMat4f(invView)
                    .putVec4((float) cameraState.pos.x(), (float) cameraState.pos.y(),
                            (float) cameraState.pos.z(), 1.0F)
                    .putVec4(PostFx.shaderTime(),
                            feature.coverage.getValue().floatValue(),
                            feature.waveSpeed.getValue().floatValue(),
                            feature.reflectivity.getValue().floatValue())
                    .putVec4(feature.scale.getValue().floatValue(),
                            feature.waveStrength.getValue().floatValue(),
                            1.0F / target.width,
                            1.0F / target.height)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniforms.slice(), data);
        }

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "ShizaVisuals Puddles", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(PostPipelines.WORLD_PUDDLES);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WorldPuddlesUniforms", this.uniforms);
            pass.bindTexture("SceneSampler", this.sceneCopy.getColorTextureView(), linear);
            pass.bindTexture("DepthSampler", target.getDepthTextureView(), nearest);
            pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
            pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
        }
    }

    private void ensureResources(int width, int height) {
        if (this.sceneCopy != null
                && this.sceneCopy.width == width
                && this.sceneCopy.height == height) {
            return;
        }
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
        }
        this.sceneCopy = new TextureTarget(
                "shizavisuals-puddles-scene",
                width,
                height,
                false,
                PostPipelines.EFFECT_FORMAT
        );
        if (this.uniforms == null) {
            this.uniforms = PostFx.createUniforms("shizavisuals-puddles", UNIFORM_SIZE);
        }
    }

    private static boolean valid(RenderTarget target) {
        return target != null
                && target.width > 0
                && target.height > 0
                && target.getColorTexture() != null
                && target.getColorTextureView() != null
                && target.getDepthTexture() != null
                && target.getDepthTextureView() != null;
    }

    public void release() {
        if (this.sceneCopy != null) {
            this.sceneCopy.destroyBuffers();
            this.sceneCopy = null;
        }
        if (this.uniforms != null) {
            this.uniforms.close();
            this.uniforms = null;
        }
    }
}