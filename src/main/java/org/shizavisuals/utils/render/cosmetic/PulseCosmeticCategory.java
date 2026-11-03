package org.shizavisuals.utils.render.cosmetic;

public enum PulseCosmeticCategory {
    WINGS("wings"),
    HAT("hat"),
    BODYWEAR("bodywear"),
    PET("pet");

    private final String id;

    PulseCosmeticCategory(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
