package org.shizavisuals.utils.render;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.shizavisuals.utils.ColorUtil;

public final class HurtUtil {
    private static final int HURT_COLOR = 0xFFFF5255;
    private static final float HURT_TICKS = 10.0F;

    private HurtUtil() {
    }

    public static float factor(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.hurtTime <= 0) {
            return 0.0F;
        }
        return Mth.clamp(living.hurtTime / HURT_TICKS, 0.0F, 1.0F);
    }

    public static float easedFactor(Entity entity) {
        float factor = factor(entity);
        return 1.0F - (1.0F - factor) * (1.0F - factor);
    }

    public static int blend(int baseColor, Entity entity, float alpha) {
        return blend(baseColor, easedFactor(entity), alpha);
    }

    public static int blend(int baseColor, float factor, float alpha) {
        factor = Mth.clamp(factor, 0.0F, 1.0F);
        int normal = ColorUtil.multiplyAlpha(baseColor, alpha);
        if (factor <= 0.0F) {
            return normal;
        }
        int hurt = ColorUtil.multiplyAlpha(HURT_COLOR, alpha);
        return ColorUtil.lerp(normal, hurt, factor);
    }

    public static float scale(Entity entity, float intensity) {
        return 1.0F + easedFactor(entity) * Math.max(0.0F, intensity);
    }
}
