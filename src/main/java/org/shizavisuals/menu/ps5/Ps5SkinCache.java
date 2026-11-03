package org.shizavisuals.menu.ps5;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Skin resolution for the PS5 menu (session chip / account previews). */
public final class Ps5SkinCache {
    private static final ConcurrentHashMap<String, Supplier<PlayerSkin>> SKINS = new ConcurrentHashMap<>();
    private static final Set<String> RESOLVING = ConcurrentHashMap.newKeySet();

    private Ps5SkinCache() {
    }

    public static Identifier texture(Minecraft minecraft, String name) {
        PlayerInfo online = onlineInfo(minecraft, name);
        String key = normalize(name);
        if (online != null) {
            Supplier<PlayerSkin> skin = online::getSkin;
            SKINS.put(key, skin);
            return skin.get().body().texturePath();
        }

        Supplier<PlayerSkin> fallback = SKINS.computeIfAbsent(key, ignored -> defaultSkin(name));
        resolve(minecraft, name, key);
        return fallback.get().body().texturePath();
    }

    private static PlayerInfo onlineInfo(Minecraft minecraft, String name) {
        if (minecraft.getConnection() == null) {
            return null;
        }
        return minecraft.getConnection().getOnlinePlayers().stream()
                .filter(info -> info.getProfile().name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private static void resolve(Minecraft minecraft, String name, String key) {
        if (!RESOLVING.add(key)) {
            return;
        }
        CompletableFuture
                .supplyAsync(() -> minecraft.services().profileResolver().fetchByName(name), Util.nonCriticalIoPool())
                .thenAccept(profile -> profile.ifPresent(resolved ->
                        minecraft.execute(() -> SKINS.put(key, minecraft.getSkinManager().createLookup(resolved, false)))))
                .whenComplete((unused, throwable) -> RESOLVING.remove(key));
    }

    private static Supplier<PlayerSkin> defaultSkin(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);
        PlayerSkin skin = DefaultPlayerSkin.get(profile);
        return () -> skin;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
