package org.shizavisuals.event.events.render;

import lombok.Getter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.event.Event;

@Getter
public final class Render2DEvent extends Event {
    private Minecraft client;
    private Gui gui;
    private GuiGraphicsExtractor guiGraphicsExtractor;
    private DeltaTracker deltaTracker;

    public Render2DEvent set(Minecraft client, Gui gui, GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker) {
        this.client = client;
        this.gui = gui;
        this.guiGraphicsExtractor = guiGraphicsExtractor;
        this.deltaTracker = deltaTracker;
        return this;
    }
}
