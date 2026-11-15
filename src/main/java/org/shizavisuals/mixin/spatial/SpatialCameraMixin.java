package org.shizavisuals.mixin.spatial;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.shizavisuals.feature.impl.visual.SpatialInventoryFeature;
import org.shizavisuals.mixin.accessor.MouseHandlerAccessor;
import org.shizavisuals.utils.render.spatial.SpatialInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Орбита камеры при 3D-инвентаре: отъезд + параллакс + плавный возврат при закрытии. */
@Mixin(Camera.class)
public abstract class SpatialCameraMixin {
    @Shadow
    private Entity entity;
    @Shadow
    private Vec3 position;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Final
    @Shadow
    private Quaternionf rotation;
    @Final
    @Shadow
    private Vector3f forwards;
    @Final
    @Shadow
    private Vector3f up;
    @Final
    @Shadow
    private Vector3f left;

    @Unique
    private static Vec3 shizavisuals$startPos;
    @Unique
    private static float shizavisuals$startYRot;
    @Unique
    private static float shizavisuals$startXRot;
    @Unique
    private static long shizavisuals$transitionStartTime;
    @Unique
    private static boolean shizavisuals$isTransitioning;
    @Unique
    private static boolean shizavisuals$wasCapturing;
    @Unique
    private static float shizavisuals$parallaxYawOffset;
    @Unique
    private static float shizavisuals$parallaxPitchOffset;
    @Unique
    private static long shizavisuals$lastParallaxNanos;
    @Unique
    private static Vec3 shizavisuals$lastOffset;
    @Unique
    private static float shizavisuals$lastYRot;
    @Unique
    private static float shizavisuals$lastXRot;
    @Unique
    private static boolean shizavisuals$closingCameraActive;

