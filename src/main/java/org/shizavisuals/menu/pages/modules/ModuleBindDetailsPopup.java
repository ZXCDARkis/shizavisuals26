package org.shizavisuals.menu.pages.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.feature.BindMode;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.ToggleComponent;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.utils.render.Textures;

public final class ModuleBindDetailsPopup extends Component {
    public static final int POPUP_WIDTH = Theme.Sizes.DROPDOWN_POPUP_WIDTH;
    public static final int POPUP_HEIGHT = 148;
    private static final int POPUP_PADDING = Theme.Sizes.DROPDOWN_POPUP_PADDING;
    private static final int POPUP_GAP = Theme.Sizes.DROPDOWN_POPUP_GAP;
    private static final int ITEM_HEIGHT = Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT;
    private static final int ITEM_RADIUS = Theme.Sizes.DROPDOWN_POPUP_ITEM_RADIUS;
    private static final int ITEM_WIDTH = POPUP_WIDTH - POPUP_PADDING * 2;
    private static final int ICON_SIZE = 12;
    private static final int ICON_TEXT_GAP = 6;
    private static final int POPUP_OFFSET_X = 8;
    private static final int VALUE_PADDING_RIGHT = 12;
    private static final int KEY_ROW_Y = POPUP_PADDING;
    private static final int MODE_ROW_Y = KEY_ROW_Y + ITEM_HEIGHT + POPUP_GAP;
    private static final int VISIBLE_ROW_Y = MODE_ROW_Y + ITEM_HEIGHT + POPUP_GAP;
    private static final int DELETE_ROW_Y = VISIBLE_ROW_Y + ITEM_HEIGHT + POPUP_GAP;

    private final ToggleComponent visibleToggle = new ToggleComponent(
            () -> this.feature != null && currentBindVisible(),
            this::toggleCurrentBindVisible,
            ToggleComponent.Style.SWITCH,
            Theme.Colors.CONTROL_STRONG,
            Theme.getAccent()
    );
    private Feature feature;
    private int bindIndex = -1;
    private int mouseX;
    private int mouseY;
    private float popupX;
    private float popupY;
    private float minX;
    private float minY;
    private float maxX;
    private float maxY;
    private float alpha = 1.0F;
    private boolean open;
    private final Animation animation = new Animation(150L, Animation.Easing.EASE_OUT_QUAD);
    private boolean listeningForBind;
    private BindMode pendingBindMode = BindMode.TOGGLE;
    private boolean pendingBindVisible = true;

    public void place(Component owner, int mouseX, int mouseY, float alpha) {
        attach(owner, owner.x(), owner.y(), owner.width(), owner.height());
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.alpha = alpha;
        if (this.open) {
            placeVisibleToggle(owner);
        }
    }

    public void openForBind(Feature feature, int bindIndex, float anchorX, float anchorY, float minX, float minY, float maxX, float maxY) {
        openInternal(feature, bindIndex, false, anchorX, anchorY, minX, minY, maxX, maxY);
    }

    public void openForNewBind(Feature feature, float anchorX, float anchorY, float minX, float minY, float maxX, float maxY) {
        openInternal(feature, -1, false, anchorX, anchorY, minX, minY, maxX, maxY);
    }

