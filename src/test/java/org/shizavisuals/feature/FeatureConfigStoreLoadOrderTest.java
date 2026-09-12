package org.shizavisuals.feature;

import com.google.gson.JsonObject;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.TextSetting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureConfigStoreLoadOrderTest {
    @Test
    void configurationIsLoadedBeforeEnabledStateIsApplied() {
        ProbeFeature feature = new ProbeFeature();
        JsonObject featureJson = new JsonObject();
        JsonObject settings = new JsonObject();
        settings.addProperty("Shared Value", "loaded");
        featureJson.add("settings", settings);
        featureJson.addProperty("enabled", true);

        try {
            FeatureConfigStore.loadFeatureConfiguration(feature, featureJson);

            assertFalse(feature.isEnabled());
            FeatureConfigStore.loadFeatureEnabledState(feature, featureJson);
            assertTrue(feature.isEnabled());
            assertEquals("loaded", feature.valueObservedOnEnable);
        } finally {
            feature.setEnabled(false);
        }
    }

    private static final class ProbeFeature extends Feature {
        private final TextSetting sharedValue = register(new TextSetting(
                "Shared Value", "default"
        ));
        private String valueObservedOnEnable;

        private ProbeFeature() {
            super(
                    "Probe",
                    "Config load-order probe",
                    FeatureCategory.MISC,
                    BindSetting.UNBOUND
            );
        }

        @Override
        protected void onEnable() {
            this.valueObservedOnEnable = this.sharedValue.getValue();
        }
    }
}
