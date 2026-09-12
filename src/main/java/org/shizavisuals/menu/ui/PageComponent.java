package org.shizavisuals.menu.ui;

import org.shizavisuals.menu.core.MenuOverlayState;
import org.shizavisuals.menu.i18n.MenuText;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

public abstract class PageComponent extends Component {
    protected MenuOverlayState state;
    protected int mouseX;
    protected int mouseY;
    protected float progress;

    public final void layout(Component frame, MenuOverlayState state, int mouseX, int mouseY) {
        this.state = state;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.progress = state.contentProgress();
        float slide = Math.round(36.0F * (1.0F - this.progress));
        attach(frame, frame.x() + frame.px(slide), frame.y(), frame.width(), frame.height());
        onLayout();
    }

    protected abstract void onLayout();

    protected final boolean contentContains(float mouseX, float mouseY) {
        return this.progress >= 0.75F
                && contains(mouseX, mouseY)
                && mouseY >= y() + px(Theme.Sizes.HEADER_HEIGHT);
    }

    protected final void pageHeader(String title, String subtitle) {
        text(32, 96, 24, MenuText.ui(title),
                Theme.Colors.TEXT_TITLE, this.progress, UiFontStyle.SEMIBOLD);
        text(32, 134, 16, MenuText.ui(subtitle),
                Theme.Colors.SECONDARY, this.progress, UiFontStyle.MEDIUM);
    }

    protected final void emptyState(float centerY, String title, String subtitle) {
        float titleHeight = UiFonts.sfProDisplay().textHeight(16);
        float descHeight = UiFonts.sfProDisplay().textHeight(12);
        float gap = 8;
        float blockTop = centerY - (titleHeight + gap + descHeight) / 2.0F;
        textCentered(512, blockTop, 16, MenuText.ui(title),
                Theme.Colors.PRIMARY, this.progress, UiFontStyle.MEDIUM);
        textCentered(512, blockTop + titleHeight + gap, 12, MenuText.ui(subtitle),
                Theme.Colors.SECONDARY, this.progress, UiFontStyle.REGULAR);
    }
}
