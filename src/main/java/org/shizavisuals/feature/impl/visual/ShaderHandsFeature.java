package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorMode;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.MultiSelectSetting;
import org.shizavisuals.feature.setting.NumberSetting;

import java.util.List;

public final class ShaderHandsFeature extends Feature {
    public static final String FILL = "Fill";
    public static final String OUTLINE = "Outline";
    public static final String GLOW = "Glow";
    public static final String GLASS = "Glass";
    public static final String SOLID = "Solid";
    public static final String PLASMA = "Plasma";

    public final MultiSelectSetting effects = register(new MultiSelectSetting(
            "Effects",
            List.of(FILL, GLOW),
            FILL,
            OUTLINE,
            GLOW
    ).configKey("render.armtweaks.fill.effects"));
    public final ModeSetting colorMode = register(ColorMode.setting()
            .configKey("render.armtweaks.fill.colorMode"));
    public final ColorSetting color = register(new ColorSetting(
            "Color",
            0x7986CB
    ).configKey("render.armtweaks.fill.color")
            .visibleWhen(() -> ColorMode.isCustom(this.colorMode)));
    public final NumberSetting fillOpacity = register(new NumberSetting(
            "Fill Opacity",
            0.19D,
            0.0D,
            1.0D,
            0.01D,
            ""
    ).configKey("render.armtweaks.fill.opacity").visibleWhen(this::hasFill));
    public final ModeSetting fillType = register(new ModeSetting(
            "Fill Type",
            GLASS,
            GLASS,
            SOLID,
            PLASMA
    ).configKey("render.armtweaks.fill.type").visibleWhen(this::hasFill));
    public final NumberSetting plasmaSpeed = register(new NumberSetting(
            "Plasma Speed",
            1.0D,
            0.1D,
            5.0D,
            0.1D,
            "x"
    ).configKey("render.armtweaks.fill.plasma.speed").visibleWhen(this::hasPlasma));
    public final NumberSetting glassBlur = register(new NumberSetting(
            "Glass Blur",
            19.0D,
            0.0D,
            60.0D,
            1.0D,
            "px"
    ).configKey("render.armtweaks.fill.glass.blur").visibleWhen(this::hasGlass));
    public final BooleanSetting mirror = register(new BooleanSetting(
            "Mirror",
            true
    ).configKey("render.armtweaks.fill.glass.mirror").visibleWhen(this::hasGlass));
    public final NumberSetting glowRadius = register(new NumberSetting(
            "Glow Radius",
            18.0D,
            1.0D,
            50.0D,
            1.0D,
            "px"
    ).configKey("render.armtweaks.fill.glow.radius").visibleWhen(this::hasGlow));
    public final NumberSetting glowStrength = register(new NumberSetting(
            "Glow Strength",
            1.0D,
            0.0D,
            1.0D,
            0.01D,
            ""
    ).configKey("render.armtweaks.fill.glow.strength").visibleWhen(this::hasGlow));
    public final BooleanSetting flame = register(new BooleanSetting(
            "Flame",
            true
    ).configKey("render.armtweaks.fill.glow.flame").visibleWhen(this::hasGlow));
    public final NumberSetting flameSpeed = register(new NumberSetting(
            "Flame Speed",
            1.1D,
            0.1D,
            5.0D,
            0.1D,
            "x"
    ).configKey("render.armtweaks.fill.glow.flameSpeed").visibleWhen(this::hasFlame));
    public final NumberSetting flameTrail = register(new NumberSetting(
            "Flame Trail",
            1.0D,
            0.0D,
            1.0D,
            0.01D,
            ""
    ).configKey("render.armtweaks.fill.glow.flameTrail").visibleWhen(this::hasFlame));
    public final NumberSetting outlineThickness = register(new NumberSetting(
            "Outline Thickness",
            1.5D,
            0.5D,
            5.0D,
            0.1D,
            "px"
    ).configKey("render.armtweaks.fill.outline.thickness").visibleWhen(this::hasOutline));
    public final BooleanSetting bothHands = register(new BooleanSetting(
            "Both Hands",
            true
    ).configKey("render.armtweaks.fill.bothHands"));

    public ShaderHandsFeature() {
        super("ShaderHands", "Renders a colored silhouette over your first-person hands", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public int resolvedColor() {
        return ColorMode.resolve(this.colorMode, this.color);
    }

    public boolean hasFill() {
        return this.effects.isSelected(FILL);
    }

    public boolean hasGlass() {
        return hasFill() && this.fillType.is(GLASS);
    }

    public boolean hasPlasma() {
        return hasFill() && this.fillType.is(PLASMA);
    }

    public boolean hasOutline() {
        return this.effects.isSelected(OUTLINE);
    }

    public boolean hasGlow() {
        return this.effects.isSelected(GLOW);
    }

    public boolean hasFlame() {
        return hasGlow() && this.flame.getValue();
    }
}
