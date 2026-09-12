package org.shizavisuals.utils.render.esp;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Model-surface scan pipeline, ported from the Delta TargetESP scan reference. */
public final class TargetScanRenderTypes {
    private static final Map<Identifier, RenderType> CACHE = new HashMap<>();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/world/target_esp_scan"))
            .withVertexShader(Identifier.parse("shizavisuals:core/target_esp_scan"))
            .withFragmentShader(Identifier.parse("shizavisuals:core/target_esp_scan"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    private TargetScanRenderTypes() {
    }

    public static RenderType scan(Identifier texture) {
        return CACHE.computeIfAbsent(texture, tex -> {
            RenderSetup setup = RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", tex)
                    .createRenderSetup();
            return RenderType.create("shizavisuals_target_esp_scan", setup);
        });
    }
}
