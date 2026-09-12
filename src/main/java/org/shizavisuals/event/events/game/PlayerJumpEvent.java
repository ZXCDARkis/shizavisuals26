package org.shizavisuals.event.events.game;

import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.event.Event;

@Getter
public final class PlayerJumpEvent extends Event {
    private LocalPlayer player;
    private Vec3 position;

    public PlayerJumpEvent set(LocalPlayer player, Vec3 position) {
        this.player = player;
        this.position = position;
        return this;
    }
}
