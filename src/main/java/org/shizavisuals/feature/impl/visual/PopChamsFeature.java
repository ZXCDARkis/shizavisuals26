package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public final class PopChamsFeature extends Feature implements MinecraftContext {
    public static final byte USE_TOTEM_STATUS = 35;

    private static final long FALLBACK_ANIMATION_DURATION_NANOS = 1_000_000_000L;

    public final BooleanSetting blending = register(new BooleanSetting(
            "Additive Glow",
            true
    ).configKey("render.popchams.blending"));
    public final BooleanSetting textured = register(new BooleanSetting(
            "Textured",
            false
    ).configKey("render.popchams.textured"));
    public final ColorSetting color = register(new ColorSetting(
            "Color",
            0xFFFFFFFF
    ).configKey("render.popchams.color"));
    public final NumberSetting glowRadius = register(new NumberSetting(
            "Glow Radius",
            6.0D,
            1.0D,
            12.0D,
            0.05000000074505806D,
            "px"
    ).configKey("render.chams.glowRadius"));

    private final List<Snapshot> snapshots = new ArrayList<>();

    public PopChamsFeature() {
        super(
                "PopChams", "Leaves a rising ghost model when a player uses a totem",
                FeatureCategory.VISUAL,
                BindSetting.UNBOUND
        );
    }

    public static PopChamsFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(PopChamsFeature.class);
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPhase() != PacketReceiveEvent.Phase.PRE
                || !(event.getPacket() instanceof ClientboundEntityEventPacket packet)
                || packet.getEventId() != USE_TOTEM_STATUS) {
            return;
        }

        mc.execute(() -> capture(packet));
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        clear();
    }

    @Override
    protected void onDisable() {
        clear();
    }

    private void capture(ClientboundEntityEventPacket packet) {
        if (!isEnabled() || mc.level == null) {
            return;
        }
        Entity entity = packet.getEntity(mc.level);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        Identifier texture = player.getSkin().body().texturePath();
        if (texture == null) {
            return;
        }

        this.snapshots.add(new Snapshot(
                texture,
                this.color.getValue(),
                this.textured.getValue(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.yBodyRot,
                player.yHeadRot - player.yHeadRotO,
                player.getXRot(),
                player.walkAnimation.position(),
                player.walkAnimation.speed(),
                System.nanoTime()
        ));
    }

    public List<Snapshot> activeSnapshots(long nowNanos) {
        this.snapshots.removeIf(snapshot -> snapshot.expired(nowNanos));
        return this.snapshots.isEmpty() ? List.of() : List.copyOf(this.snapshots);
    }

    public int effectiveGlowRadius() {
        float value = this.glowRadius.getValue().floatValue();
        return value <= 0.0F ? 6 : Math.round(value);
    }

    private void clear() {
        this.snapshots.clear();
    }

    public record Snapshot(
            Identifier texture,
            int baseColor,
            boolean textured,
            double x,
            double y,
            double z,
            float bodyYaw,
            float relativeHeadYaw,
            float pitch,
            float limbProgress,
            float limbSpeed,
            long startedAtNanos
    ) {
        public float animation(long nowNanos) {
            float progress = Math.clamp(
                    (nowNanos - this.startedAtNanos) / (float) FALLBACK_ANIMATION_DURATION_NANOS,
                    0.0F,
                    1.0F
            );
            return 1.0F - easeOutQuart(progress);
        }

        public boolean expired(long nowNanos) {
            return nowNanos - this.startedAtNanos >= FALLBACK_ANIMATION_DURATION_NANOS;
        }

        private static float easeOutQuart(float value) {
            float inverse = 1.0F - value;
            return 1.0F - inverse * inverse * inverse * inverse;
        }
    }
}
