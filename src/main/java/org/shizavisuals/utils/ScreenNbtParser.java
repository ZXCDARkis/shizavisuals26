package org.shizavisuals.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.game.GameTickEvent;
import org.shizavisuals.event.events.lifecycle.ShutdownEvent;
import org.shizavisuals.event.events.screen.ScreenCloseEvent;
import org.shizavisuals.feature.impl.misc.DonItems;
import org.shizavisuals.utils.text.ChatUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ScreenNbtParser {
    public static final ScreenNbtParser INSTANCE = new ScreenNbtParser();
    public static final String OUTPUT_FILE = "shizavisuals-nbt-parser.jsonl";

    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenNbtParser.class);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int STABLE_TICKS = 2;
    private static final int MAX_DIRTY_TICKS = 20;
    private static final int SCHEMA_VERSION = 1;

    private boolean capturing;
    private Path outputPath;
    private Screen trackedScreen;
    private String observedFingerprint;
    private String dumpedFingerprint;
    private int stableTicks;
    private int dirtyTicks;
    private int snapshotCount;
    private String lastError;

    private ScreenNbtParser() {
    }

    public boolean start(Minecraft client) {
        Path path = resolveOutputPath(client);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            this.outputPath = path;
            this.capturing = true;
            this.snapshotCount = 0;
            this.lastError = null;
            resetTrackedScreen();
            writeRecord(sessionRecord("session_start", client));
            return true;
        } catch (IOException exception) {
            return fail("Unable to start NBT parser", exception);
        }
    }

    public boolean stop(Minecraft client) {
        if (!this.capturing) {
            this.lastError = "NBT parser is not running";
            return false;
        }

        try {
            JsonObject record = sessionRecord("session_end", client);
            record.addProperty("snapshots", this.snapshotCount);
            writeRecord(record);
            this.capturing = false;
            resetTrackedScreen();
            return true;
        } catch (IOException exception) {
            this.capturing = false;
            return fail("Unable to finish NBT parser log", exception);
        }
    }

    public boolean clear(Minecraft client) {
        Path path = resolveOutputPath(client);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    "",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            this.outputPath = path;
            this.snapshotCount = 0;
            this.lastError = null;
            resetTrackedScreen();
            if (this.capturing) {
                writeRecord(sessionRecord("session_start", client));
            }
            return true;
        } catch (IOException exception) {
            return fail("Unable to clear NBT parser log", exception);
        }
    }

    public boolean captureNow(Minecraft client) {
        if (!this.capturing) {
            this.lastError = "NBT parser is not running";
            return false;
        }
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            this.lastError = "No container screen is currently open";
            return false;
        }

        try {
            track(screen);
            dumpSnapshot(client, screen, "manual");
            return true;
        } catch (Exception exception) {
            return failAndStop("Unable to capture container screen", exception);
        }
    }

    public boolean isCapturing() {
        return this.capturing;
    }

    public int getSnapshotCount() {
        return this.snapshotCount;
    }

    public String getLastError() {
        return this.lastError;
    }

    public Path getOutputPath(Minecraft client) {
        return this.outputPath == null ? resolveOutputPath(client) : this.outputPath;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!this.capturing) {
            return;
        }

        Screen current = event.getClient().gui.screen();
        if (!(current instanceof AbstractContainerScreen<?> screen)) {
            resetTrackedScreen();
            return;
        }

        try {
            track(screen);
            String fingerprint = fingerprint(screen);
            if (Objects.equals(fingerprint, this.observedFingerprint)) {
                this.stableTicks++;
            } else {
                this.observedFingerprint = fingerprint;
                this.stableTicks = 0;
            }

            if (Objects.equals(fingerprint, this.dumpedFingerprint)) {
                this.dirtyTicks = 0;
                return;
            }

            this.dirtyTicks++;
            if (this.stableTicks >= STABLE_TICKS || this.dirtyTicks >= MAX_DIRTY_TICKS) {
                dumpSnapshot(event.getClient(), screen, this.stableTicks >= STABLE_TICKS ? "stable" : "timeout");
            }
        } catch (Exception exception) {
            failAndStop("NBT parser stopped after a capture error", exception);
            ChatUtil.error("NBT parser stopped  •  " + this.lastError);
        }
    }

    @EventTarget
    public void onScreenClose(ScreenCloseEvent event) {
        if (!this.capturing
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen != this.trackedScreen) {
            return;
        }

        try {
            String fingerprint = fingerprint(screen);
            if (!Objects.equals(fingerprint, this.dumpedFingerprint)) {
                this.observedFingerprint = fingerprint;
                dumpSnapshot(event.getClient(), screen, "screen_close");
            }
        } catch (Exception exception) {
            failAndStop("NBT parser stopped while saving the closing screen", exception);
            ChatUtil.error("NBT parser stopped  •  " + this.lastError);
        } finally {
            resetTrackedScreen();
        }
    }

    @EventTarget
    public void onShutdown(ShutdownEvent event) {
        if (!this.capturing) {
            return;
        }
        try {
            JsonObject record = sessionRecord("session_end", event.getClient());
            record.addProperty("snapshots", this.snapshotCount);
            record.addProperty("reason", "client_shutdown");
            writeRecord(record);
        } catch (IOException exception) {
            LOGGER.error("Unable to finish NBT parser log during shutdown", exception);
        } finally {
            this.capturing = false;
        }
    }

    private void track(AbstractContainerScreen<?> screen) {
        if (screen == this.trackedScreen) {
            return;
        }
        this.trackedScreen = screen;
        this.observedFingerprint = null;
        this.dumpedFingerprint = null;
        this.stableTicks = 0;
        this.dirtyTicks = 0;
    }

    private void resetTrackedScreen() {
        this.trackedScreen = null;
        this.observedFingerprint = null;
        this.dumpedFingerprint = null;
        this.stableTicks = 0;
        this.dirtyTicks = 0;
    }

    private String fingerprint(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        StringBuilder value = new StringBuilder(64 + menu.slots.size() * 16);
        value.append(screen.getClass().getName())
                .append('\u0000')
                .append(screen.getTitle().getString())
                .append('\u0000')
                .append(menu.getClass().getName());

        for (int index = 0; index < menu.slots.size(); index++) {
            ItemStack stack = menu.slots.get(index).getItem();
            value.append('|').append(index).append(':');
            appendStackFingerprint(value, stack);
        }
        value.append("|carried:");
        appendStackFingerprint(value, menu.getCarried());
        return value.toString();
    }

    private void appendStackFingerprint(StringBuilder target, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            target.append('0');
            return;
        }
        target.append(stack.getCount())
                .append(':')
                .append(ItemStack.hashItemAndComponents(stack));
    }

    private void dumpSnapshot(Minecraft client,
                              AbstractContainerScreen<?> screen,
                              String reason) throws IOException {
        HolderLookup.Provider registries = client.level == null
                ? RegistryAccess.EMPTY
                : client.level.registryAccess();
        RegistryOps<JsonElement> jsonOps = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        RegistryOps<Tag> nbtOps = RegistryOps.create(NbtOps.INSTANCE, registries);
        List<String> snapshotErrors = new ArrayList<>();

        AbstractContainerMenu menu = screen.getMenu();
        JsonObject record = baseRecord("screen_snapshot");
        record.addProperty("sequence", ++this.snapshotCount);
        record.addProperty("reason", reason);
        record.addProperty("screen_class", screen.getClass().getName());
        record.addProperty("title", screen.getTitle().getString());
        record.add("title_json", encodeComponent(screen.getTitle(), jsonOps, snapshotErrors));
        record.addProperty("menu_class", menu.getClass().getName());
        record.addProperty("menu_type", menuTypeId(menu));
        record.addProperty("container_id", menu.containerId);
        record.addProperty("state_id", menu.getStateId());
        record.addProperty("slot_count", menu.slots.size());

        int occupiedSlots = 0;
        JsonArray slots = new JsonArray();
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.hasItem()) {
                occupiedSlots++;
            }
            slots.add(encodeSlot(index, slot, jsonOps, nbtOps));
        }
        record.addProperty("occupied_slot_count", occupiedSlots);
        record.add("slots", slots);

        ItemStack carried = menu.getCarried();
        if (carried != null && !carried.isEmpty()) {
            record.add("carried", encodeItem(carried, jsonOps, nbtOps));
        } else {
            record.add("carried", JsonNull.INSTANCE);
        }

        if (!snapshotErrors.isEmpty()) {
            record.add("serialization_errors", strings(snapshotErrors));
        }
        writeRecord(record);
        this.dumpedFingerprint = fingerprint(screen);
        this.observedFingerprint = this.dumpedFingerprint;
        this.stableTicks = 0;
        this.dirtyTicks = 0;
    }

    private JsonObject encodeSlot(int menuIndex,
                                  Slot slot,
                                  RegistryOps<JsonElement> jsonOps,
                                  RegistryOps<Tag> nbtOps) {
        JsonObject result = new JsonObject();
        result.addProperty("menu_index", menuIndex);
        result.addProperty("container_slot", slot.getContainerSlot());
        result.addProperty("x", slot.x);
        result.addProperty("y", slot.y);
        result.addProperty("container_class", slot.container.getClass().getName());
        result.addProperty("active", slot.isActive());
        result.addProperty("fake", slot.isFake());

        ItemStack stack = slot.getItem();
        boolean empty = stack == null || stack.isEmpty();
        result.addProperty("empty", empty);
        if (!empty) {
            try {
                result.add("item", encodeItem(stack, jsonOps, nbtOps));
            } catch (RuntimeException exception) {
                JsonObject failed = new JsonObject();
                failed.addProperty("item_id", itemId(stack));
                failed.addProperty("count", stack.getCount());
                failed.addProperty("error", exception.toString());
                result.add("item", failed);
            }
        }
        return result;
    }

    private JsonObject encodeItem(ItemStack stack,
                                  RegistryOps<JsonElement> jsonOps,
                                  RegistryOps<Tag> nbtOps) {
        List<String> errors = new ArrayList<>();
        JsonObject result = new JsonObject();
        result.addProperty("item_id", itemId(stack));
        result.addProperty("item_class", stack.getItem().getClass().getName());
        result.addProperty("count", stack.getCount());
        result.addProperty("hover_name", stack.getHoverName().getString());
        result.add("hover_name_json", encodeComponent(stack.getHoverName(), jsonOps, errors));
        result.addProperty("item_name", stack.getItemName().getString());
        result.add("item_name_json", encodeComponent(stack.getItemName(), jsonOps, errors));
        result.addProperty("foil", stack.hasFoil());
        result.addProperty("damageable", stack.isDamageableItem());
        if (stack.isDamageableItem()) {
            result.addProperty("damage", stack.getDamageValue());
            result.addProperty("max_damage", stack.getMaxDamage());
        }

        result.addProperty("component_patch_size", stack.getComponentsPatch().size());
        result.add("component_patch", encodeComponentPatch(stack.getComponentsPatch(), jsonOps, errors));
        result.add("resolved_component_ids", resolvedComponentIds(stack));
        result.add("lore", encodeLore(stack, jsonOps, errors));
        result.add("enchantments", encodeEnchantments(stack));
        result.add("potion", encodePotion(stack));
        DonItems.findAny(stack).ifPresent(item -> {
            JsonObject recognized = new JsonObject();
            recognized.addProperty("server", item.server().name());
            recognized.addProperty("category", item.category().name());
            recognized.addProperty("enum", ((Enum<?>) item).name());
            recognized.addProperty("display_name", item.displayName());
            result.add("don_item", recognized);
        });

        result.add(
                "stack_json",
                valueOrFallback(
                        ItemStack.CODEC.encodeStart(jsonOps, stack),
                        errors,
                        new JsonPrimitive(stack.toString())
                )
        );
        Tag encodedNbt = valueOrFallback(
                ItemStack.CODEC.encodeStart(nbtOps, stack),
                errors,
                null
        );
        result.addProperty("stack_snbt", encodedNbt == null ? stack.toString() : encodedNbt.toString());

        if (!errors.isEmpty()) {
            result.add("serialization_errors", strings(errors));
        }
        return result;
    }

    private JsonObject encodeComponentPatch(DataComponentPatch patch,
                                            RegistryOps<JsonElement> jsonOps,
                                            List<String> errors) {
        List<Map.Entry<DataComponentType<?>, Optional<?>>> entries = new ArrayList<>(patch.entrySet());
        entries.sort(Comparator.comparing(entry -> componentId(entry.getKey())));

        JsonObject result = new JsonObject();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : entries) {
            String id = componentId(entry.getKey());
            if (entry.getValue().isEmpty()) {
                result.add(id, JsonNull.INSTANCE);
                continue;
            }
            TypedDataComponent<?> component = TypedDataComponent.createUnchecked(
                    entry.getKey(),
                    entry.getValue().get()
            );
            result.add(
                    id,
                    valueOrFallback(
                            component.encodeValue(jsonOps),
                            errors,
                            new JsonPrimitive(String.valueOf(entry.getValue().get()))
                    )
            );
        }
        return result;
    }

    private JsonArray resolvedComponentIds(ItemStack stack) {
        List<String> ids = new ArrayList<>(stack.getComponents().size());
        for (TypedDataComponent<?> component : stack.getComponents()) {
            ids.add(componentId(component.type()));
        }
        ids.sort(String::compareTo);
        return strings(ids);
    }

    private JsonArray encodeLore(ItemStack stack,
                                 RegistryOps<JsonElement> jsonOps,
                                 List<String> errors) {
        JsonArray result = new JsonArray();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return result;
        }

        List<Component> lines = lore.styledLines();
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            JsonObject encoded = new JsonObject();
            encoded.addProperty("index", index);
            encoded.addProperty("text", line.getString());
            encoded.add("json", encodeComponent(line, jsonOps, errors));
            result.add(encoded);
        }
        return result;
    }

    private JsonArray encodeEnchantments(ItemStack stack) {
        JsonArray result = new JsonArray();
        stack.getEnchantments().entrySet().stream()
                .sorted(Comparator.comparing(entry -> holderId(entry.getKey())))
                .forEach(entry -> {
                    JsonObject enchantment = new JsonObject();
                    enchantment.addProperty("id", holderId(entry.getKey()));
                    enchantment.addProperty("level", entry.getIntValue());
                    result.add(enchantment);
                });
        return result;
    }

    private JsonElement encodePotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return JsonNull.INSTANCE;
        }

        JsonObject result = new JsonObject();
        contents.potion().ifPresentOrElse(
                potion -> result.addProperty("base_potion", holderId(potion)),
                () -> result.add("base_potion", JsonNull.INSTANCE)
        );
        contents.customColor().ifPresentOrElse(
                color -> result.addProperty("custom_color", color),
                () -> result.add("custom_color", JsonNull.INSTANCE)
        );
        contents.customName().ifPresentOrElse(
                name -> result.addProperty("custom_name", name),
                () -> result.add("custom_name", JsonNull.INSTANCE)
        );

        JsonArray effects = new JsonArray();
        for (MobEffectInstance effect : contents.getAllEffects()) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("id", holderId(effect.getEffect()));
            encoded.addProperty("description_id", effect.getDescriptionId());
            encoded.addProperty("amplifier", effect.getAmplifier());
            encoded.addProperty("level", effect.getAmplifier() + 1);
            encoded.addProperty("duration_ticks", effect.getDuration());
            encoded.addProperty("infinite", effect.isInfiniteDuration());
            encoded.addProperty("ambient", effect.isAmbient());
            encoded.addProperty("visible", effect.isVisible());
            effects.add(encoded);
        }
        result.add("effects", effects);
        return result;
    }

    private JsonElement encodeComponent(Component component,
                                        RegistryOps<JsonElement> jsonOps,
                                        List<String> errors) {
        return valueOrFallback(
                ComponentSerialization.CODEC.encodeStart(jsonOps, component),
                errors,
                new JsonPrimitive(component.getString())
        );
    }

    private <T> T valueOrFallback(DataResult<T> result,
                                  List<String> errors,
                                  T fallback) {
        return result.resultOrPartial(errors::add).orElse(fallback);
    }

    private JsonObject sessionRecord(String type, Minecraft client) {
        JsonObject record = baseRecord(type);
        record.addProperty("schema_version", SCHEMA_VERSION);
        record.addProperty("minecraft_version", SharedConstants.getCurrentVersion().name());
        record.addProperty("output_file", OUTPUT_FILE);

        ServerData server = client.getCurrentServer();
        if (server != null) {
            record.addProperty("server_name", server.name);
            record.addProperty("server_address", server.ip);
        }
        return record;
    }

    private JsonObject baseRecord(String type) {
        JsonObject record = new JsonObject();
        record.addProperty("type", type);
        record.addProperty("captured_at", Instant.now().toString());
        return record;
    }

    private JsonArray strings(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private String menuTypeId(AbstractContainerMenu menu) {
        if (menu.getType() == null) {
            return "unregistered";
        }
        var id = BuiltInRegistries.MENU.getKey(menu.getType());
        return id == null ? "unregistered" : id.toString();
    }

    private String itemId(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "unregistered" : id.toString();
    }

    private String componentId(DataComponentType<?> type) {
        var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return id == null ? "unregistered@" + Integer.toHexString(System.identityHashCode(type)) : id.toString();
    }

    private String holderId(Holder<?> holder) {
        return holder.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElseGet(() -> "direct:" + holder.value());
    }

    private Path resolveOutputPath(Minecraft client) {
        return client.gameDirectory.toPath().resolve("logs").resolve(OUTPUT_FILE);
    }

    private void writeRecord(JsonObject record) throws IOException {
        if (this.outputPath == null) {
            throw new IOException("Output path is not initialized");
        }
        Files.writeString(
                this.outputPath,
                GSON.toJson(record) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private boolean fail(String message, Exception exception) {
        this.lastError = message + ": " + exception.getMessage();
        LOGGER.error(message, exception);
        return false;
    }

    private boolean failAndStop(String message, Exception exception) {
        this.capturing = false;
        return fail(message, exception);
    }
}