    public void close() {
        this.open = false;
        this.feature = null;
        this.bindIndex = -1;
        this.listeningForBind = false;
        this.pendingBindMode = BindMode.TOGGLE;
        this.pendingBindVisible = true;
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean isListeningForBind() {
        return this.open && this.listeningForBind;
    }

    public boolean containsPopup(int mouseX, int mouseY) {
        return this.open && hit(mouseX, mouseY, this.popupX, this.popupY, POPUP_WIDTH, POPUP_HEIGHT);
    }

    public boolean captureKey(int key) {
        if (!isListeningForBind() || this.feature == null) {
            return false;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (this.bindIndex < 0) {
                close();
            } else {
                this.listeningForBind = false;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            deleteCurrentBind();
            return true;
        }

        applyBind(BindSetting.key(key));
        return true;
    }

    public boolean captureMouseButton(int button) {
        if (!isListeningForBind() || this.feature == null) {
            return false;
        }

        applyBind(BindSetting.mouse(button));
        return true;
    }

    public boolean handleMouseButton(int mouseX, int mouseY, int button) {
        if (!this.open || this.feature == null) {
            return false;
        }
        if (!containsPopup(mouseX, mouseY)) {
            close();
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }

        float contentX = this.popupX + POPUP_PADDING;
        if (hit(mouseX, mouseY, contentX, this.popupY + KEY_ROW_Y, ITEM_WIDTH, ITEM_HEIGHT)) {
            this.listeningForBind = true;
            return true;
        }
        if (hit(mouseX, mouseY, contentX, this.popupY + MODE_ROW_Y, ITEM_WIDTH, ITEM_HEIGHT)) {
            BindMode nextMode = currentBindMode() == BindMode.TOGGLE ? BindMode.HOLD : BindMode.TOGGLE;
            setCurrentBindMode(nextMode);
            return true;
        }
        if (hit(mouseX, mouseY, contentX, this.popupY + VISIBLE_ROW_Y, ITEM_WIDTH, ITEM_HEIGHT)) {
            if (!this.visibleToggle.handleClick(mouseX, mouseY)) {
                toggleCurrentBindVisible();
            }
            return true;
        }
        if (hit(mouseX, mouseY, contentX, this.popupY + DELETE_ROW_Y, ITEM_WIDTH, ITEM_HEIGHT)) {
            deleteCurrentBind();
            return true;
        }
        return true;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        if (!this.open || this.feature == null) {
            return;
        }

        float progress = this.animation.getValue();
        float baseAlpha = this.alpha;
        float baseY = this.popupY;
        this.alpha = baseAlpha * progress;
        this.popupY = baseY - 6.0F * (1.0F - progress);
        try {
            renderContent();
        } finally {
            this.alpha = baseAlpha;
            this.popupY = baseY;
        }
    }

    private void renderContent() {
        glassPanel(sx(this.popupX), sy(this.popupY), px(POPUP_WIDTH), px(POPUP_HEIGHT),
                px(Theme.Sizes.DROPDOWN_POPUP_RADIUS), px(20.0F), this.alpha);

        float contentX = this.popupX + POPUP_PADDING;
        int keyValueColor = this.listeningForBind ? Theme.getAccent() : Theme.Colors.SECONDARY_DARK;
        renderValueRow(contentX, this.popupY + KEY_ROW_Y, Textures.Icons.COMMAND,
                MenuText.setting("Key"), bindLabel(), Theme.Colors.TEXT_TEXT, keyValueColor);
        renderValueRow(contentX, this.popupY + MODE_ROW_Y, Textures.Icons.KEYBOARD,
                MenuText.setting("Mode"), modeLabel(), Theme.Colors.TEXT_TEXT, Theme.Colors.SECONDARY_DARK);
        renderVisibleRow(contentX, this.popupY + VISIBLE_ROW_Y);
        renderValueRow(contentX, this.popupY + DELETE_ROW_Y, Textures.Icons.DELETE,
                MenuText.ui("Delete"), null, Theme.Colors.TRAFFIC_CLOSE, Theme.Colors.TRAFFIC_CLOSE);
    }

    private void renderValueRow(float x, float y, Identifier icon, String label, String value, int labelColor, int valueColor) {
        boolean hovered = hit(this.mouseX, this.mouseY, x, y, ITEM_WIDTH, ITEM_HEIGHT);
        if (hovered) {
            Render2DUtil.rect(sx(x), sy(y), px(ITEM_WIDTH), px(ITEM_HEIGHT))
                    .color(alpha(Theme.Colors.OUTLINES_SMALL, this.alpha))
                    .radius(px(ITEM_RADIUS))
                    .draw();
        }

        float iconX = x + POPUP_PADDING;
        float iconY = y + (ITEM_HEIGHT - ICON_SIZE) / 2;
        texture(iconX, iconY, ICON_SIZE, icon, labelColor, this.alpha);
        drawCenteredRowText(iconX + ICON_SIZE + ICON_TEXT_GAP, iconY + ICON_SIZE / 2.0F, label, labelColor);

        if (value != null) {
            drawRightAlignedRowText(x + ITEM_WIDTH - VALUE_PADDING_RIGHT, iconY + ICON_SIZE / 2.0F, value, valueColor);
        }
    }

    private void renderVisibleRow(float x, float y) {
        boolean hovered = hit(this.mouseX, this.mouseY, x, y, ITEM_WIDTH, ITEM_HEIGHT);
        if (hovered) {
            Render2DUtil.rect(sx(x), sy(y), px(ITEM_WIDTH), px(ITEM_HEIGHT))
                    .color(alpha(Theme.Colors.OUTLINES_SMALL, this.alpha))
                    .radius(px(ITEM_RADIUS))
                    .draw();
        }

        float iconX = x + POPUP_PADDING;
        float iconY = y + (ITEM_HEIGHT - ICON_SIZE) / 2;
        texture(iconX, iconY, ICON_SIZE, Textures.Icons.EYE, Theme.Colors.TEXT_TEXT, this.alpha);
        drawCenteredRowText(iconX + ICON_SIZE + ICON_TEXT_GAP, iconY + ICON_SIZE / 2.0F,
                MenuText.ui("Visible"), Theme.Colors.TEXT_TEXT);
        this.visibleToggle.render(null, null);
    }

    private void drawCenteredRowText(float x, float centerY, String label, int color) {
        drawRowText(x, centerY, label, color, false);
    }

    private void drawRightAlignedRowText(float x, float centerY, String label, int color) {
        drawRowText(x, centerY, label, color, true);
    }

    private void drawRowText(float x, float centerY, String label, int color, boolean rightAlign) {
        MsdfFont font = UiFonts.sfProDisplay();
        float screenFontSize = px(12.0F);
        float screenCenterY = sy(0) + px(centerY);
        float baselineCenterOffset = measureBaselineCenterOffset(font, label, screenFontSize);
        float screenTextY = screenCenterY - baselineCenterOffset - font.ascender(screenFontSize);
        Render2DUtil.TextBuilder text = Render2DUtil.text(sx(x), screenTextY, screenFontSize, label)
                .style(UiFontStyle.MEDIUM)
                .color(alpha(color, this.alpha));
        if (rightAlign) {
            text.align(TextAlign.RIGHT);
        }
        text.draw();
    }

    private float measureBaselineCenterOffset(MsdfFont font, String text, float size) {
        float minTop = Float.POSITIVE_INFINITY;
        float maxBottom = Float.NEGATIVE_INFINITY;

        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);

            MsdfFont.Glyph glyph = font.glyph(codePoint);
            if (glyph == null || glyph.planeBounds() == null) {
                continue;
            }

            MsdfFont.Bounds plane = glyph.planeBounds();
            minTop = Math.min(minTop, -plane.top() * size);
            maxBottom = Math.max(maxBottom, -plane.bottom() * size);
        }

        if (minTop == Float.POSITIVE_INFINITY || maxBottom == Float.NEGATIVE_INFINITY) {
            return 0.0F;
        }
        return (minTop + maxBottom) * 0.5F;
    }

