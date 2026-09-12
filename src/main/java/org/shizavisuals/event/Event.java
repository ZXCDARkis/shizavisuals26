package org.shizavisuals.event;

public abstract class Event {
    private boolean completed;

    public final boolean isCompleted() {
        return completed;
    }
    public boolean isCancelled() {
        return false;
    }

    protected void reset() {
        this.completed = false;
    }

    final void complete() {
        this.completed = true;
    }
}
