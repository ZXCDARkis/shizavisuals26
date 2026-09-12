package org.shizavisuals.webpanel;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;
import org.shizavisuals.utils.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public final class WebPanelRenderer {
    private WebPanelRenderer() {
    }

    private static final float SDF_HEIGHT = 256.0f;

    private static final float TINT_ALPHA = 0.358f;

    private static final float BLUR_SIGMA_RATIO = 63.0f / 1440.0f;

    private static final float BAR_ALPHA_IDLE = 0.28f;
    private static final float BAR_ALPHA_HOVER = 0.97f;

    private static final float BAR_GROW_THICKNESS = 0.16f;
    private static final float BAR_GROW_LENGTH = 0.045f;

    private static final int CLOSE_GLYPH = ColorUtil.rgb(46, 46, 54);

    private static final double BEND_STEP_DEGREES = 4.0;

    private static final double VIEW_CONE = Math.toRadians(95.0);

    private static final double NEAR_DISTANCE = 10.0;

    private static final List<WebPanelInstance> ORDERED = new ArrayList<>();

    private static final ArrayList<Double> DISTANCES = new ArrayList<>();

    private static final Vector4f SCRATCH_VIEW = new Vector4f();

    public static boolean render(Vec3 camera, List<WebPanelInstance> panels,
                                  double renderDistance, float deltaSeconds,
                                  @Nullable WebPanelInstance hovered) {
        if (panels.isEmpty()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera cameraObj = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
        WebPanelView.update(cameraObj);

        double sqDistance = renderDistance * renderDistance;
        Vec3 forward = cameraForward(cameraObj);

        ORDERED.clear();
        DISTANCES.clear();
        for (WebPanelInstance panel : panels) {
            Vec3 center = panel.getCenter();
            double distance = center.distanceToSqr(camera);
            if (distance <= sqDistance && inView(center, camera, forward, panel.boundingRadius())) {
                ORDERED.add(panel);
                DISTANCES.add(distance);
            } else {
                panel.updateDormancy(false, deltaSeconds);
                panel.tickBrowser(deltaSeconds, 0.0f);
            }
        }
        if (ORDERED.isEmpty()) {
            return false;
        }
        sortFarToNear();

        boolean needed = false;
        for (WebPanelInstance panel : ORDERED) {
            if (needsBackdrop(panel, camera)) {
                needed = true;
                break;
            }
        }
        GpuTextureView blur = needed ? WebPanelBlur.capture(blurSigma()) : null;

        boolean drawn = false;
        for (int i = 0; i < ORDERED.size(); i++) {
            WebPanelInstance panel = ORDERED.get(i);
            panel.updateDormancy(true, deltaSeconds);
            panel.tickBrowser(deltaSeconds, panel == hovered ? 1.0f
                    : DISTANCES.get(i) <= NEAR_DISTANCE * NEAR_DISTANCE ? 0.5f : 0.25f);
            drawGlass(camera, panel, blur);
            drawChrome(camera, panel);
            drawn = true;
        }
        ORDERED.clear();
        DISTANCES.clear();
        return drawn;
    }

    private static void sortFarToNear() {
        for (int i = 1; i < ORDERED.size(); i++) {
            WebPanelInstance panel = ORDERED.get(i);
            double distance = DISTANCES.get(i);
            int j = i - 1;
            while (j >= 0 && DISTANCES.get(j) < distance) {
                ORDERED.set(j + 1, ORDERED.get(j));
                DISTANCES.set(j + 1, DISTANCES.get(j));
                j--;
            }
            ORDERED.set(j + 1, panel);
            DISTANCES.set(j + 1, distance);
        }
    }

    private static Vec3 cameraForward(@Nullable Camera camera) {
        if (camera == null) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        float yaw = camera.yRot() * Mth.DEG_TO_RAD;
        float pitch = camera.xRot() * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitch);
        return new Vec3(-Mth.sin(yaw) * cosPitch, -Mth.sin(pitch),
                Mth.cos(yaw) * cosPitch);
    }

    private static boolean inView(Vec3 center, Vec3 camera, Vec3 forward, double radius) {
        double dx = center.x - camera.x;
        double dy = center.y - camera.y;
        double dz = center.z - camera.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= radius) {
            return true;
        }
        double cos = (dx * forward.x + dy * forward.y + dz * forward.z) / length;
        double angle = Math.acos(Mth.clamp((float) cos, -1.0F, 1.0F));
        return angle - Math.asin(Mth.clamp((float) (radius / length), 0.0F, 1.0F)) < VIEW_CONE;
    }

    private static boolean needsBackdrop(WebPanelInstance panel, Vec3 camera) {
        if (panel.isRemote() || panel.getContentAlpha() < 0.999f) {
            return true;
        }
        Vec3 toCamera = camera.subtract(panel.getCenter());
        double distance = toCamera.length();
        if (distance < 1.0E-4) {
            return true;
        }
        double side = toCamera.dot(panel.getNormal()) / distance;
        double radius = panel.bendRadius();
        double slack = radius <= 0.0 ? 0.02
                : Math.sin(Math.min(Math.PI * 0.45, panel.getWidth() / (2.0 * radius)));
        return side <= slack;
    }

    private static float blurSigma() {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int height = Math.max(1, target != null ? target.height : 1);
        return Mth.clamp(height * BLUR_SIGMA_RATIO / WebPanelBlur.DOWNSCALE, 1.0f, 40.0f);
    }

    private static void drawGlass(Vec3 camera, WebPanelInstance panel,
                                   @Nullable GpuTextureView blur) {
        Identifier frameId = panel.isRemote()
                ? WebPanelStream.frameOf(panel.getOwnerKey(), panel.getRemoteIndex()) : null;
        MCEFBrowser browser = panel.isRemote() ? null : panel.getBrowser();
        GpuTextureView page = browser != null ? browser.getTextureView() : null;

        GpuTextureView backdrop = blur != null ? blur : page;
        if (backdrop == null) {
            drawFlatTint(camera, panel);
            return;
        }
        GpuTextureView content = page != null ? page : backdrop;
        GpuTextureView second = content;
        if (frameId != null) {
            AbstractTexture frameTexture = Minecraft.getInstance().getTextureManager().getTexture(frameId);
            if (frameTexture != null && frameTexture.getTextureView() != null) {
                second = frameTexture.getTextureView();
            }
        }

        float fade = panel.getRenderAlpha();
        float contentAlpha = page != null ? panel.getContentAlpha() : 0.0f;
        if (frameId != null) {
            contentAlpha = 1.0f;
        }
        final float pageAlpha = contentAlpha;

        float effectiveTint = blur != null ? TINT_ALPHA : 1.0f;

        float sdfW = (float) (SDF_HEIGHT * panel.getWidth() / panel.getHeight());
        float radius = WebPanelInstance.CORNER_RADIUS_SDF;

        BufferBuilder builder = WebPanelPipelines.builder(
                bendSegments(panel) * 6, DefaultVertexFormat.POSITION_TEX_COLOR);
        emitGlass(builder, camera, panel);

        MeshData mesh;
        try {
            mesh = builder.buildOrThrow();
        } catch (Throwable t) {
            return;
        }

        GpuBuffer ubo = WebPanelPipelines.createUniforms("ShizaVisuals WebPanel Glass UBO",
                WebPanelPipelines.GLASS_UBO_SIZE);
        try {
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            float screenW = Math.max(1, target != null ? target.width : 1);
            float screenH = Math.max(1, target != null ? target.height : 1);
            WebPanelPipelines.writeUniforms(ubo, WebPanelPipelines.GLASS_UBO_SIZE, std -> std
                    .putVec2(sdfW, SDF_HEIGHT)
                    .putFloat(0.5f)
                    .putFloat(pageAlpha)
                    .putVec4(radius, radius, radius, radius)
                    .putVec2(screenW, screenH)
                    .putFloat(fade)
                    .putFloat(effectiveTint)
                    .putVec4(0.0f, 0.0f, 0.0f, 1.0f));
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(
                    com.mojang.blaze3d.textures.FilterMode.LINEAR);
            GpuTextureView sampler1 = second;
            WebPanelPipelines.drawWorld("ShizaVisuals WebPanel Glass", WebPanelPipelines.GLASS, mesh,
                    pass -> {
                        pass.setUniform("WebPanelGlass", ubo.slice());
                        pass.bindTexture("Sampler0", backdrop, sampler);
                        pass.bindTexture("Sampler1", sampler1, sampler);
                    });
        } finally {
            ubo.close();
        }
    }

    private static void drawFlatTint(Vec3 camera, WebPanelInstance panel) {
        float sdfW = (float) (SDF_HEIGHT * panel.getWidth() / panel.getHeight());
        float radius = WebPanelInstance.CORNER_RADIUS_SDF;
        float alpha = TINT_ALPHA * panel.getRenderAlpha();
        int rgb = ((int) (alpha * 255.0f) & 0xFF) << 24;

        double half = panel.getWidth() * 0.5;
        double top = panel.getHeight();

        BufferBuilder builder = WebPanelPipelines.builder(6, DefaultVertexFormat.POSITION_TEX_COLOR);
        appendQuad(builder, camera, panel,
                -half, top, 0.0f, 1.0f,
                -half, 0.0, 0.0f, 0.0f,
                half, 0.0, 1.0f, 0.0f,
                half, top, 1.0f, 1.0f,
                rgb);

        MeshData mesh;
        try {
            mesh = builder.buildOrThrow();
        } catch (Throwable t) {
            return;
        }

        GpuBuffer ubo = WebPanelPipelines.createUniforms("ShizaVisuals WebPanel Sdf UBO",
                WebPanelPipelines.SDF_UBO_SIZE);
        try {
            writeSdfUbo(ubo, sdfW, SDF_HEIGHT, radius, radius, radius, radius, 0.5f);
            WebPanelPipelines.drawWorld("ShizaVisuals WebPanel Flat", WebPanelPipelines.SDF_DEPTH,
                    mesh, pass -> pass.setUniform("WebPanelSdf", ubo.slice()));
        } finally {
            ubo.close();
        }
    }

    private static void writeSdfUbo(GpuBuffer ubo, float width, float height,
                                     float tl, float tr, float br, float bl, float smoothness) {
        WebPanelPipelines.writeUniforms(ubo, WebPanelPipelines.SDF_UBO_SIZE, std -> std
                .putVec2(width, height)
                .putFloat(smoothness)
                .putVec4(tl, tr, br, bl));
    }

    private static void drawChrome(Vec3 camera, WebPanelInstance panel) {
        float fade = panel.getRenderAlpha();
        if (fade <= 0.01f) {
            return;
        }
        if (panel.isRemote()) {
            drawRemoteBadge(camera, panel, fade);
            return;
        }
        float close = panel.getHoverClose();

        float chrome = panel.getChromeEased();
        if (chrome <= 0.004f) {
            drawCornerArcs(camera, panel, fade);
            return;
        }

        float barHover = panel.getHoverBar();

        double barW = panel.barWidth() * (1.0 + BAR_GROW_LENGTH * barHover)
                * (0.72 + 0.28 * chrome);
        double barH = panel.barHeight() * (1.0 + BAR_GROW_THICKNESS * barHover);
        double barY = panel.barCenterV();

        float barAlpha = Mth.lerp(barHover, BAR_ALPHA_IDLE, BAR_ALPHA_HOVER);
        drawPlaneCapsule(camera, panel,
                -barW * 0.5 + barH * 0.5, barY, barW * 0.5 - barH * 0.5, barY, barH,
                white(barAlpha * fade * chrome));

        float reveal = Math.max(barHover, close);
        float dotAlpha = (0.78f + 0.20f * close) * reveal * fade * chrome;
        if (dotAlpha > 0.004f) {
            double cu = panel.closeCenterU();
            double radius = panel.closeRadius();
            drawPlaneCapsule(camera, panel, cu, barY, cu, barY, radius * 2.0,
                    white(dotAlpha));

            float glyph = close * close;
            if (glyph > 0.01f) {
                double arm = radius * 0.50;
                double thickness = radius * 0.24;
                int ink = ColorUtil.withAlpha(CLOSE_GLYPH, (int) (glyph * fade * 235.0f));
                drawPlaneCapsule(camera, panel,
                        cu - arm, barY - arm, cu + arm, barY + arm, thickness, ink);
                drawPlaneCapsule(camera, panel,
                        cu - arm, barY + arm, cu + arm, barY - arm, thickness, ink);
            }
        }

        drawCornerArcs(camera, panel, fade);
    }

    private static void drawRemoteBadge(Vec3 camera, WebPanelInstance panel, float fade) {
        String owner = panel.getOwnerName();
        if (owner.isEmpty()) {
            return;
        }
        double em = Math.max(0.030, panel.barHeight() * 2.6);
        WebPanelText.draw(camera, panel,
                owner, 0.0, panel.barCenterV() - em * 0.2, em, WebPanelText.Align.CENTER,
                ColorUtil.withAlpha(ColorUtil.WHITE, (int) (200.0f * fade)));
    }

    private static void drawCornerArcs(Vec3 camera, WebPanelInstance panel, float fade) {
        double radius = panel.cornerRadius();
        if (radius <= 1.0E-4) {
            return;
        }
        double thickness = panel.cornerThickness();
        for (int i = 0; i < 4; i++) {
            float amount = panel.getHoverCorner(i);
            if (amount <= 0.01f) {
                continue;
            }
            drawCornerArc(camera, panel, i, radius, thickness,
                    white(0.95f * amount * fade), amount);
        }
    }

    private static void drawCornerArc(Vec3 camera, WebPanelInstance panel,
                                       int index, double radius, double thickness,
                                       int color, float amount) {
        double[] center = panel.cornerCenter(index);
        double arcRadius = radius + thickness * 0.85;

        double sweep = Math.toRadians(100.0) * (0.40 + 0.60 * amount);
        double middle = switch (index) {
            case 0 -> Math.toRadians(225.0);
            case 1 -> Math.toRadians(315.0);
            case 2 -> Math.toRadians(135.0);
            default -> Math.toRadians(45.0);
        };
        drawArc(camera, panel, center[0], center[1], arcRadius,
                middle - sweep * 0.5, sweep, 10, thickness, color);
    }

    private static void drawArc(Vec3 camera, WebPanelInstance panel,
                                 double centerU, double centerV, double radius, double start,
                                 double sweep, int segments, double thickness, int color) {
        if (thickness <= 0.0 || (color >>> 24) == 0 || segments <= 0) {
            return;
        }
        double chord = 2.0 * radius * Math.abs(Math.sin(sweep / (2.0 * segments)));

        BufferBuilder builder = WebPanelPipelines.builder(segments * 6,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        double previousU = centerU + Math.cos(start) * radius;
        double previousV = centerV + Math.sin(start) * radius;
        for (int i = 1; i <= segments; i++) {
            double angle = start + sweep * i / segments;
            double u = centerU + Math.cos(angle) * radius;
            double v = centerV + Math.sin(angle) * radius;
            appendCapsule(builder, camera, panel, previousU, previousV, u, v, thickness, color);
            previousU = u;
            previousV = v;
        }

        MeshData mesh;
        try {
            mesh = builder.buildOrThrow();
        } catch (Throwable t) {
            return;
        }
        drawCapsuleBatch(mesh, chord, thickness);
    }

    private static int white(float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        return (a & 0xFF) << 24 | 0xFFFFFF;
    }

    private static void drawPlaneCapsule(Vec3 camera, WebPanelInstance panel,
                                          double x0, double y0, double x1, double y1,
                                          double thickness, int color) {
        if (thickness <= 0.0 || (color >>> 24) == 0) {
            return;
        }
        BufferBuilder builder = WebPanelPipelines.builder(6, DefaultVertexFormat.POSITION_TEX_COLOR);
        appendCapsule(builder, camera, panel, x0, y0, x1, y1, thickness, color);
        MeshData mesh;
        try {
            mesh = builder.buildOrThrow();
        } catch (Throwable t) {
            return;
        }
        drawCapsuleBatch(mesh, Math.hypot(x1 - x0, y1 - y0), thickness);
    }

    private static void appendCapsule(BufferBuilder builder, Vec3 camera,
                                       WebPanelInstance panel, double x0, double y0,
                                       double x1, double y1, double thickness, int rgb) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        double ux;
        double uy;
        if (length < 1.0E-9) {
            ux = 1.0;
            uy = 0.0;
        } else {
            ux = dx / length;
            uy = dy / length;
        }
        double half = thickness * 0.5;

        double sx = x0 - ux * half;
        double sy = y0 - uy * half;
        double ex = x1 + ux * half;
        double ey = y1 + uy * half;
        double nx = -uy * half;
        double ny = ux * half;

        appendQuad(builder, camera, panel,
                sx + nx, sy + ny, 0.0f, 1.0f,
                sx - nx, sy - ny, 0.0f, 0.0f,
                ex - nx, ey - ny, 1.0f, 0.0f,
                ex + nx, ey + ny, 1.0f, 1.0f,
                rgb);
    }

    private static void drawCapsuleBatch(MeshData mesh, double length, double thickness) {
        float sdfH = 32.0f;
        float sdfW = (float) (sdfH * (length + thickness) / thickness);
        float radius = sdfH * 0.5f;
        GpuBuffer ubo = WebPanelPipelines.createUniforms("ShizaVisuals WebPanel Sdf Overlay UBO",
                WebPanelPipelines.SDF_UBO_SIZE);
        try {
            writeSdfUbo(ubo, sdfW, sdfH, radius, radius, radius, radius, 0.5f);
            WebPanelPipelines.drawWorld("ShizaVisuals WebPanel Chrome",
                    WebPanelPipelines.SDF_OVERLAY, mesh,
                    pass -> pass.setUniform("WebPanelSdf", ubo.slice()));
        } finally {
            ubo.close();
        }
    }

    public static void surfacePoint(Vector3f out, Vec3 camera, WebPanelInstance panel,
                                     double u, double v, boolean offset) {
        float scale = panel.getRenderScale();
        double halfH = panel.getHeight() * 0.5;
        double sv = halfH + (v - halfH) * scale;

        double radius = panel.bendRadius();
        double su = WebPanelInstance.curveAcross(radius, u) * scale;
        double depth = WebPanelInstance.curveDepth(radius, u) * scale;

        Vec3 right = panel.getRight();
        Vec3 up = panel.getUp();
        Vec3 origin = panel.getOrigin();
        Vec3 normal = panel.getNormal();

        if (offset) {
            double side = camera.subtract(origin).dot(normal);
            depth += 0.002 * (side < 0.0 ? -1.0 : 1.0);
        }
        out.set((float) (origin.x + right.x * su + up.x * sv + normal.x * depth - camera.x),
                (float) (origin.y + right.y * su + up.y * sv + normal.y * depth - camera.y),
                (float) (origin.z + right.z * su + up.z * sv + normal.z * depth - camera.z));
    }

    public static Vec3 planePoint(Vec3 camera, WebPanelInstance panel, double u, double v,
                                   boolean offset) {
        Vector3f point = new Vector3f();
        surfacePoint(point, camera, panel, u, v, offset);
        return new Vec3(point.x, point.y, point.z);
    }

    private static void toView(Vector3f relative) {
        SCRATCH_VIEW.set(relative.x, relative.y, relative.z, 1.0f);
        WebPanelView.rotation().transform(SCRATCH_VIEW);
        relative.set(SCRATCH_VIEW.x, SCRATCH_VIEW.y, SCRATCH_VIEW.z);
    }

    private static void appendQuad(BufferBuilder builder, Vec3 camera,
                                    WebPanelInstance panel,
                                    double u0, double v0, float fu0, float fv0,
                                    double u1, double v1, float fu1, float fv1,
                                    double u2, double v2, float fu2, float fv2,
                                    double u3, double v3, float fu3, float fv3,
                                    int rgb) {
        appendPlaneVertex(builder, camera, panel, u0, v0, fu0, fv0, rgb);
        appendPlaneVertex(builder, camera, panel, u1, v1, fu1, fv1, rgb);
        appendPlaneVertex(builder, camera, panel, u2, v2, fu2, fv2, rgb);
        appendPlaneVertex(builder, camera, panel, u0, v0, fu0, fv0, rgb);
        appendPlaneVertex(builder, camera, panel, u2, v2, fu2, fv2, rgb);
        appendPlaneVertex(builder, camera, panel, u3, v3, fu3, fv3, rgb);
    }

    private static void appendPlaneVertex(BufferBuilder builder, Vec3 camera,
                                           WebPanelInstance panel, double u, double v,
                                           float fragU, float fragV, int rgb) {
        Vector3f point = new Vector3f();
        surfacePoint(point, camera, panel, u, v, true);
        toView(point);
        builder.addVertex(point.x, point.y, point.z).setUv(fragU, fragV).setColor(rgb);
    }

    private static void appendGlassVertex(BufferBuilder builder, Vec3 camera,
                                           WebPanelInstance panel, double u, double v,
                                           float texU, float texV) {
        Vector3f point = new Vector3f();
        surfacePoint(point, camera, panel, u, v, false);
        toView(point);
        builder.addVertex(point.x, point.y, point.z).setUv(texU, texV).setColor(-1);
    }

    private static int bendSegments(WebPanelInstance panel) {
        double radius = panel.bendRadius();
        if (radius <= 0.0) {
            return 1;
        }
        double degrees = Math.toDegrees(panel.getWidth() / radius);
        return Mth.clamp((int) Math.ceil(degrees / BEND_STEP_DEGREES), 1, 24);
    }

    private static void emitGlass(BufferBuilder builder, Vec3 camera,
                                   WebPanelInstance panel) {
        int segments = bendSegments(panel);
        double width = panel.getWidth();
        double half = width * 0.5;
        double top = panel.getHeight();
        for (int i = 0; i < segments; i++) {
            float s0 = (float) i / segments;
            float s1 = (float) (i + 1) / segments;
            double u0 = -half + width * s0;
            double u1 = -half + width * s1;
            appendGlassVertex(builder, camera, panel, u0, top, s0, 0.0f);
            appendGlassVertex(builder, camera, panel, u0, 0.0, s0, 1.0f);
            appendGlassVertex(builder, camera, panel, u1, 0.0, s1, 1.0f);
            appendGlassVertex(builder, camera, panel, u0, top, s0, 0.0f);
            appendGlassVertex(builder, camera, panel, u1, 0.0, s1, 1.0f);
            appendGlassVertex(builder, camera, panel, u1, top, s1, 0.0f);
        }
    }
}
