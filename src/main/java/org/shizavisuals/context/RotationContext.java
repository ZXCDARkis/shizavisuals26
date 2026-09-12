package org.shizavisuals.context;

import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class RotationContext implements MinecraftContext {
    private static final float MOUSE_TURN_SCALE = 0.15F;

    @Getter
    private static float freeYaw;
    @Getter
    private static float freePitch;
    @Getter
    private static boolean active;
    private static boolean hasLastAngle;

    @Getter
    private static float serverYaw;
    @Getter
    private static float serverPitch;

    private static float remainderYaw;
    private static float remainderPitch;

    private static float lastYaw;
    private static float lastPitch;
    private static float lastHeadYaw;
    private static float lastBodyYaw;

    private RotationContext() {
    }

    public static void apply(float yawDelta, float pitchDelta) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (!active) {
            freeYaw = player.getYRot();
            freePitch = player.getXRot();
            double g = gcdStep();
            serverYaw = (float) (Math.round(player.getYRot() / g) * g);
            serverPitch = (float) (Math.round(Mth.clamp(player.getXRot(), -90.0F, 90.0F) / g) * g);
            active = true;
        }

        rememberRenderAngles(player);

        serverYaw = serverYaw + yawDelta;
        serverPitch = Mth.clamp(serverPitch + pitchDelta, -90.0F, 90.0F);

        player.setYRot(serverYaw);
        player.setXRot(serverPitch);
        player.yHeadRot = serverYaw;
        player.yBodyRot = serverYaw;
    }

    public static void setRotation(float yaw, float pitch) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (!active) {
            freeYaw = player.getYRot();
            freePitch = player.getXRot();
            double g = gcdStep();
            serverYaw = (float) (Math.round(player.getYRot() / g) * g);
            serverPitch = (float) (Math.round(Mth.clamp(player.getXRot(), -90.0F, 90.0F) / g) * g);
            remainderYaw = 0.0F;
            remainderPitch = 0.0F;
            active = true;
        }

        float targetPitch = Mth.clamp(pitch, -90.0F, 90.0F);

        remainderYaw += Mth.wrapDegrees(yaw - serverYaw);
        remainderPitch += targetPitch - serverPitch;

        double g = gcdStep();
        if (g >= 1.0E-4D) {
            float snappedYaw = (float) (Math.round(remainderYaw / g) * g);
            float snappedPitch = (float) (Math.round(remainderPitch / g) * g);
            remainderYaw -= snappedYaw;
            remainderPitch -= snappedPitch;
            apply(snappedYaw, snappedPitch);
        } else {
            apply(remainderYaw, remainderPitch);
            remainderYaw = 0.0F;
            remainderPitch = 0.0F;
        }
    }

    public static void rotateTo(LocalPlayer player, Entity target) {
        Vec3 delta = target.getEyePosition().subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));

        setRotation(yaw, pitch);
    }

    public static void rotateToPosition(LocalPlayer player, Vec3 pos) {
        Vec3 delta = pos.subtract(player.getEyePosition());

        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));

        setRotation(yaw, pitch);
    }

    public static float getFovToEntity(LocalPlayer player, Entity entity) {
        double diffX = entity.getX() - player.getX();
        double diffZ = entity.getZ() - player.getZ();

        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0D);
        float deltaYaw = Mth.wrapDegrees(yaw - (active ? serverYaw : player.getYRot()));

        return Math.abs(deltaYaw);
    }

    public static void syncFreeLook(float yaw, float pitch) {
        if (active) {
            return;
        }

        freeYaw = yaw;
        freePitch = pitch;
    }

    public static boolean onMouseTurn(double yawInput, double pitchInput) {
        if (!active) {
            return false;
        }

        freeYaw += (float) yawInput * MOUSE_TURN_SCALE;
        freePitch = Mth.clamp(
                freePitch + (float) pitchInput * MOUSE_TURN_SCALE,
                -90.0F,
                90.0F
        );
        return true;
    }

    public static void applyRenderInterpolation() {
        if (!active || !hasLastAngle) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            hasLastAngle = false;
            return;
        }

        player.yRotO = lastYaw;
        player.xRotO = lastPitch;
        player.yHeadRotO = lastHeadYaw;
        player.yBodyRotO = lastBodyYaw;
    }

    public static void disengage() {
        if (!active) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            clear();
            return;
        }

        float deltaYaw = Mth.wrapDegrees(freeYaw - serverYaw);
        float deltaPitch = freePitch - serverPitch;

        if (Math.abs(deltaYaw) < 2.0F && Math.abs(deltaPitch) < 2.0F) {
            clear();
        } else {
            rememberRenderAngles(player);
            serverYaw = serverYaw + deltaYaw * 0.4F;
            serverPitch = Mth.clamp(serverPitch + deltaPitch * 0.4F, -90.0F, 90.0F);

            player.setYRot(serverYaw);
            player.setXRot(serverPitch);
            player.yHeadRot = serverYaw;
            player.yBodyRot = serverYaw;
        }
    }

    public static void clear() {
        LocalPlayer player = mc.player;
        if (player != null) {
            if (active) {
                player.setYRot(freeYaw);
                player.setXRot(freePitch);
                player.yRotO = freeYaw;
                player.xRotO = freePitch;
                player.yHeadRot = freeYaw;
                player.yBodyRot = freeYaw;
            }
            serverYaw = player.getYRot();
            serverPitch = player.getXRot();
            freeYaw = player.getYRot();
            freePitch = player.getXRot();
        }
        active = false;
        hasLastAngle = false;
        remainderYaw = 0.0F;
        remainderPitch = 0.0F;
    }

    private static void rememberRenderAngles(LocalPlayer player) {
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        lastHeadYaw = player.yHeadRot;
        lastBodyYaw = player.yBodyRot;
        hasLastAngle = true;
    }

    private static double gcdStep() {
        double sensitivity = mc.options.sensitivity().get();
        double f = sensitivity * 0.6D + 0.2D;
        return f * f * f * 1.2D;
    }
}