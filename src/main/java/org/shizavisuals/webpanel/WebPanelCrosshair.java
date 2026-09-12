package org.shizavisuals.webpanel;

import net.minecraft.client.Minecraft;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.gui.Render2DUtil;

public final class WebPanelCrosshair {
    private WebPanelCrosshair() {
    }

    public static final float MORPH_TIME = 0.13f;

    private static final float ARM = 5.0f;
    private static final float RING = 3.5f;
    private static final float THICKNESS = 1.5f;
    private static final int SEGMENTS = 8;
    private static final int OUTLINE_COLOR = ColorUtil.rgba(0, 0, 0, 150);
    private static final int CORE_COLOR = ColorUtil.rgba(255, 255, 255, 235);

    public static void draw(float progress) {
        var extractor = RenderContext.currentGuiGraphicsExtractor();
        if (extractor == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double scale = Math.max(1.0, mc.getWindow().getGuiScale());

        float spread = ease(MathUtil.clamp(progress / 0.60f, 0.0f, 1.0f));
        float curl = ease(MathUtil.clamp((progress - 0.40f) / 0.60f, 0.0f, 1.0f));

        float thickness = THICKNESS / (float) scale;
        float armOuter = ARM / (float) scale;
        float ringRadius = RING / (float) scale;
        float rc = MathUtil.lerp(spread, armOuter * 0.75f, ringRadius);
        float radial = MathUtil.lerp(spread, armOuter * 0.25f, 0.35f / (float) scale)
                * (1.0f - curl);
        float halfAngle = (float) (Math.PI / 4.0) * curl;

        float cx = extractor.guiWidth() * 0.5f;
        float cy = extractor.guiHeight() * 0.5f;

        for (int arm = 0; arm < 4; arm++) {
            double base = arm * (Math.PI / 2.0);
            double previousAngle = base - halfAngle;
            float previousR = rc - radial;
            for (int k = -SEGMENTS / 2 + 1; k <= SEGMENTS / 2; k++) {
                float s = 2.0f * k / SEGMENTS;
                double angle = base + halfAngle * s;
                float r = rc + radial * s;
                drawCapsule(
                        cx + (float) (Math.cos(previousAngle) * previousR),
                        cy + (float) (Math.sin(previousAngle) * previousR),
                        cx + (float) (Math.cos(angle) * r),
                        cy + (float) (Math.sin(angle) * r),
                        thickness
                );
                previousAngle = angle;
                previousR = r;
            }
        }

        float plug = (THICKNESS * 0.6f / (float) scale) * (1.0f - spread);
        if (plug > 0.02f) {
            drawRect(cx - plug, cy - plug, plug * 2.0f, plug * 2.0f, CORE_COLOR, thickness * 0.6f);
        }
    }

    private static void drawCapsule(float x0, float y0, float x1, float y1, float thickness) {
        float length = (float) Math.hypot(x1 - x0, y1 - y0);
        if (length < 1.0E-4f) {
            return;
        }
        var pose = RenderContext.currentGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.rotate((float) Math.atan2(y1 - y0, x1 - x0));
        drawRect(-thickness * 0.25f, -thickness * 0.5f,
                length + thickness * 0.5f, thickness, OUTLINE_COLOR, thickness * 0.5f + 0.5f);
        drawRect(0.0f, -thickness * 0.5f,
                length + thickness * 0.5f, thickness, CORE_COLOR, thickness * 0.5f);
        pose.popMatrix();
    }

    private static void drawRect(float x, float y, float width, float height,
                                  int color, float radius) {
        Render2DUtil.rect(x, y, width, height)
                .color(color)
                .radius(radius)
                .draw();
    }

    private static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
}
