package org.shizavisuals.mixin.core;

import net.minecraft.util.Util;
import net.minecraft.util.thread.BlockableEventLoop;
import org.shizavisuals.context.RenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {
    @Inject(method = "runAllTasks", at = @At("HEAD"), cancellable = true)
    private void onRunAllTasks(CallbackInfo ci) {
        if (RenderContext.overlayStartTime > -1L) {
            long elapsed = Util.getMillis() - RenderContext.overlayStartTime;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            boolean isStartup = (mc.gui.screen() == null);
            long maxDeferTime = isStartup ? 3000L : 1800L;
            if (elapsed < maxDeferTime) {
                ci.cancel();
            }
        }
    }
}
