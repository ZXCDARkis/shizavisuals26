package org.shizavisuals.utils.render.world;

import org.shizavisuals.feature.impl.visual.BlockOutlineFeature;
import org.shizavisuals.feature.impl.visual.HitParticlesFeature;
import org.shizavisuals.feature.impl.visual.JumpCirclesFeature;
import org.shizavisuals.feature.impl.visual.KillEffectFeature;
import org.shizavisuals.feature.impl.visual.MotionBlurFeature;
import org.shizavisuals.feature.impl.visual.PopChamsFeature;
import org.shizavisuals.feature.impl.visual.PuddlesFeature;
import org.shizavisuals.feature.impl.visual.SpiritsFeature;
import org.shizavisuals.feature.impl.visual.TargetEsp;
import org.shizavisuals.feature.impl.visual.TntEffectFeature;

import org.shizavisuals.feature.impl.visual.TrajectoriesFeature;
import org.shizavisuals.feature.impl.visual.WorldParticlesFeature;
import org.shizavisuals.feature.impl.visual.WorldTweaksFeature;
import org.shizavisuals.utils.render.chams.PopChamsRenderer;
import org.shizavisuals.utils.render.tnt.TntEffectRenderer;

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

        // Visually world geometry: drawn after target ESP layers, before the
        // saturation/motion-blur post passes can wash them out.
        register(WorldEffect.direct(
                SpiritsFeature::getEnabled,
                (feature, context) -> feature.renderWorld(context.tickDelta())
        ));

        // Pets are world geometry too: drawn while the level depth is intact so
        // blocks hide them like everything else.
        register(WorldEffect.direct(
                org.shizavisuals.feature.impl.visual.PetsFeature::getEnabled,
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

        register(WorldEffect.lazy(
                () -> gate(PuddlesFeature.getEnabled(), PuddlesFeature::usesPuddles),
                PuddlesRenderer::new,
                (feature, renderer, context) -> renderer.render(feature, context.cameraRenderState()),
                PuddlesRenderer::release
        ));

        // Needs a tick hook (smoke + shake decay) and has to publish itself
        // through a static holder so the explosion-packet and camera mixins can
        // reach it without going through the event bus.
        register(WorldEffect.lazy(
                TntEffectFeature::getEnabled,
                TntEffectRenderer::new,
                (feature, renderer, context) -> {
                    renderer.tick(feature);
                    renderer.renderWorld(feature, context.cameraRenderState(), context.projectionMatrix());
                },
                TntEffectRenderer::release
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
