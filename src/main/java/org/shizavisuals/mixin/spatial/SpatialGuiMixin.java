package org.shizavisuals.mixin.spatial;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.utils.render.spatial.SpatialInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Перенаправляет extract экрана в изолированный GuiRenderState. */
@Mixin(Gui.class)
public class SpatialGuiMixin {
    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
            ),
            index = 0
    )
    private GuiGraphicsExtractor shizavisuals$extractScreenIntoIsolatedState(GuiGraphicsExtractor graphics) {
        if (SpatialInventory.shouldCapture()) {
            return SpatialInventory.createIsolatedGraphics();
        }
        return graphics;
    }
}
