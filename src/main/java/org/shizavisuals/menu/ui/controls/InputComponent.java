package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class InputComponent extends Component {
    private static final String EMPTY_PLACEHOLDER = "Empty";

    private final Supplier<String> valueSupplier;
    private final Consumer<String> valueConsumer;
    private String placeholder = EMPTY_PLACEHOLDER;
    private java.util.function.Predicate<String> filter = val -> true;
    private int mouseX;
    private int mouseY;
    private float alpha = 1.0F;
    private boolean focused;
    private boolean masked;
    private boolean allSelected;

    public InputComponent(Supplier<String> valueSupplier, Consumer<String> valueConsumer) {
        this.valueSupplier = valueSupplier;
        this.valueConsumer = valueConsumer;
    }

    public InputComponent filter(java.util.function.Predicate<String> filter) {
        this.filter = filter;
        return this;
    }

    public InputComponent place(Component owner, int x, int y, int width, int height, int mouseX, int mouseY) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        return this;
    }

    public InputComponent alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public InputComponent placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public InputComponent masked(boolean masked) {
        this.masked = masked;
        return this;
    }

    public void focus() {
        this.focused = true;
        this.allSelected = false;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void blur() {
        this.focused = false;
        this.allSelected = false;
    }

    public boolean handleClick(int mouseX, int mouseY) {
        this.focused = contains(mouseX, mouseY);
        this.allSelected = false;
        return this.focused;
    }

    public boolean handleKey(int key) {
        if (!this.focused) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            blur();
            return true;
        }
        if (MenuClipboard.shortcutDown()) {
            if (key == GLFW.GLFW_KEY_A) {
                this.allSelected = !value().isEmpty();
                return true;
            }
            if (key == GLFW.GLFW_KEY_C) {
                if (this.allSelected) {
                    MenuClipboard.set(value());
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_X) {
                if (this.allSelected) {
                    MenuClipboard.set(value());
                    replaceValue("");
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_V) {
                String base = this.allSelected ? "" : value();
                replaceValue(base + MenuClipboard.get());
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (this.allSelected) {
                replaceValue("");
            } else {
                deleteLastCodePoint();
            }
        }
        return true;
    }

    public boolean handleCharacter(int codePoint) {
        if (!this.focused) {
            return false;
        }
        if (MenuClipboard.shortcutDown()) {
            return true;
        }
        if (!Character.isISOControl(codePoint) && Character.isValidCodePoint(codePoint)) {
            String base = this.allSelected ? "" : value();
            replaceValue(base + new String(Character.toChars(codePoint)));
        }
        return true;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        boolean hovered = contains(this.mouseX, this.mouseY);
        String value = value();
        boolean active = this.focused || !value.isEmpty();
        int background = (this.focused || hovered) ? Theme.Colors.BACKGROUND_SURFACE_S : Theme.Colors.BACKGROUND_SURFACE_M;
        int textColor = this.focused ? Theme.Colors.TEXT_TEXT : active ? Theme.Colors.TEXT_TITLE : Theme.Colors.TEXT_GHOST;
        UiFontStyle style = active ? UiFontStyle.MEDIUM : UiFontStyle.REGULAR_TRACKED;
        String visibleValue = this.masked
                ? "\u2022".repeat(value.codePointCount(0, value.length()))
                : value;
        String displayValue = this.focused && !this.allSelected
                ? visibleValue + "|"
                : active ? visibleValue : MenuText.ui(this.placeholder);

        Render2DUtil.rect(x(), y(), width(), height())
                .color(alpha(background, this.alpha))
                .radius(px(Theme.Sizes.INPUT_RADIUS))
                .border(Math.max(0.5F, px(0.5F)), alpha(Theme.Colors.OUTLINES_SMALL, this.alpha))
                .draw();

        float size = px((float) Theme.Sizes.INPUT_TEXT_SIZE);
        float letterSpacing = style.letterSpacingEm() * size;
        float textWidth = UiFonts.sfProDisplay().measureWidth(displayValue, size, letterSpacing);
        float textX = x() + px(Theme.Sizes.INPUT_PADDING_X);
        float maxTextWidth = Math.max(1, px(Theme.Sizes.INPUT_TEXT_WIDTH));
        float drawX = textWidth > maxTextWidth ? textX - Math.round(textWidth - maxTextWidth) : textX;
        float textY = UiFonts.sfProDisplay().centeredTextY(y() + height() / 2.0F, size);
        float scissorHeight = Math.max(1, height() - px(Theme.Sizes.INPUT_PADDING_Y * 2));

        Render2DUtil.pushScissor(textX, y() + px(Theme.Sizes.INPUT_PADDING_Y), maxTextWidth, scissorHeight);
        if (this.focused && this.allSelected && !visibleValue.isEmpty()) {
            Render2DUtil.rect(
                            drawX,
                            y() + px(Theme.Sizes.INPUT_PADDING_Y),
                            UiFonts.sfProDisplay().measureWidth(
                                    visibleValue,
                                    size,
                                    letterSpacing
                            ),
                            scissorHeight
                    )
                    .color(alpha(Theme.getAccent(), this.alpha * 0.55F))
                    .radius(px(2.0F))
                    .draw();
        }
        Render2DUtil.text(drawX, textY, size, displayValue)
                .style(style)
                .color(alpha(textColor, this.alpha))
                .draw();
        Render2DUtil.popScissor();
    }

    private String value() {
        String value = this.valueSupplier.get();
        return value == null ? "" : value;
    }

    private void deleteLastCodePoint() {
        String value = value();
        if (value.isEmpty()) {
            return;
        }
        int lastCodePoint = value.codePointBefore(value.length());
        replaceValue(value.substring(
                0,
                value.length() - Character.charCount(lastCodePoint)
        ));
    }

    private void replaceValue(String value) {
        String resolved = value == null ? "" : value.replaceAll("\\R", " ");
        if (this.filter.test(resolved)) {
            this.valueConsumer.accept(resolved);
            this.allSelected = false;
        }
    }
}
