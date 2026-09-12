package org.shizavisuals.event.events.screen;

import lombok.Getter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.shizavisuals.event.CancellableEvent;

@Getter
public final class ScreenMouseButtonEvent extends CancellableEvent {
    public enum Action {
        CLICK,
        RELEASE,
        DRAG
    }

    private Screen screen;
    private MouseButtonEvent mouseButtonEvent;
    private Action action;
    private double dragX;
    private double dragY;

    public ScreenMouseButtonEvent set(Screen screen, MouseButtonEvent mouseButtonEvent, Action action, double dragX, double dragY) {
        this.screen = screen;
        this.mouseButtonEvent = mouseButtonEvent;
        this.action = action;
        this.dragX = dragX;
        this.dragY = dragY;
        return this;
    }
}
