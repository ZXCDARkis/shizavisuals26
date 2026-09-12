package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.shizavisuals.utils.text.StringUtil;

@Getter
public final class TextSetting extends Setting<String> {
    private final int maxLength;
    private boolean secret;

    public TextSetting(String name, String defaultValue) {
        this(name, defaultValue, 64);
    }

    public TextSetting(String name, String defaultValue, int maxLength) {
        super(name, StringUtil.abbreviate(defaultValue, Math.max(0, maxLength)));
        this.maxLength = Math.max(0, maxLength);
    }

    public TextSetting secret() {
        this.secret = true;
        return this;
    }

    @Override
    protected String normalize(String value) {
        return StringUtil.abbreviate(value, this.maxLength);
    }

    @Override
    protected JsonElement writeValue(String value) {
        return new JsonPrimitive(value);
    }

    @Override
    protected String readValue(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return getDefaultValue();
        }
        return element.getAsString();
    }
}
