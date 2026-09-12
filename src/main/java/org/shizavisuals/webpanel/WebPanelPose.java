package org.shizavisuals.webpanel;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class WebPanelPose {

    private WebPanelPose() {
    }

    private static final double FLAT = 0.99;

    public static float snapYaw(Vec3 normal, float playerYaw) {
        if (Math.abs(normal.y) > FLAT) {
            return playerYaw;
        }
        return (float) Math.toDegrees(Math.atan2(normal.x, -normal.z));
    }

    public static float snapPitch(Vec3 normal) {
        return (float) Math.toDegrees(Math.asin(Mth.clamp(normal.y, -1.0F, 1.0F)));
    }

    public static Vec3 snapOrigin(Vec3 hitPos, Vec3 normal, Vec3 up, double height,
                                   double gap) {
        return hitPos.add(normal.scale(gap)).subtract(up.scale(height * 0.5));
    }
}
