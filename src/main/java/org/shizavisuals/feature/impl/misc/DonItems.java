package org.shizavisuals.feature.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class DonItems {
    private static final Pattern LEGACY_FORMATTING = Pattern.compile("(?i)§[0-9a-fk-orx]");
    private static final Pattern DECORATIVE_X = Pattern.compile("(?iu)(^|\\s)[xх]{2,}(?=\\s|$)");
    private static final Pattern NON_NAME_CHARACTER = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final double AMOUNT_EPSILON = 1.0E-6D;

    private DonItems() {
    }

    private static final class Index {
        private static final Map<Server, List<DonItem>> ITEMS_BY_SERVER;
        private static final Map<Server, Map<String, List<DonItem>>> ITEMS_BY_BASE_ITEM;
        private static final Map<String, FunTime> FUNTIME_BY_ID;

        static {
            EnumMap<Server, List<DonItem>> byServer = new EnumMap<>(Server.class);
            byServer.put(Server.FUNTIME, List.copyOf(Arrays.asList(FunTime.values())));
            byServer.put(Server.HOLYWORLD, List.copyOf(Arrays.asList(HolyWorld.values())));
            ITEMS_BY_SERVER = Collections.unmodifiableMap(byServer);

            EnumMap<Server, Map<String, List<DonItem>>> byBaseItem = new EnumMap<>(Server.class);
            for (Map.Entry<Server, List<DonItem>> entry : byServer.entrySet()) {
                Map<String, List<DonItem>> index = new LinkedHashMap<>();
                for (DonItem item : entry.getValue()) {
                    index.computeIfAbsent(item.baseItemId(), ignored -> new ArrayList<>()).add(item);
                }
                index.replaceAll((ignored, items) -> List.copyOf(items));
                byBaseItem.put(entry.getKey(), Collections.unmodifiableMap(index));
            }
            ITEMS_BY_BASE_ITEM = Collections.unmodifiableMap(byBaseItem);

            Map<String, FunTime> byFunTimeId = new HashMap<>();
            for (FunTime item : FunTime.values()) {
                if (item.rule.stableId != null) {
                    FunTime previous = byFunTimeId.put(item.rule.stableId, item);
                    if (previous != null) {
                        throw new IllegalStateException("Duplicate FunTime item id: " + item.rule.stableId);
                    }
                }
            }
            FUNTIME_BY_ID = Collections.unmodifiableMap(byFunTimeId);
        }

        private Index() {
        }
    }

    public enum Server {
        FUNTIME("funtime", "spookytime"),
        HOLYWORLD("holyworld");

        private final Set<String> addressMarkers;

        Server(String... addressMarkers) {
            this.addressMarkers = Set.of(addressMarkers);
        }

        public boolean matchesAddress(String address) {
            if (address == null || address.isBlank()) {
                return false;
            }
            String normalized = address.toLowerCase(Locale.ROOT);
            return this.addressMarkers.stream().anyMatch(normalized::contains);
        }

        public static Optional<Server> fromAddress(String address) {
            return Arrays.stream(values())
                    .filter(server -> server.matchesAddress(address))
                    .findFirst();
        }
    }

    public enum Category {
        WEAPON,
        ARMOR,
        COMBAT_ITEM,
        POTION,
        ARROW,
        TALISMAN,
        SPHERE,
        EXPLOSIVE,
        CUSTOM_ENCHANTMENT,
        RUNE,
        BACKPACK,
        TOOL,
        MISC
    }

    public interface DonItem {
        Server server();

        Category category();

        String displayName();

        String baseItemId();

        default boolean matches(ItemStack stack) {
            return DonItems.matches(stack, this);
        }
    }

    public enum FunTime implements DonItem {
        CRUSHER_TRIDENT(Category.WEAPON, "Трезубец Крушителя", "minecraft:trident", ft("krush-trident")),
        CRUSHER_MACE(Category.WEAPON, "Булава Крушителя", "minecraft:mace", ft("krush-mace")),
        DISORIENTATION(Category.COMBAT_ITEM, "Дезориентация", "minecraft:ender_eye", ft("desorientation")),
        SHEER_DUST(Category.COMBAT_ITEM, "Явная пыль", "minecraft:sugar", ft("sheerdust")),
        GODS_AURA(Category.COMBAT_ITEM, "Божья аура", "minecraft:phantom_membrane", ft("godsaura")),
        CRUSHER_SWORD(Category.WEAPON, "Меч Крушителя", "minecraft:netherite_sword", ft("krush-sword")),
        SATAN_SWORD(Category.WEAPON, "Меч Сатаны", "minecraft:netherite_sword", ft("satan-sword")),
        KATANA(Category.WEAPON, "Катана", "minecraft:netherite_sword", ft("katana")),
        FREEZE_SNOWBALL(Category.COMBAT_ITEM, "Снежок заморозка", "minecraft:snowball", ft("freezeball")),
        TRAP(Category.COMBAT_ITEM, "Трапка", "minecraft:netherite_scrap", ft("trap")),
        CRUSHER_CROSSBOW(Category.WEAPON, "Арбалет Крушителя", "minecraft:crossbow", ft("krush-crossbow")),
        CRUSHER_BOW(Category.WEAPON, "Лук Крушителя", "minecraft:bow", ft("krush-bow")),
        SATAN_BOW(Category.WEAPON, "Лук Сатаны", "minecraft:bow", ft("satan-bow")),
        PHANTOM_BOW(Category.WEAPON, "Лук Фантома", "minecraft:bow", ft("phantom-bow")),
        STRATUM(Category.COMBAT_ITEM, "Пласт", "minecraft:dried_kelp", ft("stratum")),
        FIERY_TORNADO(Category.COMBAT_ITEM, "Огненный смерч", "minecraft:fire_charge", ft("fierytornado")),

        EMERALD_HELMET(Category.ARMOR, "Изумрудный шлем", "minecraft:diamond_helmet", ft("emerald-helmet")),
        EMERALD_CHESTPLATE(Category.ARMOR, "Изумрудный нагрудник", "minecraft:diamond_chestplate", ft("emerald-chestplate")),
        EMERALD_LEGGINGS(Category.ARMOR, "Изумрудные поножи", "minecraft:diamond_leggings", ft("emerald-leggings")),
        EMERALD_BOOTS(Category.ARMOR, "Изумрудные ботинки", "minecraft:diamond_boots", ft("emerald-boots")),
        SATAN_HELMET(Category.ARMOR, "Шлем Сатаны", "minecraft:netherite_helmet", ft("satan-helmet")),
        SATAN_CHESTPLATE(Category.ARMOR, "Нагрудник Сатаны", "minecraft:netherite_chestplate", ft("satan-chestplate")),
        SATAN_LEGGINGS(Category.ARMOR, "Поножи Сатаны", "minecraft:netherite_leggings", ft("satan-leggings")),
        SATAN_BOOTS(Category.ARMOR, "Ботинки Сатаны", "minecraft:netherite_boots", ft("satan-boots")),
        CRUSHER_HELMET(Category.ARMOR, "Шлем Крушителя", "minecraft:netherite_helmet", ft("krush-helmet")),
        CRUSHER_CHESTPLATE(Category.ARMOR, "Нагрудник Крушителя", "minecraft:netherite_chestplate", ft("krush-chestplate")),
        CRUSHER_LEGGINGS(Category.ARMOR, "Поножи Крушителя", "minecraft:netherite_leggings", ft("krush-leggings")),
        CRUSHER_BOOTS(Category.ARMOR, "Ботинки Крушителя", "minecraft:netherite_boots", ft("krush-boots")),

        ENHANCED_STRENGTH_POTION(
                Category.POTION,
                "Зелье силы",
                "minecraft:potion",
                named().effects(effect("minecraft:strength", 3, 6000))
        ),
        ENHANCED_INVISIBILITY_POTION(
                Category.POTION,
                "Зелье невидимости",
                "minecraft:potion",
                named().effects(effect("minecraft:invisibility", 1, 18000))
        ),
        ENHANCED_SPEED_POTION(
                Category.POTION,
                "Зелье скорости",
                "minecraft:potion",
                named().effects(effect("minecraft:speed", 3, 7200))
        ),
        ENHANCED_LEAPING_POTION(
                Category.POTION,
                "Зелье прыгучести",
                "minecraft:potion",
                named().effects(effect("minecraft:jump_boost", 1, 7200))
        ),
        ENHANCED_REGENERATION_POTION(
                Category.POTION,
                "Зелье регенерации",
                "minecraft:potion",
                named().effects(effect("minecraft:regeneration", 1, 600))
        ),
        ENHANCED_NIGHT_VISION_POTION(
                Category.POTION,
                "Зелье ночного зрения",
                "minecraft:potion",
                named().effects(effect("minecraft:night_vision", 1, 18000))
        ),
        ENHANCED_FIRE_RESISTANCE_POTION(
                Category.POTION,
                "Зелье огнестойкости",
                "minecraft:potion",
                named().effects(effect("minecraft:fire_resistance", 1, 18000))
        ),
        ENHANCED_WATER_BREATHING_POTION(
                Category.POTION,
                "Зелье водного дыхания",
                "minecraft:potion",
                named().effects(effect("minecraft:water_breathing", 1, 18000))
        ),
        POPPER(
                Category.POTION,
                "Хлопушка",
                "minecraft:splash_potion",
                ft("potion-popper").effects(
                        effect("minecraft:slowness", 10, 200),
                        effect("minecraft:speed", 5, 400),
                        effect("minecraft:blindness", 10, 100),
                        effect("minecraft:glowing", 1, 3600)
                )
        ),
        HOLY_WATER(
                Category.POTION,
                "Святая вода",
                "minecraft:splash_potion",
                ft("potion-holy-water").effects(
                        effect("minecraft:regeneration", 2, 900),
                        effect("minecraft:invisibility", 2, 12000),
                        effect("minecraft:instant_health", 2, 0)
                )
        ),
        RAGE_POTION(
                Category.POTION,
                "Зелье Гнева",
                "minecraft:splash_potion",
                ft("potion-rage").effects(
                        effect("minecraft:strength", 5, 600),
                        effect("minecraft:slowness", 4, 600)
                )
        ),
        PALADIN_POTION(
                Category.POTION,
                "Зелье Палладина",
                "minecraft:splash_potion",
                ft("potion-paladin").effects(
                        effect("minecraft:resistance", 1, 12000),
                        effect("minecraft:fire_resistance", 1, 12000),
                        effect("minecraft:health_boost", 3, 1200),
                        effect("minecraft:invisibility", 1, 18000)
                )
        ),
        ASSASSIN_POTION(
                Category.POTION,
                "Зелье Ассасина",
                "minecraft:splash_potion",
                ft("potion-assassin").effects(
                        effect("minecraft:strength", 4, 1200),
                        effect("minecraft:speed", 3, 6000),
                        effect("minecraft:haste", 1, 1200),
                        effect("minecraft:instant_damage", 2, 0)
                )
        ),
        RADIATION_POTION(
                Category.POTION,
                "Зелье Радиации",
                "minecraft:splash_potion",
                ft("potion-radiation").effects(
                        effect("minecraft:poison", 2, 1200),
                        effect("minecraft:wither", 2, 1200),
                        effect("minecraft:slowness", 3, 1800),
                        effect("minecraft:hunger", 5, 1200),
                        effect("minecraft:glowing", 1, 2400)
                )
        ),
        DROWSINESS_POTION(
                Category.POTION,
                "Снотворное",
                "minecraft:splash_potion",
                ft("potion-drowsiness").effects(
                        effect("minecraft:weakness", 2, 1800),
                        effect("minecraft:mining_fatigue", 2, 200),
                        effect("minecraft:wither", 3, 1800),
                        effect("minecraft:blindness", 1, 200)
                )
        ),

        AGONY_ARROW(
                Category.ARROW,
                "Мучительная стрела",
                "minecraft:tipped_arrow",
                ft("arrow-agony").effects(
                        effect("minecraft:slowness", 3, 800),
                        effect("minecraft:wither", 3, 800),
                        effect("minecraft:poison", 3, 800)
                )
        ),
        ZEUS_ARROW(
                Category.ARROW,
                "Стрела Зевса",
                "minecraft:tipped_arrow",
                ft("arrow-zeus").effects(
                        effect("minecraft:instant_damage", 2, 0),
                        effect("minecraft:slowness", 3, 480)
                )
        ),
        BLOOD_ARROW(
                Category.ARROW,
                "Кровавая стрела",
                "minecraft:tipped_arrow",
                ft("arrow-blood").effects(
                        effect("minecraft:weakness", 3, 480),
                        effect("minecraft:blindness", 1, 320),
                        effect("minecraft:mining_fatigue", 1, 320),
                        effect("minecraft:nausea", 1, 800)
                )
        ),
        FREEZE_ARROW(
                Category.ARROW,
                "Стрела обледенения",
                "minecraft:tipped_arrow",
                ft("arrow-freeze").effects(
                        effect("minecraft:slowness", 10, 800),
                        effect("minecraft:mining_fatigue", 3, 320)
                )
        ),
        LIGHT_ARROW(
                Category.ARROW,
                "Световая стрела",
                "minecraft:tipped_arrow",
                ft("arrow-light").effects(
                        effect("minecraft:speed", 5, 800),
                        effect("minecraft:haste", 3, 800),
                        effect("minecraft:blindness", 1, 480)
                )
        ),
        REJUVENATION_ARROW(
                Category.ARROW,
                "Стрела терапии",
                "minecraft:tipped_arrow",
                ft("arrow-rejuvenation").effects(
                        effect("minecraft:slowness", 5, 1120),
                        effect("minecraft:weakness", 1, 1120),
                        effect("minecraft:instant_health", 1, 0)
                )
        ),

        DARKNESS_TALISMAN(Category.TALISMAN, "Талисман Мрака", "minecraft:totem_of_undying", ft("tal-mraka").model(1.0F)),
        WHIRLWIND_TALISMAN(Category.TALISMAN, "Талисман Вихря", "minecraft:totem_of_undying", ft("tal-vihrya").model(2.0F)),
        DEMON_TALISMAN(Category.TALISMAN, "Талисман Демона", "minecraft:totem_of_undying", ft("tal-demona").model(3.0F)),
        DISCORD_TALISMAN(Category.TALISMAN, "Талисман Раздора", "minecraft:totem_of_undying", ft("tal-razdora").model(4.0F)),
        FURY_TALISMAN(Category.TALISMAN, "Талисман Ярости", "minecraft:totem_of_undying", ft("tal-yarosti").model(5.0F)),
        CRUSHER_TALISMAN(Category.TALISMAN, "Талисман Крушителя", "minecraft:totem_of_undying", ft("tal-krush").model(6.0F)),
        PUNISHER_TALISMAN(Category.TALISMAN, "Талисман Карателя", "minecraft:totem_of_undying", named().model(7.0F)),
        TYRANT_TALISMAN(Category.TALISMAN, "Талисман Тирана", "minecraft:totem_of_undying", ft("tal-tirana").model(8.0F)),

        CHAOS_SPHERE(
                Category.SPHERE,
                "Сфера Хаоса",
                "minecraft:player_head",
                ft("sphere-haosa").attributes(
                        attribute("minecraft:max_health", -4.0D, "add_value"),
                        attribute("minecraft:armor", 1.5D, "add_value"),
                        attribute("minecraft:attack_damage", 2.5D, "add_value"),
                        attribute("minecraft:movement_speed", 0.07D, "add_multiplied_base"),
                        attribute("minecraft:attack_speed", 0.13D, "add_multiplied_base"),
                        attribute("minecraft:gravity", 0.09D, "add_multiplied_base")
                )
        ),
        SATYR_SPHERE(
                Category.SPHERE,
                "Сфера Сатира",
                "minecraft:player_head",
                ft("sphere-satira").attributes(
                        attribute("minecraft:attack_damage", 2.0D, "add_value"),
                        attribute("minecraft:jump_strength", -0.1D, "add_multiplied_base"),
                        attribute("minecraft:attack_speed", 0.15D, "add_multiplied_base")
                )
        ),
        BEAST_SPHERE(
                Category.SPHERE,
                "Сфера Бестии",
                "minecraft:player_head",
                ft("sphere-bestia").attributes(
                        attribute("minecraft:armor", 1.0D, "add_value"),
                        attribute("minecraft:max_health", 4.0D, "add_value"),
                        attribute("minecraft:movement_speed", 0.1D, "add_multiplied_base"),
                        attribute("minecraft:attack_speed", 0.1D, "add_multiplied_base")
                )
        ),
        ARES_SPHERE(
                Category.SPHERE,
                "Сфера Ареса",
                "minecraft:player_head",
                ft("sphere-aresa").attributes(
                        attribute("minecraft:attack_damage", 6.0D, "add_value"),
                        attribute("minecraft:armor", -2.0D, "add_value"),
                        attribute("minecraft:max_health", -2.0D, "add_value")
                )
        ),
        HYDRA_SPHERE(
                Category.SPHERE,
                "Сфера Гидры",
                "minecraft:player_head",
                ft("sphere-gidra").attributes(
                        attribute("minecraft:max_health", 4.0D, "add_value"),
                        attribute("minecraft:armor", 2.0D, "add_value"),
                        attribute("minecraft:submerged_mining_speed", 0.5D, "add_multiplied_base"),
                        attribute("minecraft:oxygen_bonus", 0.5D, "add_multiplied_base")
                )
        ),
        ICARUS_SPHERE(
                Category.SPHERE,
                "Сфера Икара",
                "minecraft:player_head",
                ft("sphere-ikara").attributes(
                        attribute("minecraft:attack_damage", 2.0D, "add_value"),
                        attribute("minecraft:max_health", 2.0D, "add_value")
                )
        ),
        ERIS_SPHERE(
                Category.SPHERE,
                "Сфера Эрида",
                "minecraft:player_head",
                ft("sphere-erida").attributes(
                        attribute("minecraft:luck", 1.0D, "add_value"),
                        attribute("minecraft:max_health", 2.0D, "add_value"),
                        attribute("minecraft:block_interaction_range", 1.0D, "add_value")
                )
        );

        private final Category category;
        private final String displayName;
        private final String baseItemId;
        private final MatchRule rule;

        FunTime(Category category, String displayName, String baseItemId, MatchRule rule) {
            this.category = category;
            this.displayName = displayName;
            this.baseItemId = baseItemId;
            this.rule = rule.alias(displayName);
        }

        @Override
        public Server server() {
            return Server.FUNTIME;
        }

        @Override
        public Category category() {
            return this.category;
        }

        @Override
        public String displayName() {
            return this.displayName;
        }

        @Override
        public String baseItemId() {
            return this.baseItemId;
        }

        public Optional<String> stableId() {
            return Optional.ofNullable(this.rule.stableId);
        }
    }

    public enum HolyWorld implements DonItem {
        WINNER_POTION(
                Category.POTION,
                "Зелье победителя",
                "minecraft:potion",
                named().potionColor(1834794).loreEffects(
                        loreEffect("скорость", "III", "8:00"),
                        loreEffect("сила", "III", "8:00"),
                        loreEffect("спешка", "II", "1:30"),
                        loreEffect("невидимость", "II", "8:00"),
                        loreEffect("сопротивление", "II"),
                        loreEffect("регенерация", "II")
                )
        ),
        ENHANCED_STRENGTH_POTION(
                Category.POTION,
                "Улучшенное зелье силы",
                "minecraft:potion",
                named().potionColor(16718619).loreEffects(
                        loreEffect("сила", null, "6:00")
                )
        ),
        ENHANCED_SPEED_POTION(
                Category.POTION,
                "Улучшенное зелье скорости",
                "minecraft:potion",
                named().potionColor(1434111).loreEffects(
                        loreEffect("скорость", null, "3:00", "6:00")
                )
        ),

        EXPLOSIVE_MATERIAL(Category.EXPLOSIVE, "Взрывчатое вещество", "minecraft:clay", named()),
        EXPLOSIVE_TRAP(Category.EXPLOSIVE, "Взрывная трапка", "minecraft:prismarine_shard", named()),
        TRAP(Category.EXPLOSIVE, "Трапка", "minecraft:popped_chorus_fruit", named()),
        TNT_CANNON(
                Category.EXPLOSIVE,
                "Тнт-Пушка",
                "minecraft:dispenser",
                named().numberMarker("PublicBukkitValues/litetntcannon:tnt-cannon", 1.0D)
        ),
        DYNAMITE(Category.EXPLOSIVE, "Динамит", "minecraft:tnt", named()),
        DYNAMITE_A(Category.EXPLOSIVE, "Динамит A", "minecraft:tnt", named()),
        DYNAMITE_B(Category.EXPLOSIVE, "Динамит B", "minecraft:tnt", named()),
        C4(Category.EXPLOSIVE, "С4 Взрывчатка", "minecraft:tnt", named()),
        BLAST_WAVE(Category.EXPLOSIVE, "Разрывная волна", "minecraft:tnt", named()),
        DYNAMITE_B2(Category.EXPLOSIVE, "Динамит Б2", "minecraft:tnt", named()),
        STEALER(Category.EXPLOSIVE, "Стиллер", "minecraft:tnt", named()),
        RELIABLE_STEALER(Category.EXPLOSIVE, "Надёжный стиллер", "minecraft:tnt", named().alias("Надежный стиллер")),
        ICE_WAVE(Category.EXPLOSIVE, "Ледяная волна", "minecraft:tnt", named()),

        FILTER_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Фильтр", "minecraft:enchanted_book", prefix("Фильтр")),
        STUN_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Оглушение", "minecraft:enchanted_book", prefix("Оглушение")),
        SOWING_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Посев", "minecraft:enchanted_book", prefix("Посев")),
        IMPENETRABLE_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Непробиваемый", "minecraft:enchanted_book", prefix("Непробиваемый")),
        INDESTRUCTIBILITY_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Неразрушимость", "minecraft:enchanted_book", prefix("Неразрушимость")),
        EXPERIENCED_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Опытный", "minecraft:enchanted_book", prefix("Опытный")),
        CRITICAL_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Критический", "minecraft:enchanted_book", prefix("Критический")),
        ENCHANTMENT_COMBINER(Category.CUSTOM_ENCHANTMENT, "Объединение зачарований", "minecraft:enchanted_book", named()),
        DRILL_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Бур", "minecraft:enchanted_book", prefix("Бур")),
        RICH_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Богач", "minecraft:enchanted_book", prefix("Богач")),
        AUTO_SMELT_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Автоплавка", "minecraft:enchanted_book", prefix("Автоплавка")),
        LAVA_WALKER_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Лаваход", "minecraft:enchanted_book", prefix("Лаваход")),
        DESTROYER_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Разрушитель", "minecraft:enchanted_book", prefix("Разрушитель")),
        FARMER_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Фермер", "minecraft:enchanted_book", prefix("Фермер")),
        MAGNETISM_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Магнетизм", "minecraft:enchanted_book", named()),
        LUMBERJACK_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Дровосек", "minecraft:enchanted_book", prefix("Дровосек")),
        MEGA_DRILL_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Мега-Бур", "minecraft:enchanted_book", prefix("Мега-Бур")),
        HOMING_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Самонаводка", "minecraft:enchanted_book", prefix("Самонаводка")),
        CRUSHER_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Крушитель", "minecraft:enchanted_book", prefix("Крушитель")),
        DELICATE_ENCHANT(Category.CUSTOM_ENCHANTMENT, "Деликатный", "minecraft:enchanted_book", prefix("Деликатный")),

        COMMON_SPHERE(Category.SPHERE, "Обычная сфера", "minecraft:player_head", named()),
        EPIC_SPHERE(Category.SPHERE, "Эпическая сфера", "minecraft:player_head", named()),
        LEGENDARY_SPHERE(Category.SPHERE, "Легендарная сфера", "minecraft:player_head", named()),
        MYTHIC_SPHERE(Category.SPHERE, "Мифическая сфера", "minecraft:player_head", named()),
        COMMON_TALISMAN(Category.TALISMAN, "Обычный талисман", "minecraft:totem_of_undying", named()),
        EPIC_TALISMAN(Category.TALISMAN, "Эпический талисман", "minecraft:totem_of_undying", named()),
        LEGENDARY_TALISMAN(Category.TALISMAN, "Легендарный талисман", "minecraft:totem_of_undying", named()),
        ETERNITY_TALISMAN(
                Category.TALISMAN,
                "Талисман Eternity",
                "minecraft:totem_of_undying",
                named()
                        .stringMarker("itemServiceId/name", "сфера_eternity")
                        .stringMarker("sphereEffect/name", "Eternity")
        ),

        IMMORTALITY_RUNE(Category.RUNE, "Бессмертие", "minecraft:orange_dye", named()),
        RESTORATION_RUNE(Category.RUNE, "Восстановление", "minecraft:red_dye", named()),

        BACKPACK_I(Category.BACKPACK, "Рюкзак I уровень", "minecraft:pink_shulker_box", named().alias("Рюкзак (I уровень)")),
        BACKPACK_II(Category.BACKPACK, "Рюкзак II уровень", "minecraft:light_blue_shulker_box", named().alias("Рюкзак (II уровень)")),
        BACKPACK_III(Category.BACKPACK, "Рюкзак III уровень", "minecraft:red_shulker_box", named().alias("Рюкзак (III уровень)")),
        BACKPACK_IV(Category.BACKPACK, "Рюкзак IV уровень", "minecraft:magenta_shulker_box", named().alias("Рюкзак (IV уровень)")),
        INFINITY_BACKPACK(Category.BACKPACK, "Рюкзак Infinity", "minecraft:lime_shulker_box", named().alias("Рюкзак Iɴғɪɴɪᴛʏ")),

        SPECIAL_COMPASS(
                Category.MISC,
                "Особый компас",
                "minecraft:compass",
                named().stringMarker("kringeItems/type", "RegionRadar")
        ),
        ENCHANTED_APPLE(Category.MISC, "Зачарованное яблоко", "minecraft:enchanted_golden_apple", named()),
        STAN(Category.COMBAT_ITEM, "Стан", "minecraft:nether_star", named()),
        JAKES_GOLDEN_PICKAXE(Category.TOOL, "Золотая кирка Джейка", "minecraft:golden_pickaxe", named()),
        GOLDEN_SPAWNER(Category.MISC, "Золотой Спавнер", "minecraft:spawner", named()),
        UNIQUE_CLAIM(Category.MISC, "Уникальный приват", "minecraft:ancient_debris", named()),
        ANCIENT_DEBRIS(Category.MISC, "Древние обломки", "minecraft:ancient_debris", named()),
        SUN_HELMET(
                Category.ARMOR,
                "Шлем солнца",
                "minecraft:golden_helmet",
                named().stringMarker("kringeItems/type", "SunHelmet")
        ),
        UNIVERSAL_KEY(Category.MISC, "Универсальный ключ", "minecraft:tripwire_hook", named().model(123433.0F)),
        VEX_SPAWN_EGG(Category.MISC, "Vex Spawn Egg", "minecraft:vex_spawn_egg", named()),
        ETERNITY_SHOVEL(Category.TOOL, "Лопата Eternity", "minecraft:netherite_shovel", named().alias("Лопата ᴇᴛᴇʀɴɪᴛʏ")),
        UNBREAKABLE_ELYTRA(Category.ARMOR, "Нерушимые элитры", "minecraft:elytra", named());

        private final Category category;
        private final String displayName;
        private final String baseItemId;
        private final MatchRule rule;

        HolyWorld(Category category, String displayName, String baseItemId, MatchRule rule) {
            this.category = category;
            this.displayName = displayName;
            this.baseItemId = baseItemId;
            this.rule = rule.alias(displayName);
        }

        @Override
        public Server server() {
            return Server.HOLYWORLD;
        }

        @Override
        public Category category() {
            return this.category;
        }

        @Override
        public String displayName() {
            return this.displayName;
        }

        @Override
        public String baseItemId() {
            return this.baseItemId;
        }
    }

    public static Optional<Server> currentServer() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return server == null ? Optional.empty() : Server.fromAddress(server.ip);
    }

    public static List<DonItem> all(Server server) {
        return Index.ITEMS_BY_SERVER.getOrDefault(server, List.of());
    }

    public static List<DonItem> all(Server server, Category category) {
        if (server == null || category == null) {
            return List.of();
        }
        return all(server).stream()
                .filter(item -> item.category() == category)
                .toList();
    }

    public static Optional<DonItem> find(ItemStack stack) {
        Optional<Server> server = currentServer();
        return server.isPresent() ? find(stack, server.get()) : findAny(stack);
    }

    public static Optional<DonItem> find(ItemStack stack, String serverAddress) {
        return Server.fromAddress(serverAddress).flatMap(server -> find(stack, server));
    }

    public static Optional<FunTime> findFunTime(ItemStack stack) {
        return find(stack, Server.FUNTIME)
                .filter(FunTime.class::isInstance)
                .map(FunTime.class::cast);
    }

    public static Optional<HolyWorld> findHolyWorld(ItemStack stack) {
        return find(stack, Server.HOLYWORLD)
                .filter(HolyWorld.class::isInstance)
                .map(HolyWorld.class::cast);
    }

    public static Optional<DonItem> find(ItemStack stack, Server server) {
        if (stack == null || stack.isEmpty() || server == null) {
            return Optional.empty();
        }

        StackView view = new StackView(stack);
        if (server == Server.FUNTIME) {
            Optional<String> stableId = view.funTimeId();
            if (stableId.isPresent()) {
                FunTime item = Index.FUNTIME_BY_ID.get(stableId.get());
                if (item != null && item.baseItemId().equals(view.baseItemId)) {
                    return Optional.of(item);
                }
            }
        }

        List<DonItem> candidates = Index.ITEMS_BY_BASE_ITEM
                .getOrDefault(server, Map.of())
                .getOrDefault(view.baseItemId, List.of());
        for (DonItem candidate : candidates) {
            if (rule(candidate).strongMatches(view)) {
                return Optional.of(candidate);
            }
        }
        for (DonItem candidate : candidates) {
            if (rule(candidate).nameMatches(view.normalizedName)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static Optional<DonItem> findAny(ItemStack stack) {
        for (Server server : Server.values()) {
            Optional<DonItem> item = find(stack, server);
            if (item.isPresent()) {
                return item;
            }
        }
        return Optional.empty();
    }

    public static boolean matches(ItemStack stack, DonItem expected) {
        if (stack == null || stack.isEmpty() || expected == null) {
            return false;
        }
        if (!expected.baseItemId().equals(baseItemId(stack))) {
            return false;
        }

        StackView view = new StackView(stack);
        MatchRule rule = rule(expected);
        if (expected instanceof FunTime && rule.stableId != null
                && rule.stableId.equals(view.funTimeId().orElse(null))) {
            return true;
        }
        return rule.strongMatches(view) || rule.nameMatches(view.normalizedName);
    }

    public static boolean isDonItem(ItemStack stack) {
        return find(stack).isPresent();
    }

    public static boolean isDonItem(ItemStack stack, Server server) {
        return find(stack, server).isPresent();
    }

    public static Optional<String> funTimeId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return new StackView(stack).funTimeId();
    }

    public static Optional<FunTime> funTimeById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Index.FUNTIME_BY_ID.get(id));
    }

    public static List<PotionEffectProfile> potionProfile(DonItem item) {
        if (item == null) {
            return List.of();
        }
        return rule(item).effects.stream()
                .map(effect -> new PotionEffectProfile(effect.id, effect.level, effect.durationTicks))
                .toList();
    }

    public static boolean hasPotionProfile(DonItem item) {
        if (item == null) {
            return false;
        }
        MatchRule rule = rule(item);
        return !rule.effects.isEmpty() || !rule.loreEffects.isEmpty();
    }

    public static ItemStack displayStack(DonItem item) {
        if (item == null) {
            return ItemStack.EMPTY;
        }

        Item baseItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(item.baseItemId()));
        ItemStack stack = new ItemStack(baseItem);
        MatchRule rule = rule(item);
        Integer color = rule.potionColor;
        List<MobEffectInstance> effects = new ArrayList<>();
        if (!rule.effects.isEmpty()) {
            for (EffectSignature signature : rule.effects) {
                var effect = BuiltInRegistries.MOB_EFFECT.getValue(Identifier.parse(signature.id));
                if (effect != null) {
                    effects.add(new MobEffectInstance(
                            BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                            Math.max(1, signature.durationTicks),
                            Math.max(0, signature.level - 1)
                    ));
                }
            }
            if (color == null) {
                var mixed = PotionContents.getColorOptional(effects);
                if (mixed.isPresent()) {
                    color = mixed.getAsInt();
                }
            }
        }
        if (color != null) {
            stack.set(
                    DataComponents.POTION_CONTENTS,
                    new PotionContents(Optional.empty(), Optional.of(color), List.copyOf(effects), Optional.empty())
            );
        }
        return stack;
    }

    public static Optional<Boolean> matchesPotionProfile(ItemStack stack,
                                                         DonItem item,
                                                         boolean exactLevel,
                                                         boolean exactDuration) {
        if (stack == null || stack.isEmpty() || item == null) {
            return Optional.empty();
        }
        MatchRule rule = rule(item);
        if (!rule.effects.isEmpty()) {
            return Optional.of(effectsMatch(stack, rule.effects, exactLevel, exactDuration));
        }
        if (!rule.loreEffects.isEmpty()) {
            return Optional.of(loreEffectsMatch(stack, rule.loreEffects, exactLevel, exactDuration));
        }
        return Optional.empty();
    }

    public record PotionEffectProfile(String effectId, int level, int durationTicks) {
    }

    public static String normalizedName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return normalizeName(stack.getHoverName().getString());
    }

    public static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        normalized = LEGACY_FORMATTING.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT).replace('ё', 'е');
        normalized = NON_NAME_CHARACTER.matcher(normalized).replaceAll(" ");
        normalized = DECORATIVE_X.matcher(normalized).replaceAll(" ");
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

    private static MatchRule rule(DonItem item) {
        if (item instanceof FunTime funTime) {
            return funTime.rule;
        }
        if (item instanceof HolyWorld holyWorld) {
            return holyWorld.rule;
        }
        throw new IllegalArgumentException("Unsupported DonItem implementation: " + item.getClass().getName());
    }

    private static String baseItemId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "unregistered" : id.toString();
    }

    private static String holderId(Holder<?> holder) {
        return holder.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("");
    }

    private static MatchRule ft(String stableId) {
        return new MatchRule(stableId);
    }

    private static MatchRule named() {
        return new MatchRule(null);
    }

    private static MatchRule prefix(String value) {
        return named().prefix(value);
    }

    private static EffectSignature effect(String id, int level, int durationTicks) {
        return new EffectSignature(id, level, durationTicks);
    }

    private static LoreEffectSignature loreEffect(String name, String level, String... durations) {
        return new LoreEffectSignature(
                normalizeName(name),
                level == null ? null : normalizeName(level),
                Arrays.stream(durations).map(DonItems::normalizeName).toList()
        );
    }

    private static AttributeSignature attribute(String id, double amount, String operation) {
        return new AttributeSignature(id, amount, operation);
    }

    private static final class StackView {
        private final ItemStack stack;
        private final String baseItemId;
        private final String normalizedName;
        private final CompoundTag customData;

        private StackView(ItemStack stack) {
            this.stack = stack;
            this.baseItemId = DonItems.baseItemId(stack);
            this.normalizedName = normalizeName(stack.getHoverName().getString());
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            this.customData = customData == null ? new CompoundTag() : customData.copyTag();
        }

        private Optional<String> funTimeId() {
            return this.customData
                    .getCompound("PublicBukkitValues")
                    .flatMap(values -> values.getString("minecraft:ftid"))
                    .filter(value -> !value.isBlank());
        }
    }

    private static final class MatchRule {
        private final String stableId;
        private final Set<String> aliases = new LinkedHashSet<>();
        private final Set<String> prefixes = new LinkedHashSet<>();
        private final List<NbtMarker> markers = new ArrayList<>();
        private Float model;
        private Integer potionColor;
        private List<EffectSignature> effects = List.of();
        private List<LoreEffectSignature> loreEffects = List.of();
        private List<AttributeSignature> attributes = List.of();

        private MatchRule(String stableId) {
            this.stableId = stableId;
        }

        private MatchRule alias(String... values) {
            for (String value : values) {
                String normalized = normalizeName(value);
                if (!normalized.isEmpty()) {
                    this.aliases.add(normalized);
                }
            }
            return this;
        }

        private MatchRule prefix(String... values) {
            for (String value : values) {
                String normalized = normalizeName(value);
                if (!normalized.isEmpty()) {
                    this.prefixes.add(normalized);
                }
            }
            return this;
        }

        private MatchRule model(float value) {
            this.model = value;
            return this;
        }

        private MatchRule potionColor(int value) {
            this.potionColor = value;
            return this;
        }

        private MatchRule effects(EffectSignature... values) {
            this.effects = List.of(values);
            return this;
        }

        private MatchRule loreEffects(LoreEffectSignature... values) {
            this.loreEffects = List.of(values);
            return this;
        }

        private MatchRule attributes(AttributeSignature... values) {
            this.attributes = List.of(values);
            return this;
        }

        private MatchRule stringMarker(String path, String value) {
            this.markers.add(NbtMarker.string(path, value));
            return this;
        }

        private MatchRule numberMarker(String path, double value) {
            this.markers.add(NbtMarker.number(path, value));
            return this;
        }

        private boolean nameMatches(String normalizedName) {
            if (this.aliases.contains(normalizedName)) {
                return true;
            }
            for (String prefix : this.prefixes) {
                if (normalizedName.equals(prefix) || normalizedName.startsWith(prefix + " ")) {
                    return true;
                }
            }
            return false;
        }

        private boolean strongMatches(StackView view) {
            if (this.stableId != null && this.stableId.equals(view.funTimeId().orElse(null))) {
                return true;
            }
            if (this.model != null && modelMatches(view.stack, view.customData, this.model)) {
                return true;
            }
            if (this.potionColor != null && potionColorMatches(view.stack, this.potionColor)) {
                return true;
            }
            if (!this.effects.isEmpty() && effectsMatch(view.stack, this.effects)) {
                return true;
            }
            if (!this.attributes.isEmpty() && attributesMatch(view.stack, this.attributes)) {
                return true;
            }
            for (NbtMarker marker : this.markers) {
                if (marker.matches(view.customData)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record EffectSignature(String id, int level, int durationTicks) {
        private boolean matches(MobEffectInstance effect) {
            return matches(effect, true, true);
        }

        private boolean matches(MobEffectInstance effect, boolean exactLevel, boolean exactDuration) {
            return this.id.equals(holderId(effect.getEffect()))
                    && (!exactLevel || this.level == effect.getAmplifier() + 1)
                    && (!exactDuration || this.durationTicks == effect.getDuration());
        }
    }

    private record LoreEffectSignature(String name, String level, List<String> durations) {
        private LoreEffectSignature {
            durations = List.copyOf(durations);
        }

        private boolean matches(String normalizedLine, boolean exactLevel, boolean exactDuration) {
            if (!normalizedLine.contains(this.name)) {
                return false;
            }
            if (exactLevel && this.level != null && !normalizedLine.contains(this.level)) {
                return false;
            }
            if (exactDuration && !this.durations.isEmpty()
                    && this.durations.stream().noneMatch(normalizedLine::contains)) {
                return false;
            }
            return true;
        }
    }

    private record AttributeSignature(String id, double amount, String operation) {
        private boolean matches(ItemAttributeModifiers.Entry entry) {
            return this.id.equals(holderId(entry.attribute()))
                    && Math.abs(this.amount - entry.modifier().amount()) <= AMOUNT_EPSILON
                    && this.operation.equals(entry.modifier().operation().getSerializedName());
        }
    }

    private record NbtMarker(List<String> path, String stringValue, Double numberValue) {
        private static NbtMarker string(String path, String value) {
            return new NbtMarker(splitPath(path), value, null);
        }

        private static NbtMarker number(String path, double value) {
            return new NbtMarker(splitPath(path), null, value);
        }

        private boolean matches(CompoundTag root) {
            if (this.path.isEmpty()) {
                return false;
            }

            CompoundTag current = root;
            for (int index = 0; index < this.path.size() - 1; index++) {
                Optional<CompoundTag> child = current.getCompound(this.path.get(index));
                if (child.isEmpty()) {
                    return false;
                }
                current = child.get();
            }

            String key = this.path.getLast();
            if (this.stringValue != null) {
                return current.getString(key).map(this.stringValue::equals).orElse(false);
            }
            if (this.numberValue != null) {
                return current.getDouble(key)
                        .map(value -> Math.abs(value - this.numberValue) <= AMOUNT_EPSILON)
                        .orElse(false);
            }
            return false;
        }

        private static List<String> splitPath(String path) {
            if (path == null || path.isBlank()) {
                return List.of();
            }
            return Arrays.stream(path.split("/"))
                    .filter(part -> !part.isBlank())
                    .toList();
        }
    }

    private static boolean modelMatches(ItemStack stack, CompoundTag customData, float expected) {
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model != null && !model.floats().isEmpty()
                && Math.abs(model.getFloat(0) - expected) <= AMOUNT_EPSILON) {
            return true;
        }
        return customData.getFloat("CustomModelData")
                .map(value -> Math.abs(value - expected) <= AMOUNT_EPSILON)
                .orElse(false);
    }

    private static boolean potionColorMatches(ItemStack stack, int expected) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.customColor().filter(color -> color == expected).isPresent();
    }

    private static boolean effectsMatch(ItemStack stack, List<EffectSignature> expected) {
        return effectsMatch(stack, expected, true, true);
    }

    private static boolean effectsMatch(ItemStack stack,
                                        List<EffectSignature> expected,
                                        boolean exactLevel,
                                        boolean exactDuration) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }

        List<MobEffectInstance> actual = new ArrayList<>();
        contents.getAllEffects().forEach(actual::add);
        if (actual.size() != expected.size()) {
            return false;
        }

        boolean[] used = new boolean[actual.size()];
        for (EffectSignature signature : expected) {
            boolean matched = false;
            for (int index = 0; index < actual.size(); index++) {
                if (!used[index] && signature.matches(actual.get(index), exactLevel, exactDuration)) {
                    used[index] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean loreEffectsMatch(ItemStack stack,
                                            List<LoreEffectSignature> expected,
                                            boolean exactLevel,
                                            boolean exactDuration) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }

        List<String> lines = lore.lines().stream()
                .map(line -> normalizeName(line.getString()))
                .toList();
        boolean[] used = new boolean[lines.size()];
        for (LoreEffectSignature signature : expected) {
            boolean matched = false;
            for (int index = 0; index < lines.size(); index++) {
                if (!used[index] && signature.matches(lines.get(index), exactLevel, exactDuration)) {
                    used[index] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean attributesMatch(ItemStack stack, List<AttributeSignature> expected) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null || modifiers.modifiers().size() != expected.size()) {
            return false;
        }

        List<ItemAttributeModifiers.Entry> actual = modifiers.modifiers();
        boolean[] used = new boolean[actual.size()];
        for (AttributeSignature signature : expected) {
            boolean matched = false;
            for (int index = 0; index < actual.size(); index++) {
                if (!used[index] && signature.matches(actual.get(index))) {
                    used[index] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }
}
