package org.shizavisuals.event.events.input;

import lombok.Getter;
import org.shizavisuals.event.CancellableEvent;

@Getter
public final class CharacterInputEvent extends CancellableEvent {
    private final long window;
    private final int codePoint;

    public CharacterInputEvent(long window, int codePoint) {
        this.window = window;
        this.codePoint = codePoint;
    }
}
