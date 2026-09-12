package org.shizavisuals.menu.ui;

public final class CardGrid {
    public static final int COLUMNS = 3;
    private static final int GUTTER = 16;

    private final SmoothScroll scroll = new SmoothScroll();
    private final int listTop;
    private final int cardWidth;
    private final int rowStep;
    private final int viewportBottom;
    private float offset;

    public CardGrid(int listTop, int cardWidth, int rowStep, int viewportBottom) {
        this.listTop = listTop;
        this.cardWidth = cardWidth;
        this.rowStep = rowStep;
        this.viewportBottom = viewportBottom;
    }

    public void update(int cardCount) {
        this.offset = this.scroll.update(maxScroll(cardCount));
    }

    public int x(int index) {
        return GUTTER + (index % COLUMNS) * (this.cardWidth + GUTTER);
    }

    public int y(int index) {
        return this.listTop + (index / COLUMNS) * this.rowStep - Math.round(this.offset);
    }

    public void scroll(double vertical, int cardCount) {
        this.scroll.scroll(vertical, maxScroll(cardCount));
    }

    private int maxScroll(int cardCount) {
        int rows = (cardCount + COLUMNS - 1) / COLUMNS;
        return Math.max(0, this.listTop + rows * this.rowStep - this.viewportBottom);
    }
}
