package org.shizavisuals.utils.render.cosmetic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PulseCosmeticCatalog {

    public static final String NONE = "None";

    private static final List<PulseCosmetic> COSMETICS = List.of(
            of(PulseCosmeticCategory.WINGS, "124", "Wings - Ghoul"),
            of(PulseCosmeticCategory.WINGS, "25", "Wings - Aurum"),
            of(PulseCosmeticCategory.WINGS, "61", "Wings - Angel"),
            of(PulseCosmeticCategory.WINGS, "62", "Wings - Dragon"),
            of(PulseCosmeticCategory.WINGS, "63", "Wings - Diamond"),
            of(PulseCosmeticCategory.WINGS, "64", "Wings - Spider"),
            of(PulseCosmeticCategory.WINGS, "65", "Wings - Easter"),
            of(PulseCosmeticCategory.WINGS, "82", "Wings - Chain"),
            of(PulseCosmeticCategory.WINGS, "99", "Wings - Dark"),
            of(PulseCosmeticCategory.WINGS, "custom_vin_wings", "Wings - Cyber"),

            of(PulseCosmeticCategory.HAT, "48", "Hat - Armour"),
            of(PulseCosmeticCategory.HAT, "49", "Hat - Capybara"),
            of(PulseCosmeticCategory.HAT, "50", "Hat - Chicken"),
            of(PulseCosmeticCategory.HAT, "57", "Hat - Sheep"),
            of(PulseCosmeticCategory.HAT, "97", "Hat - Happy"),
            of(PulseCosmeticCategory.HAT, "98", "Hat - Halo"),
            of(PulseCosmeticCategory.HAT, "custom_3d_glasses", "3D Glasses"),
            of(PulseCosmeticCategory.HAT, "custom_chef_hat", "Chef Hat"),
            of(PulseCosmeticCategory.HAT, "custom_newsboy_cap", "Newsboy Cap"),
            of(PulseCosmeticCategory.HAT, "custom_plaid_hat", "Plaid Hat"),
            of(PulseCosmeticCategory.HAT, "custom_straw_hat", "Straw Hat"),
            of(PulseCosmeticCategory.HAT, "custom_traffic_cone", "Traffic Cone"),
            of(PulseCosmeticCategory.HAT, "custom_ushanka_initial", "Ushanka Initial"),
            of(PulseCosmeticCategory.HAT, "custom_ushanka_overhaul", "Ushanka Overhaul"),

            of(PulseCosmeticCategory.BODYWEAR, "36", "Backpack - Louis Vuitton"),
            of(PulseCosmeticCategory.BODYWEAR, "37", "Backpack - Gucci"),
            of(PulseCosmeticCategory.BODYWEAR, "38", "Backpack - Adidas"),
            of(PulseCosmeticCategory.BODYWEAR, "39", "Backpack - Nike"),
            of(PulseCosmeticCategory.BODYWEAR, "40", "Backpack - Supreme"),
            of(PulseCosmeticCategory.BODYWEAR, "41", "Backpack - Vlone"),
            of(PulseCosmeticCategory.BODYWEAR, "42", "Backpack - Bape"),
            of(PulseCosmeticCategory.BODYWEAR, "43", "Backpack - Balenciaga"),
            of(PulseCosmeticCategory.BODYWEAR, "44", "Backpack - Chrome Hearts"),

            of(PulseCosmeticCategory.PET, "135", "Pet - Creeper"),
            of(PulseCosmeticCategory.PET, "45", "Pet - Angel"),
            of(PulseCosmeticCategory.PET, "46", "Pet - Demon"),
            of(PulseCosmeticCategory.PET, "47", "Pet - Buddy"),
            of(PulseCosmeticCategory.PET, "84", "Pet - Diamond"),
            of(PulseCosmeticCategory.PET, "93", "Pet - Bee")
    );

    private static final Map<PulseCosmeticCategory, List<PulseCosmetic>> BY_CATEGORY = index();

    private PulseCosmeticCatalog() {
    }

    public static List<PulseCosmetic> all() {
        return COSMETICS;
    }

    public static List<PulseCosmetic> byCategory(PulseCosmeticCategory category) {
        return BY_CATEGORY.getOrDefault(category, List.of());
    }

    public static List<String> modeOptions(PulseCosmeticCategory category) {
        List<String> options = new ArrayList<>();
        options.add(NONE);
        for (PulseCosmetic cosmetic : byCategory(category)) {
            options.add(cosmetic.mode());
        }
        return List.copyOf(options);
    }

    public static PulseCosmetic resolve(PulseCosmeticCategory category, String mode) {
        if (mode == null) {
            return null;
        }
        for (PulseCosmetic cosmetic : byCategory(category)) {
            if (cosmetic.mode().equalsIgnoreCase(mode)) {
                return cosmetic;
            }
        }
        return null;
    }

    private static Map<PulseCosmeticCategory, List<PulseCosmetic>> index() {
        Map<PulseCosmeticCategory, List<PulseCosmetic>> index = new LinkedHashMap<>();
        for (PulseCosmeticCategory category : PulseCosmeticCategory.values()) {
            index.put(category, new ArrayList<>());
        }
        for (PulseCosmetic cosmetic : COSMETICS) {
            index.get(cosmetic.category()).add(cosmetic);
        }
        for (Map.Entry<PulseCosmeticCategory, List<PulseCosmetic>> entry : index.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        return Map.copyOf(index);
    }

    private static PulseCosmetic of(PulseCosmeticCategory category, String id, String title) {
        return new PulseCosmetic(category, id, title);
    }

    public record PulseCosmetic(PulseCosmeticCategory category, String id, String title) {
        public String mode() {
            return category.id() + "/" + id;
        }

        public String cacheKey() {
            return mode().toLowerCase(Locale.ROOT);
        }
    }
}
