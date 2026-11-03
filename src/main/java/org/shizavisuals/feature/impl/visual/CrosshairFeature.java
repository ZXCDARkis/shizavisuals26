package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.render.Render2DEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorMode;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.ScaleUtil;

public final class CrosshairFeature extends Feature {
    public static final String DEFAULT = "Default";
    public static final String CIRCLE = "Circle";

    private static final int OUTLINE_COLOR = ColorUtil.rgba(0, 0, 0, 215);

    public final ModeSetting type = register(new ModeSetting(
            "Type",
            DEFAULT,
            DEFAULT,
            CIRCLE
    ).configKey("render.crosshair.type"));
    public final ModeSetting colorMode = register(ColorMode.setting()
            .configKey("render.crosshair.colorMode"));
    public final ColorSetting color = register(new ColorSetting(
            "Color",
            0xFFFFFFFF
    ).configKey("render.crosshair.color")
            .visibleWhen(() -> ColorMode.isCustom(this.colorMode)));
    public final NumberSetting radius = register(new NumberSetting(
            "Radius",
            12.0D,
            12.0D,
            20.0D,
            1.0D,
            "px"
    ).configKey("render.crosshair.radius").visibleWhen(() -> this.type.is(CIRCLE)));
    public final NumberSetting gap = register(new NumberSetting(
            "Gap",
            2.0D,
            2.0D,
            20.0D,
            1.0D,
            "px"
    ).configKey("render.crosshair.gap").visibleWhen(() -> this.type.is(DEFAULT)));
    public final NumberSetting length = register(new NumberSetting(
            "Length",
            5.0D,
            5.0D,
            20.0D,
            1.0D,
            "px"
    ).configKey("render.crosshair.length").visibleWhen(() -> this.type.is(DEFAULT)));
    public final NumberSetting thickness = register(new NumberSetting(
            "Thickness",
            2.0D,
            2.0D,
            5.0D,
            0.5D,
            "px"
    ).configKey("render.crosshair.thickness"));
    public final BooleanSetting cooldown = register(new BooleanSetting(
            "Cooldown Animation",
            true
    ).configKey("render.crosshair.cooldown"));
    public final NumberSetting cooldownMultiplier = register(new NumberSetting(
            "Cooldown Gap Multiplier",
            8.0D,
            8.0D,
            20.0D,
            1.0D,
            "px"
    ).configKey("render.crosshair.cooldownMultiplier")
            .visibleWhen(() -> this.cooldown.getValue()));
    public final BooleanSetting outline = register(new BooleanSetting(
            "Outline",
            true
    ).configKey("render.crosshair.outline"));
    public final BooleanSetting tMode = register(new BooleanSetting(
            "T-Mode",
            false
    ).configKey("render.crosshair.tmode").visibleWhen(() -> this.type.is(DEFAULT)));
    public final BooleanSetting centerDot = register(new BooleanSetting(
            "Center Dot",
            true
    ).configKey("render.crosshair.centerdot").visibleWhen(() -> this.type.is(DEFAULT)));
    public final NumberSetting centerSize = register(new NumberSetting(
            "Center Size",
            2.0D,
            2.0D,
            10.0D,
            1.0D,
            "px"
    ).configKey("render.crosshair.centersize")
            .visibleWhen(() -> this.type.is(DEFAULT) && this.centerDot.getValue()));

    private float animatedCooldown;
    private long lastFrameNanos;

