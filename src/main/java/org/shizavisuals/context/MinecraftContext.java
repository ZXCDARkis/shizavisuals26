package org.shizavisuals.context;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;

public interface MinecraftContext {
    Minecraft mc = Minecraft.getInstance();

    default Minecraft client() {
        return mc;
    }

    default LocalPlayer player() {
        return mc.player;
    }

    default ClientLevel level() {
        return mc.level;
    }

    default MultiPlayerGameMode gameMode() {
        return mc.gameMode;
    }

    default boolean isCurrentConnection(Connection connection) {
        if (connection == null) {
            return true;
        }
        var packetListener = client().getConnection();
        return packetListener != null && packetListener.getConnection() == connection;
    }

    default Screen screen() {
        return mc.gui.screen();
    }

    default boolean inGame() {
        return player() != null && level() != null;
    }
}
