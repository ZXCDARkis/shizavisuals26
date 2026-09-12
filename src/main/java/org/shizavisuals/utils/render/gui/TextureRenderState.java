package org.shizavisuals.utils.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.shizavisuals.context.MinecraftContext;
import org.joml.Matrix3x2fc;

public final class TextureRenderState extends UiElementRenderState {
    private final GuiTexture texture;
    private final Identifier managedTextureId;
    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;
    private final int color;
    private final float cornerRadius;
    private final float deviceScale;

    public TextureRenderState(Matrix3x2fc pose, Identifier textureId, boolean managed, float x, float y, float width, float height,
                              float u0, float v0, float u1, float v1, int color, float cornerRadius, ScreenRectangle scissor) {
        super(pose, scissor, x, y, width, height);
        this.texture = managed ? null : GuiTexture.load(textureId);
        this.managedTextureId = managed ? textureId : null;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.color = color;
        this.cornerRadius = cornerRadius;

        float poseScale = Math.max(
                (float) Math.hypot(pose.m00(), pose.m01()),
                (float) Math.hypot(pose.m10(), pose.m11())
        );
        this.deviceScale = Math.max(1, MinecraftContext.mc.getWindow().getGuiScale()) * Math.max(poseScale, 0.01F);
    }

    public TextureRenderState(Matrix3x2fc pose, Identifier textureId, float x, float y, float width, float height, int color, ScreenRectangle scissor) {
        this(pose, textureId, false, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, color, 0.0F, scissor);
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float halfWidth = this.width * 0.5F;
        float halfHeight = this.height * 0.5F;
        int packedSizeX = UiVertexPacking.packSize(this.width);
        int packedSizeY = UiVertexPacking.packSize(this.height);

        addVertex(vertexConsumer, this.x, this.y, this.u0, this.v0, -halfWidth, -halfHeight, packedSizeX, packedSizeY);
        addVertex(vertexConsumer, this.x, this.y + this.height, this.u0, this.v1, -halfWidth, halfHeight, packedSizeX, packedSizeY);
        addVertex(vertexConsumer, this.x + this.width, this.y + this.height, this.u1, this.v1, halfWidth, halfHeight, packedSizeX, packedSizeY);
        addVertex(vertexConsumer, this.x + this.width, this.y, this.u1, this.v0, halfWidth, -halfHeight, packedSizeX, packedSizeY);
    }

    private void addVertex(VertexConsumer vertexConsumer, float x, float y, float u, float v, float localX, float localY, int packedSizeX, int packedSizeY) {
        vertexConsumer.addVertex(transformX(x, y), transformY(x, y), 0.0F)
                .setColor(this.color)
                .setUv(u, v)
                .setUv1(UiVertexPacking.packSignedSize(localX), UiVertexPacking.packSignedSize(localY))
                .setUv2(packedSizeX, packedSizeY)
                .setNormal(0.0F, 0.0F, 1.0F)
                .setLineWidth(Math.max(this.cornerRadius, 0.0F));
    }

    @Override
    public RenderPipeline pipeline() {
        return GuiPipelines.TEXTURE;
    }

    @Override
    public TextureSetup textureSetup() {
        if (this.texture != null) {

            float uSpan = Math.max(Math.abs(this.u1 - this.u0), 0.01F);
            float vSpan = Math.max(Math.abs(this.v1 - this.v0), 0.01F);
            return this.texture.textureSetup(
                    this.width * this.deviceScale / uSpan,
                    this.height * this.deviceScale / vSpan
            );
        }

        return TextureSetup.singleTexture(
                MinecraftContext.mc.getTextureManager().getTexture(this.managedTextureId).getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, false)
        );
    }
}
