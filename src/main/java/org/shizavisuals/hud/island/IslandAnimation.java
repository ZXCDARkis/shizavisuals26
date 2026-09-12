package org.shizavisuals.hud.island;

public class IslandAnimation {
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
    }

    public IslandAnimation(long duration, IslandEasing easing) {
        this(duration, 0.0F, easing);
    }

    public void update(boolean bool) {
        this.update(bool ? 1.0F : 0.0F);
    }

    public float update(float newValue) {
        if (newValue != this.targetValue) {
            this.startValue = this.value;
            this.targetValue = newValue;
            this.startTime = System.currentTimeMillis();
        }
        long elapsed = System.currentTimeMillis() - this.startTime;
        if (elapsed >= this.duration) {
            this.value = this.targetValue;
            return this.value;
        }
        float progress = (float) elapsed / (float) this.duration;
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
