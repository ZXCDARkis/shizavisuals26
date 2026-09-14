package org.shizavisuals.utils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Crosshair target picking that also sees invisible entities.
 * Results are cached per client tick.
 */
public final class TargetSelector {
    private static final double REACH = 48.0D;
    private static final double EXPAND = 0.3D;

    private static ClientLevel cachedLevel;
    private static long cachedTick = -1L;
    private static LivingEntity cachedTarget;

    private TargetSelector() {
    }

    /** Максимальная дистанция цели для TargetHUD/TargetESP. */
    public static final double DISPLAY_RANGE = 3.0D;

    /**
     * Фильтр отображения для TargetHUD/TargetESP: только ближние
     * (в радиусе {@link #DISPLAY_RANGE} от игрока) и видимые цели.
     * Невидимки (зелье/эффект) и дальние сущности отбрасываются.
     */
    public static boolean isDisplayTarget(Minecraft mc, LivingEntity living) {
        if (mc == null || mc.player == null || living == null) {
            return false;
        }
        if (living.isRemoved() || !living.isAlive() || living.isInvisible()) {
            return false;
        }
        return mc.player.distanceTo(living) <= DISPLAY_RANGE;
    }

    public static LivingEntity pick(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null || mc.gameRenderer == null) {
            return null;
        }
        long tick = mc.level.getGameTime();
        if (mc.level == cachedLevel && tick == cachedTick) {
            LivingEntity target = cachedTarget;
            if (target == null || target.isRemoved() || !target.isAlive() || target.level() != mc.level) {
                return null;
            }
            return target;
        }
        cachedLevel = mc.level;
        cachedTick = tick;
        cachedTarget = raycast(mc);
        return cachedTarget;
    }

    private static LivingEntity raycast(Minecraft mc) {
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 start = camera.position();
        float pitch = camera.xRot() * ((float) Math.PI / 180.0F);
        float yaw = camera.yRot() * ((float) Math.PI / 180.0F);
        Vec3 look = new Vec3(
                -Mth.sin(yaw) * Mth.cos(pitch),
                -Mth.sin(pitch),
                Mth.cos(yaw) * Mth.cos(pitch));
        Vec3 end = start.add(look.scale(REACH));

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living == mc.player || living.isRemoved() || !living.isAlive()) {
                continue;
            }
            AABB box = living.getBoundingBox().inflate(EXPAND);
            Optional<Vec3> hit = box.clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double dist = start.distanceTo(hit.get());
            if (dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best;
    }
}
