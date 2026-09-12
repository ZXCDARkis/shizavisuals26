package org.shizavisuals.event.events.render;

import lombok.Getter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.shizavisuals.event.Event;

@Getter
public final class Render3DEvent extends Event {
    private Minecraft client;
    private GameRenderer gameRenderer;
    private DeltaTracker deltaTracker;

    public Render3DEvent set(Minecraft client, GameRenderer gameRenderer, DeltaTracker deltaTracker) {
        this.client = client;
        this.gameRenderer = gameRenderer;
        this.deltaTracker = deltaTracker;
        return this;
    }
}
