package org.shizavisuals.mixin.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.shizavisuals.utils.render.esp.EspSpriteRenderer;
import org.shizavisuals.utils.render.esp.TargetScanRenderTypes;
import org.shizavisuals.utils.render.gui.GuiPipelines;
import org.shizavisuals.utils.render.jump.JumpCircleRenderer;
import org.shizavisuals.utils.render.jump.JumpGlowRenderer;
import org.shizavisuals.utils.render.jump.JumpWaveRenderer;
import org.shizavisuals.utils.render.kill.KillEffectRenderer;
import org.shizavisuals.utils.render.particles.ProceduralParticleRenderer;
import org.shizavisuals.utils.render.particles.WorldParticleRenderer;
import org.shizavisuals.utils.render.post.PostPipelines;
import org.shizavisuals.utils.render.world.WorldMeshRenderer;
import org.shizavisuals.webpanel.WebPanelPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPipelines.class)
public abstract class RenderPipelinesMixin {
    @Shadow
    private static RenderPipeline register(RenderPipeline renderPipeline) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void registerGuiPipelines(CallbackInfo ci) {
        register(GuiPipelines.RECT);
        register(GuiPipelines.TEXT);
        register(GuiPipelines.TEXTURE);
        register(GuiPipelines.GLASS_SHADOW);
        register(GuiPipelines.BLUR_RECT);
        register(GuiPipelines.COLOR_GRID);
        register(GuiPipelines.MENU_BACKGROUND);
        register(GuiPipelines.RING);
        register(GuiPipelines.GUI_BLUR_DOWN);
        register(GuiPipelines.GUI_BLUR_UP);
        for (RenderPipeline pipeline : GuiPipelines.itemDownscaleVariants()) {
            register(pipeline);
        }
        register(PostPipelines.ESP_KAWASE_DOWN);
        register(PostPipelines.ESP_KAWASE_UP);
        register(ProceduralParticleRenderer.pipeline());
        register(PostPipelines.HAND_PLASMA);
        register(PostPipelines.POINT_LIGHTS);
        register(PostPipelines.BLOCK_OUTLINE_CLASSIC);
        register(PostPipelines.BLOCK_OUTLINE_CLASSIC_THROUGH);
        register(PostPipelines.BLOCK_OUTLINE_CAUSTICS);
        register(PostPipelines.BLOCK_OUTLINE_CAUSTICS_THROUGH);
        register(PostPipelines.BLOCK_OUTLINE_PRISMATIC);
        register(PostPipelines.BLOCK_OUTLINE_PRISMATIC_THROUGH);
        register(PostPipelines.BLOCK_OUTLINE_GLOSSY);
        register(PostPipelines.BLOCK_OUTLINE_GLOSSY_THROUGH);
        register(PostPipelines.BLOCK_OUTLINE_DEEP_SPACE);
        register(PostPipelines.BLOCK_OUTLINE_DEEP_SPACE_THROUGH);
        register(PostPipelines.BLOCK_OUTLINE_NEBULA);
        register(PostPipelines.BLOCK_OUTLINE_NEBULA_THROUGH);
        register(PostPipelines.WORLD_SKY_CLOUDS_DEEP_SPACE);
        register(PostPipelines.WORLD_SKY_CLOUDS_NEBULA);
        register(PostPipelines.WORLD_SKY_CLOUDS_PLASMA);
        register(PostPipelines.WORLD_SKY_DEEP_SPACE);
        register(PostPipelines.WORLD_SKY_NEBULA);
        register(PostPipelines.WORLD_SKY_PLASMA);
        register(PostPipelines.WORLD_SATURATION);
        register(PostPipelines.CHAMS_SOLID);
        register(PostPipelines.CHAMS_PLASMA);
        register(PostPipelines.CHAMS_NEBULA);
        register(PostPipelines.CHAMS_GLASS);
        register(PostPipelines.CHAMS_OUTLINE);
        register(PostPipelines.CHAMS_INTERNAL);
        register(PostPipelines.CHAMS_GLOW);
        register(PostPipelines.CHAMS_GLOW_ADDITIVE);
        register(PostPipelines.POPCHAMS_ADDITIVE);
        register(PostPipelines.POPCHAMS_ADDITIVE_SOLID);
        register(PostPipelines.POPCHAMS_TRANSLUCENT);
        register(PostPipelines.POPCHAMS_TRANSLUCENT_SOLID);
        register(PostPipelines.POPCHAMS_MASK);
        register(PostPipelines.POPCHAMS_MASK_SOLID);
        register(PostPipelines.POPCHAMS_COMPOSITE);
        register(PostPipelines.HAND_MASK);
        register(PostPipelines.HAND_MASK_SMOOTH);
        register(PostPipelines.SHADER_HANDS);
        register(PostPipelines.HAND_FILL);
        register(PostPipelines.HAND_GLASS);
        register(PostPipelines.HAND_OUTLINE);
        register(PostPipelines.HAND_HALO);
        register(PostPipelines.HAND_BLUR_DOWN);
        register(PostPipelines.HAND_BLUR_UP);
        register(PostPipelines.HAND_TRAIL);
        register(PostPipelines.MOTION_BLUR);
        register(PostPipelines.TNT_SHOCKWAVE);
        register(PostPipelines.WORLD_COLOR_TRANSLUCENT);
        register(PostPipelines.WORLD_COLOR_ADDITIVE);
        register(PostPipelines.WORLD_PUDDLES);
        registerAll(WebPanelPipelines.pipelines());
        registerAll(EspSpriteRenderer.pipelines());
        registerAll(WorldParticleRenderer.pipelines());
        registerAll(KillEffectRenderer.pipelines());
        registerAll(WorldMeshRenderer.pipelines());
        register(TargetScanRenderTypes.pipeline());
        register(JumpWaveRenderer.pipeline());
        register(JumpGlowRenderer.pipeline());
        register(JumpCircleRenderer.pipeline());
    }

    private static void registerAll(Iterable<RenderPipeline> pipelines) {
        for (RenderPipeline pipeline : pipelines) {
            register(pipeline);
        }
    }
}
