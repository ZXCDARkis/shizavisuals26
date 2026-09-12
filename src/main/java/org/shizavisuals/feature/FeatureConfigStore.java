package org.shizavisuals.feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.shizavisuals.utils.ConfigIO;
import org.shizavisuals.feature.setting.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class FeatureConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureConfigStore.class);

    private final Path path = ConfigIO.resolve("features.json");
    private final Path namedDir = ConfigIO.resolve("configs");

    public void load(FeatureManager manager) {
        if (loadFrom(manager, path)) {
            save(snapshot(manager));
        }
    }

    public boolean loadNamed(FeatureManager manager, String name) {
        Path named = namedPath(name);
        if (named == null || !Files.exists(named)) {
            return false;
        }
        if (loadFrom(manager, named)) {
            ConfigIO.write(named, snapshot(manager));
        }
        return true;
    }

    public boolean saveNamed(String name, JsonObject root) {
        Path named = namedPath(name);
        return named != null && ConfigIO.write(named, root);
    }

    public boolean deleteNamed(String name) {
        Path named = namedPath(name);
        try {
            return named != null && Files.deleteIfExists(named);
        } catch (IOException exception) {
            LOGGER.error("Failed to delete config {}", named, exception);
            return false;
        }
    }

    public List<String> listNamed() {
        if (!Files.isDirectory(namedDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(namedDir)) {
            return files
                    .map(file -> file.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(".json"))
                    .map(fileName -> fileName.substring(0, fileName.length() - ".json".length()))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            LOGGER.error("Failed to list configs in {}", namedDir, exception);
            return List.of();
        }
    }

    private Path namedPath(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}") ? namedDir.resolve(name + ".json") : null;
    }

    private boolean loadFrom(FeatureManager manager, Path source) {
        JsonObject root = ConfigIO.read(source);
        if (root == null) {
            return false;
        }
        JsonObject features = root.getAsJsonObject("features");
        if (features == null) {
            return false;
        }
        boolean migrated = false;

        for (Feature feature : manager.getFeatures()) {
            JsonObject featureJson = features.getAsJsonObject(feature.getName());
            if (featureJson == null) {
                continue;
            }
            try {
                loadFeatureConfiguration(feature, featureJson);
            } catch (Exception exception) {
                LOGGER.error("Failed to load config for feature {} from {}", feature.getName(), source, exception);
            }
        }
        for (Feature feature : manager.getFeatures()) {
            JsonObject featureJson = features.getAsJsonObject(feature.getName());
            if (featureJson == null) {
                continue;
            }
            try {
                loadFeatureEnabledState(feature, featureJson);
            } catch (Exception exception) {
                LOGGER.error("Failed to apply enabled state for feature {} from {}", feature.getName(), source, exception);
            }
        }
        return migrated;
    }

    static void loadFeatureConfiguration(Feature feature, JsonObject featureJson) {
        JsonElement visible = featureJson.get("visible");
        if (visible != null && visible.isJsonPrimitive()) {
            feature.setVisible(visible.getAsBoolean());
        }

        if (feature.supportsBinds()) {
            JsonElement bind = featureJson.get("bind");
            JsonElement binds = featureJson.get("binds");
            if (binds != null) {
                feature.getBind().read(binds);
            } else if (bind != null && bind.isJsonPrimitive()) {
                feature.setBind(bind.getAsInt());
            }

            JsonElement bindMode = featureJson.get("bindMode");
            JsonElement bindModes = featureJson.get("bindModes");
            if (bindModes != null) {
                feature.getBind().readModes(bindModes);
            } else if (bindMode != null && bindMode.isJsonPrimitive()) {
                feature.setBindMode(BindMode.valueOf(bindMode.getAsString()));
            }

            feature.getBind().readVisibility(featureJson.get("bindVisibility"));
        }

        JsonObject settings = featureJson.getAsJsonObject("settings");
        if (settings == null) {
            return;
        }

        for (Setting<?> setting : feature.getSettings()) {
            if (!setting.isPersistent()) {
                continue;
            }
            JsonElement value = settings.get(setting.getConfigKey());
            if (value == null) {
                continue;
            }
            try {
                setting.read(value);
            } catch (Exception exception) {
                LOGGER.error("Failed to load setting {}.{}; keeping default", feature.getName(), setting.getName(), exception);
            }
        }
    }

    static void loadFeatureEnabledState(Feature feature, JsonObject featureJson) {
        if (!feature.isToggleable()) {
            return;
        }
        JsonElement enabled = featureJson.get("enabled");
        if (enabled != null && enabled.isJsonPrimitive()) {
            feature.setEnabled(enabled.getAsBoolean());
        }
    }

    public JsonObject snapshot(FeatureManager manager) {
        JsonObject root = new JsonObject();
        JsonObject features = new JsonObject();

        for (Feature feature : manager.getFeatures()) {
            JsonObject featureJson = new JsonObject();
            if (feature.isToggleable()) {
                featureJson.addProperty("enabled", feature.isEnabled());
            }
            featureJson.addProperty("visible", feature.isVisible());
            if (feature.supportsBinds()) {
                featureJson.add("binds", feature.getBind().write());
                featureJson.add("bindModes", feature.getBind().writeModes());
                featureJson.add("bindVisibility", feature.getBind().writeVisibility());
                featureJson.addProperty("bind", feature.getBind().isBound() ? feature.getBind().get(0) : -1);
                featureJson.addProperty("bindMode", feature.getBindModeAt(0).name());
            }

            JsonObject settings = new JsonObject();
            for (Setting<?> setting : feature.getSettings()) {
                if (setting.isPersistent()) {
                    settings.add(setting.getConfigKey(), setting.write());
                }
            }

            featureJson.add("settings", settings);
            features.add(feature.getName(), featureJson);
        }

        root.add("features", features);
        return root;
    }

    public void save(JsonObject root) {
        ConfigIO.write(path, root);
    }
}
