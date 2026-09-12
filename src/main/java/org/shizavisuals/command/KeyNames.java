package org.shizavisuals.command;

import lombok.experimental.UtilityClass;
import org.shizavisuals.feature.setting.BindSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@UtilityClass
public class KeyNames {
    private final Map<String, Integer> SPECIAL = Map.ofEntries(
            Map.entry("SPACE", GLFW.GLFW_KEY_SPACE),
            Map.entry("TAB", GLFW.GLFW_KEY_TAB),
            Map.entry("ENTER", GLFW.GLFW_KEY_ENTER),
            Map.entry("BACKSPACE", GLFW.GLFW_KEY_BACKSPACE),
            Map.entry("INSERT", GLFW.GLFW_KEY_INSERT),
            Map.entry("DELETE", GLFW.GLFW_KEY_DELETE),
            Map.entry("HOME", GLFW.GLFW_KEY_HOME),
            Map.entry("END", GLFW.GLFW_KEY_END),
            Map.entry("PAGEUP", GLFW.GLFW_KEY_PAGE_UP),
            Map.entry("PAGEDOWN", GLFW.GLFW_KEY_PAGE_DOWN),
            Map.entry("UP", GLFW.GLFW_KEY_UP),
            Map.entry("DOWN", GLFW.GLFW_KEY_DOWN),
            Map.entry("LEFT", GLFW.GLFW_KEY_LEFT),
            Map.entry("RIGHT", GLFW.GLFW_KEY_RIGHT),
            Map.entry("LSHIFT", GLFW.GLFW_KEY_LEFT_SHIFT),
            Map.entry("RSHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT),
            Map.entry("LCTRL", GLFW.GLFW_KEY_LEFT_CONTROL),
            Map.entry("RCTRL", GLFW.GLFW_KEY_RIGHT_CONTROL),
            Map.entry("LALT", GLFW.GLFW_KEY_LEFT_ALT),
            Map.entry("RALT", GLFW.GLFW_KEY_RIGHT_ALT),
            Map.entry("CAPSLOCK", GLFW.GLFW_KEY_CAPS_LOCK),
            Map.entry("GRAVE", GLFW.GLFW_KEY_GRAVE_ACCENT),
            Map.entry("MINUS", GLFW.GLFW_KEY_MINUS),
            Map.entry("EQUAL", GLFW.GLFW_KEY_EQUAL),
            Map.entry("COMMA", GLFW.GLFW_KEY_COMMA),
            Map.entry("PERIOD", GLFW.GLFW_KEY_PERIOD),
            Map.entry("SLASH", GLFW.GLFW_KEY_SLASH),
            Map.entry("SEMICOLON", GLFW.GLFW_KEY_SEMICOLON),
            Map.entry("APOSTROPHE", GLFW.GLFW_KEY_APOSTROPHE),
            Map.entry("LBRACKET", GLFW.GLFW_KEY_LEFT_BRACKET),
            Map.entry("RBRACKET", GLFW.GLFW_KEY_RIGHT_BRACKET),
            Map.entry("BACKSLASH", GLFW.GLFW_KEY_BACKSLASH)
    );
    private final List<String> SUGGESTIONS = createSuggestions();

    public List<String> suggestions() {
        return SUGGESTIONS;
    }

    public int parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return BindSetting.UNBOUND;
        }
        String name = raw.trim().toUpperCase(Locale.ROOT).replace("_", "");

        int mouse = parseMouse(name);
        if (mouse != BindSetting.UNBOUND) {
            return mouse;
        }

        if (name.length() == 1) {
            char c = name.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return BindSetting.key(c);
            }
        }
        if (name.length() >= 2 && name.charAt(0) == 'F') {
            try {
                int fn = Integer.parseInt(name.substring(1));
                if (fn >= 1 && fn <= 25) {
                    return BindSetting.key(GLFW.GLFW_KEY_F1 + fn - 1);
                }
            } catch (NumberFormatException ignored) {

            }
        }

        Integer special = SPECIAL.get(name);
        return special != null ? BindSetting.key(special) : BindSetting.UNBOUND;
    }

    private int parseMouse(String name) {
        return switch (name) {
            case "LMB", "MOUSELEFT", "MOUSE1" -> BindSetting.mouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            case "RMB", "MOUSERIGHT", "MOUSE2" -> BindSetting.mouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            case "MMB", "MOUSEMIDDLE", "MOUSE3" -> BindSetting.mouse(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
            case "MOUSE4", "M4" -> BindSetting.mouse(GLFW.GLFW_MOUSE_BUTTON_4);
            case "MOUSE5", "M5" -> BindSetting.mouse(GLFW.GLFW_MOUSE_BUTTON_5);
            case "MOUSE6" -> BindSetting.mouse(GLFW.GLFW_MOUSE_BUTTON_6);
            case "MOUSE7" -> BindSetting.mouse(GLFW.GLFW_MOUSE_BUTTON_7);
            case "MOUSE8" -> BindSetting.mouse(GLFW.GLFW_MOUSE_BUTTON_8);
            default -> BindSetting.UNBOUND;
        };
    }

    private List<String> createSuggestions() {
        List<String> names = new ArrayList<>();
        for (char key = 'A'; key <= 'Z'; key++) {
            names.add(String.valueOf(key));
        }
        for (char key = '0'; key <= '9'; key++) {
            names.add(String.valueOf(key));
        }
        for (int key = 1; key <= 25; key++) {
            names.add("F" + key);
        }
        SPECIAL.keySet().stream().sorted(Comparator.naturalOrder()).forEach(names::add);
        names.addAll(List.of("LMB", "RMB", "MMB", "MOUSE4", "MOUSE5", "MOUSE6", "MOUSE7", "MOUSE8"));
        return List.copyOf(names);
    }
}
