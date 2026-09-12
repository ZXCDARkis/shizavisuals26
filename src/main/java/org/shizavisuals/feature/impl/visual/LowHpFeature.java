package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.lifecycle.WorldLeaveEvent;
import org.shizavisuals.event.events.render.Render2DEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.visual.lowhp.LowHpLayout;
import org.shizavisuals.feature.impl.visual.lowhp.LowHpMotion;
import org.shizavisuals.feature.impl.visual.lowhp.LowHpValueMotion;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ColorSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.ScaleUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

/**
 * Compact low-health warning: pulsing heart + rolling HP digits at fixed
 * crosshair/hotbar presets, with a heartbeat thump synced to the pulse.
 */
public final class LowHpFeature extends Feature {
    private static final float HEART_ASPECT = 6.25F / 7.25F;
    private static final float HEART_SLOT_PADDING = 0.5F;
    private static final float HEART_TEXT_GAP = 1.5F;
    private static final float TEXT_SIZE = 8.0F;

    public final NumberSetting threshold = register(new NumberSetting(
            "Threshold",
            4.0D,
            1.0D,
            20.0D,
            1.0D,
            " HP"
    ).configKey("render.lowhp.threshold"));
    public final ModeSetting position = register(new ModeSetting(
            "Position",
            LowHpLayout.ABOVE_HOTBAR,
            LowHpLayout.positionNames()
    ).configKey("render.lowhp.position"));
    public final BooleanSetting showHeart = register(new BooleanSetting(
            "Heart",
            true
    ).configKey("render.lowhp.showHeart"));
    public final NumberSetting heartSize = register(new NumberSetting(
            "Heart Size",
            25.0D,
            4.0D,
            32.0D,
            0.5D,
            "px"
    ).configKey("render.lowhp.heartSize").visibleWhen(() -> this.showHeart.getValue()));
    public final BooleanSetting sound = register(new BooleanSetting(
            "Heartbeat Sound",
            true
    ).configKey("render.lowhp.sound"));
    public final NumberSetting volume = register(new NumberSetting(
            "Volume",
            50.0D,
            0.0D,
            100.0D,
            1.0D,
            "%"
    ).configKey("render.lowhp.volume").visibleWhen(() -> this.sound.getValue()));
    public final ColorSetting color = register(new ColorSetting(
            "Color",
            0xFFFF4D57
    ).configKey("render.lowhp.color"));

    private final LowHpMotion motion = new LowHpMotion();
    private final LowHpValueMotion valueMotion = new LowHpValueMotion();
    private long lastFrameNanos;

