package org.shizavisuals.utils.render.jump;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

final class SceneSnapshot {
    private final String label;
    private TextureTarget copy;

    SceneSnapshot(String label) {
        this.label = label;
    }

    TextureTarget capture() {
        var mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (mainTarget == null || mainTarget.getColorTexture() == null || mainTarget.getColorTextureView() == null) {
            return null;
        }

        if (this.copy == null || this.copy.width != mainTarget.width || this.copy.height != mainTarget.height) {
            if (this.copy != null) {
                this.copy.destroyBuffers();
            }
            this.copy = new TextureTarget(this.label, mainTarget.width, mainTarget.height, false, GpuFormat.RGBA8_UNORM);
        }

        if (this.copy.getColorTexture() == null || this.copy.getColorTextureView() == null) {
            return null;
        }

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                mainTarget.getColorTexture(),
                this.copy.getColorTexture(),
                0, 0, 0, 0, 0,
                mainTarget.width,
                mainTarget.height
        );
        return this.copy;
    }

    void release() {
        if (this.copy != null) {
            this.copy.destroyBuffers();
            this.copy = null;
        }
    }
}
