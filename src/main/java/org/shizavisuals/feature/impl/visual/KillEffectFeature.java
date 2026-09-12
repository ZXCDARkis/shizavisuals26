package org.shizavisuals.feature.impl.visual;

import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.render.kill.KillEffectRenderer;
import org.shizavisuals.utils.render.kill.KillEffectRenderer.Kind;
import org.shizavisuals.utils.render.post.PostFx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class KillEffectFeature extends Feature implements MinecraftContext {
    private static final byte ENTITY_DEAD_STATUS = 3;

    public final ModeSetting mode = register(new ModeSetting(
            "Mode",
            "Portal",
            "Portal", "Tornado", "Nova", "Souls", "Beam"
    ).configKey("render.killeffect.mode"));
    public final MultiSelectSetting targets = register(new MultiSelectSetting(
            "Targets",
            List.of("Players"),
            "Players", "Mobs"
    ).configKey("render.killeffect.targets"));
    public final BooleanSetting self = register(new BooleanSetting(
            "Self",
            false
    ).configKey("render.killeffect.self"));
    public final NumberSetting lifetime = register(new NumberSetting(
            "Lifetime",
            1200.0D,
            300.0D,
            3000.0D,
            10.0D,
            " ms"
    ).configKey("render.killeffect.lifetime"));
    public final NumberSetting size = register(new NumberSetting(
            "Size",
            1.2D,
            0.3D,
            4.0D,
            0.1D,
            " blocks"
    ).configKey("render.killeffect.size"));
    public final NumberSetting intensity = register(new NumberSetting(
            "Intensity",
            1.0D,
            0.1D,
            3.0D,
            0.05D,
            "x"
    ).configKey("render.killeffect.intensity"));
    public final ColorSetting color = register(new ColorSetting(
            "Color",
            0xFF7A5CFF
    ).configKey("render.killeffect.color"));

    private final List<Active> effects = new ArrayList<>();
    private final Map<Integer, Long> lastSpawnByEntity = new HashMap<>();
    private final KillEffectRenderer renderer = new KillEffectRenderer();

    public KillEffectFeature() {
        super(
                "KillEffects", "Shader portal/tornado burst on entity death",
                FeatureCategory.VISUAL,
                BindSetting.UNBOUND
        );
    }

    public static KillEffectFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(KillEffectFeature.class);
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPhase() != PacketReceiveEvent.Phase.PRE
                || !(event.getPacket() instanceof ClientboundEntityEventPacket packet)
                || packet.getEventId() != ENTITY_DEAD_STATUS) {
            return;
        }
        mc.execute(() -> {
            if (!isEnabled() || mc.level == null) {
                return;
            }
            if (packet.getEntity(mc.level) instanceof LivingEntity living) {
                onDeath(living);
            }
        });
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        // Fallback for deaths without a status packet: players stuck at 0 hp / dying.
        for (net.minecraft.client.player.AbstractClientPlayer player : mc.level.players()) {
            if (player.isDeadOrDying() || player.getHealth() <= 0.0F) {
                onDeath(player);
            }
        }
        long now = System.currentTimeMillis();
        this.lastSpawnByEntity.values().removeIf(stamp -> now - stamp > this.lifetime.getValue().longValue() * 2L);
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        clear();
    }

    @Override
    protected void onDisable() {
        clear();
    }

    public void renderWorld() {
        if (mc.player == null || mc.level == null || this.effects.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long lifetimeMs = this.lifetime.getValue().longValue();
        float halfSize = this.size.getValue().floatValue();
        float time = PostFx.shaderTime();
        int effectColor = this.color.getValue();
        float effectIntensity = this.intensity.getValue().floatValue();
        Kind kind = kindFromMode();

        Iterator<Active> iterator = this.effects.iterator();
        while (iterator.hasNext()) {
            Active active = iterator.next();
            float progress = KillEffectRenderer.progress(now, active.spawnedAt(), lifetimeMs);
            if (progress >= 1.0F) {
                iterator.remove();
                continue;
            }
            this.renderer.shaderEffect(kind, active.center(), halfSize, progress, time, effectColor, effectIntensity);
        }
    }

    private void onDeath(LivingEntity entity) {
        if (!accepts(entity)) {
            return;
        }
        long now = System.currentTimeMillis();
        long lifetimeMs = this.lifetime.getValue().longValue();
        Long last = this.lastSpawnByEntity.get(entity.getId());
        if (last != null && now - last < lifetimeMs) {
            return;
        }
        this.lastSpawnByEntity.put(entity.getId(), now);

        float halfSize = this.size.getValue().floatValue();
        Vec3 feet = entity.position();
        // Vertical effects (tornado/souls/beam) are bottom-anchored: quad spans feet..feet+2*half.
        // Centered effects (portal/nova) sit at body center.
        Vec3 center = isBottomAnchored()
                ? feet.add(0.0D, halfSize, 0.0D)
                : feet.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        this.effects.add(new Active(center, now));
    }

    private Kind kindFromMode() {
        if (this.mode.is("Tornado")) {
            return Kind.TORNADO;
        }
        if (this.mode.is("Nova")) {
            return Kind.NOVA;
        }
        if (this.mode.is("Souls")) {
            return Kind.SOULS;
        }
        if (this.mode.is("Beam")) {
            return Kind.BEAM;
        }
        return Kind.PORTAL;
    }

    private boolean isBottomAnchored() {
        return this.mode.is("Tornado") || this.mode.is("Souls") || this.mode.is("Beam");
    }

    private boolean accepts(LivingEntity entity) {
        if (mc.player != null && entity.getId() == mc.player.getId() && !this.self.getValue()) {
            return false;
        }
        boolean isPlayer = entity instanceof Player;
        if (isPlayer) {
            return this.targets.isSelected("Players");
        }
        return this.targets.isSelected("Mobs");
    }

    private void clear() {
        this.effects.clear();
        this.lastSpawnByEntity.clear();
    }

    private record Active(Vec3 center, long spawnedAt) {
    }
}
