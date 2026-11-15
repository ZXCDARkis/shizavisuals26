package org.shizavisuals.mixin.spatial;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.shizavisuals.utils.render.spatial.SpatialInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class SpatialScreenMixin {
    @Inject(method = "init(II)V", at = @At("HEAD"))
    private void shizavisuals$hookOnShow(int width, int height, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        boolean active = SpatialInventory.isActive();
        boolean hook = SpatialInventory.shouldHook(self);
        org.slf4j.LoggerFactory.getLogger("ShizaVisuals-Spatial")
                .info("Screen init: {} level={} active={} hook={}",
                        self.getClass().getName(),
                        net.minecraft.client.Minecraft.getInstance().level != null,
                        active, hook);
        if (hook) {
            SpatialInventory.hookScreen(self);
        }
    }

    @Unique
    private boolean shizavisuals$isHooked() {
        Screen self = (Screen) (Object) this;
        return SpatialInventory.shouldCapture() && SpatialInventory.getHookedScreen() == self;
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void shizavisuals$removeBackground(GuiGraphicsExtractor graphics, int width, int height, float partialTick, CallbackInfo ci) {
        if (this.shizavisuals$isHooked()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void shizavisuals$removeTransparentBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.shizavisuals$isHooked()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void shizavisuals$removeBlur(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.shizavisuals$isHooked()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractMenuBackground", at = @At("HEAD"), cancellable = true)
    private void shizavisuals$removeMenuBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.shizavisuals$isHooked()) {
            ci.cancel();
        }
    }
}
