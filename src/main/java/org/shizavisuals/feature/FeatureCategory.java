package org.shizavisuals.feature;

import lombok.Getter;

@Getter
public enum FeatureCategory {
    VISUAL("Visual"),
    MISC("Misc");

    private final String displayName;

    FeatureCategory(String displayName) {
        this.displayName = displayName;
    }
}
