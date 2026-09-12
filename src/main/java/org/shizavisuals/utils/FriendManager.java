package org.shizavisuals.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FriendManager {
    public static final FriendManager INSTANCE = new FriendManager();

    private static final Logger LOGGER = LoggerFactory.getLogger(FriendManager.class);
    private static final long LAST_SEEN_SAVE_INTERVAL_MS = 60_000L;

    private final Path path = ConfigIO.resolve("friends.json");
    private final Map<String, FriendEntry> friends = new LinkedHashMap<>();
    private boolean initialized;
    private long nextPresenceUpdate;

    private FriendManager() {
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        load();
        initialized = true;
    }

    public synchronized boolean add(String name) {
        String displayName = sanitize(name);
        if (!isValidName(displayName) || friends.containsKey(normalize(displayName))) {
            return false;
        }
        friends.put(normalize(displayName), new FriendEntry(displayName, false, 0L));
        save();
        return true;
    }

    public synchronized boolean remove(String name) {
        if (name == null || friends.remove(normalize(name)) == null) {
            return false;
        }
        save();
        return true;
    }

    public synchronized boolean isFriend(String name) {
        return name != null && friends.containsKey(normalize(name));
    }

    public synchronized Collection<String> getFriends() {
        return getEntries().stream().map(FriendEntry::name).toList();
    }

    public synchronized List<FriendEntry> getEntries() {
        List<FriendEntry> entries = new ArrayList<>(friends.values());
        entries.sort(Comparator.comparing(FriendEntry::pinned).reversed());
        return List.copyOf(entries);
    }

    public synchronized boolean togglePinned(String name) {
        if (name == null) {
            return false;
        }
        String key = normalize(name);
        FriendEntry entry = friends.get(key);
        if (entry == null) {
            return false;
        }
        friends.put(key, new FriendEntry(entry.name(), !entry.pinned(), entry.lastSeen()));
        save();
        return true;
    }

    public synchronized void markSeen(String name) {
        if (name == null) {
            return;
        }
        String key = normalize(name);
        FriendEntry entry = friends.get(key);
        if (entry == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - entry.lastSeen() < LAST_SEEN_SAVE_INTERVAL_MS) {
            return;
        }
        friends.put(key, new FriendEntry(entry.name(), entry.pinned(), now));
        save();
    }

    public synchronized boolean clear() {
        if (friends.isEmpty()) {
            return false;
        }
        friends.clear();
        save();
        return true;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        long now = System.currentTimeMillis();
        if (now < this.nextPresenceUpdate || event.getClient().getConnection() == null) {
            return;
        }
        this.nextPresenceUpdate = now + 5000L;
        for (var player : event.getClient().getConnection().getOnlinePlayers()) {
            markSeen(player.getProfile().name());
        }
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    private void load() {
        JsonObject root = ConfigIO.read(path);
        if (root == null) {
            return;
        }

        try {
            JsonElement entries = root.get("friends");
            if (entries == null || !entries.isJsonArray()) {
                return;
            }

            for (JsonElement element : entries.getAsJsonArray()) {
                FriendEntry entry = readEntry(element);
                if (entry != null) {
                    friends.putIfAbsent(normalize(entry.name()), entry);
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to load friends from {}", path, exception);
        }
    }

    private FriendEntry readEntry(JsonElement element) {
        if (element.isJsonPrimitive()) {
            String name = sanitize(element.getAsString());
            return isValidName(name) ? new FriendEntry(name, false, 0L) : null;
        }
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String name = object.has("name") ? sanitize(object.get("name").getAsString()) : null;
        if (!isValidName(name)) {
            return null;
        }
        boolean pinned = object.has("pinned") && object.get("pinned").getAsBoolean();
        long lastSeen = object.has("lastSeen") ? Math.max(0L, object.get("lastSeen").getAsLong()) : 0L;
        return new FriendEntry(name, pinned, lastSeen);
    }

    private void save() {
        JsonArray entries = new JsonArray();
        for (FriendEntry friend : friends.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", friend.name());
            entry.addProperty("pinned", friend.pinned());
            entry.addProperty("lastSeen", friend.lastSeen());
            entries.add(entry);
        }

        JsonObject root = new JsonObject();
        root.add("friends", entries);

        ConfigIO.write(path, root);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String name = value.trim();
        return name.isEmpty() ? null : name;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record FriendEntry(String name, boolean pinned, long lastSeen) {
    }
}
