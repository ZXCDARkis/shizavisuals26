package org.shizavisuals.feature.impl.visual;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.particles.ProceduralParticleRenderer;
import org.shizavisuals.utils.render.particles.ProceduralParticleRenderer.Shape;
import org.shizavisuals.utils.render.particles.ProceduralParticleRenderer.Sprite;
import org.shizavisuals.utils.render.particles.ProceduralParticleState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class HitParticlesFeature extends Feature implements MinecraftContext {
    private static final int HARD_PARTICLE_CAP = 450;

    public final ModeSetting shape = register(new ModeSetting(
            "Shape",
            Shape.RANDOM.displayName(),
            Shape.displayNames()
    ).configKey("render.particles.texture"));
    public final NumberSetting amount = register(new NumberSetting(
            "Hit Amount",
            15.0D,
            1.0D,
            50.0D,
            1.0D,
            ""
    ).configKey("render.particles.hitamount"));
    public final NumberSetting lifetime = register(new NumberSetting(
            "Lifetime",
            60.0D,
            10.0D,
            200.0D,
            1.0D,
            " ticks"
    ).configKey("render.particles.lifetime"));
    public final NumberSetting size = register(new NumberSetting(
            "Size",
            0.15D,
            0.05D,
            0.5D,
            0.05D,
            " blocks"
    ).configKey("render.particles.size"));
    public final ColorSetting color = register(new ColorSetting(
            "Color",
            0xFFFFFFFF
    ).configKey("render.particles.color"));
    public final BooleanSetting natural = register(new BooleanSetting(
            "Themed Colors",
            false
    ).configKey("render.particles.natural"));
    public final NumberSetting glow = register(new NumberSetting(
            "Glow",
            0.0D,
            0.0D,
            1.0D,
            0.05D,
            ""
    ).configKey("render.particles.glow"));

    private final List<ProceduralParticleState> particles = new ArrayList<>();
    private final List<Sprite> sprites = new ArrayList<>();
    private final ProceduralParticleRenderer renderer = new ProceduralParticleRenderer();
    private final Random random = new Random();

    public HitParticlesFeature() {
        super(
                "HitParticles", "Spawns procedural particles when you attack an entity",
                FeatureCategory.VISUAL,
                BindSetting.UNBOUND
        );
    }

    public static HitParticlesFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(HitParticlesFeature.class);
    }

    public void onAttack(Entity target) {
        if (target instanceof LivingEntity living) {
            spawnHit(living);
        }
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (mc.level == null) {
            clear();
            return;
        }
        if (this.particles.isEmpty()) {
            return;
        }

        float configuredSize = this.size.getValue().floatValue();
        Iterator<ProceduralParticleState> iterator = this.particles.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().update(mc.level, configuredSize, 1.0F, this.random)) {
                iterator.remove();
            }
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        clear();
    }

    @Override
    protected void onDisable() {
        clear();
        this.renderer.release();
    }

    private void clear() {
        this.particles.clear();
        this.sprites.clear();
    }

    public void renderWorld(float tickDelta) {
        if (mc.player == null || mc.level == null || this.particles.isEmpty()) {
            return;
        }

        int baseColor = this.color.getValue();
        float configuredSize = this.size.getValue().floatValue();
        this.sprites.clear();
        for (ProceduralParticleState particle : this.particles) {
            this.sprites.add(new Sprite(
                    particle.interpolatedPosition(tickDelta),
                    configuredSize,
                    ColorUtil.multiplyAlpha(baseColor, particle.opacityEnvelope()),
                    particle.shape(),
                    particle.renderRotation(tickDelta),
                    particle.normalizedLife(),
                    particle.seed(),
                    particle.phase()
            ));
        }
        this.renderer.render(
                this.sprites,
                this.glow.getValue().floatValue(),
                this.natural.getValue()
        );
    }

    private void spawnHit(LivingEntity entity) {
        int count = Math.min(
                this.amount.getValue().intValue(),
                HARD_PARTICLE_CAP - this.particles.size()
        );
        if (count <= 0) {
            return;
        }

        Vec3 base = entity.position();
        int configuredLifetime = this.lifetime.getValue().intValue();
        for (int index = 0; index < count; index++) {
            Vec3 position = base.add(
                    (this.random.nextDouble() - 0.5D) * entity.getBbWidth(),
                    this.random.nextDouble() * entity.getBbHeight(),
                    (this.random.nextDouble() - 0.5D) * entity.getBbWidth()
            );
            Vec3 velocity = ProceduralParticleState.randomVelocity(this.random);
            this.particles.add(new ProceduralParticleState(
                    position.x,
                    position.y,
                    position.z,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    configuredLifetime,
                    selectedShape(),
                    this.random
            ));
        }
    }

    private Shape selectedShape() {
        return Shape.fromDisplayName(this.shape.getValue()).resolve(this.random);
    }
}
