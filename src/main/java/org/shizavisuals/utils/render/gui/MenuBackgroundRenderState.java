package org.shizavisuals.utils.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fc;

public final class MenuBackgroundRenderState extends UiElementRenderState {
    private final float x0;
    private final float y0;
    private final float x1;
    private final float y1;
    private final float seconds;
    private final int mode;
    private final float radius;
    private final int primaryColor;
    private final int secondaryColor;

    public MenuBackgroundRenderState(
            Matrix3x2fc pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float seconds,
            int mode,
            float radius,
            int primaryColor,
            int secondaryColor,
            ScreenRectangle scissor
    ) {
        super(pose, scissor, x0, y0, x1 - x0, y1 - y0);
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.seconds = seconds;
        this.mode = mode;
        this.radius = radius;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        int packedRg = UiVertexPacking.packU8Pair((this.secondaryColor >> 16) & 0xFF, (this.secondaryColor >> 8) & 0xFF);
        int packedBa = UiVertexPacking.packU8Pair(this.secondaryColor & 0xFF, (this.secondaryColor >>> 24) & 0xFF);
        float width = Math.max(1.0F, this.x1 - this.x0);
        float height = Math.max(1.0F, this.y1 - this.y0);
        int packedAspect = UiVertexPacking.packRadius(width / height);

        float radiusNorm = Math.min(0.5F, this.radius / height);

        addVertex(vertexConsumer, this.x0, this.y0, 0.0F, 0.0F, packedRg, packedBa, packedAspect, radiusNorm);
        addVertex(vertexConsumer, this.x0, this.y1, 0.0F, 1.0F, packedRg, packedBa, packedAspect, radiusNorm);
        addVertex(vertexConsumer, this.x1, this.y1, 1.0F, 1.0F, packedRg, packedBa, packedAspect, radiusNorm);
        addVertex(vertexConsumer, this.x1, this.y0, 1.0F, 0.0F, packedRg, packedBa, packedAspect, radiusNorm);
    }

    private void addVertex(
            VertexConsumer vertexConsumer,
            float x,
            float y,
            float u,
            float v,
            int packedRg,
            int packedBa,
            int packedAspect,
            float radiusNorm
    ) {
        vertexConsumer.addVertex(transformX(x, y), transformY(x, y), this.seconds)
                .setColor(this.primaryColor)
                .setUv(u, v)
                .setUv1(packedRg, packedBa)
                .setUv2(this.mode, packedAspect)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setLineWidth(radiusNorm);
    }

    @Override
    public RenderPipeline pipeline() {
        return GuiPipelines.MENU_BACKGROUND;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
