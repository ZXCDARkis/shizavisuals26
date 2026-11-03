package org.shizavisuals.feature.impl.visual;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;

import java.util.Set;

public final class RemovalsFeature extends Feature {
    private static RemovalsFeature instance;

    private final MultiSelectSetting overlay = register(new MultiSelectSetting(
            "Overlay",
            Set.of("Fire", "Bad Effects"),
            "Fire",
            "Bad Effects",
            "Shaking",
            "Totem Overlay",
            "Scoreboard",
            "Boss Bar"
    ));
    private final MultiSelectSetting world = register(new MultiSelectSetting(
            "World",
            Set.of("Weather"),
            "Weather",
            "Totem Particles",
            "Effects"
    ));
    private final MultiSelectSetting sounds = register(new MultiSelectSetting(
            "Sounds",
            Set.of(),
            "Hit",
            "Hurt",
            "Totem",
            "Step",
            "Eat",
            "Firework",
            "Warden"
    ));
    private final MultiSelectSetting camera = register(new MultiSelectSetting(
            "Camera",
            Set.of(),
            "Static FOV"
    ));

    public RemovalsFeature() {
        super("Removals", "Скрывает отвлекающие оверлеи, эффекты, частицы и звуки", FeatureCategory.VISUAL, BindSetting.UNBOUND);
        instance = this;
    }

    public static boolean shouldRemoveFireOverlay() {
        return active() && instance.overlay.isSelected("Fire");
    }

    public static boolean shouldRemoveBadEffectsVisuals() {
        return active() && instance.overlay.isSelected("Bad Effects");
    }

    public static boolean shouldRemoveShaking() {
        return active() && instance.overlay.isSelected("Shaking");
    }

    public static boolean shouldRemoveTotemOverlay() {
        return active() && instance.overlay.isSelected("Totem Overlay");
    }

    public static boolean shouldRemoveScoreboard() {
        return active() && instance.overlay.isSelected("Scoreboard");
    }

    public static boolean shouldRemoveBossBar() {
        return active() && instance.overlay.isSelected("Boss Bar");
    }

    public static boolean shouldRemoveFovDynamics() {
        return active() && instance.camera.isSelected("Static FOV");
    }

    public static boolean shouldRemoveWeather() {
        return active() && instance.world.isSelected("Weather");
    }

    public static boolean shouldRemoveParticle(ParticleOptions options) {
        if (!active() || options == null) {
            return false;
        }
        if (instance.world.isSelected("Totem Particles") && options.getType() == ParticleTypes.TOTEM_OF_UNDYING) {
            return true;
        }
        return instance.world.isSelected("Effects");
    }

    public static boolean shouldRemoveSound(SoundEvent soundEvent) {
        if (!active() || soundEvent == null) {
            return false;
        }

        String soundPath = soundEvent.location().getPath();
        if (instance.sounds.isSelected("Totem") && soundPath.contains("totem")) return true;
        if (instance.sounds.isSelected("Firework") && soundPath.contains("firework")) return true;
        if (instance.sounds.isSelected("Step") && soundPath.contains("step")) return true;
        if (instance.sounds.isSelected("Eat") && (soundPath.contains("eat") || soundPath.contains("drink") || soundPath.contains("burp"))) return true;
        if (instance.sounds.isSelected("Hit") && (soundPath.contains("attack") || soundPath.contains("hit"))) return true;
        if (instance.sounds.isSelected("Warden") && (soundPath.contains("warden") || soundPath.contains("sculk_sensor") || soundPath.contains("sculk_shrieker") || soundPath.contains("shriek"))) {
            return true;
        }
        return instance.sounds.isSelected("Hurt") && soundPath.contains("hurt");
    }

    private static boolean active() {
        return instance != null && instance.isEnabled();
    }
}
