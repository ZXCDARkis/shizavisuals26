package org.shizavisuals.mixin.spatial;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.shizavisuals.feature.impl.visual.SpatialInventoryFeature;
import org.shizavisuals.utils.render.spatial.SpatialInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Рендер 3D-инвентаря: изолят-рендер до ванильного GUI, затем квад в мире,
 * плюс обновление кадра и мыши.
 */
@Mixin(GameRenderer.class)
public abstract class SpatialGameRendererMixin {
    @Final
    @Shadow
    private GameRenderState gameRenderState;
    @Final
    @Shadow
    private FogRenderer fogRenderer;

    @Inject(method = "render", at = @At("HEAD"))
    private void shizavisuals$onRenderHead(CallbackInfo ci) {
        SpatialInventory.updateFrame();
        if (SpatialInventory.shouldCapture()) {
            SpatialInventory.prepareTarget();
            SpatialInventory.onFrameStart();
        } else if (SpatialInventory.isClosing()) {
            SpatialInventory.onFrameStart();
        }
    }

    @Inject(
            method = "render",
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            )}
    )
    private void shizavisuals$beforeGuiRender(CallbackInfo ci) {
        SpatialInventoryFeature f = SpatialInventory.feature();
        if (f == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (SpatialInventory.shouldCapture()) {
            RenderSystem.setShaderFog(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
            SpatialInventory.renderIsolatedGui();
            SpatialInventory.renderInWorldPost();
            this.gameRenderState.guiRenderState.isHudHidden = false;
        } else if (SpatialInventory.isClosing()) {
            RenderSystem.setShaderFog(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
            SpatialInventory.renderInWorldPost();
        }
    }

    @Inject(method = "extract", at = @At("HEAD"))
    private void shizavisuals$refreshMousePosition(CallbackInfo ci) {
        SpatialInventory.refreshMousePosition();
    }
}
