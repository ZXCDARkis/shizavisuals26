package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.ResourceReloadEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.utils.render.cosmetic.PulseCosmeticCatalog;
import org.shizavisuals.utils.render.cosmetic.PulseCosmeticCategory;
import org.shizavisuals.utils.render.cosmetic.PulseCosmeticRenderLayer;

public final class CosmeticsFeature extends Feature {

    public final ModeSetting wings = register(new ModeSetting(
            "Wings",
            PulseCosmeticCatalog.NONE,
            PulseCosmeticCatalog.modeOptions(PulseCosmeticCategory.WINGS).toArray(String[]::new)
    ).configKey("cosmetics.wings"));
    public final ModeSetting hat = register(new ModeSetting(
            "Hat",
            PulseCosmeticCatalog.NONE,
            PulseCosmeticCatalog.modeOptions(PulseCosmeticCategory.HAT).toArray(String[]::new)
    ).configKey("cosmetics.hat"));
    public final ModeSetting bodywear = register(new ModeSetting(
            "Bodywear",
            PulseCosmeticCatalog.NONE,
            PulseCosmeticCatalog.modeOptions(PulseCosmeticCategory.BODYWEAR).toArray(String[]::new)
    ).configKey("cosmetics.bodywear"));
    public final ModeSetting pet = register(new ModeSetting(
            "Pet",
            PulseCosmeticCatalog.NONE,
            PulseCosmeticCatalog.modeOptions(PulseCosmeticCategory.PET).toArray(String[]::new)
    ).configKey("cosmetics.pet"));
    public final BooleanSetting selfOnly = register(new BooleanSetting(
            "Self Only",
            true
    ).configKey("cosmetics.selfOnly"));
    public final BooleanSetting translucent = register(new BooleanSetting(
            "Translucent",
            false
    ).configKey("cosmetics.translucent"));

    public CosmeticsFeature() {
        super(
                "Cosmetics",
                "Attaches Pulse wings, hats, backpacks and pets to your avatar",
                FeatureCategory.VISUAL,
                BindSetting.UNBOUND
        );
    }

    public static CosmeticsFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(CosmeticsFeature.class);
    }

    public static boolean isSelfOnly() {
        CosmeticsFeature feature = getEnabled();
        return feature == null || feature.selfOnly.getValue();
    }

    public static boolean isTranslucent() {
        CosmeticsFeature feature = getEnabled();
        return feature != null && feature.translucent.getValue();
    }

    public PulseCosmeticCatalog.PulseCosmetic selected(PulseCosmeticCategory category) {
        return switch (category) {
            case WINGS -> PulseCosmeticCatalog.resolve(category, this.wings.getValue());
            case HAT -> PulseCosmeticCatalog.resolve(category, this.hat.getValue());
            case BODYWEAR -> PulseCosmeticCatalog.resolve(category, this.bodywear.getValue());
            case PET -> PulseCosmeticCatalog.resolve(category, this.pet.getValue());
        };
    }

    public ModeSetting setting(PulseCosmeticCategory category) {
        return switch (category) {
            case WINGS -> this.wings;
            case HAT -> this.hat;
            case BODYWEAR -> this.bodywear;
            case PET -> this.pet;
        };
    }

    @EventTarget
    public void onResourceReload(ResourceReloadEvent event) {
        PulseCosmeticRenderLayer.invalidateCache();
    }
}
