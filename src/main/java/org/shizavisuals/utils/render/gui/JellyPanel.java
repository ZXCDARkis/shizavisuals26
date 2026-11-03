package org.shizavisuals.utils.render.gui;

import org.shizavisuals.utils.math.FrameClock;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.math.Spring;

/**
 * Jelly response for a panel: how it deforms when it is thrown, when its size
 * changes, and when two panels fuse into one.
 *
 * <p>The deformation is volume-preserving — a panel stretched along X squeezes
 * along Y and gains depth. Squash is driven by the panel's own velocity, so a
 * fast fling visibly smears and a slow move does not, which is what makes it
 * read as a physical material instead of a scale animation.
 *
 * <p>Instances are cheap but not free; keep one per panel, not one per draw call.
 */
public final class JellyPanel {
    /**
     * Fraction of the panel's own velocity that becomes deformation.
     *
     * <p>0.18, not 0.55: the panels travel far less now (see {@code Inertia}), and
     * a gain tuned for that distance smeared them into obvious blobs on every
     * reposition. This keeps the wobble legible on a normal drag without the
     * panel looking broken.
     */
    private static final float SQUASH_GAIN = 0.18F;
    /** Velocity magnitude that produces the maximum deformation. */
    private static final float SQUASH_REFERENCE = 1400.0F;
    private static final float MAX_SQUASH = 0.10F;
    /** Depth (fake Z) range, in pixels of blur/sheen boost at full stretch. */
    private static final float MAX_DEPTH = 1.0F;

    private final Spring scaleX = new Spring(1.0F, 260.0F, 24.0F);
    private final Spring scaleY = new Spring(1.0F, 260.0F, 24.0F);
    private final Spring depth = new Spring(0.0F, 200.0F, 22.0F);
    /** Smooth-union radius handed to the glass shader when this panel merges. */
    private final Spring merge = new Spring(0.0F, 150.0F, 26.0F);

    private float lastCenterX;
    private float lastCenterY;
    private boolean hasLastCenter;
    private float velocityX;
    private float velocityY;

    /**
     * @param centerX       panel centre in GUI pixels
     * @param centerY       panel centre in GUI pixels
     * @param mergeTarget   positive smooth-union radius while merging with a
     *                      neighbour, 0 when standalone
     */
    public void update(float centerX, float centerY, float mergeTarget) {
        float dt = FrameClock.delta();
        if (dt <= 0.0F) {
            return;
        }
        if (this.hasLastCenter) {
            // Exponential average of the frame-to-frame velocity: a single frame
            // can report an enormous delta (a snap, a teleport, a resize), and
            // feeding that straight into the squash would tear the panel.
            float instantX = (centerX - this.lastCenterX) / dt;
            float instantY = (centerY - this.lastCenterY) / dt;
            this.velocityX = this.velocityX * 0.6F + instantX * 0.4F;
            this.velocityY = this.velocityY * 0.6F + instantY * 0.4F;
        }
        this.lastCenterX = centerX;
        this.lastCenterY = centerY;
        this.hasLastCenter = true;

        float speed = Math.max(Math.abs(this.velocityX), Math.abs(this.velocityY));
        float amount = MathUtil.clamp(speed / SQUASH_REFERENCE, 0.0F, 1.0F) * SQUASH_GAIN;
        amount = Math.min(amount, MAX_SQUASH);

        // Stretch along the direction of travel, squeeze across it, and keep the
        // area roughly constant so the panel never visibly loses or gains size.
        boolean horizontal = Math.abs(this.velocityX) >= Math.abs(this.velocityY);
        float sign = Math.signum(horizontal ? this.velocityX : this.velocityY);
        float stretch = 1.0F + amount * sign;
        float squeeze = 1.0F - amount * 0.92F * sign;

        this.scaleX.target(horizontal ? stretch : squeeze);
        this.scaleY.target(horizontal ? squeeze : stretch);
        this.depth.target(amount * MAX_DEPTH * 2.0F);
        this.merge.target(Math.max(0.0F, mergeTarget));

        this.scaleX.update(dt);
        this.scaleY.update(dt);
        this.depth.update(dt);
        this.merge.update(dt);
    }

    /** Forgets the motion history — call when a panel is teleported or respawned. */
    public void reset() {
        this.velocityX = 0.0F;
        this.velocityY = 0.0F;
        this.hasLastCenter = false;
        this.scaleX.snap(1.0F);
        this.scaleY.snap(1.0F);
        this.depth.snap(0.0F);
    }

    public float scaleX() {
        return this.scaleX.value();
    }

    public float scaleY() {
        return this.scaleY.value();
    }

    /** 0..1: how much of the max specular/refraction boost to apply. */
    public float depth() {
        return MathUtil.clamp01(this.depth.value());
    }

    public boolean settled() {
        return this.scaleX.settled() && this.scaleY.settled()
                && this.depth.settled() && this.merge.settled();
    }
}