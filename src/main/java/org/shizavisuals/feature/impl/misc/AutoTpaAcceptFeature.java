package org.shizavisuals.feature.impl.misc;

import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.utils.FriendManager;

import java.util.List;
import java.util.Locale;

public final class AutoTpaAcceptFeature extends Feature implements MinecraftContext {
    private static final List<String> REQUEST_MARKERS = List.of(
            "has requested teleport",
            "телепортироваться"
    );
    private static final long ACCEPT_COOLDOWN_MILLIS = 1000L;

    public final BooleanSetting friendsOnly = register(new BooleanSetting(
            "Friends Only", false
    ));
    public final BooleanSetting avoidCombat = register(new BooleanSetting(
            "Avoid Combat", true
    ));

    private long lastAcceptAt;

    public AutoTpaAcceptFeature() {
        super("AutoTpaAccept", "Automatically accepts teleport requests", FeatureCategory.MISC, BindSetting.UNBOUND);
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPhase() != PacketReceiveEvent.Phase.PRE || player() == null) {
            return;
        }
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet)) {
            return;
        }

        String text = packet.content().getString();
        String normalized = text.toLowerCase(Locale.ROOT);
        if (REQUEST_MARKERS.stream().noneMatch(normalized::contains)) {
            return;
        }
        if (this.avoidCombat.getValue() && isInCombat()) {
            return;
        }
        if (this.friendsOnly.getValue()
                && FriendManager.INSTANCE.getFriends().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .noneMatch(normalized::contains)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAcceptAt < ACCEPT_COOLDOWN_MILLIS) {
            return;
        }
        lastAcceptAt = now;
        player().connection.sendCommand("tpaccept");
    }

    private boolean isInCombat() {
        return player().hurtTime > 0;
    }
}
