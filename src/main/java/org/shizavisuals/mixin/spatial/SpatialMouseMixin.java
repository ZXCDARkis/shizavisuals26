package org.shizavisuals.mixin.spatial;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.joml.Vector2d;
import org.shizavisuals.utils.render.spatial.SpatialInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Перенаправляет курсор на квад: raw-позиция + scaled-переопределение. */
@Mixin(value = MouseHandler.class, priority = 1100)
public class SpatialMouseMixin {
    @ModifyReturnValue(
            method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D",
            at = @At("RETURN")
    )
    private double shizavisuals$overrideScaledX(double original) {
        return SpatialInventory.mouseOverrideActive() ? SpatialInventory.getLastPosX(original) : original;
    }

    @ModifyReturnValue(
            method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D",
            at = @At("RETURN")
    )
    private double shizavisuals$overrideScaledY(double original) {
        return SpatialInventory.mouseOverrideActive() ? SpatialInventory.getLastPosY(original) : original;
    }

    @WrapMethod(method = "onMove")
    private void shizavisuals$onMove(long handle, double x, double y, Operation<Void> original) {
        Vector2d mapped = SpatialInventory.remapMove(handle, x, y);
        if (mapped != null) {
            x = mapped.x;
            y = mapped.y;
        }
        original.call(handle, x, y);
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"))
    private void shizavisuals$capturePreRelease(CallbackInfo ci) {
        SpatialInventory.onPreReleaseMouse();
    }

    @Inject(method = "releaseMouse", at = @At("TAIL"))
    private void shizavisuals$restoreCursorFields(CallbackInfo ci) {
        SpatialInventory.onPostReleaseMouse();
    }
}
