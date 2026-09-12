package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ButtonSetting extends Setting<Boolean> {
    private final String buttonLabel;
    private final Runnable action;

    public ButtonSetting(String name, String buttonLabel, Runnable action) {
        super(name, false);
        this.buttonLabel = buttonLabel;
        this.action = action == null ? () -> {
        } : action;
        nonPersistent();
    }

    public String getButtonLabel() {
        return this.buttonLabel;
    }

    public void press() {
        this.action.run();
    }

    @Override
    protected JsonElement writeValue(Boolean value) {
        return new JsonPrimitive(false);
    }

    @Override
    protected Boolean readValue(JsonElement element) {
        return false;
    }
}
