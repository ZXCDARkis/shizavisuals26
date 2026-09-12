package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.Theme;

public final class IconButton extends Component {
    private final Runnable action;
    private Identifier icon;
    private int iconSize;
    private int backgroundRadius = 6;
    private int idleTint = Theme.Colors.ICON;
    private int hoverTint = Theme.Colors.TEXT_TITLE;
    private int activeTint = Theme.Colors.TEXT_TITLE;
    private int disabledTint = Theme.Colors.ICON_GHOST;
    private Integer hoverBackground;
    private Integer activeBackground;
    private Integer activeHoverBackground;
    private boolean active;
    private boolean enabled = true;
    private float alpha = 1.0F;
    private int mouseX;
    private int mouseY;

    public IconButton(Identifier icon, int iconSize, Runnable action) {
        this.icon = icon;
        this.iconSize = iconSize;
        this.action = action == null ? () -> {
        } : action;
    }

    public IconButton place(Component owner, float x, float y, int box, int mouseX, int mouseY) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(box), owner.px(box));
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }

    public IconButton placeAt(Component owner, float screenX, float screenY, float boxPx, int mouseX, int mouseY) {
        attach(owner, screenX, screenY, boxPx, boxPx);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }

    public IconButton icon(Identifier icon) {
        this.icon = icon;
        return this;
    }

    public IconButton iconSize(int iconSize) {
        this.iconSize = iconSize;
        return this;
    }

    public IconButton radius(int backgroundRadius) {
        this.backgroundRadius = backgroundRadius;
        return this;
    }

    public IconButton tint(int idleTint, int hoverTint) {
        this.idleTint = idleTint;
        this.hoverTint = hoverTint;
        return this;
    }

    public IconButton activeTint(int activeTint) {
        this.activeTint = activeTint;
        return this;
    }

    public IconButton disabledTint(int disabledTint) {
        this.disabledTint = disabledTint;
        return this;
    }

    public IconButton hoverBackground(Integer color) {
        this.hoverBackground = color;
        return this;
    }

    public IconButton activeBackground(Integer color, Integer hoveredColor) {
        this.activeBackground = color;
        this.activeHoverBackground = hoveredColor;
        return this;
    }

    public IconButton active(boolean active) {
        this.active = active;
        return this;
    }

    public IconButton enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public IconButton alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public boolean hovered() {
        return this.enabled && contains(this.mouseX, this.mouseY);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY) {
        if (!this.enabled || !contains(mouseX, mouseY)) {
            return false;
        }
        this.action.run();
        return true;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        boolean hovered = hovered();
        Integer background = resolveBackground(hovered);
        if (background != null) {
            Render2DUtil.rect(x(), y(), width(), height())
                    .color(ColorUtil.multiplyAlpha(background, this.alpha))
                    .radius(px(this.backgroundRadius))
                    .draw();
        }
        float iconPx = px(this.iconSize);
        float inset = (width() - iconPx) / 2.0F;
        Render2DUtil.texture(x() + inset, y() + inset, iconPx, iconPx, this.icon)
                .color(ColorUtil.multiplyAlpha(resolveTint(hovered), this.alpha))
                .draw();
    }

    private Integer resolveBackground(boolean hovered) {
        if (this.active) {
            return hovered && this.activeHoverBackground != null ? this.activeHoverBackground : this.activeBackground;
        }
        return hovered ? this.hoverBackground : null;
    }

    private int resolveTint(boolean hovered) {
        if (!this.enabled) {
            return this.disabledTint;
        }
        if (this.active) {
            return this.activeTint;
        }
        return hovered ? this.hoverTint : this.idleTint;
    }
}
