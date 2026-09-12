package org.shizavisuals.event.events.lifecycle;

import lombok.Getter;
import org.shizavisuals.event.Event;
import org.shizavisuals.feature.Feature;

@Getter
public final class FeatureToggleEvent extends Event {
    private final Feature feature;
    private final boolean enabled;

    public FeatureToggleEvent(Feature feature, boolean enabled) {
        this.feature = feature;
        this.enabled = enabled;
    }

    public boolean isDisabled() {
        return !this.enabled;
    }
}
