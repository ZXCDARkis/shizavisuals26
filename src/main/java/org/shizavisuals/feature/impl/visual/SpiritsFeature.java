package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.render.world.SpiritsRenderer;

public final class SpiritsFeature extends Feature {
    public final NumberSetting count = register(new NumberSetting(
            "Count", 8.0D, 1.0D, 32.0D, 1.0D, "").configKey("render.spirits.count"));
    public final ModeSetting palette = register(new ModeSetting(
            "Palette", "Rainbow", "Rainbow", "Flame", "Ghost", "Abyss", "Custom"));
    public final ColorSetting color1 = register(new ColorSetting(
            "Custom Color 1", 0xFF7DF9FF).configKey("render.spirits.color1")
            .visibleWhen(() -> this.palette.is("Custom")));
    public final ColorSetting color2 = register(new ColorSetting(
            "Custom Color 2", 0xFFB45CFF).configKey("render.spirits.color2")
            .visibleWhen(() -> this.palette.is("Custom")));
    public final NumberSetting size = register(new NumberSetting(
            "Size", 1.0D, 0.4D, 2.2D, 0.05D, "x").configKey("render.spirits.size"));
    public final NumberSetting speed = register(new NumberSetting(
            "Speed", 1.0D, 0.5D, 2.0D, 0.05D, "x").configKey("render.spirits.speed"));
    public final NumberSetting radius = register(new NumberSetting(
            "Radius", 1.15D, 0.6D, 2.0D, 0.05D, "x").configKey("render.spirits.radius"));
    public final NumberSetting trailLength = register(new NumberSetting(
            "Trail Length", 0.55D, 0.1D, 1.2D, 0.05D, "s").configKey("render.spirits.trailLength"));
    public final NumberSetting trailWidth = register(new NumberSetting(
            "Trail Width", 1.0D, 0.4D, 2.0D, 0.05D, "x").configKey("render.spirits.trailWidth"));
    public final NumberSetting brightness = register(new NumberSetting(
            "Brightness", 1.0D, 0.2D, 2.0D, 0.05D, "x").configKey("render.spirits.brightness"));
    public final NumberSetting sparkDensity = register(new NumberSetting(
            "Spark Density", 1.0D, 0.0D, 2.0D, 0.05D, "x").configKey("render.spirits.sparkDensity"));

    private SpiritsRenderer renderer;

    public SpiritsFeature() {
        super("Spirits", "Орбитальные духи вокруг игрока: охота, нова, зарядка", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static SpiritsFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(SpiritsFeature.class);
    }

    @Override
    protected void onEnable() {
        this.renderer = new SpiritsRenderer();
        this.renderer.reset();
    }

    @Override
    protected void onDisable() {
        if (this.renderer != null) {
            this.renderer.release();
            this.renderer = null;
        }
    }

    /** Entry point used by the attack mixin; guarded, cheap when disabled. */
    public static void handleAttack(Entity target) {
        SpiritsFeature feature = getEnabled();
        if (feature != null && feature.renderer != null) {
            feature.renderer.onAttack(target);
        }
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (this.renderer != null) {
            this.renderer.tick();
        }
    }

    @EventTarget
    public void onPacketReceive(org.shizavisuals.event.events.packet.PacketReceiveEvent event) {
        // Event id 35 on a living entity is the totem-of-undying activation.
        if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != 35) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!this.isEnabled() || mc.level == null || mc.player == null
                || packet.getEntity(mc.level) != mc.player) {
            return;
        }
        if (this.renderer != null) {
            this.renderer.onTotemPop();
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        if (this.renderer != null) {
            this.renderer.reset();
        }
    }

    /** World-pass hook registered in WorldEffects; asserts with the null-check. */
    public void renderWorld(float tickDelta) {
        if (this.renderer != null) {
            this.renderer.render(tickDelta);
        }
    }

    /** Convenience filters used by the renderer when picking a hunt target. */
    public static boolean isHuntable(Entity entity) {
        return entity instanceof LivingEntity living
                && !(entity instanceof ItemEntity)
                && living.getHealth() > 0.0F;
    }
}
