package org.shizavisuals.context;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface Render2DContext extends MinecraftContext {
    default boolean isInRender2D() {
        return RenderContext.state2D().isActive();
    }

    default Gui gui() {
        return RenderContext.state2D().getGui();
    }

    default GuiGraphicsExtractor guiGraphicsExtractor() {
        return RenderContext.state2D().getGuiGraphicsExtractor();
    }

    default DeltaTracker deltaTracker() {
        return RenderContext.state2D().getDeltaTracker();
    }
}
