package org.shizavisuals.utils.render.spatial;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;

/** Offscreen-таргет, в который рендерится изолированный GUI экрана. */
public final class SpatialScreenTexture {
    private TextureTarget target;

    public void clearTarget() {
        if (this.target != null) {
            GpuTexture colorTexture = this.target.getColorTexture();
            GpuTexture depthTexture = this.target.getDepthTexture();
            if (colorTexture != null) {
                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.clearColorTexture(colorTexture, new Vector4f(0.0F, 0.0F, 0.0F, 0.0F));
                if (depthTexture != null) {
                    encoder.clearDepthTexture(depthTexture, 1.0);
                }
            }
        }
    }

    public void prepareTarget() {
        Minecraft client = Minecraft.getInstance();
        int windowWidth = client.getWindow().getWidth();
        int windowHeight = client.getWindow().getHeight();
        if (windowWidth > 0 && windowHeight > 0) {
            if (this.target == null) {
                this.target = new TextureTarget("ShizaVisuals Spatial GUI", windowWidth, windowHeight, true, GpuFormat.RGBA8_UNORM);
                this.clearTarget();
            } else if (this.target.width != windowWidth || this.target.height != windowHeight) {
                this.target.resize(windowWidth, windowHeight);
                this.clearTarget();
            }
        }
    }

    public TextureTarget getTarget() {
        return this.target;
    }

    public boolean hasTexture() {
        return this.target != null && this.target.getColorTextureView() != null;
    }
}
