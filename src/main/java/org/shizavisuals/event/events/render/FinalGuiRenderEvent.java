package org.shizavisuals.event.events.render;

import lombok.Getter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.event.Event;

@Getter
public final class FinalGuiRenderEvent extends Event {
    private Minecraft client;
    private Gui gui;
    private GuiGraphicsExtractor guiGraphicsExtractor;
    private DeltaTracker deltaTracker;
    private boolean renderHud;
    private boolean renderScreen;
    private int mouseX;
    private int mouseY;

    public FinalGuiRenderEvent set(Minecraft client, Gui gui, GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker, boolean renderHud, boolean renderScreen, int mouseX, int mouseY) {
        this.client = client;
        this.gui = gui;
        this.guiGraphicsExtractor = guiGraphicsExtractor;
        this.deltaTracker = deltaTracker;
        this.renderHud = renderHud;
        this.renderScreen = renderScreen;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }
}
