package org.shizavisuals.feature.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.shizavisuals.feature.BindMode;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class BindSetting extends Setting<List<Integer>> {
    public static final int UNBOUND = -1;
    private static final int MOUSE_FLAG = 1 << 30;

    private record Meta(BindMode mode, boolean visible) {
        static final Meta DEFAULT = new Meta(BindMode.TOGGLE, true);

        Meta withMode(BindMode mode) {
            return new Meta(mode, this.visible);
        }

        Meta withVisible(boolean visible) {
            return new Meta(this.mode, visible);
        }
    }

    private List<Meta> meta = List.of();

    public BindSetting(String name) {
        this(name, List.of());
    }

    public BindSetting(String name, int defaultValue) {
        this(name, defaultValue == UNBOUND ? List.of() : List.of(defaultValue));
    }

    public BindSetting(String name, List<Integer> defaultValue) {
        super(name, defaultValue == null ? List.of() : List.copyOf(defaultValue));
    }

    public static int key(int keyCode) {
        return keyCode;
    }

    public static int mouse(int button) {
        return MOUSE_FLAG | button;
    }

    public static boolean isMouse(int bindCode) {
        return bindCode != UNBOUND && (bindCode & MOUSE_FLAG) != 0;
    }

    public static boolean isKeyboard(int bindCode) {
        return bindCode != UNBOUND && !isMouse(bindCode);
    }

    public static int rawButton(int bindCode) {
        return bindCode & ~MOUSE_FLAG;
    }

    public boolean isBound() {
        return !getValue().isEmpty();
    }

    public int size() {
        return getValue().size();
    }

    public boolean isEmpty() {
        return getValue().isEmpty();
    }

    public boolean hasIndex(int index) {
        return index >= 0 && index < getValue().size();
    }

    public int get(int index) {
        return getValue().get(index);
    }

    public boolean contains(int bindCode) {
        return getValue().contains(bindCode);
    }

    public boolean matches(int keyCode) {
        return matchesCode(key(keyCode));
    }

    public boolean matchesMouse(int button) {
        return matchesCode(mouse(button));
    }

    public boolean matchesCode(int bindCode) {
        return bindCode != UNBOUND && getValue().contains(bindCode);
    }

    public void setSingle(int bindCode) {
        Meta preserved = getMeta(0);
        setValue(bindCode == UNBOUND ? List.of() : List.of(bindCode));
        this.meta = getValue().isEmpty() ? List.of() : List.of(preserved);
    }

    public int add(int bindCode) {
        int normalized = normalizeCode(bindCode);
        if (normalized == UNBOUND) {
            return -1;
        }

        List<Integer> binds = new ArrayList<>(getValue());
        int existingIndex = binds.indexOf(normalized);
        if (existingIndex >= 0) {
            return existingIndex;
        }

        List<Meta> nextMeta = new ArrayList<>(alignedMeta());
        binds.add(normalized);
        setValue(binds);
        nextMeta.add(Meta.DEFAULT);
        this.meta = fitMeta(nextMeta, getValue().size());
        return binds.size() - 1;
    }

    public int setAt(int index, int bindCode) {
        if (index < 0) {
            return add(bindCode);
        }

        int normalized = normalizeCode(bindCode);
        List<Integer> binds = new ArrayList<>(getValue());
        if (normalized == UNBOUND) {
            removeAt(index);
            return -1;
        }

        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        if (index >= binds.size()) {
            binds.add(normalized);
            bindMeta.add(Meta.DEFAULT);
        } else {
            binds.set(index, normalized);
        }

        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        List<Integer> normalizedBinds = new ArrayList<>();
        List<Meta> normalizedMeta = new ArrayList<>();
        int resolvedIndex = -1;
        for (int i = 0; i < binds.size(); i++) {
            int code = binds.get(i);
            Meta entry = i < bindMeta.size() ? bindMeta.get(i) : Meta.DEFAULT;
            boolean added = unique.add(code);
            if (added) {
                normalizedBinds.add(code);
                normalizedMeta.add(entry);
                if (i == index) {
                    resolvedIndex = normalizedBinds.size() - 1;
                }
            }
            if (!added && code == normalized && i == index) {
                resolvedIndex = normalizedBinds.indexOf(normalized);
                if (resolvedIndex >= 0) {
                    normalizedMeta.set(resolvedIndex, entry);
                }
            }
        }

        setValue(normalizedBinds);
        this.meta = fitMeta(normalizedMeta, getValue().size());
        return resolvedIndex >= 0 ? resolvedIndex : getValue().indexOf(normalized);
    }

    public void removeAt(int index) {
        if (!hasIndex(index)) {
            return;
        }
        List<Integer> binds = new ArrayList<>(getValue());
        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        binds.remove(index);
        if (index < bindMeta.size()) {
            bindMeta.remove(index);
        }
        setValue(binds);
        this.meta = fitMeta(bindMeta, getValue().size());
    }

    public void clear() {
        setValue(List.of());
        this.meta = List.of();
    }

    public BindMode getMode(int index, BindMode fallback) {
        if (!hasIndex(index)) {
            return fallback == null ? BindMode.TOGGLE : fallback;
        }
        return getMeta(index).mode();
    }

    public BindMode getModeForCode(int bindCode, BindMode fallback) {
        int index = getValue().indexOf(bindCode);
        return getMode(index, fallback);
    }

    public void setMode(int index, BindMode mode) {
        if (!hasIndex(index) || mode == null) {
            return;
        }
        updateMeta(index, getMeta(index).withMode(mode));
    }

    public void setAllModes(BindMode mode) {
        if (mode == null || getValue().isEmpty()) {
            this.meta = List.of();
            return;
        }

        List<Meta> bindMeta = new ArrayList<>();
        for (Meta entry : alignedMeta()) {
            bindMeta.add(entry.withMode(mode));
        }
        this.meta = List.copyOf(bindMeta);
    }

    public boolean isVisibleAt(int index) {
        return !hasIndex(index) || getMeta(index).visible();
    }

    public void setVisibleAt(int index, boolean visible) {
        if (!hasIndex(index)) {
            return;
        }
        updateMeta(index, getMeta(index).withVisible(visible));
    }

    public JsonElement writeModes() {
        JsonArray array = new JsonArray();
        for (Meta entry : alignedMeta()) {
            array.add(entry.mode().name());
        }
        return array;
    }

    public void readModes(JsonElement element) {
        List<BindMode> bindModes = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                BindMode mode = readMode(item);
                if (mode != null) {
                    bindModes.add(mode);
                }
            }
        } else if (element != null && !element.isJsonNull()) {
            BindMode mode = readMode(element);
            if (mode != null) {
                bindModes.add(mode);
            }
        }

        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        for (int i = 0; i < bindMeta.size() && i < bindModes.size(); i++) {
            bindMeta.set(i, bindMeta.get(i).withMode(bindModes.get(i)));
        }
        this.meta = List.copyOf(bindMeta);
    }

    public JsonElement writeVisibility() {
        JsonArray array = new JsonArray();
        for (Meta entry : alignedMeta()) {
            array.add(entry.visible());
        }
        return array;
    }

    public void readVisibility(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return;
        }

        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < bindMeta.size() && i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item != null && item.isJsonPrimitive()) {
                bindMeta.set(i, bindMeta.get(i).withVisible(item.getAsBoolean()));
            }
        }
        this.meta = List.copyOf(bindMeta);
    }

    public String getDisplayValue() {
        return isBound() ? getDisplayValue(0) : "None";
    }

    public String getDisplayValue(int index) {
        if (!hasIndex(index)) {
            return "None";
        }
        return describe(get(index));
    }

    public static String describe(int bindCode) {
        if (bindCode == UNBOUND) {
            return "None";
        }

        if (isMouse(bindCode)) {
            return switch (rawButton(bindCode)) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "Mouse Left";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "Mouse Right";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "Mouse Middle";
                case GLFW.GLFW_MOUSE_BUTTON_4 -> "Mouse 4";
                case GLFW.GLFW_MOUSE_BUTTON_5 -> "Mouse 5";
                case GLFW.GLFW_MOUSE_BUTTON_6 -> "Mouse 6";
                case GLFW.GLFW_MOUSE_BUTTON_7 -> "Mouse 7";
                case GLFW.GLFW_MOUSE_BUTTON_8 -> "Mouse 8";
                default -> "Mouse " + rawButton(bindCode);
            };
        }

        if (bindCode > GLFW.GLFW_KEY_SPACE && bindCode <= GLFW.GLFW_KEY_GRAVE_ACCENT) {
            return String.valueOf((char) bindCode);
        }
        if (bindCode >= GLFW.GLFW_KEY_F1 && bindCode <= GLFW.GLFW_KEY_F25) {
            return "F" + (bindCode - GLFW.GLFW_KEY_F1 + 1);
        }
        if (bindCode >= GLFW.GLFW_KEY_KP_0 && bindCode <= GLFW.GLFW_KEY_KP_9) {
            return "Num " + (bindCode - GLFW.GLFW_KEY_KP_0);
        }

        return switch (bindCode) {
            case GLFW.GLFW_KEY_KP_DECIMAL -> "Num .";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "Num /";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Num *";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num -";
            case GLFW.GLFW_KEY_KP_ADD -> "Num +";
            case GLFW.GLFW_KEY_KP_ENTER -> "Num Enter";
            case GLFW.GLFW_KEY_KP_EQUAL -> "Num =";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            default -> "Key " + bindCode;
        };
    }

    @Override
    protected List<Integer> normalize(List<Integer> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (Integer bindCode : value) {
            int normalized = normalizeCode(bindCode);
            if (normalized != UNBOUND) {
                unique.add(normalized);
            }
        }
        return unique.isEmpty() ? List.of() : List.copyOf(unique);
    }

    @Override
    protected JsonElement writeValue(List<Integer> value) {
        JsonArray array = new JsonArray();
        for (Integer bindCode : normalize(value)) {
            array.add(bindCode);
        }
        return array;
    }

    @Override
    protected List<Integer> readValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonArray()) {
            List<Integer> binds = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                if (item != null && item.isJsonPrimitive()) {
                    binds.add(item.getAsInt());
                }
            }
            return binds;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                int bindCode = primitive.getAsInt();
                return bindCode == UNBOUND ? List.of() : List.of(bindCode);
            }
        }
        return List.of();
    }

    private BindMode readMode(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }

        try {
            return BindMode.valueOf(element.getAsString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Meta getMeta(int index) {
        return index >= 0 && index < this.meta.size() ? this.meta.get(index) : Meta.DEFAULT;
    }

    private void updateMeta(int index, Meta entry) {
        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        bindMeta.set(index, entry);
        this.meta = List.copyOf(bindMeta);
    }

    private List<Meta> alignedMeta() {
        return fitMeta(this.meta, getValue().size());
    }

    private List<Meta> fitMeta(List<Meta> source, int size) {
        if (size <= 0) {
            return List.of();
        }

        List<Meta> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Meta entry = source != null && i < source.size() ? source.get(i) : null;
            result.add(entry == null ? Meta.DEFAULT : entry);
        }
        return List.copyOf(result);
    }

    private int normalizeCode(Integer value) {
        if (value == null) {
            return UNBOUND;
        }
        return value < UNBOUND ? UNBOUND : value;
    }
}
