package org.shizavisuals.feature.impl.visual;

import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.BooleanSetting;
import org.shizavisuals.feature.setting.NumberSetting;

/**
 * 3D-инвентарь: экран контейнера рисуется на плавающем кваде в мире,
 * камера отъезжает на орбиту перед/за игроком (порт механики spatial-gui).
 */
public final class SpatialInventoryFeature extends Feature {
    // Камера
    public final NumberSetting cameraDistance = register(new NumberSetting(
            "Camera Distance", 1.7D, 0.5D, 5.0D, 0.05D, "блоков").configKey("render.spatial.cameraDistance"));
    public final NumberSetting cameraHeightOffset = register(new NumberSetting(
            "Camera Height", 1.5D, -4.0D, 4.0D, 0.05D, "блоков").configKey("render.spatial.cameraHeight"));
    public final NumberSetting cameraSideOffset = register(new NumberSetting(
            "Camera Side", -1.2D, -3.0D, 3.0D, 0.05D, "блоков").configKey("render.spatial.cameraSide"));
    public final NumberSetting cameraOrbitYaw = register(new NumberSetting(
            "Camera Orbit", 0.0D, -180.0D, 180.0D, 5.0D, "°").configKey("render.spatial.cameraOrbit"));
    public final NumberSetting cameraTargetPitch = register(new NumberSetting(
            "Camera Pitch", 10.0D, -90.0D, 90.0D, 1.0D, "°").configKey("render.spatial.cameraPitch"));
    public final NumberSetting parallaxYaw = register(new NumberSetting(
            "Parallax Yaw", 0.1D, 0.0D, 0.5D, 0.01D, "").configKey("render.spatial.parallaxYaw"));
    public final NumberSetting parallaxPitch = register(new NumberSetting(
            "Parallax Pitch", 0.03D, 0.0D, 0.2D, 0.01D, "").configKey("render.spatial.parallaxPitch"));

    // Экран-квад
    public final NumberSetting screenDistance = register(new NumberSetting(
            "Screen Distance", 2.5D, 0.5D, 8.0D, 0.05D, "блоков").configKey("render.spatial.screenDistance"));
    public final NumberSetting screenSideOffset = register(new NumberSetting(
            "Screen Side", -0.6D, -3.0D, 3.0D, 0.05D, "блоков").configKey("render.spatial.screenSide"));
    public final NumberSetting screenHeightOffset = register(new NumberSetting(
            "Screen Height", -0.4D, -3.0D, 3.0D, 0.05D, "блоков").configKey("render.spatial.screenHeight"));
    public final NumberSetting screenYawOffset = register(new NumberSetting(
            "Screen Yaw", 160.0D, -180.0D, 180.0D, 5.0D, "°").configKey("render.spatial.screenYaw"));
    public final NumberSetting screenPitchOffset = register(new NumberSetting(
            "Screen Pitch", 0.0D, -90.0D, 90.0D, 1.0D, "°").configKey("render.spatial.screenPitch"));
    public final NumberSetting screenScale = register(new NumberSetting(
            "Screen Scale", 3.5D, 0.5D, 10.0D, 0.1D, "x").configKey("render.spatial.screenScale"));
    public final NumberSetting screenOpacity = register(new NumberSetting(
            "Screen Opacity", 100.0D, 20.0D, 100.0D, 5.0D, "%").configKey("render.spatial.screenOpacity"));

    // Анимации
    public final NumberSetting transitionMs = register(new NumberSetting(
            "Transition Time", 300.0D, 0.0D, 1500.0D, 50.0D, "мс").configKey("render.spatial.transition"));
    public final NumberSetting fadeMs = register(new NumberSetting(
            "Fade Time", 150.0D, 0.0D, 1000.0D, 25.0D, "мс").configKey("render.spatial.fade"));
    public final NumberSetting scaleAnimMs = register(new NumberSetting(
            "Scale Time", 300.0D, 0.0D, 1000.0D, 50.0D, "мс").configKey("render.spatial.scaleAnim"));
    public final NumberSetting startScalePercent = register(new NumberSetting(
            "Start Scale", 75.0D, 10.0D, 100.0D, 5.0D, "%").configKey("render.spatial.startScale"));

    // Поведение
    public final BooleanSetting hideHotbar = register(new BooleanSetting(
            "Hide Hotbar", true).configKey("render.spatial.hideHotbar"));

    public SpatialInventoryFeature() {
        super("SpatialInventory", "Инвентарь на 3D-кваде в мире, камера отъезжает (как spatial-gui)", FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static SpatialInventoryFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(SpatialInventoryFeature.class);
    }
}
