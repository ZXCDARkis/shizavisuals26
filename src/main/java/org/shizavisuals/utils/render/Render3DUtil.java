package org.shizavisuals.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class Render3DUtil {
    private static final Matrix4f LEVEL_PROJECTION = new Matrix4f();
    private static boolean levelProjectionCaptured;

    private Render3DUtil() {
    }

    public static Vec3 interpolatedPosition(Entity entity, float tickDelta) {
        return new Vec3(
                Mth.lerp(tickDelta, entity.xOld, entity.getX()),
                Mth.lerp(tickDelta, entity.yOld, entity.getY()),
                Mth.lerp(tickDelta, entity.zOld, entity.getZ())
        );
    }

    public static Matrix4f buildBillboardPose(Camera camera, Vec3 worldPos, double offsetY, float zRotationDegrees) {
        Vec3 cameraPos = camera.position();
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180.0F));
        poseStack.translate(
                worldPos.x - cameraPos.x,
                worldPos.y - cameraPos.y + offsetY,
                worldPos.z - cameraPos.z
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
        if (zRotationDegrees != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(zRotationDegrees));
        }
        return new Matrix4f(poseStack.last().pose());
    }

    public static Matrix4f cameraViewPose(Camera camera) {
        return camera.getViewRotationMatrix(new Matrix4f());
    }

    public static Vector4f toViewSpace(Vec3 point, Vec3 cameraPos, Matrix4f pose) {
        return new Vector4f(
                (float) (point.x - cameraPos.x),
                (float) (point.y - cameraPos.y),
                (float) (point.z - cameraPos.z),
                1.0F
        ).mul(pose);
    }

    public static void captureLevelProjection(Matrix4fc projection) {
        LEVEL_PROJECTION.set(projection);
        levelProjectionCaptured = true;
    }

    public static Matrix4f levelProjectionCopy() {
        return levelProjectionCaptured ? new Matrix4f(LEVEL_PROJECTION) : null;
    }

    public static ScreenPoint projectToScreen(Minecraft mc, Vec3 pos) {
        if (pos == null || mc.getWindow() == null || mc.gameRenderer == null || !levelProjectionCaptured) {
            return null;
        }

        CameraRenderState cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (!cameraState.initialized) {
            return null;
        }

        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        if (guiWidth <= 0 || guiHeight <= 0) {
            return null;
        }

        Vector4f clip = new Vector4f(
                (float) (pos.x - cameraState.pos.x()),
                (float) (pos.y - cameraState.pos.y()),
                (float) (pos.z - cameraState.pos.z()),
                1.0F
        );
        cameraState.viewRotationMatrix.transform(clip);
        LEVEL_PROJECTION.transform(clip);
        if (clip.w <= 1.0E-4F) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (Math.abs(ndcX) > 2.0F || Math.abs(ndcY) > 2.0F) {
            return null;
        }

        float screenX = (ndcX + 1.0F) * 0.5F * guiWidth;
        float screenY = (1.0F - ndcY) * 0.5F * guiHeight;
        return new ScreenPoint(screenX, screenY);
    }

    public static ScreenBounds includeInBounds(Minecraft mc, ScreenBounds bounds, double x, double y, double z) {
        ScreenPoint point = projectToScreen(mc, new Vec3(x, y, z));
        if (point == null) {
            return bounds;
        }
        if (bounds == null) {
            return new ScreenBounds(point.x(), point.y(), point.x(), point.y());
        }
        return new ScreenBounds(
                Math.min(bounds.minX(), point.x()),
                Math.min(bounds.minY(), point.y()),
                Math.max(bounds.maxX(), point.x()),
                Math.max(bounds.maxY(), point.y())
        );
    }

    public static ScreenBounds projectBoxBounds(Minecraft mc,
                                                double minX, double minY, double minZ,
                                                double maxX, double maxY, double maxZ) {
        ScreenBounds bounds = null;
        bounds = includeInBounds(mc, bounds, minX, minY, minZ);
        bounds = includeInBounds(mc, bounds, minX, minY, maxZ);
        bounds = includeInBounds(mc, bounds, minX, maxY, minZ);
        bounds = includeInBounds(mc, bounds, minX, maxY, maxZ);
        bounds = includeInBounds(mc, bounds, maxX, minY, minZ);
        bounds = includeInBounds(mc, bounds, maxX, minY, maxZ);
        bounds = includeInBounds(mc, bounds, maxX, maxY, minZ);
        bounds = includeInBounds(mc, bounds, maxX, maxY, maxZ);
        return bounds;
    }

    public record ScreenPoint(float x, float y) {
    }

    public record ScreenBounds(float minX, float minY, float maxX, float maxY) {
        public float width() {
            return maxX - minX;
        }

        public float height() {
            return maxY - minY;
        }
    }
}
