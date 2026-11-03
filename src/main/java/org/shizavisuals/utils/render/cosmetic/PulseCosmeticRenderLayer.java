package org.shizavisuals.utils.render.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionfc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PulseCosmeticRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Logger LOGGER = Logger.getLogger(PulseCosmeticRenderLayer.class.getName());
    private static final Map<String, PulseCosmeticModel> MODELS = new LinkedHashMap<>();
    private static final Map<String, Boolean> BROKEN = new LinkedHashMap<>();

    private final Function<PulseCosmeticCategory, PulseCosmeticCatalog.PulseCosmetic> selector;
    private final BooleanSupplier selfOnly;
    private final BooleanSupplier translucent;

    public PulseCosmeticRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent,
                                    Function<PulseCosmeticCategory, PulseCosmeticCatalog.PulseCosmetic> selector,
                                    BooleanSupplier selfOnly,
                                    BooleanSupplier translucent) {
        super(parent);
        this.selector = selector;
        this.selfOnly = selfOnly == null ? () -> true : selfOnly;
        this.translucent = translucent == null ? () -> false : translucent;
    }

    public static void invalidateCache() {
        synchronized (MODELS) {
            MODELS.clear();
            BROKEN.clear();
        }
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        if (this.selfOnly.getAsBoolean() && !isSelf(state)) {
            return;
        }

        for (PulseCosmeticCategory category : PulseCosmeticCategory.values()) {
            PulseCosmeticCatalog.PulseCosmetic cosmetic = this.selector.apply(category);
            if (cosmetic != null) {
                submitCosmetic(category, cosmetic, poseStack, collector, packedLight, state);
            }
        }
    }

    private void submitCosmetic(PulseCosmeticCategory category, PulseCosmeticCatalog.PulseCosmetic cosmetic,
                                PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                                AvatarRenderState state) {
        PulseCosmeticModel model = model(category, cosmetic);
        if (model == null) {
            return;
        }

        PulseCosmeticModel.Transform transform = model.getTransform();
        Identifier texture = PulseCosmeticModel.texture(category.id(), cosmetic.id());

        poseStack.pushPose();
        try {
            switch (model.getAttachment()) {
                case HEAD -> getParentModel().head.translateAndRotate(poseStack);
                case BODY -> getParentModel().body.translateAndRotate(poseStack);
                case ROOT -> applyCrouchOffset(poseStack, state);
            }

            poseStack.translate(transform.x(), transform.y(), transform.z());
            if (transform.yaw() != 0.0F) {
                poseStack.mulPose((Quaternionfc) Axis.YP.rotationDegrees(transform.yaw()));
            }
            if (transform.pitch() != 0.0F) {
                poseStack.mulPose((Quaternionfc) Axis.XP.rotationDegrees(transform.pitch()));
            }
            if (transform.roll() != 0.0F) {
                poseStack.mulPose((Quaternionfc) Axis.ZP.rotationDegrees(transform.roll()));
            }
            poseStack.scale(transform.scale(), transform.scale(), transform.scale());

            var renderType = this.translucent.getAsBoolean()
                    ? RenderTypes.entityTranslucent(texture, false)
                    : RenderTypes.entityCutout(texture, false);
            collector.submitModel(model, state, poseStack, renderType,
                    packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        } finally {
            poseStack.popPose();
        }
    }

    private static void applyCrouchOffset(PoseStack poseStack, AvatarRenderState state) {
        if (state.isCrouching) {
            poseStack.translate(0.0F, 0.2F, 0.05F);
        }
    }

    private static boolean isSelf(AvatarRenderState state) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && state.id == player.getId();
    }

    private static PulseCosmeticModel model(PulseCosmeticCategory category, PulseCosmeticCatalog.PulseCosmetic cosmetic) {
        String key = cosmetic.cacheKey();
        synchronized (MODELS) {
            PulseCosmeticModel cached = MODELS.get(key);
            if (cached != null) {
                return cached;
            }
            if (Boolean.TRUE.equals(BROKEN.get(key))) {
                return null;
            }
        }

        PulseCosmeticModel loaded;
        try {
            loaded = PulseCosmeticModel.load(category.id(), cosmetic.id());
        } catch (Throwable throwable) {
            LOGGER.log(Level.WARNING, "Failed to load Pulse cosmetic " + key, throwable);
            synchronized (MODELS) {
                BROKEN.put(key, Boolean.TRUE);
            }
            return null;
        }

        synchronized (MODELS) {
            return MODELS.computeIfAbsent(key, ignored -> loaded);
        }
    }
}
