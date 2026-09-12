package org.shizavisuals.menu.ui.controls;

import org.shizavisuals.menu.ui.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.feature.setting.InputBindSetting;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.lwjgl.glfw.GLFW;

public final class InputBindComponent extends Component {
    private static final String LISTENING_LABEL = "...";

    private final InputBindSetting setting;
    private float alpha = 1.0F;
    private boolean listening;

    public InputBindComponent(InputBindSetting setting) {
        this.setting = setting;
    }

    public InputBindComponent place(Component owner, int x, int y, int width, int height) {
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(height));
        return this;
    }

    public InputBindComponent alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public boolean isListening() {
        return this.listening;
    }

    public void stopListening() {
        this.listening = false;
    }

    public boolean handleClick(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            this.listening = false;
            return false;
        }
        this.listening = !this.listening;
        return true;
    }

    public boolean captureKey(int key) {
        if (!this.listening) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            this.setting.clear();
        } else if (key != GLFW.GLFW_KEY_ESCAPE) {
            this.setting.setKey(key);
        }
        this.listening = false;
        return true;
    }

    public boolean captureMouse(int button) {
        if (!this.listening || button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        this.setting.setMouse(button);
        this.listening = false;
        return true;
    }

    public static int pillWidth(String value) {
        float textSize = Theme.Sizes.INPUT_BIND_TEXT_SIZE;
        float textWidth = UiFonts.sfProDisplay().measureWidth(value == null ? "" : value, textSize, UiFontStyle.MEDIUM.letterSpacingEm() * textSize);
        return Math.max(Theme.Sizes.INPUT_BIND_WIDTH, Math.round(textWidth) + Theme.Sizes.INPUT_BIND_PADDING_X * 2);
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        Render2DUtil.rect(x(), y(), width(), height())
                .color(alpha(Theme.Colors.OUTLINES_SMALL, this.alpha))
                .radius(px(Theme.Sizes.INPUT_BIND_RADIUS))
                .border(Math.max(0.5F, px(0.5F)), alpha(this.listening ? Theme.getAccent() : Theme.Colors.OUTLINES_SMALL, this.alpha))
                .draw();

        String label = this.listening
                ? LISTENING_LABEL
                : MenuText.bind(this.setting.getDisplayValue());
        float textSize = px(Theme.Sizes.INPUT_BIND_TEXT_SIZE);
        float textY = UiFonts.sfProDisplay().centeredTextY(y() + height() / 2.0F, textSize);
        int textColor = this.listening ? Theme.getAccent() : Theme.Colors.TEXT_GHOST;
        Render2DUtil.text(x() + width() / 2.0F, textY, textSize, label)
                .style(UiFontStyle.MEDIUM)
                .color(alpha(textColor, this.alpha))
                .align(TextAlign.CENTER)
                .draw();
    }
}
