package org.shizavisuals.webpanel;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class WebPanelGrab {
    private static final double MIN_WIDTH = 0.35;
    private static final double MAX_WIDTH = 64.0;
    private static final double MIN_HEIGHT = 0.25;
    private static final double MAX_HEIGHT = 40.0;
    private static final float SMOOTH_TIME = 0.085f;

    private static final double CLEARANCE = 0.28;

    private static final double CLEARANCE_SIZE_SHARE = 0.14;

    private static final double MIN_CLEARANCE = 0.52;

    private static final double GRAB_OFFSET_SHARE = 0.75;

    public static final double SNAP_GAP = 0.055;

    private static final float MAX_TURN_PER_SECOND = 200.0f;

    private static final float TURN_DEAD_ZONE = 0.03f;

    private final double maxDistance;
    private boolean snapEnabled = true;

    @Nullable
    private WebPanelInstance dragging;
    private double distance;
    private double distanceTarget;
    private double grabU;
    private double grabV;
    private boolean level;

    @Nullable
    private WebPanelInstance resizing;
    private int corner;
    private Vec3 center = Vec3.ZERO;
    private Vec3 right = Vec3.ZERO;
    private Vec3 up = Vec3.ZERO;
    private Vec3 normal = Vec3.ZERO;

    public WebPanelGrab(double maxDistance) {
        this.maxDistance = maxDistance;
    }

    @Nullable
    public WebPanelInstance getDragging() {
        return this.dragging;
    }

    @Nullable
    public WebPanelInstance getResizing() {
        return this.resizing;
    }

    public int getCorner() {
        return this.corner;
    }

    public boolean isActive() {
        return this.dragging != null || this.resizing != null;
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
    }

    public void toggleLevel() {
        this.level = !this.level;
    }

    private double minDistance(WebPanelInstance panel) {
        return Mth.clamp(CLEARANCE + panel.boundingRadius() * CLEARANCE_SIZE_SHARE,
                MIN_CLEARANCE, 8.0);
    }

    public void scroll(double amount) {
        WebPanelInstance panel = this.dragging;
        double min = panel == null ? MIN_CLEARANCE : minDistance(panel);
        this.distanceTarget = Mth.clamp(this.distanceTarget + amount * 0.45,
                min, this.maxDistance);
    }

    public void beginDrag(WebPanelInstance panel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        WebPanelInstance.Hit hit = panel.hitTest(eye, mc.player.getViewVector(1.0F), this.maxDistance);
        double min = minDistance(panel);
        this.dragging = panel;
        this.distance = Math.max(min,
                hit != null ? hit.t() : panel.getCenter().distanceTo(eye));
        this.distanceTarget = this.distance;
        this.grabU = hit != null ? hit.u() : 0.0;
        this.grabV = hit != null ? hit.v() : panel.getHeight() * 0.5;
        this.level = false;
        panel.setGrabbed(true);
    }

    public void beginResize(WebPanelInstance panel, int corner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        this.center = panel.getCenter();
        this.right = panel.getRight();
        this.up = panel.getUp();
        this.normal = panel.getNormal();
        this.corner = corner;
        this.resizing = panel;
        panel.setGrabbed(true);
        panel.setChromeHidden(true);
    }

    public void update(float deltaSeconds) {
        updateDrag(deltaSeconds);
        updateResize();
    }

    private void updateDrag(float deltaSeconds) {
        Minecraft mc = Minecraft.getInstance();
        WebPanelInstance panel = this.dragging;
        if (panel == null) {
            return;
        }
        if (mc.player == null || panel.isClosing()) {
            end();
            return;
        }
        float smooth = 1.0f - (float) Math.exp(-deltaSeconds / SMOOTH_TIME);

        double min = minDistance(panel);
        this.distanceTarget = Mth.clamp(this.distanceTarget, min, this.maxDistance);
        this.distance += (this.distanceTarget - this.distance) * smooth;
        this.distance = Mth.clamp(this.distance, min, this.maxDistance);

        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);

        if (this.snapEnabled && snapToSurface(panel, eye, smooth, deltaSeconds)) {
            return;
        }

        Vec3 toEye = eye.subtract(panel.getCenter());
        float targetYaw = panel.getYaw();
        float targetPitch = panel.getPitch();
        if (toEye.lengthSqr() > 1.0E-6) {
            Vec3 n = toEye.normalize();
            targetPitch = (float) Math.toDegrees(Math.asin(Mth.clamp(n.y, -1.0, 1.0)));
            if (Math.abs(targetPitch) < WebPanelInstance.MAX_FREE_PITCH) {
                targetYaw = (float) Math.toDegrees(Math.atan2(n.x, -n.z));
            }
        }
        if (this.level) {
            targetPitch = 0.0f;
        }
        turnTowards(panel, targetYaw,
                Mth.clamp(targetPitch, -WebPanelInstance.MAX_FREE_PITCH,
                        WebPanelInstance.MAX_FREE_PITCH), smooth, deltaSeconds);

        double limit = this.distance * GRAB_OFFSET_SHARE;
        double half = panel.getHeight() * 0.5;
        double grabU = Mth.clamp(this.grabU, -limit, limit);
        double grabV = half + Mth.clamp(this.grabV - half, -limit, limit);

        Vec3 target = eye.add(look.scale(this.distance))
                .subtract(panel.getRight().scale(grabU))
                .subtract(panel.getUp().scale(grabV));
        Vec3 current = panel.getOrigin();
        panel.setOrigin(new Vec3(
                current.x + (target.x - current.x) * smooth,
                current.y + (target.y - current.y) * smooth,
                current.z + (target.z - current.z) * smooth));
        pushOutOfPlayer(panel, eye, min);
    }

    private void turnTowards(WebPanelInstance panel, float targetYaw, float targetPitch,
                              float smooth, float deltaSeconds) {
        float limit = MAX_TURN_PER_SECOND * deltaSeconds;
        float dYaw = Mth.wrapDegrees(targetYaw - panel.getYaw()) * smooth;
        float dPitch = (targetPitch - panel.getPitch()) * smooth;
        dYaw = Mth.clamp(dYaw, -limit, limit);
        dPitch = Mth.clamp(dPitch, -limit, limit);        if (Math.abs(dYaw) < TURN_DEAD_ZONE) {
            dYaw = 0.0f;
        }
        if (Math.abs(dPitch) < TURN_DEAD_ZONE) {
            dPitch = 0.0f;
        }
        if (dYaw != 0.0f || dPitch != 0.0f) {
            panel.setOrientation(panel.getYaw() + dYaw, panel.getPitch() + dPitch);
        }
    }

    private void pushOutOfPlayer(WebPanelInstance panel, Vec3 eye, double min) {
        Vec3 toCenter = panel.getCenter().subtract(eye);
        double length = toCenter.length();
        if (length >= min) {
            return;
        }
        Vec3 direction = length > 1.0E-4 ? toCenter.scale(1.0 / length) : panel.getNormal().reverse();
        panel.setOrigin(panel.getOrigin().add(direction.scale(min - length)));
    }

    private boolean snapToSurface(WebPanelInstance panel, Vec3 eye, float smooth,
                                   float deltaSeconds) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.player.pick(this.distance + 0.75, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult block)) {
            return false;
        }
        if (hit.getLocation().distanceTo(eye) > this.distance + 0.75) {
            return false;
        }
        Vec3 normal = block.getDirection().getUnitVec3();
        turnTowards(panel, WebPanelPose.snapYaw(normal, panel.getYaw()),
                WebPanelPose.snapPitch(normal), smooth, deltaSeconds);

        Vec3 target = WebPanelPose.snapOrigin(hit.getLocation(), normal, panel.getUp(),
                panel.getHeight(), SNAP_GAP);
        Vec3 current = panel.getOrigin();
        panel.setOrigin(new Vec3(
                current.x + (target.x - current.x) * smooth,
                current.y + (target.y - current.y) * smooth,
                current.z + (target.z - current.z) * smooth));
        return true;
    }

    private void updateResize() {
        Minecraft mc = Minecraft.getInstance();
        WebPanelInstance panel = this.resizing;
        if (panel == null) {
            return;
        }
        if (mc.player == null || panel.isClosing()) {
            end();
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);
        double denominator = look.dot(this.normal);
        if (Math.abs(denominator) < 1.0E-6) {
            return;
        }
        double t = this.center.subtract(eye).dot(this.normal) / denominator;
        if (t <= 0.0) {
            return;
        }
        Vec3 delta = eye.add(look.scale(t)).subtract(this.center);
        panel.setTargetSize(
                Mth.clamp(Math.abs(delta.dot(this.right)) * 2.0, MIN_WIDTH, MAX_WIDTH),
                Mth.clamp(Math.abs(delta.dot(this.up)) * 2.0, MIN_HEIGHT, MAX_HEIGHT));
        panel.setOrigin(this.center.subtract(this.up.scale(panel.getHeight() * 0.5)));
    }

    public void end() {
        if (this.dragging != null) {
            this.dragging.setGrabbed(false);
            this.dragging = null;
        }
        if (this.resizing != null) {
            WebPanelInstance panel = this.resizing;
            panel.snapSize();
            panel.setOrigin(this.center.subtract(this.up.scale(panel.getHeight() * 0.5)));
            panel.applyAspect();
            panel.setGrabbed(false);
            panel.setChromeHidden(false);
            this.resizing = null;
        }
    }
}
