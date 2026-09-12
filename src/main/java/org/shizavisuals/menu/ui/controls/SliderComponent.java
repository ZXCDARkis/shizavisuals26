package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.AnimatedValue;
import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.util.function.Supplier;

public final class SliderComponent extends Component {
    private static final long ANIMATION_MS = 140L;

    private final Supplier<Float> valueSupplier;
    private final ValueConsumer valueConsumer;
    private final AnimatedValue animation = new AnimatedValue(ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    private int trackColor;
    private int fillColor;
    private int knobColor;
    private float alpha = 1.0F;
    private boolean enabled = true;
    private boolean dragging;
    private float step;

    private double anchorValue;
    private double anchorMouseX;
    private Runnable releaseAction = () -> {
    };

    public SliderComponent(Supplier<Float> valueSupplier, ValueConsumer valueConsumer, int trackColor, int fillColor, int knobColor) {
        this.valueSupplier = valueSupplier;
        this.valueConsumer = valueConsumer;
        this.trackColor = trackColor;
        this.fillColor = fillColor;
        this.knobColor = knobColor;
    }

    public SliderComponent place(Component owner, int x, int y, int width, int height) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        return this;
    }

    public SliderComponent style(int trackColor, int fillColor, int knobColor) {
        this.trackColor = trackColor;
        this.fillColor = fillColor;
        this.knobColor = knobColor;
        return this;
    }

    public SliderComponent alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public SliderComponent enabled(boolean enabled) {
        if (!enabled && this.dragging) {
            this.dragging = false;
            this.releaseAction.run();
        }
        this.enabled = enabled;
        return this;
    }

    public SliderComponent onRelease(Runnable releaseAction) {
        this.releaseAction = releaseAction == null ? () -> {
        } : releaseAction;
        return this;
    }

    public SliderComponent step(float step) {
        this.step = Math.max(0.0F, step);
        return this;
    }

    public boolean handleClick(int mouseX, int mouseY) {
        if (!this.enabled || !contains(mouseX, mouseY)) {
            return false;
        }
        this.dragging = true;

        double mouse = preciseMouseX(mouseX);
        double current = MathUtil.clamp01(this.valueSupplier.get());
        double knobCenter = innerX() + current * innerWidth();
        double knobRadius = height() / 2.0D + 1.0D;
        if (Math.abs(mouse - knobCenter) > knobRadius) {
            updateValue(mouseX);
            current = MathUtil.clamp01(this.valueSupplier.get());
        }
        this.anchorValue = snap(current);
        this.anchorMouseX = mouse;
        return true;
    }

    public boolean drag(int mouseX) {
        if (!this.enabled || !this.dragging) {
            return false;
        }
        double mouse = preciseMouseX(mouseX);
        double value = MathUtil.clamp01((float) (this.anchorValue + (mouse - this.anchorMouseX) / innerWidth()));
        this.valueConsumer.accept((float) snap(value));
        return true;
    }

    public boolean releasePointer() {
        if (!this.dragging) {
            return false;
        }
        this.dragging = false;
        this.releaseAction.run();
        return true;
    }

    public boolean isDragging() {
        return this.dragging;
    }

    public boolean handleScroll(int mouseX, int mouseY, double vertical) {
        if (this.step <= 0.0F || !contains(mouseX, mouseY)) {
            return false;
        }
        float current = Math.round(MathUtil.clamp01(this.valueSupplier.get()) / this.step) * this.step;
        float next = MathUtil.clamp01(current + (vertical > 0.0 ? this.step : -this.step));
        this.valueConsumer.accept(Math.round(next / this.step) * this.step);
        return true;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        float value = this.animation.toward(MathUtil.clamp01(this.valueSupplier.get()));
        float effectiveAlpha = this.alpha * (this.enabled ? 1.0F : 0.32F);
        int effectiveFillColor = this.enabled ? this.fillColor : Theme.Colors.ICON_GHOST;
        int effectiveKnobColor = this.enabled ? this.knobColor : Theme.Colors.ICON_GHOST;

        float currentHeight = height();

        float padding = Math.round(currentHeight * (Theme.Sizes.SLIDER_PADDING / (float) Theme.Sizes.SLIDER_HEIGHT));
        float trackHeight = Math.round(currentHeight * (Theme.Sizes.SLIDER_TRACK_HEIGHT / (float) Theme.Sizes.SLIDER_HEIGHT));
        float knob = Math.round(currentHeight * (Theme.Sizes.SLIDER_KNOB_SIZE / (float) Theme.Sizes.SLIDER_HEIGHT));

        if (trackHeight < 2) trackHeight = 2;
        if (knob < 2) knob = 2;

        float trackX = x() + padding;
        float trackWidth = Math.max(0, width() - padding * 2);

        float trackY = y() + (currentHeight - trackHeight) / 2;
        float knobY = y() + (currentHeight - knob) / 2;

        float filled = MathUtil.clamp(Math.round(trackWidth * value), 0, trackWidth);
        float knobX = MathUtil.clamp(trackX + filled - knob / 2, x(), x() + width() - knob);

        Render2DUtil.rect(x(), y(), width(), currentHeight)
                .color(ColorUtil.multiplyAlpha(this.trackColor, effectiveAlpha))
                .radius(999)
                .draw();

        if (filled > 0) {
            Render2DUtil.rect(trackX, trackY, filled, trackHeight)
                    .color(ColorUtil.multiplyAlpha(effectiveFillColor, effectiveAlpha))
                    .radius(999)
                    .shadow(ColorUtil.multiplyAlpha(Theme.Colors.CONTROL_SHADOW, effectiveAlpha), px(Theme.Sizes.SLIDER_SHADOW_BLUR))
                    .draw();
        }

        Render2DUtil.rect(knobX, knobY, knob, knob)
                .color(ColorUtil.multiplyAlpha(effectiveKnobColor, effectiveAlpha))
                .radius(knob)
                .shadow(ColorUtil.multiplyAlpha(Theme.Colors.CONTROL_SHADOW, effectiveAlpha), px(Theme.Sizes.SLIDER_SHADOW_BLUR))
                .draw();
    }

    private void updateValue(int mouseX) {
        double value = MathUtil.clamp01((float) ((preciseMouseX(mouseX) - innerX()) / innerWidth()));
        this.valueConsumer.accept((float) snap(value));
    }

    private double snap(double value) {
        if (this.step <= 0.0F) {
            return value;
        }
        return MathUtil.clamp01((float) (Math.round(value / this.step) * (double) this.step));
    }

    private double preciseMouseX(int mouseX) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                ? minecraft.mouseHandler.getScaledXPos(minecraft.getWindow())
                : mouseX;
    }

    private double innerX() {
        return x() + px(Theme.Sizes.SLIDER_PADDING);
    }

    private double innerWidth() {
        return Math.max(1.0F, width() - px(Theme.Sizes.SLIDER_PADDING) * 2.0F);
    }

    @FunctionalInterface
    public interface ValueConsumer {
        void accept(float value);
    }
}
