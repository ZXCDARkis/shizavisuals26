package org.shizavisuals.event.events.input;

import lombok.Getter;
import org.shizavisuals.event.CancellableEvent;

@Getter
public final class KeyboardInputEvent extends CancellableEvent {
    private final long window;
    private final int key;
    private final int scanCode;
    private final int action;
    private final int modifiers;

    public KeyboardInputEvent(long window, int key, int scanCode, int action, int modifiers) {
        this.window = window;
        this.key = key;
        this.scanCode = scanCode;
        this.action = action;
        this.modifiers = modifiers;
    }
}
