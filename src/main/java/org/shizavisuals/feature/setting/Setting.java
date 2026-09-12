package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import lombok.Getter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

@Getter
public abstract class Setting<T> {
    private final String name;
    private final T defaultValue;
    private String configKey;
    private boolean persistent = true;

    private T value;
    private Runnable changeListener = () -> {};
    private BooleanSupplier visibility = () -> true;
    private Double warningRiskValue;
    private Double warningExtraRiskValue;
    private WarningLevel staticWarningLevel = WarningLevel.NONE;
    private final Map<String, WarningLevel> optionWarningLevels = new LinkedHashMap<>();

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.configKey = name;
        this.value = defaultValue;
    }

    public final void setValue(T value) {
        T normalized = normalize(value);
        if (this.value != null && this.value.equals(normalized)) {
            return;
        }

        this.value = normalized;
        changeListener.run();
    }

    public final void reset() {
        setValue(defaultValue);
    }

    public final void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener == null ? () -> {} : changeListener;
    }

    public final <S extends Setting<T>> S configKey(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            throw new IllegalArgumentException("Setting config key cannot be blank");
        }
        this.configKey = configKey;
        return self();
    }

    public final <S extends Setting<T>> S visibleWhen(BooleanSupplier visibility) {
        this.visibility = visibility == null ? () -> true : visibility;
        return self();
    }

    public final <S extends Setting<T>> S nonPersistent() {
        this.persistent = false;
        return self();
    }

    public final boolean isVisible() {
        return this.visibility.getAsBoolean();
    }

    public final <S extends Setting<T>> S warning(double riskValue) {
        return warning(riskValue, null);
    }

    public final <S extends Setting<T>> S warning(double riskValue, double extraRiskValue) {
        return warning(riskValue, Double.valueOf(extraRiskValue));
    }

    public final <S extends Setting<T>> S risk() {
        this.staticWarningLevel = WarningLevel.RISK;
        return self();
    }

    public final <S extends Setting<T>> S risk(String option) {
        this.optionWarningLevels.put(normalizeWarningOption(option), WarningLevel.RISK);
        return self();
    }

    public final <S extends Setting<T>> S extraRisk() {
        this.staticWarningLevel = WarningLevel.EXTRA_RISK;
        return self();
    }

    public final <S extends Setting<T>> S extraRisk(String option) {
        this.optionWarningLevels.put(normalizeWarningOption(option), WarningLevel.EXTRA_RISK);
        return self();
    }

    public final <S extends Setting<T>> S extrarisk() {
        return extraRisk();
    }

    public final <S extends Setting<T>> S extrarisk(String option) {
        return extraRisk(option);
    }

    public final WarningLevel warningLevel() {
        Object current = getValue();
        if (current instanceof Number number && this.warningRiskValue != null) {
            double value = number.doubleValue();
            if (this.warningExtraRiskValue != null && value >= this.warningExtraRiskValue) {
                return WarningLevel.EXTRA_RISK;
            }
            if (value >= this.warningRiskValue) {
                return WarningLevel.RISK;
            }
        }

        WarningLevel optionWarningLevel = optionWarningLevel(current);
        if (optionWarningLevel != WarningLevel.NONE) {
            return optionWarningLevel;
        }

        if (this.staticWarningLevel == WarningLevel.NONE || !isStaticWarningActive(current)) {
            return WarningLevel.NONE;
        }
        return this.staticWarningLevel;
    }

    public final WarningLevel warningLevel(String option) {
        return optionWarningLevels.getOrDefault(normalizeWarningOption(option), WarningLevel.NONE);
    }

    public final JsonElement write() {
        return writeValue(value);
    }

    public final void read(JsonElement element) {
        setValue(readValue(element));
    }

    protected T normalize(T value) {
        return value;
    }

    protected String normalizeWarningOption(String option) {
        return Objects.requireNonNull(option, "option");
    }

    protected abstract JsonElement writeValue(T value);

    protected abstract T readValue(JsonElement element);

    private <S extends Setting<T>> S warning(double riskValue, Double extraRiskValue) {
        this.warningRiskValue = riskValue;
        this.warningExtraRiskValue = extraRiskValue;
        return self();
    }

    private boolean isStaticWarningActive(Object value) {
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        return false;
    }

    private WarningLevel optionWarningLevel(Object value) {
        if (this.optionWarningLevels.isEmpty() || value == null) {
            return WarningLevel.NONE;
        }
        if (value instanceof Collection<?> collection) {
            WarningLevel highest = WarningLevel.NONE;
            for (Object option : collection) {
                WarningLevel warningLevel = optionWarningLevel(String.valueOf(option));
                if (warningLevel.ordinal() > highest.ordinal()) {
                    highest = warningLevel;
                }
            }
            return highest;
        }
        return optionWarningLevel(String.valueOf(value));
    }

    private WarningLevel optionWarningLevel(String option) {
        return this.optionWarningLevels.getOrDefault(normalizeWarningOption(option), WarningLevel.NONE);
    }

    @SuppressWarnings("unchecked")
    private <S extends Setting<T>> S self() {
        return (S) this;
    }

    public enum WarningLevel {
        NONE,
        RISK,
        EXTRA_RISK
    }
}
