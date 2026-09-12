package org.shizavisuals.menu.ui.popups;

import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.InputComponent;
import org.shizavisuals.menu.ui.controls.PillButton;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.lwjgl.glfw.GLFW;

public final class ModalDialog extends Component {
    public static final int WIDTH = 185;
    public static final int HEIGHT = 136;
    private static final int RADIUS = 16;
    private static final int PADDING = 12;
    private static final int GAP = 8;
    private static final int INPUT_HEIGHT = 28;
    private static final int BUTTON_HEIGHT = 24;
    private static final int MODAL_X = (1024 - WIDTH) / 2;
    private static final int MODAL_Y = 252;

    public interface Extras {
        default void render(ModalDialog modal, float alpha) {
        }

        default boolean click(ModalDialog modal, int mouseX, int mouseY) {
            return false;
        }
    }

    private final Identifier titleIcon;
    private final String title;
    private final String[] descriptionLines;
    private final InputComponent input;
    private final Runnable onSave;
    private final Extras extras;
    private final PillButton cancelButton;
    private final PillButton saveButton;
    private boolean open;
    private float pageAlpha = 1.0F;
    private int mouseX;
    private int mouseY;

    public ModalDialog(Identifier titleIcon, String title, String[] descriptionLines,
                       InputComponent input, Runnable onSave, Extras extras) {
        this.titleIcon = titleIcon;
        this.title = title;
        this.descriptionLines = descriptionLines;
        this.input = input;
        this.onSave = onSave;
        this.extras = extras == null ? new Extras() {
        } : extras;
        this.cancelButton = new PillButton("Cancel", this::close);
        this.saveButton = new PillButton("Save", onSave)
                .colors(ColorUtil.multiplyAlpha(Theme.Colors.SYSTEM_INFORMATION, 0.9F),
                        Theme.Colors.SYSTEM_INFORMATION, Theme.Colors.TEXT_TITLE);
    }

    public void place(Component owner, int mouseX, int mouseY, float pageAlpha) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.pageAlpha = pageAlpha;
        attach(owner, owner.sx(MODAL_X), owner.sy(MODAL_Y), owner.px(WIDTH), owner.px(HEIGHT));
        if (!this.open) {
            return;
        }
        this.input.place(owner, contentX(), inputY(), contentWidth(), INPUT_HEIGHT, mouseX, mouseY)
                .alpha(pageAlpha);
        float buttonWidth = (contentWidth() - GAP) / 2.0F;
        this.cancelButton.place(owner, contentX(), buttonsY(), buttonWidth, BUTTON_HEIGHT, mouseX, mouseY)
                .alpha(pageAlpha);
        this.saveButton.place(owner, contentX() + buttonWidth + GAP, buttonsY(), buttonWidth, BUTTON_HEIGHT, mouseX, mouseY)
                .alpha(pageAlpha);
    }

    public void open() {
        this.open = true;
        this.input.focus();
    }

    public void close() {
        this.open = false;
        this.input.blur();
    }

    public boolean isOpen() {
        return this.open;
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            close();
            return true;
        }
        if (this.extras.click(this, mouseX, mouseY)) {
            return true;
        }
        if (this.input.handleClick(mouseX, mouseY)) {
            return true;
        }
        if (this.cancelButton.handleClick(mouseX, mouseY)) {
            return true;
        }
        this.saveButton.handleClick(mouseX, mouseY);
        return true;
    }

    public boolean handleKey(int key) {
        if (!this.open) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            this.onSave.run();
            return true;
        }
        this.input.handleKey(key);
        return true;
    }

    public boolean handleCharacter(int codePoint) {
        return this.open && this.input.handleCharacter(codePoint);
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (!this.open) {
            return;
        }
        Component owner = frame();

        Render2DUtil.rect(owner.x(), owner.y() + owner.px(Theme.Sizes.HEADER_HEIGHT),
                        owner.width(), owner.height() - owner.px(Theme.Sizes.HEADER_HEIGHT))
                .color(ColorUtil.withAlpha(Theme.Colors.OVERLAY, Math.round(218.0F * this.pageAlpha)))
                .radius(0, 0, owner.px(12), owner.px(12))
                .draw();
        glassPanel(x(), y(), width(), height(), px(RADIUS), px(20.0F), this.pageAlpha);

        float cursorY = MODAL_Y + PADDING;
        texture(contentX(), cursorY + 1, 12, this.titleIcon, Theme.Colors.SECONDARY, this.pageAlpha);
        text(contentX() + 18, centeredTextY(cursorY + 7, 12), 12,
                MenuText.ui(this.title), Theme.Colors.TEXT_TITLE, this.pageAlpha, UiFontStyle.SEMIBOLD);
        float lineStep = org.shizavisuals.utils.render.gui.UiFonts.sfProDisplay().textHeight(10) + 2;
        for (int index = 0; index < this.descriptionLines.length; index++) {
            text(contentX(), cursorY + 22 + index * lineStep, 10,
                    MenuText.ui(this.descriptionLines[index]),
                    Theme.Colors.SECONDARY, this.pageAlpha, UiFontStyle.REGULAR);
        }

        this.input.render(minecraft, guiGraphicsExtractor);
        this.cancelButton.render(minecraft, guiGraphicsExtractor);
        this.saveButton.render(minecraft, guiGraphicsExtractor);

        this.extras.render(this, this.pageAlpha);
    }

    public int contentX() {
        return MODAL_X + PADDING;
    }

    public int contentWidth() {
        return WIDTH - PADDING * 2;
    }

    public int headerY() {
        return MODAL_Y + PADDING;
    }

    public int inputY() {
        return MODAL_Y + HEIGHT - PADDING - BUTTON_HEIGHT - GAP - INPUT_HEIGHT;
    }

    public int inputHeight() {
        return INPUT_HEIGHT;
    }

    private int buttonsY() {
        return MODAL_Y + HEIGHT - PADDING - BUTTON_HEIGHT;
    }
}
