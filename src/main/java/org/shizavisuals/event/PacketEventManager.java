package org.shizavisuals.event;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.shizavisuals.event.events.packet.PacketReceiveEvent;
import org.shizavisuals.event.events.packet.PacketSendEvent;

public final class PacketEventManager {
    private PacketEventManager() {
    }

    public static boolean hasSendListeners() {
        return EventManager.hasListeners(PacketSendEvent.class);
    }

    public static boolean hasReceiveListeners() {
        return EventManager.hasListeners(PacketReceiveEvent.class);
    }

    public static PacketSendEvent callSendPre(Connection connection, Packet<?> packet) {
        return EventManager.call(new PacketSendEvent(connection, packet, PacketSendEvent.Phase.PRE));
    }

    public static void callSendPost(Connection connection, Packet<?> packet) {
        EventManager.call(new PacketSendEvent(connection, packet, PacketSendEvent.Phase.POST));
    }

    public static boolean callReceivePre(Connection connection, Packet<?> packet) {
        return EventManager.call(new PacketReceiveEvent(connection, packet, PacketReceiveEvent.Phase.PRE)).isCancelled();
    }

    public static void callReceivePost(Connection connection, Packet<?> packet) {
        EventManager.call(new PacketReceiveEvent(connection, packet, PacketReceiveEvent.Phase.POST));
    }
}
