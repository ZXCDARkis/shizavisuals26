package org.shizavisuals.mixin.render;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.shizavisuals.feature.impl.visual.TntEffectFeature;
import org.shizavisuals.utils.render.tnt.TntEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explosions are driven from the server packet only. The reference mod also
 * hooked {@code ClientWorld.createExplosion}, which double-fired every blast
 * because the client replays the same explosion it was just told about.
 */
@Mixin(ClientPacketListener.class)
public abstract class TntExplosionMixin {

    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void shizavisuals$onExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        TntEffectFeature feature = TntEffectFeature.getEnabled();
        TntEffectRenderer renderer = TntEffectRenderer.active();
        if (feature == null || renderer == null) {
            return;
        }

        // Wind charges reuse the explosion packet with a much smaller radius and
        // their own sound id, which is what the "TNT only" filter keys on.
        String sound = packet.explosionSound().value().location().getPath();
        boolean windCharge = sound.contains("wind");
        if (!feature.acceptsExplosion(windCharge)) {
            return;
        }

        var center = packet.center();
        renderer.onExplosion(center.x, center.y, center.z,
                packet.radius() * feature.radiusScale.getValue().floatValue());
    }
}