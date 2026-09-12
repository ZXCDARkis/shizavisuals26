package org.shizavisuals.utils.render.particles;

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
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.utils.render.Render3DUtil;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.shizavisuals.utils.render.Textures;

public final class WorldParticleRenderer {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withSampler("BloomSampler")
            .withUniform("WorldParticleUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/world/world_particle"))
            .withVertexShader(Identifier.parse("shizavisuals:core/world_particle"))
            .withFragmentShader(Identifier.parse("shizavisuals:core/world_particle"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline THROUGH_WALLS_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/world/world_particle_through_walls"))
            .withVertexShader(Identifier.parse("shizavisuals:core/world_particle"))
            .withFragmentShader(Identifier.parse("shizavisuals:core/world_particle"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline ALPHA_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/world/world_particle_alpha"))
            .withVertexShader(Identifier.parse("shizavisuals:core/world_particle"))
            .withFragmentShader(Identifier.parse("shizavisuals:core/world_particle"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private static final RenderPipeline ALPHA_THROUGH_WALLS_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/world/world_particle_alpha_through_walls"))
            .withVertexShader(Identifier.parse("shizavisuals:core/world_particle"))
            .withFragmentShader(Identifier.parse("shizavisuals:core/world_particle"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public record Sprite(Vec3 position, float halfSize, int color) {
    }

    public void render(List<Sprite> sprites, float brightness) {
        render(sprites, brightness, false, true);
    }

    public void render(List<Sprite> sprites, float brightness, boolean throughWalls) {
        render(sprites, brightness, throughWalls, true);
    }

    public void render(List<Sprite> sprites, float brightness, boolean throughWalls, boolean additive) {
        Minecraft mc = Minecraft.getInstance();
        if (sprites.isEmpty() || mc.level == null) {
            return;
        }

        var target = mc.gameRenderer.mainRenderTarget();
        GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
        if (colorView == null) {
            return;
        }

        AbstractTexture bloom = mc.getTextureManager().getTexture(Textures.Shader.BLOOM);
        GpuTextureView bloomView = bloom != null ? bloom.getTextureView() : null;
        if (bloomView == null) {
            return;
        }

        MeshData meshData = buildMesh(mc, sprites);
        if (meshData == null) {
            return;
        }

        var device = RenderSystem.getDevice();
        GpuBuffer vertexBuffer = null;
        GpuBuffer uniformBuffer = null;
        try {
            vertexBuffer = device.createBuffer(
                    () -> "ShizaVisuals World Particles Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    meshData.vertexBuffer()
            );
            uniformBuffer = uploadUniform(brightness);

            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuTextureView depthView = target.getDepthTextureView();
            RenderPass pass = !throughWalls && depthView != null
                    ? device.createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals World Particles Pass", colorView, Optional.empty(), depthView, OptionalDouble.empty())
                    : device.createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals World Particles Pass", colorView, Optional.empty());
            try {
                RenderPipeline pipeline = additive
                        ? (throughWalls ? THROUGH_WALLS_PIPELINE : PIPELINE)
                        : (throughWalls ? ALPHA_THROUGH_WALLS_PIPELINE : ALPHA_PIPELINE);
                pass.setPipeline(pipeline);
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("WorldParticleUniforms", uniformBuffer);
                pass.bindTexture("BloomSampler", bloomView, sampler);
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.draw(sprites.size() * 6, 1, 0, 0);
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

    private GpuBuffer uploadUniform(float brightness) {
        var device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(
                () -> "ShizaVisuals World Particles UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putVec4(brightness, 0.0F, 0.0F, 0.0F)
                    .get();
            device.createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }

    private MeshData buildMesh(Minecraft mc, List<Sprite> sprites) {
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Matrix4f pose = Render3DUtil.cameraViewPose(camera);

        BufferBuilder builder = new BufferBuilder(
                ByteBufferBuilder.exactlySized(sprites.size() * 6 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize()),
                PrimitiveTopology.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR
        );
        for (Sprite sprite : sprites) {

            Vector4f center = Render3DUtil.toViewSpace(sprite.position(), cameraPos, pose);
            float half = sprite.halfSize();
            int color = sprite.color();

            addVertex(builder, center, -half, -half, 0.0F, 0.0F, color);
            addVertex(builder, center, -half, half, 0.0F, 1.0F, color);
            addVertex(builder, center, half, half, 1.0F, 1.0F, color);
            addVertex(builder, center, -half, -half, 0.0F, 0.0F, color);
            addVertex(builder, center, half, half, 1.0F, 1.0F, color);
            addVertex(builder, center, half, -half, 1.0F, 0.0F, color);
        }
        return builder.build();
    }

    private void addVertex(BufferBuilder builder, Vector4f center, float dx, float dy, float u, float v, int color) {
        builder.addVertex(center.x + dx, center.y + dy, center.z)
                .setUv(u, v)
                .setColor(color);
    }
}
