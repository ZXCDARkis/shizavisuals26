package org.shizavisuals.menu.pages.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.shizavisuals.utils.render.Textures;

public final class ModuleBindPopup extends Component {
    public enum ActionType {
        NONE,
        EDIT_BIND,
        CREATE_BIND,
        RELOAD
    }

    public static final class Action {
        public static final Action NONE = new Action(ActionType.NONE, -1);

        private final ActionType type;
        private final int bindIndex;

        private Action(ActionType type, int bindIndex) {
            this.type = type;
            this.bindIndex = bindIndex;
        }

        public ActionType type() {
            return this.type;
        }

        public int bindIndex() {
            return this.bindIndex;
        }
    }

    public static final int POPUP_WIDTH = Theme.Sizes.DROPDOWN_POPUP_WIDTH;
    private static final int POPUP_PADDING = Theme.Sizes.DROPDOWN_POPUP_PADDING;
    private static final int POPUP_GAP = Theme.Sizes.DROPDOWN_POPUP_GAP;
    private static final int ITEM_HEIGHT = Theme.Sizes.DROPDOWN_POPUP_ITEM_HEIGHT;
    private static final int ITEM_RADIUS = Theme.Sizes.DROPDOWN_POPUP_ITEM_RADIUS;
    private static final int ITEM_WIDTH = POPUP_WIDTH - POPUP_PADDING * 2;
    private static final int DIVIDER_TOP_MARGIN = 8;
    private static final int DIVIDER_BOTTOM_MARGIN = 3;
    private static final int ICON_SIZE = 12;
    private static final int ICON_TEXT_GAP = 6;

    private Feature feature;
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

    public void place(Component owner, int mouseX, int mouseY, float alpha) {
        attach(owner, owner.x(), owner.y(), owner.width(), owner.height());
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.alpha = alpha;
        clampPosition();
    }

    public void openAt(Feature feature, float x, float y, float minX, float minY, float maxX, float maxY) {
        if (feature == null || !feature.supportsBinds()) {
            closeImmediately();
            return;
        }
        this.feature = feature;
        this.open = true;
        this.popupX = x;
        this.popupY = y;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.animation.animate(0.0F, 1.0F, 150L, Animation.Easing.EASE_OUT_QUAD);
        clampPosition();
    }

    public void close() {
        if (this.open) {

            this.animation.animate(this.animation.getValue(), 0.0F, 120L, Animation.Easing.EASE_OUT_QUAD);
        }
        this.open = false;
    }

