package org.shizavisuals.utils.math;

/**
 * Frame clock shared by every spring in the UI.
 *
 * <p>Springs need a real delta. Each surface used to read
 * {@code System.currentTimeMillis()} itself, which meant a) several different
 * deltas for one frame and b) a delta of 0 or a huge one after a stall. This
 * publishes one delta per frame, clamped to a sane range.
 */
public final class FrameClock {
    private static final float MAX_DELTA = 0.05F;

    private static long lastNanos = System.nanoTime();
    private static float delta = 1.0F / 60.0F;

    private FrameClock() {
    }

    /** Call once at the start of each rendered frame, before anything springs. */
    public static void beginFrame() {
        long now = System.nanoTime();
        float seconds = (now - lastNanos) / 1_000_000_000.0F;
        lastNanos = now;
        // A first frame, a window drag or a world load can hand us a huge or
        // negative delta; clamping keeps every spring on screen.
        delta = Math.clamp(seconds, 1.0F / 1000.0F, MAX_DELTA);
    }

    /** Seconds elapsed since the previous frame. */
    public static float delta() {
        return delta;
    }
}