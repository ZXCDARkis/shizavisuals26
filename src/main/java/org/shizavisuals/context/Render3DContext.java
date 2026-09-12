package org.shizavisuals.context;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;

public interface Render3DContext extends MinecraftContext {
    default boolean isInRender3D() {
        return RenderContext.state3D().isActive();
    }

    default GameRenderer gameRenderer3D() {
        return RenderContext.state3D().getGameRenderer();
    }

    default DeltaTracker deltaTracker() {
        return RenderContext.state3D().getDeltaTracker();
    }
}
