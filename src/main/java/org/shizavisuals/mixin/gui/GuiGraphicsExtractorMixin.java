package org.shizavisuals.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.shizavisuals.utils.text.NameProtectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @ModifyVariable(
            method = {
                    "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
                    "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
                    "centeredText(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
            },
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private String protectString(String text) {
        return NameProtectUtil.protect(text);
    }

    @ModifyVariable(
            method = {
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
                    "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                    "textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V",
                    "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V",
                    "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V",
                    "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IILnet/minecraft/resources/Identifier;)V"
            },
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private Component protectComponent(Component component) {
        return NameProtectUtil.protect(component);
    }

    @ModifyVariable(
            method = {
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V",
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
                    "centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"
            },
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private FormattedCharSequence protectFormattedCharSequence(FormattedCharSequence sequence) {
        return NameProtectUtil.protect(sequence);
    }

    @ModifyVariable(
            method = {
                    "textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIII)V",
                    "textWithWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIIIZ)V"
            },
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private FormattedText protectFormattedText(FormattedText text) {
        return NameProtectUtil.protect(text);
    }
}
