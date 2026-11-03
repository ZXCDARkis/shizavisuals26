package org.shizavisuals.mixin.gui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.shizavisuals.utils.math.FrameClock;
import org.shizavisuals.utils.render.gui.GuiPipelines;
import org.shizavisuals.utils.render.gui.GuiBackdrop;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow
    @Final
    private List<?> draws;

    @Unique
    private final IntList shizavisuals$glassSplits = new IntArrayList();
    @Unique
    private boolean shizavisuals$previousElementWasGlass;

    @Inject(method = "prepare", at = @At("HEAD"))
    private void shizavisuals$resetGlassSplits(CallbackInfo ci) {
        // One clock for the whole UI layer: HUD inertia, menu inertia and the
        // jelly squash all read the same delta, so nothing can disagree about how
        // long the frame was.
        FrameClock.beginFrame();
        this.shizavisuals$glassSplits.clear();
        this.shizavisuals$previousElementWasGlass = false;
    }

    @Inject(method = "addElementToMesh", at = @At("HEAD"))
    private void shizavisuals$markGlassRuns(GuiElementRenderState elementState, CallbackInfo ci) {
        boolean glass = shizavisuals$isGlassPipeline(elementState.pipeline());
        if (glass && !this.shizavisuals$previousElementWasGlass) {

            this.shizavisuals$glassSplits.add(this.draws.size());
        }
        this.shizavisuals$previousElementWasGlass = glass;
    }

    @WrapOperation(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;executeDrawRange(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;II)V"
            )
    )
    private void shizavisuals$captureBackdropBeforeGlassRuns(
            GuiRenderer instance,
            Supplier<String> label,
            RenderTarget mainRenderTarget,
            GpuBufferSlice dynamicTransforms,
            int startIndex,
            int endIndex,
            Operation<Void> original
    ) {
        int cursor = startIndex;
        for (int i = 0; i < this.shizavisuals$glassSplits.size(); i++) {
            int split = this.shizavisuals$glassSplits.getInt(i);
            if (split < cursor || split >= endIndex) {
                continue;
            }
            if (split > cursor) {
                original.call(instance, label, mainRenderTarget, dynamicTransforms, cursor, split);
            }
            GuiBackdrop.captureNow();
            cursor = split;
        }
        if (cursor < endIndex) {
            original.call(instance, label, mainRenderTarget, dynamicTransforms, cursor, endIndex);
        }
    }

    @Unique
    private static boolean shizavisuals$isGlassPipeline(RenderPipeline pipeline) {
        return pipeline == GuiPipelines.BLUR_RECT || pipeline == GuiPipelines.GLASS_SHADOW;
    }

    @WrapOperation(
            method = "submitBlitFromItemAtlas",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/SamplerCache;getRepeat(Lcom/mojang/blaze3d/textures/FilterMode;)Lcom/mojang/blaze3d/textures/GpuSampler;"
            )
    )
    private GpuSampler shizavisuals$smoothDownscaledItems(
            SamplerCache cache,
            FilterMode filterMode,
            Operation<GpuSampler> original,
            @Local(argsOnly = true) GuiItemRenderState itemState
    ) {
        if (shizavisuals$isDownscaled(itemState)) {
            return original.call(cache, FilterMode.LINEAR);
        }
        return original.call(cache, filterMode);
    }

    @WrapOperation(
            method = "submitBlitFromItemAtlas",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/RenderPipelines;GUI_TEXTURED_PREMULTIPLIED_ALPHA:Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
            )
    )
    private RenderPipeline shizavisuals$bicubicDownscalePipeline(
            Operation<RenderPipeline> original,
            @Local(argsOnly = true) GuiItemRenderState itemState
    ) {
        if (shizavisuals$isDownscaled(itemState)) {
            return GuiPipelines.itemDownscale(Minecraft.getInstance().getWindow().getGuiScale());
        }
        return original.call();
    }

    @Unique
    private static boolean shizavisuals$isDownscaled(GuiItemRenderState itemState) {
        return itemState.pose().m00 < 0.999F;
    }
}
