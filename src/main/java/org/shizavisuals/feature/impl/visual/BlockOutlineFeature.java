package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorMode;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;

public final class BlockOutlineFeature extends Feature {
    public static final String MODE_SHADER = "Shader";
    public static final String MODE_NORMAL = "Normal";

    public static final String VARIANT_CLASSIC = "Classic";
    public static final String VARIANT_CAUSTICS = "Water Caustics";
    public static final String VARIANT_PRISMATIC = "Prismatic Flow";
    public static final String VARIANT_GLOSSY = "Glossy Gradients";
    public static final String VARIANT_DEEP_SPACE = "Deep Space";
    public static final String VARIANT_NEBULA = "Nebula";

    public final ModeSetting mode = register(new ModeSetting(
            "Mode",
            MODE_SHADER,
            MODE_SHADER,
            MODE_NORMAL
    ).configKey("render.blockoutline.mode"));
    public final ModeSetting variant = register(new ModeSetting(
            "Variant",
            VARIANT_CLASSIC,
            VARIANT_CLASSIC,
            VARIANT_CAUSTICS,
            VARIANT_PRISMATIC,
            VARIANT_GLOSSY,
            VARIANT_DEEP_SPACE,
            VARIANT_NEBULA
    ).configKey("render.blockoutline.variant").visibleWhen(this::usesShader));
    public final ModeSetting colorMode = register(ColorMode.setting()
            .configKey("render.blockoutline.tintMode")
            .visibleWhen(this::usesShader));
    public final ColorSetting tint = register(new ColorSetting(
            "Tint",
            0xFFFFFFFF
    ).configKey("render.blockoutline.tint")
            .visibleWhen(() -> usesShader() && ColorMode.isCustom(this.colorMode)));
    public final BooleanSetting ignoreDepth = register(new BooleanSetting(
            "Ignore Depth",
            false
    ).configKey("render.blockoutline.ignoreDepth").visibleWhen(this::usesShader));
    public final NumberSetting animationSpeed = register(new NumberSetting(
            "Animation Speed",
            15.0D,
            1.0D,
            30.0D,
            1.0D,
            ""
    ).configKey("render.blockoutline.animationSpeed").visibleWhen(this::usesShader));
    public final NumberSetting shaderSpeed = register(new NumberSetting(
            "Shader Speed",
            1.0D,
            0.1D,
            3.0D,
            0.05D,
            "x"
    ).configKey("render.blockoutline.shaderSpeed").visibleWhen(this::usesShader));
    public final NumberSetting shaderIntensity = register(new NumberSetting(
            "Shader Intensity",
            1.5D,
            0.1D,
            3.0D,
            0.05D,
            "x"
    ).configKey("render.blockoutline.shaderIntensity").visibleWhen(this::usesShader));

    public BlockOutlineFeature() {
        super("BlockOutline", "Анимированный шейдер над выбранным блоком", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static BlockOutlineFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(BlockOutlineFeature.class);
    }

    public boolean usesShader() {
        return this.mode.is(MODE_SHADER);
    }

    public int resolvedTint() {
        return ColorMode.resolve(this.colorMode, this.tint);
    }
}
