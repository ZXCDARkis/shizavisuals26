package org.shizavisuals.context;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.shizavisuals.event.PacketEventManager;

public interface PacketContext extends MinecraftContext {
    default ClientPacketListener packetListener() {
        return client().getConnection();
    }

    default Connection connection() {
        ClientPacketListener packetListener = packetListener();
        return packetListener != null ? packetListener.getConnection() : null;
    }

    default boolean hasConnection() {
        return connection() != null;
    }

    default boolean isConnected() {
        Connection connection = connection();
        return connection != null && connection.isConnected();
    }

    default void sendPacket(Packet<?> packet) {
        Connection connection = connection();
        if (connection != null && packet != null) {
            connection.send(packet);
        }
    }

    default boolean hasPacketSendListeners() {
        return PacketEventManager.hasSendListeners();
    }

    default boolean hasPacketReceiveListeners() {
        return PacketEventManager.hasReceiveListeners();
    }
}
