package org.shizavisuals.webpanel;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

public final class WebPanelPipelines {
    private WebPanelPipelines() {
    }

    public static final int GLASS_UBO_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putFloat()
            .putFloat()
            .putVec4()
            .putVec2()
            .putFloat()
            .putFloat()
            .putVec4()
            .get();

    public static final int SDF_UBO_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putFloat()
            .putVec4()
            .get();

    public static final int TEXT_UBO_SIZE = new Std140SizeCalculator()
            .putFloat()
            .putFloat()
            .get();

    private static final BindGroupLayout GLASS_LAYOUT = BindGroupLayout.builder()
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withUniform("WebPanelGlass", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout SDF_LAYOUT = BindGroupLayout.builder()
            .withUniform("WebPanelSdf", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout TEXT_LAYOUT = BindGroupLayout.builder()
            .withSampler("Sampler0")
            .withUniform("WebPanelText", UniformType.UNIFORM_BUFFER)
            .build();

    private static final RenderPipeline.Builder glassBuilder(String location) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse(location))
                .withVertexShader(Identifier.parse("shizavisuals:core/webpanel_glass"))
                .withFragmentShader(Identifier.parse("shizavisuals:core/webpanel_glass"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(GLASS_LAYOUT)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false);
    }

    public static final RenderPipeline GLASS = glassBuilder("shizavisuals:pipeline/webpanel/glass")
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .build();

    private static final RenderPipeline.Builder sdfBuilder(String location,
                                                            DepthStencilState depth) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.parse(location))
                .withVertexShader(Identifier.parse("shizavisuals:core/webpanel_sdf"))
                .withFragmentShader(Identifier.parse("shizavisuals:core/webpanel_sdf"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(SDF_LAYOUT)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false);
        if (depth != null) {
            builder.withDepthStencilState(depth);
        } else {
            builder.withDepthStencilState(Optional.<DepthStencilState>empty());
        }
        return builder;
    }

    public static final RenderPipeline SDF_DEPTH = sdfBuilder(
            "shizavisuals:pipeline/webpanel/sdf_depth",
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)).build();

    public static final RenderPipeline SDF_OVERLAY = sdfBuilder(
            "shizavisuals:pipeline/webpanel/sdf_overlay", null).build();

    public static final RenderPipeline TEXT = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/webpanel/text"))
            .withVertexShader(Identifier.parse("shizavisuals:core/webpanel_text"))
            .withFragmentShader(Identifier.parse("shizavisuals:core/webpanel_text"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(TEXT_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static java.util.List<RenderPipeline> pipelines() {
        return java.util.List.of(GLASS, SDF_DEPTH, SDF_OVERLAY, TEXT);
    }

    public static GpuBuffer createUniforms(String label, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                Math.max(size, 16)
        );
    }

    public static void writeUniforms(GpuBuffer buffer, int size, Consumer<Std140Builder> writer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder builder = Std140Builder.onStack(stack, Math.max(size, 16));
            writer.accept(builder);
            ByteBuffer data = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }

    public static void drawWorld(String label,
                                  RenderPipeline pipeline,
                                  MeshData mesh,
                                  Consumer<RenderPass> setup) {
        Minecraft mc = Minecraft.getInstance();
        var target = mc.gameRenderer != null ? mc.gameRenderer.mainRenderTarget() : null;
        GpuTextureView colorView = target != null ? target.getColorTextureView() : null;
        if (colorView == null) {
            mesh.close();
            return;
        }

        var device = RenderSystem.getDevice();
        GpuBuffer vertexBuffer = null;
        try {
            vertexBuffer = device.createBuffer(
                    () -> "ShizaVisuals WebPanel Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    mesh.vertexBuffer()
            );

            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuTextureView depthView = target.getDepthTextureView();
            RenderPass pass = depthView != null
                    ? device.createCommandEncoder().createRenderPass(
                    () -> label, colorView, Optional.empty(), depthView, OptionalDouble.empty())
                    : device.createCommandEncoder().createRenderPass(
                    () -> label, colorView, Optional.empty());
            try {
                pass.setPipeline(pipeline);
                pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
                setup.accept(pass);
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.draw(mesh.drawState().vertexCount(), 1, 0, 0);
            } finally {
                pass.close();
            }
        } finally {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            mesh.close();
        }
    }

    public static BufferBuilder builder(int vertices, VertexFormat format) {
        return new BufferBuilder(
                ByteBufferBuilder.exactlySized(vertices * format.getVertexSize()),
                PrimitiveTopology.TRIANGLES,
                format
        );
    }
}