    public LowHpFeature() {
        super("LowHP", "Предупреждение о низком здоровье с пульсом и звуком", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static LowHpFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(LowHpFeature.class);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Minecraft client = event.getClient();
        LocalPlayer player = client.player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            return;
        }

        float health = player.getHealth();
        boolean low = LowHpLayout.isLowHealth(health, this.threshold.getValue().floatValue());
        float delta = frameDelta();

        LowHpMotion.Frame motionFrame = this.motion.update(low, delta);
        LowHpValueMotion.Frame valueFrame = this.valueMotion.update(health, delta);

        if (this.motion.consumeBeat() && low && this.sound.getValue()) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.WARDEN_HEARTBEAT, 1.0F, heartbeatVolume()));
        }

        float alpha = motionFrame.alpha();
        if (alpha <= 0.001F) {
            return;
        }

        float unit = ScaleUtil.toGuiPixels(1.0F, client.getWindow().getGuiScale());
        MsdfFont font = UiFonts.sfProDisplay();
        float textSize = TEXT_SIZE * unit;
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();

        float textWidth = Math.max(
                font.measureWidth(valueFrame.previousText(), textSize, letterSpacing),
                font.measureWidth(valueFrame.currentText(), textSize, letterSpacing));

        float heartWidth = this.showHeart.getValue()
                ? Math.clamp(this.heartSize.getValue().floatValue(), 4.0F, 32.0F) * unit
                : 0.0F;
        float heartHeight = heartWidth * HEART_ASPECT;
        float contentHeight = Math.max(TEXT_SIZE * unit + 4.0F * unit, heartHeight * 1.10F + 1.0F * unit);
        float contentWidth = textWidth;
        if (this.showHeart.getValue()) {
            contentWidth += heartWidth + (HEART_SLOT_PADDING + HEART_TEXT_GAP) * unit;
        }
        contentWidth = Math.max(4.0F * unit, contentWidth);

        float guiWidth = event.getGuiGraphicsExtractor().guiWidth();
        float guiHeight = event.getGuiGraphicsExtractor().guiHeight();
        LowHpLayout.Point point = LowHpLayout.resolve(
                this.position.getValue(), guiWidth, guiHeight, contentWidth, contentHeight);

        float centerX = point.x() + contentWidth * 0.5F;
        float centerY = point.y() + contentHeight * 0.5F;

        var pose = event.getGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY + motionFrame.verticalOffset() * unit);
        pose.scale(motionFrame.contentScale(), motionFrame.contentScale());
        pose.translate(-centerX, -centerY);
        try {
            drawWarning(font, point.x(), point.y(), contentHeight, textWidth, textSize,
                    heartWidth, heartHeight, valueFrame, alpha, motionFrame.heartScale(), unit);
        } finally {
            pose.popMatrix();
        }
    }

    @EventTarget
    public void onWorldLeave(WorldLeaveEvent event) {
        resetMotion();
    }

    @Override
    protected void onEnable() {
        resetMotion();
    }

    @Override
    protected void onDisable() {
        resetMotion();
    }

    private void drawWarning(MsdfFont font, float x, float y, float contentHeight, float textWidth,
                             float textSize, float heartWidth, float heartHeight, LowHpValueMotion.Frame valueFrame,
                             float alpha, float heartScale, float unit) {
        float cursor = x;
        int baseColor = this.color.getValue();
        float baseAlpha = ColorUtil.alpha(baseColor) / 255.0F;

        if (this.showHeart.getValue()) {
            float slotWidth = heartWidth + HEART_SLOT_PADDING * unit;
            float animatedWidth = heartWidth * heartScale;
            float animatedHeight = heartHeight * heartScale;
            float heartX = cursor + slotWidth * 0.5F - animatedWidth * 0.5F;
            float heartY = y + contentHeight * 0.5F - animatedHeight * 0.5F;
            Render2DUtil.texture(heartX, heartY, animatedWidth, animatedHeight, Textures.Icons.SCAN_HEART)
                    .color(ColorUtil.multiplyAlpha(baseColor, baseAlpha * alpha))
                    .draw();
            cursor += slotWidth + HEART_TEXT_GAP * unit;
        }

        drawHealthLayer(font, valueFrame.previousText(), cursor, y, contentHeight, textWidth, textSize,
                valueFrame.previousOffsetY() * unit, valueFrame.previousAlpha(), alpha, baseColor, baseAlpha);
        drawHealthLayer(font, valueFrame.currentText(), cursor, y, contentHeight, textWidth, textSize,
                valueFrame.currentOffsetY() * unit, valueFrame.currentAlpha(), alpha, baseColor, baseAlpha);
    }

    private void drawHealthLayer(MsdfFont font, String text, float slotX, float y, float contentHeight,
                                 float textWidth, float textSize, float offsetY, float layerAlpha,
                                 float alpha, int baseColor, float baseAlpha) {
        float combined = alpha * Math.clamp(layerAlpha, 0.0F, 1.0F);
        if (combined <= 0.001F || text.isEmpty()) {
            return;
        }
        float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();
        float width = font.measureWidth(text, textSize, letterSpacing);
        float textX = slotX + (textWidth - width) * 0.5F;
        float centerY = y + contentHeight * 0.5F + offsetY;
        Render2DUtil.text(textX, font.centeredTextY(centerY, textSize), textSize, text)
                .style(UiFontStyle.MEDIUM)
                .color(ColorUtil.multiplyAlpha(baseColor, baseAlpha * combined))
                .draw();
    }

    private float heartbeatVolume() {
        float linear = Math.clamp(this.volume.getValue().floatValue() / 100.0F, 0.0F, 1.0F);
        return linear * linear * linear;
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float delta = this.lastFrameNanos == 0L
                ? 1.0F / 60.0F
                : (now - this.lastFrameNanos) / 1_000_000_000.0F;
        this.lastFrameNanos = now;
        return Math.clamp(delta, 0.001F, 0.05F);
    }

    private void resetMotion() {
        this.motion.reset();
        this.valueMotion.reset();
        this.lastFrameNanos = 0L;
    }
}
