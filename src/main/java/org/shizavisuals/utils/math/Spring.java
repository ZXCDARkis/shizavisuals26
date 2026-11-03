package org.shizavisuals.utils.math;

/**
 * Frame-rate independent spring.
 *
 * <p>Everything animated in the UI (HUD inertia, menu inertia, the jelly squash)
 * is a spring towards a target. The previous code eased a value towards a target
 * every frame, which restarts whenever the target jitters by a fraction of a
 * pixel and has no notion of velocity — you cannot fling a panel with it, and it
 * cannot overshoot.
 *
 * <p>Integrated with fixed sub-steps rather than one variable-size Euler step:
 * a single explicit Euler step is only stable while {@code dt < 2/damping}, so at
 * 30 fps with a snappy spring it would visibly gain energy and jitter forever.
 * Sub-stepping at a fixed 1/240s keeps it stable and makes the motion identical
 * at any frame rate.
 *
 * <p>Not thread safe: call {@link #update} from the render thread only.
 */
public final class Spring {
    /** Fixed integration step. Small enough to be stable at any stiffness we use. */
    private static final float SUB_STEP = 1.0F / 240.0F;
    /** Longest slice of real time a single update may simulate (a stall, a shader compile). */
    private static final float MAX_FRAME = 0.05F;
    /** Below this the spring snaps and stops costing anything. */
    private static final float SLEEP_DISTANCE = 0.02F;
    private static final float SLEEP_VELOCITY = 0.05F;

    private float value;
    private float target;
    private float velocity;
    private float stiffness = 170.0F;
    private float damping = 26.0F;

    public Spring(float initial) {
        this(initial, 170.0F, 26.0F);
    }

    public Spring(float initial, float stiffness, float damping) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public float value() {
        return this.value;
    }

    public float target() {
        return this.target;
    }

    public float velocity() {
        return this.velocity;
    }

    /** Overwrites the stored velocity without touching position or target. */
    public Spring velocity(float velocity) {
        this.velocity = velocity;
        return this;
    }

    public Spring target(float target) {
        this.target = target;
        return this;
    }

    /** Jumps to a value, cancelling any motion in flight. */
    public Spring snap(float value) {
        this.value = value;
        this.target = value;
        this.velocity = 0.0F;
        return this;
    }

    public Spring stiffness(float stiffness) {
        this.stiffness = Math.max(0.0F, stiffness);
        return this;
    }

    public Spring damping(float damping) {
        this.damping = Math.max(0.0F, damping);
        return this;
    }

    /** Injects velocity in units per second — this is what makes a fling feel like a fling. */
    public Spring impulse(float velocity) {
        this.velocity += velocity;
        return this;
    }

    public boolean settled() {
        return Math.abs(this.target - this.value) < SLEEP_DISTANCE
                && Math.abs(this.velocity) < SLEEP_VELOCITY;
    }

    /**
     * Advances the spring by {@code deltaSeconds} of real time and returns the new
     * value. Safe to call with wildly varying deltas.
     */
    public float update(float deltaSeconds) {
        float remaining = Math.min(Math.max(deltaSeconds, 0.0F), MAX_FRAME);
        while (remaining > 0.0F) {
            float step = Math.min(SUB_STEP, remaining);
            remaining -= step;
            if (settled()) {
                this.value = this.target;
                this.velocity = 0.0F;
                break;
            }
            // Semi-implicit Euler: velocity first, then position from the NEW
            // velocity. Stable for the step sizes used here and unconditionally
            // monotone in energy, unlike explicit Euler.
            float displacement = this.value - this.target;
            float acceleration = -this.stiffness * displacement - this.damping * this.velocity;
            this.velocity += acceleration * step;
            this.value += this.velocity * step;
        }
        if (settled()) {
            this.value = this.target;
            this.velocity = 0.0F;
        }
        return this.value;
    }
}