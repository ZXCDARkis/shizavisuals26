package org.shizavisuals.utils.render.esp;

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

/**
 * Camera-facing textured quads in world space. Used by TargetESP modes
 * (soft glow sprites, spinning velvet billboard, soul particles).
 */
public final class EspSpriteRenderer {
    private static final int UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withSampler("EspSpriteSampler")
            .withUniform("EspSpriteUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static RenderPipeline pipeline(String idSuffix, boolean additive, boolean throughWalls) {
        com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/esp_sprite_" + idSuffix))
                .withVertexShader(Identifier.parse("shizavisuals:core/esp_sprite"))
                .withFragmentShader(Identifier.parse("shizavisuals:core/esp_sprite"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(LAYOUT)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false);
        if (additive) {
            builder.withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)));
        } else {
            builder.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        }
        if (throughWalls) {
            builder.withDepthStencilState(Optional.<DepthStencilState>empty());
        } else {
            builder.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
        }
        return builder.build();
    }

    private static final RenderPipeline ADDITIVE = pipeline("additive", true, false);
    private static final RenderPipeline ADDITIVE_THROUGH = pipeline("additive_through", true, true);
    private static final RenderPipeline ALPHA = pipeline("alpha", false, false);
    private static final RenderPipeline ALPHA_THROUGH = pipeline("alpha_through", false, true);

    public static List<RenderPipeline> pipelines() {
        return List.of(ADDITIVE, ADDITIVE_THROUGH, ALPHA, ALPHA_THROUGH, MODEL);
    }

    /** Shared pipelines; Spirits reuses the same additive/alpha sprite shader. */
    public static RenderPipeline additivePipeline() {
        return ADDITIVE;
    }

    public static RenderPipeline additiveThroughPipeline() {
        return ADDITIVE_THROUGH;
    }

    public static RenderPipeline alphaPipeline() {
        return ALPHA;
    }

    /**
     * Opaque-ish textured model pass (pets): TRANS LUCENT blend but with real
     * depth test and depth writes, so a solid model does not paint itself over
     * its own far side. QUADS topology pairs with the sequential index buffer.
     */
    private static final RenderPipeline MODEL = pipelineModel();

    private static RenderPipeline pipelineModel() {
        com.mojang.blaze3d.pipeline.RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/esp_sprite_model"))
                .withVertexShader(Identifier.parse("shizavisuals:core/esp_sprite"))
                .withFragmentShader(Identifier.parse("shizavisuals:core/esp_sprite"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(LAYOUT)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true));
        return builder.build();
    }

    public static RenderPipeline modelPipeline() {
        return MODEL;
    }

    public record Sprite(Vec3 position, float halfSize, int color, float rotation) {
        public Sprite(Vec3 position, float halfSize, int color) {
            this(position, halfSize, color, 0.0F);
        }
    }

    private final Identifier texture;

    public EspSpriteRenderer(Identifier texture) {
        this.texture = texture;
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

        AbstractTexture tex = mc.getTextureManager().getTexture(this.texture);
        GpuTextureView texView = tex != null ? tex.getTextureView() : null;
        if (texView == null) {
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
                    () -> "ShizaVisuals ESP Sprites Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    meshData.vertexBuffer()
            );
            uniformBuffer = uploadUniform(brightness);

            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuTextureView depthView = target.getDepthTextureView();
            RenderPass pass = !throughWalls && depthView != null
                    ? device.createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals ESP Sprites Pass", colorView, Optional.empty(), depthView, OptionalDouble.empty())
                    : device.createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals ESP Sprites Pass", colorView, Optional.empty());
            try {
                RenderPipeline selected = additive
                        ? (throughWalls ? ADDITIVE_THROUGH : ADDITIVE)
                        : (throughWalls ? ALPHA_THROUGH : ALPHA);
                pass.setPipeline(selected);
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("EspSpriteUniforms", uniformBuffer);
                pass.bindTexture("EspSpriteSampler", texView, sampler);
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
                () -> "ShizaVisuals ESP Sprites UBO",
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
            float rotation = sprite.rotation();
            float cos = (float) Math.cos(rotation);
            float sin = (float) Math.sin(rotation);

            float corner00X = -half * cos + half * sin;
            float corner00Y = -half * sin - half * cos;
            float corner01X = -half * cos - half * sin;
            float corner01Y = -half * sin + half * cos;
            float corner11X = half * cos - half * sin;
            float corner11Y = half * sin + half * cos;
            float corner10X = half * cos + half * sin;
            float corner10Y = half * sin - half * cos;

            builder.addVertex(center.x + corner00X, center.y + corner00Y, center.z).setUv(0.0F, 0.0F).setColor(color);
            builder.addVertex(center.x + corner01X, center.y + corner01Y, center.z).setUv(0.0F, 1.0F).setColor(color);
            builder.addVertex(center.x + corner11X, center.y + corner11Y, center.z).setUv(1.0F, 1.0F).setColor(color);
            builder.addVertex(center.x + corner00X, center.y + corner00Y, center.z).setUv(0.0F, 0.0F).setColor(color);
            builder.addVertex(center.x + corner11X, center.y + corner11Y, center.z).setUv(1.0F, 1.0F).setColor(color);
            builder.addVertex(center.x + corner10X, center.y + corner10Y, center.z).setUv(1.0F, 0.0F).setColor(color);
        }
        return builder.build();
    }
}
