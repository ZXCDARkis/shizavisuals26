package org.shizavisuals.mixin.input;

import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.shizavisuals.context.RotationContext;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.events.input.MouseInputEvent;
import org.shizavisuals.feature.impl.misc.WebPanelFeature;
import org.shizavisuals.menu.core.MenuOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (!EventManager.hasListeners(MouseInputEvent.class)) {
            return;
        }
        MouseInputEvent event = EventManager.call(new MouseInputEvent(
                window,
                buttonInfo.button(),
                action,
                buttonInfo.modifiers()
        ));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void blockWorldScrollWhileMenuOpen(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (MenuOverlay.blocksInput()) {
            MenuOverlay.handleScroll(vertical);
            ci.cancel();
            return;
        }
        if (WebPanelFeature.consumeScroll(vertical)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void onTurnPlayer(LocalPlayer player, double yawDelta, double pitchDelta) {
        if (RotationContext.onMouseTurn(yawDelta, pitchDelta)) {
            return;
        }

        player.turn(yawDelta, pitchDelta);
    }
}