    public void closeImmediately() {
        this.open = false;
        this.feature = null;
        this.animation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    public boolean isOpen() {
        return this.open;
    }

    public Action handleMouseButton(int mouseX, int mouseY, int button) {
        if (!this.open || this.feature == null) {
            return Action.NONE;
        }
        if (!containsPopup(mouseX, mouseY)) {
            close();
            return Action.NONE;
        }
        if (button != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return Action.NONE;
        }

        int bindRows = bindRowCount();
        for (int index = 0; index < bindRows; index++) {
            if (!hit(mouseX, mouseY, this.popupX + POPUP_PADDING, bindRowY(index), ITEM_WIDTH, ITEM_HEIGHT)) {
                continue;
            }
            if (this.feature.getBind().isEmpty()) {
                return new Action(ActionType.CREATE_BIND, -1);
            }
            return new Action(ActionType.EDIT_BIND, index);
        }

        if (hit(mouseX, mouseY, this.popupX + POPUP_PADDING, createRowY(), ITEM_WIDTH, ITEM_HEIGHT)) {
            return new Action(ActionType.CREATE_BIND, -1);
        }
        if (hit(mouseX, mouseY, this.popupX + POPUP_PADDING, reloadRowY(), ITEM_WIDTH, ITEM_HEIGHT)) {
            return new Action(ActionType.RELOAD, -1);
        }
        return Action.NONE;
    }

    public boolean containsPopup(int mouseX, int mouseY) {
        return this.open && hit(mouseX, mouseY, this.popupX, this.popupY, POPUP_WIDTH, popupHeight());
    }

    public float popupX() {
        return this.popupX;
    }

    public float popupY() {
        return this.popupY;
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        float progress = this.animation.getValue();
        if (this.feature == null || progress <= 0.001F) {
            if (!this.open) {
                this.feature = null;
            }
            return;
        }

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
        glassPanel(sx(this.popupX), sy(this.popupY), px(POPUP_WIDTH), px(popupHeight()),
                px(Theme.Sizes.DROPDOWN_POPUP_RADIUS), px(20.0F), this.alpha);

        float contentX = this.popupX + POPUP_PADDING;
        if (this.feature.getBind().isEmpty()) {
            renderRow(contentX, bindRowY(0), Textures.Icons.COMMAND,
                    MenuText.ui("Unbound"), Theme.Colors.SECONDARY_DARK);
        } else {
            for (int index = 0; index < this.feature.getBind().size(); index++) {
                renderRow(contentX, bindRowY(index), Textures.Icons.COMMAND,
                        MenuText.bind(this.feature.getBind().getDisplayValue(index)), Theme.Colors.TEXT_TEXT);
            }
        }

        float dividerY = dividerY();
        Render2DUtil.rect(sx(contentX), sy(dividerY), px(ITEM_WIDTH), px(1))
                .color(0x00000000)
                .border(Math.max(0.5F, px(0.5F)), alpha(Theme.Colors.OUTLINES_MEDIUM, this.alpha))
                .draw();
        renderRow(contentX, createRowY(), Textures.Icons.CIRCLE_PLUS,
                MenuText.ui("New Hotkey"), Theme.Colors.TEXT_TEXT);
        renderRow(contentX, reloadRowY(), Textures.Icons.REFRESH_CCW,
                MenuText.ui("Reset"), Theme.Colors.TRAFFIC_CLOSE);
    }

    private void renderRow(float x, float y, Identifier icon, String label, int color) {
        boolean hovered = hit(this.mouseX, this.mouseY, x, y, ITEM_WIDTH, ITEM_HEIGHT);
        if (hovered) {
            Render2DUtil.rect(sx(x), sy(y), px(ITEM_WIDTH), px(ITEM_HEIGHT))
                    .color(alpha(Theme.Colors.OUTLINES_SMALL, this.alpha))
                    .radius(px(ITEM_RADIUS))
                    .draw();
        }

        float iconX = x + POPUP_PADDING;
        float iconY = y + (ITEM_HEIGHT - ICON_SIZE) / 2;
        texture(iconX, iconY, ICON_SIZE, icon, color, this.alpha);
        float textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        float textWidth = Math.max(1.0F, x + ITEM_WIDTH - POPUP_PADDING - textX);
        drawCenteredRowText(
                textX,
                iconY + ICON_SIZE / 2.0F,
                textWidth,
                label,
                color
        );
    }

    private void drawCenteredRowText(float x,
                                     float centerY,
                                     float maxWidth,
                                     String label,
                                     int color) {
        MsdfFont font = UiFonts.sfPro(UiFontStyle.MEDIUM.weight());
        float screenFontSize = px(12.0F);
        float letterSpacing = screenFontSize * UiFontStyle.MEDIUM.letterSpacingEm();
        String displayLabel = font.ellipsize(
                label,
                screenFontSize,
                letterSpacing,
                px(maxWidth)
        );
        float screenCenterY = sy(0) + px(centerY);
        float baselineCenterOffset = measureBaselineCenterOffset(
                font,
                displayLabel,
                screenFontSize
        );
        float screenTextY = screenCenterY - baselineCenterOffset - font.ascender(screenFontSize);

        Render2DUtil.pushScissor(
                sx(x),
                sy(centerY - ITEM_HEIGHT / 2.0F),
                px(maxWidth),
                px(ITEM_HEIGHT)
        );
        Render2DUtil.text(sx(x), screenTextY, screenFontSize, displayLabel)
                .style(UiFontStyle.MEDIUM)
                .color(alpha(color, this.alpha))
                .draw();
        Render2DUtil.popScissor();
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

    private int bindRowCount() {
        return Math.max(1, this.feature == null ? 0 : this.feature.getBind().size());
    }

    private float bindRowY(int index) {
        return this.popupY + POPUP_PADDING + index * (ITEM_HEIGHT + POPUP_GAP);
    }

    private float dividerY() {
        float bindRowsBottom = bindRowY(bindRowCount() - 1) + ITEM_HEIGHT;
        return bindRowsBottom + DIVIDER_TOP_MARGIN;
    }

    private float createRowY() {
        return dividerY() + DIVIDER_BOTTOM_MARGIN;
    }

    private float reloadRowY() {
        return createRowY() + ITEM_HEIGHT + POPUP_GAP;
    }

    private float popupHeight() {
        return reloadRowY() + ITEM_HEIGHT + POPUP_PADDING - this.popupY;
    }

    private void clampPosition() {
        this.popupX = clamp(this.popupX, this.minX, this.maxX);
        this.popupY = clamp(this.popupY, this.minY, maxPopupY());
    }

    private float maxPopupY() {
        float maxAllowedY = this.maxY - popupHeight();
        return Math.max(this.minY, maxAllowedY);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
