package org.shizavisuals.utils.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Serializable snapshot of one item stack: id, count, display name, full NBT. */
public final class InvItem {
    public final String id;
    public final int count;
    public final String name;
    public final String json;
    public final String key;

    private ItemStack cached;
    private String keyCache;

    public InvItem(String id, int count, String name, String json, String key) {
        this.id = id;
        this.count = count;
        this.name = name == null ? "" : name;
        this.json = json == null ? "" : json;
        this.key = key == null ? "" : key;
    }

    public InvItem(String id, int count, String name, String json) {
        this(id, count, name, json, "");
    }

    public static InvItem of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ItemStack copy = stack.copy();
        String id = BuiltInRegistries.ITEM.getKey(copy.getItem()).toString();
        String displayName;
        try {
            displayName = copy.getHoverName().getString();
        } catch (RuntimeException ignored) {
            displayName = "";
        }
        InvItem item = new InvItem(id, copy.getCount(), displayName, encode(copy),
                InventoryDiff.identityKey(copy));
        item.cached = copy;
        return item;
    }

    public InvItem withCount(int newCount) {
        return new InvItem(id, newCount, name, json, key);
    }

    public ItemStack toStack() {
        ItemStack resolved = resolve().copy();
        if (!resolved.isEmpty()) {
            resolved.setCount(Math.max(1, count));
        }
        return resolved;
    }

    private ItemStack resolve() {
        if (cached != null) {
            return cached;
        }
        if (!json.isEmpty()) {
            ItemStack decoded = decode(json);
            if (!decoded.isEmpty()) {
                cached = decoded;
                return cached;
            }
        }
        var key = net.minecraft.resources.Identifier.tryParse(id);
        var ref = key == null ? java.util.Optional.<net.minecraft.core.Holder.Reference<Item>>empty()
                : BuiltInRegistries.ITEM.get(key);
        Item item = ref.map(net.minecraft.core.Holder.Reference::value).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    public String identityKey() {
        if (!key.isEmpty()) {
            return key;
        }
        if (keyCache == null) {
            keyCache = InventoryDiff.identityKey(resolve());
        }
        return keyCache;
    }

    public boolean matches(ItemStack other) {
        return other != null && !other.isEmpty()
                && identityKey().equals(InventoryDiff.identityKey(other));
    }

    public String searchName() {
        return cleanName(name);
    }

    static String encode(ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || stack.isEmpty()) {
                return "";
            }
            var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return ItemStack.CODEC.encodeStart(ops, stack)
                    .map(JsonElement::toString)
                    .result()
                    .orElse("");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static ItemStack decode(String json) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return ItemStack.EMPTY;
            }
            var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, JsonParser.parseString(json))
                    .result()
                    .orElse(ItemStack.EMPTY);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** Strips color codes and decorative filler from funtime-style item names. */
    public static String cleanName(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        s = s.replaceAll("&#[0-9A-Fa-f]{6}", " ");
        s = s.replaceAll("[§&][0-9A-Za-z]", " ");
        String[] parts = s.trim().split("\\s+");
        int lo = 0, hi = parts.length - 1;
        while (lo <= hi && isDecorative(parts[lo])) lo++;
        while (hi >= lo && isDecorative(parts[hi])) hi--;
        StringBuilder sb = new StringBuilder();
        for (int i = lo; i <= hi; i++) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? s.trim() : out;
    }

    private static boolean isDecorative(String t) {
        if (t.isEmpty()) {
            return true;
        }
        boolean allSame = true;
        for (int i = 1; i < t.length(); i++) {
            if (t.charAt(i) != t.charAt(0)) {
                allSame = false;
                break;
            }
        }
        if (allSame && t.length() >= 2) {
            return true;
        }
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetterOrDigit(t.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
