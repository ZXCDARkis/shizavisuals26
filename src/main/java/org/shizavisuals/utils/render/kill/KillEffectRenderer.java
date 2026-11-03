package org.shizavisuals.utils.render.kill;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.post.PostFx;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * ShaderEffect for kill effects (portal sphere / tornado beam).
 *
 * <p>Renders a single camera-facing billboard quad with one of the
 * {@code kill_effect_*} core shaders (portal / tornado / nova / souls / beam).
 * Uniform layout mirrors the original {@code BeamData} block:
 * {@code params = (uTime, uProgress, uIntensity, uSize)}, {@code effectColor = uColor}.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * KillEffectRenderer fx = new KillEffectRenderer();
 * // each frame while the effect is alive:
 * float progress = KillEffectRenderer.progress(now, spawnedAt, lifetimeMs);
 * fx.shaderEffect(KillEffectRenderer.Kind.PORTAL, victimPos.add(0, 1.0, 0),
 *     1.2F, progress, PostFx.shaderTime(), 0xFF7A5CFF, 1.0F);
 * }</pre></p>
 */
public final class KillEffectRenderer {
    public enum Kind {
        PORTAL("kill_effect_portal"),
        TORNADO("kill_effect_tornado"),
        NOVA("kill_effect_nova"),
        SOULS("kill_effect_souls"),
        BEAM("kill_effect_beam");

        private final String shader;

        Kind(String shader) {
            this.shader = shader;
        }
    }

    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4() // params: uTime, uProgress, uIntensity, uSize
            .putVec4() // effectColor: uColor rgba
            .get();

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withUniform("KillEffectUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static RenderPipeline pipeline(Kind kind) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/" + kind.shader))
                .withVertexShader(Identifier.parse("shizavisuals:core/" + kind.shader))
                .withFragmentShader(Identifier.parse("shizavisuals:core/" + kind.shader))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(LAYOUT)
                .withColorTargetState(new ColorTargetState(
                        new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static final java.util.Map<Kind, RenderPipeline> PIPELINES = buildPipelines();

    private static java.util.Map<Kind, RenderPipeline> buildPipelines() {
        java.util.Map<Kind, RenderPipeline> map = new java.util.EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            map.put(kind, pipeline(kind));
        }
        return java.util.Map.copyOf(map);
    }

    public static java.util.List<RenderPipeline> pipelines() {
        return java.util.List.copyOf(PIPELINES.values());
    }

    /**
     * Main ShaderEffect function.
     *
     * @param kind      PORTAL (expanding sphere) or TORNADO (vertical beam)
     * @param worldPos  effect center in world space
     * @param halfSize  half of the billboard quad side in blocks (maps to uSize)
     * @param progress  0..1 lifecycle (maps to uProgress; appear 0..0.1, vanish 0.7..1.0)
     * @param time      seconds, drives animation (maps to uTime, use {@link PostFx#shaderTime()})
     * @param color     ARGB, maps to uColor + vertex alpha
     * @param intensity brightness multiplier (maps to uIntensity, 1.0 = normal)
     */
    public void shaderEffect(Kind kind, Vec3 worldPos, float halfSize,
                             float progress, float time, int color, float intensity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || halfSize <= 0.001F || intensity <= 0.001F) {
            return;
        }
        float clampedProgress = Math.clamp(progress, 0.0F, 1.0F);
        if (clampedProgress >= 1.0F) {
            return;
        }

        var target = mc.gameRenderer.mainRenderTarget();
        GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
        if (colorView == null) {
            return;
        }

        var device = RenderSystem.getDevice();
        MeshData meshData = buildBillboard(mc, worldPos, halfSize, color);
        GpuBuffer vertexBuffer = null;
        GpuBuffer uniformBuffer = null;
        try {
            vertexBuffer = device.createBuffer(
                    () -> "ShizaVisuals Kill Effect Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    meshData.vertexBuffer());
            uniformBuffer = uploadUniform(time, clampedProgress, intensity, halfSize, color);

            GpuTextureView depthView = target.getDepthTextureView();
            RenderPass pass = depthView != null
                    ? device.createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals Kill Effect Pass", colorView, Optional.empty(), depthView, OptionalDouble.empty())
                    : device.createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals Kill Effect Pass", colorView, Optional.empty());
            try {
                pass.setPipeline(PIPELINES.get(kind));
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("KillEffectUniforms", uniformBuffer);
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.draw(6, 1, 0, 0);
            } finally {
                pass.close();
            }
        } finally {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            meshData.close();
        }
    }

    /** Convenience overload using {@link PostFx#shaderTime()} as the clock. */
    public void shaderEffect(Kind kind, Vec3 worldPos, float halfSize,
                             float progress, int color, float intensity) {
        shaderEffect(kind, worldPos, halfSize, progress, PostFx.shaderTime(), color, intensity);
    }

    /** 0..1 lifetime progress from timestamps. */
    public static float progress(long nowMs, long spawnedAtMs, long lifetimeMs) {
        if (lifetimeMs <= 0L) {
            return 1.0F;
        }
        return Math.clamp((nowMs - spawnedAtMs) / (float) lifetimeMs, 0.0F, 1.0F);
    }

    private MeshData buildBillboard(Minecraft mc, Vec3 worldPos, float halfSize, int color) {
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Matrix4f pose = Render3DUtil.cameraViewPose(camera);
        Vector4f center = Render3DUtil.toViewSpace(worldPos, cameraPos, pose);

        BufferBuilder builder = new BufferBuilder(
                ByteBufferBuilder.exactlySized(6 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize()),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR);

        // Two triangles, UV 0..1 across the quad, constant color (alpha feeds vertexColor.a).
        addVertex(builder, center, -halfSize, -halfSize, 0.0F, 0.0F, color);
        addVertex(builder, center, -halfSize, halfSize, 0.0F, 1.0F, color);
        addVertex(builder, center, halfSize, halfSize, 1.0F, 1.0F, color);
        addVertex(builder, center, -halfSize, -halfSize, 0.0F, 0.0F, color);
        addVertex(builder, center, halfSize, halfSize, 1.0F, 1.0F, color);
        addVertex(builder, center, halfSize, -halfSize, 1.0F, 0.0F, color);
        return builder.buildOrThrow();
    }

    private void addVertex(BufferBuilder builder, Vector4f center,
                           float ox, float oy, float u, float v, int color) {
        builder.addVertex(center.x + ox, center.y + oy, center.z).setUv(u, v).setColor(color);
    }

    private GpuBuffer uploadUniform(float time, float progress, float intensity, float size, int color) {
        var device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(
                () -> "ShizaVisuals Kill Effect UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putVec4(time, progress, intensity, size)
                    .putVec4(
                            ColorUtil.red(color) / 255.0F,
                            ColorUtil.green(color) / 255.0F,
                            ColorUtil.blue(color) / 255.0F,
                            ColorUtil.alpha(color) / 255.0F)
                    .get();
            device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }
}
