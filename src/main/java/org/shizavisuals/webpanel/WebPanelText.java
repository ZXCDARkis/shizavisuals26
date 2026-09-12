package org.shizavisuals.webpanel;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.gui.MsdfFont;

public final class WebPanelText {

    private WebPanelText() {
    }

    private static final float SIZE = 10.0f;

    private static final float WEIGHT = 0.008f;

    public enum Align {
        LEFT, CENTER, RIGHT
    }

    public static MsdfFont font(String text) {
        return MsdfFont.load(fontId(text));
    }

    private static Identifier fontId(String text) {
        boolean cyrillic = false;
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= 'Ѐ' && c <= 'ѿ') {
                    cyrillic = true;
                    break;
                }
            }
        }
        return Identifier.parse(cyrillic
                ? "shizavisuals:fonts/google_sans_medium.json"
                : "shizavisuals:fonts/google_sans_flex_medium.json");
    }

    public static double width(MsdfFont font, String text, double emBlocks) {
        return font.measureWidth(text, SIZE) * (emBlocks / SIZE);
    }

    public static String fit(MsdfFont font, String text, double emBlocks, double maxBlocks) {
        if (text == null || text.isEmpty() || maxBlocks <= 0.0) {
            return "";
        }
        if (width(font, text, emBlocks) <= maxBlocks) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && width(font, result + "…", emBlocks) > maxBlocks) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    public static void draw(Vec3 camera, WebPanelInstance panel,
                             String text, double u, double v,
                             double emBlocks, Align align, int color) {
        if (text == null || text.isEmpty() || emBlocks <= 1.0E-6) {
            return;
        }
        MsdfFont font = font(text);
        draw(camera, panel, font, text, u, v, emBlocks, align, color);
    }

    public static void draw(Vec3 camera, WebPanelInstance panel,
                             MsdfFont font, String text, double u, double v,
                             double emBlocks, Align align, int color) {
        if (text == null || text.isEmpty() || emBlocks <= 1.0E-6) {
            return;
        }
        double unit = emBlocks / SIZE;
        double lineWidth = font.measureWidth(text, SIZE) * unit;
        double left = switch (align) {
            case LEFT -> u;
            case CENTER -> u - lineWidth * 0.5;
            case RIGHT -> u - lineWidth;
        };

        Matrix4f local = planeMatrix(camera, panel, left, v + emBlocks * 0.5, unit,
                left + lineWidth * 0.5);

        BufferBuilder builder = WebPanelPipelines.builder(
                text.length() * 6, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        emitGlyphs(builder, local, font, text, SIZE, color);
        MeshData mesh;
        try {
            mesh = builder.buildOrThrow();
        } catch (Throwable t) {
            return;
        }

        GpuTextureView atlasView;
        try {
            atlasView = font.textureSetup().texure0();
        } catch (Throwable t) {
            mesh.close();
            return;
        }
        if (atlasView == null) {
            mesh.close();
            return;
        }

        GpuBuffer ubo = WebPanelPipelines.createUniforms("ShizaVisuals WebPanel Text UBO",
                WebPanelPipelines.TEXT_UBO_SIZE);
        try {
            WebPanelPipelines.writeUniforms(ubo, WebPanelPipelines.TEXT_UBO_SIZE, std -> std
                    .putFloat(font.distanceRange())
                    .putFloat(WEIGHT));
            WebPanelPipelines.drawWorld("ShizaVisuals WebPanel Text", WebPanelPipelines.TEXT, mesh,
                    pass -> {
                        pass.setUniform("WebPanelText", ubo.slice());
                        var sampler = RenderSystem.getSamplerCache()
                                .getClampToEdge(FilterMode.LINEAR);
                        pass.bindTexture("Sampler0", atlasView, sampler);
                    });
        } finally {
            ubo.close();
        }
    }

    private static void emitGlyphs(BufferBuilder builder, Matrix4f local, MsdfFont font,
                                    String text, float size, int color) {
        MsdfFont.Paragraph paragraph = font.shape(text);
        if (paragraph.lines().isEmpty()) {
            return;
        }
        MsdfFont.Line line = paragraph.lines().get(0);
        float baselineY = SIZE * 0.7f;
        float penX = -0.75f;
        for (int i = 0; i < line.glyphCount(); i++) {
            MsdfFont.Glyph glyph = line.glyphAt(i);
            MsdfFont.Bounds plane = glyph.planeBounds();
            if (plane == null) {
                continue;
            }
            float x = penX + line.penX(i, size, 0.0F);
            float width = (plane.right() - plane.left()) * size;
            float height = (plane.top() - plane.bottom()) * size;
            float y = baselineY - (float) plane.top() * size;

            builder.addVertex(local, x, y, 0.0F).setUv(glyph.u0(), glyph.v0()).setColor(color);
            builder.addVertex(local, x, y + height, 0.0F).setUv(glyph.u0(), glyph.v1()).setColor(color);
            builder.addVertex(local, x + width, y + height, 0.0F).setUv(glyph.u1(), glyph.v1()).setColor(color);
            builder.addVertex(local, x, y, 0.0F).setUv(glyph.u0(), glyph.v0()).setColor(color);
            builder.addVertex(local, x + width, y + height, 0.0F).setUv(glyph.u1(), glyph.v1()).setColor(color);
            builder.addVertex(local, x + width, y, 0.0F).setUv(glyph.u1(), glyph.v0()).setColor(color);
        }
    }

    private static Matrix4f planeMatrix(Vec3 camera, WebPanelInstance panel,
                                         double u, double v, double unit, double tangentAt) {
        float scale = panel.getRenderScale();
        double step = unit * scale;

        Vec3 right = panel.getRight();
        Vec3 up = panel.getUp();
        Vec3 normal = panel.getNormal();

        double radius = panel.bendRadius();
        if (radius > 0.0) {
            double angle = tangentAt / radius;
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            Vec3 tangent = right.scale(cos).add(normal.scale(sin));
            normal = normal.scale(cos).subtract(right.scale(sin));
            right = tangent;
        }

        Vec3 anchorRelative = WebPanelRenderer.planePoint(camera, panel, u, v, true);
        Vector4f anchorView = Render3DUtil.toViewSpace(
                new Vec3(anchorRelative.x + camera.x,
                        anchorRelative.y + camera.y,
                        anchorRelative.z + camera.z),
                camera, WebPanelView.rotation());

        Vector3f viewRight = new Vector3f((float) right.x, (float) right.y, (float) right.z);
        Vector3f viewUp = new Vector3f((float) up.x, (float) up.y, (float) up.z);
        Vector3f viewNormal = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
        WebPanelView.rotation().transformDirection(viewRight);
        WebPanelView.rotation().transformDirection(viewUp);
        WebPanelView.rotation().transformDirection(viewNormal);

        return new Matrix4f()
                .m00(viewRight.x * (float) step)
                .m01(viewRight.y * (float) step)
                .m02(viewRight.z * (float) step)
                .m10(-viewUp.x * (float) step)
                .m11(-viewUp.y * (float) step)
                .m12(-viewUp.z * (float) step)
                .m20(viewNormal.x * (float) step)
                .m21(viewNormal.y * (float) step)
                .m22(viewNormal.z * (float) step)
                .m30(anchorView.x())
                .m31(anchorView.y())
                .m32(anchorView.z());
    }
}
