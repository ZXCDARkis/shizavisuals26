package org.shizavisuals.utils.render.world;

import org.shizavisuals.feature.impl.visual.BlockOutlineFeature;
import org.shizavisuals.feature.impl.visual.HitParticlesFeature;
import org.shizavisuals.feature.impl.visual.JumpCirclesFeature;
import org.shizavisuals.feature.impl.visual.KillEffectFeature;
import org.shizavisuals.feature.impl.visual.MotionBlurFeature;
import org.shizavisuals.feature.impl.visual.PopChamsFeature;
import org.shizavisuals.feature.impl.visual.TargetEsp;

import org.shizavisuals.feature.impl.visual.TrajectoriesFeature;
import org.shizavisuals.feature.impl.visual.WorldParticlesFeature;
import org.shizavisuals.feature.impl.visual.WorldTweaksFeature;
import org.shizavisuals.utils.render.chams.PopChamsRenderer;

import java.util.ArrayList;
import java.util.List;

public final class WorldEffects {
    private static final List<WorldEffect> EFFECTS = new ArrayList<>();

    private WorldEffects() {
    }

    public static void bootstrap() {
        if (!EFFECTS.isEmpty()) {
            return;
        }

        register(WorldEffect.lazy(
                () -> gate(WorldTweaksFeature.getEnabled(), WorldTweaksFeature::usesSky),
                WorldTweaksRenderer::new,
                (feature, renderer, context) -> renderer.renderSky(feature, context.cameraRenderState()),
                WorldTweaksRenderer::release
        ));

        register(WorldEffect.lazy(
                () -> gate(BlockOutlineFeature.getEnabled(), BlockOutlineFeature::usesShader),
                BlockOutlineRenderer::new,
                (feature, renderer, context) -> renderer.render(feature, context.cameraRenderState()),
                BlockOutlineRenderer::release
        ));

        register(WorldEffect.direct(
                JumpCirclesFeature::getEnabled,
                (feature, context) -> feature.renderWorld()
        ));

        register(WorldEffect.direct(
                org.shizavisuals.feature.impl.visual.WaveEffectFeature::getEnabled,
                (feature, context) -> feature.renderWorld()
        ));

        register(WorldEffect.direct(
                org.shizavisuals.feature.impl.misc.WebPanelFeature::getEnabled,
                (feature, context) -> feature.renderWorld()
        ));

        register(WorldEffect.direct(
                TrajectoriesFeature::getEnabled,
                (feature, context) -> feature.renderWorld()
        ));

        register(WorldEffect.direct(
                WorldParticlesFeature::getEnabled,
                (feature, context) -> feature.renderWorld(context.levelRenderState().cameraRenderState, context.tickDelta())
        ));

        register(WorldEffect.lazy(
                PopChamsFeature::getEnabled,
                PopChamsRenderer::new,
                (feature, renderer, context) -> renderer.render(context.levelRenderState(), feature),
                PopChamsRenderer::release
        ));

        register(WorldEffect.direct(
                HitParticlesFeature::getEnabled,
                (feature, context) -> feature.renderWorld(context.tickDelta())
        ));

        register(WorldEffect.direct(
                KillEffectFeature::getEnabled,
                (feature, context) -> feature.renderWorld()
        ));

        register(WorldEffect.direct(
                TargetEsp::getEnabled,
                (feature, context) -> feature.renderWorld(context.tickDelta())
        ));

        register(WorldEffect.lazy(
                () -> gate(WorldTweaksFeature.getEnabled(), WorldTweaksFeature::usesSaturation),
                WorldTweaksRenderer::new,
                (feature, renderer, context) -> renderer.renderSaturation(feature),
                WorldTweaksRenderer::release
        ));

        register(WorldEffect.lazy(
                MotionBlurFeature::getEnabled,
                MotionBlurRenderer::new,
                (feature, renderer, context) -> renderer.render(feature, context.cameraRenderState(), context.projectionMatrix()),
                MotionBlurRenderer::release
        ));

    }

    public static void register(WorldEffect effect) {
        EFFECTS.add(effect);
    }

    public static void render(WorldEffectContext context) {
        for (WorldEffect effect : EFFECTS) {
            if (effect.active()) {
                effect.render(context);
            } else {
                effect.release();
            }
        }
    }

    private static <F> F gate(F feature, java.util.function.Predicate<F> enabled) {
        return feature != null && enabled.test(feature) ? feature : null;
    }
}
