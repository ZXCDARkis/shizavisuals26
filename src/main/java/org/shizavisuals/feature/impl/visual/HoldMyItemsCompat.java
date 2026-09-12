package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class HoldMyItemsCompat {
    private static final long ATTACK_STYLE_WINDOW_NANOS = 250_000_000L;
    private static final String ACCESSOR_CLASS =
            "com.holdmylua.source.access.LivingEntityAccessor";
    private static final String RESET_MAIN_HAND_SWING =
            "hMI5_0$resetMainHandSwing";
    private static final Method RESET_METHOD = findResetMethod();
    private static int auraAttackIndex;
    private static long forceGroundStyleUntil;

    private HoldMyItemsCompat() {
    }

    public static void beginMainHandAttack(LocalPlayer player) {
        if (player == null
                || !HoldMyItemsFeature.isActive()
                || RESET_METHOD == null
                || !RESET_METHOD.getDeclaringClass().isInstance(player)) {
            return;
        }
        try {
            RESET_METHOD.invoke(player, false);

            boolean groundStyle = auraAttackIndex++ % 3 != 2;
            forceGroundStyleUntil = groundStyle
                    ? System.nanoTime() + ATTACK_STYLE_WINDOW_NANOS
                    : 0L;
        } catch (IllegalAccessException | InvocationTargetException ignored) {

        }
    }

    public static boolean shouldUseGroundAttackStyle(AbstractClientPlayer player) {
        return player != null
                && player == Minecraft.getInstance().player
                && System.nanoTime() < forceGroundStyleUntil;
    }

    private static Method findResetMethod() {
        try {
            Class<?> accessor = Class.forName(
                    ACCESSOR_CLASS,
                    false,
                    HoldMyItemsCompat.class.getClassLoader()
            );
            return accessor.getMethod(RESET_MAIN_HAND_SWING, boolean.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }
}
