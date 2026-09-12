package org.shizavisuals.webpanel;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.shizavisuals.utils.render.post.KawaseBlur;
import org.shizavisuals.utils.render.post.PostFx;
import org.shizavisuals.utils.render.post.PostPipelines;

public final class WebPanelBlur {
    private WebPanelBlur() {
    }

    public static final int DOWNSCALE = 8;

    private static final KawaseBlur BLUR = new KawaseBlur(
            "WebPanel",
            PostPipelines.ESP_KAWASE_DOWN,
            PostPipelines.ESP_KAWASE_UP,
            "KawaseDownUniforms",
            "KawaseUpUniforms",
            KawaseBlur.ESP_UNIFORM_SIZE,
            (builder, inputWidth, inputHeight, offset, alpha) -> builder
                    .putVec2(0.5F / inputWidth, 0.5F / inputHeight)
                    .putVec2(0.0F, 0.0F)
                    .putFloat(offset)
    );

    @Nullable
    public static GpuTextureView capture(float sigma) {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTextureView colorView = main != null ? main.getColorTextureView() : null;
        if (colorView == null || main.width <= 1 || main.height <= 1) {
            return null;
        }

        try {
            int hw = Math.max(2, main.width / DOWNSCALE);
            int hh = Math.max(2, main.height / DOWNSCALE);
            float offset = Math.min(4.0f, Math.max(0.35f, sigma / 6.0f));
            return BLUR.run(colorView, hw, hh, 1, offset, offset * 0.75f,
                    1.0f, 1.0f, PostFx.linearSampler());
        } catch (Throwable t) {
            return null;
        }
    }

    public static void release() {
        BLUR.release();
    }
}
