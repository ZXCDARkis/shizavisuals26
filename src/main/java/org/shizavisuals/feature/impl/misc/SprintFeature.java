package org.shizavisuals.feature.impl.misc;

import net.minecraft.client.player.LocalPlayer;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.lwjgl.glfw.GLFW;

public final class SprintFeature extends Feature {
    public final BooleanSetting omniDirectional = register(new BooleanSetting("Omni Directional", false));
    public final BooleanSetting keepSprint = register(new BooleanSetting("Keep Sprint", true));

    public SprintFeature() {
        super("Sprint", "Automatically sprints while moving", FeatureCategory.MISC, GLFW.GLFW_KEY_V);
    }

    public static SprintFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(SprintFeature.class);
    }

    public static boolean shouldForceSprintKey() {
        return getEnabled() != null && !pveControlsMovement();
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!omniDirectional.getValue() || pveControlsMovement()) {
            return;
        }

        LocalPlayer player = event.getClient().player;
        if (player == null || player.isSpectator() || player.isUsingItem()) {
            return;
        }

        if (player.input.getMoveVector().lengthSquared() > 0.0F
                && (keepSprint.getValue() || !player.isSprinting())) {
            player.setSprinting(true);
        }
    }

    private static boolean pveControlsMovement() {
        return false;
    }
}
