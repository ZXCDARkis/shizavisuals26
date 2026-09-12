package org.shizavisuals.event.events.lifecycle;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.shizavisuals.event.Event;

@Getter
public final class DisconnectEvent extends Event {
    private Minecraft client;
    private Screen screen;
    private boolean transferring;
    private boolean resetting;

    public DisconnectEvent set(Minecraft client, Screen screen, boolean transferring, boolean resetting) {
        this.client = client;
        this.screen = screen;
        this.transferring = transferring;
        this.resetting = resetting;
        return this;
    }
}
