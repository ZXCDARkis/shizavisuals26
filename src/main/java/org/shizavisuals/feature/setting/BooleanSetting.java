package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean defaultValue) {
        super(name, defaultValue);
    }

    @Override
    protected JsonElement writeValue(Boolean value) {
        return new JsonPrimitive(value);
    }

    @Override
    protected Boolean readValue(JsonElement element) {
        return element.getAsBoolean();
    }
}
