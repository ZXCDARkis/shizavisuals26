package org.shizavisuals.feature.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.WorldJoinEvent;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.feature.Feature;
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
    private volatile long worldGeneration;

    public AutoTpaAcceptFeature() {
        super("AutoTpaAccept", "Автоматически принимает запросы телепортации", FeatureCategory.MISC, BindSetting.UNBOUND);
    }

    @EventTarget
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPhase() != PacketReceiveEvent.Phase.PRE
                || !this.isEnabled()
                || !(event.getPacket() instanceof ClientboundSystemChatPacket packet)
                || !isPotentialRequest(packet.content().getString())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Connection connection = event.getConnection();
        long generation = this.worldGeneration;
        if (mc.isSameThread()) {
            acceptRequest(mc, packet, connection, generation);
        } else {
            mc.execute(() -> acceptRequest(mc, packet, connection, generation));
    }
}

    private static boolean isPotentialRequest(String text) {
        return text.contains("has requested teleport")
                || text.contains("телепортироваться")
                || text.contains("Телепортироваться");
    }

    private void acceptRequest(Minecraft mc, ClientboundSystemChatPacket packet,
                               Connection connection, long generation) {
        if (!this.isEnabled() || generation != this.worldGeneration
                || !isCurrentConnection(connection) || mc.player == null || mc.player.connection == null) {
            return;
        }
        String normalized = packet.content().getString().toLowerCase(Locale.ROOT);
        boolean request = false;
        for (String marker : REQUEST_MARKERS) {
            if (normalized.contains(marker)) {
                request = true;
                break;
            }
        }
        if (!request) {
            return;
        }
        if (this.avoidCombat.getValue() && mc.player.hurtTime > 0) {
            return;
        }
        if (this.friendsOnly.getValue() && !containsFriend(normalized)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastAcceptAt < ACCEPT_COOLDOWN_MILLIS) {
            return;
        }
        this.lastAcceptAt = now;
        mc.player.connection.sendCommand("tpaccept");
    }

    private boolean containsFriend(String normalized) {
        for (String name : FriendManager.INSTANCE.getFriends()) {
            if (name != null && normalized.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @EventTarget
    public void onWorldJoin(WorldJoinEvent event) {
        this.worldGeneration++;
        this.lastAcceptAt = 0L;
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        this.worldGeneration++;
        this.lastAcceptAt = 0L;
    }

    @Override
    protected void onEnable() {
        this.worldGeneration++;
        this.lastAcceptAt = 0L;
    }

    @Override
    protected void onDisable() {
        this.worldGeneration++;
        this.lastAcceptAt = 0L;
    }
}
