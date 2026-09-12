package org.shizavisuals.mixin.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.shizavisuals.utils.render.esp.TargetScanState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class TargetScanRenderStateMixin implements TargetScanState {
    @Unique
    private boolean shiza$targetScanTarget;

    @Override
    public boolean shiza$isTargetScanTarget() {
        return this.shiza$targetScanTarget;
    }

    @Override
    public void shiza$setTargetScanTarget(boolean target) {
        this.shiza$targetScanTarget = target;
    }
}
