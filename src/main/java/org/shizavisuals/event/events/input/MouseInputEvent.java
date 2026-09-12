package org.shizavisuals.event.events.input;

import lombok.Getter;
import org.shizavisuals.event.CancellableEvent;

@Getter
public final class MouseInputEvent extends CancellableEvent {
    private final long window;
    private final int button;
    private final int action;
    private final int modifiers;

    public MouseInputEvent(long window, int button, int action, int modifiers) {
        this.window = window;
        this.button = button;
        this.action = action;
        this.modifiers = modifiers;
    }
}