    private String bindLabel() {
        if (this.listeningForBind) {
            return MenuText.ui("Press");
        }
        if (this.feature == null || !this.feature.hasBindAt(this.bindIndex)) {
            return MenuText.ui("Unbound");
        }
        return MenuText.bind(this.feature.getBind().getDisplayValue(this.bindIndex));
    }

    private String modeLabel() {
        return MenuText.option(currentBindMode() == BindMode.HOLD ? "Hold" : "Toggle");
    }

    private void placeVisibleToggle(Component owner) {
        if (this.feature == null) {
            return;
        }
        float progress = this.animation.getValue();
        this.visibleToggle.place(
                        owner,
                        Math.round(this.popupX + POPUP_WIDTH - POPUP_PADDING - Theme.Sizes.MODULE_CARD_SWITCH_WIDTH - POPUP_PADDING),
                        Math.round(this.popupY + VISIBLE_ROW_Y + (ITEM_HEIGHT - Theme.Sizes.MODULE_CARD_SWITCH_HEIGHT) / 2 - 6.0F * (1.0F - progress)),
                        Theme.Sizes.MODULE_CARD_SWITCH_WIDTH,
                        Theme.Sizes.MODULE_CARD_SWITCH_HEIGHT
                )
                .style(ToggleComponent.Style.SWITCH, Theme.Colors.CONTROL_STRONG, Theme.getAccent())
                .alpha(this.alpha * progress);
    }

