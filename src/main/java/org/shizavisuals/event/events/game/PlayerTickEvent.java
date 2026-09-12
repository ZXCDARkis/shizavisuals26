package org.shizavisuals.event.events.game;

import lombok.Getter;
import net.minecraft.client.player.LocalPlayer;
import org.shizavisuals.event.Event;

@Getter
public final class PlayerTickEvent extends Event {
    public enum Phase {
        PRE,
        POST
    }

    private LocalPlayer player;
    private Phase phase;

    public PlayerTickEvent set(LocalPlayer player, Phase phase) {
        this.player = player;
        this.phase = phase;
        return this;
    }

    public boolean isPre() {
        return this.phase == Phase.PRE;
    }

    public boolean isPost() {
        return this.phase == Phase.POST;
    }
}
