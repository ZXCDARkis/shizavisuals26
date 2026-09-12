package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name, defaultValue);
        if (modes == null || modes.length == 0) {
            throw new IllegalArgumentException("Mode setting requires at least one mode");
        }

        this.modes = List.copyOf(new ArrayList<>(List.of(modes)));
        if (!containsMode(defaultValue)) {
            throw new IllegalArgumentException("Default mode must exist in modes: " + defaultValue);
        }
    }

    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }

    public void cycle() {
        int nextIndex = (indexOf(getValue()) + 1) % modes.size();
        setValue(modes.get(nextIndex));
    }

    public int indexOf(String mode) {
        for (int i = 0; i < modes.size(); i++) {
            if (modes.get(i).equalsIgnoreCase(mode)) {
                return i;
            }
        }
        return -1;
    }

    public boolean containsMode(String mode) {
        return indexOf(mode) != -1;
    }

    @Override
    protected String normalize(String value) {
        Objects.requireNonNull(value, "mode");
        int index = indexOf(value);
        if (index == -1) {
            throw new IllegalArgumentException("Unknown mode '" + value + "' for setting " + getName());
        }
        return modes.get(index);
    }

    @Override
    protected String normalizeWarningOption(String option) {
        Objects.requireNonNull(option, "mode");
        int index = indexOf(option);
        if (index == -1) {
            throw new IllegalArgumentException("Unknown mode '" + option + "' for setting " + getName());
        }
        return modes.get(index);
    }

    @Override
    protected JsonElement writeValue(String value) {
        return new JsonPrimitive(value);
    }

    @Override
    protected String readValue(JsonElement element) {
        String value = element.getAsString();
        int index = indexOf(value);
        return index == -1 ? getDefaultValue() : this.modes.get(index);
    }
}
