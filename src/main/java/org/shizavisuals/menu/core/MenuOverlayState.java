package org.shizavisuals.menu.core;

import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.math.MathUtil;
import org.shizavisuals.utils.render.Theme;

public final class MenuOverlayState {
    private static final long CONTENT_ANIMATION_MS = 180L;
    private static final long OPEN_ANIMATION_MS = 320L;
    private static final long CLOSE_ANIMATION_MS = 240L;
    public static final float MIN_UI_SCALE = 0.525F;
    public static final float MAX_UI_SCALE = 2.1F;
    public static final float MIN_UI_SCALE_PERCENT = 50.0F;
    public static final float MAX_UI_SCALE_PERCENT = 200.0F;
    public static final float DEFAULT_UI_SCALE_VALUE = 1.0F / 3.0F;
    public static final int DEFAULT_PANEL_RADIUS = 12;

    private final Animation contentAnimation = new Animation(CONTENT_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    private final Animation openAnimation = new Animation(0L, Animation.Easing.EASE_OUT_QUAD);
    private boolean open;
    private boolean closing;
    private boolean grabbedMouseBeforeOpen;
    private boolean positionInitialized;
    private float panelX;
    private float panelY;
    private MenuPage page = MenuPage.NONE;
    private MenuPage lastPage = MenuPage.NONE;
    private int focusedHeaderAction = -1;
    private float uiScaleValue = MenuConfigStore.loadUiScaleValue();
    private float panelRadius = Theme.Sizes.PANEL_RADII[(int) MathUtil.clamp(
            MenuConfigStore.getInt("roundedMode", Theme.Sizes.DEFAULT_PANEL_RADIUS_INDEX), 0, Theme.Sizes.PANEL_RADII.length - 1)];
    private final java.util.List<MenuPage> backHistory = new java.util.ArrayList<>();
    private final java.util.List<MenuPage> forwardHistory = new java.util.ArrayList<>();
    private boolean traversingHistory;

    public boolean isOpen() {
        return this.open;
    }

    public void open(boolean grabbedMouseBeforeOpen) {

        float from = this.open ? openProgress() : 0.0F;
        this.open = true;
        this.closing = false;
        this.grabbedMouseBeforeOpen = grabbedMouseBeforeOpen;
        this.positionInitialized = false;
        this.openAnimation.animate(from, 1.0F, OPEN_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    }

    public void beginClose() {
        if (!this.open || this.closing) {
            return;
        }
        this.closing = true;
        // The menu can start closing while the resize grip is held. If the preview
        // survives, liveUiScaleValue() keeps feeding the (half-dragged) scale to the
        // layout and the percentage pill freezes on it after the next open.
        this.previewUiScaleValue = -1.0F;
        this.openAnimation.animate(openProgress(), 0.0F, CLOSE_ANIMATION_MS, Animation.Easing.EASE_IN_QUAD);
    }

    public void suspend() {
        this.open = false;
        this.closing = false;
        this.previewUiScaleValue = -1.0F;
        this.openAnimation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
    }

    public void resume() {
        this.open = true;
        this.closing = false;
        this.openAnimation.animate(0.0F, 1.0F, OPEN_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    }

    public void close() {
        this.open = false;
        this.closing = false;
        this.grabbedMouseBeforeOpen = false;
        this.previewUiScaleValue = -1.0F;
        this.page = MenuPage.NONE;
        this.lastPage = MenuPage.NONE;
        this.contentAnimation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
        this.openAnimation.animate(0.0F, 0.0F, 0L, Animation.Easing.EASE_OUT_QUAD);
        this.focusedHeaderAction = -1;
        this.backHistory.clear();
        this.forwardHistory.clear();
    }

    public boolean isClosing() {
        return this.closing;
    }

    public boolean isInteractive() {
        return this.open && !this.closing;
    }

    public float openProgress() {
        return this.openAnimation.getValue();
    }

    public boolean grabbedMouseBeforeOpen() {
        return this.grabbedMouseBeforeOpen;
    }

    public float panelX() {
        return this.panelX;
    }

    public float panelY() {
        return this.panelY;
    }

    public MenuPage page() {
        return this.page;
    }

    public MenuPage displayPage() {
        return this.page != MenuPage.NONE || contentProgress() <= 0.001F ? this.page : this.lastPage;
    }

    public float contentProgress() {
        return this.contentAnimation.getValue();
    }

    public int focusedHeaderAction() {
        return this.focusedHeaderAction;
    }

    public float uiScaleValue() {
        return this.uiScaleValue;
    }

    public void setUiScaleValue(float uiScaleValue) {
        this.uiScaleValue = MathUtil.clamp01(uiScaleValue);
        MenuConfigStore.saveUiScaleValue(this.uiScaleValue);
    }

    /**
     * Scale used while the resize grip is held. Kept out of the config so dragging
     * the grip does not write to disk on every frame, and is discarded on release.
     */
    private float previewUiScaleValue = -1.0F;

    public void previewUiScaleValue(float value) {
        this.previewUiScaleValue = MathUtil.clamp01(value);
    }

    public void commitPreviewUiScale() {
        if (this.previewUiScaleValue >= 0.0F) {
            setUiScaleValue(this.previewUiScaleValue);
            this.previewUiScaleValue = -1.0F;
        }
    }

    public void cancelPreviewUiScale() {
        this.previewUiScaleValue = -1.0F;
    }

    /** Live scale: the preview while resizing, otherwise the committed one. */
    public float liveUiScaleValue() {
        return this.previewUiScaleValue >= 0.0F ? this.previewUiScaleValue : this.uiScaleValue;
    }

    public float panelRadius() {
        return this.panelRadius;
    }

    public void setPanelRadius(float panelRadius) {
        this.panelRadius = Math.max(0.0F, panelRadius);
    }

    public void focusNextHeaderAction(int direction) {
        if (this.focusedHeaderAction < 0) {
            this.focusedHeaderAction = direction < 0 ? MenuPage.actionCount() - 1 : 0;
            return;
        }
        this.focusedHeaderAction = Math.floorMod(this.focusedHeaderAction + direction, MenuPage.actionCount());
    }

    public void activateFocusedHeaderAction() {
        if (this.focusedHeaderAction >= 0) {
            openPage(MenuPage.actionAt(this.focusedHeaderAction));
        }
    }

    public void openPage(MenuPage requestedPage) {
        if (requestedPage == null) {
            return;
        }
        if (requestedPage == this.page) {
            return;
        }
        if (!this.traversingHistory) {
            this.backHistory.add(this.page);
            this.forwardHistory.clear();
        }
        if (requestedPage == MenuPage.NONE) {
            this.page = MenuPage.NONE;
            this.focusedHeaderAction = -1;
            this.contentAnimation.animate(contentProgress(), 0.0F, CONTENT_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
            return;
        }
        this.page = requestedPage;
        this.lastPage = requestedPage;
        this.focusedHeaderAction = requestedPage.actionIndex();
        this.contentAnimation.animate(0.0F, 1.0F, CONTENT_ANIMATION_MS, Animation.Easing.EASE_OUT_QUAD);
    }

    public boolean canGoBack() {
        return !this.backHistory.isEmpty();
    }

    public boolean canGoForward() {
        return !this.forwardHistory.isEmpty();
    }

    public void goBack() {
        if (!canGoBack()) {
            return;
        }
        this.traversingHistory = true;
        try {
            MenuPage target = this.backHistory.remove(this.backHistory.size() - 1);
            this.forwardHistory.add(this.page);
            openPage(target);
        } finally {
            this.traversingHistory = false;
        }
    }

    public void goForward() {
        if (!canGoForward()) {
            return;
        }
        this.traversingHistory = true;
        try {
            MenuPage target = this.forwardHistory.remove(this.forwardHistory.size() - 1);
            this.backHistory.add(this.page);
            openPage(target);
        } finally {
            this.traversingHistory = false;
        }
    }

    public boolean hasPosition() {
        return this.positionInitialized;
    }

    public void markPositionInitialized() {
        this.positionInitialized = true;
    }

    public void setPanelPosition(float panelX, float panelY) {
        this.panelX = panelX;
        this.panelY = panelY;
    }
}
