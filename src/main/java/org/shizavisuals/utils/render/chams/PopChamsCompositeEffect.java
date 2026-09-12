package org.shizavisuals.utils.render.chams;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.shizavisuals.utils.render.post.PostPipelines;
import org.shizavisuals.utils.render.post.KawaseBlur;
import org.shizavisuals.utils.render.post.PostFx;

public final class PopChamsCompositeEffect {
    private static final int MAX_MIPS = 3;
    private static final int COMPOSITE_SIZE = new Std140SizeCalculator().putVec4().get();

    private final KawaseBlur blur = new KawaseBlur("shizavisuals-popchams");
    private GpuBuffer compositeUniforms;

    public void render(RenderTarget mask, RenderTarget output, int radius) {
        if (mask == null
                || output == null
                || mask.getColorTextureView() == null
                || output.getColorTextureView() == null
                || output.width <= 0
                || output.height <= 0) {
            return;
        }

        if (this.compositeUniforms == null) {
            this.compositeUniforms = PostFx.createUniforms("ShizaVisuals PopChams Composite UBO", COMPOSITE_SIZE);
        }
        float screenScale = Math.max(0.5F, output.height / 1080.0F);
        float scaledRadius = Math.max(0.05F, radius * screenScale);
        int levels = Math.clamp(
                (int) Math.floor(Math.log(scaledRadius) / Math.log(2.0D)) + 1,
                1,
                MAX_MIPS
        );
        float offset = Math.min(2.2F, 1.1F * (float) Math.pow(scaledRadius, 0.32F));
        float intensity = Math.min(1.5F, radius * 0.15F + 0.45F);

        GpuSampler sampler = PostFx.linearSampler();
        GpuTextureView blurred = this.blur.run(
                mask.getColorTextureView(),
                output.width,
                output.height,
                levels,
                offset,
                sampler
        );

        PostFx.writeUniforms(this.compositeUniforms, COMPOSITE_SIZE, builder ->
                builder.putVec4(intensity, 0.0F, 0.0F, 0.0F));
        PostFx.pass("ShizaVisuals PopChams Composite", PostPipelines.POPCHAMS_COMPOSITE, output, pass -> {
            pass.setUniform("PopChamsComposite", this.compositeUniforms);
            pass.bindTexture("BlurredSampler", blurred, sampler);
        });
    }

    public void release() {
        this.blur.release();
        if (this.compositeUniforms != null) {
            this.compositeUniforms.close();
            this.compositeUniforms = null;
        }
    }
}
