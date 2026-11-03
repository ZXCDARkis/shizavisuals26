package org.shizavisuals.hud.island;

import java.util.concurrent.TimeUnit;

public class IslandAnimation {
    /**
     * Targets closer than this count as "the same". Callers pass computed floats,
     * so an exact compare restarted the easing on every jittery frame, which made
     * the island values twitch instead of settling.
     */
    private static final float EPSILON = 1.0E-4F;

    private long duration;
    private float value;
    private IslandEasing easing;
    private long startTime;
    private float startValue;
    private float targetValue;

    public IslandAnimation(long duration, float initialValue, IslandEasing easing) {
        this.duration = duration;
        this.easing = easing;
        this.value = initialValue;
        this.startValue = initialValue;
        this.targetValue = initialValue;
        // -1 so the first update() sees elapsed >= duration and snaps to target.
        this.startTime = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(duration);
    }

    public IslandAnimation(long duration, IslandEasing easing) {
        this(duration, 0.0F, easing);
    }

    public void update(boolean bool) {
        this.update(bool ? 1.0F : 0.0F);
    }

    public float update(float newValue) {
        if (Math.abs(newValue - this.targetValue) > EPSILON) {
            this.startValue = this.value;
            this.targetValue = newValue;
            // nanoTime, not currentTimeMillis: monotonic and finer grained, so a
            // wall-clock adjustment cannot stall or jump an in-flight animation.
            this.startTime = System.nanoTime();
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.startTime);
        if (elapsed >= this.duration) {
            this.value = this.targetValue;
            return this.value;
        }
        float progress = this.duration <= 0L ? 1.0F : (float) elapsed / (float) this.duration;
        float easedProgress = this.easing.ease(progress);
        this.value = this.startValue + (this.targetValue - this.startValue) * easedProgress;
        return this.value;
    }

    public void setValue(float newValue) {
        this.value = newValue;
        this.startValue = newValue;
        this.targetValue = newValue;
    }

    public float getValue() {
        return this.value;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setEasing(IslandEasing easing) {
        if (easing != null && easing != this.easing) {
            this.easing = easing;
        }
    }
}
