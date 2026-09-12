package org.shizavisuals.utils.text;

import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class ChatUtil {
    private final Pattern EMOJI_ALIAS = Pattern.compile(":[A-Za-z0-9_+\\-]+:");
    private final String PREFIX = "ShizaVisuals: ";

    public void print(String message) {
        info(message);
    }

    public void header(String message) {
        send(":sparkles:  " + message);
    }

    public void info(String message) {
        send(":information_source:  " + message);
    }

    public void success(String message) {
        send(":white_check_mark:  " + message);
    }

    public void error(String message) {
        send(":x:  " + message);
    }

    public void usage(String message) {
        send(":keyboard:  " + message);
    }

    public void entry(String emoji, String title, String detail) {
        send(emoji + "  " + title + (detail == null || detail.isBlank() ? "" : "  •  " + detail));
    }

    public void send(String message) {
        Minecraft mc = MinecraftContext.mc;
        if (mc.gui != null) {
            mc.gui.hud.getChat().addClientSystemMessage(gradient(PREFIX + message));
        }
    }

    private MutableComponent gradient(String text) {
        MutableComponent result = Component.empty();
        int startColor = Theme.getAccent();
        int endColor = ColorUtil.lerp(startColor, ColorUtil.WHITE, 0.62F);
        Matcher matcher = EMOJI_ALIAS.matcher(text);
        int cursor = 0;

        while (cursor < text.length()) {
            if (matcher.find(cursor) && matcher.start() == cursor) {
                appendColored(result, matcher.group(), gradientColor(startColor, endColor, cursor, text.length()));
                cursor = matcher.end();
                continue;
            }
            int codePoint = text.codePointAt(cursor);
            appendColored(result, new String(Character.toChars(codePoint)),
                    gradientColor(startColor, endColor, cursor, text.length()));
            cursor += Character.charCount(codePoint);
        }
        return result;
    }

    private static int gradientColor(int start, int end, int index, int length) {
        return ColorUtil.lerp(start, end, length <= 1 ? 0.0F : index / (float) (length - 1));
    }

    private static void appendColored(MutableComponent target, String text, int color) {
        target.append(Component.literal(text).withStyle(Style.EMPTY.withColor(color & 0xFFFFFF)));
    }
}
