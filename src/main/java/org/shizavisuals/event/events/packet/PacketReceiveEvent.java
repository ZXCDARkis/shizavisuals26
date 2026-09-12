package org.shizavisuals.event.events.packet;

import lombok.Getter;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.shizavisuals.event.CancellableEvent;

@Getter
public final class PacketReceiveEvent extends CancellableEvent {
    public enum Phase {
        PRE,
        POST
    }

    private final Connection connection;
    private final Packet<?> packet;
    private final Phase phase;

    public PacketReceiveEvent(Connection connection, Packet<?> packet, Phase phase) {
        this.connection = connection;
        this.packet = packet;
        this.phase = phase;
    }
}
