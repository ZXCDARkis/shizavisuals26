package org.shizavisuals.event.events.screen;

import lombok.Getter;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.shizavisuals.event.CancellableEvent;

@Getter
public final class ScreenKeyEvent extends CancellableEvent {
    public enum Action {
        PRESS,
        RELEASE
    }

    private Screen screen;
    private KeyEvent keyEvent;
    private Action action;

    public ScreenKeyEvent set(Screen screen, KeyEvent keyEvent, Action action) {
        this.screen = screen;
        this.keyEvent = keyEvent;
        this.action = action;
        return this;
    }
}
