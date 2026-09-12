package org.shizavisuals.feature.impl.misc;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.utils.text.ChatUtil;

public final class DeathCoordsFeature extends Feature {
    private boolean reported;

    public DeathCoordsFeature() {
        super("DeathCoords", "Shows your coordinates on death", FeatureCategory.MISC, BindSetting.UNBOUND);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        LocalPlayer player = event.getClient().player;
        if (player == null) {
            reported = false;
            return;
        }

        if (!player.isDeadOrDying()) {
            reported = false;
            return;
        }
        if (reported) {
            return;
        }

        BlockPos pos = player.getOnPos();
        ChatUtil.print("Death at x: " + pos.getX() + " y: " + pos.getY() + " z: " + pos.getZ());
        reported = true;
    }
}
