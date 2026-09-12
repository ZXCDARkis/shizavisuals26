package org.shizavisuals.hud.island;

import org.shizavisuals.utils.ColorUtil;

public class IslandColorAnimation {
    private final IslandAnimation red;
    private final IslandAnimation green;
    private final IslandAnimation blue;
    private final IslandAnimation alpha;
    private boolean initialized;

    public IslandColorAnimation(long duration, int initialColor, IslandEasing easing) {
        this.red = new IslandAnimation(duration, ColorUtil.red(initialColor), easing);
        this.green = new IslandAnimation(duration, ColorUtil.green(initialColor), easing);
        this.blue = new IslandAnimation(duration, ColorUtil.blue(initialColor), easing);
        this.alpha = new IslandAnimation(duration, ColorUtil.alpha(initialColor), easing);
        this.initialized = true;
    }

    public void update(int targetColor) {
        if (!this.initialized) {
            this.setColor(targetColor);
            return;
        }
        this.red.update(ColorUtil.red(targetColor));
        this.green.update(ColorUtil.green(targetColor));
        this.blue.update(ColorUtil.blue(targetColor));
        this.alpha.update(ColorUtil.alpha(targetColor));
    }

    public int getColor() {
        return ColorUtil.pack(
                (int) this.red.getValue(),
                (int) this.green.getValue(),
                (int) this.blue.getValue(),
                (int) this.alpha.getValue()
        );
    }

    public void setColor(int color) {
        this.initialized = true;
        this.red.setValue(ColorUtil.red(color));
        this.green.setValue(ColorUtil.green(color));
        this.blue.setValue(ColorUtil.blue(color));
        this.alpha.setValue(ColorUtil.alpha(color));
    }
}
