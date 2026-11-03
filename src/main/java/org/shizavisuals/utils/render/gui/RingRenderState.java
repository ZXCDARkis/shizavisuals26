package org.shizavisuals.utils.render.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fc;

/**
 * 2D arc ring (spinner / progress): a full-circle faint base ring plus an
 * angular sweep arc with soft blend at both ends. Geometry is a bounding box;
 * the fragment shader derives the ring shape, so any radius fits one quad.
 */
public final class RingRenderState extends UiElementRenderState {
    private final float cx;
    private final float cy;
    private final float radius;
    private final float thickness;
    private final float startAngle;
    private final float sweepAngle;
    private final float blendDegrees;
    private final int baseColor;
    private final int arcColor;

    public RingRenderState(
            Matrix3x2fc pose,
            ScreenRectangle scissor,
            float cx,
            float cy,
            float radius,
            float thickness,
            float startAngle,
            float sweepAngle,
            float blendDegrees,
            int baseColor,
            int arcColor
    ) {
        super(
                pose,
                scissor,
                cx - boundingExtent(radius, thickness),
                cy - boundingExtent(radius, thickness),
                boundingExtent(radius, thickness) * 2.0F,
                boundingExtent(radius, thickness) * 2.0F
        );
        this.cx = cx;
        this.cy = cy;
        this.radius = radius;
        this.thickness = thickness;
        this.startAngle = startAngle;
        this.sweepAngle = sweepAngle;
        this.blendDegrees = blendDegrees;
        this.baseColor = baseColor;
        this.arcColor = arcColor;
    }

    private static float boundingExtent(float radius, float thickness) {
        return Math.max(radius + thickness * 0.5F + 0.5F, 1.0F);
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float extent = boundingExtent(this.radius, this.thickness);
        int packedSizeX = UiVertexPacking.packSize(extent);
        int packedStart = UiVertexPacking.packSignedSize(this.startAngle * 64.0F);
        int packedSweep = UiVertexPacking.packSignedSize(this.sweepAngle * 64.0F);
        float packedDual = UiVertexPacking.packDual12Raw(
                UiVertexPacking.packDualHi(this.radius),
                UiVertexPacking.packDualLo(this.blendDegrees)
        );
        float packedZ = UiVertexPacking.packZ(this.thickness, (this.arcColor >>> 24) & 0xFF, false);
        float effectRed = UiVertexPacking.snormChannel((this.arcColor >> 16) & 0xFF);
        float effectGreen = UiVertexPacking.snormChannel((this.arcColor >> 8) & 0xFF);
        float effectBlue = UiVertexPacking.snormChannel(this.arcColor & 0xFF);

        addVertex(vertexConsumer, this.cx - extent, this.cy - extent, -extent, -extent, packedSizeX, packedStart, packedSweep, packedDual, packedZ, effectRed, effectGreen, effectBlue, this.baseColor);
        addVertex(vertexConsumer, this.cx - extent, this.cy + extent, -extent, extent, packedSizeX, packedStart, packedSweep, packedDual, packedZ, effectRed, effectGreen, effectBlue, this.baseColor);
        addVertex(vertexConsumer, this.cx + extent, this.cy + extent, extent, extent, packedSizeX, packedStart, packedSweep, packedDual, packedZ, effectRed, effectGreen, effectBlue, this.baseColor);
        addVertex(vertexConsumer, this.cx + extent, this.cy - extent, extent, -extent, packedSizeX, packedStart, packedSweep, packedDual, packedZ, effectRed, effectGreen, effectBlue, this.baseColor);
    }

    private void addVertex(
            VertexConsumer vertexConsumer,
            float x,
            float y,
            float localX,
            float localY,
            int packedSize,
            int packedStart,
            int packedSweep,
            float packedDual,
            float packedZ,
            float effectRed,
            float effectGreen,
            float effectBlue,
            int baseColor
    ) {
        vertexConsumer.addVertex(transformX(x, y), transformY(x, y), packedZ)
                .setColor(baseColor)
                .setUv(localX, localY)
                .setUv1(packedSize, packedSize)
                .setUv2(packedStart, packedSweep)
                .setNormal(effectRed, effectGreen, effectBlue)
                .setLineWidth(packedDual);
    }

    @Override
    public RenderPipeline pipeline() {
        return GuiPipelines.RING;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
