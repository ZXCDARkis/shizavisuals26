package org.shizavisuals.menu;

import net.minecraft.client.Minecraft;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.render.FinalGuiRenderEvent;
import org.shizavisuals.menu.core.MenuOverlay;
import org.shizavisuals.menu.core.MenuAppearance;
import org.shizavisuals.utils.render.gui.Render2DUtil;

public final class MenuOverlayRenderHandler {
    @EventTarget
    public void onFinalGuiRender(FinalGuiRenderEvent event) {
        Minecraft minecraft = event.getClient();
        RenderContext.enter2D(event.getGui(), event.getGuiGraphicsExtractor(), event.getDeltaTracker());
        try {
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            int screenHeight = minecraft.getWindow().getGuiScaledHeight();
            Render2DUtil.beginFrame();
            Render2DUtil.setBackdropBlurScale(MenuAppearance.glassBlurScale());
            MenuOverlay.render(minecraft, event.getGuiGraphicsExtractor(), screenWidth, screenHeight);
            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }
}
