package org.shizavisuals.event;

public abstract class CancellableEvent extends Event {
    private boolean cancelled;

    public final boolean isCancelled() {
        return cancelled;
    }

    @Override
    protected void reset() {
        super.reset();
        this.cancelled = false;
    }

    public final void cancel() {
        if (isCompleted()) {
            throw new IllegalStateException("Cannot cancel an event that has already been completed.");
        }
        this.cancelled = true;
    }
}
