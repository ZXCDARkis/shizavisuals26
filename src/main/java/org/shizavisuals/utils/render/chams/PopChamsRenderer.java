package org.shizavisuals.utils.render.chams;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.shizavisuals.feature.impl.visual.PopChamsFeature;
import org.shizavisuals.utils.ColorUtil;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public final class PopChamsRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float MODEL_OFFSET_Y = -1.5010000467300415F;
    private static final float MODEL_DILATION = -0.20000000298023224F;
    private static final float SCALE_AMOUNT = 0.699999988079071F;
    private static final float RISE_AMOUNT = 0.44999998807907104F;
    private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    private final PopChamsCompositeEffect composite = new PopChamsCompositeEffect();
    private TextureTarget maskBuffer;
    private FeatureRenderDispatcher dispatcher;
    private PlayerModel model;

    public void render(LevelRenderState levelRenderState, PopChamsFeature feature) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.gameRenderer == null
                || levelRenderState == null) {
            return;
        }

        long nowNanos = System.nanoTime();
        List<PopChamsFeature.Snapshot> snapshots = feature.activeSnapshots(nowNanos);
        if (snapshots.isEmpty()) {
            return;
        }

        RenderTarget output = minecraft.gameRenderer.mainRenderTarget();
        if (output == null || output.getColorTextureView() == null) {
            return;
        }
        ensureResources(minecraft, output.width, output.height);
        if (this.maskBuffer == null || this.dispatcher == null || this.model == null) {
            return;
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                this.maskBuffer.getColorTexture(),
                CLEAR
        );

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(levelRenderState.cameraRenderState.viewRotationMatrix);
        try {
            submitPass(
                    snapshots,
                    levelRenderState,
                    feature,
                    nowNanos,
                    output.getColorTextureView(),
                    false
            );
            submitPass(
                    snapshots,
                    levelRenderState,
                    feature,
                    nowNanos,
                    this.maskBuffer.getColorTextureView(),
                    true
            );
        } finally {
            modelViewStack.popMatrix();
        }

        this.composite.render(this.maskBuffer, output, feature.effectiveGlowRadius());
    }

    private void submitPass(List<PopChamsFeature.Snapshot> snapshots,
                            LevelRenderState levelRenderState,
                            PopChamsFeature feature,
                            long nowNanos,
                            GpuTextureView output,
                            boolean mask) {
        SubmitNodeStorage storage = new SubmitNodeStorage();
        PoseStack poseStack = new PoseStack();
        double cameraX = levelRenderState.cameraRenderState.pos.x();
        double cameraY = levelRenderState.cameraRenderState.pos.y();
        double cameraZ = levelRenderState.cameraRenderState.pos.z();
        int submitted = 0;

        for (PopChamsFeature.Snapshot snapshot : snapshots) {
            float animation = snapshot.animation(nowNanos);
            if (animation <= 0.0F) {
                continue;
            }
            float eased = easeOutQuart(1.0F - animation);
            float scale = 1.0F + eased * SCALE_AMOUNT;
            float rise = eased * RISE_AMOUNT;

            AvatarRenderState renderState = buildRenderState(snapshot);
            this.model.setupAnim(renderState);

            poseStack.pushPose();
            poseStack.translate(
                    snapshot.x() - cameraX,
                    snapshot.y() - cameraY + rise,
                    snapshot.z() - cameraZ
            );
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - snapshot.bodyYaw()));
            poseStack.scale(-scale, -scale, scale);
            poseStack.translate(0.0F, MODEL_OFFSET_Y, 0.0F);

            int renderColor = mask
                    ? ColorUtil.withAlpha(snapshot.baseColor(), 255)
                    : ColorUtil.multiplyAlpha(snapshot.baseColor(), animation);
            this.dispatcherSubmit(
                    storage,
                    renderState,
                    poseStack,
                    mask
                            ? PopChamsRenderTypes.mask(snapshot.texture(), snapshot.textured())
                            : PopChamsRenderTypes.model(
                            snapshot.texture(),
                            snapshot.textured(),
                            feature.blending.getValue()
                    ),
                    renderColor
            );
            poseStack.popPose();
            submitted++;
        }

        if (submitted == 0) {
            return;
        }
        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        try {
            RenderSystem.outputColorTextureOverride = output;
            RenderSystem.outputDepthTextureOverride = null;
            this.dispatcher.renderAllFeatures(storage);
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
        }
    }

    private void dispatcherSubmit(SubmitNodeStorage storage,
                                  AvatarRenderState renderState,
                                  PoseStack poseStack,
                                  net.minecraft.client.renderer.rendertype.RenderType renderType,
                                  int color) {
        storage.submitModel(
                this.model,
                renderState,
                poseStack,
                renderType,
                FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                color,
                null,
                0,
                null
        );
    }

    private static AvatarRenderState buildRenderState(PopChamsFeature.Snapshot snapshot) {
        AvatarRenderState state = new AvatarRenderState();
        state.bodyRot = snapshot.bodyYaw();
        state.yRot = snapshot.relativeHeadYaw();
        state.xRot = snapshot.pitch();
        state.walkAnimationPos = snapshot.limbProgress();
        state.walkAnimationSpeed = snapshot.limbSpeed();
        return state;
    }

    private void ensureResources(Minecraft minecraft, int width, int height) {
        if (this.dispatcher == null) {
            this.dispatcher = new FeatureRenderDispatcher(
                    minecraft.gameRenderer.renderBuffers(),
                    minecraft.getModelManager(),
                    minecraft.getAtlasManager(),
                    minecraft.font,
                    minecraft.gameRenderer.gameRenderState()
            );
        }
        if (this.model == null) {
            this.model = new PlayerModel(
                    minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER),
                    false
            );
            this.model.root().offsetScale(new Vector3f(
                    MODEL_DILATION,
                    MODEL_DILATION,
                    MODEL_DILATION
            ));
        }
        if (this.maskBuffer == null
                || this.maskBuffer.width != width
                || this.maskBuffer.height != height) {
            if (this.maskBuffer != null) {
                this.maskBuffer.destroyBuffers();
            }
            this.maskBuffer = new TextureTarget(
                    "shizavisuals-popchams-mask",
                    width,
                    height,
                    false,
                    GpuFormat.RGBA8_UNORM
            );
        }
    }

    private static float easeOutQuart(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse * inverse;
    }

    public void release() {
        if (this.maskBuffer != null) {
            this.maskBuffer.destroyBuffers();
            this.maskBuffer = null;
        }
        this.composite.release();
    }
}
