package org.shizavisuals.context;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

public interface PlayerContext extends WorldContext {
    double MOVEMENT_EPSILON = 1.0E-4;

    default LocalPlayer localPlayer() {
        return player();
    }

    default Inventory inventory() {
        LocalPlayer player = localPlayer();
        return player != null ? player.getInventory() : null;
    }

    default boolean hasPlayer() {
        return localPlayer() != null;
    }

    default boolean isMoving() {
        LocalPlayer player = localPlayer();
        if (player == null) {
            return false;
        }

        Vec3 movement = player.getDeltaMovement();
        return movement.horizontalDistanceSqr() > MOVEMENT_EPSILON;
    }

    default boolean hasMovementInput() {
        LocalPlayer player = localPlayer();
        return player != null && (player.input.keyPresses.forward()
                || player.input.keyPresses.backward()
                || player.input.keyPresses.left()
                || player.input.keyPresses.right());
    }
}
