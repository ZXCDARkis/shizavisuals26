package org.shizavisuals.mixin.gui;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import org.shizavisuals.command.CommandManager;
import org.shizavisuals.utils.text.SensitiveChatMask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    private Screen screen;
    @Shadow
    @Final
    private EditBox input;
    @Shadow
    @Final
    private List<FormattedCharSequence> commandUsage;
    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow
    private boolean keepSuggestions;
    @Shadow
    private boolean allowSuggestions;

    @Shadow
    public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Shadow
    public abstract void hide();

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void suggestClientCommands(CallbackInfo ci) {
        if (!(this.screen instanceof ChatScreen)) {
            return;
        }
        String text = this.input.getValue();
        if (SensitiveChatMask.hasSecret(text)) {
            ci.cancel();
            this.input.setSuggestion(null);
            hide();
            this.commandUsage.clear();
            this.pendingSuggestions = null;
            return;
        }
        String prefix = CommandManager.INSTANCE.getPrefix();
        if (prefix.equals("/") || !text.startsWith(prefix)) {
            return;
        }

        ci.cancel();
        if (this.keepSuggestions) {
            return;
        }

        this.input.setSuggestion(null);
        hide();
        this.commandUsage.clear();

        StringReader reader = new StringReader(text);
        reader.setCursor(prefix.length());
        CommandDispatcher<Object> dispatcher = CommandManager.INSTANCE.getDispatcher();
        ParseResults<Object> parse = dispatcher.parse(reader, new Object());
        int cursor = Math.max(this.input.getCursorPosition(), prefix.length());
        CompletableFuture<Suggestions> future = dispatcher.getCompletionSuggestions(parse, cursor);
        this.pendingSuggestions = future;
        future.thenRun(() -> {
            if (this.pendingSuggestions == future && future.isDone()
                    && !future.join().isEmpty() && this.allowSuggestions) {
                showSuggestions(false);
            }
        });
    }
}
