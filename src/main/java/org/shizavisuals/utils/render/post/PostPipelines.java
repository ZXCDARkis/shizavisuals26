package org.shizavisuals.utils.render.post;

import com.mojang.blaze3d.GpuFormat;
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

public final class PostPipelines {
    private static final BindGroupLayout KAWASE_DOWN_LAYOUT = BindGroupLayout.builder()
            .withSampler("CurrentInput")
            .withUniform("KawaseDownUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout KAWASE_UP_LAYOUT = BindGroupLayout.builder()
            .withSampler("CurrentInput")
            .withUniform("KawaseUpUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout HAND_MASK_LAYOUT = BindGroupLayout.builder()
            .withSampler("BeforeTexture")
            .withSampler("AfterTexture")
            .withSampler("BeforeDepth")
            .withSampler("AfterDepth")
            .withUniform("HandMaskUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout SHADER_HANDS_LAYOUT = BindGroupLayout.builder()
            .withSampler("BlurredSampler")
            .withSampler("MaskSampler")
            .withUniform("HandCompositeUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout HAND_FILL_LAYOUT = BindGroupLayout.builder()
            .withSampler("MaskSampler")
            .withUniform("HandFillUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout HAND_GLASS_LAYOUT = BindGroupLayout.builder()
            .withSampler("SceneSampler")
            .withSampler("MaskSampler")
            .withUniform("HandGlassUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout HAND_OUTLINE_LAYOUT = BindGroupLayout.builder()
            .withSampler("MaskSampler")
            .withUniform("HandOutlineUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout HAND_HALO_LAYOUT = BindGroupLayout.builder()
            .withSampler("BlurredSampler")
            .withSampler("MaskSampler")
            .withUniform("HandHaloUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout HAND_TRAIL_LAYOUT = BindGroupLayout.builder()
            .withSampler("PrevSampler")
            .withSampler("InjectSampler")
            .withUniform("HandTrailUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout HAND_BLUR_LAYOUT = BindGroupLayout.builder()
            .withSampler("CurrentInput")
            .withUniform("HandBlurUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout POINT_LIGHT_LAYOUT = BindGroupLayout.builder()
            .withSampler("SceneSampler")
            .withSampler("DepthSampler")
            .withUniform("PointLightUniforms", UniformType.UNIFORM_BUFFER)
            .withUniform("PointLights", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout BLOCK_OUTLINE_LAYOUT = BindGroupLayout.builder()
            .withUniform("BlockOutlineTransform", UniformType.UNIFORM_BUFFER)
            .withUniform("BlockOutlineStyle", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout WORLD_SKY_CLOUDS_LAYOUT = BindGroupLayout.builder()
            .withSampler("DepthSampler")
            .withUniform("WorldSkyUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout WORLD_SKY_LAYOUT = BindGroupLayout.builder()
            .withSampler("DepthSampler")
            .withSampler("CloudSampler")
            .withUniform("WorldSkyUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout HAND_MASK_SMOOTH_LAYOUT = BindGroupLayout.builder()
            .withSampler("RawMask")
            .withUniform("HandMaskUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout SATURATION_LAYOUT = BindGroupLayout.builder()
            .withSampler("SceneSampler")
            .withUniform("SaturationUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout MOTION_BLUR_LAYOUT = BindGroupLayout.builder()
            .withSampler("SceneSampler")
            .withSampler("DepthSampler")
            .withUniform("MotionBlurUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout PUDDLES_LAYOUT = BindGroupLayout.builder()
            .withSampler("SceneSampler")
            .withSampler("DepthSampler")
            .withUniform("WorldPuddlesUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout CHAMS_MASK_STYLE_LAYOUT = BindGroupLayout.builder()
            .withSampler("MaskSampler")
            .withUniform("ChamsStyle", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout CHAMS_GLASS_LAYOUT = BindGroupLayout.builder()
            .withSampler("SceneSampler")
            .withSampler("MaskSampler")
            .withUniform("ChamsStyle", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout CHAMS_BLURRED_LAYOUT = BindGroupLayout.builder()
            .withSampler("BlurredSampler")
            .withSampler("MaskSampler")
            .withUniform("ChamsStyle", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout POPCHAMS_COMPOSITE_LAYOUT = BindGroupLayout.builder()
            .withSampler("BlurredSampler")
            .withUniform("PopChamsComposite", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout TNT_SHOCKWAVE_LAYOUT = BindGroupLayout.builder()
            .withSampler("SceneSampler")
            .withUniform("TntShockwaveUniforms", UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline ESP_KAWASE_DOWN = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/blurs/kawase_down"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/blurs/esp_kawase_down"))
            .withBindGroupLayout(KAWASE_DOWN_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline ESP_KAWASE_UP = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/blurs/kawase_up"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/blurs/esp_kawase_up"))
            .withBindGroupLayout(KAWASE_UP_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final GpuFormat MASK_RAW_FORMAT = GpuFormat.RGBA16_FLOAT;

    public static final RenderPipeline HAND_MASK = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_mask"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_mask"))
            .withBindGroupLayout(HAND_MASK_LAYOUT)
            .withColorTargetState(new ColorTargetState(Optional.empty(), MASK_RAW_FORMAT, ColorTargetState.WRITE_ALL))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_MASK_SMOOTH = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_mask_smooth"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_mask_smooth"))
            .withBindGroupLayout(HAND_MASK_SMOOTH_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline SHADER_HANDS = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/shader_hands"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/shader_hands"))
            .withBindGroupLayout(SHADER_HANDS_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_FILL = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_fill"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_fill"))
            .withBindGroupLayout(HAND_FILL_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_PLASMA = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_plasma"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_plasma"))
            .withBindGroupLayout(HAND_FILL_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_GLASS = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_glass"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_glass"))
            .withBindGroupLayout(HAND_GLASS_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_OUTLINE = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_outline"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_outline"))
            .withBindGroupLayout(HAND_OUTLINE_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_HALO = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_halo"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_halo"))
            .withBindGroupLayout(HAND_HALO_LAYOUT)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_BLUR_DOWN = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_blur_down"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_blur_down"))
            .withBindGroupLayout(HAND_BLUR_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_BLUR_UP = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_blur_up"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_blur_up"))
            .withBindGroupLayout(HAND_BLUR_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline HAND_TRAIL = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/hand_trail"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/hand_trail"))
            .withBindGroupLayout(HAND_TRAIL_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline POINT_LIGHTS = RenderPipeline.builder()
            .withLocation(Identifier.parse("shizavisuals:pipeline/post/point_lights"))
            .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
            .withFragmentShader(Identifier.parse("shizavisuals:post/point_lights"))
            .withBindGroupLayout(POINT_LIGHT_LAYOUT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final RenderPipeline BLOCK_OUTLINE_CLASSIC = blockOutlinePipeline(
            "classic", "classic", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_OUTLINE_CLASSIC_THROUGH = blockOutlinePipeline(
            "classic_through", "classic", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline BLOCK_OUTLINE_CAUSTICS = blockOutlinePipeline(
            "caustics", "caustics", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_OUTLINE_CAUSTICS_THROUGH = blockOutlinePipeline(
            "caustics_through", "caustics", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline BLOCK_OUTLINE_PRISMATIC = blockOutlinePipeline(
            "prismatic", "prismatic", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_OUTLINE_PRISMATIC_THROUGH = blockOutlinePipeline(
            "prismatic_through", "prismatic", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline BLOCK_OUTLINE_GLOSSY = blockOutlinePipeline(
            "glossy", "glossy", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_OUTLINE_GLOSSY_THROUGH = blockOutlinePipeline(
            "glossy_through", "glossy", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline BLOCK_OUTLINE_DEEP_SPACE = blockOutlinePipeline(
            "deep_space", "deep_space", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_OUTLINE_DEEP_SPACE_THROUGH = blockOutlinePipeline(
            "deep_space_through", "deep_space", CompareOp.ALWAYS_PASS);
    public static final RenderPipeline BLOCK_OUTLINE_NEBULA = blockOutlinePipeline(
            "nebula", "nebula", CompareOp.GREATER_THAN_OR_EQUAL);
    public static final RenderPipeline BLOCK_OUTLINE_NEBULA_THROUGH = blockOutlinePipeline(
            "nebula_through", "nebula", CompareOp.ALWAYS_PASS);

    public static final GpuFormat SKY_CLOUDS_FORMAT = GpuFormat.RGBA16_FLOAT;

    public static final RenderPipeline WORLD_SKY_CLOUDS_DEEP_SPACE = skyCloudsPipeline(
            "sky_clouds_deep_space", "shizavisuals:world/sky/clouds_deep_space");
    public static final RenderPipeline WORLD_SKY_CLOUDS_NEBULA = skyCloudsPipeline(
            "sky_clouds_nebula", "shizavisuals:world/sky/clouds_nebula");
    public static final RenderPipeline WORLD_SKY_CLOUDS_PLASMA = skyCloudsPipeline(
            "sky_clouds_plasma", "shizavisuals:world/sky/clouds_plasma");

    public static final RenderPipeline WORLD_SKY_DEEP_SPACE = worldPostPipeline(
            "sky_deep_space",
            "shizavisuals:world/sky/deep_space",
            WORLD_SKY_LAYOUT
    );
    public static final RenderPipeline WORLD_SKY_NEBULA = worldPostPipeline(
            "sky_nebula",
            "shizavisuals:world/sky/nebula",
            WORLD_SKY_LAYOUT
    );
    public static final RenderPipeline WORLD_SKY_PLASMA = worldPostPipeline(
            "sky_plasma",
            "shizavisuals:world/sky/plasma",
            WORLD_SKY_LAYOUT
    );
    public static final RenderPipeline WORLD_SATURATION = worldPostPipeline(
            "saturation",
            "shizavisuals:post/world_saturation",
            SATURATION_LAYOUT
    );
    public static final RenderPipeline MOTION_BLUR = worldPostPipeline(
            "motion_blur",
            "shizavisuals:post/motion_blur",
            MOTION_BLUR_LAYOUT
    );

    public static final RenderPipeline WORLD_PUDDLES = worldPostPipeline(
            "puddles",
            "shizavisuals:post/world_puddles",
            PUDDLES_LAYOUT
    );

    // One pass per live blast, each with its own ring origin. No blending: the
    // pass already re-samples the scene copy and writes the distorted result,
    // so SRC_ALPHA would double-apply the ring highlight.
    public static final RenderPipeline TNT_SHOCKWAVE = worldPostPipeline(
            "tnt_shockwave",
            "shizavisuals:post/tnt_shockwave",
            TNT_SHOCKWAVE_LAYOUT
    );

    // Reuses the plain POSITION_COLOR passthrough (shizavisuals_line) for
    // world-space effect geometry that carries its own colour per vertex.
    public static final RenderPipeline WORLD_COLOR_TRANSLUCENT = worldColorPipeline(
            "color_translucent", BlendFunction.TRANSLUCENT);
    public static final RenderPipeline WORLD_COLOR_ADDITIVE = worldColorPipeline(
            "color_additive", BlendFunction.LIGHTNING);

    private static RenderPipeline worldColorPipeline(String name, BlendFunction blend) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/" + name))
                .withVertexShader(Identifier.parse("shizavisuals:core/shizavisuals_line"))
                .withFragmentShader(Identifier.parse("shizavisuals:core/shizavisuals_line"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    public static final RenderPipeline CHAMS_SOLID = chamsPipeline(
            "solid", "shizavisuals:post/chams_solid", CHAMS_MASK_STYLE_LAYOUT, BlendFunction.TRANSLUCENT);
    public static final RenderPipeline CHAMS_PLASMA = chamsPipeline(
            "plasma", "shizavisuals:post/chams_plasma", CHAMS_MASK_STYLE_LAYOUT, BlendFunction.TRANSLUCENT);
    public static final RenderPipeline CHAMS_NEBULA = chamsPipeline(
            "nebula", "shizavisuals:post/chams_nebula", CHAMS_MASK_STYLE_LAYOUT, BlendFunction.TRANSLUCENT);
    public static final RenderPipeline CHAMS_GLASS = chamsPipeline(
            "glass", "shizavisuals:post/chams_glass", CHAMS_GLASS_LAYOUT, BlendFunction.TRANSLUCENT);
    public static final RenderPipeline CHAMS_OUTLINE = chamsPipeline(
            "outline", "shizavisuals:post/chams_outline", CHAMS_MASK_STYLE_LAYOUT, BlendFunction.TRANSLUCENT);
    public static final RenderPipeline CHAMS_INTERNAL = chamsPipeline(
            "internal", "shizavisuals:post/chams_internal", CHAMS_BLURRED_LAYOUT, BlendFunction.LIGHTNING);
    public static final RenderPipeline CHAMS_GLOW = chamsPipeline(
            "glow", "shizavisuals:post/chams_glow", CHAMS_BLURRED_LAYOUT, BlendFunction.TRANSLUCENT);
    public static final RenderPipeline CHAMS_GLOW_ADDITIVE = chamsPipeline(
            "glow_additive", "shizavisuals:post/chams_glow", CHAMS_BLURRED_LAYOUT, BlendFunction.LIGHTNING);

    public static final RenderPipeline POPCHAMS_ADDITIVE = popChamsModelPipeline(
            "additive_no_depth", BlendFunction.LIGHTNING, false, false);
    public static final RenderPipeline POPCHAMS_ADDITIVE_SOLID = popChamsModelPipeline(
            "additive_solid_no_depth", BlendFunction.LIGHTNING, true, false);
    public static final RenderPipeline POPCHAMS_TRANSLUCENT = popChamsModelPipeline(
            "translucent_no_depth", BlendFunction.TRANSLUCENT, false, false);
    public static final RenderPipeline POPCHAMS_TRANSLUCENT_SOLID = popChamsModelPipeline(
            "translucent_solid_no_depth", BlendFunction.TRANSLUCENT, true, false);
    public static final RenderPipeline POPCHAMS_MASK = popChamsModelPipeline(
            "mask", null, false, true);
    public static final RenderPipeline POPCHAMS_MASK_SOLID = popChamsModelPipeline(
            "mask_solid", null, true, false);
    public static final RenderPipeline POPCHAMS_COMPOSITE = chamsPipeline(
            "popchams_composite",
            "shizavisuals:post/popchams_composite",
            POPCHAMS_COMPOSITE_LAYOUT,
            BlendFunction.LIGHTNING
    );

    public static final GpuFormat EFFECT_FORMAT = GpuFormat.RGBA8_UNORM;

    private static RenderPipeline blockOutlinePipeline(String name,
                                                       String fragment,
                                                       CompareOp depthCompare) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/block_outline/" + name))
                .withVertexShader(Identifier.parse("shizavisuals:world/block_outline"))
                .withFragmentShader(Identifier.parse("shizavisuals:world/block_outline/" + fragment))
                .withBindGroupLayout(BLOCK_OUTLINE_LAYOUT)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(depthCompare, false))
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static RenderPipeline worldPostPipeline(String name,
                                                    String fragment,
                                                    BindGroupLayout layout) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/" + name))
                .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
                .withFragmentShader(Identifier.parse(fragment))
                .withBindGroupLayout(layout)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static RenderPipeline skyCloudsPipeline(String name, String fragment) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/" + name))
                .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
                .withFragmentShader(Identifier.parse(fragment))
                .withBindGroupLayout(WORLD_SKY_CLOUDS_LAYOUT)
                .withColorTargetState(new ColorTargetState(Optional.empty(), SKY_CLOUDS_FORMAT, ColorTargetState.WRITE_ALL))
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static RenderPipeline chamsPipeline(String name,
                                                String fragment,
                                                BindGroupLayout layout,
                                                BlendFunction blend) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("shizavisuals:pipeline/chams/" + name))
                .withVertexShader(Identifier.parse("shizavisuals:post/blurs/kawase_common"))
                .withFragmentShader(Identifier.parse(fragment))
                .withBindGroupLayout(layout)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static RenderPipeline popChamsModelPipeline(String name,
                                                        BlendFunction blend,
                                                        boolean solid,
                                                        boolean alphaCutout) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.parse("shizavisuals:pipeline/world/popchams_" + name))
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_OVERLAY")
                .withColorTargetState(blend == null
                        ? ColorTargetState.DEFAULT
                        : new ColorTargetState(blend))
                .withDepthStencilState(Optional.<DepthStencilState>empty())
                .withCull(false);
        if (solid) {
            builder.withFragmentShader(Identifier.parse("shizavisuals:core/popchams_solid"));
        }
        if (alphaCutout) {
            builder.withShaderDefine("ALPHA_CUTOUT", 0.10000000149011612F);
        }
        return builder.build();
    }

    private PostPipelines() {
    }
}
