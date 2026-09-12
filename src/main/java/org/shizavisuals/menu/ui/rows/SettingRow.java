package org.shizavisuals.menu.ui.rows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.shizavisuals.feature.setting.Setting;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.menu.ui.Component;
import org.shizavisuals.menu.ui.controls.MarqueeText;
import org.shizavisuals.utils.render.Textures;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.UiFontStyle;

public abstract class SettingRow extends Component {
    public static final int ROW_HEIGHT = Theme.Sizes.MODULE_CARD_ROW_HEIGHT;
    protected static final int PADDING = Theme.Sizes.MODULE_CARD_PADDING;
    protected static final int WARNING_ICON_SIZE = 12;
    protected static final int WARNING_ICON_GAP = 5;

    protected final Setting<?> setting;
    private final MarqueeText label;
    protected String featureName;
    protected RowHost host;
    protected int rowX;
    protected int rowY;
    protected int rowWidth;
    protected float rowAlpha = 1.0F;
    protected int mouseX;
    protected int mouseY;

    protected SettingRow(Setting<?> setting) {
        this.setting = setting;
        this.label = new MarqueeText(() ->
                MenuText.setting(this.featureName, this.setting.getName()));
    }

    final SettingRow context(String featureName) {
        this.featureName = featureName;
        return this;
    }

    public final Setting<?> setting() {
        return this.setting;
    }

    public final boolean visible() {
        return this.setting.isVisible();
    }

    public final void place(Component owner, RowHost host, int x, int y, int width, float alpha, int mouseX, int mouseY) {
        this.host = host;
        this.rowX = x;
        this.rowY = y;
        this.rowWidth = width;
        this.rowAlpha = alpha;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        attach(owner, owner.sx(x), owner.sy(y), owner.px(width), owner.px(ROW_HEIGHT));
        placeControl(owner);
        int labelX = this.rowX + PADDING;
        float labelWidth = Math.max(0.0F, labelRight() - labelX);
        this.label.place(owner, labelX, this.rowY, labelWidth, ROW_HEIGHT, mouseX, mouseY);
    }

    protected abstract void placeControl(Component owner);

    public abstract boolean click(int mouseX, int mouseY);

    public boolean key(int key) {
        return false;
    }

    public boolean character(int codePoint) {
        return false;
    }

    public boolean captureMouse(int button) {
        return false;
    }

    public void drag(int mouseX) {
    }

    public boolean scroll(int mouseX, int mouseY, double vertical) {
        return false;
    }

    public void releasePointer() {
    }

    public boolean isCapturingBind() {
        return false;
    }

    public void closeTransient() {
    }

    public void closeTransientImmediately() {
        closeTransient();
    }

    public boolean hasOpenPopup() {
        return false;
    }

    public boolean popupClick(int mouseX, int mouseY) {
        return false;
    }

    public void renderPopupOverlay(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
    }

    @Override
    public final void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
        this.label.style(12.0F, UiFontStyle.MEDIUM, labelColor(), this.rowAlpha)
                .render(minecraft, guiGraphicsExtractor);
        Integer warning = warningColor(this.setting);
        if (warning != null && !controlRendersWarning()) {
            texture(warningIconX(), this.rowY + (ROW_HEIGHT - WARNING_ICON_SIZE) / 2, WARNING_ICON_SIZE,
                    Textures.Icons.TRIANGLE_ALERT, warning, this.rowAlpha);
        }
        renderExtras(minecraft, guiGraphicsExtractor);
        renderControl(minecraft, guiGraphicsExtractor);
    }

    protected abstract void renderControl(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor);

    protected void renderExtras(Minecraft minecraft, GuiGraphicsExtractor guiGraphicsExtractor) {
    }

    protected abstract int controlWidth();

    protected abstract int controlHeight();

    protected final int controlX() {
        return this.rowX + this.rowWidth - PADDING - controlWidth();
    }

    protected final int controlY() {
        return this.rowY + (ROW_HEIGHT - controlHeight()) / 2;
    }

    protected final int warningIconX() {
        return controlX() - WARNING_ICON_GAP - WARNING_ICON_SIZE;
    }

    protected float labelRight() {
        Integer warning = warningColor(this.setting);
        return warning != null && !controlRendersWarning()
                ? warningIconX() - WARNING_ICON_GAP
                : controlX() - WARNING_ICON_GAP;
    }

    protected boolean controlRendersWarning() {
        return false;
    }

    protected int labelColor() {
        return Theme.Colors.PRIMARY;
    }

    protected static int controlAccent(Setting<?> setting) {
        Integer warning = warningColor(setting);
        return warning == null ? Theme.getAccent() : warning;
    }

    public static Integer warningColor(Setting<?> setting) {
        return warningColor(setting.warningLevel());
    }

    public static Integer warningColor(Setting.WarningLevel warningLevel) {
        return switch (warningLevel) {
            case NONE -> null;
            case RISK -> Theme.Colors.RISK;
            case EXTRA_RISK -> Theme.Colors.EXTRA_RISK;
        };
    }
}
