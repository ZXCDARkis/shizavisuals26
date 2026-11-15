package org.shizavisuals.utils.render.spatial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector2f;
import org.joml.Vector3f;

/** Математика квада: трансформ экрана, базис плоскости, ray-plane маппинг мыши. */
public final class SpatialRenderUtil {
    private SpatialRenderUtil() {
    }

    public record ScreenTransformConfig(float distance, float sideOffset, float heightOffset, float yawOffset,
                                        float pitchOffset, float scale) {
    }

    public record QuadBasis(Vector3f centerOffset, Vector3f right, Vector3f up, Vector3f normal,
                            float halfWidth, float halfHeight) {
    }

    public static void addQuadVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z,
                                     float u, float v, int rgb, int a) {
        buffer.addVertex(pose, x, y, z).setUv(u, v).setColor(rgb, rgb, rgb, a);
    }

    public static void addScreenQuad(VertexConsumer buffer, Matrix4f pose, float aspect, int rgb, int a) {
        float halfWidth = aspect * 0.5F;
        float halfHeight = 0.5F;
        addQuadVertex(buffer, pose, -halfWidth, -halfHeight, 0.0F, 0.0F, 0.0F, rgb, a);
        addQuadVertex(buffer, pose, halfWidth, -halfHeight, 0.0F, 1.0F, 0.0F, rgb, a);
        addQuadVertex(buffer, pose, halfWidth, halfHeight, 0.0F, 1.0F, 1.0F, rgb, a);
        addQuadVertex(buffer, pose, -halfWidth, halfHeight, 0.0F, 0.0F, 1.0F, rgb, a);
    }

    /** Третье лицо: экран перед игроком, повёрнутый к камере. */
    public static void applyScreenTransform(PoseStack matrices, float yawRadians, ScreenTransformConfig config) {
        float yawOffsetRad = (float) Math.toRadians(config.yawOffset());
        float pitchOffsetRad = (float) Math.toRadians(config.pitchOffset());
        matrices.translate(
                -Mth.sin(yawRadians) * config.distance() + Mth.cos(yawRadians) * config.sideOffset(),
                config.heightOffset(),
                Mth.cos(yawRadians) * config.distance() + Mth.sin(yawRadians) * config.sideOffset()
        );
        matrices.mulPose(new Quaternionf().rotateY(-yawRadians + yawOffsetRad).rotateX(pitchOffsetRad).get(new Matrix4f()));
    }

    public static QuadBasis computeQuadBasis(Matrix4f worldPose, float aspect, float scale) {
        Vector3f centerOffset = worldPose.transformPosition(new Vector3f());
        Vector3f right = worldPose.transformDirection(1.0F, 0.0F, 0.0F, new Vector3f()).normalize();
        Vector3f up = worldPose.transformDirection(0.0F, 1.0F, 0.0F, new Vector3f()).normalize();
        Vector3f normal = new Vector3f(right).cross(up).normalize();
        return new QuadBasis(centerOffset, right, up, normal, aspect * 0.5F * scale, 0.5F * scale);
    }

    /** Луч из физического курсора в пространстве камеры (без FOV-модификаторов квада). */
    private static float[] buildCameraRayDirection(double screenX, double screenY) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getScreenWidth();
        int screenHeight = mc.getWindow().getScreenHeight();
        double ndcX = screenX / screenWidth * 2.0 - 1.0;
        double ndcY = 1.0 - screenY / screenHeight * 2.0;
        Camera camera = mc.gameRenderer.mainCamera();
        float yawRadians = (float) Math.toRadians(camera.yRot());
        float pitchRadians = (float) Math.toRadians(camera.xRot());
        float fx = (float) (-Math.sin(yawRadians) * Math.cos(pitchRadians));
        float fy = (float) (-Math.sin(pitchRadians));
        float fz = (float) (Math.cos(yawRadians) * Math.cos(pitchRadians));
        float fInv = 1.0F / (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx *= fInv;
        fy *= fInv;
        fz *= fInv;
        float rx = -fz;
        float ry = 0.0F;
        float rInv = 1.0F / (float) Math.sqrt(rx * rx + fx * fx);
        rx *= rInv;
        float rz = fx * rInv;
        float ux = ry * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - ry * fx;
        float uInv = 1.0F / (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux *= uInv;
        uy *= uInv;
        uz *= uInv;
        float fovDegrees = camera.getFov();
        float aspect = (float) screenWidth / screenHeight;
        float tanHalfFovY = (float) Math.tan(Math.toRadians(fovDegrees / 2.0));
        float tanHalfFovX = tanHalfFovY * aspect;
        float xCoeff = (float) ndcX * tanHalfFovX;
        float yCoeff = (float) ndcY * tanHalfFovY;
        float dx = fx + rx * xCoeff + ux * yCoeff;
        float dy = fy + ry * xCoeff + uy * yCoeff;
        float dz = fz + rz * xCoeff + uz * yCoeff;
        float dInv = 1.0F / (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new float[]{dx * dInv, dy * dInv, dz * dInv};
    }

    public static Vector2f getQuadPlanePosition(double screenX, double screenY, QuadBasis basis) {
        float[] ray = buildCameraRayDirection(screenX, screenY);
        float dx = ray[0];
        float dy = ray[1];
        float dz = ray[2];
        Vector3f norm = basis.normal();
        float denom = dx * norm.x() + dy * norm.y() + dz * norm.z();
        if (Math.abs(denom) < 1.0E-6F) {
            return null;
        }
        Vector3f center = basis.centerOffset();
        float t = (center.x() * norm.x() + center.y() * norm.y() + center.z() * norm.z()) / denom;
        if (t <= 0.0F || Float.isNaN(t) || Float.isInfinite(t)) {
            return null;
        }
        float hx = dx * t - center.x();
        float hy = dy * t - center.y();
        float hz = dz * t - center.z();
        Vector3f bRight = basis.right();
        Vector3f bUp = basis.up();
        float localX = hx * bRight.x() + hy * bRight.y() + hz * bRight.z();
        float localY = hx * bUp.x() + hy * bUp.y() + hz * bUp.z();
        return new Vector2f(localX, localY);
    }

    private static Vector2f getQuadUv(double screenX, double screenY, QuadBasis basis) {
        if (basis.halfWidth() <= 0.0F || basis.halfHeight() <= 0.0F) {
            return null;
        }
        Vector2f plane = getQuadPlanePosition(screenX, screenY, basis);
        if (plane == null) {
            return null;
        }
        float u = (plane.x / basis.halfWidth() + 1.0F) / 2.0F;
        float v = (plane.y / basis.halfHeight() + 1.0F) / 2.0F;
        return Float.isNaN(u) || Float.isNaN(v) ? null : new Vector2f(u, v);
    }

    public static boolean isMouseOverQuad(double screenX, double screenY, QuadBasis basis) {
        Vector2f uv = getQuadUv(screenX, screenY, basis);
        return uv != null && uv.x >= 0.0F && uv.x <= 1.0F && uv.y >= 0.0F && uv.y <= 1.0F;
    }

    /** Позиция курсора в пикселях фреймбуфера после проекции на квад. */
    public static Vector2d getQuadScreenPosition(double screenX, double screenY, QuadBasis basis) {
        Vector2f uv = getQuadUv(screenX, screenY, basis);
        if (uv == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        float clampedU = Mth.clamp(uv.x, 0.0F, 1.0F);
        float clampedV = Mth.clamp(uv.y, 0.0F, 1.0F);
        int targetWidth = mc.getWindow().getWidth();
        int targetHeight = mc.getWindow().getHeight();
        return new Vector2d(clampedU * targetWidth, (1.0 - clampedV) * targetHeight);
    }
}