    @Unique
    private void shizavisuals$updateLookVectors() {
        this.forwards.set(0.0F, 0.0F, -1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void shizavisuals$modifyCamera(float partialTicks, CallbackInfo ci) {
        SpatialInventoryFeature f = SpatialInventory.feature();
        boolean capturing = SpatialInventory.shouldCapture();
        Minecraft mc = Minecraft.getInstance();
        if (capturing && f != null && this.entity != null && mc.level != null && this.entity.level() != null) {
            boolean resumedFromClosing = shizavisuals$closingCameraActive;
            shizavisuals$closingCameraActive = false;
            long transitionMs = Math.max(1L, f.transitionMs.getValue().longValue());

            SpatialCameraMixin.CameraTransform transform = this.shizavisuals$computeTransform(f, partialTicks);
            Vec3 newTargetPos = transform.pos();
            float newTargetYRot = transform.yaw();
            float newTargetXRot = transform.pitch();

            if (!shizavisuals$wasCapturing || !shizavisuals$isTransitioning || resumedFromClosing) {
                this.shizavisuals$startTransition(newTargetYRot, newTargetXRot);
            }

            this.shizavisuals$applyCameraUpdate(f, transitionMs, newTargetPos, newTargetYRot, newTargetXRot);
            SpatialInventory.setScreenAnchorDelta(newTargetPos.subtract(this.position));
            shizavisuals$lastOffset = this.position.subtract(SpatialInventory.lerpEntityPosition(this.entity, partialTicks));
            shizavisuals$lastYRot = this.yRot;
            shizavisuals$lastXRot = this.xRot;
            this.rotation.rotationYXZ(
                    (float) (Math.PI - this.yRot * (Math.PI / 180.0)),
                    (float) (-this.xRot * (Math.PI / 180.0)),
                    0.0F);
            this.shizavisuals$updateLookVectors();
            shizavisuals$wasCapturing = true;
        } else if (SpatialInventory.isClosingWithCameraTransition()
                && shizavisuals$lastOffset != null
                && this.entity != null
                && f != null
                && mc.level != null) {
            shizavisuals$closingCameraActive = true;
            float easedProgress = SpatialInventory.getCloseBlend();
            Vec3 screenCameraPos = SpatialInventory.lerpEntityPosition(this.entity, partialTicks).add(shizavisuals$lastOffset);
            Vec3 normalPos = this.position;
            float normalYRot = this.yRot;
            float normalXRot = this.xRot;
            this.position = new Vec3(
                    SpatialInventory.lerp(screenCameraPos.x, normalPos.x, easedProgress),
                    SpatialInventory.lerp(screenCameraPos.y, normalPos.y, easedProgress),
                    SpatialInventory.lerp(screenCameraPos.z, normalPos.z, easedProgress));
            SpatialInventory.setScreenAnchorDelta(screenCameraPos.subtract(this.position));
            this.yRot = SpatialInventory.rotLerp(shizavisuals$lastYRot, normalYRot, easedProgress);
            this.xRot = normalXRot;
            this.rotation.rotationYXZ(
                    (float) (Math.PI - this.yRot * (Math.PI / 180.0)),
                    (float) (-this.xRot * (Math.PI / 180.0)),
                    0.0F);
            this.shizavisuals$updateLookVectors();
        } else {
            this.shizavisuals$resetCameraState();
        }
    }

    @Unique
    private SpatialCameraMixin.CameraTransform shizavisuals$computeTransform(SpatialInventoryFeature f, float partialTicks) {
        float distance = f.cameraDistance.getValue().floatValue();
        float sideOffset = f.cameraSideOffset.getValue().floatValue();
        float heightOffset = Mth.clamp(f.cameraHeightOffset.getValue().floatValue(), -4.0F, 4.0F);
        float baseYaw = SpatialInventory.anchoredYaw();
        float orbit = SpatialInventory.orbitYaw(f);

        Minecraft mc = Minecraft.getInstance();
        MouseHandlerAccessor accessor = (MouseHandlerAccessor) mc.mouseHandler;
        double rawX = SpatialInventory.getPhysicalX(mc.getWindow().getScreenWidth() / 2.0);
        double rawY = SpatialInventory.getPhysicalY(mc.getWindow().getScreenHeight() / 2.0);
        float normX = Mth.clamp((float) rawX / mc.getWindow().getScreenWidth() * 2.0F - 1.0F, -1.0F, 1.0F);
        float normY = Mth.clamp((float) rawY / mc.getWindow().getScreenHeight() * 2.0F - 1.0F, -1.0F, 1.0F);
        this.shizavisuals$smoothParallaxOffsets(
                normX * 90.0F * f.parallaxYaw.getValue().floatValue(),
                normY * 180.0F * f.parallaxPitch.getValue().floatValue());

        float yaw = baseYaw + orbit + shizavisuals$parallaxYawOffset;
        float pitch = f.cameraTargetPitch.getValue().floatValue() + shizavisuals$parallaxPitchOffset;
        float positionYaw = baseYaw + orbit;

        float yawRadians = (float) Math.toRadians(positionYaw);
        Vec3 entityPos = SpatialInventory.lerpEntityPosition(this.entity, partialTicks);
        double camX = entityPos.x + Math.sin(yawRadians) * distance + Math.cos(yawRadians) * sideOffset;
        double camY = entityPos.y + heightOffset;
        double camZ = entityPos.z - Math.cos(yawRadians) * distance + Math.sin(yawRadians) * sideOffset;
        Vec3 eyePos = new Vec3(entityPos.x, entityPos.y + this.entity.getEyeHeight(), entityPos.z);
        Vec3 target = shizavisuals$adjustCameraPositionForCollision(this.entity, eyePos, new Vec3(camX, camY, camZ));
        return new SpatialCameraMixin.CameraTransform(target, yaw, pitch);
    }

    @Unique
    private void shizavisuals$smoothParallaxOffsets(float targetYaw, float targetPitch) {
        long now = System.nanoTime();
        float dt = shizavisuals$lastParallaxNanos == 0L ? 0.016F : Math.min((float) (now - shizavisuals$lastParallaxNanos) / 1.0E9F, 0.1F);
        shizavisuals$lastParallaxNanos = now;
        float alpha = 1.0F - (float) Math.exp(-dt / 0.05F);
        shizavisuals$parallaxYawOffset = shizavisuals$parallaxYawOffset + (targetYaw - shizavisuals$parallaxYawOffset) * alpha;
        shizavisuals$parallaxPitchOffset = shizavisuals$parallaxPitchOffset + (targetPitch - shizavisuals$parallaxPitchOffset) * alpha;
    }

    /** Укорачивает дистанцию камеры, если путь упирается в блоки. */
    @Unique
    private static Vec3 shizavisuals$adjustCameraPositionForCollision(Entity entity, Vec3 eye, Vec3 target) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return target;
        }
        Vec3 delta = target.subtract(eye);
        double maxDist = delta.length();
        if (maxDist < 0.001D) {
            return target;
        }
        double cameraDist = maxDist;
        for (int i = 0; i < 8; i++) {
            double offsetX = (i & 1) * 2 - 1;
            double offsetY = (i >> 1 & 1) * 2 - 1;
            double offsetZ = (i >> 2 & 1) * 2 - 1;
            Vec3 offset = new Vec3(offsetX * 0.2D, offsetY * 0.2D, offsetZ * 0.2D);
            Vec3 from = eye.add(offset);
            net.minecraft.world.level.ClipContext clipContext =
                    new net.minecraft.world.level.ClipContext(from, target.add(offset),
                            net.minecraft.world.level.ClipContext.Block.VISUAL,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, entity);
            net.minecraft.world.phys.HitResult hit = level.clip(clipContext);
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                cameraDist = Math.min(cameraDist, hit.getLocation().distanceTo(from));
            }
        }
        return eye.add(delta.scale(cameraDist / maxDist));
    }

    @Unique
    private void shizavisuals$startTransition(float newTargetYRot, float newTargetXRot) {
        Vec3 savedStartPos = SpatialInventory.getCaptureStartPos();
        if (savedStartPos != null) {
            shizavisuals$startPos = savedStartPos;
            shizavisuals$startYRot = SpatialInventory.getCaptureStartYRot();
            shizavisuals$startXRot = SpatialInventory.getCaptureStartXRot();
        } else {
            shizavisuals$startPos = new Vec3(this.position.x, this.position.y, this.position.z);
            shizavisuals$startYRot = this.yRot;
            shizavisuals$startXRot = this.xRot;
        }
        shizavisuals$transitionStartTime = System.currentTimeMillis();
        shizavisuals$isTransitioning = true;
    }

    @Unique
    private void shizavisuals$applyCameraUpdate(SpatialInventoryFeature f, long transitionMs,
                                                Vec3 newTargetPos, float newTargetYRot, float newTargetXRot) {
        long elapsed = System.currentTimeMillis() - shizavisuals$transitionStartTime;
        float progress = Math.min((float) elapsed / transitionMs, 1.0F);
        float easedProgress = SpatialInventory.easeInOutSine(progress);
        this.position = new Vec3(
                SpatialInventory.lerp(shizavisuals$startPos.x, newTargetPos.x, easedProgress),
                SpatialInventory.lerp(shizavisuals$startPos.y, newTargetPos.y, easedProgress),
                SpatialInventory.lerp(shizavisuals$startPos.z, newTargetPos.z, easedProgress));
        this.yRot = SpatialInventory.rotLerp(shizavisuals$startYRot, newTargetYRot, easedProgress);
        this.xRot = progress < 1.0F
                ? SpatialInventory.lerp(shizavisuals$startXRot, newTargetXRot, easedProgress)
                : newTargetXRot;
    }

    @Unique
    private void shizavisuals$resetCameraState() {
        shizavisuals$startPos = null;
        shizavisuals$lastOffset = null;
        shizavisuals$closingCameraActive = false;
        shizavisuals$wasCapturing = false;
        shizavisuals$isTransitioning = false;
        shizavisuals$parallaxYawOffset = 0.0F;
        shizavisuals$parallaxPitchOffset = 0.0F;
        shizavisuals$lastParallaxNanos = 0L;
        SpatialInventory.setScreenAnchorDelta(Vec3.ZERO);
    }

    @Unique
    private record CameraTransform(Vec3 pos, float yaw, float pitch) {
    }
}