    public CrosshairFeature() {
        super("Crosshair", "Процедурный настраиваемый прицел", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Minecraft client = event.getClient();
        LocalPlayer player = client.player;
        if (player == null
                || client.options.getCameraType() != CameraType.FIRST_PERSON
                || client.getDebugOverlay().showDebugScreen()) {
            return;
        }

        float centerX = event.getGuiGraphicsExtractor().guiWidth() / 2.0F;
        float centerY = event.getGuiGraphicsExtractor().guiHeight() / 2.0F;
        float unit = ScaleUtil.toGuiPixels(1.0F, client.getWindow().getGuiScale());
        float delta = frameDelta();
        float cooldownTarget = this.cooldown.getValue()
                ? 1.0F - player.getAttackStrengthScale(0.5F)
                : 0.0F;
        this.animatedCooldown += (cooldownTarget - this.animatedCooldown)
                * (1.0F - (float) Math.exp(-16.0F * delta));
        float expansion = this.animatedCooldown
                * this.cooldownMultiplier.getValue().floatValue()
                * unit;
        int resolvedColor = ColorMode.resolve(this.colorMode, this.color);

        if (this.type.is(CIRCLE)) {
            drawCircle(
                    event,
                    centerX,
                    centerY,
                    this.radius.getValue().floatValue() * unit + expansion,
                    this.thickness.getValue().floatValue() * unit,
                    resolvedColor,
                    unit
            );
        } else {
            drawDefault(event, centerX, centerY, expansion, resolvedColor, unit);
        }
    }

    @Override
    protected void onEnable() {
        resetAnimation();
    }

    @Override
    protected void onDisable() {
        resetAnimation();
    }

    private void drawDefault(Render2DEvent event,
                             float centerX,
                             float centerY,
                             float expansion,
                             int color,
                             float unit) {
        float lineGap = this.gap.getValue().floatValue() * unit + expansion;
        float lineLength = this.length.getValue().floatValue() * unit;
        float lineThickness = this.thickness.getValue().floatValue() * unit;
        float halfThickness = lineThickness * 0.5F;

        drawBar(centerX - lineGap - lineLength, centerY - halfThickness, lineLength, lineThickness, color, unit);
        drawBar(centerX + lineGap, centerY - halfThickness, lineLength, lineThickness, color, unit);
        if (!this.tMode.getValue()) {
            drawBar(centerX - halfThickness, centerY - lineGap - lineLength, lineThickness, lineLength, color, unit);
        }
        drawBar(centerX - halfThickness, centerY + lineGap, lineThickness, lineLength, color, unit);

        if (this.centerDot.getValue()) {
            float dotSize = this.centerSize.getValue().floatValue() * unit;
            drawBar(centerX - dotSize * 0.5F, centerY - dotSize * 0.5F, dotSize, dotSize, color, unit);
        }
    }

    private void drawCircle(Render2DEvent event,
                            float centerX,
                            float centerY,
                            float radius,
                            float thickness,
                            int color,
                            float unit) {
        if (this.outline.getValue()) {
            drawRing(event, centerX, centerY, radius, thickness + 2.0F * unit, OUTLINE_COLOR, unit);
        }
        drawRing(event, centerX, centerY, radius, thickness, color, unit);
    }

    private void drawRing(Render2DEvent event,
                          float centerX,
                          float centerY,
                          float radius,
                          float thickness,
                          int color,
                          float unit) {
        int segments = Math.max(40, (int) Math.ceil(radius * 4.0F));
        for (int index = 0; index < segments; index++) {
            double firstAngle = Math.PI * 2.0D * index / segments;
            double secondAngle = Math.PI * 2.0D * (index + 1) / segments;
            float x1 = centerX + (float) Math.cos(firstAngle) * radius;
            float y1 = centerY + (float) Math.sin(firstAngle) * radius;
            float x2 = centerX + (float) Math.cos(secondAngle) * radius;
            float y2 = centerY + (float) Math.sin(secondAngle) * radius;
            drawSegment(event, x1, y1, x2, y2, thickness, color, unit);
        }
    }

    private void drawSegment(Render2DEvent event,
                             float x1,
                             float y1,
                             float x2,
                             float y2,
                             float thickness,
                             int color,
                             float unit) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float segmentLength = (float) Math.sqrt(dx * dx + dy * dy);
        float overlap = Math.max(0.35F * unit, thickness * 0.2F);
        var pose = event.getGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(dy, dx));
        Render2DUtil.rect(-overlap, -thickness * 0.5F, segmentLength + overlap * 2.0F, thickness)
                .color(color)
                .radius(thickness * 0.5F)
                .draw();
        pose.popMatrix();
    }

    private void drawBar(float x, float y, float width, float height, int color, float unit) {
        if (this.outline.getValue()) {
            float border = unit;
            Render2DUtil.rect(x - border, y - border, width + border * 2.0F, height + border * 2.0F)
                    .color(OUTLINE_COLOR)
                    .radius(Math.min(width, height) * 0.35F + border)
                    .draw();
        }
        Render2DUtil.rect(x, y, width, height)
                .color(color)
                .radius(Math.min(width, height) * 0.35F)
                .draw();
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = this.lastFrameNanos == 0L
                ? 1.0F / 60.0F
                : (now - this.lastFrameNanos) / 1_000_000_000.0F;
        this.lastFrameNanos = now;
        return Math.clamp(delta, 0.001F, 0.05F);
    }

    private void resetAnimation() {
        this.animatedCooldown = 0.0F;
        this.lastFrameNanos = 0L;
    }
}
