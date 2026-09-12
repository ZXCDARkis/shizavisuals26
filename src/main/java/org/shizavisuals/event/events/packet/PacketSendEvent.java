package org.shizavisuals.event.events.packet;

import lombok.Getter;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.shizavisuals.event.CancellableEvent;

@Getter
public final class PacketSendEvent extends CancellableEvent {
    public enum Phase {
        PRE,
        POST
    }

    private final Connection connection;
    private Packet<?> packet;
    private final Phase phase;

    public PacketSendEvent(Connection connection, Packet<?> packet, Phase phase) {
        this.connection = connection;
        this.packet = packet;
        this.phase = phase;
    }

    public void setPacket(Packet<?> packet) {
        if (isCompleted()) {
            throw new IllegalStateException("Cannot replace a packet after event dispatch.");
        }
        this.packet = java.util.Objects.requireNonNull(packet, "packet");
    }
}
