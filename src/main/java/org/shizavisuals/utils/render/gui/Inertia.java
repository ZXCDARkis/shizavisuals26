package org.shizavisuals.utils.render.gui;

import org.shizavisuals.utils.math.FrameClock;
import org.shizavisuals.utils.math.Spring;

/**
 * Flingable position with momentum, spring snapping and caller-resolved bounds.
 *
 * <p>Replaces "follow the mouse 1:1 while held, stop dead on release", which is
 * why nothing in this client could be thrown. On release the remaining velocity
 * carries the panel, friction bleeds it off, and a spring pulls it to the snapped
 * target. Bounds are NOT applied here — the target depends on the panel's own
 * size, which the owner knows and this class does not.
 *
 * <p>Two independent axes rather than a Vec2 so the two can settle differently.
 * Not thread safe — render thread only.
 */
public final class Inertia {
    /**
 * Velocity retained per second. 2.6 let a hard throw coast across most of the
 * screen, which made a HUD panel feel like it was being launched rather than
 * placed. 7.0 keeps the momentum readable — roughly an eighth of a screen on a
 * fast flick — and hands over to the spring quickly.
 */
    private static final float FRICTION = 11.0F;
    /** Below this speed the spring takes over from free flight. */
    private static final float REST_SPEED = 34.0F;
    private static final float MAX_SPEED = 2600.0F;

    /** Firm settle with a barely visible overshoot: a snapped panel should feel placed. */
    private static final float STIFFNESS = 220.0F;
    private static final float DAMPING = 30.0F;

    private final Spring x = new Spring(0.0F, STIFFNESS, DAMPING);
    private final Spring y = new Spring(0.0F, STIFFNESS, DAMPING);

    private float velocityX;
    private float velocityY;
    private float grabOffsetX;
    private float grabOffsetY;
    private float lastMouseX;
    private float lastMouseY;
    private boolean hasLastMouse;
    private boolean held;
    private boolean initialized;
    private float latchedVelocityX;
    private float latchedVelocityY;

    public Inertia(float initialX, float initialY) {
        this.x.snap(initialX);
        this.y.snap(initialY);
    }

    public float x() {
        return this.x.value();
    }

    public float y() {
        return this.y.value();
    }

    public float velocityX() {
        return this.velocityX;
    }

    public float velocityY() {
        return this.velocityY;
    }

    public boolean held() {
        return this.held;
    }

    public boolean initialized() {
        return this.initialized;
    }

    /**
     * True only when the panel has actually come to rest.
     *
     * <p>Velocity has to be part of this. During free flight the springs are
     * dragged along by {@code snap(v).target(v)}, so they report "settled" on
     * every frame of a throw — and a settled() that ignored the velocity made
     * the panel freeze mid-air one frame after release.
     */
    public boolean settled() {
        return !this.held
                && Math.abs(this.velocityX) < REST_SPEED
                && Math.abs(this.velocityY) < REST_SPEED
                && this.x.settled() && this.y.settled();
    }

    /** Places the panel without motion — first layout, reset, or an explicit jump. */
    public void snapTo(float x, float y) {
        this.x.snap(x);
        this.y.snap(y);
        this.velocityX = 0.0F;
        this.velocityY = 0.0F;
        this.hasLastMouse = false;
        this.initialized = true;
    }

    /** Starts a drag from a point on the panel. Cancels any flight in progress. */
    public void grab(float mouseX, float mouseY, float panelX, float panelY) {
        this.held = true;
        this.initialized = true;
        this.grabOffsetX = mouseX - panelX;
        this.grabOffsetY = mouseY - panelY;
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.hasLastMouse = true;
        // A panel still drifting out from under the cursor feels broken, so
        // grabbing stops the flight dead.
        this.velocityX = 0.0F;
        this.velocityY = 0.0F;
    }

    /**
     * Ends the drag and hands the measured speed to {@link #velocityX()} /
     * {@link #velocityY()}.
     *
     * <p>The stored speed is CLEARED here on purpose. It used to survive the
     * release, so a caller that then re-applied it through {@link #fling} added
     * the same velocity twice — the panel left at double speed, overshot the
     * landing spot and got pulled back, which read as a forward jolt at the end of
     * every throw.
     */
    public void release() {
        this.held = false;
        this.hasLastMouse = false;
        this.latchedVelocityX = this.velocityX;
        this.latchedVelocityY = this.velocityY;
        this.velocityX = 0.0F;
        this.velocityY = 0.0F;
        this.x.velocity(0.0F);
        this.y.velocity(0.0F);
    }

    /** Drag speed at the moment of release, in units per second. */
    public float latchedVelocityX() {
        return this.latchedVelocityX;
    }

    public float latchedVelocityY() {
        return this.latchedVelocityY;
    }

    /**
     * Launches the panel. SETS the speed rather than adding to it: the drag speed
     * is usually still present when this is called, and adding it again doubled
     * the exit velocity, which made the panel overshoot and snap back.
     */
    public void fling(float impulseX, float impulseY) {
        this.velocityX = clampSpeed(impulseX);
        this.velocityY = clampSpeed(impulseY);
    }

    /**
     * Advances one frame.
     *
     * @param mouseX  current cursor, ignored unless held
     * @param mouseY
     * @param targetX where the panel settles once the motion dies out
     * @param targetY
     */
    public void update(float mouseX, float mouseY, float targetX, float targetY) {
        float dt = FrameClock.delta();
        if (dt <= 0.0F) {
            return;
        }
        this.initialized = true;

        if (this.held) {
            if (this.hasLastMouse) {
                // Smoothed over frames. A single dropped frame read as a raw
                // one-frame delta would produce an enormous velocity and launch
                // the panel across the screen on release.
                float instantX = clampSpeed((mouseX - this.lastMouseX) / dt);
                float instantY = clampSpeed((mouseY - this.lastMouseY) / dt);
                this.velocityX = this.velocityX * 0.55F + instantX * 0.45F;
                this.velocityY = this.velocityY * 0.55F + instantY * 0.45F;
            }
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            this.hasLastMouse = true;
            this.x.snap(mouseX - this.grabOffsetX);
            this.y.snap(mouseY - this.grabOffsetY);
            return;
        }

        if (Math.abs(this.velocityX) > REST_SPEED || Math.abs(this.velocityY) > REST_SPEED) {
            // Free flight. The springs are dragged along so that when the
            // velocity finally drops the handover is continuous.
            float decay = (float) Math.exp(-FRICTION * dt);
            this.velocityX *= decay;
            this.velocityY *= decay;
            float nextX = this.x.value() + this.velocityX * dt;
            float nextY = this.y.value() + this.velocityY * dt;
            this.x.snap(nextX).target(nextX);
            this.y.snap(nextY).target(nextY);
            return;
        }

        this.velocityX = 0.0F;
        this.velocityY = 0.0F;
        this.x.target(targetX).update(dt);
        this.y.target(targetY).update(dt);
        if (this.x.settled() && this.y.settled()) {
            this.x.snap(targetX);
            this.y.snap(targetY);
        }
    }

    private static float clampSpeed(float speed) {
        return Math.clamp(speed, -MAX_SPEED, MAX_SPEED);
    }

    /**
     * How far the panel still coasting at {@code velocity} will travel before it
     * stops. Free flight decays as exp(-FRICTION * t), and the distance integral
     * of that decay is exactly v / FRICTION — this is the number snap-guide
     * prediction has to use, or the landing spot is overestimated.
     */
    public static float remainingGlide(float velocity) {
        return velocity / FRICTION;
    }
}