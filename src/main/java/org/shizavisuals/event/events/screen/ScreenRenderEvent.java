package org.shizavisuals.event.events.screen;

import lombok.Getter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.shizavisuals.event.CancellableEvent;

@Getter
public final class ScreenRenderEvent extends CancellableEvent {
    private Screen screen;
    private GuiGraphicsExtractor guiGraphicsExtractor;
    private int mouseX;
    private int mouseY;
    private float partialTick;

    public ScreenRenderEvent set(Screen screen, GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        this.screen = screen;
        this.guiGraphicsExtractor = guiGraphicsExtractor;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTick = partialTick;
        return this;
    }
}
