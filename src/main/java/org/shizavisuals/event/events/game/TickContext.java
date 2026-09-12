package org.shizavisuals.event.events.game;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

@Getter
public final class TickContext {
    private Minecraft client;
    private LocalPlayer player;
    private ClientLevel level;
    private boolean worldReady;

    private boolean closestAttackableResolved;
    private Entity closestAttackableTarget;
    private double closestAttackableDistance = Double.POSITIVE_INFINITY;

    public TickContext begin(Minecraft client) {
        this.client = client;
        this.player = client.player;
        this.level = client.level;
        this.worldReady = this.player != null && this.level != null;
        this.closestAttackableResolved = false;
        this.closestAttackableTarget = null;
        this.closestAttackableDistance = Double.POSITIVE_INFINITY;
        return this;
    }
}
