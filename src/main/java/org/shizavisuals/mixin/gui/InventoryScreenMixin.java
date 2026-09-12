package org.shizavisuals.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.misc.InventoryManagerFeature;
import org.shizavisuals.mixin.accessor.AbstractContainerScreenAccessor;
import org.shizavisuals.utils.inventory.InventoryManager;
import org.shizavisuals.utils.inventory.InventoryOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class)
public abstract class InventoryScreenMixin {

    private static boolean inv$active() {
        return FeatureManager.INSTANCE.getEnabled(InventoryManagerFeature.class) != null;
    }

    private static Minecraft inv$mc() {
        return Minecraft.getInstance();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void inv$click(net.minecraft.client.input.MouseButtonEvent event, boolean doubled,
                           CallbackInfoReturnable<Boolean> cir) {
        if (!inv$active() || !((Object) this instanceof InventoryScreen)) {
            return;
        }
        InventoryManager m = InventoryManager.get();
        double mx = event.x();
        double my = event.y();

        if (InventoryManager.inside(mx, my, m.chestBtnRect)) {
            inv$mc().setScreenAndShow(new org.shizavisuals.gui.InventoryManagerGuiScreen(inv$mc().gui.screen()));
            cir.setReturnValue(true);
            return;
        }
        if (m.store().active() != null && InventoryManager.inside(mx, my, m.arrangeBtnRect)) {
            m.toggleArrange();
            cir.setReturnValue(true);
            return;
        }
        List<float[]> rows = m.missingRowRects;
        for (int i = 0; i < rows.size(); i++) {
            if (InventoryManager.inside(mx, my, rows.get(i))) {
                m.searchAuction(m.missingRowNames.get(i));
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void inv$scroll(double mouseX, double mouseY, double scrollX, double scrollY,
                            CallbackInfoReturnable<Boolean> cir) {
        if (!inv$active() || !((Object) this instanceof InventoryScreen)) {
            return;
        }
        InventoryManager m = InventoryManager.get();
        if (m.missingScrollMax <= 0) {
            return;
        }
        if (InventoryManager.inside(mouseX, mouseY, m.missingPanelRect)) {
            m.missingScroll -= (int) Math.signum(scrollY);
            m.missingScroll = Math.max(0, Math.min(m.missingScroll, m.missingScrollMax));
            cir.setReturnValue(true);
        }
    }
}
