package org.shizavisuals.mixin.gui;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.misc.InventoryManagerFeature;
import org.shizavisuals.mixin.accessor.AbstractContainerScreenAccessor;
import org.shizavisuals.utils.inventory.InventoryOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * InventoryScreen OVERRIDES extractRenderState, so an inject into the base
 * AbstractContainerScreen method never runs for it — this mixin must target
 * the subclass directly.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenRenderMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void inv$overlay(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                             int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (FeatureManager.INSTANCE.getEnabled(InventoryManagerFeature.class) == null) {
            return;
        }
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) this;
        InventoryOverlay.render(acc.getLeftPos(), acc.getTopPos(),
                acc.getImageWidth(), acc.getImageHeight(), mouseX, mouseY, graphics);
    }
}
