package org.shizavisuals.mixin.player;

import net.minecraft.client.player.AbstractClientPlayer;
import org.shizavisuals.feature.impl.visual.HoldMyItemsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.holdmylua.source.scripting.script_wrappers.P", remap = false)
public abstract class HoldMyItemsPlayerWrapperMixin {
    @Inject(method = "isOnGround", at = @At("HEAD"), cancellable = true, remap = false)
    private void alternateAuraAttackStyle(AbstractClientPlayer player,
                                                 CallbackInfoReturnable<Boolean> callback) {
        if (HoldMyItemsCompat.shouldUseGroundAttackStyle(player)) {
            callback.setReturnValue(true);
        }
    }
}
