package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorMode;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;

public final class WorldTweaksFeature extends Feature {
    public static final String SKY_DEEP_SPACE = "Deep Space";
    public static final String SKY_NEBULA = "Nebula";
    public static final String SKY_PLASMA = "Plasma";

    private static final long DAY_LENGTH = 24_000L;
    private static final long TIME_TRANSITION_MILLIS = 5_000L;

    public final BooleanSetting changeTime = register(new BooleanSetting(
            "Change Time",
            false
    ).configKey("render.worldtweaks.changetime"));
    public final ModeSetting timeOfDay = register(new ModeSetting(
            "Time of Day",
            "Day",
            "Day",
            "Night",
            "Sunset",
            "Midnight",
            "Sunrise"
    ).configKey("render.worldtweaks.timeofday").visibleWhen(() -> this.changeTime.getValue()));

    public final BooleanSetting changeFog = register(new BooleanSetting(
            "Change Fog",
            false
    ).configKey("render.worldtweaks.fogcolor"));
    public final NumberSetting fogDistance = register(new NumberSetting(
            "Fog Distance",
            90.0D,
            10.0D,
            200.0D,
            1.0D,
            " blocks"
    ).configKey("render.worldtweaks.fogcolor.distance").visibleWhen(() -> this.changeFog.getValue()));
    public final ModeSetting fogColorMode = register(ColorMode.setting()
            .configKey("render.worldtweaks.fogcolor.colorMode")
            .visibleWhen(() -> this.changeFog.getValue()));
    public final ColorSetting fogColor = register(new ColorSetting(
            "Fog Color",
            0xFF6E74E3
    ).configKey("render.worldtweaks.fogcolor.color")
            .visibleWhen(() -> this.changeFog.getValue() && ColorMode.isCustom(this.fogColorMode)));

    public final BooleanSetting changeSky = register(new BooleanSetting(
            "Change Sky",
            false
    ).configKey("render.worldtweaks.sky"));
    public final ModeSetting skyEffect = register(new ModeSetting(
            "Effect",
            SKY_DEEP_SPACE,
            SKY_DEEP_SPACE,
            SKY_NEBULA,
            SKY_PLASMA
    ).configKey("render.worldtweaks.sky.effect").visibleWhen(() -> this.changeSky.getValue()));
    public final ColorSetting skyColor1 = register(new ColorSetting(
            "Primary Color",
            0xFF7986CB
    ).configKey("render.worldtweaks.sky.color1").visibleWhen(() -> this.changeSky.getValue()));
    public final ColorSetting skyColor2 = register(new ColorSetting(
            "Secondary Color",
            0xFF7986CB
    ).configKey("render.worldtweaks.sky.color2").visibleWhen(() -> this.changeSky.getValue()));
    public final NumberSetting skySpeed = register(new NumberSetting(
            "Speed",
            4.0D,
            0.1D,
            8.0D,
            0.1D,
            "x"
    ).configKey("render.worldtweaks.sky.speed").visibleWhen(() -> this.changeSky.getValue()));
    public final NumberSetting skyIntensity = register(new NumberSetting(
            "Intensity",
            3.0D,
            0.1D,
            5.0D,
            0.1D,
            "x"
    ).configKey("render.worldtweaks.sky.intensity").visibleWhen(() -> this.changeSky.getValue()));

    public final BooleanSetting changeSaturation = register(new BooleanSetting(
            "Change Saturation",
            false
    ).configKey("render.worldtweaks.saturation"));
    public final NumberSetting saturationAmount = register(new NumberSetting(
            "Saturation",
            0.5D,
            -1.0D,
            2.0D,
            0.05D,
            ""
    ).configKey("render.worldtweaks.saturation.amount")
            .visibleWhen(() -> this.changeSaturation.getValue()));

    public final BooleanSetting changeWorldColor = register(new BooleanSetting(
            "Change World Color",
            false
    ).configKey("render.worldtweaks.changeworldcolor"));
    public final ModeSetting worldColorMode = register(ColorMode.setting()
            .configKey("render.worldtweaks.changeworldcolor.mode")
            .visibleWhen(() -> this.changeWorldColor.getValue()));
    public final ColorSetting worldColor = register(new ColorSetting(
            "World Color",
            0xFFFFFFFF
    ).configKey("render.worldtweaks.changeworldcolor.color")
            .visibleWhen(() -> this.changeWorldColor.getValue()
                    && ColorMode.isCustom(this.worldColorMode)));

    private final Animation timeAnimation = new Animation(0L, Animation.Easing.EASE_OUT_CUBIC);
    private String lastSelectedTime;

    public WorldTweaksFeature() {
        super("WorldTweaks", "Время, туман, небо, насыщенность и освещение мира", FeatureCategory.VISUAL, BindSetting.UNBOUND);
        resetTimeAnimation();
    }

    public static WorldTweaksFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(WorldTweaksFeature.class);
    }

    @Override
    protected void onEnable() {
        resetTimeAnimation();
    }

    public long getCustomTime() {
        String selected = this.timeOfDay.getValue();
        if (!selected.equals(this.lastSelectedTime)) {
            float current = this.timeAnimation.getValue();
            float target = targetTime(selected);
            this.timeAnimation.animate(
                    current,
                    current + shortestDifference(current, target),
                    TIME_TRANSITION_MILLIS,
                    Animation.Easing.EASE_OUT_CUBIC
            );
            this.lastSelectedTime = selected;
        }
        return normalizeTime(this.timeAnimation.getValue());
    }

    public void applyFog(FogData fog) {
        float end = this.fogDistance.getValue().floatValue();
        float span = Mth.clamp(end / 10.0F, 4.0F, 64.0F);
        float start = Math.max(0.0F, end - span);
        fog.environmentalStart = start;
        fog.environmentalEnd = end;
        fog.renderDistanceStart = start;
        fog.renderDistanceEnd = end;
        fog.skyEnd = end;
        fog.cloudEnd = end;

        int color = ColorMode.resolve(this.fogColorMode, this.fogColor);
        fog.color.set(
                ColorUtil.red(color) / 255.0F,
                ColorUtil.green(color) / 255.0F,
                ColorUtil.blue(color) / 255.0F,
                1.0F
        );
    }

    public boolean usesSky() {
        return this.changeSky.getValue();
    }

    public boolean usesSaturation() {
        return this.changeSaturation.getValue()
                && Math.abs(this.saturationAmount.getValue()) > 1.0E-4D;
    }

    public boolean usesWorldColor() {
        return this.changeWorldColor.getValue();
    }

    public int resolvedWorldColor() {
        return ColorMode.resolve(this.worldColorMode, this.worldColor);
    }

    private void resetTimeAnimation() {
        float target = targetTime(this.timeOfDay.getValue());
        this.timeAnimation.animate(target, target, 0L, Animation.Easing.EASE_OUT_CUBIC);
        this.lastSelectedTime = this.timeOfDay.getValue();
    }

    private static float targetTime(String time) {
        return switch (time) {
            case "Night" -> 13_000.0F;
            case "Sunset" -> 12_000.0F;
            case "Midnight" -> 18_000.0F;
            case "Sunrise" -> 23_000.0F;
            default -> 1_000.0F;
        };
    }

    private static float shortestDifference(float current, float target) {
        double difference = (target - current % DAY_LENGTH + DAY_LENGTH * 1.5D) % DAY_LENGTH
                - DAY_LENGTH / 2.0D;
        return (float) difference;
    }

    private static long normalizeTime(float time) {
        long normalized = (long) time % DAY_LENGTH;
        return normalized < 0L ? normalized + DAY_LENGTH : normalized;
    }
}
