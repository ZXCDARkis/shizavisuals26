package org.shizavisuals.utils.render.post;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.ArrayList;
import java.util.List;

public final class KawaseBlur {

    public static final int ESP_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putVec2()
            .putFloat()
            .get();

    public static final int HANDS_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putFloat()
            .putFloat()
            .get();

    @FunctionalInterface
    public interface UniformWriter {
        void write(Std140Builder builder, float inputWidth, float inputHeight, float offset, float alpha);
    }

    private final String name;
    private final RenderPipeline downPipeline;
    private final RenderPipeline upPipeline;
    private final String downUniformName;
    private final String upUniformName;
    private final int uniformSize;
    private final UniformWriter uniformWriter;
    private final List<TextureTarget> mips = new ArrayList<>(4);
    private final List<GpuBuffer> passUniforms = new ArrayList<>(8);

    public KawaseBlur(String name) {
        this(
                name,
                PostPipelines.ESP_KAWASE_DOWN,
                PostPipelines.ESP_KAWASE_UP,
                "KawaseDownUniforms",
                "KawaseUpUniforms",
                ESP_UNIFORM_SIZE,
                (builder, inputWidth, inputHeight, offset, alpha) -> builder
                        .putVec2(0.5F / inputWidth, 0.5F / inputHeight)
                        .putVec2(0.0F, 0.0F)
                        .putFloat(offset)
        );
    }

    public KawaseBlur(String name,
                      RenderPipeline downPipeline,
                      RenderPipeline upPipeline,
                      String downUniformName,
                      String upUniformName,
                      int uniformSize,
                      UniformWriter uniformWriter) {
        this.name = name;
        this.downPipeline = downPipeline;
        this.upPipeline = upPipeline;
        this.downUniformName = downUniformName;
        this.upUniformName = upUniformName;
        this.uniformSize = uniformSize;
        this.uniformWriter = uniformWriter;
    }

    public GpuTextureView run(GpuTextureView source, int width, int height, int levels, float offset, GpuSampler sampler) {
        return run(source, width, height, levels, offset, offset, 1.0F, 1.0F, sampler);
    }

    public GpuTextureView run(GpuTextureView source,
                              int width,
                              int height,
                              int levels,
                              float downOffset,
                              float upOffset,
                              float downAlpha,
                              float upAlpha,
                              GpuSampler sampler) {
        int levelCount = Math.max(1, levels);
        ensureMips(width, height, levelCount);

        int passIndex = 0;
        GpuTextureView input = source;
        float inputWidth = width;
        float inputHeight = height;
        for (int index = 0; index < levelCount; index++) {
            TextureTarget destination = this.mips.get(index);
            runPass(passIndex++, this.downPipeline, this.downUniformName, destination, input, inputWidth, inputHeight, downOffset, downAlpha, sampler);
            input = destination.getColorTextureView();
            inputWidth = destination.width;
            inputHeight = destination.height;
        }

        for (int index = levelCount - 1; index > 0; index--) {
            TextureTarget inputTarget = this.mips.get(index);
            TextureTarget destination = this.mips.get(index - 1);
            runPass(passIndex++, this.upPipeline, this.upUniformName, destination, inputTarget.getColorTextureView(), inputTarget.width, inputTarget.height, upOffset, upAlpha, sampler);
        }
        return this.mips.getFirst().getColorTextureView();
    }

    private void runPass(int passIndex,
                         RenderPipeline pipeline,
                         String uniformName,
                         TextureTarget destination,
                         GpuTextureView input,
                         float inputWidth,
                         float inputHeight,
                         float offset,
                         float alpha,
                         GpuSampler sampler) {
        GpuBuffer uniforms = passUniforms(passIndex);
        PostFx.writeUniforms(uniforms, this.uniformSize, builder ->
                this.uniformWriter.write(builder, inputWidth, inputHeight, offset, alpha));
        PostFx.pass(this.name + " blur", pipeline, destination.getColorTextureView(), pass -> {
            pass.setUniform(uniformName, uniforms);
            pass.bindTexture("CurrentInput", input, sampler);
        });
    }

    private GpuBuffer passUniforms(int index) {
        while (this.passUniforms.size() <= index) {
            int slot = this.passUniforms.size();
            this.passUniforms.add(PostFx.createUniforms(this.name + " kawase UBO " + slot, this.uniformSize));
        }
        return this.passUniforms.get(index);
    }

    public void release() {
        for (TextureTarget mip : this.mips) {
            if (mip != null) {
                mip.destroyBuffers();
            }
        }
        this.mips.clear();
        for (GpuBuffer uniforms : this.passUniforms) {
            uniforms.close();
        }
        this.passUniforms.clear();
    }

    private void ensureMips(int width, int height, int levels) {
        while (this.mips.size() < levels) {
            this.mips.add(null);
        }
        int mipWidth = width;
        int mipHeight = height;
        for (int index = 0; index < levels; index++) {
            mipWidth = Math.max(1, mipWidth / 2);
            mipHeight = Math.max(1, mipHeight / 2);
            TextureTarget current = this.mips.get(index);
            if (current == null || current.width != mipWidth || current.height != mipHeight) {
                if (current != null) {
                    current.destroyBuffers();
                }
                this.mips.set(index, new TextureTarget(
                        this.name + "-mip-" + index,
                        mipWidth,
                        mipHeight,
                        false,
                        PostPipelines.EFFECT_FORMAT
                ));
            }
        }
    }
}