    private void openInternal(Feature feature, int bindIndex, boolean listenImmediately, float anchorX, float anchorY, float minX, float minY, float maxX, float maxY) {
        this.feature = feature;
        this.bindIndex = bindIndex;
        this.listeningForBind = listenImmediately;
        this.pendingBindMode = feature != null && feature.hasBindAt(bindIndex) ? feature.getBindModeAt(bindIndex) : BindMode.TOGGLE;
        this.pendingBindVisible = feature == null || !feature.hasBindAt(bindIndex) || feature.isBindVisibleAt(bindIndex);
        if (!this.open) {
            this.animation.animate(0.0F, 1.0F, 150L, Animation.Easing.EASE_OUT_QUAD);
        }
        this.open = true;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;

        float preferredRightX = anchorX + ModuleBindPopup.POPUP_WIDTH + POPUP_OFFSET_X;
        float preferredLeftX = anchorX - POPUP_WIDTH - POPUP_OFFSET_X;
        this.popupX = preferredRightX + POPUP_WIDTH <= maxX ? preferredRightX : preferredLeftX;
        this.popupY = anchorY;
        clampPosition();
    }

    private void applyBind(int bindCode) {
        if (this.feature == null) {
            return;
        }

        BindMode mode = currentBindMode();
        boolean visible = currentBindVisible();
        if (this.bindIndex >= 0 && this.feature.hasBindAt(this.bindIndex)) {
            this.bindIndex = this.feature.setBindAt(this.bindIndex, bindCode);
        } else {
            this.bindIndex = this.feature.addBind(bindCode);
        }
        if (this.bindIndex >= 0) {
            this.feature.setBindModeAt(this.bindIndex, mode);
            this.feature.setBindVisibleAt(this.bindIndex, visible);
        }
        this.listeningForBind = false;
    }

    private BindMode currentBindMode() {
        if (this.feature != null && this.feature.hasBindAt(this.bindIndex)) {
            return this.feature.getBindModeAt(this.bindIndex);
        }
        return this.pendingBindMode;
    }

    private void setCurrentBindMode(BindMode mode) {
        if (this.feature != null && this.feature.hasBindAt(this.bindIndex)) {
            this.feature.setBindModeAt(this.bindIndex, mode);
            return;
        }
        this.pendingBindMode = mode;
    }

    private boolean currentBindVisible() {
        if (this.feature != null && this.feature.hasBindAt(this.bindIndex)) {
            return this.feature.isBindVisibleAt(this.bindIndex);
        }
        return this.pendingBindVisible;
    }

    private void toggleCurrentBindVisible() {
        if (this.feature != null && this.feature.hasBindAt(this.bindIndex)) {
            this.feature.setBindVisibleAt(this.bindIndex, !this.feature.isBindVisibleAt(this.bindIndex));
            return;
        }
        this.pendingBindVisible = !this.pendingBindVisible;
    }

    private void deleteCurrentBind() {
        if (this.feature != null && this.bindIndex >= 0) {
            this.feature.removeBindAt(this.bindIndex);
        }
        close();
    }

    private void clampPosition() {
        this.popupX = clamp(this.popupX, this.minX, this.maxX - POPUP_WIDTH);
        this.popupY = clamp(this.popupY, this.minY, this.maxY - POPUP_HEIGHT);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
