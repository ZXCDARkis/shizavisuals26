package org.shizavisuals.mixin.render;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.shizavisuals.utils.render.ItemEntityRenderStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemEntityRenderStateAccess {
    @Unique
    private boolean onGround;

    @Override
    public boolean isOnGround() {
        return onGround;
    }

    @Override
    public void setOnGround(boolean onGround) {
        onGround = onGround;
    }
}
