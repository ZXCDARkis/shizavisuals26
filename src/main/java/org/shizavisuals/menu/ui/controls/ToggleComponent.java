package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.AnimatedValue;
import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.util.function.BooleanSupplier;

public final class ToggleComponent extends Component {
    private static final long ANIMATION_MS = 140L;

    private final BooleanSupplier enabledSupplier;
    private final Runnable toggleAction;
    private final AnimatedValue animation = new AnimatedValue(ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    private Style style;
    private int offColor;
    private int onColor;
    private float alpha = 1.0F;

    public ToggleComponent(BooleanSupplier enabledSupplier, Runnable toggleAction, Style style, int offColor, int onColor) {
        this.enabledSupplier = enabledSupplier;
        this.toggleAction = toggleAction;
        this.style = style;
        this.offColor = offColor;
        this.onColor = onColor;
    }

    public ToggleComponent place(Component owner, int x, int y, int width, int height) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        return this;
    }

    public ToggleComponent style(Style style, int offColor, int onColor) {
        this.style = style;
        this.offColor = offColor;
        this.onColor = onColor;
        return this;
    }

    public ToggleComponent circle() {
        this.style = Style.CIRCLE;
        return this;
    }

    public ToggleComponent alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public boolean handleClick(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        this.toggleAction.run();
        return true;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        float progress = this.animation.toward(this.enabledSupplier.getAsBoolean() ? 1.0F : 0.0F);
        if (this.style == Style.CIRCLE || this.style == Style.DOT) {
            drawDot(progress);
            return;
        }
        drawSwitch(progress);
    }

    private void drawSwitch(float progress) {
        float padding = Math.max(1, height() / 8);
        float knobHeight = Math.max(1, height() - padding * 2);
        float knobWidth = Math.max(knobHeight, width() - height());
        float travel = Math.max(0, width() - knobWidth - padding * 2);

        Render2DUtil.rect(x(), y(), width(), height())
                .color(ColorUtil.multiplyAlpha(ColorUtil.lerp(this.offColor, this.onColor, progress), this.alpha))
                .radius(Math.max(1, height() / 2))
                .draw();
        Render2DUtil.rect(x() + padding + Math.round(travel * progress), y() + (height() - knobHeight) / 2, knobWidth, knobHeight)
                .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, this.alpha))
                .radius(Math.max(1, knobHeight / 2))
                .draw();
    }

    private void drawDot(float progress) {
        float diameter = Math.min(width(), height());
        float left = x() + (width() - diameter) / 2;
        float top = y() + (height() - diameter) / 2;
        float dot = Math.max(4, diameter / 4);
        if ((Math.round(dot) & 1) != (Math.round(diameter) & 1)) {
            dot++;
        }
        dot = Math.min(dot, diameter - 2);
        float offset = (diameter - dot) / 2;

        Render2DUtil.rect(left, top, diameter, diameter)
                .color(ColorUtil.multiplyAlpha(ColorUtil.lerp(this.offColor, this.onColor, progress), this.alpha))
                .radius(diameter)
                .draw();
        if (progress > 0.01F) {
            Render2DUtil.rect(left + offset, top + offset, dot, dot)
                    .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, progress * this.alpha))
                    .radius(dot)
                    .draw();
        }
    }

    public enum Style {
        SWITCH,
        CIRCLE,
        DOT
    }
}
