package org.shizavisuals.hud.island;

import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

public abstract class IslandStatus {
    protected final DynamicIsland island;
    protected final String name;
    protected IslandSize size = new IslandSize(48.0F, 15.0F);
    protected final IslandAnimation animation = new IslandAnimation(500L, IslandEasing.BAKEK_SIZE);
    private volatile boolean enabled = true;
    private boolean toggleable = true;

    public IslandStatus(DynamicIsland island, String name) {
        this.island = island;
        this.name = name;
    }

    public IslandStatus(DynamicIsland island, String name, boolean toggleable) {
        this.island = island;
        this.name = name;
        this.toggleable = toggleable;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean isToggleable() {
        return this.toggleable;
    }

    public void draw(float alpha) {
    }

    public void click(float mouseX, float mouseY, int button) {
    }

    public abstract boolean canShow();

    public int getColor() {
        return ColorUtil.rgb(22, 22, 22);
    }

    public IslandSize getSize() {
        return this.size;
    }

    public IslandAnimation getAnimation() {
        return this.animation;
    }

    public String getName() {
        return this.name;
    }

    protected static MsdfFont font(String text, UiFontStyle style) {
        return UiFonts.getFontForText(text, style.weight());
    }

    protected static float spacing(float size) {
        return size * UiFontStyle.MEDIUM.letterSpacingEm();
    }
}
