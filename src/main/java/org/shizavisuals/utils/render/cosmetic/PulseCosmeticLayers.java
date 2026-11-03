package org.shizavisuals.utils.render.cosmetic;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.shizavisuals.feature.impl.visual.CosmeticsFeature;

public final class PulseCosmeticLayers {

    private static boolean registered;

    private PulseCosmeticLayers() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof AvatarRenderer<?>) {
                RenderLayerParent<AvatarRenderState, PlayerModel> parent =
                        (RenderLayerParent<AvatarRenderState, PlayerModel>) entityRenderer;
                helper.register(new PulseCosmeticRenderLayer(parent,
                        PulseCosmeticLayers::selected,
                        () -> CosmeticsFeature.isSelfOnly(),
                        () -> CosmeticsFeature.isTranslucent()));
            }
        });
    }

    private static PulseCosmeticCatalog.PulseCosmetic selected(PulseCosmeticCategory category) {
        CosmeticsFeature feature = CosmeticsFeature.getEnabled();
        return feature == null ? null : feature.selected(category);
    }
}
