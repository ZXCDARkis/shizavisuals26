package org.shizavisuals.utils.render.world;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import org.shizavisuals.feature.impl.visual.ShaderHandsFeature;
import org.shizavisuals.utils.render.post.PostFx;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.post.FullscreenQuad;
import org.shizavisuals.utils.render.post.PostPipelines;
import org.shizavisuals.utils.render.post.KawaseBlur;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ShaderHandsRenderer {
    private static final Vector4f CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
    private static final int MAX_BLUR_LEVELS = 4;

    private static final int MASK_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putVec2()
            .get();
    private static final int FILL_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();
    private static final int GLASS_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putFloat()
            .get();
    private static final int OUTLINE_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putFloat()
            .get();
    private static final int HALO_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putFloat()
            .get();
    private static final int TRAIL_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putFloat()
            .putFloat()
            .putFloat()
            .putFloat()
            .get();
    private static final int FLAME_UNIFORM_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putFloat()
            .get();
    private final GpuBuffer maskUniforms = uniformBuffer("ShizaVisuals Arm Fill Mask UBO", MASK_UNIFORM_SIZE);
    private final GpuBuffer fillUniforms = uniformBuffer("ShizaVisuals Arm Fill UBO", FILL_UNIFORM_SIZE);
    private final GpuBuffer glassUniforms = uniformBuffer("ShizaVisuals Arm Glass UBO", GLASS_UNIFORM_SIZE);
    private final GpuBuffer outlineUniforms = uniformBuffer("ShizaVisuals Arm Outline UBO", OUTLINE_UNIFORM_SIZE);
    private final GpuBuffer haloUniforms = uniformBuffer("ShizaVisuals Arm Halo UBO", HALO_UNIFORM_SIZE);
    private final GpuBuffer trailUniforms = uniformBuffer("ShizaVisuals Arm Flame Accum UBO", TRAIL_UNIFORM_SIZE);
    private final GpuBuffer flameUniforms = uniformBuffer("ShizaVisuals Arm Flame UBO", FLAME_UNIFORM_SIZE);

    private final KawaseBlur blur = new KawaseBlur(
            "shizavisuals-arm-blur",
            PostPipelines.HAND_BLUR_DOWN,
            PostPipelines.HAND_BLUR_UP,
            "HandBlurUniforms",
            "HandBlurUniforms",
            KawaseBlur.HANDS_UNIFORM_SIZE,
            (builder, inputWidth, inputHeight, offset, alpha) -> builder
                    .putVec2(1.0F / inputWidth, 1.0F / inputHeight)
                    .putFloat(offset)
                    .putFloat(alpha)
    );
    private TextureTarget beforeTarget;
    private TextureTarget maskRawTarget;
    private TextureTarget maskTarget;
    private TextureTarget trailA;
    private TextureTarget trailB;
    private boolean trailUsesA;
    private boolean flameHistoryActive;

    public void render(ShaderHandsFeature feature, Runnable handDraw) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        if (!validMainTarget(mainTarget)) {
            handDraw.run();
            return;
        }

        ensureTargets(mainTarget.width, mainTarget.height);
        if (!resourcesReady()) {
            handDraw.run();
            return;
        }

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(
                mainTarget.getColorTexture(),
                this.beforeTarget.getColorTexture(),
                0, 0, 0, 0, 0,
                mainTarget.width,
                mainTarget.height
        );
        encoder.copyTextureToTexture(
                mainTarget.getDepthTexture(),
                this.beforeTarget.getDepthTexture(),
                0, 0, 0, 0, 0,
                mainTarget.width,
                mainTarget.height
        );

        handDraw.run();

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        writeMaskUniforms(feature, mainTarget.width, mainTarget.height);
        renderMask(mainTarget, sampler);

        if (feature.hasFill()) {
            if (feature.hasGlass()) {
                GpuTextureView scene = this.beforeTarget.getColorTextureView();
                float blurRadius = feature.glassBlur.getValue().floatValue();
                if (blurRadius > 0.001F) {
                    scene = blurTexture(
                            scene,
                            mainTarget.width,
                            mainTarget.height,
                            blurRadius,
                            false,
                            sampler
                    );
                }
                writeGlassUniforms(feature);
                renderGlass(mainTarget, scene, sampler);
            } else {
                writeFillUniforms(feature);
                renderFill(feature, mainTarget, sampler);
            }
        }

        if (feature.hasOutline()) {
            writeOutlineUniforms(feature);
            renderOutline(mainTarget, sampler);
        }

        if (feature.hasGlow()) {
            GpuTextureView blurredMask = blurTexture(
                    this.maskTarget.getColorTextureView(),
                    mainTarget.width,
                    mainTarget.height,
                    feature.glowRadius.getValue().floatValue(),
                    true,
                    sampler
            );
            if (feature.hasFlame()) {
                float time = PostFx.shaderTime() * feature.flameSpeed.getValue().floatValue();
                writeTrailUniforms(feature, mainTarget.width, mainTarget.height, time);
                GpuTextureView flame = renderTrail(blurredMask, sampler);
                writeFlameUniforms(feature, time);
                renderFlame(mainTarget, flame, sampler);
                this.flameHistoryActive = true;
            } else {
                clearFlameHistoryIfNeeded();
                writeHaloUniforms(feature);
                renderHalo(mainTarget, blurredMask, sampler);
            }
        } else {
            clearFlameHistoryIfNeeded();
        }
    }

    public void clearHistory() {
        this.trailUsesA = false;
        this.flameHistoryActive = false;
        clear(this.trailA);
        clear(this.trailB);
    }

    public void release() {
        this.beforeTarget = destroy(this.beforeTarget);
        this.maskRawTarget = destroy(this.maskRawTarget);
        this.maskTarget = destroy(this.maskTarget);
        this.trailA = destroy(this.trailA);
        this.trailB = destroy(this.trailB);
        this.blur.release();
        this.maskUniforms.close();
        this.fillUniforms.close();
        this.glassUniforms.close();
        this.outlineUniforms.close();
        this.haloUniforms.close();
        this.trailUniforms.close();
        this.flameUniforms.close();
    }

    private static TextureTarget destroy(TextureTarget target) {
        if (target != null) {
            target.destroyBuffers();
        }
        return null;
    }

    private void clearFlameHistoryIfNeeded() {
        if (this.flameHistoryActive) {
            clearHistory();
        }
    }

    private void renderMask(RenderTarget mainTarget, GpuSampler sampler) {

        try (RenderPass pass = renderPass("ShizaVisuals Arm Fill mask", this.maskRawTarget)) {
            pass.setPipeline(PostPipelines.HAND_MASK);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandMaskUniforms", this.maskUniforms);
            pass.bindTexture("BeforeTexture", this.beforeTarget.getColorTextureView(), sampler);
            pass.bindTexture("AfterTexture", mainTarget.getColorTextureView(), sampler);
            pass.bindTexture("BeforeDepth", this.beforeTarget.getDepthTextureView(), sampler);
            pass.bindTexture("AfterDepth", mainTarget.getDepthTextureView(), sampler);
            draw(pass);
        }
        try (RenderPass pass = renderPass("ShizaVisuals Arm Fill mask smooth", this.maskTarget)) {
            pass.setPipeline(PostPipelines.HAND_MASK_SMOOTH);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandMaskUniforms", this.maskUniforms);
            pass.bindTexture("RawMask", this.maskRawTarget.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private void renderFill(ShaderHandsFeature feature,
                            RenderTarget mainTarget,
                            GpuSampler sampler) {
        try (RenderPass pass = renderPass("ShizaVisuals Arm fill", mainTarget)) {
            pass.setPipeline(feature.hasPlasma()
                    ? PostPipelines.HAND_PLASMA
                    : PostPipelines.HAND_FILL);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandFillUniforms", this.fillUniforms);
            pass.bindTexture("MaskSampler", this.maskTarget.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private void renderGlass(RenderTarget mainTarget,
                             GpuTextureView scene,
                             GpuSampler sampler) {
        try (RenderPass pass = renderPass("ShizaVisuals Arm glass fill", mainTarget)) {
            pass.setPipeline(PostPipelines.HAND_GLASS);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandGlassUniforms", this.glassUniforms);
            pass.bindTexture("SceneSampler", scene, sampler);
            pass.bindTexture("MaskSampler", this.maskTarget.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private void renderOutline(RenderTarget mainTarget, GpuSampler sampler) {
        try (RenderPass pass = renderPass("ShizaVisuals Arm outline", mainTarget)) {
            pass.setPipeline(PostPipelines.HAND_OUTLINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandOutlineUniforms", this.outlineUniforms);
            pass.bindTexture("MaskSampler", this.maskTarget.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private void renderHalo(RenderTarget mainTarget,
                            GpuTextureView blurredMask,
                            GpuSampler sampler) {
        try (RenderPass pass = renderPass("ShizaVisuals Arm halo", mainTarget)) {
            pass.setPipeline(PostPipelines.HAND_HALO);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandHaloUniforms", this.haloUniforms);
            pass.bindTexture("BlurredSampler", blurredMask, sampler);
            pass.bindTexture("MaskSampler", this.maskTarget.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private GpuTextureView renderTrail(GpuTextureView injectTexture, GpuSampler sampler) {
        TextureTarget source = this.trailUsesA ? this.trailA : this.trailB;
        TextureTarget destination = this.trailUsesA ? this.trailB : this.trailA;
        try (RenderPass pass = renderPass("ShizaVisuals Arm flame accumulation", destination)) {
            pass.setPipeline(PostPipelines.HAND_TRAIL);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandTrailUniforms", this.trailUniforms);
            pass.bindTexture("PrevSampler", source.getColorTextureView(), sampler);
            pass.bindTexture("InjectSampler", injectTexture, sampler);
            draw(pass);
        }
        this.trailUsesA = !this.trailUsesA;
        return destination.getColorTextureView();
    }

    private void renderFlame(RenderTarget mainTarget,
                             GpuTextureView flame,
                             GpuSampler sampler) {
        try (RenderPass pass = renderPass("ShizaVisuals Arm flame composite", mainTarget)) {
            pass.setPipeline(PostPipelines.SHADER_HANDS);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("HandCompositeUniforms", this.flameUniforms);
            pass.bindTexture("BlurredSampler", flame, sampler);
            pass.bindTexture("MaskSampler", this.maskTarget.getColorTextureView(), sampler);
            draw(pass);
        }
    }

    private GpuTextureView blurTexture(GpuTextureView source,
                                       int width,
                                       int height,
                                       float radius,
                                       boolean boostAlpha,
                                       GpuSampler sampler) {
        int levels = Math.clamp((int) Math.ceil(radius / 16.0F), 1, MAX_BLUR_LEVELS);
        float offset = Math.max(0.5F, radius / 18.0F);
        return this.blur.run(
                source,
                width,
                height,
                levels,
                offset,
                offset,
                boostAlpha ? 1.12F : 1.0F,
                boostAlpha ? 1.04F : 1.0F,
                sampler
        );
    }

    private void writeMaskUniforms(ShaderHandsFeature feature, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean left = feature.bothHands.getValue();
        boolean right = feature.bothHands.getValue();
        if (!feature.bothHands.getValue()) {
            HumanoidArm mainArm = minecraft.player == null ? HumanoidArm.RIGHT : minecraft.player.getMainArm();
            left = mainArm == HumanoidArm.LEFT;
            right = mainArm == HumanoidArm.RIGHT;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, MASK_UNIFORM_SIZE)
                    .putVec2(1.0F / width, 1.0F / height)
                    .putVec2(left ? 1.0F : 0.0F, right ? 1.0F : 0.0F)
                    .get();
            write(this.maskUniforms, data);
        }
    }

    private void writeFillUniforms(ShaderHandsFeature feature) {
        int color = feature.resolvedColor();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, FILL_UNIFORM_SIZE)
                    .putVec4(
                            channel(ColorUtil.red(color)),
                            channel(ColorUtil.green(color)),
                            channel(ColorUtil.blue(color)),
                            feature.fillOpacity.getValue().floatValue()
                    )
                    .putVec4(
                            PostFx.shaderTime() * feature.plasmaSpeed.getValue().floatValue(),
                            0.0F,
                            0.0F,
                            0.0F
                    )
                    .get();
            write(this.fillUniforms, data);
        }
    }

    private void writeGlassUniforms(ShaderHandsFeature feature) {
        int color = feature.resolvedColor();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, GLASS_UNIFORM_SIZE)
                    .putVec4(
                            channel(ColorUtil.red(color)),
                            channel(ColorUtil.green(color)),
                            channel(ColorUtil.blue(color)),
                            feature.fillOpacity.getValue().floatValue()
                    )
                    .putFloat(feature.mirror.getValue() ? 1.0F : 0.0F)
                    .get();
            write(this.glassUniforms, data);
        }
    }

    private void writeOutlineUniforms(ShaderHandsFeature feature) {
        int color = feature.resolvedColor();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, OUTLINE_UNIFORM_SIZE)
                    .putVec4(
                            channel(ColorUtil.red(color)),
                            channel(ColorUtil.green(color)),
                            channel(ColorUtil.blue(color)),
                            1.0F
                    )
                    .putFloat(feature.outlineThickness.getValue().floatValue())
                    .get();
            write(this.outlineUniforms, data);
        }
    }

    private void writeHaloUniforms(ShaderHandsFeature feature) {
        int color = feature.resolvedColor();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, HALO_UNIFORM_SIZE)
                    .putVec4(
                            channel(ColorUtil.red(color)),
                            channel(ColorUtil.green(color)),
                            channel(ColorUtil.blue(color)),
                            feature.glowStrength.getValue().floatValue()
                    )
                    .putFloat(feature.glowRadius.getValue().floatValue() / 12.0F)
                    .get();
            write(this.haloUniforms, data);
        }
    }

    private void writeTrailUniforms(ShaderHandsFeature feature, int width, int height, float time) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, TRAIL_UNIFORM_SIZE)
                    .putVec2(1.0F / width, 1.0F / height)
                    .putFloat(feature.flameTrail.getValue().floatValue())
                    .putFloat(feature.flameSpeed.getValue().floatValue())
                    .putFloat(time)
                    .putFloat(0.0F)
                    .get();
            write(this.trailUniforms, data);
        }
    }

    private void writeFlameUniforms(ShaderHandsFeature feature, float time) {
        int color = feature.resolvedColor();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, FLAME_UNIFORM_SIZE)
                    .putVec4(
                            channel(ColorUtil.red(color)),
                            channel(ColorUtil.green(color)),
                            channel(ColorUtil.blue(color)),
                            feature.glowStrength.getValue().floatValue()
                    )
                    .putFloat(time)
                    .get();
            write(this.flameUniforms, data);
        }
    }

    private void ensureTargets(int width, int height) {
        boolean resetTrail = this.trailA == null
                || this.trailB == null
                || this.trailA.width != width
                || this.trailA.height != height
                || this.trailB.width != width
                || this.trailB.height != height;
        this.beforeTarget = ensureTarget(this.beforeTarget, "shizavisuals-arm-before", width, height, true, PostPipelines.EFFECT_FORMAT);
        this.maskRawTarget = ensureTarget(this.maskRawTarget, "shizavisuals-arm-mask-raw", width, height, false, PostPipelines.MASK_RAW_FORMAT);
        this.maskTarget = ensureTarget(this.maskTarget, "shizavisuals-arm-mask", width, height, false, PostPipelines.EFFECT_FORMAT);
        this.trailA = ensureTarget(this.trailA, "shizavisuals-arm-flame-a", width, height, false, PostPipelines.EFFECT_FORMAT);
        this.trailB = ensureTarget(this.trailB, "shizavisuals-arm-flame-b", width, height, false, PostPipelines.EFFECT_FORMAT);
        if (resetTrail) {
            clearHistory();
        }
    }

    private TextureTarget ensureTarget(TextureTarget target,
                                       String label,
                                       int width,
                                       int height,
                                       boolean useDepth,
                                       GpuFormat format) {
        if (target != null && target.width == width && target.height == height) {
            return target;
        }
        if (target != null) {
            target.destroyBuffers();
        }
        return new TextureTarget(label, width, height, useDepth, format);
    }

    private boolean resourcesReady() {
        return ready(this.beforeTarget)
                && ready(this.maskRawTarget)
                && ready(this.maskTarget)
                && ready(this.trailA)
                && ready(this.trailB)
                && this.beforeTarget.getDepthTexture() != null
                && this.beforeTarget.getDepthTextureView() != null;
    }

    private static boolean ready(TextureTarget target) {
        return target != null
                && target.getColorTexture() != null
                && target.getColorTextureView() != null;
    }

    private static boolean validMainTarget(RenderTarget target) {
        return target != null
                && target.width > 0
                && target.height > 0
                && target.getColorTexture() != null
                && target.getColorTextureView() != null
                && target.getDepthTexture() != null
                && target.getDepthTextureView() != null;
    }

    private static RenderPass renderPass(String label, RenderTarget target) {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> label,
                target.getColorTextureView(),
                Optional.empty()
        );
    }

    private static void draw(RenderPass pass) {
        pass.setVertexBuffer(0, FullscreenQuad.buffer().slice());
        pass.draw(FullscreenQuad.vertexCount(), 1, 0, 0);
    }

    private static void clear(TextureTarget target) {
        if (target != null && target.getColorTexture() != null) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), CLEAR);
        }
    }

    private static void write(GpuBuffer buffer, ByteBuffer data) {
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
    }

    private static float channel(int value) {
        return value / 255.0F;
    }

    private static GpuBuffer uniformBuffer(String label, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> label,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
    }
}
