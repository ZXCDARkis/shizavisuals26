package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.shizavisuals.utils.ColorUtil;

public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, int defaultValue) {
        super(name, ColorUtil.withAlpha(defaultValue, 255));
    }

    @Override
    protected Integer normalize(Integer value) {
        return ColorUtil.withAlpha(value == null ? getDefaultValue() : value, 255);
    }

    @Override
    protected JsonElement writeValue(Integer value) {
        return new JsonPrimitive(ColorUtil.toHex(value));
    }

    @Override
    protected Integer readValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return getDefaultValue();
        }
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return ColorUtil.withAlpha(primitive.getAsInt(), 255);
            }
            Integer parsed = ColorUtil.parse(primitive.getAsString());
            if (parsed != null) {
                return parsed;
            }
        }
        return getDefaultValue();
    }
}
