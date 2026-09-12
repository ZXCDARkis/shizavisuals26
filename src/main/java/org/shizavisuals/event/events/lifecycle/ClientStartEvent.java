package org.shizavisuals.event.events.lifecycle;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.shizavisuals.event.Event;

@Getter
public final class ClientStartEvent extends Event {
    private Minecraft client;

    public ClientStartEvent set(Minecraft client) {
        this.client = client;
        return this;
    }
}
