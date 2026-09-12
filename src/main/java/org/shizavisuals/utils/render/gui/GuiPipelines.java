package org.shizavisuals.utils.render.gui;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class GuiPipelines {
    public static final RenderPipeline RECT = builder("rect", "rect", false).build();
    public static final RenderPipeline TEXT = builder("text", "text", true).build();
    public static final RenderPipeline TEXTURE = builder("texture", "texture", true).build();

    public static final RenderPipeline GLASS_SHADOW = builder("glass_shadow", "rect", false).build();
    public static final RenderPipeline BLUR_RECT = builder("blur_rect", "blur_rect", true).build();
    public static final RenderPipeline COLOR_GRID = builder("color_grid", "color_grid", true).build();

    public static final RenderPipeline MENU_BACKGROUND = builder("menu_background", "menu_background", false).build();

    private static final BindGroupLayout GUI_BLUR_LAYOUT = BindGroupLayout.builder()
            .withSampler("CurrentInput")
            .withUniform("GuiKawaseUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline GUI_BLUR_DOWN = blurBuilder("gui_blur_down", "gui_kawase_down").build();
    public static final RenderPipeline GUI_BLUR_UP = blurBuilder("gui_blur_up", "gui_kawase_up").build();

    private static final RenderPipeline[] ITEM_DOWNSCALE = {
            itemDownscalePipeline(1),
            itemDownscalePipeline(2),
            itemDownscalePipeline(3),
            itemDownscalePipeline(4),
            itemDownscalePipeline(5),
            itemDownscalePipeline(6),
            itemDownscalePipeline(7),
            itemDownscalePipeline(8)
    };

    public static RenderPipeline itemDownscale(int guiScale) {
        return ITEM_DOWNSCALE[Math.clamp(guiScale, 1, ITEM_DOWNSCALE.length) - 1];
    }

    public static RenderPipeline[] itemDownscaleVariants() {
        return ITEM_DOWNSCALE.clone();
    }

    private static RenderPipeline itemDownscalePipeline(int texelSize) {
        return RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                .withLocation(Identifier.parse("shizavisuals:gui/item_downscale_" + texelSize))
                .withVertexShader(Identifier.parse("shizavisuals:core/item_downscale"))
                .withFragmentShader(Identifier.parse("shizavisuals:core/item_downscale"))
                .withShaderDefine("TEXEL_SIZE", texelSize)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .build();
    }

    private static RenderPipeline.Builder blurBuilder(String name, String fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:gui/" + name))
                .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
                .withFragmentShader(Identifier.parse("shizavisuals:post/blurs/" + fragmentShader))
                .withBindGroupLayout(GUI_BLUR_LAYOUT)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false);
    }

    private static RenderPipeline.Builder builder(String name, String shader, boolean sampled) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(Identifier.parse("shizavisuals:gui/" + name))
                .withVertexShader(Identifier.parse("shizavisuals:core/" + shader))
                .withFragmentShader(Identifier.parse("shizavisuals:core/" + shader))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withVertexBinding(0, UiVertexFormats.UI)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false);
        if (sampled) {
            builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0);
        }
        return builder;
    }

    private GuiPipelines() {
    }
}
