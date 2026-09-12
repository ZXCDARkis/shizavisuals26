package org.shizavisuals.feature;

import lombok.Getter;
import org.shizavisuals.event.EventManager;
import org.shizavisuals.event.events.lifecycle.FeatureToggleEvent;
import org.slf4j.LoggerFactory;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
public abstract class Feature {
    private final String name;
    private final String description;
    private final FeatureCategory category;
    protected final BindSetting bind;
    protected final ModeSetting bindMode = new ModeSetting("Bind Mode", BindMode.TOGGLE.name(), BindMode.HOLD.name(), BindMode.TOGGLE.name());
    private final List<Setting<?>> settings = new ArrayList<>();
    @Getter(lombok.AccessLevel.NONE)
    private final Map<String, Setting<?>> settingsByName = new HashMap<>();

    private boolean enabled;
    private boolean visible = true;
    private Runnable stateListener = () -> {};

    protected Feature(String name, String description, FeatureCategory category, int bind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.bind = new BindSetting("Bind", bind);
    }

    public boolean isToggleable() {
        return true;
    }

    public boolean supportsBinds() {
        return true;
    }

    public final void setEnabled(boolean enabled) {
        if (!isToggleable()) {
            return;
        }
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;
        if (enabled) {
            EventManager.subscribe(this);
            try {
                onEnable();
            } catch (Throwable throwable) {

                if (throwable instanceof FeatureEnableRejectedException) {
                    LoggerFactory.getLogger(getClass()).warn(
                            "{} was not enabled: {}",
                            name,
                            throwable.getMessage()
                    );
                } else {
                    LoggerFactory.getLogger(getClass()).error("{} failed to enable", name, throwable);
                }
                this.enabled = false;
                EventManager.unsubscribe(this);
                return;
            }
        } else {
            try {
                onDisable();
            } catch (Throwable throwable) {
                LoggerFactory.getLogger(getClass()).error("{} failed to disable cleanly", name, throwable);
            }
            EventManager.unsubscribe(this);
        }
        EventManager.call(new FeatureToggleEvent(this, enabled));
        stateListener.run();
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        onStateChanged();
    }

    protected final <S extends Setting<?>> S register(S setting) {
        Objects.requireNonNull(setting, "setting");
        setting.setChangeListener(this::onStateChanged);
        this.settings.add(setting);
        this.settingsByName.put(setting.getName(), setting);
        return setting;
    }

    public final List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public final Setting<?> getSetting(String name) {
        return this.settingsByName.get(name);
    }

    public final void setStateListener(Runnable stateListener) {
        this.stateListener = stateListener == null ? () -> {} : stateListener;
    }

    public final void setBind(int key) {
        if (!supportsBinds()) {
            return;
        }
        bind.setSingle(key);
        onStateChanged();
    }

    public final void setKeyBind(int key) {
        if (!supportsBinds()) {
            return;
        }
        bind.setSingle(BindSetting.key(key));
        onStateChanged();
    }

    public final void setMouseBind(int button) {
        if (!supportsBinds()) {
            return;
        }
        bind.setSingle(BindSetting.mouse(button));
        onStateChanged();
    }

    public final void clearBind() {
        if (!supportsBinds()) {
            return;
        }
        bind.clear();
        onStateChanged();
    }

    public final int addBind(int bindCode) {
        if (!supportsBinds()) {
            return -1;
        }
        int index = bind.add(bindCode);
        onStateChanged();
        return index;
    }

    public final int addKeyBind(int key) {
        return addBind(BindSetting.key(key));
    }

    public final int addMouseBind(int button) {
        return addBind(BindSetting.mouse(button));
    }

    public final int setBindAt(int index, int bindCode) {
        if (!supportsBinds()) {
            return -1;
        }
        int resolvedIndex = bind.setAt(index, bindCode);
        onStateChanged();
        return resolvedIndex;
    }

    public final int setKeyBindAt(int index, int key) {
        return setBindAt(index, BindSetting.key(key));
    }

    public final int setMouseBindAt(int index, int button) {
        return setBindAt(index, BindSetting.mouse(button));
    }

    public final void removeBindAt(int index) {
        if (!supportsBinds()) {
            return;
        }
        bind.removeAt(index);
        onStateChanged();
    }

    public final boolean hasBindAt(int index) {
        return supportsBinds() && bind.hasIndex(index);
    }

    public final List<Integer> getBinds() {
        return supportsBinds() ? bind.getValue() : List.of();
    }

    public final void setBindMode(BindMode mode) {
        if (!supportsBinds()) {
            return;
        }
        bindMode.setValue(mode.name());
        bind.setAllModes(mode);
        onStateChanged();
    }

    public final void setBindModeAt(int index, BindMode mode) {
        if (!supportsBinds()) {
            return;
        }
        bind.setMode(index, mode);
        onStateChanged();
    }

    public final BindMode getResolvedBindMode() {
        return BindMode.valueOf(bindMode.getValue());
    }

    public final BindMode getBindModeAt(int index) {
        return bind.getMode(index, getResolvedBindMode());
    }

    public final BindMode getBindModeForCode(int bindCode) {
        return bind.getModeForCode(bindCode, getResolvedBindMode());
    }

    public final boolean isBindVisibleAt(int index) {
        return supportsBinds() && bind.isVisibleAt(index);
    }

    public final void setBindVisibleAt(int index, boolean visible) {
        if (!supportsBinds()) {
            return;
        }
        bind.setVisibleAt(index, visible);
        onStateChanged();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onStateChanged() {
        stateListener.run();
    }
}
