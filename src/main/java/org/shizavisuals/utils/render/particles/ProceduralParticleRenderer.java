package org.shizavisuals.utils.render.particles;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.post.PostFx;
import org.shizavisuals.utils.render.Render3DUtil;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;

public final class ProceduralParticleRenderer {
    private static final int INSTANCE_STRIDE = 16 * Float.BYTES;
    private static final int UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    private static final VertexFormat INSTANCE_FORMAT = VertexFormat.builder(1)
            .addAttribute("PosSize", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Dynamics", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Params", GpuFormat.RGBA32_FLOAT)
            .build();

    private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
            .withSampler("DepthSampler")
            .withUniform("ProceduralParticleUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/world/procedural_particles"))
            .withVertexShader(Identifier.parse("shizavisuals:core/procedural_particle"))
            .withFragmentShader(Identifier.parse("shizavisuals:core/procedural_particle"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(LAYOUT)
            .withColorTargetState(new ColorTargetState(
                    new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA)
            ))
            .withDepthStencilState(new DepthStencilState(
                    CompareOp.GREATER_THAN_OR_EQUAL,
                    false
            ))
            .withVertexBinding(0, INSTANCE_FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    private TextureTarget depthCopy;

    public record Sprite(
            Vec3 position,
            float halfSize,
            int color,
            Shape shape,
            float rotation,
            float normalizedLife,
            float seed,
            float phase
    ) {
    }

    public static RenderPipeline pipeline() {
        return PIPELINE;
    }

    public enum Shape {
        RANDOM("Random", -1, 0.4F, 0.5F),
        STAR("Stars", 0, 0.5F, 0.65F),
        DOLLAR("Dollars", 1, 0.3F, 0.2F),
        SNOWFLAKE("Snowflakes", 2, 0.25F, 0.15F),
        GLOW("Glow", 3, 1.0F, 0.9F),
        PUMPKIN("Pumpkins", 4, 0.2F, 0.0F),
        HEART("Hearts", 5, 0.35F, 0.2F),
        SPARK("Sparks", 6, 0.8F, 0.85F),
        EMBER("Embers", 7, 0.9F, 0.8F);

        private static final Shape[] CONCRETE = {
                STAR, DOLLAR, SNOWFLAKE, GLOW, PUMPKIN, HEART, SPARK, EMBER
        };

        private final String displayName;
        private final int shapeId;
        private final float additivity;
        private final float glow;

        Shape(String displayName, int shapeId, float additivity, float glow) {
            this.displayName = displayName;
            this.shapeId = shapeId;
            this.additivity = additivity;
            this.glow = glow;
        }

        public String displayName() {
            return this.displayName;
        }

        public Shape resolve(Random random) {
            return this == RANDOM ? CONCRETE[random.nextInt(CONCRETE.length)] : this;
        }

        public static Shape fromDisplayName(String value) {
            for (Shape shape : values()) {
                if (shape.displayName.equals(value)) {
                    return shape;
                }
            }
            return RANDOM;
        }

        public static String[] displayNames() {
            Shape[] values = values();
            String[] names = new String[values.length];
            for (int index = 0; index < values.length; index++) {
                names[index] = values[index].displayName;
            }
            return names;
        }
    }

    public void render(List<Sprite> sprites, float glow, boolean natural) {
        Minecraft minecraft = Minecraft.getInstance();
        if (sprites.isEmpty() || minecraft.level == null) {
            return;
        }

        var target = minecraft.gameRenderer.mainRenderTarget();
        if (target == null
                || target.getColorTextureView() == null
                || target.getDepthTexture() == null
                || target.getDepthTextureView() == null
                || target.width <= 0
                || target.height <= 0) {
            return;
        }

        ensureDepthCopy(target.width, target.height);
        if (this.depthCopy == null
                || this.depthCopy.getDepthTexture() == null
                || this.depthCopy.getDepthTextureView() == null) {
            return;
        }
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                target.getDepthTexture(),
                this.depthCopy.getDepthTexture(),
                0, 0, 0, 0, 0,
                target.width,
                target.height
        );

        ByteBuffer instances = buildInstances(minecraft, sprites);
        GpuBuffer instanceBuffer = null;
        GpuBuffer uniforms = null;
        try {
            instanceBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "ShizaVisuals Procedural Particle Instances",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    instances
            );
            uniforms = uploadUniforms(
                    target.width,
                    target.height,
                    Math.clamp(glow, 0.0F, 1.0F),
                    natural
            );
            GpuSampler depthSampler = RenderSystem.getSamplerCache()
                    .getClampToEdge(FilterMode.NEAREST);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "ShizaVisuals Procedural Particles",
                    target.getColorTextureView(),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(PIPELINE);
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                pass.setUniform("ProceduralParticleUniforms", uniforms);
                pass.bindTexture(
                        "DepthSampler",
                        this.depthCopy.getDepthTextureView(),
                        depthSampler
                );
                pass.setVertexBuffer(0, instanceBuffer.slice());
                pass.draw(6, sprites.size(), 0, 0);
            }
        } finally {
            MemoryUtil.memFree(instances);
            if (uniforms != null) {
                uniforms.close();
            }
            if (instanceBuffer != null) {
                instanceBuffer.close();
            }
        }
    }

    private ByteBuffer buildInstances(Minecraft minecraft, List<Sprite> sprites) {
        ByteBuffer data = MemoryUtil.memAlloc(sprites.size() * INSTANCE_STRIDE)
                .order(ByteOrder.nativeOrder());
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 cameraPosition = camera.position();
        Matrix4f viewPose = Render3DUtil.cameraViewPose(camera);
        for (Sprite sprite : sprites) {
            Vector4f position = Render3DUtil.toViewSpace(
                    sprite.position(),
                    cameraPosition,
                    viewPose
            );
            Shape shape = sprite.shape() == Shape.RANDOM ? Shape.STAR : sprite.shape();
            putVec4(data, position.x, position.y, position.z, sprite.halfSize());
            putVec4(
                    data,
                    sprite.rotation(),
                    Math.clamp(sprite.normalizedLife(), 0.0F, 1.0F),
                    sprite.seed(),
                    sprite.phase()
            );
            int color = sprite.color();
            putVec4(
                    data,
                    ColorUtil.red(color) / 255.0F,
                    ColorUtil.green(color) / 255.0F,
                    ColorUtil.blue(color) / 255.0F,
                    ColorUtil.alpha(color) / 255.0F
            );
            putVec4(data, shape.shapeId, shape.additivity, shape.glow, 0.0F);
        }
        return data.flip();
    }

    private GpuBuffer uploadUniforms(int width, int height, float glow, boolean natural) {
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "ShizaVisuals Procedural Particle UBO",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putVec4(PostFx.shaderTime(), glow, natural ? 1.0F : 0.0F, 0.0025F)
                    .putVec4(
                            width,
                            height,
                            1.0F,
                            0.0F
                    )
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }

    private void ensureDepthCopy(int width, int height) {
        if (this.depthCopy != null
                && this.depthCopy.width == width
                && this.depthCopy.height == height) {
            return;
        }
        if (this.depthCopy != null) {
            this.depthCopy.destroyBuffers();
        }
        this.depthCopy = new TextureTarget(
                "shizavisuals-procedural-particle-depth",
                width,
                height,
                true,
                GpuFormat.RGBA8_UNORM
        );
    }

    private static void putVec4(ByteBuffer buffer, float x, float y, float z, float w) {
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    public void release() {
        if (this.depthCopy != null) {
            this.depthCopy.destroyBuffers();
            this.depthCopy = null;
        }
    }
}
