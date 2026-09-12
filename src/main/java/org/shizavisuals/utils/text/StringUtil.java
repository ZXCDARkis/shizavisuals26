package org.shizavisuals.utils.text;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.StringJoiner;

@UtilityClass
public class StringUtil {
    public String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        int length = Math.max(0, maxLength);
        return value.length() <= length ? value : value.substring(0, length);
    }

    public String joinLimited(Collection<String> values, int maxVisible) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        int limit = Math.max(0, maxVisible);
        StringJoiner joiner = new StringJoiner(", ");
        int index = 0;
        for (String value : values) {
            if (index >= limit) {
                break;
            }
            joiner.add(value);
            index++;
        }

        if (values.size() > limit) {
            joiner.add("...");
        }
        return joiner.toString();
    }
}
