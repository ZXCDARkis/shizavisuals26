package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class InputBindSetting extends Setting<Integer> {
    public static final int UNBOUND = -1;

    public InputBindSetting(String name, int defaultKey) {
        super(name, defaultKey < 0 ? UNBOUND : defaultKey);
    }

    public boolean isBound() {
        return getValue() != UNBOUND;
    }

    public boolean matches(int keyCode) {
        return isBound() && getValue() == BindSetting.key(keyCode);
    }

    public boolean matchesMouse(int button) {
        return isBound() && getValue() == BindSetting.mouse(button);
    }

    public void setKey(int keyCode) {
        setValue(BindSetting.key(keyCode));
    }

    public void setMouse(int button) {
        setValue(BindSetting.mouse(button));
    }

    public void clear() {
        setValue(UNBOUND);
    }

    public String getDisplayValue() {
        return isBound() ? BindSetting.describe(getValue()) : "None";
    }

    @Override
    protected Integer normalize(Integer value) {
        return value == null || value < 0 ? UNBOUND : value;
    }

    @Override
    protected JsonElement writeValue(Integer value) {
        return new JsonPrimitive(value);
    }

    @Override
    protected Integer readValue(JsonElement element) {
        return element.getAsInt();
    }
}
