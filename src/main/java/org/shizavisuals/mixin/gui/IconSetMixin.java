package org.shizavisuals.mixin.gui;

import com.mojang.blaze3d.platform.IconSet;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;

@Mixin(IconSet.class)
public class IconSetMixin {

    @Inject(method = "getStandardIcons", at = @At("HEAD"), cancellable = true)
    private void onGetStandardIcons(PackResources resources, CallbackInfoReturnable<List<IoSupplier<InputStream>>> cir) {
        try {
            IoSupplier<InputStream> icon16 = () -> IconSetMixin.class.getResourceAsStream("/assets/shizavisuals/textures/gui/icon_16.png");
            IoSupplier<InputStream> icon32 = () -> IconSetMixin.class.getResourceAsStream("/assets/shizavisuals/textures/gui/icon_32.png");

            if (icon16.get() != null && icon32.get() != null) {
                cir.setReturnValue(List.of(icon16, icon32));
            }
        } catch (Exception e) {
            System.err.println("[ShizaVisuals] Failed to load custom standard icons: " + e.getMessage());
        }
    }

    @Inject(method = "getMacIcon", at = @At("HEAD"), cancellable = true)
    private void onGetMacIcon(PackResources resources, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        try {
            IoSupplier<InputStream> macIcon = () -> IconSetMixin.class.getResourceAsStream("/assets/shizavisuals/textures/gui/icon_mac.png");

            if (macIcon.get() != null) {
                cir.setReturnValue(macIcon);
            }
        } catch (Exception e) {
            System.err.println("[ShizaVisuals] Failed to load custom macOS app icon: " + e.getMessage());
        }
    }
}
