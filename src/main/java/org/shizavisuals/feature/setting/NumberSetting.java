package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.shizavisuals.utils.math.MathUtil;

import java.util.Locale;

@Getter
public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;

    public NumberSetting(String name, double defaultValue, double min, double max, double step, String suffix) {
        super(name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
    }

    public String getDisplayValue() {
        return String.format(Locale.ROOT, "%.2f", getValue()) + suffix;
    }

    public double getProgress() {
        if (max <= min) {
            return 0.0;
        }
        return (getValue() - min) / (max - min);
    }

    @Override
    protected Double normalize(Double value) {
        double clamped = MathUtil.clamp(value, min, max);
        if (step <= 0.0) {
            return clamped;
        }

        double snapped = min + Math.round((clamped - min) / step) * step;
        return MathUtil.clamp(snapped, min, max);
    }

    @Override
    protected JsonElement writeValue(Double value) {
        return new JsonPrimitive(value);
    }

    @Override
    protected Double readValue(JsonElement element) {
        return element.getAsDouble();
    }
}
