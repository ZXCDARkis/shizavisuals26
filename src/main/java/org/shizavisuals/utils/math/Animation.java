package org.shizavisuals.utils.math;

import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class Animation {
    private long startNanos;
    private long durationMillis;
    private float fromValue;
    private float toValue;
    private boolean reverse;
    private Easing easing;
    private long cachedFrameNanos = Long.MIN_VALUE;
    private long cachedElapsedMillis;

    public Animation(long duration, Easing easing) {
        setDuration(duration);
        setEasing(easing);
        reset();
    }

    public void reset() {
        this.startNanos = System.nanoTime();
        this.cachedFrameNanos = Long.MIN_VALUE;
    }

    public void animate(float from, float to, long duration, Easing easing) {
        this.fromValue = from;
        this.toValue = to;
        setDuration(duration);
        setEasing(easing);
        this.reverse = false;
        reset();
    }

    public float getValue() {
        float progress = progress();
        if (this.reverse) {
            progress = 1.0F - progress;
        }
        return Mth.lerp(this.easing.ease(progress), this.fromValue, this.toValue);
    }

    public boolean isFinished() {
        return elapsedMillis() >= this.durationMillis;
    }

    public void setReverse(boolean reverse) {
        if (this.reverse == reverse) {
            return;
        }
        long elapsed = Math.min(elapsedMillis(), this.durationMillis);
        this.reverse = reverse;
        this.startNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(this.durationMillis - elapsed);
    }

    public void setDuration(long duration) {
        if (duration < 0L) {
            throw new IllegalArgumentException("Animation duration cannot be negative");
        }
        this.durationMillis = duration;
    }

    public void setEasing(Easing easing) {
        this.easing = Objects.requireNonNull(easing, "easing");
    }

    private float progress() {
        if (this.durationMillis == 0L) {
            return 1.0F;
        }
        return Mth.clamp(elapsedMillis() / (float) this.durationMillis, 0.0F, 1.0F);
    }

    private long elapsedMillis() {
        long now = System.nanoTime();
        // getValue()/isFinished() are polled several times per frame per component;
        // one clock read per frame instead of one per call. Also quantised to 1/240 s
        // so sub-millisecond jitter cannot make the easing visibly stutter.
        if (this.cachedFrameNanos != now) {
            this.cachedFrameNanos = now;
            this.cachedElapsedMillis = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, now - this.startNanos));
        }
        return this.cachedElapsedMillis;
    }

    public enum Easing {
        LINEAR {
            @Override
            public float ease(float x) {
                return x;
            }
        },
        EASE_IN_QUAD {
            @Override
            public float ease(float x) {
                return x * x;
            }
        },
        EASE_OUT_QUAD {
            @Override
            public float ease(float x) {
                return 1.0F - (1.0F - x) * (1.0F - x);
            }
        },
        EASE_OUT_CUBIC {
            @Override
            public float ease(float x) {
                float inverse = 1.0F - x;
                return 1.0F - inverse * inverse * inverse;
            }
        },
        EASE_OUT_EXPO {
            @Override
            public float ease(float x) {
                return x >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0F * x);
            }
        },
        EASE_IN_OUT_QUAD {
            @Override
            public float ease(float x) {
                return x < 0.5F ? 2.0F * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 2.0F) / 2.0F;
            }
        },
        EASE_OUT_BACK {
            @Override
            public float ease(float x) {
                float overshoot = 1.70158F;
                float shifted = x - 1.0F;
                return 1.0F + (overshoot + 1.0F) * shifted * shifted * shifted + overshoot * shifted * shifted;
            }
        },
        EASE_OUT_BOUNCE {
            @Override
            public float ease(float x) {
                float factor = 7.5625F;
                float divisor = 2.75F;
                if (x < 1.0F / divisor) return factor * x * x;
                if (x < 2.0F / divisor) return factor * (x -= 1.5F / divisor) * x + 0.75F;
                if (x < 2.5F / divisor) return factor * (x -= 2.25F / divisor) * x + 0.9375F;
                return factor * (x -= 2.625F / divisor) * x + 0.984375F;
            }
        };

        public abstract float ease(float x);
    }
}
