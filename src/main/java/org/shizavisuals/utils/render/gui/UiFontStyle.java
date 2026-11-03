package org.shizavisuals.utils.render.gui;

public enum UiFontStyle {
    REGULAR(400, 0.0F),
    REGULAR_TRACKED(400, 0.015F),
    MEDIUM(500, 0.015F),
    SEMIBOLD(600, 0.015F),
    BOLD(700, 0.0F);

    private final int weight;
    private final float letterSpacingEm;

    UiFontStyle(int weight, float letterSpacingEm) {
        this.weight = weight;
        this.letterSpacingEm = letterSpacingEm;
    }

    public int weight() {
        return weight;
    }

    public float letterSpacingEm() {
        return letterSpacingEm;
    }
}
