package org.shizavisuals.feature.impl.visual;

import net.fabricmc.loader.api.FabricLoader;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/**
 * Тот же принцип, что и HoldMyItemsFeature, но анимации от первого лица
 * отдаётся моду Punchy: пока функции выключена, Punchy рисует ванильные
 * руки/предметы, а при включении рисует свои анимации.
 * <p>
 * Работает только через публичное API Punchy (punchy.api.PunchyApi) через
 * рефлексию, поэтому компилируется и безопасно ничего не делает, если
 * Punchy отсутствует в сборке.
 */
public final class HoldMyItemsPunchyFeature extends Feature {
    private static final Logger LOGGER = LoggerFactory.getLogger(HoldMyItemsPunchyFeature.class);

    /** ID, под которым ShizaVisuals регистрирует свой override в Punchy API. */
    private static final String PUNCHY_OWNER_ID = "shizavisuals_holdmyitems";
    private static final String PUNCHY_API_CLASS = "punchy.api.PunchyApi";
    private static final String USE_MINECRAFT_HANDS = "useMinecraftHands";

    /** Подпись по умолчанию: включённая функция = анимации Punchy, выключенная = ваниль. */
    private static final BooleanSupplier USE_PUNCHY_WHILE = () -> !HoldMyItemsPunchyFeature.isActive();

    public HoldMyItemsPunchyFeature() {
        super("HoldMyItemsPunchy", "Анимированные руки и предметы от первого лица через Punchy", FeatureCategory.VISUAL, BindSetting.UNBOUND);
        setVisible(isPunchyLoaded());
    }

    public static boolean isActive() {
        return FeatureManager.INSTANCE.getEnabled(HoldMyItemsPunchyFeature.class) != null;
    }

    /** Установлен ли мод Punchy в текущей сборке. */
    public static boolean isPunchyLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded("punchy");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Вызывается один раз при старте клиента: сразу регистрирует состояние
     * в Punchy API. Так как override живой (BooleanSupplier), переключение
     * функции дальше не требует ни перерегистрации, ни перезагрузки ресурсов.
     */
    public static void syncOnClientStart() {
        if (!isPunchyLoaded()) {
            return;
        }
        try {
            Class<?> api = Class.forName(PUNCHY_API_CLASS, false, HoldMyItemsPunchyFeature.class.getClassLoader());
            Method useMinecraftHands = api.getMethod(USE_MINECRAFT_HANDS, String.class, BooleanSupplier.class);
            useMinecraftHands.invoke(null, PUNCHY_OWNER_ID, (BooleanSupplier) USE_PUNCHY_WHILE);
            LOGGER.debug("HoldMyItemsPunchy: Punchy override registered (owner {})", PUNCHY_OWNER_ID);
        } catch (Throwable error) {
            LOGGER.warn("HoldMyItemsPunchy: registering Punchy override failed", error);
        }
    }
}
