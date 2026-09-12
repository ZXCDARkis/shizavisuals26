package org.shizavisuals.event.events.game;

import lombok.Getter;
import org.shizavisuals.event.Event;
import net.minecraft.client.Minecraft;

@Getter
public final class GameTickEvent extends Event {
    private Minecraft client;
    private TickContext context;

    public GameTickEvent set(Minecraft client, TickContext context) {
        this.client = client;
        this.context = context;
        return this;
    }
}
