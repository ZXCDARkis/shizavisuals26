package org.shizavisuals.mixin.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.shizavisuals.command.CommandManager;
import org.shizavisuals.utils.text.SensitiveChatMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow
    protected EditBox input;
    @Shadow
    private CommandSuggestions commandSuggestions;

    @Unique
    private Button privacyButton;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addPrivacyButton(CallbackInfo ci) {
        this.privacyButton = Button.builder(
                        privacyLabel(),
                        ignored -> {
                            SensitiveChatMask.toggle();
                            this.privacyButton.setMessage(privacyLabel());
                            this.commandSuggestions.updateCommandInfo();
                            focusInputAfterClick();
                        }
                )
                .bounds(this.width - 150, this.height - 40, 146, 20)
                .build();
        addRenderableWidget(this.privacyButton);
    }

    @Inject(method = "formatChat", at = @At("HEAD"), cancellable = true)
    private void maskSensitiveText(String text, int offset,
                                         CallbackInfoReturnable<FormattedCharSequence> cir) {
        FormattedCharSequence masked = SensitiveChatMask.format(this.input.getValue(), text, offset);
        if (masked != null) {
            cir.setReturnValue(masked);
        }
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void keepKeyboardFocusInInput(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        this.setFocused(this.input);
        this.input.setFocused(true);
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void handleClientCommand(String message, boolean addToRecentChat, CallbackInfo ci) {
        ChatScreen screen = (ChatScreen) (Object) this;
        String normalized = screen.normalizeChatMessage(message);
        if (!CommandManager.INSTANCE.handleChat(normalized)) {
            return;
        }
        if (addToRecentChat) {
            Minecraft.getInstance().gui.hud.getChat().addRecentChat(normalized);
        }
        ci.cancel();
    }

    @Unique
    private static Component privacyLabel() {
        return Component.literal("Скрыть данные: " + (SensitiveChatMask.isEnabled() ? "True" : "False"));
    }

    @Unique
    private void focusInputAfterClick() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.gui.screen() == (ChatScreen) (Object) this) {
                this.setFocused(this.input);
                this.input.setFocused(true);
            }
        });
    }
}
