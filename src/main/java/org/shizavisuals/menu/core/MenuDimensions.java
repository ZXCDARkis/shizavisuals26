package org.shizavisuals.menu.core;

import net.minecraft.client.Minecraft;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.gui.ScaleUtil;

public record MenuDimensions(float panelWidth, float panelHeight, float panelRadius, float panelBorder) {
    private static final float DESIGN_WIDTH = 1024.0F;
    private static final float DESIGN_HEIGHT = 640.0F;
    private static final float DESIGN_BORDER = 0.5F;
    private static final float SCREEN_MARGIN = 12.0F;

    public MenuDimensions {
        if (panelWidth <= 0.0F || panelHeight <= 0.0F || panelRadius < 0.0F || panelBorder < 0.0F) {
            throw new IllegalArgumentException("Invalid menu dimensions");
        }
    }

    public static MenuDimensions resolve(Minecraft minecraft, MenuOverlayState state) {
        double guiScale = minecraft.getWindow().getGuiScale();
        float menuScale = MathUtil.lerp(MenuOverlayState.MIN_UI_SCALE, MenuOverlayState.MAX_UI_SCALE, state.uiScaleValue());
        float naturalWidth = Math.max(1.0F, ScaleUtil.toGuiPixels(DESIGN_WIDTH * menuScale, guiScale));
        float naturalHeight = Math.max(1.0F, ScaleUtil.toGuiPixels(DESIGN_HEIGHT * menuScale, guiScale));
        float availableWidth = Math.max(1.0F, minecraft.getWindow().getGuiScaledWidth() - SCREEN_MARGIN * 2.0F);
        float availableHeight = Math.max(1.0F, minecraft.getWindow().getGuiScaledHeight() - SCREEN_MARGIN * 2.0F);
        float fit = Math.min(1.0F, Math.min(availableWidth / naturalWidth, availableHeight / naturalHeight));
        return new MenuDimensions(
                Math.max(1.0F, naturalWidth * fit),
                Math.max(1.0F, naturalHeight * fit),
                Math.max(0.0F, ScaleUtil.toGuiPixels(state.panelRadius(), guiScale) * fit),
                Math.max(0.1F, ScaleUtil.toGuiPixels(DESIGN_BORDER * menuScale, guiScale) * fit)
        );
    }
}
