package org.shizavisuals.feature.impl.visual;

import net.minecraft.world.phys.Vec3;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.PlayerJumpEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorMode;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.render.jump.JumpCircleRenderer;
import org.shizavisuals.utils.render.jump.JumpGlowRenderer;
import org.shizavisuals.utils.render.jump.JumpWaveRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class JumpCirclesFeature extends Feature implements MinecraftContext {
    public final NumberSetting lifetime = register(new NumberSetting("Lifetime", 800.0D, 200.0D, 3000.0D, 10.0D, " ms"));
    public final NumberSetting radius = register(new NumberSetting("Radius", 1.5D, 0.5D, 5.0D, 0.1D, " blocks"));
    public final BooleanSetting waveEffect = register(new BooleanSetting("Wave Effect", false));
    public final NumberSetting waveStrength = register(new NumberSetting("Wave Strength", 0.6D, 0.0D, 2.0D, 0.05D, ""));
    public final NumberSetting waveRadius = register(new NumberSetting("Wave Radius", 3.0D, 0.5D, 10.0D, 0.1D, " blocks"));
    public final NumberSetting waveTime = register(new NumberSetting("Wave Time", 600.0D, 150.0D, 2500.0D, 10.0D, " ms"));
    public final BooleanSetting glow = register(new BooleanSetting("Glow", false));
    public final NumberSetting distortionIntensity = register(new NumberSetting("Distortion", 1.0D, 0.0D, 3.0D, 0.05D, "x"));
    public final ModeSetting colorMode = register(ColorMode.setting());
    public final ColorSetting color = register(new ColorSetting("Color", 0x7765FF)
            .visibleWhen(() -> ColorMode.isCustom(this.colorMode)));

    private final List<Circle> circles = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    private final JumpCircleRenderer circleRenderer = new JumpCircleRenderer();
    private final JumpWaveRenderer waveRenderer = new JumpWaveRenderer();
    private final JumpGlowRenderer glowRenderer = new JumpGlowRenderer();

    public JumpCirclesFeature() {
        super("JumpCircles", "Spawns expanding rings at jump positions", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static JumpCirclesFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(JumpCirclesFeature.class);
    }

    @EventTarget
    public void onJump(PlayerJumpEvent event) {
        this.circles.add(new Circle(event.getPosition(), System.currentTimeMillis(), this.lifetime.getValue().floatValue()));
        if (this.waveEffect.getValue()) {
            this.waves.add(new Wave(
                    event.getPosition(),
                    System.currentTimeMillis(),
                    this.waveTime.getValue().floatValue(),
                    (float) (this.waveStrength.getValue() * this.distortionIntensity.getValue()),
                    this.waveRadius.getValue().floatValue()
            ));
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        clear();
    }

    public void renderWorld() {
        if (mc.player == null || mc.level == null) {
            return;
        }

        long now = System.currentTimeMillis();
        float time = (now % 1_000_000L) / 1000.0F;
        boolean glowEnabled = this.glow.getValue();
        int fixedColor = ColorMode.resolve(this.colorMode, this.color);

        Iterator<Circle> iterator = this.circles.iterator();
        while (iterator.hasNext()) {
            Circle circle = iterator.next();
            if (circle.finished(now)) {
                iterator.remove();
                continue;
            }

            float rawProgress = circle.rawProgress(now);
            float progress = easeOutCubic(rawProgress);
            float fade = 1.0F - rawProgress;
            this.circleRenderer.render(
                    circle.position(),
                    this.radius.getValue().floatValue() * progress,
                    fade,
                    time,
                    fixedColor,
                    glowEnabled
            );
        }

        Iterator<Wave> waveIterator = this.waves.iterator();
        while (waveIterator.hasNext()) {
            Wave wave = waveIterator.next();
            if (wave.finished(now)) {
                waveIterator.remove();
                continue;
            }

            float waveRaw = wave.rawProgress(now);
            float ringProgress = easeOutCubic(waveRaw);
            float waveFade = fadeEnvelope(waveRaw);
            this.waveRenderer.render(
                    wave.position(),
                    wave.maxRadius(),
                    ringProgress,
                    wave.strength(),
                    waveFade,
                    fixedColor
            );

            if (glowEnabled) {
                this.glowRenderer.render(
                        wave.position(),
                        wave.maxRadius(),
                        ringProgress,
                        fixedColor,
                        waveFade,
                        time
                );
            }
        }
    }

    @Override
    protected void onDisable() {
        clear();
        this.waveRenderer.release();
        this.glowRenderer.release();
    }

    private void clear() {
        this.circles.clear();
        this.waves.clear();
    }

    private static float fadeEnvelope(float raw) {
        float clamped = Math.clamp(raw, 0.0F, 1.0F);
        float appear = smoothStep(clamped / 0.18F);
        float disappear = smoothStep((1.0F - clamped) / 0.82F);
        return Math.clamp(appear * disappear, 0.0F, 1.0F);
    }

    private static float smoothStep(float value) {
        float t = Math.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0F - Math.clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private record Circle(Vec3 position, long createdAt, float lifetimeMs) {
        private float rawProgress(long now) {
            return Math.clamp((now - createdAt) / lifetimeMs, 0.0F, 1.0F);
        }

        private boolean finished(long now) {
            return now - createdAt >= lifetimeMs;
        }
    }

    private record Wave(Vec3 position, long createdAt, float lifetimeMs, float strength, float maxRadius) {
        private float rawProgress(long now) {
            return Math.clamp((now - createdAt) / lifetimeMs, 0.0F, 1.0F);
        }

        private boolean finished(long now) {
            return now - createdAt >= lifetimeMs;
        }
    }
}
