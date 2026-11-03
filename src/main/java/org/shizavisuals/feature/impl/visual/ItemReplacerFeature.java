package org.shizavisuals.feature.impl.visual;

import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.ModeSetting;

import java.util.List;

public final class ItemReplacerFeature extends Feature implements MinecraftContext {
    public static final List<String> WEAPONS = List.of(
            "Abominable Blade",
            "Abominable Great Saber",
            "Abominable Scythe",
            "Acidic Cleaver",
            "Amethyst Shuriken",
            "Ancient Royal Great Sword",
            "Aquatic Sacred Blade",
            "Arcanethyst",
            "Ashura's Blade",
            "Awakened Lichblade",
            "Blood Edge",
            "Bloody Death",
            "Bramblethorn",
            "Brimstone Claymore",
            "Carian Knight's Sword",
            "Chrono Blade",
            "Corrupted Mythic Blade",
            "Creation Splitter",
            "Crescent Rose",
            "Cyber Katana",
            "Cyber Mantis Blade",
            "Cyber Sword",
            "Cybernetic Chainsaw Blade",
            "Cybernetic Katana",
            "Cybernetic Knife",
            "Dainsleif",
            "Dark Blade",
            "Dark Cleaver",
            "Death Knight's Dagger",
            "Death Knight's Sword",
            "Demigod's Unholy Blade",
            "Demigod's Unholy Halberd",
            "Demon Lord's Great Axe",
            "Demon Lord's Sword",
            "Demonic Blade",
            "Demonic Cleaver",
            "Divine Axe Rhitta",
            "Divine Justice",
            "Divine Punisher",
            "Divine Reaper",
            "Dragon Slaying blade",
            "Edge Of The Astral Plane",
            "Emberblade",
            "Enigma",
            "Epic Sword",
            "Estoc",
            "Fallen God's Spear",
            "Fallen God's Sword",
            "Floral Longsword",
            "Floral Sabre",
            "Forest Guardian's Glaive",
            "Frost Axe",
            "Frost Blade",
            "Frost Scythe",
            "Hearthflame",
            "Hero Sword",
            "Holy Moonlight Sword",
            "Hornet's Needle",
            "Icewhisper",
            "Jade Halberd",
            "Katana",
            "Legendary Sword",
            "Longsword",
            "Magi Scythe",
            "Masamune",
            "Mjolnir",
            "Molten Blade",
            "Molten Sword",
            "Muramasa",
            "Mystical Spellblade",
            "Mythic Blade",
            "Ocean's Rage",
            "Partisan",
            "Pharaoh's Treasure",
            "Pheonix Grace",
            "Plague Longsword",
            "Power Fuse Hammer",
            "Power Fuse Sword",
            "Requiem of the Ninth Abyss",
            "Ribbon Cleaver",
            "Righteous Relic",
            "Rivers Of Blood",
            "Royal Chakram",
            "Royal Rapier",
            "Sabre",
            "Scissor Blade",
            "Sculk Cleaver",
            "Sculk Scythe",
            "Sculk Sword",
            "Sentinel's Will",
            "Silverine Blade",
            "Soul Claws",
            "Soul Collector",
            "Soul Devourer",
            "Soul Edge",
            "Soul Harvester",
            "Soul Stealer",
            "Soulrender",
            "Star's Edge",
            "Steel Sword",
            "Stop Sign",
            "Storm Bringer",
            "Storm's Edge",
            "Sunbreak",
            "Tengen's Blade",
            "Terra Blade",
            "Thousand Demon Daggers",
            "Thunder Bringer",
            "Thunderbrand",
            "True Excalibur",
            "Vampiric Needle",
            "Wakizashi",
            "Watcher Claymore",
            "Watching Warglaive",
            "Waxweaver",
            "Whisperwind",
            "Wickpiercer",
            "Wraith Scythe",
            "Yoru"
    );

    public final BooleanSetting selfOnly = register(new BooleanSetting(
            "Self Only", true
    ).configKey("render.itemreplacer.selfonly"));
    public final ModeSetting weapon = register(new ModeSetting(
            "Weapon",
            WEAPONS.get(0),
            WEAPONS.toArray(String[]::new)
    ).configKey("render.itemreplacer.weapon"));

    public ItemReplacerFeature() {
        super(
                "Item Replacer",
                "Replaces sword models with custom weapons",
                FeatureCategory.VISUAL,
                0
        );
    }

    public static ItemReplacerFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(ItemReplacerFeature.class);
    }

    public static boolean isSword(ItemStack stack) {
        return stack != null && (stack.is(Items.DIAMOND_SWORD) || stack.is(Items.NETHERITE_SWORD)
                || stack.is(Items.IRON_SWORD) || stack.is(Items.GOLDEN_SWORD)
                || stack.is(Items.STONE_SWORD) || stack.is(Items.WOODEN_SWORD));
    }

    /** Self Only gate, checked before the stack is touched at all. */
    public boolean shouldApply(ItemOwner itemOwner) {
        if (!this.selfOnly.getValue()) {
            return true;
        }
        if (MinecraftContext.mc == null || MinecraftContext.mc.player == null) {
            return false;
        }
        return itemOwner != null && itemOwner.asLivingEntity() == MinecraftContext.mc.player;
    }

    /**
     * Recognises the shape this feature used to write into stacks before it
     * stopped mutating them: one string entry, everything else empty, and the
     * string is one of the 119 weapon names. Such leftovers are dropped instead
     * of being restored, otherwise swords keep the custom texture forever.
     */
    public static boolean isInjectedModelData(CustomModelData data) {
        return data != null
                && data.floats().isEmpty()
                && data.flags().isEmpty()
                && data.colors().isEmpty()
                && data.strings().size() == 1
                && WEAPONS.contains(data.strings().getFirst());
    }

    /**
     * Applied from the item model mixin before the renderer resolves the model.
     * The mixin restores the previous CUSTOM_MODEL_DATA on RETURN, so this never
     * has to be undone and never reaches the saved item.
     */
    public void apply(ItemStack stack, Level level, ItemOwner itemOwner) {
        if (!isSword(stack)) {
            return;
        }
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(), List.of(), List.of(this.weapon.getValue()), List.of()
        ));
    }
}