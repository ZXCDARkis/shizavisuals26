package org.shizavisuals.feature.impl.visual.lowhp;

/**
 * Frame-rate-independent top-to-bottom transition between exact health values.
 * The old and new tenths stay truthful while their glyphs cross-fade and slide.
 * Ported from the vesper reference.
 */
public final class LowHpValueMotion {
    private static final float RESPONSE = 11.0F;
    private static final float SLIDE_DISTANCE = 2.15F;
    private static final float MAX_DELTA_SECONDS = 0.1F;

    private int previousTenths;
    private int currentTenths;
    private float progress = 1.0F;
    private boolean initialized;

    public Frame update(float health, float deltaSeconds) {
        int targetTenths = toTenths(health);
        if (!this.initialized) {
            this.previousTenths = targetTenths;
            this.currentTenths = targetTenths;
            this.progress = 1.0F;
            this.initialized = true;
        } else if (targetTenths != this.currentTenths) {
            // During rapid damage/healing, continue from the visually dominant value.
            this.previousTenths = this.progress < 0.5F ? this.previousTenths : this.currentTenths;
            this.currentTenths = targetTenths;
            this.progress = 0.0F;
        }

        float delta = sanitizeDelta(deltaSeconds);
        if (this.progress < 1.0F && delta > 0.0F) {
            this.progress = exponentialApproach(this.progress, 1.0F, RESPONSE, delta);
            if (this.progress >= 0.999F) {
                this.progress = 1.0F;
                this.previousTenths = this.currentTenths;
            }
        }

        float eased = smoothStep(this.progress);
        if (this.progress >= 1.0F) {
            return new Frame(
                    formatTenths(this.currentTenths),
                    formatTenths(this.currentTenths),
                    0.0F,
                    1.0F,
                    0.0F,
                    0.0F
            );
        }
        return new Frame(
                formatTenths(this.previousTenths),
                formatTenths(this.currentTenths),
                1.0F - eased,
                eased,
                SLIDE_DISTANCE * eased,
                -SLIDE_DISTANCE * (1.0F - eased)
        );
    }

    public void reset() {
        this.previousTenths = 0;
        this.currentTenths = 0;
        this.progress = 1.0F;
        this.initialized = false;
    }

    private static int toTenths(float health) {
        if (!Float.isFinite(health) || health <= 0.0F) {
            return 0;
        }
        return Math.max(0, Math.round(Math.min(health, 1_000_000.0F) * 10.0F));
    }

    private static String formatTenths(int tenths) {
        int safeTenths = Math.max(0, tenths);
        int whole = safeTenths / 10;
        int fraction = safeTenths % 10;
        return fraction == 0 ? Integer.toString(whole) : whole + "." + fraction;
    }

    private static float exponentialApproach(float current, float target, float response, float deltaSeconds) {
        double retained = Math.exp(-Math.max(0.0F, response) * deltaSeconds);
        return (float) (target + (current - target) * retained);
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

    public record Frame(
            String previousText,
            String currentText,
            float previousAlpha,
            float currentAlpha,
            float previousOffsetY,
            float currentOffsetY
    ) {
    }
}
