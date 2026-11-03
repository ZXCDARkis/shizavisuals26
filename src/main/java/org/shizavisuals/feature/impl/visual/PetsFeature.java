package org.shizavisuals.feature.impl.visual;

import net.minecraft.world.entity.player.Player;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.render.world.PetsRenderer;

/**
 * A pet that trots along behind you. It is not an entity and the server never
 * hears about it: it walks on the world's own blocks, falls, climbs steps and
 * hops over what it cannot step onto, and catches up when it falls too far
 * behind. "Masmol" is a flat cutout that faces the camera; "Pig" is the game's
 * own pig model posed by its native walk animation.
 */
public final class PetsFeature extends Feature {
    public final ModeSetting mode = register(new ModeSetting(
            "Mode", "Masmol", "Masmol", "Pig"));
    public final NumberSetting size = register(new NumberSetting(
            "Size", 1.0D, 0.3D, 3.0D, 0.05D, "x").configKey("render.pets.size"));
    public final NumberSetting distance = register(new NumberSetting(
            "Distance", 2.5D, 1.0D, 6.0D, 0.1D, "").configKey("render.pets.distance"));
    public final NumberSetting speed = register(new NumberSetting(
            "Speed", 1.0D, 0.5D, 2.5D, 0.05D, "x").configKey("render.pets.speed"));
    public final BooleanSetting jump = register(new BooleanSetting(
            "Hop", true).configKey("render.pets.jump"));
    public final BooleanSetting animate = register(new BooleanSetting(
            "Animate", true).configKey("render.pets.animate"));
    public final BooleanSetting teleport = register(new BooleanSetting(
            "Teleport on Lag", true).configKey("render.pets.teleport"));

    private PetsRenderer renderer;

    public PetsFeature() {
        super("Pets", "Питомец бежит следом за вами: ходит по блокам, прыгает и весёлит мир", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static PetsFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(PetsFeature.class);
    }

    @Override
    protected void onEnable() {
        this.renderer = new PetsRenderer();
    }

    @Override
    protected void onDisable() {
        if (this.renderer != null) {
            this.renderer = null;
        }
    }

    public boolean isAnimated() {
        return this.animate.getValue();
    }

    public void tick() {
        if (this.renderer != null) {
            this.renderer.tick(this);
        }
    }

    /** World-pass hook registered in WorldEffects. */
    public void renderWorld(float tickDelta) {
        if (this.renderer != null) {
            this.renderer.render(this, tickDelta);
        }
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        tick();
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        if (this.renderer != null) {
            this.renderer.reset();
        }
    }
}
