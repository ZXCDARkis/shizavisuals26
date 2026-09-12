package org.shizavisuals.utils.render.gui;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class MsdfFontFamily {
    private final NavigableMap<Integer, Identifier> variants;
    private final Map<Integer, MsdfFont> resolved = new ConcurrentHashMap<>();

    private MsdfFontFamily(NavigableMap<Integer, Identifier> variants) {
        this.variants = variants;
    }

    public static Builder builder() {
        return new Builder();
    }

    public MsdfFont resolve(int weight) {
        int clamped = Math.max(1, Math.min(1000, weight));
        return this.resolved.computeIfAbsent(clamped, w -> MsdfFont.load(resolveVariant(w)));
    }

    public MsdfFont regular() {
        return resolve(400);
    }

    private Identifier resolveVariant(int weight) {
        Identifier exact = this.variants.get(weight);
        if (exact != null) {
            return exact;
        }

        if (weight >= 400 && weight <= 500) {
            Map.Entry<Integer, Identifier> inRange = this.variants.ceilingEntry(weight);
            if (inRange != null && inRange.getKey() <= 500) {
                return inRange.getValue();
            }
            Map.Entry<Integer, Identifier> below = this.variants.floorEntry(weight);
            if (below != null) {
                return below.getValue();
            }
            return this.variants.firstEntry().getValue();
        }

        if (weight < 400) {
            Map.Entry<Integer, Identifier> below = this.variants.floorEntry(weight);
            return below != null ? below.getValue() : this.variants.firstEntry().getValue();
        }

        Map.Entry<Integer, Identifier> above = this.variants.ceilingEntry(weight);
        return above != null ? above.getValue() : this.variants.lastEntry().getValue();
    }

    public static final class Builder {
        private final NavigableMap<Integer, Identifier> variants = new TreeMap<>();

        private Builder() {
        }

        public Builder variant(int weight, String metadataId) {
            return variant(weight, Identifier.parse(metadataId));
        }

        public Builder variant(int weight, Identifier metadataId) {
            if (weight < 1 || weight > 1000) {
                throw new IllegalArgumentException("Font weight out of range: " + weight);
            }
            this.variants.put(weight, Objects.requireNonNull(metadataId, "metadataId"));
            return this;
        }

        public MsdfFontFamily build() {
            if (this.variants.isEmpty()) {
                throw new IllegalStateException("Font family needs at least one variant");
            }
            return new MsdfFontFamily(new TreeMap<>(this.variants));
        }
    }
}
