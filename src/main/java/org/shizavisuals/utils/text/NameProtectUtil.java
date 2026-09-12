package org.shizavisuals.utils.text;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.impl.misc.NameProtectFeature;

public final class NameProtectUtil {
    private static final String FEATURE_NAME = "NameProtect";

    private NameProtectUtil() {
    }

    public static String protect(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String realName = realName();
        String fakeName = fakeName();
        if (realName.isEmpty() || fakeName.isEmpty() || realName.equals(fakeName) || !text.contains(realName)) {
            return text;
        }

        return text.replace(realName, fakeName);
    }

    public static Component protect(Component component) {
        if (component == null) {
            return null;
        }

        String protectedText = protect(component.getString());
        if (protectedText.equals(component.getString())) {
            return component;
        }

        return Component.literal(protectedText).withStyle(component.getStyle());
    }

    public static FormattedText protect(FormattedText text) {
        if (text == null) {
            return null;
        }
        if (text instanceof Component component) {
            return protect(component);
        }

        String protectedText = protect(text.getString());
        if (protectedText.equals(text.getString())) {
            return text;
        }

        return FormattedText.of(protectedText);
    }

    public static FormattedCharSequence protect(FormattedCharSequence sequence) {
        if (sequence == null) {
            return null;
        }

        StringBuilder text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });

        String original = text.toString();
        String protectedText = protect(original);
        if (protectedText.equals(original)) {
            return sequence;
        }

        return FormattedCharSequence.forward(protectedText, Style.EMPTY);
    }

    private static String realName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return "";
        }
        if (minecraft.getUser() == null || minecraft.getUser().getName() == null) {
            return "";
        }
        return minecraft.getUser().getName();
    }

    private static String fakeName() {
        Feature feature = FeatureManager.INSTANCE.getFeature(FEATURE_NAME);
        if (!(feature instanceof NameProtectFeature nameProtect) || !nameProtect.isEnabled()) {
            return "";
        }

        String value = nameProtect.name.getValue();
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
