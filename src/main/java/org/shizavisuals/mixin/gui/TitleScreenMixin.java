package org.shizavisuals.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.shizavisuals.menu.ps4.MenuSettings;
import org.shizavisuals.menu.ps4.Ps4MainMenuScreen;
import org.shizavisuals.menu.ps4.Ps4Sound;
import org.shizavisuals.menu.ps4.Ps4WelcomeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    private static final AtomicBoolean REDIRECTING = new AtomicBoolean();

    /** Deluxe-style boot flow: welcome screen first, then the main menu. */
    private static volatile boolean WELCOME_SHOWN;

    @Inject(method = "init", at = @At("TAIL"))
    private void shizavisuals$replaceTitle(CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || minecraft.level != null
                || minecraft.gui.screen() != (Object) this) {
            return;
        }
        if (!REDIRECTING.compareAndSet(false, true)) {
            return;
        }
        minecraft.execute(() -> {
            try {
                if (minecraft.gui.screen() != (Object) this) {
                    return;
                }
                boolean vanillaRequested = MenuSettings.isUseVanillaMenu() && WELCOME_SHOWN;
                if (vanillaRequested) {
                    return; // show vanilla title screen
                }
                if (!WELCOME_SHOWN) {
                    WELCOME_SHOWN = true;
                    minecraft.setScreenAndShow(new Ps4WelcomeScreen());
                } else {
                    minecraft.setScreenAndShow(new Ps4MainMenuScreen());
                }
            } finally {
                REDIRECTING.set(false);
            }
        });
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void shizavisuals$addReturnButton(CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || minecraft.level != null
                || minecraft.gui.screen() != (Object) this || !MenuSettings.isUseVanillaMenu()) {
            return;
        }
        this.shizavisuals$addMenuButton(minecraft);
    }

    private void shizavisuals$addMenuButton(Minecraft minecraft) {
        Screen self = (Screen) (Object) this;
        ((org.shizavisuals.mixin.accessor.ScreenAccessor) self).shizavisuals$addRenderableWidget(
                Button.builder(Component.literal("\u2605 \u041c\u0435\u043d\u044e PS4"), button -> {
                    MenuSettings.setUseVanillaMenu(false);
                    Ps4Sound.startAmbientMusic();
                    minecraft.setScreenAndShow(new Ps4MainMenuScreen());
                }).bounds(10, 10, 120, 20).build());
    }
}
