package org.shizavisuals.event.events.lifecycle;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.shizavisuals.event.Event;

@Getter
public final class WorldLeaveEvent extends Event {
    private Minecraft client;
    private ClientLevel level;

    public WorldLeaveEvent set(Minecraft client, ClientLevel level) {
        this.client = client;
        this.level = level;
        return this;
    }
}
