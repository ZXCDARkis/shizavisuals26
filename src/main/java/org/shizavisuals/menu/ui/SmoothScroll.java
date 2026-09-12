package org.shizavisuals.menu.ui;

import org.shizavisuals.utils.math.MathUtil;

public final class SmoothScroll {
    private float value;
    private float target;
    private long lastNanos = System.nanoTime();

    public void scroll(double wheelDelta, float maximum) {
        this.target = MathUtil.clamp(this.target - (float) wheelDelta * 54.0F, 0.0F, Math.max(0.0F, maximum));
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public float update(float maximum) {
        float max = Math.max(0.0F, maximum);
        this.target = MathUtil.clamp(this.target, 0.0F, max);
        long now = System.nanoTime();
        float seconds = Math.min(0.05F, (now - this.lastNanos) / 1_000_000_000.0F);
        this.lastNanos = now;
        float blend = 1.0F - (float) Math.exp(-seconds * 15.0F);
        this.value += (this.target - this.value) * blend;
        if (Math.abs(this.target - this.value) < 0.05F) this.value = this.target;
        return this.value;
    }

    public void reset() {
        this.value = 0.0F;
        this.target = 0.0F;
    }
}
