package org.shizavisuals.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.shizavisuals.event.events.packet.PacketSendEvent;
import org.shizavisuals.event.PacketEventManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
    private void wrapSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        if (!PacketEventManager.hasSendListeners()) {
            original.call(packet, listener, flush);
            return;
        }

        PacketSendEvent event = PacketEventManager.callSendPre((Connection) (Object) this, packet);
        if (event.isCancelled()) {
            return;
        }

        Packet<?> dispatchedPacket = event.getPacket();
        original.call(dispatchedPacket, listener, flush);
        PacketEventManager.callSendPost((Connection) (Object) this, dispatchedPacket);
    }

    @WrapMethod(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V")
    private void wrapReceive(ChannelHandlerContext context, Packet<?> packet, Operation<Void> original) {
        if (!PacketEventManager.hasReceiveListeners()) {
            original.call(context, packet);
            return;
        }
        if (PacketEventManager.callReceivePre((Connection) (Object) this, packet)) {
            return;
        }
        original.call(context, packet);
        PacketEventManager.callReceivePost((Connection) (Object) this, packet);
    }
}
