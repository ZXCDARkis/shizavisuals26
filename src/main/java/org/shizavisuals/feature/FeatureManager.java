package org.shizavisuals.feature;

import org.shizavisuals.ShizaVisuals;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.input.KeyboardInputEvent;
import org.shizavisuals.event.events.input.MouseInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.shizavisuals.event.events.lifecycle.ShutdownEvent;
import org.shizavisuals.feature.impl.misc.AutoTpaAcceptFeature;
import org.shizavisuals.feature.impl.misc.ChestStealerFeature;
import org.shizavisuals.feature.impl.misc.DeathCoordsFeature;
import org.shizavisuals.feature.impl.misc.FakePlayerFeature;
import org.shizavisuals.feature.impl.misc.InventoryManagerFeature;
import org.shizavisuals.feature.impl.misc.NameProtectFeature;


import org.shizavisuals.feature.impl.misc.SprintFeature;
import org.shizavisuals.feature.impl.misc.WebPanelFeature;
import org.shizavisuals.feature.impl.visual.TargetEsp;
import org.shizavisuals.feature.impl.visual.BlockOutlineFeature;
import org.shizavisuals.feature.impl.visual.CosmeticsFeature;
import org.shizavisuals.feature.impl.visual.CrosshairFeature;
import org.shizavisuals.feature.impl.visual.FullBrightFeature;
import org.shizavisuals.feature.impl.visual.HitParticlesFeature;
import org.shizavisuals.feature.impl.visual.HoldMyItemsFeature;
import org.shizavisuals.feature.impl.visual.HoldMyItemsPunchyFeature;
import org.shizavisuals.feature.impl.visual.HudFeature;
import org.shizavisuals.feature.impl.visual.ItemPhysicsFeature;
import org.shizavisuals.feature.impl.visual.ItemReplacerFeature;
import org.shizavisuals.feature.impl.visual.JumpCirclesFeature;
import org.shizavisuals.feature.impl.visual.KillEffectFeature;
import org.shizavisuals.feature.impl.visual.KineticLyricsFeature;
import org.shizavisuals.feature.impl.visual.LowHpFeature;
import org.shizavisuals.feature.impl.visual.MotionBlurFeature;
import org.shizavisuals.feature.impl.visual.WaveEffectFeature;
import org.shizavisuals.feature.impl.visual.PopChamsFeature;
import org.shizavisuals.feature.impl.visual.PuddlesFeature;
import org.shizavisuals.feature.impl.visual.RemovalsFeature;
import org.shizavisuals.feature.impl.visual.ShaderHandsFeature;
import org.shizavisuals.feature.impl.visual.SwingAnimationFeature;
import org.shizavisuals.feature.impl.visual.TntEffectFeature;
import org.shizavisuals.feature.impl.visual.SpiritsFeature;
import org.shizavisuals.feature.impl.visual.PetsFeature;
import org.shizavisuals.feature.impl.visual.SpatialInventoryFeature;
import org.shizavisuals.feature.impl.visual.TrajectoriesFeature;
import org.shizavisuals.feature.impl.visual.ViewModelFeature;
import org.shizavisuals.feature.impl.visual.WorldTweaksFeature;
import org.shizavisuals.feature.impl.visual.WorldParticlesFeature;
import org.shizavisuals.feature.setting.BindSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class FeatureManager {
    public static final FeatureManager INSTANCE = new FeatureManager();

    private final Map<String, Feature> features = new LinkedHashMap<>();
    private final Map<Class<? extends Feature>, Feature> featuresByType = new ConcurrentHashMap<>();
    private final FeatureConfigStore configStore = new FeatureConfigStore();
    private final ScheduledExecutorService configWriter = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ShizaVisuals Config Writer");
        thread.setDaemon(true);
        return thread;
    });
    private final Object saveLock = new Object();
    private final Map<Feature, Set<Integer>> activeHoldBinds = new IdentityHashMap<>();
    private ScheduledFuture<?> pendingSave;
    private boolean initialized;

    private FeatureManager() {
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        List.of(
                new SprintFeature(),
                new HudFeature(),
                new HitParticlesFeature(),
                new JumpCirclesFeature(),
                new KillEffectFeature(),
                new WaveEffectFeature(),
                new KineticLyricsFeature(),
                new LowHpFeature(),
                new MotionBlurFeature(),
                new TrajectoriesFeature(),
                new WorldParticlesFeature(),
                new PopChamsFeature(),
                new RemovalsFeature(),
                new CrosshairFeature(),
                new FullBrightFeature(),
                new SwingAnimationFeature(),
                new ViewModelFeature(),
                new ShaderHandsFeature(),
                new BlockOutlineFeature(),
                new HoldMyItemsFeature(),
                new HoldMyItemsPunchyFeature(),
                new InventoryManagerFeature(),
                new ItemPhysicsFeature(),
                new WorldTweaksFeature(),
                new NameProtectFeature(),
                new FakePlayerFeature(),
                new WebPanelFeature(),
                new DeathCoordsFeature(),
                new AutoTpaAcceptFeature(),
                new ChestStealerFeature(),
                new TargetEsp(),
                new CosmeticsFeature(),
                new PuddlesFeature(),
                new TntEffectFeature(),
                new SpiritsFeature(),
                new PetsFeature(),
                new ItemReplacerFeature(),
                new SpatialInventoryFeature()
        ).forEach(this::register);

        configStore.load(this);
        initialized = true;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ShizaVisuals.markStopping();
            flushPendingSave();
        }, "ShizaVisuals Config Flush"));
    }

    public void save() {
        if (!initialized) {
            return;
        }
        var snapshot = configStore.snapshot(this);
        synchronized (saveLock) {
            if (pendingSave != null) {
                pendingSave.cancel(false);
            }
            pendingSave = configWriter.schedule(() -> configStore.save(snapshot), 2L, TimeUnit.SECONDS);
        }
    }

    private void flushPendingSave() {
        synchronized (saveLock) {
            if (pendingSave == null) {
                return;
            }
            pendingSave.cancel(false);
            pendingSave = null;
        }
        configStore.save(configStore.snapshot(this));
    }

    public boolean saveConfigAs(String name) {
        return initialized && configStore.saveNamed(name, configStore.snapshot(this));
    }

    public boolean loadConfig(String name) {
        if (!initialized || !configStore.loadNamed(this, name)) {
            return false;
        }
        save();
        return true;
    }

    public boolean deleteConfig(String name) {
        return configStore.deleteNamed(name);
    }

    public List<String> configNames() {
        return configStore.listNamed();
    }

    public void register(Feature feature) {
        String key = normalize(feature.getName());
        if (features.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate feature name: " + feature.getName());
        }

        feature.setStateListener(this::save);
        features.put(key, feature);
        featuresByType.put(feature.getClass(), feature);
    }

    public Collection<Feature> getFeatures() {
        return Collections.unmodifiableCollection(features.values());
    }

    public List<Feature> getFeatures(FeatureCategory category) {
        List<Feature> result = new ArrayList<>();
        for (Feature feature : features.values()) {
            if (feature.getCategory() == category) {
                result.add(feature);
            }
        }
        return result;
    }

    public Feature getFeature(String name) {
        return features.get(normalize(name));
    }

    public <T extends Feature> T getFeature(Class<T> type) {
        return type.cast(featuresByType.get(type));
    }

    public <T extends Feature> T getEnabled(Class<T> type) {
        T feature = getFeature(type);
        return feature != null && feature.isEnabled() ? feature : null;
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof AbstractContainerScreen)) {
            return;
        }
        dispatchBind(BindSetting.key(event.getKey()), event.getAction());
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof AbstractContainerScreen)) {
            return;
        }
        dispatchBind(BindSetting.mouse(event.getButton()), event.getAction());
    }

    @EventTarget
    public void onShutdown(ShutdownEvent event) {
        try {
            synchronized (saveLock) {
                if (pendingSave != null) {
                    pendingSave.cancel(false);
                    pendingSave = null;
                }
            }
            configStore.save(configStore.snapshot(this));
        } finally {
            this.activeHoldBinds.clear();
            this.configWriter.shutdown();
            this.initialized = false;

            try { org.shizavisuals.webpanel.WebPanelAwtGuard.shutdown(); } catch (Throwable ignored) {}
            try { org.shizavisuals.webpanel.WebPanelStream.shutdown(); } catch (Throwable ignored) {}
            try { org.shizavisuals.hud.island.MusicStatus.shutdown(); } catch (Throwable ignored) {}
        }
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private void dispatchBind(int bindCode, int action) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) {
            return;
        }
        for (Feature feature : features.values()) {
            if (feature.supportsBinds()
                    && feature.isToggleable()
                    && feature.getBind().matchesCode(bindCode)) {
                handleBindInput(feature, bindCode, action);
            }
        }
    }

    private void handleBindInput(Feature feature, int bindCode, int action) {
        if (feature.getBindModeForCode(bindCode) == BindMode.HOLD) {
            Set<Integer> activeCodes = this.activeHoldBinds.computeIfAbsent(feature, ignored -> new LinkedHashSet<>());
            if (action == GLFW.GLFW_PRESS) {
                if (activeCodes.add(bindCode)) {
                    feature.setEnabled(true);
                }
                return;
            }

            activeCodes.remove(bindCode);
            if (activeCodes.isEmpty()) {
                this.activeHoldBinds.remove(feature);
                feature.setEnabled(false);
            }
            return;
        }

        if (action == GLFW.GLFW_PRESS) {
            feature.toggle();
        }
    }
}
