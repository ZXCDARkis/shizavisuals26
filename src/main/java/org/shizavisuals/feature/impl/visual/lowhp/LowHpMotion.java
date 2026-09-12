package org.shizavisuals.feature.impl.visual.lowhp;

/** Frame-rate-independent entrance, exit and heartbeat motion for LowHP. Ported from the vesper reference. */
public final class LowHpMotion {
    private static final float ENTER_RESPONSE = 8.5F;
    private static final float EXIT_RESPONSE = 7.5F;
    private static final float MAX_DELTA_SECONDS = 0.1F;
    private static final double HEARTBEAT_PERIOD_SECONDS = 1.2D;
    private static final double PRIMARY_BEAT_PHASE = 0.18D;
    private static final float HEARTBEAT_AMPLITUDE = 0.09F;

    private float visibility;
    private double heartbeatSeconds;
    private boolean previousTarget;
    private boolean beatPending;

    public Frame update(boolean visible, float deltaSeconds) {
        float delta = sanitizeDelta(deltaSeconds);
        if (visible && !this.previousTarget) {
            this.heartbeatSeconds = 0.0D;
        }
        this.previousTarget = visible;

        if (delta > 0.0F) {
            double previousHeartbeat = this.heartbeatSeconds;
            float target = visible ? 1.0F : 0.0F;
            float response = visible ? ENTER_RESPONSE : EXIT_RESPONSE;
            this.visibility = exponentialApproach(this.visibility, target, response, delta);
            if (visible || this.visibility > 0.001F) {
                this.heartbeatSeconds += delta;
                if (visible && crossedBeat(previousHeartbeat, this.heartbeatSeconds)) {
                    this.beatPending = true;
                }
            }
        }

        float easedVisibility = smoothStep(this.visibility);
        float contentScale = 0.96F + 0.04F * easedVisibility;
        float verticalOffset = 1.75F * (1.0F - easedVisibility);
        float heartbeat = heartbeat(this.heartbeatSeconds);
        float heartScale = 1.0F + HEARTBEAT_AMPLITUDE * heartbeat * easedVisibility;
        return new Frame(easedVisibility, contentScale, verticalOffset, heartScale);
    }

    /** Returns true once per primary heartbeat thump; used to trigger the sound. */
    public boolean consumeBeat() {
        boolean beat = this.beatPending;
        this.beatPending = false;
        return beat;
    }

    public void reset() {
        this.visibility = 0.0F;
        this.heartbeatSeconds = 0.0D;
        this.previousTarget = false;
        this.beatPending = false;
    }

    private static boolean crossedBeat(double previous, double current) {
        return Math.floor((previous - PRIMARY_BEAT_PHASE) / HEARTBEAT_PERIOD_SECONDS)
                != Math.floor((current - PRIMARY_BEAT_PHASE) / HEARTBEAT_PERIOD_SECONDS);
    }

    private static float exponentialApproach(float current, float target, float response, float deltaSeconds) {
        double retained = Math.exp(-Math.max(0.0F, response) * deltaSeconds);
        return (float) (target + (current - target) * retained);
    }

    /** Two restrained, rounded beats per cycle instead of a mechanical sine loop. */
    private static float heartbeat(double seconds) {
        double phase = positiveModulo(seconds, HEARTBEAT_PERIOD_SECONDS);
        double primary = gaussian(periodicDistance(phase, 0.18D, HEARTBEAT_PERIOD_SECONDS), 0.095D);
        double secondary = 0.58D
                * gaussian(periodicDistance(phase, 0.43D, HEARTBEAT_PERIOD_SECONDS), 0.115D);
        return (float) Math.min(1.0D, primary + secondary);
    }

    private static double gaussian(double distance, double width) {
        double normalized = distance / width;
        return Math.exp(-(normalized * normalized));
    }

    private static double periodicDistance(double value, double center, double period) {
        double direct = Math.abs(value - center);
        return Math.min(direct, period - direct);
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }

    private static float smoothStep(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float sanitizeDelta(float value) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            return 0.0F;
        }
        return Math.min(value, MAX_DELTA_SECONDS);
    }

    public record Frame(float alpha, float contentScale, float verticalOffset, float heartScale) {
    }
}
