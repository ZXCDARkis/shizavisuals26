package org.shizavisuals.utils.render.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

import java.util.Map;

public final class DynamicLightManager {
    public static final DynamicLightManager INSTANCE = new DynamicLightManager();

    private volatile Map<Long, Integer> virtualLuminance = Map.of();
    private ClientLevel engineLevel;

    private DynamicLightManager() {
    }

    public static int virtualLuminance(long blockPos) {
        return INSTANCE.virtualLuminance.getOrDefault(blockPos, 0);
    }

    public void revalidateAfterVanillaUpdates(ClientLevel level) {
        Map<Long, Integer> sources = this.virtualLuminance;
        if (level == null || level != this.engineLevel || sources.isEmpty()) {
            return;
        }
        var blockLight = level.getChunkSource()
                .getLightEngine()
                .getLayerListener(LightLayer.BLOCK);
        for (long packedPos : sources.keySet()) {
            blockLight.checkBlock(BlockPos.of(packedPos));
        }
        blockLight.runLightUpdates();
    }

    public void clear() {
        this.virtualLuminance = Map.of();
        this.engineLevel = null;
    }
}
